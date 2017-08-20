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
package com.jd.edison.mybatis.builder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

import com.jd.edison.mybatis.mapping.MappedStatement;
import com.jd.edison.mybatis.mapping.ParameterMap;
import com.jd.edison.mybatis.mapping.SqlSource;
import com.jd.edison.mybatis.type.JdbcType;
import com.jd.edison.mybatis.type.TypeHandler;
import com.jd.edison.mybatis.cache.Cache;
import com.jd.edison.mybatis.cache.decorators.LruCache;
import com.jd.edison.mybatis.cache.impl.PerpetualCache;
import com.jd.edison.mybatis.executor.ErrorContext;
import com.jd.edison.mybatis.executor.keygen.KeyGenerator;
import com.jd.edison.mybatis.mapping.CacheBuilder;
import com.jd.edison.mybatis.mapping.Discriminator;
import com.jd.edison.mybatis.mapping.ParameterMapping;
import com.jd.edison.mybatis.mapping.ParameterMode;
import com.jd.edison.mybatis.mapping.ResultFlag;
import com.jd.edison.mybatis.mapping.ResultMap;
import com.jd.edison.mybatis.mapping.ResultMapping;
import com.jd.edison.mybatis.mapping.ResultSetType;
import com.jd.edison.mybatis.mapping.SqlCommandType;
import com.jd.edison.mybatis.mapping.StatementType;
import com.jd.edison.mybatis.reflection.MetaClass;
import com.jd.edison.mybatis.scripting.LanguageDriver;
import com.jd.edison.mybatis.session.Configuration;

/**
 * mapper xml关联解析
 *
 * @author Clinton Begin
 */
public class MapperBuilderAssistant extends BaseBuilder {

	private String currentNamespace;
	private String resource;
	private Cache currentCache;
	private boolean unresolvedCacheRef; // issue #676

	public MapperBuilderAssistant(Configuration configuration, String resource) {
		super(configuration);
		ErrorContext.instance().resource(resource);
		this.resource = resource;
	}

	public String getCurrentNamespace() {
		return currentNamespace;
	}

	public void setCurrentNamespace(String currentNamespace) {
		if (currentNamespace == null) {
			throw new BuilderException("The mapper element requires a namespace attribute to be specified.");
		}

		if (this.currentNamespace != null && !this.currentNamespace.equals(currentNamespace)) {
			throw new BuilderException("Wrong namespace. Expected '"
					+ this.currentNamespace + "' but found '" + currentNamespace + "'.");
		}

		this.currentNamespace = currentNamespace;
	}

	public String applyCurrentNamespace(String base, boolean isReference) {
		if (base == null) {
			return null;
		}
		if (isReference) {
			// is it qualified with any namespace yet?
			if (base.contains(".")) {
				return base;
			}
		} else {
			// is it qualified with this namespace yet?
			if (base.startsWith(currentNamespace + ".")) {
				return base;
			}
			if (base.contains(".")) {
				throw new BuilderException("Dots are not allowed in element names, please remove it from " + base);
			}
		}
		return currentNamespace + "." + base;
	}

	/**
	 * 使用参照缓存（使用其他命名空间中的缓存）
	 * 将本助理中的缓存替换为参照缓存
	 *
	 * @param namespace 参照缓存的命名空间
	 * @return 参照缓存
	 * @throws IncompleteElementException 当找不到目标缓存时会抛出该异常
	 */
	public Cache useCacheRef(String namespace) {
		if (namespace == null) {
			throw new BuilderException("cache-ref element requires a namespace attribute.");
		}
		try {
			unresolvedCacheRef = true;
			Cache cache = configuration.getCache(namespace);
			if (cache == null) {
				throw new IncompleteElementException("No cache for namespace '" + namespace + "' could be found.");
			}
			currentCache = cache;
			unresolvedCacheRef = false;
			return cache;
		} catch (IllegalArgumentException e) {
			throw new IncompleteElementException("No cache for namespace '" + namespace + "' could be found.", e);
		}
	}

