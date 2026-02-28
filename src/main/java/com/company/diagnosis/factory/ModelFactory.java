package com.company.diagnosis.factory;

import com.company.diagnosis.config.AgentScopeConfig.LlmClientRegistry;
import com.company.diagnosis.llm.LlmClient;
import com.company.diagnosis.llm.OpenAiCompatibleClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

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

    private static final Logger log = LoggerFactory.getLogger(ModelFactory.class);

    @Autowired(required = false)
    private LlmClientRegistry llmClientRegistry;

    /**
     * 创建模型客户端
     *
     * @param provider 提供商名称（如dashscope、openai、ollama）
     * @param config 模型配置
     * @return 模型客户端实例
     */
    public LlmClient createModelClient(String provider, Map<String, Object> config) {
        if (!StringUtils.hasText(provider)) {
            throw new IllegalArgumentException("提供商名称不能为空");
        }
        
        log.info("创建模型客户端: provider={}", provider);

        // 从配置中提取参数
        String baseUrl = (String) config.get("baseUrl");
        String apiKey = (String) config.get("apiKey");
        String defaultModel = (String) config.get("defaultModel");
        Integer timeout = (Integer) config.getOrDefault("timeout", 300);

        // 验证必要参数
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalArgumentException("baseUrl不能为空");
        }
        if (!StringUtils.hasText(defaultModel)) {
            throw new IllegalArgumentException("defaultModel不能为空");
        }

        // 创建OpenAI兼容客户端
        LlmClient client = new OpenAiCompatibleClient(
                provider,
                baseUrl,
                apiKey,
                timeout != null ? timeout : 300,
                defaultModel,
                WebClient.builder()
        );

        log.info("模型客户端创建成功: provider={}, model={}", provider, defaultModel);
        return client;
    }

    /**
     * 获取已注册的模型客户端
     *
     * @param provider 提供商名称
     * @return 模型客户端，如不存在则返回null
     */
    public LlmClient getModelClient(String provider) {
        if (llmClientRegistry == null) {
            log.warn("LlmClientRegistry未注入");
            return null;
        }

        if (!llmClientRegistry.hasProvider(provider)) {
            log.warn("未找到提供商: {}", provider);
            return null;
        }

        return llmClientRegistry.getClient(provider);
    }

    /**
     * 获取默认模型客户端
     *
     * @return 默认模型客户端
     */
    public LlmClient getDefaultModelClient() {
        if (llmClientRegistry == null) {
            throw new IllegalStateException("LlmClientRegistry未配置");
        }
        return llmClientRegistry.getDefaultClient();
    }

    /**
     * 检查提供商是否可用
     *
     * @param provider 提供商名称
     * @return 是否可用
     */
    public boolean isProviderAvailable(String provider) {
        return llmClientRegistry != null && llmClientRegistry.hasProvider(provider);
    }
}
