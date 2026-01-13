package com.company.diagnosis.factory;

import org.springframework.stereotype.Component;

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

    /**
     * 创建模型客户端
     *
     * @param provider 提供商名称（如DashScope、OpenAI）
     * @param config 模型配置
     * @return 模型客户端实例
     */
    public Object createModelClient(String provider, java.util.Map<String, Object> config) {
        // TODO: 待实现
        return null;
    }
}
