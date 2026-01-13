package com.company.diagnosis.model.config;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 智能体配置类
 * 
 * 功能描述:
 * - 定义智能体的配置信息
 * - 支持配置驱动的智能体实例化
 * - 支持层级配置
 * 
 * @author System
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
     * 模型配置类
     */
    @Data
    public static class ModelConfig {
        private String provider;
        private String modelName;
        private Double temperature;
        private Integer maxTokens;
        private Double topP;
        private Boolean enableThinking;
        private Boolean streaming;
    }

    /**
     * 校验配置类
     */
    @Data
    public static class ValidationConfig {
        private Boolean enabled;
        private String schemaPath;
    }
}
package com.company.diagnosis.model.config;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 智能体配置类
 * 
 * 功能描述:
 * - 定义智能体的配置信息
 * - 支持配置驱动的智能体实例化
 * - 支持层级配置
 * 
 * @author System
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
     * 模型配置类
     */
    @Data
    public static class ModelConfig {
        private String provider;
        private String modelName;
        private Double temperature;
        private Integer maxTokens;
        private Double topP;
        private Boolean enableThinking;
        private Boolean streaming;
    }

    /**
     * 校验配置类
     */
    @Data
    public static class ValidationConfig {
        private Boolean enabled;
        private String schemaPath;
    }
}
