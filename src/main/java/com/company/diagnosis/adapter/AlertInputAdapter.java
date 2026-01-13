package com.company.diagnosis.adapter;

import com.company.diagnosis.model.dto.DiagnosisRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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

    private static final Logger logger = LoggerFactory.getLogger(AlertInputAdapter.class);

    // Kafka消息字段
    private static final String KAFKA_ALERT_ID = "alertId";
    private static final String KAFKA_ALERT_TYPE = "alertType";
    private static final String KAFKA_ALERT_MESSAGE = "alertMessage";
    private static final String KAFKA_DEVICE_ID = "deviceId";
    private static final String KAFKA_TIMESTAMP = "timestamp";
    private static final String KAFKA_SEVERITY = "severity";
    private static final String KAFKA_SOURCE = "source";
    private static final String KAFKA_EXTRA_DATA = "extraData";

    /**
     * 适配Kafka消息
     *
     * @param kafkaMessage Kafka消息
     * @return 诊断请求
     */
    public DiagnosisRequest adaptFromKafka(Map<String, Object> kafkaMessage) {
        if (kafkaMessage == null || kafkaMessage.isEmpty()) {
            logger.warn("Kafka消息为空，无法适配");
            return null;
        }

        DiagnosisRequest request = new DiagnosisRequest();
        
        // 设置请求ID
        String alertId = getStringValue(kafkaMessage, KAFKA_ALERT_ID);
        request.setRequestId(alertId != null ? alertId : UUID.randomUUID().toString());
        
        // 设置诊断类型
        String alertType = getStringValue(kafkaMessage, KAFKA_ALERT_TYPE);
        request.setDiagnosisType(mapAlertTypeToDiagnosisType(alertType));
        
        // 设置问题描述
        String alertMessage = getStringValue(kafkaMessage, KAFKA_ALERT_MESSAGE);
        if (alertMessage == null || alertMessage.isEmpty()) {
            alertMessage = buildProblemDescription(kafkaMessage);
        }
        request.setProblem(alertMessage);
        
        // 构建参数
        Map<String, Object> parameters = buildParameters(kafkaMessage);
        request.setParameters(parameters);
        
        // 设置诊断选项
        DiagnosisRequest.DiagnosisOptions options = buildOptions(kafkaMessage);
        request.setOptions(options);
        
        logger.info("Kafka消息适配完成: requestId={}, type={}", request.getRequestId(), request.getDiagnosisType());
        return request;
    }

    /**
     * 适配HTTP请求
     *
     * @param httpRequest HTTP请求数据
     * @return 诊断请求
     */
    public DiagnosisRequest adaptFromHttp(Map<String, Object> httpRequest) {
        if (httpRequest == null || httpRequest.isEmpty()) {
            logger.warn("HTTP请求数据为空，无法适配");
            return null;
        }

        DiagnosisRequest request = new DiagnosisRequest();
        
        // 设置请求ID
        String requestId = getStringValue(httpRequest, "requestId", "request_id", "id");
        request.setRequestId(requestId != null ? requestId : UUID.randomUUID().toString());
        
        // 设置会话ID
        String sessionId = getStringValue(httpRequest, "sessionId", "session_id");
        request.setSessionId(sessionId);
        
        // 设置诊断类型
        String diagnosisType = getStringValue(httpRequest, "diagnosisType", "diagnosis_type", "type");
        request.setDiagnosisType(diagnosisType != null ? diagnosisType : "general");
        
        // 设置问题描述
        String problem = getStringValue(httpRequest, "problem", "question", "description", "query");
        request.setProblem(problem);
        
        // 设置参数
        Object params = httpRequest.get("parameters");
        if (params == null) {
            params = httpRequest.get("params");
        }
        if (params instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> paramMap = (Map<String, Object>) params;
            request.setParameters(paramMap);
        }
        
        // 设置选项
        Object opts = httpRequest.get("options");
        if (opts instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> optMap = (Map<String, Object>) opts;
            DiagnosisRequest.DiagnosisOptions options = mapToOptions(optMap);
            request.setOptions(options);
        }
        
        logger.info("HTTP请求适配完成: requestId={}, type={}", request.getRequestId(), request.getDiagnosisType());
        return request;
    }

    /**
     * 从WebSocket消息适配
     *
     * @param wsMessage WebSocket消息
     * @return 诊断请求
     */
    public DiagnosisRequest adaptFromWebSocket(Map<String, Object> wsMessage) {
        // WebSocket消息格式与HTTP类似
        return adaptFromHttp(wsMessage);
    }

    private String mapAlertTypeToDiagnosisType(String alertType) {
        if (alertType == null || alertType.isEmpty()) {
            return "general";
        }
        
        // 映射告警类型到诊断类型
        switch (alertType.toLowerCase()) {
            case "cpu_high":
            case "memory_high":
            case "disk_full":
                return "resource";
            case "network_error":
            case "connection_failed":
            case "timeout":
                return "network";
            case "service_down":
            case "process_crash":
                return "service";
            case "security_alert":
            case "intrusion_detected":
                return "security";
            default:
                return alertType;
        }
    }

    private String buildProblemDescription(Map<String, Object> message) {
        StringBuilder sb = new StringBuilder();
        
        String alertType = getStringValue(message, KAFKA_ALERT_TYPE);
        if (alertType != null) {
            sb.append("告警类型: ").append(alertType);
        }
        
        String severity = getStringValue(message, KAFKA_SEVERITY);
        if (severity != null) {
            sb.append(", 严重程度: ").append(severity);
        }
        
        String deviceId = getStringValue(message, KAFKA_DEVICE_ID);
        if (deviceId != null) {
            sb.append(", 设备: ").append(deviceId);
        }
        
        String source = getStringValue(message, KAFKA_SOURCE);
        if (source != null) {
            sb.append(", 来源: ").append(source);
        }
        
        return sb.length() > 0 ? sb.toString() : "未知告警";
    }

    private Map<String, Object> buildParameters(Map<String, Object> message) {
        Map<String, Object> parameters = new HashMap<>();
        
        // 提取关键参数
        copyIfPresent(message, parameters, KAFKA_DEVICE_ID, "deviceId");
        copyIfPresent(message, parameters, KAFKA_TIMESTAMP, "alertTime");
        copyIfPresent(message, parameters, KAFKA_SEVERITY, "severity");
        copyIfPresent(message, parameters, KAFKA_SOURCE, "source");
        
        // 提取额外数据
        Object extraData = message.get(KAFKA_EXTRA_DATA);
        if (extraData instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> extra = (Map<String, Object>) extraData;
            parameters.putAll(extra);
        }
        
        return parameters;
    }

    private DiagnosisRequest.DiagnosisOptions buildOptions(Map<String, Object> message) {
        DiagnosisRequest.DiagnosisOptions options = new DiagnosisRequest.DiagnosisOptions();
        
        // 根据严重程度决定是否启用深度分析
        String severity = getStringValue(message, KAFKA_SEVERITY);
        if ("critical".equalsIgnoreCase(severity) || "high".equalsIgnoreCase(severity)) {
            options.setEnableDeepAnalysis(true);
            options.setRequireExpertOpinion(true);
        } else {
            options.setEnableDeepAnalysis(false);
            options.setRequireExpertOpinion(false);
        }
        
        // 默认流式输出
        options.setStreaming(true);
        options.setTimeout(300000L); // 5分钟
        options.setMaxSteps(10);
        
        return options;
    }

    private DiagnosisRequest.DiagnosisOptions mapToOptions(Map<String, Object> optMap) {
        DiagnosisRequest.DiagnosisOptions options = new DiagnosisRequest.DiagnosisOptions();
        
        Object enableDeep = optMap.get("enableDeepAnalysis");
        if (enableDeep instanceof Boolean) {
            options.setEnableDeepAnalysis((Boolean) enableDeep);
        }
        
        Object requireExpert = optMap.get("requireExpertOpinion");
        if (requireExpert instanceof Boolean) {
            options.setRequireExpertOpinion((Boolean) requireExpert);
        }
        
        Object timeout = optMap.get("timeout");
        if (timeout instanceof Number) {
            options.setTimeout(((Number) timeout).longValue());
        }
        
        Object streaming = optMap.get("streaming");
        if (streaming instanceof Boolean) {
            options.setStreaming((Boolean) streaming);
        }
        
        Object maxSteps = optMap.get("maxSteps");
        if (maxSteps instanceof Number) {
            options.setMaxSteps(((Number) maxSteps).intValue());
        }
        
        return options;
    }

    private String getStringValue(Map<String, Object> map, String... keys) {
        if (map == null) {
            return null;
        }
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, 
                               String sourceKey, String targetKey) {
        Object value = source.get(sourceKey);
        if (value != null) {
            target.put(targetKey, value);
        }
    }
}