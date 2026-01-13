package com.company.diagnosis.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 响应适配器
 * <p>
 * 职责：
 * 1. 将底层接口调用的原始响应转换为上层智能体可用的结构
 * 2. 处理不同接口返回格式的差异
 * 3. 统一错误码和错误信息结构
 */
@Component
public class ResponseAdapter {

    private static final Logger logger = LoggerFactory.getLogger(ResponseAdapter.class);

    // 标准响应字段
    private static final String FIELD_SUCCESS = "success";
    private static final String FIELD_CODE = "code";
    private static final String FIELD_MESSAGE = "message";
    private static final String FIELD_DATA = "data";
    private static final String FIELD_ERROR = "error";

    /**
     * 适配HTTP接口响应
     *
     * @param rawResponse 接口原始响应（已解析为Map）
     * @param spec 接口规范信息（如字段映射规则）
     * @return 统一结构的响应数据
     */
    public Map<String, Object> adaptHttpResponse(Map<String, Object> rawResponse, Map<String, Object> spec) {
        if (rawResponse == null) {
            return createErrorResponse("EMPTY_RESPONSE", "接口返回为空");
        }

        Map<String, Object> result = new HashMap<>();
        
        // 获取字段映射规则
        Map<String, String> fieldMapping = extractFieldMapping(spec);
        
        // 提取成功标识
        boolean success = extractSuccessFlag(rawResponse, fieldMapping);
        result.put(FIELD_SUCCESS, success);
        
        // 提取错误码
        String code = extractCode(rawResponse, fieldMapping, success);
        result.put(FIELD_CODE, code);
        
        // 提取消息
        String message = extractMessage(rawResponse, fieldMapping);
        result.put(FIELD_MESSAGE, message);
        
        // 提取数据
        Object data = extractData(rawResponse, fieldMapping);
        if (data != null) {
            result.put(FIELD_DATA, data);
        }
        
        // 如果有错误信息，单独提取
        if (!success) {
            Object error = extractError(rawResponse, fieldMapping);
            if (error != null) {
                result.put(FIELD_ERROR, error);
            }
        }

        logger.debug("响应适配完成: success={}, code={}", success, code);
        return result;
    }

    /**
     * 适配分页响应
     *
     * @param rawResponse 原始响应
     * @param spec 接口规范
     * @return 包含分页信息的统一响应
     */
    public Map<String, Object> adaptPagedResponse(Map<String, Object> rawResponse, Map<String, Object> spec) {
        Map<String, Object> result = adaptHttpResponse(rawResponse, spec);
        
        // 提取分页信息
        Map<String, Object> pagination = new HashMap<>();
        pagination.put("page", extractValue(rawResponse, "page", "pageNum", "current"));
        pagination.put("pageSize", extractValue(rawResponse, "pageSize", "size", "limit"));
        pagination.put("total", extractValue(rawResponse, "total", "totalCount", "count"));
        pagination.put("totalPages", extractValue(rawResponse, "totalPages", "pages"));
        
        result.put("pagination", pagination);
        return result;
    }

    /**
     * 适配列表响应
     *
     * @param rawResponse 原始响应
     * @param listPath 列表数据路径
     * @return 统一的列表响应
     */
    public Map<String, Object> adaptListResponse(Map<String, Object> rawResponse, String listPath) {
        Map<String, Object> result = new HashMap<>();
        result.put(FIELD_SUCCESS, true);
        result.put(FIELD_CODE, "SUCCESS");
        
        Object listData = extractByPath(rawResponse, listPath);
        if (listData instanceof List) {
            result.put(FIELD_DATA, listData);
            result.put("count", ((List<?>) listData).size());
        } else {
            result.put(FIELD_DATA, rawResponse);
        }
        
        return result;
    }

    /**
     * 创建错误响应
     */
    public Map<String, Object> createErrorResponse(String code, String message) {
        Map<String, Object> result = new HashMap<>();
        result.put(FIELD_SUCCESS, false);
        result.put(FIELD_CODE, code);
        result.put(FIELD_MESSAGE, message);
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> extractFieldMapping(Map<String, Object> spec) {
        if (spec == null) {
            return new HashMap<>();
        }
        Object mapping = spec.get("fieldMapping");
        if (mapping instanceof Map) {
            return (Map<String, String>) mapping;
        }
        return new HashMap<>();
    }

    private boolean extractSuccessFlag(Map<String, Object> response, Map<String, String> mapping) {
        String successField = mapping.getOrDefault("success", "success");
        
        // 尝试多种成功标识
        Object value = extractValue(response, successField, "success", "status", "ok");
        
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            String str = ((String) value).toLowerCase();
            return "true".equals(str) || "success".equals(str) || "ok".equals(str);
        }
        if (value instanceof Number) {
            int code = ((Number) value).intValue();
            return code == 0 || code == 200;
        }
        
        // 检查是否有code字段为200或0
        Object codeValue = extractValue(response, "code", "statusCode", "retCode");
        if (codeValue instanceof Number) {
            int code = ((Number) codeValue).intValue();
            return code == 0 || code == 200;
        }
        
        return true; // 默认认为成功
    }

    private String extractCode(Map<String, Object> response, Map<String, String> mapping, boolean success) {
        String codeField = mapping.getOrDefault("code", "code");
        Object value = extractValue(response, codeField, "code", "statusCode", "retCode", "errCode");
        
        if (value != null) {
            return String.valueOf(value);
        }
        return success ? "SUCCESS" : "ERROR";
    }

    private String extractMessage(Map<String, Object> response, Map<String, String> mapping) {
        String messageField = mapping.getOrDefault("message", "message");
        Object value = extractValue(response, messageField, "message", "msg", "errMsg", "errorMessage", "description");
        return value != null ? String.valueOf(value) : null;
    }

    private Object extractData(Map<String, Object> response, Map<String, String> mapping) {
        String dataField = mapping.getOrDefault("data", "data");
        return extractValue(response, dataField, "data", "result", "body", "content");
    }

    private Object extractError(Map<String, Object> response, Map<String, String> mapping) {
        String errorField = mapping.getOrDefault("error", "error");
        return extractValue(response, errorField, "error", "errors", "errorInfo", "errorDetail");
    }

    private Object extractValue(Map<String, Object> map, String... keys) {
        if (map == null) {
            return null;
        }
        for (String key : keys) {
            if (key != null && map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Object extractByPath(Map<String, Object> map, String path) {
        if (map == null || path == null || path.isEmpty()) {
            return map;
        }
        
        String[] parts = path.split("\\.");
        Object current = map;
        
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
            } else {
                return null;
            }
        }
        
        return current;
    }
}