	/**
	 * 使用新缓存
	 *
	 * @param typeClass		用户自定义的缓存实现
	 * @param evictionClass	回收算法
	 * @param flushInterval	过期时间
	 * @param size			缓存的对象数量
	 * @param readWrite		只读
	 * @param blocking
	 * @param props
	 * @return
	 */
	public Cache useNewCache(Class<? extends Cache> typeClass,
							 Class<? extends Cache> evictionClass,
							 Long flushInterval,
							 Integer size,
							 boolean readWrite,
							 boolean blocking,
							 Properties props) {
		//判断具体缓存类型
		typeClass = valueOrDefault(typeClass, PerpetualCache.class);
		//判断具体回收算法
		evictionClass = valueOrDefault(evictionClass, LruCache.class);

		Cache cache = new CacheBuilder(currentNamespace)
				.implementation(typeClass)
				.addDecorator(evictionClass)
				.clearInterval(flushInterval)
				.size(size)
				.readWrite(readWrite)
				.blocking(blocking)
				.properties(props)
				.build();
		configuration.addCache(cache);
		currentCache = cache;
		return cache;
	}

	public ParameterMap addParameterMap(String id, Class<?> parameterClass, List<ParameterMapping> parameterMappings) {
		id = applyCurrentNamespace(id, false);
		ParameterMap.Builder parameterMapBuilder = new ParameterMap.Builder(configuration, id, parameterClass, parameterMappings);
		ParameterMap parameterMap = parameterMapBuilder.build();
		configuration.addParameterMap(parameterMap);
		return parameterMap;
	}

	/**
	 * 构建参数映射
	 * @param parameterType
	 * @param property
	 * @param javaType
	 * @param jdbcType
	 * @param resultMap
	 * @param parameterMode
	 * @param typeHandler
	 * @param numericScale
	 * @return
	 */
	public ParameterMapping buildParameterMapping(
			Class<?> parameterType,
			String property,
			Class<?> javaType,
			JdbcType jdbcType,
			String resultMap,
			ParameterMode parameterMode,
			Class<? extends TypeHandler<?>> typeHandler,
			Integer numericScale) {
		resultMap = applyCurrentNamespace(resultMap, true);

		// Class parameterType = parameterMapBuilder.type();
		Class<?> javaTypeClass = resolveParameterJavaType(parameterType, property, javaType, jdbcType);
		TypeHandler<?> typeHandlerInstance = resolveTypeHandler(javaTypeClass, typeHandler);

		ParameterMapping.Builder builder = new ParameterMapping.Builder(configuration, property, javaTypeClass);
		builder.jdbcType(jdbcType);
		builder.resultMapId(resultMap);
		builder.mode(parameterMode);
		builder.numericScale(numericScale);
		builder.typeHandler(typeHandlerInstance);
		return builder.build();
	}

	public ResultMap addResultMap(
			String id,
			Class<?> type,
			String extend,
			Discriminator discriminator,
			List<ResultMapping> resultMappings,
			Boolean autoMapping) {
		id = applyCurrentNamespace(id, false);
		extend = applyCurrentNamespace(extend, true);

		ResultMap.Builder resultMapBuilder = new ResultMap.Builder(configuration, id, type, resultMappings, autoMapping);
		if (extend != null) {
			if (!configuration.hasResultMap(extend)) {
				throw new IncompleteElementException("Could not find a parent resultmap with id '" + extend + "'");
			}
			ResultMap resultMap = configuration.getResultMap(extend);
			List<ResultMapping> extendedResultMappings = new ArrayList<ResultMapping>(resultMap.getResultMappings());
			extendedResultMappings.removeAll(resultMappings);
			// Remove parent constructor if this resultMap declares a constructor.
			boolean declaresConstructor = false;
			for (ResultMapping resultMapping : resultMappings) {
				if (resultMapping.getFlags().contains(ResultFlag.CONSTRUCTOR)) {
					declaresConstructor = true;
					break;
				}
			}
			if (declaresConstructor) {
				Iterator<ResultMapping> extendedResultMappingsIter = extendedResultMappings.iterator();
				while (extendedResultMappingsIter.hasNext()) {
					if (extendedResultMappingsIter.next().getFlags().contains(ResultFlag.CONSTRUCTOR)) {
						extendedResultMappingsIter.remove();
					}
				}
			}
			resultMappings.addAll(extendedResultMappings);
		}
		resultMapBuilder.discriminator(discriminator);
		ResultMap resultMap = resultMapBuilder.build();
		configuration.addResultMap(resultMap);
		return resultMap;
	}

