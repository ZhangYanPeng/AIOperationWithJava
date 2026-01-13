package com.company.diagnosis.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;

/**
 * JSON工具类
 * <p>
 * 职责：
 * 1. 提供JSON序列化和反序列化功能
 * 2. 提供JSON解析和操作工具方法
 * 3. 处理JSON格式转换
 * 4. 提供JSON验证功能
 * <p>
 * 设计考虑：
 * - 使用Jackson作为JSON处理库
 * - 提供统一的错误处理
 * - 支持自定义序列化配置
 * - 提供常用的JSON操作方法
 *
 * @author Diagnosis System
 * @date 2026-01-13
 */
public class JsonUtil {

    private static final Logger logger = LoggerFactory.getLogger(JsonUtil.class);
    
    private static final ObjectMapper objectMapper;
    
    static {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    /**
     * 获取ObjectMapper实例
     *
     * @return ObjectMapper实例
     */
    public static ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /**
     * 将对象序列化为JSON字符串
     * <p>
     * 功能说明：
     * 将Java对象序列化为JSON格式字符串
     *
     * @param object Java对象
     * @return JSON字符串
     */
    public static String toJson(Object object) {
        if (object == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            logger.error("对象序列化为JSON失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 将JSON字符串反序列化为对象
     * <p>
     * 功能说明：
     * 将JSON字符串反序列化为指定类型的Java对象
     *
     * @param json JSON字符串
     * @param clazz 目标类型
     * @param <T> 类型参数
     * @return Java对象
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            logger.error("JSON反序列化失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 将JSON字符串解析为JsonNode
     * <p>
     * 功能说明：
     * 将JSON字符串解析为Jackson的JsonNode对象
     *
     * @param json JSON字符串
     * @return JsonNode对象
     */
    public static JsonNode parseJson(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            logger.error("JSON解析失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 将Map转换为JSON字符串
     * <p>
     * 功能说明：
     * 将Map对象转换为JSON字符串
     *
     * @param map Map对象
     * @return JSON字符串
     */
    public static String mapToJson(Map<String, Object> map) {
        return toJson(map);
    }

    /**
     * 将JSON字符串转换为Map
     * <p>
     * 功能说明：
     * 将JSON字符串转换为Map对象
     *
     * @param json JSON字符串
     * @return Map对象
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> jsonToMap(String json) {
        if (json == null || json.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            logger.error("JSON转Map失败: {}", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    /**
     * 美化JSON字符串
     * <p>
     * 功能说明：
     * 格式化JSON字符串，添加缩进和换行
     *
     * @param json JSON字符串
     * @return 格式化后的JSON字符串
     */
    public static String prettyPrint(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        try {
            Object jsonObject = objectMapper.readValue(json, Object.class);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObject);
        } catch (JsonProcessingException e) {
            logger.error("JSON美化失败: {}", e.getMessage(), e);
            return json;
        }
    }

    /**
     * 验证JSON格式
     * <p>
     * 功能说明：
     * 验证字符串是否为合法的JSON格式
     *
     * @param json JSON字符串
     * @return 是否合法
     */
    public static Boolean isValidJson(String json) {
        if (json == null || json.isEmpty()) {
            return false;
        }
        try {
            objectMapper.readTree(json);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }
    
    /**
     * 将对象转换为指定类型
     *
     * @param object 源对象
     * @param clazz 目标类型
     * @param <T> 类型参数
     * @return 转换后的对象
     */
    public static <T> T convertValue(Object object, Class<T> clazz) {
        if (object == null) {
            return null;
        }
        return objectMapper.convertValue(object, clazz);
    }
}