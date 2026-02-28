package com.company.diagnosis.llm;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Map;

/**
 * LLM响应封装类
 * <p>
 * 职责：
 * - 封装LLM调用的响应结果
 * - 支持成功/失败状态
 * - 支持结构化数据和原始响应
 *
 * @author Diagnosis System
 * @since 2026-01-13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmResponse {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 解析后的数据（JSON格式响应时使用）
     */
    private Map<String, Object> data;

    /**
     * 原始响应文本
     */
    private String rawResponse;

    /**
     * 错误信息（失败时使用）
     */
    private String error;

    /**
     * 错误类型
     */
    private String errorType;

    /**
     * 是否为容错解析的结果
     */
    private boolean fallbackParsed;

    /**
     * 思维链内容（如果有）
     */
    private String thinkingContent;

    /**
     * 使用的模型名称
     */
    private String model;

    /**
     * 响应耗时（毫秒）
     */
    private Long durationMs;

    /**
     * 创建成功响应
     *
     * @param data        解析后的数据
     * @param rawResponse 原始响应
     * @return LlmResponse实例
     */
    public static LlmResponse success(Map<String, Object> data, String rawResponse) {
        return LlmResponse.builder()
                .success(true)
                .data(data)
                .rawResponse(rawResponse)
                .build();
    }

    /**
     * 创建成功响应（带思维链）
     *
     * @param data            解析后的数据
     * @param rawResponse     原始响应
     * @param thinkingContent 思维链内容
     * @return LlmResponse实例
     */
    public static LlmResponse success(Map<String, Object> data, String rawResponse, String thinkingContent) {
        return LlmResponse.builder()
                .success(true)
                .data(data)
                .rawResponse(rawResponse)
                .thinkingContent(thinkingContent)
                .build();
    }

    /**
     * 创建容错解析成功响应
     *
     * @param data        解析后的数据
     * @param rawResponse 原始响应
     * @return LlmResponse实例
     */
    public static LlmResponse successWithFallback(Map<String, Object> data, String rawResponse) {
        return LlmResponse.builder()
                .success(true)
                .data(data)
                .rawResponse(rawResponse)
                .fallbackParsed(true)
                .build();
    }

    /**
     * 创建失败响应
     *
     * @param error     错误信息
     * @param errorType 错误类型
     * @return LlmResponse实例
     */
    public static LlmResponse error(String error, String errorType) {
        return LlmResponse.builder()
                .success(false)
                .error(error)
                .errorType(errorType)
                .build();
    }

    /**
     * 创建失败响应（带原始响应）
     *
     * @param error       错误信息
     * @param errorType   错误类型
     * @param rawResponse 原始响应
     * @return LlmResponse实例
     */
    public static LlmResponse error(String error, String errorType, String rawResponse) {
        return LlmResponse.builder()
                .success(false)
                .error(error)
                .errorType(errorType)
                .rawResponse(rawResponse)
                .build();
    }

    /**
     * 创建超时错误响应
     *
     * @param timeoutSeconds 超时秒数
     * @return LlmResponse实例
     */
    public static LlmResponse timeout(int timeoutSeconds) {
        return LlmResponse.builder()
                .success(false)
                .error("请求超时(" + timeoutSeconds + "秒)")
                .errorType("timeout")
                .build();
    }

    /**
     * 创建JSON解析错误响应
     *
     * @param rawResponse 原始响应
     * @return LlmResponse实例
     */
    public static LlmResponse jsonParseError(String rawResponse) {
        return LlmResponse.builder()
                .success(false)
                .error("JSON解析失败")
                .errorType("json_parse_error")
                .rawResponse(rawResponse)
                .build();
    }

    /**
     * 获取数据中的字符串值
     *
     * @param key 键名
     * @return 字符串值，不存在时返回null
     */
    public String getDataString(String key) {
        if (data == null || key == null) {
            return null;
        }
        Object value = data.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    /**
     * 获取数据中的布尔值
     *
     * @param key 键名
     * @return 布尔值，不存在时返回false
     */
    public boolean getDataBoolean(String key) {
        if (data == null || key == null) {
            return false;
        }
        Object value = data.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return false;
    }

    /**
     * 获取数据中的整数值
     *
     * @param key 键名
     * @return 整数值，不存在时返回null
     */
    public Integer getDataInteger(String key) {
        if (data == null || key == null) {
            return null;
        }
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 判断是否有错误
     *
     * @return 是否有错误
     */
    public boolean hasError() {
        return !success || error != null;
    }

    /**
     * 判断是否有数据
     *
     * @return 是否有数据
     */
    public boolean hasData() {
        return data != null && !data.isEmpty();
    }

    /**
     * 获取解析后的JSON数据
     * 
     * @return JSON数据Map
     */
    public Map<String, Object> getParsedJson() {
        return data;
    }
}
