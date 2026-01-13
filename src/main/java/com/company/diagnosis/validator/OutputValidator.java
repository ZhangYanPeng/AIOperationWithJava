package com.company.diagnosis.validator;

import com.company.diagnosis.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 输出验证器
 * <p>
 * 职责：
 * 1. 验证智能体输出的格式和完整性
 * 2. 验证输出是否符合JSON Schema
 * 3. 提供输出的修复建议
 *
 * @author Diagnosis System
 * @date 2026-01-13
 */
@Component
public class OutputValidator {

    private static final Logger logger = LoggerFactory.getLogger(OutputValidator.class);

    /**
     * 验证输出格式
     *
     * @param output 输出数据
     * @param schema JSON Schema定义
     * @return 验证结果
     */
    public Boolean validate(Map<String, Object> output, String schema) {
        if (output == null) {
            logger.warn("输出数据为空");
            return false;
        }
        
        if (schema == null || schema.isEmpty()) {
            // 无Schema定义，只验证非空
            return !output.isEmpty();
        }
        
        try {
            JsonNode schemaNode = JsonUtil.parseJson(schema);
            if (schemaNode == null) {
                logger.warn("Schema解析失败");
                return false;
            }
            
            return validateAgainstSchema(output, schemaNode);
        } catch (Exception e) {
            logger.error("Schema验证失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 验证必填字段
     *
     * @param output 输出数据
     * @param requiredFields 必填字段列表
     * @return 验证结果
     */
    public Boolean validateRequiredFields(Map<String, Object> output, List<String> requiredFields) {
        if (output == null) {
            return false;
        }
        
        if (requiredFields == null || requiredFields.isEmpty()) {
            return true;
        }
        
        for (String field : requiredFields) {
            if (!hasField(output, field)) {
                logger.warn("缺少必填字段: {}", field);
                return false;
            }
        }
        
        return true;
    }

    /**
     * 验证字段类型
     *
     * @param output 输出数据
     * @param fieldTypes 字段类型映射 (字段名 -> 类型)
     * @return 验证结果
     */
    public Boolean validateFieldTypes(Map<String, Object> output, Map<String, String> fieldTypes) {
        if (output == null || fieldTypes == null) {
            return output != null;
        }
        
        for (Map.Entry<String, String> entry : fieldTypes.entrySet()) {
            String field = entry.getKey();
            String expectedType = entry.getValue();
            
            Object value = output.get(field);
            if (value != null && !isTypeMatch(value, expectedType)) {
                logger.warn("字段类型不匹配: {} 期望 {}, 实际 {}", 
                        field, expectedType, value.getClass().getSimpleName());
                return false;
            }
        }
        
        return true;
    }

    /**
     * 验证字段值范围
     *
     * @param output 输出数据
     * @param field 字段名
     * @param min 最小值
     * @param max 最大值
     * @return 验证结果
     */
    public Boolean validateRange(Map<String, Object> output, String field, Number min, Number max) {
        if (output == null || field == null) {
            return false;
        }
        
        Object value = output.get(field);
        if (value == null) {
            return true; // 空值不验证范围
        }
        
        if (!(value instanceof Number)) {
            return false;
        }
        
        double numValue = ((Number) value).doubleValue();
        
        if (min != null && numValue < min.doubleValue()) {
            logger.warn("字段 {} 值 {} 小于最小值 {}", field, numValue, min);
            return false;
        }
        
        if (max != null && numValue > max.doubleValue()) {
            logger.warn("字段 {} 值 {} 大于最大值 {}", field, numValue, max);
            return false;
        }
        
        return true;
    }

    /**
     * 验证字符串格式
     *
     * @param output 输出数据
     * @param field 字段名
     * @param pattern 正则表达式
     * @return 验证结果
     */
    public Boolean validatePattern(Map<String, Object> output, String field, String pattern) {
        if (output == null || field == null || pattern == null) {
            return false;
        }
        
        Object value = output.get(field);
        if (value == null) {
            return true;
        }
        
        String strValue = String.valueOf(value);
        boolean matches = Pattern.matches(pattern, strValue);
        
        if (!matches) {
            logger.warn("字段 {} 值不匹配模式 {}", field, pattern);
        }
        
        return matches;
    }

    /**
     * 获取验证错误列表
     *
     * @param output 输出数据
     * @param schema JSON Schema定义
     * @return 错误列表
     */
    public List<String> getValidationErrors(Map<String, Object> output, String schema) {
        List<String> errors = new ArrayList<>();
        
        if (output == null) {
            errors.add("输出数据为空");
            return errors;
        }
        
        if (schema == null || schema.isEmpty()) {
            return errors;
        }
        
        try {
            JsonNode schemaNode = JsonUtil.parseJson(schema);
            if (schemaNode == null) {
                errors.add("Schema解析失败");
                return errors;
            }
            
            collectValidationErrors(output, schemaNode, "", errors);
        } catch (Exception e) {
            errors.add("验证异常: " + e.getMessage());
        }
        
        return errors;
    }

    private boolean validateAgainstSchema(Map<String, Object> data, JsonNode schema) {
        // 验证必填字段
        JsonNode required = schema.get("required");
        if (required != null && required.isArray()) {
            for (JsonNode field : required) {
                if (!data.containsKey(field.asText())) {
                    return false;
                }
            }
        }
        
        // 验证属性类型
        JsonNode properties = schema.get("properties");
        if (properties != null && properties.isObject()) {
            Iterator<String> fieldNames = properties.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                Object value = data.get(fieldName);
                if (value != null) {
                    JsonNode propSchema = properties.get(fieldName);
                    if (!validateValueAgainstSchema(value, propSchema)) {
                        return false;
                    }
                }
            }
        }
        
        return true;
    }

    private boolean validateValueAgainstSchema(Object value, JsonNode schema) {
        if (schema == null) {
            return true;
        }
        
        JsonNode typeNode = schema.get("type");
        if (typeNode == null) {
            return true;
        }
        
        String expectedType = typeNode.asText();
        return isTypeMatch(value, expectedType);
    }

    private void collectValidationErrors(Map<String, Object> data, JsonNode schema, 
                                         String path, List<String> errors) {
        // 检查必填字段
        JsonNode required = schema.get("required");
        if (required != null && required.isArray()) {
            for (JsonNode field : required) {
                String fieldName = field.asText();
                if (!data.containsKey(fieldName)) {
                    errors.add(buildPath(path, fieldName) + " 是必填字段");
                }
            }
        }
        
        // 检查属性
        JsonNode properties = schema.get("properties");
        if (properties != null && properties.isObject()) {
            Iterator<String> fieldNames = properties.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                Object value = data.get(fieldName);
                JsonNode propSchema = properties.get(fieldName);
                
                if (value != null) {
                    JsonNode typeNode = propSchema.get("type");
                    if (typeNode != null && !isTypeMatch(value, typeNode.asText())) {
                        errors.add(buildPath(path, fieldName) + " 类型不匹配，期望 " + typeNode.asText());
                    }
                }
            }
        }
    }

    private boolean hasField(Map<String, Object> data, String field) {
        if (field.contains(".")) {
            // 支持嵌套路径
            String[] parts = field.split("\\.");
            Object current = data;
            for (String part : parts) {
                if (!(current instanceof Map)) {
                    return false;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) current;
                current = map.get(part);
                if (current == null) {
                    return false;
                }
            }
            return true;
        }
        return data.containsKey(field) && data.get(field) != null;
    }

    private boolean isTypeMatch(Object value, String expectedType) {
        switch (expectedType.toLowerCase()) {
            case "string":
                return value instanceof String;
            case "number":
            case "integer":
                return value instanceof Number;
            case "boolean":
                return value instanceof Boolean;
            case "array":
                return value instanceof List;
            case "object":
                return value instanceof Map;
            default:
                return true;
        }
    }

    private String buildPath(String base, String field) {
        if (base == null || base.isEmpty()) {
            return field;
        }
        return base + "." + field;
    }
}