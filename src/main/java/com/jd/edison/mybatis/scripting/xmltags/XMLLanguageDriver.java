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
package com.jd.edison.mybatis.scripting.xmltags;

import com.jd.edison.mybatis.builder.xml.XMLMapperEntityResolver;
import com.jd.edison.mybatis.executor.parameter.ParameterHandler;
import com.jd.edison.mybatis.mapping.BoundSql;
import com.jd.edison.mybatis.mapping.MappedStatement;
import com.jd.edison.mybatis.mapping.SqlSource;
import com.jd.edison.mybatis.parsing.PropertyParser;
import com.jd.edison.mybatis.parsing.XNode;
import com.jd.edison.mybatis.scripting.defaults.DefaultParameterHandler;
import com.jd.edison.mybatis.scripting.defaults.RawSqlSource;
import com.jd.edison.mybatis.session.Configuration;
import com.jd.edison.mybatis.parsing.XPathParser;
import com.jd.edison.mybatis.scripting.LanguageDriver;

/**
 * @author Eduardo Macarron
 */
public class XMLLanguageDriver implements LanguageDriver {

  @Override
  public ParameterHandler createParameterHandler(MappedStatement mappedStatement, Object parameterObject, BoundSql boundSql) {
    return new DefaultParameterHandler(mappedStatement, parameterObject, boundSql);
  }

  @Override
  public SqlSource createSqlSource(Configuration configuration, XNode script, Class<?> parameterType) {
    XMLScriptBuilder builder = new XMLScriptBuilder(configuration, script, parameterType);
    return builder.parseScriptNode();
  }

  @Override
  public SqlSource createSqlSource(Configuration configuration, String script, Class<?> parameterType) {
    // issue #3
    if (script.startsWith("<script>")) {
      XPathParser parser = new XPathParser(script, false, configuration.getVariables(), new XMLMapperEntityResolver());
      return createSqlSource(configuration, parser.evalNode("/script"), parameterType);
    } else {
      // issue #127
      script = PropertyParser.parse(script, configuration.getVariables());
      TextSqlNode textSqlNode = new TextSqlNode(script);
      if (textSqlNode.isDynamic()) {
        return new DynamicSqlSource(configuration, textSqlNode);
      } else {
        return new RawSqlSource(configuration, script, parameterType);
      }
    }
  }

}