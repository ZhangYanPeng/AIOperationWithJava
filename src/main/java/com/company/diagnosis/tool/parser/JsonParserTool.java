package com.company.diagnosis.tool.parser;

import com.company.diagnosis.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * JSON解析工具
 * <p>
 * 职责：
 * 1. 解析HTTP接口返回的JSON结构
 * 2. 提取指定字段或路径的数据
 * 3. 提供通用的JSON遍历与转换能力
 */
@Component
public class JsonParserTool {

    private static final Logger logger = LoggerFactory.getLogger(JsonParserTool.class);

    /**
     * 按JSON路径提取字段
     *
     * @param json 原始JSON字符串
     * @param jsonPath JSON路径表达式（如"$.data.items[0].id"）
     * @return 提取出的字段值，未找到时返回null
     */
    public Object extractByPath(String json, String jsonPath) {
        if (json == null || json.isEmpty() || jsonPath == null || jsonPath.isEmpty()) {
            return null;
        }

        try {
            JsonNode root = JsonUtil.parseJson(json);
            if (root == null) {
                return null;
            }

            // 处理JSON路径
            String path = jsonPath;
            if (path.startsWith("$.")) {
                path = path.substring(2);
            } else if (path.startsWith("$")) {
                path = path.substring(1);
            }

            JsonNode result = navigatePath(root, path);
            return convertJsonNodeToObject(result);
        } catch (Exception e) {
            logger.error("JSON路径提取失败: path={}, error={}", jsonPath, e.getMessage());
            return null;
        }
    }

    /**
     * 将JSON解析为Map
     *
     * @param json 原始JSON字符串
     * @return JSON对应的Map结构
     */
    public Map<String, Object> parseToMap(String json) {
        if (json == null || json.isEmpty()) {
            return new HashMap<>();
        }
        return JsonUtil.jsonToMap(json);
    }

    /**
     * 将JSON解析为列表
     *
     * @param json 原始JSON字符串
     * @return JSON对应的List结构
     */
    public List<Object> parseToList(String json) {
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            JsonNode root = JsonUtil.parseJson(json);
            if (root == null || !root.isArray()) {
                return new ArrayList<>();
            }
            return convertArrayNode((ArrayNode) root);
        } catch (Exception e) {
            logger.error("JSON解析为List失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 提取多个字段
     *
     * @param json 原始JSON字符串
     * @param paths 字段路径列表
     * @return 字段名与值的映射
     */
    public Map<String, Object> extractMultiple(String json, List<String> paths) {
        Map<String, Object> result = new HashMap<>();
        if (json == null || paths == null) {
            return result;
        }

        for (String path : paths) {
            Object value = extractByPath(json, path);
            if (value != null) {
                result.put(path, value);
            }
        }

        return result;
    }

    /**
     * 提取数组中的特定字段
     *
     * @param json 原始JSON字符串
     * @param arrayPath 数组路径
     * @param fieldName 要提取的字段名
     * @return 字段值列表
     */
    public List<Object> extractArrayField(String json, String arrayPath, String fieldName) {
        List<Object> result = new ArrayList<>();

        Object arrayObj = extractByPath(json, arrayPath);
        if (!(arrayObj instanceof List)) {
            return result;
        }

        @SuppressWarnings("unchecked")
        List<Object> array = (List<Object>) arrayObj;
        for (Object item : array) {
            if (item instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) item;
                Object value = map.get(fieldName);
                if (value != null) {
                    result.add(value);
                }
            }
        }

        return result;
    }

    /**
     * 检查路径是否存在
     *
     * @param json 原始JSON字符串
     * @param path 路径
     * @return 是否存在
     */
    public Boolean pathExists(String json, String path) {
        return extractByPath(json, path) != null;
    }

    /**
     * 获取数组长度
     *
     * @param json 原始JSON字符串
     * @param arrayPath 数组路径
     * @return 数组长度，非数组返回-1
     */
    public int getArrayLength(String json, String arrayPath) {
        Object arrayObj = extractByPath(json, arrayPath);
        if (arrayObj instanceof List) {
            return ((List<?>) arrayObj).size();
        }
        return -1;
    }

    /**
     * 合并两个JSON对象
     *
     * @param json1 第一个JSON字符串
     * @param json2 第二个JSON字符串
     * @return 合并后的Map
     */
    public Map<String, Object> merge(String json1, String json2) {
        Map<String, Object> result = parseToMap(json1);
        Map<String, Object> map2 = parseToMap(json2);
        result.putAll(map2);
        return result;
    }

    /**
     * 扁平化JSON对象
     *
     * @param json 原始JSON字符串
     * @return 扁平化后的Map（使用点号分隔的路径作为key）
     */
    public Map<String, Object> flatten(String json) {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> map = parseToMap(json);
        flattenMap("", map, result);
        return result;
    }

    private JsonNode navigatePath(JsonNode node, String path) {
        if (node == null || path == null || path.isEmpty()) {
            return node;
        }

        String[] parts = path.split("\\.");
        JsonNode current = node;

        for (String part : parts) {
            if (current == null) {
                return null;
            }

            // 处理数组索引，如 items[0]
            if (part.contains("[") && part.contains("]")) {
                int bracketStart = part.indexOf('[');
                int bracketEnd = part.indexOf(']');
                String fieldName = part.substring(0, bracketStart);
                int index = Integer.parseInt(part.substring(bracketStart + 1, bracketEnd));

                if (!fieldName.isEmpty()) {
                    current = current.get(fieldName);
                }

                if (current != null && current.isArray() && index < current.size()) {
                    current = current.get(index);
                } else {
                    return null;
                }
            } else {
                current = current.get(part);
            }
        }

        return current;
    }

    private Object convertJsonNodeToObject(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }

        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isNumber()) {
            if (node.isInt()) {
                return node.asInt();
            }
            if (node.isLong()) {
                return node.asLong();
            }
            return node.asDouble();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isArray()) {
            return convertArrayNode((ArrayNode) node);
        }
        if (node.isObject()) {
            return convertObjectNode((ObjectNode) node);
        }

        return node.asText();
    }

    private List<Object> convertArrayNode(ArrayNode arrayNode) {
        List<Object> list = new ArrayList<>();
        for (JsonNode item : arrayNode) {
            list.add(convertJsonNodeToObject(item));
        }
        return list;
    }

    private Map<String, Object> convertObjectNode(ObjectNode objectNode) {
        Map<String, Object> map = new HashMap<>();
        Iterator<String> fieldNames = objectNode.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            map.put(fieldName, convertJsonNodeToObject(objectNode.get(fieldName)));
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private void flattenMap(String prefix, Map<String, Object> map, Map<String, Object> result) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Map) {
                flattenMap(key, (Map<String, Object>) value, result);
            } else if (value instanceof List) {
                List<?> list = (List<?>) value;
                for (int i = 0; i < list.size(); i++) {
                    Object item = list.get(i);
                    if (item instanceof Map) {
                        flattenMap(key + "[" + i + "]", (Map<String, Object>) item, result);
                    } else {
                        result.put(key + "[" + i + "]", item);
                    }
                }
            } else {
                result.put(key, value);
            }
        }
    }
}