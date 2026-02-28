package com.company.diagnosis.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.util.StringUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;

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
@ConditionalOnProperty(name = "elasticsearch.enabled", havingValue = "true", matchIfMissing = false)
@EnableElasticsearchRepositories(basePackages = "com.company.diagnosis.repository")
@ConfigurationProperties(prefix = "elasticsearch")
@Data
@EqualsAndHashCode(callSuper = false)
public class ElasticsearchConfig extends ElasticsearchConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchConfig.class);

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
        String hostAndPort = String.format("%s:%d", host, port);
        log.info("配置Elasticsearch连接: {}", hostAndPort);
        
        ClientConfiguration.MaybeSecureClientConfigurationBuilder builder = 
                ClientConfiguration.builder()
                        .connectedTo(hostAndPort);
        
        // 配置认证信息
        if (StringUtils.hasText(username) && StringUtils.hasText(password)) {
            builder.withBasicAuth(username, password);
            log.info("已配置Elasticsearch认证");
        }
        
        // 配置超时参数
        ClientConfiguration config = builder
                .withConnectTimeout(Duration.ofMillis(connectionTimeout))
                .withSocketTimeout(Duration.ofMillis(socketTimeout))
                .build();
        
        log.info("Elasticsearch配置完成: connectionTimeout={}ms, socketTimeout={}ms", 
                connectionTimeout, socketTimeout);
        
        return config;
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
        log.info("创建索引名称提供器: toolInterface={}, diagnosisManual={}, reasoningRule={}", 
                indices.getToolInterface(), indices.getDiagnosisManual(), indices.getReasoningRule());
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
         * 根据知识类型获取对应索引名称
         * 
         * @param knowledgeType 知识类型
         * @return 对应的索引名称
         */
        public String getIndexByType(String knowledgeType) {
            if (knowledgeType == null) {
                return indices.getDomainKnowledge();
            }
            
            return switch (knowledgeType.toLowerCase()) {
                case "tool", "tool_interface" -> indices.getToolInterface();
                case "manual", "diagnosis_manual" -> indices.getDiagnosisManual();
                case "rule", "reasoning_rule" -> indices.getReasoningRule();
                case "conclusion", "conclusion_analysis" -> indices.getConclusionAnalysis();
                default -> indices.getDomainKnowledge();
            };
        }
    }
}
