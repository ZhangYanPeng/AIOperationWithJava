package com.company.diagnosis.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

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

    private static final Logger logger = LoggerFactory.getLogger(AgentScopeConfig.class);

    /**
     * 默认模型提供商
     */
    private String defaultProvider = "dashscope";

    /**
     * DashScope配置
     */
    private ModelProviderConfig dashscope = new ModelProviderConfig();

    /**
     * OpenAI配置
     */
    private ModelProviderConfig openai = new ModelProviderConfig();

    /**
     * Ollama配置
     */
    private ModelProviderConfig ollama = new ModelProviderConfig();

    /**
     * 通用模型参数
     */
    private Double temperature = 0.7;
    private Integer maxTokens = 4096;
    private Double topP = 0.9;
    private Boolean enableThinking = false;
    private Boolean streaming = true;

    /**
     * 创建模型客户端注册表
     * 
     * 功能说明:
     * 提供统一的模型客户端访问接口,根据提供商名称获取对应客户端
     * 
     * @return ModelClientRegistry 模型客户端注册表
     */
    @Bean
    public ModelClientRegistry modelClientRegistry() {
        Map<String, ModelProviderConfig> providers = new HashMap<>();
        
        // 注册所有配置的提供商
        if (dashscope != null && dashscope.getApiKey() != null) {
            providers.put("dashscope", dashscope);
            logger.info("注册模型提供商: dashscope, model={}", dashscope.getDefaultModel());
        }
        
        if (openai != null && openai.getApiKey() != null) {
            providers.put("openai", openai);
            logger.info("注册模型提供商: openai, model={}", openai.getDefaultModel());
        }
        
        if (ollama != null && ollama.getBaseUrl() != null) {
            providers.put("ollama", ollama);
            logger.info("注册模型提供商: ollama, model={}", ollama.getDefaultModel());
        }
        
        // 创建默认参数
        ModelParameters defaultParams = new ModelParameters();
        defaultParams.setTemperature(temperature);
        defaultParams.setMaxTokens(maxTokens);
        defaultParams.setTopP(topP);
        defaultParams.setEnableThinking(enableThinking);
        defaultParams.setStreaming(streaming);
        
        logger.info("初始化模型客户端注册表: defaultProvider={}, 注册提供商数量={}", 
                defaultProvider, providers.size());
        
        return new ModelClientRegistry(providers, defaultProvider, defaultParams);
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
        private Integer timeout = 60;
        
        /**
         * 是否启用
         */
        private Boolean enabled = true;
    }
    
    /**
     * 模型参数配置
     */
    @Data
    public static class ModelParameters {
        private Double temperature;
        private Integer maxTokens;
        private Double topP;
        private Boolean enableThinking;
        private Boolean streaming;
    }

    /**
     * 模型客户端注册表
     * 
     * 管理所有模型客户端,提供统一访问接口
     */
    public static class ModelClientRegistry {
        private final Map<String, ModelProviderConfig> providers;
        private final String defaultProvider;
        private final ModelParameters defaultParameters;

        public ModelClientRegistry(Map<String, ModelProviderConfig> providers, 
                                   String defaultProvider,
                                   ModelParameters defaultParameters) {
            this.providers = providers;
            this.defaultProvider = defaultProvider;
            this.defaultParameters = defaultParameters;
        }

        /**
         * 根据提供商名称获取模型配置
         * 
         * @param provider 提供商名称(dashscope/openai/ollama)
         * @return ModelProviderConfig 对应的模型配置
         * @throws IllegalArgumentException 当提供商不存在时抛出
         */
        public ModelProviderConfig getProviderConfig(String provider) {
            if (provider == null || provider.isEmpty()) {
                throw new IllegalArgumentException("提供商名称不能为空");
            }
            
            ModelProviderConfig config = providers.get(provider.toLowerCase());
            if (config == null) {
                throw new IllegalArgumentException("模型提供商不存在: " + provider);
            }
            
            return config;
        }

        /**
         * 获取默认模型配置
         * 
         * @return ModelProviderConfig 默认模型配置
         */
        public ModelProviderConfig getDefaultProviderConfig() {
            return getProviderConfig(defaultProvider);
        }

        /**
         * 检查提供商是否存在
         * 
         * @param provider 提供商名称
         * @return boolean 是否存在
         */
        public boolean hasProvider(String provider) {
            return provider != null && providers.containsKey(provider.toLowerCase());
        }
        
        /**
         * 获取所有可用的提供商名称
         * 
         * @return 提供商名称列表
         */
        public java.util.Set<String> getAvailableProviders() {
            return providers.keySet();
        }
        
        /**
         * 获取默认模型参数
         * 
         * @return ModelParameters 默认参数
         */
        public ModelParameters getDefaultParameters() {
            return defaultParameters;
        }
        
        /**
         * 获取默认提供商名称
         * 
         * @return 默认提供商名称
         */
        public String getDefaultProvider() {
            return defaultProvider;
        }
        
        /**
         * 构建模型调用参数
         * 
         * @param provider 提供商名称
         * @param overrides 覆盖参数
         * @return 合并后的参数Map
         */
        public Map<String, Object> buildCallParameters(String provider, Map<String, Object> overrides) {
            Map<String, Object> params = new HashMap<>();
            
            // 添加默认参数
            if (defaultParameters != null) {
                if (defaultParameters.getTemperature() != null) {
                    params.put("temperature", defaultParameters.getTemperature());
                }
                if (defaultParameters.getMaxTokens() != null) {
                    params.put("max_tokens", defaultParameters.getMaxTokens());
                }
                if (defaultParameters.getTopP() != null) {
                    params.put("top_p", defaultParameters.getTopP());
                }
                if (defaultParameters.getStreaming() != null) {
                    params.put("stream", defaultParameters.getStreaming());
                }
            }
            
            // 添加提供商特定配置
            ModelProviderConfig config = providers.get(provider != null ? provider.toLowerCase() : defaultProvider);
            if (config != null) {
                params.put("api_key", config.getApiKey());
                params.put("base_url", config.getBaseUrl());
                params.put("model", config.getDefaultModel());
                params.put("timeout", config.getTimeout());
            }
            
            // 应用覆盖参数
            if (overrides != null) {
                params.putAll(overrides);
            }
            
            return params;
        }
    }
}
