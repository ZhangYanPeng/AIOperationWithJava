package com.company.diagnosis.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.Map;

/**
 * 诊断请求DTO
 * 
 * 功能描述:
 * - 定义诊断请求的输入参数
 * - 支持参数校验
 * 
 * @author System
 * @since 2026-01-13
 */
@Data
public class DiagnosisRequest {

    /**
     * 请求ID(可选,如果不提供则自动生成)
     */
    private String requestId;

    /**
     * 会话ID(可选,用于关联会话)
     */
    private String sessionId;

    /**
     * 诊断类型(必填)
     */
    @NotBlank(message = "诊断类型不能为空")
    private String diagnosisType;

    /**
     * 问题描述(必填)
     */
    @NotBlank(message = "问题描述不能为空")
    private String problem;

    /**
     * 初始参数(可选)
     * 可包含设备ID、通道信息、时间范围等
     */
    private Map<String, Object> parameters;

    /**
     * 诊断选项(可选)
     */
    private DiagnosisOptions options;

    /**
     * 诊断选项类
     */
    @Data
    public static class DiagnosisOptions {
        /**
         * 是否启用深度分析
         */
        private Boolean enableDeepAnalysis;

        /**
         * 是否需要专家意见
         */
        private Boolean requireExpertOpinion;

        /**
         * 超时时间(毫秒)
         */
        private Long timeout;

        /**
         * 是否流式输出
         */
        private Boolean streaming;

        /**
         * 最大步骤数
         */
        private Integer maxSteps;
    }
}
