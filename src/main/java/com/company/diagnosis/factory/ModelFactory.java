package com.company.diagnosis.factory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型工厂
 * <p>
 * 职责：
 * 1. 创建和管理LLM模型客户端
 * 2. 支持多种模型提供商
 * 3. 管理模型的配置和生命周期
 *
 * @author Diagnosis System
 * @date 2026-01-13
 */
@Component
public class ModelFactory {

    private static final Logger logger = LoggerFactory.getLogger(ModelFactory.class);
    
    /**
     * 模型客户端缓存
     */
    private final Map<String, Object> clientCache = new ConcurrentHashMap<>();
    
    @Value("${llm.dashscope.api-key:}")
    private String dashscopeApiKey;
    
    @Value("${llm.dashscope.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String dashscopeBaseUrl;
    
    @Value("${llm.openai.api-key:}")
    private String openaiApiKey;
    
    @Value("${llm.openai.base-url:https://api.openai.com/v1}")
    private String openaiBaseUrl;
    
    @Value("${llm.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;
    
    @Value("${llm.default-provider:dashscope}")
    private String defaultProvider;

    /**
     * 创建模型客户端
     *
     * @param provider 提供商名称（如DashScope、OpenAI）
     * @param config 模型配置
     * @return 模型客户端实例
     */
    public Object createModelClient(String provider, Map<String, Object> config) {
        String providerName = provider != null ? provider.toLowerCase() : defaultProvider;
        
        // 生成缓存键
        String cacheKey = generateCacheKey(providerName, config);
        
        // 检查缓存
        Object cached = clientCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        // 创建客户端
        Object client = doCreateClient(providerName, config);
        if (client != null) {
            clientCache.put(cacheKey, client);
        }
        
        return client;
    }
    
    /**
     * 获取默认模型客户端
     *
     * @return 默认模型客户端
     */
    public Object getDefaultClient() {
        return createModelClient(defaultProvider, Map.of());
    }
    
    /**
     * 创建DashScope客户端
     */
    public WebClient createDashScopeClient() {
        return WebClient.builder()
                .baseUrl(dashscopeBaseUrl)
                .defaultHeader("Authorization", "Bearer " + dashscopeApiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
    
    /**
     * 创建OpenAI兼容客户端
     */
    public WebClient createOpenAIClient() {
        return WebClient.builder()
                .baseUrl(openaiBaseUrl)
                .defaultHeader("Authorization", "Bearer " + openaiApiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
    
    /**
     * 创建Ollama客户端
     */
    public WebClient createOllamaClient() {
        return WebClient.builder()
                .baseUrl(ollamaBaseUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
    
    /**
     * 获取模型配置
     *
     * @param provider 提供商
     * @return 配置Map
     */
    public Map<String, Object> getProviderConfig(String provider) {
        String providerName = provider != null ? provider.toLowerCase() : defaultProvider;
        
        return switch (providerName) {
            case "dashscope" -> Map.of(
                    "apiKey", dashscopeApiKey,
                    "baseUrl", dashscopeBaseUrl
            );
            case "openai" -> Map.of(
                    "apiKey", openaiApiKey,
                    "baseUrl", openaiBaseUrl
            );
            case "ollama" -> Map.of(
                    "baseUrl", ollamaBaseUrl
            );
            default -> Map.of();
        };
    }
    
    /**
     * 检查提供商是否可用
     *
     * @param provider 提供商名称
     * @return 是否可用
     */
    public boolean isProviderAvailable(String provider) {
        String providerName = provider != null ? provider.toLowerCase() : defaultProvider;
        
        return switch (providerName) {
            case "dashscope" -> dashscopeApiKey != null && !dashscopeApiKey.isEmpty() 
                    && !dashscopeApiKey.equals("your-api-key");
            case "openai" -> openaiApiKey != null && !openaiApiKey.isEmpty() 
                    && !openaiApiKey.equals("your-api-key");
            case "ollama" -> ollamaBaseUrl != null && !ollamaBaseUrl.isEmpty();
            default -> false;
        };
    }
    
    /**
     * 清除客户端缓存
     */
    public void clearCache() {
        clientCache.clear();
        logger.info("模型客户端缓存已清除");
    }
    
    /**
     * 根据提供商创建客户端
     */
    private Object doCreateClient(String provider, Map<String, Object> config) {
        try {
            return switch (provider) {
                case "dashscope" -> createDashScopeClient();
                case "openai" -> createOpenAIClient();
                case "ollama" -> createOllamaClient();
                default -> {
                    logger.warn("未知的模型提供商: {}, 使用默认提供商", provider);
                    yield createDashScopeClient();
                }
            };
        } catch (Exception e) {
            logger.error("创建模型客户端失败: provider={}, error={}", provider, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 生成缓存键
     */
    private String generateCacheKey(String provider, Map<String, Object> config) {
        StringBuilder keyBuilder = new StringBuilder(provider);
        
        if (config != null && !config.isEmpty()) {
            // 添加关键配置到缓存键
            if (config.containsKey("modelName")) {
                keyBuilder.append("_").append(config.get("modelName"));
            }
        }
        
        return keyBuilder.toString();
    }
}
