/**
 *    Copyright 2009-2015 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package com.jd.mybatis.jdbc;

import com.jd.mybatis.type.BigDecimalTypeHandler;
import com.jd.mybatis.type.BlobTypeHandler;
import com.jd.mybatis.type.BooleanTypeHandler;
import com.jd.mybatis.type.ByteArrayTypeHandler;
import com.jd.mybatis.type.ByteTypeHandler;
import com.jd.mybatis.type.ClobTypeHandler;
import com.jd.mybatis.type.DateOnlyTypeHandler;
import com.jd.mybatis.type.DateTypeHandler;
import com.jd.mybatis.type.DoubleTypeHandler;
import com.jd.mybatis.type.FloatTypeHandler;
import com.jd.mybatis.type.IntegerTypeHandler;
import com.jd.mybatis.type.JdbcType;
import com.jd.mybatis.type.LongTypeHandler;
import com.jd.mybatis.type.ObjectTypeHandler;
import com.jd.mybatis.type.ShortTypeHandler;
import com.jd.mybatis.type.SqlDateTypeHandler;
import com.jd.mybatis.type.SqlTimeTypeHandler;
import com.jd.mybatis.type.SqlTimestampTypeHandler;
import com.jd.mybatis.type.StringTypeHandler;
import com.jd.mybatis.type.TimeOnlyTypeHandler;
import com.jd.mybatis.type.TypeHandler;

/**
 * @author Clinton Begin
 * @author Adam Gent
 */
public enum Null {
  BOOLEAN(new BooleanTypeHandler(), JdbcType.BOOLEAN),

  BYTE(new ByteTypeHandler(), JdbcType.TINYINT),
  SHORT(new ShortTypeHandler(), JdbcType.SMALLINT),
  INTEGER(new IntegerTypeHandler(), JdbcType.INTEGER),
  LONG(new LongTypeHandler(), JdbcType.BIGINT),
  FLOAT(new FloatTypeHandler(), JdbcType.FLOAT),
  DOUBLE(new DoubleTypeHandler(), JdbcType.DOUBLE),
  BIGDECIMAL(new BigDecimalTypeHandler(), JdbcType.DECIMAL),

  STRING(new StringTypeHandler(), JdbcType.VARCHAR),
  CLOB(new ClobTypeHandler(), JdbcType.CLOB),
  LONGVARCHAR(new ClobTypeHandler(), JdbcType.LONGVARCHAR),

  BYTEARRAY(new ByteArrayTypeHandler(), JdbcType.LONGVARBINARY),
  BLOB(new BlobTypeHandler(), JdbcType.BLOB),
  LONGVARBINARY(new BlobTypeHandler(), JdbcType.LONGVARBINARY),

  OBJECT(new ObjectTypeHandler(), JdbcType.OTHER),
  OTHER(new ObjectTypeHandler(), JdbcType.OTHER),
  TIMESTAMP(new DateTypeHandler(), JdbcType.TIMESTAMP),
  DATE(new DateOnlyTypeHandler(), JdbcType.DATE),
  TIME(new TimeOnlyTypeHandler(), JdbcType.TIME),
  SQLTIMESTAMP(new SqlTimestampTypeHandler(), JdbcType.TIMESTAMP),
  SQLDATE(new SqlDateTypeHandler(), JdbcType.DATE),
  SQLTIME(new SqlTimeTypeHandler(), JdbcType.TIME);

  private TypeHandler<?> typeHandler;
  private JdbcType jdbcType;

  private Null(TypeHandler<?> typeHandler, JdbcType jdbcType) {
    this.typeHandler = typeHandler;
    this.jdbcType = jdbcType;
  }

  public TypeHandler<?> getTypeHandler() {
    return typeHandler;
  }

  public JdbcType getJdbcType() {
    return jdbcType;
  }
}
