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
package com.jd.edison.mybatis.builder.xml;

import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;

import javax.sql.DataSource;

import com.jd.edison.mybatis.type.JdbcType;
import com.jd.edison.mybatis.builder.BaseBuilder;
import com.jd.edison.mybatis.builder.BuilderException;
import com.jd.edison.mybatis.datasource.DataSourceFactory;
import com.jd.edison.mybatis.executor.ErrorContext;
import com.jd.edison.mybatis.executor.loader.ProxyFactory;
import com.jd.edison.mybatis.io.Resources;
import com.jd.edison.mybatis.mapping.DatabaseIdProvider;
import com.jd.edison.mybatis.mapping.Environment;
import com.jd.edison.mybatis.parsing.XNode;
import com.jd.edison.mybatis.parsing.XPathParser;
import com.jd.edison.mybatis.plugin.Interceptor;
import com.jd.edison.mybatis.reflection.DefaultReflectorFactory;
import com.jd.edison.mybatis.reflection.MetaClass;
import com.jd.edison.mybatis.reflection.ReflectorFactory;
import com.jd.edison.mybatis.reflection.factory.ObjectFactory;
import com.jd.edison.mybatis.reflection.wrapper.ObjectWrapperFactory;
import com.jd.edison.mybatis.session.AutoMappingBehavior;
import com.jd.edison.mybatis.session.Configuration;
import com.jd.edison.mybatis.session.ExecutorType;
import com.jd.edison.mybatis.session.LocalCacheScope;
import com.jd.edison.mybatis.transaction.TransactionFactory;

/**
 * XMLConfigBuilder ：负责将mybatis-config.xml配置文件解析成Configuration对象，
 * 共SqlSessonFactoryBuilder使用，创建SqlSessionFactory
 * @author Clinton Begin
 */
public class XMLConfigBuilder extends BaseBuilder {

  private boolean parsed;
  private XPathParser parser;
  private String environment;
  private ReflectorFactory localReflectorFactory = new DefaultReflectorFactory();

  public XMLConfigBuilder(Reader reader) {
    this(reader, null, null);
  }

  public XMLConfigBuilder(Reader reader, String environment) {
    this(reader, environment, null);
  }
  /**
   * 1，将XML配置文件的信息转换为Document对象，
   * 2，XML配置定义文件DTD转换成XMLMapperEntityResolver对象，
   * 3，将二者封装到XpathParser对象中，XpathParser的作用是提供根据Xpath表达式获取基本的DOM节点Node信息的操作
   * @param reader
   * @param environment
   * @param props
   */
  public XMLConfigBuilder(Reader reader, String environment, Properties props) {
    this(new XPathParser(reader, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  public XMLConfigBuilder(InputStream inputStream) {
    this(inputStream, null, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment) {
    this(inputStream, environment, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment, Properties props) {
	  //XML配置定义文件DTD转换成XMLMapperEntityResolver对象
    this(new XPathParser(inputStream, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  private XMLConfigBuilder(XPathParser parser, String environment, Properties props) {
    super(new Configuration());
    ErrorContext.instance().resource("SQL Mapper Configuration");
    this.configuration.setVariables(props);
    this.parsed = false;
    this.environment = environment;
    this.parser = parser;
  }

  /**
   * 2.  之后XMLConfigBuilder调用parse()方法：会从XPathParser中取出 <configuration>节点对应的Node对象，
   * 然后解析此Node节点的子Node：properties, settings, typeAliases,typeHandlers,
   *  objectFactory, objectWrapperFactory, plugins, environments,databaseIdProvider, mappers
   * @return
   */
  public Configuration parse() {
    if (parsed) {
      throw new BuilderException("Each XMLConfigBuilder can only be used once.");
    }
    parsed = true;
    //1，解析config节点
    XNode configNode = parser.evalNode("/configuration");
    //2，解析config下的所有子节点
    parseConfiguration(configNode);
    return configuration;
  }

  /**
   * 解析Configuration节点
   * @param root        父节点
   */
  private void parseConfiguration(XNode root) {
    try {
      //issue #117 read properties first
      //1.首先处理properties 节点     
      propertiesElement(root.evalNode("properties"));
      //2.处理typeAliases  
      typeAliasesElement(root.evalNode("typeAliases"));
      //3.处理插件  
      pluginElement(root.evalNode("plugins"));
      //4.处理objectFactory  
      objectFactoryElement(root.evalNode("objectFactory"));
      //5.objectWrapperFactory  
      objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
      
      reflectionFactoryElement(root.evalNode("reflectionFactory"));
      //6.settings  
      settingsElement(root.evalNode("settings"));
      // read it after objectFactory and objectWrapperFactory issue #631
      //7.处理environments  
      environmentsElement(root.evalNode("environments"));
      //8.database  
      databaseIdProviderElement(root.evalNode("databaseIdProvider"));
      //9. typeHandlers  
      typeHandlerElement(root.evalNode("typeHandlers"));
      //10 mappers  
      mapperElement(root.evalNode("mappers"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
    }
  }

  private void typeAliasesElement(XNode parent) {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        if ("package".equals(child.getName())) {
          String typeAliasPackage = child.getStringAttribute("name");
          configuration.getTypeAliasRegistry().registerAliases(typeAliasPackage);
        } else {
          String alias = child.getStringAttribute("alias");
          String type = child.getStringAttribute("type");
          try {
            Class<?> clazz = Resources.classForName(type);
            if (alias == null) {
              typeAliasRegistry.registerAlias(clazz);
            } else {
              typeAliasRegistry.registerAlias(alias, clazz);
            }
          } catch (ClassNotFoundException e) {
            throw new BuilderException("Error registering typeAlias for '" + alias + "'. Cause: " + e, e);
          }
        }
      }
    }
  }

  private void pluginElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        String interceptor = child.getStringAttribute("interceptor");
        Properties properties = child.getChildrenAsProperties();
        Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).newInstance();
        interceptorInstance.setProperties(properties);
        configuration.addInterceptor(interceptorInstance);
      }
    }
  }

  private void objectFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties properties = context.getChildrenAsProperties();
      ObjectFactory factory = (ObjectFactory) resolveClass(type).newInstance();
      factory.setProperties(properties);
      configuration.setObjectFactory(factory);
    }
  }

  private void objectWrapperFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      ObjectWrapperFactory factory = (ObjectWrapperFactory) resolveClass(type).newInstance();
      configuration.setObjectWrapperFactory(factory);
    }
  }

