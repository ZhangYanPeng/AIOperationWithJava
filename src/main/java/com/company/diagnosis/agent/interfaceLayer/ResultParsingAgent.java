package com.company.diagnosis.agent.interfaceLayer;

import com.company.diagnosis.agent.base.BaseIntelligentAgent;
import com.company.diagnosis.util.JsonUtil;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * 结果解析智能体（接口调用层）
 * 
 * 职责：
 * - 根据上层调用需求，理解需要提取的字段
 * - 从接口返回的JSON数据中提取关键信息
 * - 返回结构化的解析结果
 * 
 * 输入数据结构（Map）：
 * {
 *   "call_requirement": "查询设备ID为123的通道状态",
 *   "api_response": { ... },  // 接口返回的JSON数据
 *   "expected_fields": ["deviceId", "channelStatus", "lastUpdateTime"]
 * }
 * 
 * 输出数据结构（Map）：
 * {
 *   "success": true/false,
 *   "extracted_data": {
 *     "deviceId": "123",
 *     "channelStatus": "online",
 *     "lastUpdateTime": "2026-01-13T14:00:00"
 *   },
 *   "missing_fields": [],
 *   "error": "错误信息（如果success=false）"
 * }
 * 
 * @author AIOperation Team
 * @since 2026-01-13
 */
@Component
public class ResultParsingAgent extends BaseIntelligentAgent {
    
    public ResultParsingAgent() {
        super("ResultParsingAgent");
    }
    
    @Override
    public Mono<Map<String, Object>> execute(Map<String, Object> input) {
        return Mono.fromCallable(() -> {
            logger.info("结果解析智能体开始执行");
            
            try {
                // 提取输入参数
                String callRequirement = (String) input.get("call_requirement");
                @SuppressWarnings("unchecked")
                Map<String, Object> apiResponse = (Map<String, Object>) input.get("api_response");
                @SuppressWarnings("unchecked")
                List<String> expectedFields = (List<String>) input.get("expected_fields");
                
                if (apiResponse == null) {
                    return errorResponse("API响应不能为空");
                }
                
                // 执行结果解析
                Map<String, Object> parseResult = parseResponse(callRequirement, apiResponse, expectedFields);
                
                logger.info("结果解析完成: 提取了{}个字段", 
                        ((Map<?, ?>) parseResult.getOrDefault("extracted_data", Map.of())).size());
                return successResponse(parseResult);
                
            } catch (Exception e) {
                String error = handleException(e, "结果解析");
                return errorResponse(error);
            }
        });
    }
    
    @Override
    public Flux<Map<String, Object>> executeStream(Map<String, Object> input) {
        return Flux.create(sink -> {
            // 发送开始事件
            sink.next(yieldThinkingEvent("开始解析API响应", "result_parsing_start"));
            
            execute(input).subscribe(
                    result -> {
                        sink.next(yieldResultEvent(result));
                        sink.complete();
                    },
                    error -> {
                        sink.next(yieldErrorEvent(error.getMessage(), "ResultParsingError"));
                        sink.complete();
                    }
            );
        });
    }
    
    /**
     * 解析API响应
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseResponse(String callRequirement,
                                               Map<String, Object> apiResponse,
                                               List<String> expectedFields) {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> extractedData = new HashMap<>();
        List<String> missingFields = new ArrayList<>();
        
        // 检查响应是否成功
        Boolean success = (Boolean) apiResponse.getOrDefault("success", true);
        if (!success) {
            result.put("extracted_data", extractedData);
            result.put("missing_fields", missingFields);
            result.put("response_error", apiResponse.get("error"));
            return result;
        }
        
        // 获取实际数据（可能在data字段中）
        Map<String, Object> data = apiResponse;
        if (apiResponse.containsKey("data")) {
            Object dataField = apiResponse.get("data");
            if (dataField instanceof Map) {
                data = (Map<String, Object>) dataField;
            }
        }
        
        // 如果指定了期望字段，只提取这些字段
        if (expectedFields != null && !expectedFields.isEmpty()) {
            for (String field : expectedFields) {
                Object value = extractFieldValue(data, field);
                if (value != null) {
                    extractedData.put(field, value);
                } else {
                    missingFields.add(field);
                }
            }
        } else {
            // 没有指定期望字段，提取所有非null字段
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                if (entry.getValue() != null && !entry.getKey().startsWith("_")) {
                    extractedData.put(entry.getKey(), entry.getValue());
                }
            }
        }
        
        result.put("extracted_data", extractedData);
        result.put("missing_fields", missingFields);
        result.put("total_fields", extractedData.size());
        result.put("raw_response", apiResponse);
        
        return result;
    }
    
    /**
     * 提取字段值（支持嵌套路径，如 "user.profile.name"）
     */
    @SuppressWarnings("unchecked")
    private Object extractFieldValue(Map<String, Object> data, String fieldPath) {
        if (data == null || fieldPath == null) {
            return null;
        }
        
        // 直接查找
        if (data.containsKey(fieldPath)) {
            return data.get(fieldPath);
        }
        
        // 尝试嵌套路径
        String[] parts = fieldPath.split("\\.");
        Object current = data;
        
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
                if (current == null) {
                    return null;
                }
            } else if (current instanceof List) {
                // 如果是列表，尝试获取第一个元素的该字段
                List<?> list = (List<?>) current;
                if (!list.isEmpty() && list.get(0) instanceof Map) {
                    current = ((Map<String, Object>) list.get(0)).get(part);
                } else {
                    return null;
                }
            } else {
                return null;
            }
        }
        
        return current;
    }
    
    /**
     * 智能提取（根据调用需求推断需要提取的字段）
     */
    private List<String> inferExpectedFields(String callRequirement) {
        List<String> fields = new ArrayList<>();
        
        if (callRequirement == null) {
            return fields;
        }
        
        String lower = callRequirement.toLowerCase();
        
        // 根据需求关键词推断字段
        if (lower.contains("设备") || lower.contains("device")) {
            fields.add("deviceId");
            fields.add("deviceName");
            fields.add("deviceStatus");
        }
        
        if (lower.contains("通道") || lower.contains("channel")) {
            fields.add("channelId");
            fields.add("channelStatus");
            fields.add("channelNum");
        }
        
        if (lower.contains("状态") || lower.contains("status")) {
            fields.add("status");
            fields.add("state");
        }
        
        if (lower.contains("时间") || lower.contains("time")) {
            fields.add("createTime");
            fields.add("updateTime");
            fields.add("lastUpdateTime");
        }
        
        return fields;
    }
}
