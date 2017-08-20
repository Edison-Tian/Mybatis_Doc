/**
 * Copyright 2009-2015 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jd.edison.mybatis.mapping;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.jd.edison.mybatis.cache.Cache;
import com.jd.edison.mybatis.cache.CacheException;
import com.jd.edison.mybatis.cache.impl.PerpetualCache;
import com.jd.edison.mybatis.reflection.MetaObject;
import com.jd.edison.mybatis.reflection.SystemMetaObject;
import com.jd.edison.mybatis.cache.decorators.BlockingCache;
import com.jd.edison.mybatis.cache.decorators.LoggingCache;
import com.jd.edison.mybatis.cache.decorators.LruCache;
import com.jd.edison.mybatis.cache.decorators.ScheduledCache;
import com.jd.edison.mybatis.cache.decorators.SerializedCache;
import com.jd.edison.mybatis.cache.decorators.SynchronizedCache;

/**
 * 缓存构建器
 *
 * @author Clinton Begin
 */
public class CacheBuilder {
	/**
	 * NameSpace
	 */
	private String id;
	private Class<? extends Cache> implementation;
	private List<Class<? extends Cache>> decorators;
	private Integer size;
	private Long clearInterval;
	private boolean readWrite;
	private Properties properties;
	private boolean blocking;

	public CacheBuilder(String id) {
		this.id = id;
		this.decorators = new ArrayList<>();
	}

	/**
	 * 添加缓存实现类
	 *
	 * @param implementation 缓存实现类
	 * @return
	 */
	public CacheBuilder implementation(Class<? extends Cache> implementation) {
		this.implementation = implementation;
		return this;
	}

	/**
	 * 添加回收策略
	 *
	 * @param decorator
	 * @return
	 */
	public CacheBuilder addDecorator(Class<? extends Cache> decorator) {
		if (decorator != null) {
			this.decorators.add(decorator);
		}
		return this;
	}

	/**
	 * 绑定最高缓存对象数量
	 *
	 * @param size
	 * @return
	 */
	public CacheBuilder size(Integer size) {
		this.size = size;
		return this;
	}

	/**
	 * 绑定过期时间
	 *
	 * @param clearInterval
	 * @return
	 */
	public CacheBuilder clearInterval(Long clearInterval) {
		this.clearInterval = clearInterval;
		return this;
	}

	/**
	 * 绑定是否只读
	 *
	 * @param readWrite
	 * @return
	 */
	public CacheBuilder readWrite(boolean readWrite) {
		this.readWrite = readWrite;
		return this;
	}

	public CacheBuilder blocking(boolean blocking) {
		this.blocking = blocking;
		return this;
	}

	/**
	 * 绑定其他配置信息
	 *
	 * @param properties
	 * @return
	 */
	public CacheBuilder properties(Properties properties) {
		this.properties = properties;
		return this;
	}

	/**
	 * 构建缓存
	 * 1，设置默认的缓存策略
	 * 2，获取缓存实例，并与命名空间相绑定
	 * 3，设置缓存的自定义属性
	 * 4，若是mybatis提供的缓存策略，则会安排配置去匹配相应的装饰器（回收策略）
	 *
	 * @return 缓存对象
	 */
	public Cache build() {
		//设置默认缓存策略
		setDefaultImplementations();
		//获取缓存实例，并与namespace绑定
		Cache cache = newBaseCacheInstance(implementation, id);
		//设置缓存的自定义属性
		setCacheProperties(cache);
		// issue #352, do not apply decorators to custom caches
		//如果是默认缓存的话，使用装饰者模式进行装饰
		if (PerpetualCache.class.equals(cache.getClass())) {
			for (Class<? extends Cache> decorator : decorators) {
				cache = newCacheDecoratorInstance(decorator, cache);
				setCacheProperties(cache);
			}
			cache = setStandardDecorators(cache);
		} else if (!LoggingCache.class.isAssignableFrom(cache.getClass())) {
			cache = new LoggingCache(cache);
		}
		return cache;
	}

	/**
	 * 设置默认缓存策略
	 * TODO
	 * 这块没太看懂，外层已经绑定了默认的缓存实现和回收策略，不知道这块为为什么还要再设置一遍
	 */
	private void setDefaultImplementations() {
		if (implementation == null) {
			implementation = PerpetualCache.class;
			if (decorators.isEmpty()) {
				decorators.add(LruCache.class);
			}
		}
	}