  private void reflectionFactoryElement(XNode context) throws Exception {
    if (context != null) {
       String type = context.getStringAttribute("type");
       ReflectorFactory factory = (ReflectorFactory) resolveClass(type).newInstance();
       configuration.setReflectorFactory(factory);
    }
  }

  /**
   * 读取Properties节点信息
   * <p>1，resource属性和url属性不可同时存在</p>
   * <p>2，</p>
   * @param context       父节点
   * @throws Exception    读取失败时会抛出异常
   */
  private void propertiesElement(XNode context) throws Exception {
    if (context != null) {
      //默认参数
      Properties defaults = context.getChildrenAsProperties();
      String resource = context.getStringAttribute("resource");
      String url = context.getStringAttribute("url");
      if (resource != null && url != null) {
        throw new BuilderException("The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
      }
      //将配置文件或URL中的配置信息放置到默认参数中
      if (resource != null) {
        defaults.putAll(Resources.getResourceAsProperties(resource));
      } else if (url != null) {
        defaults.putAll(Resources.getUrlAsProperties(url));
      }
      //如果配置文件中有默认的参数信息则也一并放入默认参数中
      Properties vars = configuration.getVariables();
      if (vars != null) {
        defaults.putAll(vars);
      }
      parser.setVariables(defaults);
      configuration.setVariables(defaults);
    }
  }

  private void settingsElement(XNode context) throws Exception {
    if (context != null) {
      Properties props = context.getChildrenAsProperties();
      // Check that all settings are known to the configuration class
      MetaClass metaConfig = MetaClass.forClass(Configuration.class, localReflectorFactory);
      for (Object key : props.keySet()) {
        if (!metaConfig.hasSetter(String.valueOf(key))) {
          throw new BuilderException("The setting " + key + " is not known.  Make sure you spelled it correctly (case sensitive).");
        }
      }
      configuration.setAutoMappingBehavior(AutoMappingBehavior.valueOf(props.getProperty("autoMappingBehavior", "PARTIAL")));
      configuration.setCacheEnabled(booleanValueOf(props.getProperty("cacheEnabled"), true));
      configuration.setProxyFactory((ProxyFactory) createInstance(props.getProperty("proxyFactory")));
      configuration.setLazyLoadingEnabled(booleanValueOf(props.getProperty("lazyLoadingEnabled"), false));
      configuration.setAggressiveLazyLoading(booleanValueOf(props.getProperty("aggressiveLazyLoading"), true));
      configuration.setMultipleResultSetsEnabled(booleanValueOf(props.getProperty("multipleResultSetsEnabled"), true));
      configuration.setUseColumnLabel(booleanValueOf(props.getProperty("useColumnLabel"), true));
      configuration.setUseGeneratedKeys(booleanValueOf(props.getProperty("useGeneratedKeys"), false));
      configuration.setDefaultExecutorType(ExecutorType.valueOf(props.getProperty("defaultExecutorType", "SIMPLE")));
      configuration.setDefaultStatementTimeout(integerValueOf(props.getProperty("defaultStatementTimeout"), null));
      configuration.setDefaultFetchSize(integerValueOf(props.getProperty("defaultFetchSize"), null));
      configuration.setMapUnderscoreToCamelCase(booleanValueOf(props.getProperty("mapUnderscoreToCamelCase"), false));
      configuration.setSafeRowBoundsEnabled(booleanValueOf(props.getProperty("safeRowBoundsEnabled"), false));
      configuration.setLocalCacheScope(LocalCacheScope.valueOf(props.getProperty("localCacheScope", "SESSION")));
      configuration.setJdbcTypeForNull(JdbcType.valueOf(props.getProperty("jdbcTypeForNull", "OTHER")));
      configuration.setLazyLoadTriggerMethods(stringSetValueOf(props.getProperty("lazyLoadTriggerMethods"), "equals,clone,hashCode,toString"));
      configuration.setSafeResultHandlerEnabled(booleanValueOf(props.getProperty("safeResultHandlerEnabled"), true));
      configuration.setDefaultScriptingLanguage(resolveClass(props.getProperty("defaultScriptingLanguage")));
      configuration.setCallSettersOnNulls(booleanValueOf(props.getProperty("callSettersOnNulls"), false));
      configuration.setLogPrefix(props.getProperty("logPrefix"));
      configuration.setLogImpl(resolveClass(props.getProperty("logImpl")));
      configuration.setConfigurationFactory(resolveClass(props.getProperty("configurationFactory")));
    }
  }

  /**
   * 解析environments节点，并将结果设置到Configuration对象中   
   * 注意：创建envronment时，如果SqlSessionFactoryBuilder指定了特定的环境（即数据源）；  则返回指定环境（数据源）的Environment对象，否则返回默认的Environment对象； 
   * 这种方式实现了MyBatis可以连接多数据源
   * Builder模式应用2： 数据库连接环境Environment对象的创建
   * @param context
   * @throws Exception
   */
  private void environmentsElement(XNode context) throws Exception {
    if (context != null) {
      if (environment == null) {
        environment = context.getStringAttribute("default");
      }
      for (XNode child : context.getChildren()) {
        String id = child.getStringAttribute("id");
        if (isSpecifiedEnvironment(id)) { //是和默认的环境相同时，解析之  
          //1.创建事务工厂 TransactionFactory  
          TransactionFactory txFactory = transactionManagerElement(child.evalNode("transactionManager"));
          DataSourceFactory dsFactory = dataSourceElement(child.evalNode("dataSource"));
          //2.创建数据源DataSource  
          DataSource dataSource = dsFactory.getDataSource();
          //使用了Environment内置的构造器Builder，传递id、 事务工厂和数据源 
          //3. 构造Environment对象 
          Environment.Builder environmentBuilder = new Environment.Builder(id)
              .transactionFactory(txFactory)
              .dataSource(dataSource);
          //4. 将创建的Envronment对象设置到configuration 对象中 
          configuration.setEnvironment(environmentBuilder.build());
        }
      }
    }
  }

  private void databaseIdProviderElement(XNode context) throws Exception {
    DatabaseIdProvider databaseIdProvider = null;
    if (context != null) {
      String type = context.getStringAttribute("type");
      // awful patch to keep backward compatibility
      if ("VENDOR".equals(type)) {
          type = "DB_VENDOR";
      }
      Properties properties = context.getChildrenAsProperties();
      databaseIdProvider = (DatabaseIdProvider) resolveClass(type).newInstance();
      databaseIdProvider.setProperties(properties);
    }
    Environment environment = configuration.getEnvironment();
    if (environment != null && databaseIdProvider != null) {
      String databaseId = databaseIdProvider.getDatabaseId(environment.getDataSource());
      configuration.setDatabaseId(databaseId);
    }
  }

  /**
   *  解析<transactionManager>节点，创建对应的TransactionFactory 
   *  <environment>节点定义了连接某个数据库的信息，
   *  其子节点<transactionManager> 的type 会决定我们用什么类型的事务管理机制。
   * @param context
   * @return
   * @throws Exception
   */
  private TransactionFactory transactionManagerElement(XNode context) throws Exception {
    if (context != null) {
      //type=JDBC,使用jdbc管理事务；type=MANAGED,使用WEB容器（Tomcat、JBOSS）管理事务
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
      /*在Configuration初始化的时候，会通过以下语句，给JDBC和MANAGED对应的工厂类 
      typeAliasRegistry.registerAlias("JDBC", JdbcTransactionFactory.class); 
      typeAliasRegistry.registerAlias("MANAGED", ManagedTransactionFactory.class); 
      下述的resolveClass(type).newInstance()会创建对应的工厂实例 */
      TransactionFactory factory = (TransactionFactory) resolveClass(type).newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a TransactionFactory.");
  }

  private DataSourceFactory dataSourceElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
      DataSourceFactory factory = (DataSourceFactory) resolveClass(type).newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a DataSourceFactory.");
  }

  private void typeHandlerElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        if ("package".equals(child.getName())) {
          String typeHandlerPackage = child.getStringAttribute("name");
          typeHandlerRegistry.register(typeHandlerPackage);
        } else {
          String javaTypeName = child.getStringAttribute("javaType");
          String jdbcTypeName = child.getStringAttribute("jdbcType");
          String handlerTypeName = child.getStringAttribute("handler");
          Class<?> javaTypeClass = resolveClass(javaTypeName);
          JdbcType jdbcType = resolveJdbcType(jdbcTypeName);
          Class<?> typeHandlerClass = resolveClass(handlerTypeName);
          if (javaTypeClass != null) {
            if (jdbcType == null) {
              typeHandlerRegistry.register(javaTypeClass, typeHandlerClass);
            } else {
              typeHandlerRegistry.register(javaTypeClass, jdbcType, typeHandlerClass);
            }
          } else {
            typeHandlerRegistry.register(typeHandlerClass);
          }
        }
      }
    }
  }

  private void mapperElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
    	  //1.<package name="org.mybatis.builder"/> Register all interfaces in a package as mappers
        if ("package".equals(child.getName())) {
          String mapperPackage = child.getStringAttribute("name");
          configuration.addMappers(mapperPackage);
        } else {
          String resource = child.getStringAttribute("resource");
          String url = child.getStringAttribute("url");
          String mapperClass = child.getStringAttribute("class");
          //2.<mapper resource="org/mybatis/builder/AuthorMapper.xml"/> Using classpath relative resources
          if (resource != null && url == null && mapperClass == null) {
            ErrorContext.instance().resource(resource);
            InputStream inputStream = Resources.getResourceAsStream(resource);
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments());
            mapperParser.parse();
          //3.<mapper url="file:///var/mappers/AuthorMapper.xml"/> Using url fully qualified paths
          } else if (resource == null && url != null && mapperClass == null) {
            ErrorContext.instance().resource(url);
            InputStream inputStream = Resources.getUrlAsStream(url);
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url, configuration.getSqlFragments());
            mapperParser.parse();
            //4.<mapper class="org.mybatis.builder.AuthorMapper"/> Using mapper interface classes
          } else if (resource == null && url == null && mapperClass != null) {
            Class<?> mapperInterface = Resources.classForName(mapperClass);
            configuration.addMapper(mapperInterface);
          } else {
            throw new BuilderException("A mapper element may only specify a url, resource or class, but not more than one.");
          }
        }
      }
    }
  }

  private boolean isSpecifiedEnvironment(String id) {
    if (environment == null) {
      throw new BuilderException("No environment specified.");
    } else if (id == null) {
      throw new BuilderException("Environment requires an id attribute.");
    } else if (environment.equals(id)) {
      return true;
    }
    return false;
  }

}
