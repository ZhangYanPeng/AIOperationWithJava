package com.company.diagnosis.model.config;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 智能体配置类
 * <p>
 * 功能描述:
 * - 定义智能体的配置信息
 * - 支持配置驱动的智能体实例化
 * - 支持层级配置
 *
 * @author Diagnosis System
 * @since 2026-01-13
 */
@Data
public class AgentConfig {

    /**
     * 智能体名称
     */
    private String name;

    /**
     * 智能体描述
     */
    private String description;

    /**
     * 层级编号(1=接口调用层, 2~N=执行层)
     */
    private Integer layer;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 下层智能体名称(关键配置项)
     * 指定本智能体调用的下一层智能体
     */
    private String lowerLayerAgent;

    /**
     * 触发条件(SpEL表达式)
     */
    private String triggerCondition;

    /**
     * 知识库类型列表
     */
    private List<String> knowledgeTypes;

    /**
     * 模型配置
     */
    private ModelConfig model;

    /**
     * 提示词模板路径
     */
    private Map<String, String> promptTemplates;

    /**
     * 工具函数列表
     */
    private List<String> tools;

    /**
     * 最大重试次数
     */
    private Integer maxRetries;

    /**
     * 重试退避时间(毫秒)
     */
    private Long retryBackoff;

    /**
     * 超时时间(毫秒)
     */
    private Long timeout;

    /**
     * 是否支持并发步骤
     */
    private Boolean concurrentSteps;

    /**
     * 最大并发数
     */
    private Integer maxConcurrency;

    /**
     * 输出校验配置
     */
    private ValidationConfig validation;

    /**
     * 最大步骤数
     */
    private Integer maxSteps;

    /**
     * 单步超时(毫秒)
     */
    private Long stepTimeout;

    /**
     * 获取重试次数（带默认值）
     *
     * @return 重试次数
     */
    public int getMaxRetriesOrDefault() {
        return maxRetries != null ? maxRetries : 3;
    }

    /**
     * 获取超时时间（带默认值）
     *
     * @return 超时时间（毫秒）
     */
    public long getTimeoutOrDefault() {
        return timeout != null ? timeout : 60000L;
    }

    /**
     * 获取温度参数（带默认值）
     *
     * @return 温度参数
     */
    public double getTemperatureOrDefault() {
        return model != null && model.getTemperature() != null ? model.getTemperature() : 0.3;
    }

    /**
     * 判断是否启用流式输出
     *
     * @return 是否启用
     */
    public boolean isStreamingEnabled() {
        return model != null && Boolean.TRUE.equals(model.getStreaming());
    }

    /**
     * 模型配置类
     */
    @Data
    public static class ModelConfig {
        /**
         * 模型提供商 (dashscope/openai/ollama)
         */
        private String provider;

        /**
         * 模型名称
         */
        private String modelName;

        /**
         * 温度参数
         */
        private Double temperature;

        /**
         * 最大输出token数
         */
        private Integer maxTokens;

        /**
         * TopP参数
         */
        private Double topP;

        /**
         * 是否启用思维链
         */
        private Boolean enableThinking;

        /**
         * 是否启用流式输出
         */
        private Boolean streaming;

        /**
         * 获取最大token数（带默认值）
         *
         * @return 最大token数
         */
        public int getMaxTokensOrDefault() {
            return maxTokens != null ? maxTokens : 2000;
        }
    }

    /**
     * 校验配置类
     */
    @Data
    public static class ValidationConfig {
        /**
         * 是否启用校验
         */
        private Boolean enabled;

        /**
         * JSON Schema路径
         */
        private String schemaPath;
    }
}
