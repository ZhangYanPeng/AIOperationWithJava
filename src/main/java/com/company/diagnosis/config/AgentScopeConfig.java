package com.company.diagnosis.config;

import com.company.diagnosis.llm.LlmClient;
import com.company.diagnosis.llm.OpenAiCompatibleClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * AgentScope配置类
 * 
 * 功能描述:
 * - 配置AgentScope框架
 * - 配置LLM模型客户端
 * - 提供模型工厂Bean
 * 
 * 设计考虑:
 * - 支持多种模型提供商(DashScope、OpenAI、Ollama)
 * - 支持模型参数配置
 * - 支持模型客户端池化
 * - 提供统一的模型访问接口
 * 
 * @author System
 * @since 2026-01-13
 */
@Configuration
@ConfigurationProperties(prefix = "llm")
@Data
public class AgentScopeConfig {

    private static final Logger log = LoggerFactory.getLogger(AgentScopeConfig.class);

    /**
     * 默认模型提供商
     */
    private String defaultProvider;

    /**
     * DashScope配置
     */
    private ModelProviderConfig dashscope;

    /**
     * OpenAI配置
     */
    private ModelProviderConfig openai;

    /**
     * Ollama配置
     */
    private ModelProviderConfig ollama;

    /**
     * 通用模型参数
     */
    private Double temperature;
    private Integer maxTokens;
    private Double topP;
    private Boolean enableThinking;
    private Boolean streaming;

    /**
     * 创建DashScope模型客户端
     * 
     * 功能说明:
     * 创建阿里云百炼(DashScope)模型客户端,使用OpenAI兼容接口
     * 
     * @return LlmClient DashScope模型客户端,如配置无效则返回null
     */
    @Bean
    public LlmClient dashscopeClient() {
        if (dashscope == null || !dashscope.isValid()) {
            log.warn("DashScope配置无效,跳过客户端创建");
            return null;
        }
        
        log.info("创建DashScope客户端: baseUrl={}, model={}", 
                dashscope.getBaseUrl(), dashscope.getDefaultModel());
        
        return new OpenAiCompatibleClient(
                "dashscope",
                dashscope.getBaseUrl(),
                dashscope.getApiKey(),
                Optional.ofNullable(dashscope.getTimeout()).orElse(300),
                dashscope.getDefaultModel(),
                WebClient.builder()
        );
    }

    /**
     * 创建OpenAI模型客户端
     * 
     * 功能说明:
     * 创建OpenAI兼容模型客户端
     * 
     * @return LlmClient OpenAI模型客户端,如配置无效则返回null
     */
    @Bean
    public LlmClient openaiClient() {
        if (openai == null || !openai.isValid()) {
            log.warn("OpenAI配置无效,跳过客户端创建");
            return null;
        }
        
        log.info("创建OpenAI客户端: baseUrl={}, model={}", 
                openai.getBaseUrl(), openai.getDefaultModel());
        
        return new OpenAiCompatibleClient(
                "openai",
                openai.getBaseUrl(),
                openai.getApiKey(),
                Optional.ofNullable(openai.getTimeout()).orElse(300),
                openai.getDefaultModel(),
                WebClient.builder()
        );
    }

    /**
     * 创建Ollama模型客户端
     * 
     * 功能说明:
     * 创建本地Ollama模型客户端,Ollama兼容OpenAI接口
     * 
     * @return LlmClient Ollama模型客户端,如配置无效则返回null
     */
    @Bean
    public LlmClient ollamaClient() {
        if (ollama == null || !ollama.isValidForOllama()) {
            log.warn("Ollama配置无效,跳过客户端创建");
            return null;
        }
        
        log.info("创建Ollama客户端: baseUrl={}, model={}", 
                ollama.getBaseUrl(), ollama.getDefaultModel());
        
        // Ollama使用OpenAI兼容接口,无需API Key
        return new OpenAiCompatibleClient(
                "ollama",
                ollama.getBaseUrl() + "/v1",
                null,
                Optional.ofNullable(ollama.getTimeout()).orElse(300),
                ollama.getDefaultModel(),
                WebClient.builder()
        );
    }