	public Discriminator buildDiscriminator(
			Class<?> resultType,
			String column,
			Class<?> javaType,
			JdbcType jdbcType,
			Class<? extends TypeHandler<?>> typeHandler,
			Map<String, String> discriminatorMap) {
		ResultMapping resultMapping = buildResultMapping(
				resultType,
				null,
				column,
				javaType,
				jdbcType,
				null,
				null,
				null,
				null,
				typeHandler,
				new ArrayList<ResultFlag>(),
				null,
				null,
				false);
		Map<String, String> namespaceDiscriminatorMap = new HashMap<String, String>();
		for (Map.Entry<String, String> e : discriminatorMap.entrySet()) {
			String resultMap = e.getValue();
			resultMap = applyCurrentNamespace(resultMap, true);
			namespaceDiscriminatorMap.put(e.getKey(), resultMap);
		}
		Discriminator.Builder discriminatorBuilder = new Discriminator.Builder(configuration, resultMapping, namespaceDiscriminatorMap);
		return discriminatorBuilder.build();
	}

	public MappedStatement addMappedStatement(
			String id,
			SqlSource sqlSource,
			StatementType statementType,
			SqlCommandType sqlCommandType,
			Integer fetchSize,
			Integer timeout,
			String parameterMap,
			Class<?> parameterType,
			String resultMap,
			Class<?> resultType,
			ResultSetType resultSetType,
			boolean flushCache,
			boolean useCache,
			boolean resultOrdered,
			KeyGenerator keyGenerator,
			String keyProperty,
			String keyColumn,
			String databaseId,
			LanguageDriver lang,
			String resultSets) {

		if (unresolvedCacheRef) {
			throw new IncompleteElementException("Cache-ref not yet resolved");
		}

		id = applyCurrentNamespace(id, false);
		boolean isSelect = sqlCommandType == SqlCommandType.SELECT;

		MappedStatement.Builder statementBuilder = new MappedStatement.Builder(configuration, id, sqlSource, sqlCommandType);
		statementBuilder.resource(resource);
		statementBuilder.fetchSize(fetchSize);
		statementBuilder.statementType(statementType);
		statementBuilder.keyGenerator(keyGenerator);
		statementBuilder.keyProperty(keyProperty);
		statementBuilder.keyColumn(keyColumn);
		statementBuilder.databaseId(databaseId);
		statementBuilder.lang(lang);
		statementBuilder.resultOrdered(resultOrdered);
		statementBuilder.resulSets(resultSets);
		setStatementTimeout(timeout, statementBuilder);

		setStatementParameterMap(parameterMap, parameterType, statementBuilder);
		setStatementResultMap(resultMap, resultType, resultSetType, statementBuilder);
		setStatementCache(isSelect, flushCache, useCache, currentCache, statementBuilder);

		MappedStatement statement = statementBuilder.build();
		configuration.addMappedStatement(statement);
		return statement;
	}

	/**
	 * 判断是使用自定义的缓存还是默认的缓存
	 *
	 * @param value        自定义缓存
	 * @param defaultValue 默认缓存
	 * @param <T>          缓存泛型
	 * @return 判断结果
	 */
	private <T> T valueOrDefault(T value, T defaultValue) {
		return value == null ? defaultValue : value;
	}

	private void setStatementCache(
			boolean isSelect,
			boolean flushCache,
			boolean useCache,
			Cache cache,
			MappedStatement.Builder statementBuilder) {
		flushCache = valueOrDefault(flushCache, !isSelect);
		useCache = valueOrDefault(useCache, isSelect);
		statementBuilder.flushCacheRequired(flushCache);
		statementBuilder.useCache(useCache);
		statementBuilder.cache(cache);
	}

