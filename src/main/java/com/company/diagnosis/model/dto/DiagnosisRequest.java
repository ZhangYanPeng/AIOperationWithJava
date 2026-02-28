package com.company.diagnosis.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.HashMap;
import java.util.Map;

/**
 * 诊断请求DTO
 * 
 * 功能描述:
 * - 定义诊断请求的输入参数
 * - 支持参数校验
 * - 支持多种输入来源（HTTP、Kafka）
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
     * 告警ID(必填,唯一标识告警)
     */
    @NotBlank(message = "告警ID不能为空")
    private String alertId;

    /**
     * 诊断类型(可选)
     */
    private String diagnosisType;

    /**
     * 问题描述(可选)
     */
    private String problem;

    /**
     * 告警原始数据
     */
    private Map<String, Object> alertData;

    /**
     * 初始参数(可选)
     * 可包含设备ID、通道信息、时间范围等
     */
    private Map<String, Object> parameters;

    /**
     * 诊断选项
     */
    private Map<String, Object> options;

    /**
     * 获取或创建选项Map
     * 
     * @return 选项Map
     */
    public Map<String, Object> getOptions() {
        if (options == null) {
            options = new HashMap<>();
        }
        return options;
    }

    /**
     * 获取或创建参数Map
     * 
     * @return 参数Map
     */
    public Map<String, Object> getParameters() {
        if (parameters == null) {
            parameters = new HashMap<>();
        }
        return parameters;
    }

    /**
     * 获取或创建告警数据Map
     * 
     * @return 告警数据Map
     */
    public Map<String, Object> getAlertData() {
        if (alertData == null) {
            alertData = new HashMap<>();
        }
        return alertData;
    }

    /**
     * 便捷方法：从parameters获取设备ID
     * 
     * @return 设备ID，如不存在返回null
     */
    public String getDeviceId() {
        return parameters != null ? (String) parameters.get("deviceId") : null;
    }

    /**
     * 便捷方法：设置设备ID
     * 
     * @param deviceId 设备ID
     */
    public void setDeviceId(String deviceId) {
        getParameters().put("deviceId", deviceId);
    }

    /**
     * 便捷方法：判断是否启用流式输出
     * 
     * @return 是否启用流式输出
     */
    public boolean isStreamingEnabled() {
        if (options == null) {
            return false;
        }
        Object streaming = options.get("streaming");
        return Boolean.TRUE.equals(streaming);
    }

    /**
     * 便捷方法：获取超时时间
     * 
     * @return 超时时间（毫秒），默认300000
     */
    public long getTimeout() {
        if (options == null) {
            return 300000L;
        }
        Object timeout = options.get("timeout");
        if (timeout instanceof Number) {
            return ((Number) timeout).longValue();
        }
        return 300000L;
    }

    /**
     * 获取问题描述
     * 
     * @return 问题描述
     */
    public String getProblemDescription() {
        return problem;
    }

    /**
     * 设置问题描述
     * 
     * @param problemDescription 问题描述
     */
    public void setProblemDescription(String problemDescription) {
        this.problem = problemDescription;
    }
}
