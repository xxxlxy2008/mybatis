/**
 * Copyright 2009-2020 the original author or authors.
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
package org.apache.ibatis.builder.xml;

import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.builder.CacheRefResolver;
import org.apache.ibatis.builder.IncompleteElementException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.builder.ResultMapResolver;
import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Discriminator;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.ResultFlag;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XMLMapperBuilder extends BaseBuilder {

    private final XPathParser parser;
    private final MapperBuilderAssistant builderAssistant;
    private final Map<String, XNode> sqlFragments;
    private final String resource;

    @Deprecated
    public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
        this(reader, configuration, resource, sqlFragments);
        this.builderAssistant.setCurrentNamespace(namespace);
    }

    @Deprecated
    public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
        this(new XPathParser(reader, true, configuration.getVariables(), new XMLMapperEntityResolver()),
                configuration, resource, sqlFragments);
    }

    public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
        this(inputStream, configuration, resource, sqlFragments);
        this.builderAssistant.setCurrentNamespace(namespace);
    }

    public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
        this(new XPathParser(inputStream, true, configuration.getVariables(), new XMLMapperEntityResolver()),
                configuration, resource, sqlFragments);
    }

    private XMLMapperBuilder(XPathParser parser, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
        super(configuration);
        this.builderAssistant = new MapperBuilderAssistant(configuration, resource);
        this.parser = parser;
        this.sqlFragments = sqlFragments;
        this.resource = resource;
    }

    public void parse() {
        // 检测MyBatis是否已经加载过当前的Mapper映射文件
        if (!configuration.isResourceLoaded(resource)) {
            // 解析整个Mapper映射文件的内容
            configurationElement(parser.evalNode("/mapper"));
            // 如果是首次加载的话，会将Mapper映射文件的地址添加
            // 到Configuration.loadedResources集合(HashSet<String>)中保存，
            // 用于后续判重
            configuration.addLoadedResource(resource);
            // 获取当前Mapper映射文件指定的Mapper接口，并进行注册
            bindMapperForNamespace();
        }
        // 处理configurationElement()方法中解析失败的<resultMap>节点
        parsePendingResultMaps();
        // 处理configurationElement()方法中解析失败的<cache-ref>节点
        parsePendingCacheRefs();
        // 处理configurationElement()方法中解析失败的SQL语句节点
        parsePendingStatements();
    }

    public XNode getSqlFragment(String refid) {
        return sqlFragments.get(refid);
    }

    private void configurationElement(XNode context) {
        try {
            // 获取<mapper>标签中的namespace属性，同时会进行边界检查
            String namespace = context.getStringAttribute("namespace");
            if (namespace == null || namespace.isEmpty()) {
                throw new BuilderException("Mapper's namespace cannot be empty");
            }
            // 使用MapperBuilderAssistant的currentNamespace字段记录当前namespace值
            builderAssistant.setCurrentNamespace(namespace);
            // 解析<cache-ref>标签
            cacheRefElement(context.evalNode("cache-ref"));
            // 解析<cache>节点
            cacheElement(context.evalNode("cache"));
            // 解析<parameterMap>节点（该节点已废弃，不再推荐使用，不做详细介绍）
            parameterMapElement(context.evalNodes("/mapper/parameterMap"));
            // 解析<resultMap>节点
            resultMapElements(context.evalNodes("/mapper/resultMap"));
            // 解析<sql>节点
            sqlElement(context.evalNodes("/mapper/sql"));
            // 解析<select>、<insert>、<update>、<delete>等SQL节点
            buildStatementFromContext(context.evalNodes("select|insert|update|delete"));
        } catch (Exception e) {
            throw new BuilderException("Error parsing Mapper XML. The XML location is '" + resource + "'. Cause: " + e, e);
        }
    }

    private void buildStatementFromContext(List<XNode> list) {
        if (configuration.getDatabaseId() != null) {
            buildStatementFromContext(list, configuration.getDatabaseId());
        }
        buildStatementFromContext(list, null);
    }

    private void buildStatementFromContext(List<XNode> list, String requiredDatabaseId) {
        for (XNode context : list) {
            final XMLStatementBuilder statementParser = new XMLStatementBuilder(configuration, builderAssistant, context, requiredDatabaseId);
            try {
                statementParser.parseStatementNode();
            } catch (IncompleteElementException e) {
                configuration.addIncompleteStatement(statementParser);
            }
        }
    }

    private void parsePendingResultMaps() {
        Collection<ResultMapResolver> incompleteResultMaps = configuration.getIncompleteResultMaps();
        synchronized (incompleteResultMaps) {
            Iterator<ResultMapResolver> iter = incompleteResultMaps.iterator();
            while (iter.hasNext()) {
                try {
                    iter.next().resolve();
                    iter.remove();
                } catch (IncompleteElementException e) {
                    // ResultMap is still missing a resource...
                }
            }
        }
    }

    private void parsePendingCacheRefs() {
        Collection<CacheRefResolver> incompleteCacheRefs = configuration.getIncompleteCacheRefs();
        synchronized (incompleteCacheRefs) {
            Iterator<CacheRefResolver> iter = incompleteCacheRefs.iterator();
            while (iter.hasNext()) {
                try {
                    iter.next().resolveCacheRef();
                    iter.remove();
                } catch (IncompleteElementException e) {
                    // Cache ref is still missing a resource...
                }
            }
        }
    }

    private void parsePendingStatements() {
        // 获取incompleteStatements集合中记录的未成功解析的SQL语句
        Collection<XMLStatementBuilder> incompleteStatements =
                configuration.getIncompleteStatements();
        synchronized (incompleteStatements) { // 加锁，防止并发
            // 遍历incompleteStatements集合，逐个重新解析
            Iterator<XMLStatementBuilder> iter = incompleteStatements.iterator();
            while (iter.hasNext()) {
                try {
                    // 通过parseStatementNode()方法重新走一遍解析流程
                    iter.next().parseStatementNode();
                    // 解析成功之后，从incompleteStatements集合中清理该XMLStatementBuilder对象
                    iter.remove();
                } catch (IncompleteElementException e) {
                    // 此时依旧无法成功解析SQL语句，则忽略该SQL
                }
            }
        }
    }

    private void cacheRefElement(XNode context) {
        if (context != null) {
            // 当前namespace与被引用namespace的关联关系，记录到cacheRefMap集合中
            configuration.addCacheRef(builderAssistant.getCurrentNamespace(), context.getStringAttribute("namespace"));
            // 创建CacheRefResolver对象
            CacheRefResolver cacheRefResolver =
                    new CacheRefResolver(builderAssistant,
                            context.getStringAttribute("namespace"));
            try {
                // 解析Cache引用
                cacheRefResolver.resolveCacheRef();
            } catch (IncompleteElementException e) {
                // 如果解析过程出现异常，则暂时将CacheRefResolver对象记录到
                // Configuration.incompleteCacheRefs集合，后续会重新解析
                configuration.addIncompleteCacheRef(cacheRefResolver);
            }
        }
    }

    private void cacheElement(XNode context) {
        if (context != null) {
            // 获取<cache>节点的type属性，默认值是PERPETUAL
            String type = context.getStringAttribute("type", "PERPETUAL");
            // 查找type属性对应的Cache接口实现，TypeAliasRegistry的实现前面介绍过了，不再赘述
            Class<? extends Cache> typeClass = typeAliasRegistry.resolveAlias(type);
            // 获取<cache>节点的eviction属性，默认值是LRU
            String eviction = context.getStringAttribute("eviction", "LRU");
            // 解析eviction属性指定的Cache装饰器类型
            Class<? extends Cache> evictionClass = typeAliasRegistry.resolveAlias(eviction);
            // 获取<cache>节点的flushInterval属性，默认值是null
            Long flushInterval = context.getLongAttribute("flushInterval");
            // 获取<cache>节点的size属性，默认值是null
            Integer size = context.getIntAttribute("size");
            // 获取<cache>节点的readOnly属性，默认值是false
            boolean readWrite = !context.getBooleanAttribute("readOnly", false);
            // 获取<cache>节点的blocking属性，默认值是false
            boolean blocking = context.getBooleanAttribute("blocking", false);
            // 获取<cache>节点下的子节点，将用于初始化二级缓存
            Properties props = context.getChildrenAsProperties();
            // 通过MapperBuilderAssistant创建Cache对象，并添加到Configuration.caches集合中保存
            builderAssistant.useNewCache(typeClass, evictionClass, flushInterval, size, readWrite, blocking, props);
        }
    }

    private void parameterMapElement(List<XNode> list) {
        for (XNode parameterMapNode : list) {
            String id = parameterMapNode.getStringAttribute("id");
            String type = parameterMapNode.getStringAttribute("type");
            Class<?> parameterClass = resolveClass(type);
            List<XNode> parameterNodes = parameterMapNode.evalNodes("parameter");
            List<ParameterMapping> parameterMappings = new ArrayList<>();
            for (XNode parameterNode : parameterNodes) {
                String property = parameterNode.getStringAttribute("property");
                String javaType = parameterNode.getStringAttribute("javaType");
                String jdbcType = parameterNode.getStringAttribute("jdbcType");
                String resultMap = parameterNode.getStringAttribute("resultMap");
                String mode = parameterNode.getStringAttribute("mode");
                String typeHandler = parameterNode.getStringAttribute("typeHandler");
                Integer numericScale = parameterNode.getIntAttribute("numericScale");
                ParameterMode modeEnum = resolveParameterMode(mode);
                Class<?> javaTypeClass = resolveClass(javaType);
                JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
                Class<? extends TypeHandler<?>> typeHandlerClass = resolveClass(typeHandler);
                ParameterMapping parameterMapping = builderAssistant.buildParameterMapping(parameterClass, property, javaTypeClass, jdbcTypeEnum, resultMap, modeEnum, typeHandlerClass, numericScale);
                parameterMappings.add(parameterMapping);
            }
            builderAssistant.addParameterMap(id, parameterClass, parameterMappings);
        }
    }

    private void resultMapElements(List<XNode> list) {
        for (XNode resultMapNode : list) {
            try {
                resultMapElement(resultMapNode);
            } catch (IncompleteElementException e) {
                // ignore, it will be retried
            }
        }
    }

    private ResultMap resultMapElement(XNode resultMapNode) {
        return resultMapElement(resultMapNode, Collections.emptyList(), null);
    }

    private ResultMap resultMapElement(XNode resultMapNode, List<ResultMapping> additionalResultMappings, Class<?> enclosingType) {
        ErrorContext.instance().activity("processing " + resultMapNode.getValueBasedIdentifier());
        // 获取<resultMap>标签的type属性值，这个值表示结果集将被映射成type指定类型的对象
        // 如果没有指定type属性的话，会找其他属性值，优先级依次是：type、ofType、resultType、javaType
        String type = resultMapNode.getStringAttribute("type",
                resultMapNode.getStringAttribute("ofType",
                        resultMapNode.getStringAttribute("resultType",
                                resultMapNode.getStringAttribute("javaType"))));
        // 解析映射的对象类型，支持别名
        Class<?> typeClass = resolveClass(type);
        if (typeClass == null) {
            // 主要是嵌套<association>和<case>这两个标签的时候，会走到这个逻辑
            typeClass = inheritEnclosingType(resultMapNode, enclosingType);
        }
        Discriminator discriminator = null;
        List<ResultMapping> resultMappings = new ArrayList<>(additionalResultMappings);
        List<XNode> resultChildren = resultMapNode.getChildren();
        // 解析<resultMap>标签的子标签
        for (XNode resultChild : resultChildren) {
            // 解析<constructor>子标签
            if ("constructor".equals(resultChild.getName())) {
                processConstructorElement(resultChild, typeClass, resultMappings);
            } else if ("discriminator".equals(resultChild.getName())) {
                // 解析<discriminator>子标签
                discriminator = processDiscriminatorElement(resultChild, typeClass, resultMappings);
            } else {
                // 处理<id>、<result>、<association>、<collection>等标签
                List<ResultFlag> flags = new ArrayList<>();
                if ("id".equals(resultChild.getName())) {
                    flags.add(ResultFlag.ID);
                }
                resultMappings.add(buildResultMappingFromContext(resultChild, typeClass, flags));
            }
        }
        // 获取<resultMap>标签的id属性，默认值会拼装所有父节点的id或value或property属性值，感兴
        // 趣的读者请参考XNode.getValueBasedIdentifier()方法的实现
        String id = resultMapNode.getStringAttribute("id",
                resultMapNode.getValueBasedIdentifier());
        // 获取<resultMap>标签的extends、autoMapping等属性
        String extend = resultMapNode.getStringAttribute("extends");
        Boolean autoMapping = resultMapNode.getBooleanAttribute("autoMapping");
        // 创建ResultMapResolver对象
        ResultMapResolver resultMapResolver =
                new ResultMapResolver(builderAssistant, id, typeClass, extend, discriminator, resultMappings, autoMapping);
        try {
            // 创建ResultMap对象，并添加到Configuration.resultMaps集合中，该集合是StrictMap类型
            return resultMapResolver.resolve();
        } catch (IncompleteElementException e) {
            configuration.addIncompleteResultMap(resultMapResolver);
            throw e;
        }
    }

    protected Class<?> inheritEnclosingType(XNode resultMapNode, Class<?> enclosingType) {
        if ("association".equals(resultMapNode.getName()) && resultMapNode.getStringAttribute("resultMap") == null) {
            String property = resultMapNode.getStringAttribute("property");
            if (property != null && enclosingType != null) {
                MetaClass metaResultType = MetaClass.forClass(enclosingType, configuration.getReflectorFactory());
                return metaResultType.getSetterType(property);
            }
        } else if ("case".equals(resultMapNode.getName()) && resultMapNode.getStringAttribute("resultMap") == null) {
            return enclosingType;
        }
        return null;
    }

    private void processConstructorElement(XNode resultChild, Class<?> resultType, List<ResultMapping> resultMappings) {
        // 获取<constructor>标签的子标签
        List<XNode> argChildren = resultChild.getChildren();
        for (XNode argChild : argChildren) {
            List<ResultFlag> flags = new ArrayList<>();
            flags.add(ResultFlag.CONSTRUCTOR); // 添加CONSTRUCTOR标志
            if ("idArg".equals(argChild.getName())) {
                flags.add(ResultFlag.ID);  // 对于<idArg>节点，添加ID标志
            }
            // 创建ResultMapping对象，记录resultMappings集合中
            resultMappings.add(buildResultMappingFromContext(argChild, resultType, flags));
        }
    }

    private Discriminator processDiscriminatorElement(XNode context, Class<?> resultType, List<ResultMapping> resultMappings) {
        String column = context.getStringAttribute("column");
        String javaType = context.getStringAttribute("javaType");
        String jdbcType = context.getStringAttribute("jdbcType");
        String typeHandler = context.getStringAttribute("typeHandler");
        Class<?> javaTypeClass = resolveClass(javaType);
        Class<? extends TypeHandler<?>> typeHandlerClass = resolveClass(typeHandler);
        JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
        // 从<discriminator>标签中解析column、javaType、jdbcType、typeHandler四个属性的逻辑非常简单，
        // 这里暂时省略
        Map<String, String> discriminatorMap = new HashMap<>();
        // 解析<discriminator>标签的<case>子标签
        for (XNode caseChild : context.getChildren()) {
            String value = caseChild.getStringAttribute("value");
            // 通过前面介绍的processNestedResultMappings()方法，解析<case>标签，
            // 创建相应的嵌套ResultMap对象
            String resultMap = caseChild.getStringAttribute("resultMap",
                    processNestedResultMappings(caseChild, resultMappings, resultType));
            // 记录该列值与对应选择的ResultMap的Id
            discriminatorMap.put(value, resultMap);
        }
        // 创建Discriminator对象
        return builderAssistant.buildDiscriminator(resultType, column, javaTypeClass, jdbcTypeEnum, typeHandlerClass, discriminatorMap);
    }

    private void sqlElement(List<XNode> list) {
        if (configuration.getDatabaseId() != null) {
            sqlElement(list, configuration.getDatabaseId());
        }
        sqlElement(list, null);
    }

    private void sqlElement(List<XNode> list, String requiredDatabaseId) {
        for (XNode context : list) { // 遍历<sql>标签
            // 获取databaseId、id属性值
            String databaseId = context.getStringAttribute("databaseId");
            String id = context.getStringAttribute("id");
            id = builderAssistant.applyCurrentNamespace(id, false);
            // 检测当前<sql>标签的的databaseId与全局配置中使用的databaseId是否一致
            if (databaseIdMatchesCurrent(id, databaseId, requiredDatabaseId)) {
                // 将待加载的SQL片段记录到Configuration.sqlFragments集合中
                sqlFragments.put(id, context);
            }
        }
    }

    private boolean databaseIdMatchesCurrent(String id, String databaseId, String requiredDatabaseId) {
        if (requiredDatabaseId != null) {
            return requiredDatabaseId.equals(databaseId);
        }
        if (databaseId != null) {
            return false;
        }
        if (!this.sqlFragments.containsKey(id)) {
            return true;
        }
        // skip this fragment if there is a previous one with a not null databaseId
        XNode context = this.sqlFragments.get(id);
        return context.getStringAttribute("databaseId") == null;
    }

    private ResultMapping buildResultMappingFromContext(XNode context, Class<?> resultType, List<ResultFlag> flags) {
        String property;
        if (flags.contains(ResultFlag.CONSTRUCTOR)) {
            // 如果<constructor>标签，则获取name属性
            property = context.getStringAttribute("name");
        } else {
            // 获取该标签的property的属性值
            property = context.getStringAttribute("property");
        }
        // 获取column、javaType、typeHandler、jdbcType、select等一系列属性，
        // 与获取property属性的方式类似
        String column = context.getStringAttribute("column");
        String javaType = context.getStringAttribute("javaType");
        String jdbcType = context.getStringAttribute("jdbcType");
        String nestedSelect = context.getStringAttribute("select");
        // 如果未指定<association>节点的resultMap属性，则是匿名的嵌套映射，需要通过
        //  processNestedResultMappings()方法解析该匿名的嵌套映射，在后面分析<collection>节点时
        // 还会涉及匿名嵌套映射的解析过程
        String nestedResultMap = context.getStringAttribute("resultMap", () ->
                processNestedResultMappings(context, Collections.emptyList(), resultType));
        String notNullColumn = context.getStringAttribute("notNullColumn");
        String columnPrefix = context.getStringAttribute("columnPrefix");
        String typeHandler = context.getStringAttribute("typeHandler");
        String resultSet = context.getStringAttribute("resultSet");
        String foreignColumn = context.getStringAttribute("foreignColumn");
        boolean lazy = "lazy".equals(context.getStringAttribute("fetchType", configuration.isLazyLoadingEnabled() ? "lazy" : "eager"));
        Class<?> javaTypeClass = resolveClass(javaType);
        Class<? extends TypeHandler<?>> typeHandlerClass = resolveClass(typeHandler);
        JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
        // 根据上面解析到的属性值，创建ResultMapping对象
        return builderAssistant.buildResultMapping(resultType, property, column, javaTypeClass, jdbcTypeEnum, nestedSelect, nestedResultMap, notNullColumn, columnPrefix, typeHandlerClass, flags, resultSet, foreignColumn, lazy);
    }

    private String processNestedResultMappings(XNode context, List<ResultMapping> resultMappings, Class<?> enclosingType) {
        // 该方法只会解析<association>、<collection>和<case>三个标签
        if (Arrays.asList("association", "collection", "case").contains(context.getName())
                && context.getStringAttribute("select") == null) {
            // 指定了select属性值之后，不会生成嵌套的ResultMap对象
            validateCollection(context, enclosingType);
            // 递归执行resultMapElement()方法，处理匿名嵌套映射，其中同样会创建一个完整的ResultMap对象，
            // 并添加到Configuration.resultMaps集合中
            ResultMap resultMap = resultMapElement(context, resultMappings, enclosingType);
            return resultMap.getId();
        }
        return null;
    }

    protected void validateCollection(XNode context, Class<?> enclosingType) {
        if ("collection".equals(context.getName()) && context.getStringAttribute("resultMap") == null
                && context.getStringAttribute("javaType") == null) {
            MetaClass metaResultType = MetaClass.forClass(enclosingType, configuration.getReflectorFactory());
            String property = context.getStringAttribute("property");
            if (!metaResultType.hasSetter(property)) {
                throw new BuilderException(
                        "Ambiguous collection type for property '" + property + "'. You must specify 'javaType' or 'resultMap'.");
            }
        }
    }

    private void bindMapperForNamespace() {
        // 拿到当前Mapper映射文件的namespace
        String namespace = builderAssistant.getCurrentNamespace();
        if (namespace != null) {
            Class<?> boundType = null;
            try {
                // 获取namespace对应的Mapper接口
                boundType = Resources.classForName(namespace);
            } catch (ClassNotFoundException e) {
            }
            // 如果此时未加载了该Mapper接口，则调用MapperRegistry.addMapper()方法注册Mapper接口
            if (boundType != null && !configuration.hasMapper(boundType)) {
                configuration.addLoadedResource("namespace:" + namespace);
                configuration.addMapper(boundType);
            }
        }
    }

}
