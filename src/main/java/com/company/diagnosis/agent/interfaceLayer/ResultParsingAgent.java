package com.company.diagnosis.agent.interfaceLayer;

import com.company.diagnosis.agent.base.BaseIntelligentAgent;
import com.company.diagnosis.util.JsonUtil;
import org.springframework.stereotype.Component;
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
 * 输入数据结构：
 * {
 *   "call_requirement": "查询设备ID为123的通道状态",
 *   "api_response": { ... },  // 接口返回的JSON数据
 *   "expected_fields": ["deviceId", "channelStatus", "lastUpdateTime"]
 * }
 * 
 * 输出数据结构：
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
    
    private static final String SYSTEM_PROMPT = """
        你是一个API响应解析专家。根据调用需求和期望字段,从API响应中提取关键信息。
        
        请以JSON格式输出:
        {
            "extracted_data": {
                "字段1": "提取的值1",
                "字段2": "提取的值2"
            },
            "missing_fields": ["未找到的字段1"],
            "data_quality": "GOOD/PARTIAL/POOR",
            "notes": "解析备注(可选)"
        }
        
        注意:
        - 优先从api_response中直接提取对应字段
        - 如果字段在嵌套结构中,需要正确遍历提取
        - 如果字段不存在,加入missing_fields列表
        - data_quality表示数据完整度:
          - GOOD: 所有期望字段都找到
          - PARTIAL: 部分字段缺失
          - POOR: 大部分字段缺失
        """;
    
    public ResultParsingAgent() {
        super("ResultParsingAgent");
    }
    
    @Override
    public Mono<Map<String, Object>> process(Map<String, Object> input) {
        logger.info("[{}] 开始结果解析", agentName);
        
        // 1. 提取输入
        String callRequirement = (String) input.getOrDefault("call_requirement", "");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> apiResponse = (Map<String, Object>) input.get("api_response");
        if (apiResponse == null) {
            return Mono.just(errorResponse("API响应不能为空"));
        }
        
        @SuppressWarnings("unchecked")
        List<String> expectedFields = (List<String>) input.getOrDefault("expected_fields", new ArrayList<>());
        
        // 2. 尝试直接提取(简单情况)
        Map<String, Object> directExtraction = directExtract(apiResponse, expectedFields);
        List<String> missingFields = findMissingFields(directExtraction, expectedFields);
        
        // 如果直接提取成功(无缺失字段),直接返回
        if (missingFields.isEmpty() && !directExtraction.isEmpty()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("extracted_data", directExtraction);
            result.put("missing_fields", missingFields);
            result.put("data_quality", "GOOD");
            
            logger.info("[{}] 直接提取成功: 提取{}个字段", agentName, directExtraction.size());
            return Mono.just(successResponse(result));
        }
        
        // 3. 复杂情况使用LLM辅助解析
        String prompt = buildPrompt(callRequirement, apiResponse, expectedFields);
        
        return callLlmJson(prompt, SYSTEM_PROMPT, 0.2, 2)
                .map(response -> {
                    if (!response.isSuccess()) {
                        // 失败时使用直接提取结果
                        return buildResult(directExtraction, missingFields);
                    }
                    
                    Map<String, Object> llmData = response.getData();
                    if (llmData == null) {
                        return buildResult(directExtraction, missingFields);
                    }
                    
                    // 合并LLM提取结果和直接提取结果
                    @SuppressWarnings("unchecked")
                    Map<String, Object> llmExtracted = (Map<String, Object>) llmData.getOrDefault("extracted_data", new HashMap<>());
                    
                    Map<String, Object> mergedExtraction = new LinkedHashMap<>(directExtraction);
                    llmExtracted.forEach((k, v) -> {
                        if (v != null && !mergedExtraction.containsKey(k)) {
                            mergedExtraction.put(k, v);
                        }
                    });
                    
                    @SuppressWarnings("unchecked")
                    List<String> llmMissing = (List<String>) llmData.getOrDefault("missing_fields", new ArrayList<>());
                    String dataQuality = (String) llmData.getOrDefault("data_quality", "PARTIAL");
                    
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("extracted_data", mergedExtraction);
                    result.put("missing_fields", llmMissing);
                    result.put("data_quality", dataQuality);
                    
                    logger.info("[{}] 结果解析完成: extracted={}, missing={}", 
                            agentName, mergedExtraction.size(), llmMissing.size());
                    return successResponse(result);
                })
                .onErrorResume(e -> {
                    String error = handleException((Exception) e, "结果解析");
                    return Mono.just(errorResponse(error));
                });
    }
    
    /**
     * 直接从API响应中提取字段
     */
    private Map<String, Object> directExtract(Map<String, Object> apiResponse, List<String> expectedFields) {
        Map<String, Object> extracted = new LinkedHashMap<>();
        
        for (String field : expectedFields) {
            Object value = extractField(apiResponse, field);
            if (value != null) {
                extracted.put(field, value);
            }
        }
        
        // 如果没有指定期望字段,提取所有顶层字段
        if (expectedFields.isEmpty()) {
            extracted.putAll(apiResponse);
        }
        
        return extracted;
    }
    
    /**
     * 从嵌套Map中提取字段值(支持点分隔的路径,如"data.deviceInfo.id")
     */
    private Object extractField(Map<String, Object> map, String fieldPath) {
        if (map == null || fieldPath == null) {
            return null;
        }
        
        // 先尝试直接获取
        if (map.containsKey(fieldPath)) {
            return map.get(fieldPath);
        }
        
        // 尝试按路径获取
        String[] parts = fieldPath.split("\\.");
        Object current = map;
        
        for (String part : parts) {
            if (current instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> currentMap = (Map<String, Object>) current;
                current = currentMap.get(part);
            } else {
                return null;
            }
        }
        
        return current;
    }
    
    /**
     * 查找缺失的字段
     */
    private List<String> findMissingFields(Map<String, Object> extracted, List<String> expectedFields) {
        List<String> missing = new ArrayList<>();
        
        for (String field : expectedFields) {
            if (!extracted.containsKey(field)) {
                missing.add(field);
            }
        }
        
        return missing;
    }
    
    private Map<String, Object> buildResult(Map<String, Object> extracted, List<String> missing) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("extracted_data", extracted);
        result.put("missing_fields", missing);
        
        // 计算数据质量
        String quality;
        if (missing.isEmpty()) {
            quality = "GOOD";
        } else if (missing.size() <= extracted.size()) {
            quality = "PARTIAL";
        } else {
            quality = "POOR";
        }
        result.put("data_quality", quality);
        
        return successResponse(result);
    }
    
    private String buildPrompt(String callRequirement, Map<String, Object> apiResponse, List<String> expectedFields) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请从以下API响应中提取所需信息:\n\n");
        
        if (!callRequirement.isEmpty()) {
            prompt.append("【调用需求】\n").append(callRequirement).append("\n\n");
        }
        
        prompt.append("【API响应】\n").append(JsonUtil.toPrettyJson(apiResponse)).append("\n\n");
        
        if (!expectedFields.isEmpty()) {
            prompt.append("【期望提取的字段】\n").append(expectedFields).append("\n\n");
        }
        
        prompt.append("请提取上述字段的值。");
        return prompt.toString();
    }
}
