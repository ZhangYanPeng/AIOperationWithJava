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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JSON工具类
 * <p>
 * 职责：
 * 1. 提供JSON序列化和反序列化功能
 * 2. 提供JSON解析和操作工具方法
 * 3. 处理JSON格式转换
 * 4. 提供JSON验证功能
 * 5. 提供容错解析能力（处理LLM返回的非标准JSON）
 * <p>
 * 设计考虑：
 * - 使用Jackson作为JSON处理库
 * - 提供统一的错误处理
 * - 支持自定义序列化配置
 * - 提供常用的JSON操作方法
 * - 容错处理：支持提取```json代码块、移除&lt;think&gt;标签等
 *
 * @author Diagnosis System
 * @since 2026-01-13
 */
public class JsonUtil {

    private static final Logger logger = LoggerFactory.getLogger(JsonUtil.class);

    /**
     * ObjectMapper单例，配置了常用选项
     */
    private static final ObjectMapper objectMapper;

    /**
     * 用于美化输出的ObjectMapper
     */
    private static final ObjectMapper prettyMapper;

    /**
     * 匹配```json代码块的正则表达式
     */
    private static final Pattern JSON_CODE_BLOCK_PATTERN = Pattern.compile("```json\\s*([\\s\\S]*?)```", Pattern.MULTILINE);

    /**
     * 匹配<think>标签的正则表达式
     */
    private static final Pattern THINK_TAG_PATTERN = Pattern.compile("<think>[\\s\\S]*?</think>", Pattern.MULTILINE);

    /**
     * 匹配JSON对象的正则表达式
     */
    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile("\\{[\\s\\S]*\\}");

    static {
        objectMapper = new ObjectMapper();
        // 注册Java 8时间模块
        objectMapper.registerModule(new JavaTimeModule());
        // 忽略未知属性，提高容错性
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // 允许空对象
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        // 日期格式化为ISO-8601
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

        prettyMapper = objectMapper.copy();
        prettyMapper.enable(SerializationFeature.INDENT_OUTPUT);
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
     * @return JSON字符串，序列化失败时返回null
     */
    public static String toJson(Object object) {
        if (object == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            logger.error("JSON序列化失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 将JSON字符串反序列化为对象
     * <p>
     * 功能说明：
     * 将JSON字符串反序列化为指定类型的Java对象
     *
     * @param json  JSON字符串
     * @param clazz 目标类型
     * @param <T>   类型参数
     * @return Java对象，反序列化失败时返回null
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            logger.error("JSON反序列化失败: {}, JSON前100字符: {}", e.getMessage(), 
                    json.length() > 100 ? json.substring(0, 100) : json);
            return null;
        }
    }

    /**
     * 将JSON字符串反序列化为指定类型（支持泛型）
     *
     * @param json          JSON字符串
     * @param typeReference 类型引用
     * @param <T>           类型参数
     * @return Java对象，反序列化失败时返回null
     */
    public static <T> T fromJson(String json, TypeReference<T> typeReference) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            logger.error("JSON反序列化失败: {}", e.getMessage());
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
     * @return JsonNode对象，解析失败时返回null
     */
    public static JsonNode parseJson(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            logger.error("JSON解析失败: {}", e.getMessage());
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
     * @return Map对象，解析失败时返回空Map
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> jsonToMap(String json) {
        if (json == null || json.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            logger.error("JSON转Map失败: {}", e.getMessage());
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
     * @return 格式化后的JSON字符串，格式化失败时返回原字符串
     */
    public static String prettyPrint(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            return prettyMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            logger.warn("JSON美化失败: {}", e.getMessage());
            return json;
        }
    }

    /**
     * 美化对象为JSON字符串
     *
     * @param object Java对象
     * @return 格式化后的JSON字符串
     */
    public static String prettyPrint(Object object) {
        if (object == null) {
            return null;
        }
        try {
            return prettyMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            logger.warn("对象美化输出失败: {}", e.getMessage());
            return null;
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
     * 容错解析JSON（处理LLM返回的非标准格式）
     * <p>
     * 功能说明：
     * 从可能包含```json代码块、&lt;think&gt;标签等的文本中提取并解析JSON
     *
     * @param text 可能包含JSON的文本
     * @return Map对象，解析失败时返回空Map
     */
    public static Map<String, Object> extractAndParseJson(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyMap();
        }

        String cleanedText = text;

        // 1. 移除<think>标签内容
        cleanedText = THINK_TAG_PATTERN.matcher(cleanedText).replaceAll("");
        cleanedText = cleanedText.replace("</think>", "").trim();

        // 2. 尝试从```json代码块中提取
        Matcher codeBlockMatcher = JSON_CODE_BLOCK_PATTERN.matcher(cleanedText);
        if (codeBlockMatcher.find()) {
            String jsonContent = codeBlockMatcher.group(1).trim();
            Map<String, Object> result = jsonToMap(jsonContent);
            if (!result.isEmpty()) {
                logger.debug("成功从代码块中提取JSON");
                return result;
            }
        }

        // 3. 尝试直接解析
        Map<String, Object> directResult = jsonToMap(cleanedText);
        if (!directResult.isEmpty()) {
            return directResult;
        }

        // 4. 尝试提取JSON对象
        Matcher jsonMatcher = JSON_OBJECT_PATTERN.matcher(cleanedText);
        if (jsonMatcher.find()) {
            String jsonObject = jsonMatcher.group(0);
            Map<String, Object> result = jsonToMap(jsonObject);
            if (!result.isEmpty()) {
                logger.debug("成功从文本中提取JSON对象");
                return result;
            }
        }

        logger.warn("无法从文本中提取有效JSON，文本前200字符: {}", 
                text.length() > 200 ? text.substring(0, 200) : text);
        return Collections.emptyMap();
    }

    /**
     * 提取&lt;think&gt;标签中的内容
     *
     * @param text 包含think标签的文本
     * @return think标签中的内容，如果没有则返回空字符串
     */
    public static String extractThinkingContent(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        Pattern pattern = Pattern.compile("<think>([\\s\\S]*?)</think>");
        Matcher matcher = pattern.matcher(text);
        StringBuilder thinking = new StringBuilder();
        while (matcher.find()) {
            if (thinking.length() > 0) {
                thinking.append("\n");
            }
            thinking.append(matcher.group(1).trim());
        }
        return thinking.toString();
    }

    /**
     * 移除文本中的&lt;think&gt;标签及其内容
     *
     * @param text 原始文本
     * @return 移除think标签后的文本
     */
    public static String removeThinkingContent(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = THINK_TAG_PATTERN.matcher(text).replaceAll("");
        return result.replace("</think>", "").trim();
    }

    /**
     * 将对象转换为另一个类型
     *
     * @param source      源对象
     * @param targetClass 目标类型
     * @param <T>         类型参数
     * @return 转换后的对象，转换失败时返回null
     */
    public static <T> T convertValue(Object source, Class<T> targetClass) {
        if (source == null) {
            return null;
        }
        try {
            return objectMapper.convertValue(source, targetClass);
        } catch (IllegalArgumentException e) {
            logger.error("对象类型转换失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 将对象转换为另一个类型（支持泛型）
     *
     * @param source        源对象
     * @param typeReference 类型引用
     * @param <T>           类型参数
     * @return 转换后的对象，转换失败时返回null
     */
    public static <T> T convertValue(Object source, TypeReference<T> typeReference) {
        if (source == null) {
            return null;
        }
        try {
            return objectMapper.convertValue(source, typeReference);
        } catch (IllegalArgumentException e) {
            logger.error("对象类型转换失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 将Map转换为美化的JSON字符串
     *
     * @param map Map对象
     * @return 格式化后的JSON字符串
     */
    public static String toPrettyJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        return prettyPrint(map);
    }
}
