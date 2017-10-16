package com.jd.mybatis;

import com.jd.mybatis.cache.Cache;

import java.util.concurrent.locks.ReadWriteLock;

/**
 * @author 田继东 on 2017/6/9.
 */
public class Test implements Cache {
	@Override
	public String getId() {
		return null;
	}

	@Override
	public void putObject(Object key, Object value) {

	}

	@Override
	public Object getObject(Object key) {
		return null;
	}

	@Override
	public Object removeObject(Object key) {
		return null;
	}

	@Override
	public void clear() {

	}

	@Override
	public int getSize() {
		return 0;
	}

	@Override
	public ReadWriteLock getReadWriteLock() {
		return null;
	}
}