    /**
     * 创建模型客户端注册表
     * 
     * 功能说明:
     * 提供统一的模型客户端访问接口,根据提供商名称获取对应客户端
     * 
     * @return LlmClientRegistry 模型客户端注册表
     */
    @Bean
    @Primary
    public LlmClientRegistry llmClientRegistry() {
        Map<String, LlmClient> clients = new HashMap<>();
        
        // 注册已创建的客户端
        LlmClient dashscopeClientInstance = dashscopeClient();
        if (dashscopeClientInstance != null) {
            clients.put("dashscope", dashscopeClientInstance);
            log.info("注册DashScope客户端");
        }
        
        LlmClient openaiClientInstance = openaiClient();
        if (openaiClientInstance != null) {
            clients.put("openai", openaiClientInstance);
            log.info("注册OpenAI客户端");
        }
        
        LlmClient ollamaClientInstance = ollamaClient();
        if (ollamaClientInstance != null) {
            clients.put("ollama", ollamaClientInstance);
            log.info("注册Ollama客户端");
        }
        
        // 确定默认提供商
        String effectiveDefaultProvider = determineDefaultProvider(clients);
        
        log.info("LLM客户端注册表创建完成: 已注册{}个客户端, 默认提供商={}", 
                clients.size(), effectiveDefaultProvider);
        
        return new LlmClientRegistry(clients, effectiveDefaultProvider);
    }

    /**
     * 确定有效的默认提供商
     * 
     * @param clients 已注册的客户端Map
     * @return 有效的默认提供商名称
     */
    private String determineDefaultProvider(Map<String, LlmClient> clients) {
        // 如果配置的默认提供商可用,使用它
        if (StringUtils.hasText(defaultProvider) && clients.containsKey(defaultProvider)) {
            return defaultProvider;
        }
        
        // 否则按优先级选择第一个可用的
        String[] fallbackOrder = {"dashscope", "openai", "ollama"};
        for (String provider : fallbackOrder) {
            if (clients.containsKey(provider)) {
                log.warn("配置的默认提供商'{}'不可用,回退到'{}'", defaultProvider, provider);
                return provider;
            }
        }
        
        log.error("没有可用的LLM客户端!");
        return null;
    }

    /**
     * 模型提供商配置类
     */
    @Data
    public static class ModelProviderConfig {
        /**
         * API密钥
         */
        private String apiKey;

        /**
         * 基础URL
         */
        private String baseUrl;

        /**
         * 默认模型名称
         */
        private String defaultModel;

        /**
         * 超时时间(秒)
         */
        private Integer timeout;
        
        /**
         * 检查配置是否有效(需要API Key的提供商)
         * 
         * @return 配置是否有效
         */
        public boolean isValid() {
            return StringUtils.hasText(apiKey) 
                    && StringUtils.hasText(baseUrl) 
                    && StringUtils.hasText(defaultModel);
        }
        
        /**
         * 检查Ollama配置是否有效(不需要API Key)
         * 
         * @return 配置是否有效
         */
        public boolean isValidForOllama() {
            return StringUtils.hasText(baseUrl) && StringUtils.hasText(defaultModel);
        }
    }

    /**
     * LLM客户端注册表
     * 
     * 管理所有LLM客户端,提供统一访问接口
     */
    public static class LlmClientRegistry {
        
        private static final Logger log = LoggerFactory.getLogger(LlmClientRegistry.class);
        
        private final Map<String, LlmClient> clients;
        private final String defaultProvider;

        public LlmClientRegistry(Map<String, LlmClient> clients, String defaultProvider) {
            this.clients = new HashMap<>(clients);
            this.defaultProvider = defaultProvider;
        }

        /**
         * 根据提供商名称获取LLM客户端
         * 
         * @param provider 提供商名称(dashscope/openai/ollama)
         * @return LlmClient 对应的模型客户端
         * @throws IllegalArgumentException 当提供商不存在时抛出
         */
        public LlmClient getClient(String provider) {
            if (!StringUtils.hasText(provider)) {
                throw new IllegalArgumentException("提供商名称不能为空");
            }
            
            LlmClient client = clients.get(provider.toLowerCase());
            if (client == null) {
                throw new IllegalArgumentException(
                        String.format("未找到提供商'%s'的LLM客户端,可用提供商: %s", 
                                provider, clients.keySet()));
            }
            
            return client;
        }

        /**
         * 获取默认LLM客户端
         * 
         * @return LlmClient 默认模型客户端
         * @throws IllegalStateException 当没有可用客户端时抛出
         */
        public LlmClient getDefaultClient() {
            if (defaultProvider == null || !clients.containsKey(defaultProvider)) {
                throw new IllegalStateException("没有可用的默认LLM客户端");
            }
            return clients.get(defaultProvider);
        }

        /**
         * 检查提供商是否存在
         * 
         * @param provider 提供商名称
         * @return boolean 是否存在
         */
        public boolean hasProvider(String provider) {
            return StringUtils.hasText(provider) && clients.containsKey(provider.toLowerCase());
        }
        
        /**
         * 获取所有可用的提供商名称
         * 
         * @return 提供商名称集合
         */
        public java.util.Set<String> getAvailableProviders() {
            return java.util.Collections.unmodifiableSet(clients.keySet());
        }
        
        /**
         * 获取默认提供商名称
         * 
         * @return 默认提供商名称
         */
        public String getDefaultProvider() {
            return defaultProvider;
        }
    }
}
