package com.company.diagnosis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import lombok.Data;

/**
 * Elasticsearch配置类
 * 
 * 功能描述:
 * - 配置Elasticsearch客户端连接
 * - 管理索引映射和配置
 * - 提供Elasticsearch操作Bean
 * 
 * 设计考虑:
 * - 支持连接池配置
 * - 支持超时配置
 * - 支持认证配置
 * - 使用Spring Data Elasticsearch简化操作
 * 
 * @author System
 * @since 2026-01-13
 */
@Configuration
@EnableElasticsearchRepositories(basePackages = "com.company.diagnosis.repository")
@ConfigurationProperties(prefix = "elasticsearch")
@Data
public class ElasticsearchConfig extends ElasticsearchConfiguration {

    /**
     * Elasticsearch主机地址
     */
    private String host;

    /**
     * Elasticsearch端口
     */
    private Integer port;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 连接超时时间(毫秒)
     */
    private Integer connectionTimeout;

    /**
     * Socket超时时间(毫秒)
     */
    private Integer socketTimeout;

    /**
     * 索引配置
     */
    private IndexConfig indices;

    /**
     * 创建Elasticsearch客户端配置
     * 
     * 功能说明:
     * 1. 配置主机和端口
     * 2. 配置认证信息
     * 3. 配置超时参数
     * 4. 配置连接池参数
     * 
     * @return ClientConfiguration Elasticsearch客户端配置对象
     */
    @Override
    public ClientConfiguration clientConfiguration() {
        // TODO: 待实现
        // 1. 构建ClientConfiguration.builder()
        // 2. 设置connectedTo(host:port)
        // 3. 设置withBasicAuth(username, password)
        // 4. 设置withConnectTimeout和withSocketTimeout
        // 5. 返回build()结果
        return null;
    }

    /**
     * 索引配置类
     * 
     * 定义系统中使用的所有Elasticsearch索引名称
     */
    @Data
    public static class IndexConfig {
        /**
         * 工具接口文档索引
         */
        private String toolInterface;

        /**
         * 诊断手册索引
         */
        private String diagnosisManual;

        /**
         * 推理规则索引
         */
        private String reasoningRule;

        /**
         * 结论分析索引
         */
        private String conclusionAnalysis;

        /**
         * 领域知识索引
         */
        private String domainKnowledge;
    }

    /**
     * 创建索引名称提供器Bean
     * 
     * 功能说明:
     * 提供统一的索引名称访问接口,避免硬编码
     * 
     * @return IndexNameProvider 索引名称提供器
     */
    @Bean
    public IndexNameProvider indexNameProvider() {
        // TODO: 待实现
        // 返回包含所有索引名称的提供器对象
        return null;
    }

    /**
     * 索引名称提供器
     * 
     * 提供类型安全的索引名称访问
     */
    public static class IndexNameProvider {
        private final IndexConfig indices;

        public IndexNameProvider(IndexConfig indices) {
            this.indices = indices;
        }

        /**
         * 获取工具接口索引名称
         */
        public String getToolInterfaceIndex() {
            return indices.getToolInterface();
        }

        /**
         * 获取诊断手册索引名称
         */
        public String getDiagnosisManualIndex() {
            return indices.getDiagnosisManual();
        }

        /**
         * 获取推理规则索引名称
         */
        public String getReasoningRuleIndex() {
            return indices.getReasoningRule();
        }

        /**
         * 获取结论分析索引名称
         */
        public String getConclusionAnalysisIndex() {
            return indices.getConclusionAnalysis();
        }

        /**
         * 获取领域知识索引名称
         */
        public String getDomainKnowledgeIndex() {
            return indices.getDomainKnowledge();
        }
    }
}
package com.company.diagnosis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import lombok.Data;

/**
 * Elasticsearch配置类
 * 
 * 功能描述:
 * - 配置Elasticsearch客户端连接
 * - 管理索引映射和配置
 * - 提供Elasticsearch操作Bean
 * 
 * 设计考虑:
 * - 支持连接池配置
 * - 支持超时配置
 * - 支持认证配置
 * - 使用Spring Data Elasticsearch简化操作
 * 
 * @author System
 * @since 2026-01-13
 */
@Configuration
@EnableElasticsearchRepositories(basePackages = "com.company.diagnosis.repository")
@ConfigurationProperties(prefix = "elasticsearch")
@Data
public class ElasticsearchConfig extends ElasticsearchConfiguration {

    /**
     * Elasticsearch主机地址
     */
    private String host;

    /**
     * Elasticsearch端口
     */
    private Integer port;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 连接超时时间(毫秒)
     */
    private Integer connectionTimeout;

    /**
     * Socket超时时间(毫秒)
     */
    private Integer socketTimeout;

    /**
     * 索引配置
     */
    private IndexConfig indices;

    /**
     * 创建Elasticsearch客户端配置
     * 
     * 功能说明:
     * 1. 配置主机和端口
     * 2. 配置认证信息
     * 3. 配置超时参数
     * 4. 配置连接池参数
     * 
     * @return ClientConfiguration Elasticsearch客户端配置对象
     */
    @Override
    public ClientConfiguration clientConfiguration() {
        // TODO: 待实现
        // 1. 构建ClientConfiguration.builder()
        // 2. 设置connectedTo(host:port)
        // 3. 设置withBasicAuth(username, password)
        // 4. 设置withConnectTimeout和withSocketTimeout
        // 5. 返回build()结果
        return null;
    }

    /**
     * 索引配置类
     * 
     * 定义系统中使用的所有Elasticsearch索引名称
     */
    @Data
    public static class IndexConfig {
        /**
         * 工具接口文档索引
         */
        private String toolInterface;

        /**
         * 诊断手册索引
         */
        private String diagnosisManual;

        /**
         * 推理规则索引
         */
        private String reasoningRule;

        /**
         * 结论分析索引
         */
        private String conclusionAnalysis;

        /**
         * 领域知识索引
         */
        private String domainKnowledge;
    }

    /**
     * 创建索引名称提供器Bean
     * 
     * 功能说明:
     * 提供统一的索引名称访问接口,避免硬编码
     * 
     * @return IndexNameProvider 索引名称提供器
     */
    @Bean
    public IndexNameProvider indexNameProvider() {
        // TODO: 待实现
        // 返回包含所有索引名称的提供器对象
        return null;
    }

    /**
     * 索引名称提供器
     * 
     * 提供类型安全的索引名称访问
     */
    public static class IndexNameProvider {
        private final IndexConfig indices;

        public IndexNameProvider(IndexConfig indices) {
            this.indices = indices;
        }

        /**
         * 获取工具接口索引名称
         */
        public String getToolInterfaceIndex() {
            return indices.getToolInterface();
        }

        /**
         * 获取诊断手册索引名称
         */
        public String getDiagnosisManualIndex() {
            return indices.getDiagnosisManual();
        }

        /**
         * 获取推理规则索引名称
         */
        public String getReasoningRuleIndex() {
            return indices.getReasoningRule();
        }

        /**
         * 获取结论分析索引名称
         */
        public String getConclusionAnalysisIndex() {
            return indices.getConclusionAnalysis();
        }

        /**
         * 获取领域知识索引名称
         */
        public String getDomainKnowledgeIndex() {
            return indices.getDomainKnowledge();
        }
    }
}
