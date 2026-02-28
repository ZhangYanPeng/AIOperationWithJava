package com.company.diagnosis.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 响应适配器
 * <p>
 * 职责：
 * 1. 将底层接口调用的原始响应转换为上层智能体可用的结构
 * 2. 处理不同接口返回格式的差异
 * 3. 统一错误码和错误信息结构
 *
 * @author Diagnosis System
 * @date 2026-01-13
 */
@Component
public class ResponseAdapter {

    private static final Logger log = LoggerFactory.getLogger(ResponseAdapter.class);

    /**
     * 适配HTTP接口响应
     * <p>
     * 功能说明：
     * 将原始HTTP响应转换为统一格式
     *
     * @param rawResponse 接口原始响应（已解析为Map）
     * @param spec 接口规范信息（如字段映射规则）
     * @return 统一结构的响应数据
     */
    public Map<String, Object> adaptHttpResponse(Map<String, Object> rawResponse, Map<String, Object> spec) {
        if (rawResponse == null) {
            return createErrorResponse("RAW_RESPONSE_NULL", "原始响应为空");
        }

        log.debug("适配HTTP响应: rawKeys={}", rawResponse.keySet());

        Map<String, Object> result = new LinkedHashMap<>();

        // 1. 判断响应是否成功
        boolean success = determineSuccess(rawResponse);
        result.put("success", success);

        // 2. 提取数据
        Object data = extractData(rawResponse);
        if (data != null) {
            result.put("data", data);
        }

        // 3. 提取错误信息（如果有）
        if (!success) {
            String errorCode = extractErrorCode(rawResponse);
            String errorMsg = extractErrorMessage(rawResponse);
            result.put("errorCode", errorCode);
            result.put("errorMessage", errorMsg);
        }

        // 4. 应用字段映射规则（如果提供了spec）
        if (spec != null && !spec.isEmpty()) {
            result = applyFieldMapping(result, spec);
        }

        // 5. 添加元数据
        result.put("_adapted", true);
        result.put("_adaptedAt", System.currentTimeMillis());

        log.debug("HTTP响应适配完成: success={}", success);
        return result;
    }

    /**
     * 适配SSE流式响应
     *
     * @param sseEvent SSE事件数据
     * @return 统一格式的事件数据
     */
    public Map<String, Object> adaptSseEvent(Map<String, Object> sseEvent) {
        if (sseEvent == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> adapted = new LinkedHashMap<>();

        // 提取事件类型
        String eventType = extractString(sseEvent, "type", "event", "eventType");
        adapted.put("type", eventType != null ? eventType : "message");

        // 提取数据
        Object data = sseEvent.get("data");
        if (data != null) {
            adapted.put("data", data);
        }

        // 提取其他字段
        sseEvent.forEach((k, v) -> {
            if (!"type".equals(k) && !"event".equals(k) && !"eventType".equals(k) && !"data".equals(k)) {
                adapted.put(k, v);
            }
        });

        return adapted;
    }

    /**
     * 适配错误响应
     *
     * @param error 错误对象
     * @return 统一格式的错误响应
     */
    public Map<String, Object> adaptErrorResponse(Throwable error) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("errorCode", "INTERNAL_ERROR");
        result.put("errorMessage", error != null ? error.getMessage() : "未知错误");

        if (error != null) {
            result.put("errorType", error.getClass().getSimpleName());
        }

        return result;
    }

    /**
     * 判断响应是否成功
     */
    private boolean determineSuccess(Map<String, Object> response) {
        // 检查常见的成功标志
        Object success = response.get("success");
        if (success instanceof Boolean) {
            return (Boolean) success;
        }

        Object status = response.get("status");
        if (status != null) {
            String statusStr = status.toString().toLowerCase();
            if ("success".equals(statusStr) || "ok".equals(statusStr)) {
                return true;
            }
            if ("error".equals(statusStr) || "fail".equals(statusStr) || "failed".equals(statusStr)) {
                return false;
            }
        }

        Object code = response.get("code");
        if (code instanceof Number) {
            int codeInt = ((Number) code).intValue();
            return codeInt == 0 || codeInt == 200;
        }
        if (code instanceof String) {
            return "0".equals(code) || "200".equals(code) || "OK".equalsIgnoreCase((String) code);
        }

        // 如果有数据且没有error字段，默认成功
        return response.containsKey("data") && !response.containsKey("error");
    }

    /**
     * 提取数据部分
     */
    private Object extractData(Map<String, Object> response) {
        // 常见的数据字段名
        String[] dataKeys = {"data", "result", "body", "payload", "content"};

        for (String key : dataKeys) {
            if (response.containsKey(key)) {
                return response.get(key);
            }
        }

        // 如果没有明确的数据字段，返回整个响应（排除元数据）
        Map<String, Object> cleaned = new LinkedHashMap<>(response);
        cleaned.remove("success");
        cleaned.remove("status");
        cleaned.remove("code");
        cleaned.remove("message");
        cleaned.remove("error");
        cleaned.remove("errorCode");
        cleaned.remove("errorMessage");

        return cleaned.isEmpty() ? null : cleaned;
    }

    /**
     * 提取错误码
     */
    private String extractErrorCode(Map<String, Object> response) {
        Object code = response.get("errorCode");
        if (code == null) {
            code = response.get("error_code");
        }
        if (code == null) {
            code = response.get("code");
        }

        return code != null ? code.toString() : "UNKNOWN_ERROR";
    }

    /**
     * 提取错误信息
     */
    private String extractErrorMessage(Map<String, Object> response) {
        Object msg = response.get("errorMessage");
        if (msg == null) {
            msg = response.get("error_message");
        }
        if (msg == null) {
            msg = response.get("message");
        }
        if (msg == null) {
            msg = response.get("error");
        }
        if (msg == null) {
            msg = response.get("msg");
        }

        return msg != null ? msg.toString() : "未知错误";
    }

    /**
     * 应用字段映射规则
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> applyFieldMapping(Map<String, Object> data, Map<String, Object> spec) {
        Map<String, String> fieldMapping = (Map<String, String>) spec.get("fieldMapping");
        if (fieldMapping == null || fieldMapping.isEmpty()) {
            return data;
        }

        Map<String, Object> result = new LinkedHashMap<>(data);
        Object dataObj = data.get("data");

        if (dataObj instanceof Map) {
            Map<String, Object> dataMap = new LinkedHashMap<>((Map<String, Object>) dataObj);

            for (Map.Entry<String, String> mapping : fieldMapping.entrySet()) {
                String sourceField = mapping.getKey();
                String targetField = mapping.getValue();

                if (dataMap.containsKey(sourceField)) {
                    Object value = dataMap.remove(sourceField);
                    dataMap.put(targetField, value);
                }
            }

            result.put("data", dataMap);
        }

        return result;
    }

    /**
     * 创建错误响应
     */
    private Map<String, Object> createErrorResponse(String code, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("errorCode", code);
        result.put("errorMessage", message);
        return result;
    }

    /**
     * 从Map中提取字符串
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
