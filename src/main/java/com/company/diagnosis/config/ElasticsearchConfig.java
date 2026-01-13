package com.company.diagnosis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import lombok.Data;

import java.time.Duration;

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
    private String host = "localhost";

    /**
     * Elasticsearch端口
     */
    private Integer port = 9200;

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
    private Integer connectionTimeout = 30000;

    /**
     * Socket超时时间(毫秒)
     */
    private Integer socketTimeout = 60000;

    /**
     * 索引配置
     */
    private IndexConfig indices = new IndexConfig();

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
        ClientConfiguration.MaybeSecureClientConfigurationBuilder builder = 
            ClientConfiguration.builder()
                .connectedTo(host + ":" + port);
        
        // 配置认证信息
        if (username != null && !username.isEmpty() && password != null) {
            builder.withBasicAuth(username, password);
        }
        
        // 配置超时
        return builder
            .withConnectTimeout(Duration.ofMillis(connectionTimeout))
            .withSocketTimeout(Duration.ofMillis(socketTimeout))
            .build();
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
        private String toolInterface = "kb_tool_interface";

        /**
         * 诊断手册索引
         */
        private String diagnosisManual = "kb_diagnosis_manual";

        /**
         * 推理规则索引
         */
        private String reasoningRule = "kb_reasoning_rule";

        /**
         * 结论分析索引
         */
        private String conclusionAnalysis = "kb_conclusion_analysis";

        /**
         * 领域知识索引
         */
        private String domainKnowledge = "kb_domain_knowledge";
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
        return new IndexNameProvider(indices);
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
        
        /**
         * 根据知识类型获取索引名称
         *
         * @param knowledgeType 知识类型
         * @return 索引名称
         */
        public String getIndexByType(String knowledgeType) {
            return switch (knowledgeType) {
                case "tool", "tool-interface" -> indices.getToolInterface();
                case "diagnosis", "diagnosis-manual" -> indices.getDiagnosisManual();
                case "reasoning", "reasoning-rules" -> indices.getReasoningRule();
                case "conclusion", "conclusion-analysis" -> indices.getConclusionAnalysis();
                case "domain", "domain-knowledge" -> indices.getDomainKnowledge();
                default -> indices.getDomainKnowledge();
            };
        }
    }
}
