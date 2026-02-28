package com.company.diagnosis.agent.executionLayer;

import com.company.diagnosis.agent.base.BaseIntelligentAgent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * 结果生成智能体（执行层）
 * 
 * 职责：
 * - 当步骤决策智能体判断流程结束时被调用
 * - 综合所有步骤的执行历史和参数记忆，生成最终输出
 * - 格式化为符合上层要求的结构化结果
 * 
 * 输入数据结构：
 * {
 *   "task_objective": "诊断M机单播通道中断问题",
 *   "execution_history": [...],
 *   "parameter_memory": {...},
 *   "expected_output_format": "诊断结论+根本原因+建议措施"
 * }
 * 
 * 输出数据结构：
 * {
 *   "success": true/false,
 *   "final_result": {
 *     "diagnosis_conclusion": "M机单播通道中断由网络故障导致",
 *     "root_cause": "交换机端口故障",
 *     "evidence": ["设备通道状态异常", "网络探测失败"],
 *     "recommendations": ["更换交换机端口", "重启设备"]
 *   },
 *   "summary": "诊断完成，共执行3个步骤",
 *   "error": "错误信息（如果success=false）"
 * }
 * 
 * @author AIOperation Team
 * @since 2026-01-13
 */
@Component
public class ResultGenerationAgent extends BaseIntelligentAgent {
    
    private static final String SYSTEM_PROMPT = """
        你是一个诊断结果生成专家。根据执行历史和收集的参数,生成结构化的诊断结果。
        
        请以JSON格式输出:
        {
            "diagnosis_conclusion": "总体诊断结论(1-2句话)",
            "root_cause": "根本原因分析",
            "evidence": ["支撑结论的证据1", "证据2"],
            "confidence": 0.0-1.0的置信度,
            "severity": "HIGH/MEDIUM/LOW",
            "recommendations": ["建议措施1", "建议措施2"]
        }
        
        注意:
        - 结论要简洁明确
        - 证据要来自执行历史中的实际数据
        - 建议要具体可操作
        - 置信度基于证据充分程度
        """;
    
    public ResultGenerationAgent() {
        super("ResultGenerationAgent");
    }
    
    @Override
    public Mono<Map<String, Object>> process(Map<String, Object> input) {
        logger.info("[{}] 开始生成诊断结果", agentName);
        
        // 1. 提取输入
        String taskObjective = (String) input.getOrDefault("task_objective", "诊断任务");
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> executionHistory = (List<Map<String, Object>>) input.getOrDefault("execution_history", new ArrayList<>());
        
        @SuppressWarnings("unchecked")
        Map<String, Object> paramMemory = (Map<String, Object>) input.getOrDefault("parameter_memory", new HashMap<>());
        
        String expectedFormat = (String) input.getOrDefault("expected_output_format", "诊断结论+根本原因+建议措施");
        
        // 2. 检索结论分析规则
        return retrieveKnowledge(taskObjective, "conclusion", 2, "")
                .flatMap(conclusionRules -> {
                    // 3. 构建提示词
                    String prompt = buildPrompt(taskObjective, executionHistory, paramMemory, expectedFormat, conclusionRules);
                    
                    // 4. 调用LLM生成结果
                    return callLlmJson(prompt, SYSTEM_PROMPT, 0.3, 2);
                })
                .map(response -> {
                    if (!response.isSuccess()) {
                        // 失败时生成默认结果
                        return successResponse(generateDefaultResult(taskObjective, executionHistory));
                    }
                    
                    Map<String, Object> llmData = response.getData();
                    if (llmData == null) {
                        return successResponse(generateDefaultResult(taskObjective, executionHistory));
                    }
                    
                    // 5. 构建最终结果
                    Map<String, Object> finalResult = new LinkedHashMap<>();
                    finalResult.put("diagnosis_conclusion", llmData.getOrDefault("diagnosis_conclusion", "诊断完成"));
                    finalResult.put("root_cause", llmData.getOrDefault("root_cause", "待进一步分析"));
                    finalResult.put("evidence", llmData.getOrDefault("evidence", new ArrayList<>()));
                    finalResult.put("confidence", llmData.getOrDefault("confidence", 0.7));
                    finalResult.put("severity", llmData.getOrDefault("severity", "MEDIUM"));
                    finalResult.put("recommendations", llmData.getOrDefault("recommendations", new ArrayList<>()));
                    
                    String summary = String.format("诊断完成,共执行%d个步骤", executionHistory.size());
                    
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("final_result", finalResult);
                    result.put("summary", summary);
                    
                    logger.info("[{}] 诊断结果生成完成: conclusion={}", agentName, finalResult.get("diagnosis_conclusion"));
                    return successResponse(result);
                })
                .onErrorResume(e -> {
                    String error = handleException((Exception) e, "结果生成");
                    return Mono.just(errorResponse(error));
                });
    }
    
    private String buildPrompt(String taskObjective, List<Map<String, Object>> executionHistory,
                               Map<String, Object> paramMemory, String expectedFormat, String conclusionRules) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请根据以下信息生成诊断结果:\n\n");
        
        prompt.append("【任务目标】\n").append(taskObjective).append("\n\n");
        
        prompt.append("【期望输出格式】\n").append(expectedFormat).append("\n\n");
        
        if (!executionHistory.isEmpty()) {
            prompt.append("【执行历史】\n");
            for (Map<String, Object> step : executionHistory) {
                prompt.append("- 步骤").append(step.get("step_index")).append(": ")
                      .append(step.get("step_name")).append("\n");
                prompt.append("  状态: ").append(step.get("status")).append("\n");
                if (step.get("result") != null) {
                    prompt.append("  结果: ").append(step.get("result")).append("\n");
                }
            }
            prompt.append("\n");
        }
        
        if (!paramMemory.isEmpty()) {
            prompt.append("【收集的参数】\n").append(paramMemory).append("\n\n");
        }
        
        if (!conclusionRules.isEmpty()) {
            prompt.append("【结论分析规则】\n").append(conclusionRules).append("\n\n");
        }
        
        prompt.append("请生成结构化的诊断结果。");
        return prompt.toString();
    }
    
    private Map<String, Object> generateDefaultResult(String taskObjective, List<Map<String, Object>> executionHistory) {
        Map<String, Object> finalResult = new LinkedHashMap<>();
        finalResult.put("diagnosis_conclusion", "针对\"" + taskObjective + "\"的诊断已完成");
        finalResult.put("root_cause", "需要进一步分析确定根本原因");
        
        // 从执行历史提取证据
        List<String> evidence = new ArrayList<>();
        for (Map<String, Object> step : executionHistory) {
            if ("SUCCESS".equals(step.get("status"))) {
                evidence.add("步骤\"" + step.get("step_name") + "\"执行成功");
            }
        }
        if (evidence.isEmpty()) {
            evidence.add("诊断流程已完成");
        }
        finalResult.put("evidence", evidence);
        
        finalResult.put("confidence", 0.6);
        finalResult.put("severity", "MEDIUM");
        finalResult.put("recommendations", Arrays.asList("建议持续监控", "如问题复发请联系支持"));
        
        String summary = String.format("诊断完成,共执行%d个步骤", executionHistory.size());
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("final_result", finalResult);
        result.put("summary", summary);
        return result;
    }
}
