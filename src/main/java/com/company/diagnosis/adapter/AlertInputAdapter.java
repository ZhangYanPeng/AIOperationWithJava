package com.company.diagnosis.adapter;

import com.company.diagnosis.model.dto.DiagnosisRequest;
import com.company.diagnosis.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 告警输入适配器
 * <p>
 * 职责：
 * 1. 适配不同来源的告警输入
 * 2. 转换为统一的DiagnosisRequest格式
 * 3. 验证输入数据的完整性
 *
 * @author Diagnosis System
 * @date 2026-01-13
 */
@Component
public class AlertInputAdapter {

    private static final Logger log = LoggerFactory.getLogger(AlertInputAdapter.class);

    /**
     * 适配Kafka消息
     * <p>
     * Kafka消息格式:
     * {
     *   "alertId": "告警ID",
     *   "alertType": "告警类型",
     *   "deviceId": "设备ID",
     *   "alertTime": "告警时间",
     *   "alertContent": "告警内容",
     *   "severity": "严重程度",
     *   "extra": {...}
     * }
     *
     * @param kafkaMessage Kafka消息
     * @return 诊断请求
     */
    public DiagnosisRequest adaptFromKafka(Map<String, Object> kafkaMessage) {
        if (kafkaMessage == null || kafkaMessage.isEmpty()) {
            throw new IllegalArgumentException("Kafka消息不能为空");
        }

        log.info("适配Kafka消息: {}", kafkaMessage.keySet());

        DiagnosisRequest request = new DiagnosisRequest();

        // 1. 提取alertId（必填）
        String alertId = extractString(kafkaMessage, "alertId", "alert_id", "id");
        if (!StringUtils.hasText(alertId)) {
            alertId = UUID.randomUUID().toString().replace("-", "");
            log.warn("Kafka消息缺少alertId,自动生成: {}", alertId);
        }
        request.setAlertId(alertId);

        // 2. 提取requestId
        String requestId = extractString(kafkaMessage, "requestId", "request_id", "traceId");
        if (!StringUtils.hasText(requestId)) {
            requestId = UUID.randomUUID().toString();
        }
        request.setRequestId(requestId);

        // 3. 提取诊断类型
        String diagnosisType = extractString(kafkaMessage, "alertType", "alert_type", "type", "diagnosisType");
        request.setDiagnosisType(diagnosisType);

        // 4. 提取问题描述
        String problem = extractString(kafkaMessage, "alertContent", "alert_content", "content", "problem", "message");
        request.setProblem(problem);

        // 5. 提取参数
        Map<String, Object> parameters = new HashMap<>();
        
        String deviceId = extractString(kafkaMessage, "deviceId", "device_id");
        if (StringUtils.hasText(deviceId)) {
            parameters.put("deviceId", deviceId);
        }
        
        String alertTime = extractString(kafkaMessage, "alertTime", "alert_time", "timestamp");
        if (StringUtils.hasText(alertTime)) {
            parameters.put("alertTime", alertTime);
        }
        
        String severity = extractString(kafkaMessage, "severity", "level", "priority");
        if (StringUtils.hasText(severity)) {
            parameters.put("severity", severity);
        }

        // 合并extra字段
        @SuppressWarnings("unchecked")
        Map<String, Object> extra = (Map<String, Object>) kafkaMessage.get("extra");
        if (extra != null) {
            parameters.putAll(extra);
        }

        request.setParameters(parameters);

        // 6. 保存原始告警数据
        request.setAlertData(new HashMap<>(kafkaMessage));

        log.info("Kafka消息适配完成: alertId={}, type={}", alertId, diagnosisType);
        return request;
    }

    /**
     * 适配HTTP请求
     * <p>
     * HTTP请求格式（更灵活）:
     * {
     *   "alertId": "告警ID",
     *   "problem": "问题描述",
     *   "parameters": {...},
     *   "options": {...}
     * }
     *
     * @param httpRequest HTTP请求数据
     * @return 诊断请求
     */
    public DiagnosisRequest adaptFromHttp(Map<String, Object> httpRequest) {
        if (httpRequest == null || httpRequest.isEmpty()) {
            throw new IllegalArgumentException("HTTP请求数据不能为空");
        }

        log.info("适配HTTP请求: {}", httpRequest.keySet());

        DiagnosisRequest request = new DiagnosisRequest();

        // 1. 提取alertId
        String alertId = extractString(httpRequest, "alertId", "alert_id");
        if (!StringUtils.hasText(alertId)) {
            alertId = UUID.randomUUID().toString().replace("-", "");
        }
        request.setAlertId(alertId);

        // 2. 提取requestId
        String requestId = extractString(httpRequest, "requestId", "request_id");
        request.setRequestId(requestId);

        // 3. 提取sessionId
        String sessionId = extractString(httpRequest, "sessionId", "session_id");
        request.setSessionId(sessionId);

        // 4. 提取诊断类型和问题描述
        request.setDiagnosisType(extractString(httpRequest, "diagnosisType", "diagnosis_type", "type"));
        request.setProblem(extractString(httpRequest, "problem", "description", "content"));

        // 5. 提取参数
        @SuppressWarnings("unchecked")
        Map<String, Object> parameters = (Map<String, Object>) httpRequest.get("parameters");
        if (parameters != null) {
            request.setParameters(new HashMap<>(parameters));
        }

        // 6. 提取选项
        @SuppressWarnings("unchecked")
        Map<String, Object> options = (Map<String, Object>) httpRequest.get("options");
        if (options != null) {
            request.setOptions(new HashMap<>(options));
        }

        // 7. 保存原始数据
        @SuppressWarnings("unchecked")
        Map<String, Object> alertData = (Map<String, Object>) httpRequest.get("alertData");
        if (alertData != null) {
            request.setAlertData(alertData);
        }

        log.info("HTTP请求适配完成: alertId={}", alertId);
        return request;
    }

    /**
     * 从Map中提取字符串值（支持多个可能的键名）
     */
    private String extractString(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) {
                return value.toString();
            }
        }
        return null;
    }
}
