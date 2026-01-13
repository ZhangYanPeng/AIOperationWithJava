package com.company.diagnosis.agent.executionLayer;

import com.company.diagnosis.agent.base.BaseIntelligentAgent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * 参数生成智能体（执行层）
 * 
 * 职责：
 * - 为选定的下一步骤生成调用下层智能体的输入参数
 * - 从参数记忆中提取相关参数
 * - 组装符合下层要求的结构化输入
 * 
 * 输入数据结构（Map）：
 * {
 *   "target_step": {
 *     "step_index": 3,
 *     "step_name": "查询设备通道状态",
 *     "required_tool": "查询设备通道状态"
 *   },
 *   "parameter_memory": {...},
 *   "execution_history": [...]
 * }
 * 
 * 输出数据结构（Map）：
 * {
 *   "success": true/false,
 *   "generated_input": {
 *     "call_requirement": "查询设备ID为123的通道状态",
 *     "expected_fields": ["channelStatus", "lastUpdateTime"]
 *   },
 *   "used_parameters": ["deviceId"],
 *   "error": "错误信息（如果success=false）"
 * }
 * 
 * @author AIOperation Team
 * @since 2026-01-13
 */
@Component
public class ParameterGenerationAgent extends BaseIntelligentAgent {
    
    public ParameterGenerationAgent() {
        super("ParameterGenerationAgent");
    }
    
    @Override
    public Mono<Map<String, Object>> execute(Map<String, Object> input) {
        return Mono.fromCallable(() -> {
            logger.info("参数生成智能体开始执行");
            
            try {
                // 提取输入参数
                @SuppressWarnings("unchecked")
                Map<String, Object> targetStep = (Map<String, Object>) input.get("target_step");
                @SuppressWarnings("unchecked")
                Map<String, Object> parameterMemory = (Map<String, Object>) input.getOrDefault("parameter_memory", new HashMap<>());
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> executionHistory = (List<Map<String, Object>>) input.get("execution_history");
                
                // 生成参数
                Map<String, Object> generatedInput = generateInput(targetStep, parameterMemory, executionHistory);
                
                Map<String, Object> resultData = new HashMap<>();
                resultData.put("generated_input", generatedInput);
                resultData.put("used_parameters", extractUsedParameters(generatedInput, parameterMemory));
                
                logger.info("参数生成完成");
                return successResponse(resultData);
                
            } catch (Exception e) {
                String error = handleException(e, "参数生成");
                return errorResponse(error);
            }
        });
    }
    
    @Override
    public Flux<Map<String, Object>> executeStream(Map<String, Object> input) {
        return execute(input).flux();
    }
    
    /**
     * 生成调用输入
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> generateInput(Map<String, Object> targetStep,
                                               Map<String, Object> parameterMemory,
                                               List<Map<String, Object>> executionHistory) {
        Map<String, Object> generatedInput = new HashMap<>();
        
        if (targetStep == null) {
            return generatedInput;
        }
        
        String stepName = (String) targetStep.get("step_name");
        String requiredTool = (String) targetStep.get("required_tool");
        
        // 构建调用需求描述
        StringBuilder callRequirement = new StringBuilder();
        if (stepName != null) {
            callRequirement.append(stepName);
        }
        
        // 添加参数到描述中
        if (parameterMemory.containsKey("deviceId")) {
            callRequirement.append("，设备ID为").append(parameterMemory.get("deviceId"));
        }
        if (parameterMemory.containsKey("channelNum")) {
            callRequirement.append("，通道号为").append(parameterMemory.get("channelNum"));
        }
        
        generatedInput.put("call_requirement", callRequirement.toString());
        generatedInput.put("required_tool", requiredTool);
        
        // 根据步骤类型确定期望提取的字段
        List<String> expectedFields = determineExpectedFields(stepName, requiredTool);
        generatedInput.put("expected_fields", expectedFields);
        
        // 传递参数记忆
        generatedInput.put("parameter_memory", new HashMap<>(parameterMemory));
        
        return generatedInput;
    }
    
    /**
     * 确定期望提取的字段
     */
    private List<String> determineExpectedFields(String stepName, String requiredTool) {
        List<String> fields = new ArrayList<>();
        
        String lower = (stepName != null ? stepName : "").toLowerCase();
        String toolLower = (requiredTool != null ? requiredTool : "").toLowerCase();
        
        if (lower.contains("设备") || toolLower.contains("device")) {
            fields.add("deviceId");
            fields.add("deviceName");
            fields.add("deviceStatus");
        }
        
        if (lower.contains("通道") || toolLower.contains("channel")) {
            fields.add("channelId");
            fields.add("channelStatus");
            fields.add("channelNum");
        }
        
        if (lower.contains("状态") || toolLower.contains("status")) {
            fields.add("status");
            fields.add("lastUpdateTime");
        }
        
        if (fields.isEmpty()) {
            fields.add("result");
            fields.add("status");
        }
        
        return fields;
    }
    
    /**
     * 提取使用的参数
     */
    private List<String> extractUsedParameters(Map<String, Object> generatedInput, 
                                                Map<String, Object> parameterMemory) {
        List<String> used = new ArrayList<>();
        
        String callRequirement = (String) generatedInput.get("call_requirement");
        if (callRequirement != null) {
            for (String key : parameterMemory.keySet()) {
                Object value = parameterMemory.get(key);
                if (value != null && callRequirement.contains(String.valueOf(value))) {
                    used.add(key);
                }
            }
        }
        
        return used;
    }
}