	private void setStatementParameterMap(
			String parameterMap,
			Class<?> parameterTypeClass,
			MappedStatement.Builder statementBuilder) {
		parameterMap = applyCurrentNamespace(parameterMap, true);

		if (parameterMap != null) {
			try {
				statementBuilder.parameterMap(configuration.getParameterMap(parameterMap));
			} catch (IllegalArgumentException e) {
				throw new IncompleteElementException("Could not find parameter map " + parameterMap, e);
			}
		} else if (parameterTypeClass != null) {
			List<ParameterMapping> parameterMappings = new ArrayList<ParameterMapping>();
			ParameterMap.Builder inlineParameterMapBuilder = new ParameterMap.Builder(
					configuration,
					statementBuilder.id() + "-Inline",
					parameterTypeClass,
					parameterMappings);
			statementBuilder.parameterMap(inlineParameterMapBuilder.build());
		}
	}

	private void setStatementResultMap(
			String resultMap,
			Class<?> resultType,
			ResultSetType resultSetType,
			MappedStatement.Builder statementBuilder) {
		resultMap = applyCurrentNamespace(resultMap, true);

		List<ResultMap> resultMaps = new ArrayList<ResultMap>();
		if (resultMap != null) {
			String[] resultMapNames = resultMap.split(",");
			for (String resultMapName : resultMapNames) {
				try {
					resultMaps.add(configuration.getResultMap(resultMapName.trim()));
				} catch (IllegalArgumentException e) {
					throw new IncompleteElementException("Could not find result map " + resultMapName, e);
				}
			}
		} else if (resultType != null) {
			ResultMap.Builder inlineResultMapBuilder = new ResultMap.Builder(
					configuration,
					statementBuilder.id() + "-Inline",
					resultType,
					new ArrayList<ResultMapping>(),
					null);
			resultMaps.add(inlineResultMapBuilder.build());
		}
		statementBuilder.resultMaps(resultMaps);

		statementBuilder.resultSetType(resultSetType);
	}

	private void setStatementTimeout(Integer timeout, MappedStatement.Builder statementBuilder) {
		if (timeout == null) {
			timeout = configuration.getDefaultStatementTimeout();
		}
		statementBuilder.timeout(timeout);
	}

	public ResultMapping buildResultMapping(
			Class<?> resultType,
			String property,
			String column,
			Class<?> javaType,
			JdbcType jdbcType,
			String nestedSelect,
			String nestedResultMap,
			String notNullColumn,
			String columnPrefix,
			Class<? extends TypeHandler<?>> typeHandler,
			List<ResultFlag> flags,
			String resultSet,
			String foreignColumn,
			boolean lazy) {
		Class<?> javaTypeClass = resolveResultJavaType(resultType, property, javaType);
		TypeHandler<?> typeHandlerInstance = resolveTypeHandler(javaTypeClass, typeHandler);
		List<ResultMapping> composites = parseCompositeColumnName(column);
		if (composites.size() > 0) {
			column = null;
		}
		ResultMapping.Builder builder = new ResultMapping.Builder(configuration, property, column, javaTypeClass);
		builder.jdbcType(jdbcType);
		builder.nestedQueryId(applyCurrentNamespace(nestedSelect, true));
		builder.nestedResultMapId(applyCurrentNamespace(nestedResultMap, true));
		builder.resultSet(resultSet);
		builder.typeHandler(typeHandlerInstance);
		builder.flags(flags == null ? new ArrayList<ResultFlag>() : flags);
		builder.composites(composites);
		builder.notNullColumns(parseMultipleColumnNames(notNullColumn));
		builder.columnPrefix(columnPrefix);
		builder.foreignColumn(foreignColumn);
		builder.lazy(lazy);
		return builder.build();
	}

