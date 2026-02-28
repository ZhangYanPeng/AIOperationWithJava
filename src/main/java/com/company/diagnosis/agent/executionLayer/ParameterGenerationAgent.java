package com.company.diagnosis.agent.executionLayer;

import com.company.diagnosis.agent.base.BaseIntelligentAgent;
import org.springframework.stereotype.Component;
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
 * 输入数据结构：
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
 * 输出数据结构：
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
    
    private static final String SYSTEM_PROMPT = """
        你是一个参数生成专家。根据目标步骤和已有参数,生成调用接口所需的输入。
        
        请以JSON格式输出:
        {
            "call_requirement": "自然语言描述的调用需求",
            "expected_fields": ["期望返回的字段1", "字段2"],
            "mapped_params": {
                "参数名": "参数值"
            }
        }
        
        注意:
        - call_requirement要清晰描述调用意图
        - expected_fields列出需要获取的关键字段
        - mapped_params包含所有必需的参数值
        - 参数值优先从parameter_memory中提取
        """;
    
    public ParameterGenerationAgent() {
        super("ParameterGenerationAgent");
    }
    
    @Override
    public Mono<Map<String, Object>> process(Map<String, Object> input) {
        logger.info("[{}] 开始参数生成", agentName);
        
        // 1. 提取输入
        @SuppressWarnings("unchecked")
        Map<String, Object> targetStep = (Map<String, Object>) input.get("target_step");
        if (targetStep == null) {
            return Mono.just(errorResponse("目标步骤不能为空"));
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> paramMemory = (Map<String, Object>) input.getOrDefault("parameter_memory", new HashMap<>());
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> executionHistory = (List<Map<String, Object>>) input.getOrDefault("execution_history", new ArrayList<>());
        
        String stepName = (String) targetStep.getOrDefault("step_name", "");
        String requiredTool = (String) targetStep.getOrDefault("required_tool", "");
        
        // 2. 检索工具接口文档
        return retrieveKnowledge(requiredTool, "tool", 1, "")
                .flatMap(toolDoc -> {
                    // 3. 构建提示词
                    String prompt = buildPrompt(targetStep, paramMemory, executionHistory, toolDoc);
                    
                    // 4. 调用LLM生成参数
                    return callLlmJson(prompt, SYSTEM_PROMPT, 0.2, 2);
                })
                .map(response -> {
                    if (!response.isSuccess()) {
                        // 失败时使用默认参数
                        return successResponse(generateDefaultInput(targetStep, paramMemory));
                    }
                    
                    Map<String, Object> llmData = response.getData();
                    if (llmData == null) {
                        return successResponse(generateDefaultInput(targetStep, paramMemory));
                    }
                    
                    // 5. 构建输出
                    Map<String, Object> generatedInput = new LinkedHashMap<>();
                    generatedInput.put("call_requirement", llmData.getOrDefault("call_requirement", stepName));
                    generatedInput.put("expected_fields", llmData.getOrDefault("expected_fields", new ArrayList<>()));
                    generatedInput.put("mapped_params", llmData.getOrDefault("mapped_params", new HashMap<>()));
                    
                    // 记录使用的参数
                    List<String> usedParams = extractUsedParameters(llmData, paramMemory);
                    
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("generated_input", generatedInput);
                    result.put("used_parameters", usedParams);
                    
                    logger.info("[{}] 参数生成完成: usedParams={}", agentName, usedParams);
                    return successResponse(result);
                })
                .onErrorResume(e -> {
                    String error = handleException((Exception) e, "参数生成");
                    return Mono.just(errorResponse(error));
                });
    }
    
    private String buildPrompt(Map<String, Object> targetStep, Map<String, Object> paramMemory,
                               List<Map<String, Object>> executionHistory, String toolDoc) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请为以下步骤生成调用参数:\n\n");
        
        prompt.append("【目标步骤】\n");
        prompt.append("- 名称: ").append(targetStep.get("step_name")).append("\n");
        prompt.append("- 目标: ").append(targetStep.get("step_goal")).append("\n");
        prompt.append("- 工具: ").append(targetStep.get("required_tool")).append("\n\n");
        
        if (!paramMemory.isEmpty()) {
            prompt.append("【可用参数】\n").append(paramMemory).append("\n\n");
        }
        
        if (!toolDoc.isEmpty()) {
            prompt.append("【工具接口文档】\n").append(toolDoc).append("\n\n");
        }
        
        if (!executionHistory.isEmpty()) {
            prompt.append("【执行历史】\n");
            int historyCount = Math.min(3, executionHistory.size());
            for (int i = executionHistory.size() - historyCount; i < executionHistory.size(); i++) {
                prompt.append("- ").append(executionHistory.get(i).get("step_name")).append(": ")
                      .append(executionHistory.get(i).get("status")).append("\n");
            }
            prompt.append("\n");
        }
        
        prompt.append("请生成调用参数。");
        return prompt.toString();
    }
    
    private Map<String, Object> generateDefaultInput(Map<String, Object> targetStep, Map<String, Object> paramMemory) {
        Map<String, Object> generatedInput = new LinkedHashMap<>();
        generatedInput.put("call_requirement", targetStep.getOrDefault("step_goal", targetStep.get("step_name")));
        generatedInput.put("expected_fields", new ArrayList<>());
        generatedInput.put("mapped_params", new HashMap<>(paramMemory));
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("generated_input", generatedInput);
        result.put("used_parameters", new ArrayList<>(paramMemory.keySet()));
        return result;
    }
    
    private List<String> extractUsedParameters(Map<String, Object> llmData, Map<String, Object> paramMemory) {
        List<String> usedParams = new ArrayList<>();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> mappedParams = (Map<String, Object>) llmData.get("mapped_params");
        if (mappedParams != null) {
            for (String key : mappedParams.keySet()) {
                if (paramMemory.containsKey(key)) {
                    usedParams.add(key);
                }
            }
        }
        
        return usedParams;
    }
}