	/**
	 * 设置装饰器(设置各种回收策略)
	 *
	 * @param cache
	 * @return
	 */
	private Cache setStandardDecorators(Cache cache) {
		try {
			MetaObject metaCache = SystemMetaObject.forObject(cache);
			if (size != null && metaCache.hasSetter("size")) {
				metaCache.setValue("size", size);
			}
			if (clearInterval != null) {
				cache = new ScheduledCache(cache);
				((ScheduledCache) cache).setClearInterval(clearInterval);
			}
			if (readWrite) {
				cache = new SerializedCache(cache);
			}
			cache = new LoggingCache(cache);
			cache = new SynchronizedCache(cache);
			if (blocking) {
				cache = new BlockingCache(cache);
			}
			return cache;
		} catch (Exception e) {
			throw new CacheException("Error building standard cache decorators.  Cause: " + e, e);
		}
	}

	/**
	 * 为缓存设置自定义属性（利用反射），自定义属性和缓存中的set方法必须匹配,只支持基本数据类型
	 *
	 * @param cache 缓存对象
	 */
	private void setCacheProperties(Cache cache) {
		if (properties != null) {
			MetaObject metaCache = SystemMetaObject.forObject(cache);
			for (Map.Entry<Object, Object> entry : properties.entrySet()) {
				String name = (String) entry.getKey();
				String value = (String) entry.getValue();
				if (metaCache.hasSetter(name)) {
					Class<?> type = metaCache.getSetterType(name);
					if (String.class == type) {
						metaCache.setValue(name, value);
					} else if (int.class == type
							|| Integer.class == type) {
						metaCache.setValue(name, Integer.valueOf(value));
					} else if (long.class == type
							|| Long.class == type) {
						metaCache.setValue(name, Long.valueOf(value));
					} else if (short.class == type
							|| Short.class == type) {
						metaCache.setValue(name, Short.valueOf(value));
					} else if (byte.class == type
							|| Byte.class == type) {
						metaCache.setValue(name, Byte.valueOf(value));
					} else if (float.class == type
							|| Float.class == type) {
						metaCache.setValue(name, Float.valueOf(value));
					} else if (boolean.class == type
							|| Boolean.class == type) {
						metaCache.setValue(name, Boolean.valueOf(value));
					} else if (double.class == type
							|| Double.class == type) {
						metaCache.setValue(name, Double.valueOf(value));
					} else {
						throw new CacheException("Unsupported property type for cache: '" + name + "' of type " + type);
					}
				}
			}
		}
	}

	/**
	 * 新建缓存实现
	 * 新建的缓存实现必须要保证，有一个参数为String的构造函数
	 *
	 * @param cacheClass 缓存实现类
	 * @param id         缓存绑定的命名空间
	 * @return 缓存对象
	 * @see #getBaseCacheConstructor(Class)
	 */
	private Cache newBaseCacheInstance(Class<? extends Cache> cacheClass, String id) {
		Constructor<? extends Cache> cacheConstructor = getBaseCacheConstructor(cacheClass);
		try {
			return cacheConstructor.newInstance(id);
		} catch (Exception e) {
			throw new CacheException("Could not instantiate cache implementation (" + cacheClass + "). Cause: " + e, e);
		}
	}

	/**
	 * 获取缓存构造函数
	 * <strong>必须要有一个参数为String的构造函数，否则会抛出异常</strong>
	 *
	 * @param cacheClass 缓存实现类
	 * @return 缓存实现类的构造函数
	 */
	private Constructor<? extends Cache> getBaseCacheConstructor(Class<? extends Cache> cacheClass) {
		try {
			return cacheClass.getConstructor(String.class);
		} catch (Exception e) {
			throw new CacheException("Invalid base cache implementation (" + cacheClass + ").  " +
					"Base cache implementations must have a constructor that takes a String id as a parameter.  Cause: " + e, e);
		}
	}

	private Cache newCacheDecoratorInstance(Class<? extends Cache> cacheClass, Cache base) {
		Constructor<? extends Cache> cacheConstructor = getCacheDecoratorConstructor(cacheClass);
		try {
			return cacheConstructor.newInstance(base);
		} catch (Exception e) {
			throw new CacheException("Could not instantiate cache decorator (" + cacheClass + "). Cause: " + e, e);
		}
	}

	/**
	 * 获取缓存装饰器
	 *
	 * @param cacheClass
	 * @return
	 */
	private Constructor<? extends Cache> getCacheDecoratorConstructor(Class<? extends Cache> cacheClass) {
		try {
			return cacheClass.getConstructor(Cache.class);
		} catch (Exception e) {
			throw new CacheException("Invalid cache decorator (" + cacheClass + ").  " +
					"Cache decorators must have a constructor that takes a Cache instance as a parameter.  Cause: " + e, e);
		}
	}
}