	private Set<String> parseMultipleColumnNames(String columnName) {
		Set<String> columns = new HashSet<String>();
		if (columnName != null) {
			if (columnName.indexOf(',') > -1) {
				StringTokenizer parser = new StringTokenizer(columnName, "{}, ", false);
				while (parser.hasMoreTokens()) {
					String column = parser.nextToken();
					columns.add(column);
				}
			} else {
				columns.add(columnName);
			}
		}
		return columns;
	}

	private List<ResultMapping> parseCompositeColumnName(String columnName) {
		List<ResultMapping> composites = new ArrayList<ResultMapping>();
		if (columnName != null && (columnName.indexOf('=') > -1 || columnName.indexOf(',') > -1)) {
			StringTokenizer parser = new StringTokenizer(columnName, "{}=, ", false);
			while (parser.hasMoreTokens()) {
				String property = parser.nextToken();
				String column = parser.nextToken();
				ResultMapping.Builder complexBuilder = new ResultMapping.Builder(configuration, property, column, configuration.getTypeHandlerRegistry().getUnknownTypeHandler());
				composites.add(complexBuilder.build());
			}
		}
		return composites;
	}

	private Class<?> resolveResultJavaType(Class<?> resultType, String property, Class<?> javaType) {
		if (javaType == null && property != null) {
			try {
				MetaClass metaResultType = MetaClass.forClass(resultType, configuration.getReflectorFactory());
				javaType = metaResultType.getSetterType(property);
			} catch (Exception e) {
				//ignore, following null check statement will deal with the situation
			}
		}
		if (javaType == null) {
			javaType = Object.class;
		}
		return javaType;
	}

	private Class<?> resolveParameterJavaType(Class<?> resultType, String property, Class<?> javaType, JdbcType jdbcType) {
		if (javaType == null) {
			if (JdbcType.CURSOR.equals(jdbcType)) {
				javaType = java.sql.ResultSet.class;
			} else if (Map.class.isAssignableFrom(resultType)) {
				javaType = Object.class;
			} else {
				MetaClass metaResultType = MetaClass.forClass(resultType, configuration.getReflectorFactory());
				javaType = metaResultType.getGetterType(property);
			}
		}
		if (javaType == null) {
			javaType = Object.class;
		}
		return javaType;
	}

	/**
	 * Backward compatibility signature
	 */
	public ResultMapping buildResultMapping(
			Class<?> resultType,
			String property,
			String column,
			Class<?> javaType,
			JdbcType jdbcType,
			String nestedSelect,
			String nestedResultMap,
			String notNullColumn,
			String columnPrefix,
			Class<? extends TypeHandler<?>> typeHandler,
			List<ResultFlag> flags) {
		return buildResultMapping(
				resultType, property, column, javaType, jdbcType, nestedSelect,
				nestedResultMap, notNullColumn, columnPrefix, typeHandler, flags, null, null, configuration.isLazyLoadingEnabled());
	}

	public LanguageDriver getLanguageDriver(Class<?> langClass) {
		if (langClass != null) {
			configuration.getLanguageRegistry().register(langClass);
		} else {
			langClass = configuration.getLanguageRegistry().getDefaultDriverClass();
		}
		return configuration.getLanguageRegistry().getDriver(langClass);
	}

	/**
	 * Backward compatibility signature
	 */
	public MappedStatement addMappedStatement(
			String id,
			SqlSource sqlSource,
			StatementType statementType,
			SqlCommandType sqlCommandType,
			Integer fetchSize,
			Integer timeout,
			String parameterMap,
			Class<?> parameterType,
			String resultMap,
			Class<?> resultType,
			ResultSetType resultSetType,
			boolean flushCache,
			boolean useCache,
			boolean resultOrdered,
			KeyGenerator keyGenerator,
			String keyProperty,
			String keyColumn,
			String databaseId,
			LanguageDriver lang) {
		return addMappedStatement(
				id, sqlSource, statementType, sqlCommandType, fetchSize, timeout,
				parameterMap, parameterType, resultMap, resultType, resultSetType,
				flushCache, useCache, resultOrdered, keyGenerator, keyProperty,
				keyColumn, databaseId, lang, null);
	}

}
