package com.company.diagnosis.agent.executionLayer;

import com.company.diagnosis.agent.base.BaseIntelligentAgent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * 步骤规划智能体（执行层）
 * 
 * 职责：
 * - 根据任务目标和检索到的知识，生成粗粒度执行步骤列表
 * - 标注步骤间的分支条件和依赖关系
 * - 提供步骤执行指导
 * 
 * 输入数据结构：
 * {
 *   "task_objective": "诊断M机单播通道中断问题",
 *   "retrieved_knowledge": "诊断策略文本...",
 *   "parameter_memory": {...}
 * }
 * 
 * 输出数据结构：
 * {
 *   "success": true/false,
 *   "steps": [
 *     {
 *       "step_index": 1,
 *       "step_name": "查询设备信息",
 *       "step_goal": "获取设备基本信息",
 *       "required_tool": "查询设备信息",
 *       "branch_condition": null,
 *       "depends_on": []
 *     }
 *   ],
 *   "error": "错误信息（如果success=false）"
 * }
 * 
 * @author AIOperation Team
 * @since 2026-01-13
 */
@Component
public class StepPlanningAgent extends BaseIntelligentAgent {
    
    private static final String SYSTEM_PROMPT = """
        你是一个诊断步骤规划专家。根据任务目标和参考知识,生成执行步骤列表。
        
        每个步骤应包含:
        - step_index: 步骤序号(从1开始)
        - step_name: 步骤名称(简洁)
        - step_goal: 步骤目标(具体)
        - required_tool: 需要调用的工具/接口名称
        - branch_condition: 分支条件(可为null)
        - depends_on: 依赖的步骤序号列表
        
        请以JSON格式输出:
        {
            "steps": [
                {
                    "step_index": 1,
                    "step_name": "步骤名称",
                    "step_goal": "步骤目标",
                    "required_tool": "工具名称",
                    "branch_condition": null,
                    "depends_on": []
                }
            ]
        }
        
        注意:
        - 步骤要有逻辑顺序
        - 保持步骤数量精简(通常3-7步)
        - 明确标注依赖关系
        """;
    
    public StepPlanningAgent() {
        super("StepPlanningAgent");
    }
    
    @Override
    public Mono<Map<String, Object>> process(Map<String, Object> input) {
        logger.info("[{}] 开始步骤规划", agentName);
        
        // 1. 提取输入
        String taskObjective = (String) input.get("task_objective");
        if (taskObjective == null || taskObjective.trim().isEmpty()) {
            return Mono.just(errorResponse("任务目标不能为空"));
        }
        
        String retrievedKnowledge = (String) input.getOrDefault("retrieved_knowledge", "");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> paramMemory = (Map<String, Object>) input.getOrDefault("parameter_memory", new HashMap<>());
        
        // 2. 检索诊断策略知识
        return retrieveKnowledge(taskObjective, "diagnosis", 3, "")
                .flatMap(knowledge -> {
                    String finalKnowledge = knowledge.isEmpty() ? retrievedKnowledge : knowledge;
                    
                    // 3. 构建提示词
                    String prompt = buildPrompt(taskObjective, finalKnowledge, paramMemory);
                    
                    // 4. 调用LLM生成步骤
                    return callLlmJson(prompt, SYSTEM_PROMPT, 0.3, 2);
                })
                .map(response -> {
                    if (!response.isSuccess()) {
                        return errorResponse("步骤规划失败: " + response.getError());
                    }
                    
                    Map<String, Object> llmData = response.getData();
                    if (llmData == null || !llmData.containsKey("steps")) {
                        // 返回默认步骤
                        return successResponse(Map.of("steps", createDefaultSteps(taskObjective)));
                    }
                    
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> steps = (List<Map<String, Object>>) llmData.get("steps");
                    
                    // 验证和规范化步骤
                    List<Map<String, Object>> normalizedSteps = normalizeSteps(steps);
                    
                    logger.info("[{}] 步骤规划完成: 共{}个步骤", agentName, normalizedSteps.size());
                    return successResponse(Map.of("steps", normalizedSteps));
                })
                .onErrorResume(e -> {
                    String error = handleException((Exception) e, "步骤规划");
                    return Mono.just(errorResponse(error));
                });
    }
    
    private String buildPrompt(String taskObjective, String knowledge, Map<String, Object> paramMemory) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请为以下任务生成执行步骤:\n\n");
        prompt.append("【任务目标】\n").append(taskObjective).append("\n\n");
        
        if (!knowledge.isEmpty()) {
            prompt.append("【参考知识】\n").append(knowledge).append("\n\n");
        }
        
        if (!paramMemory.isEmpty()) {
            prompt.append("【已知参数】\n").append(paramMemory).append("\n\n");
        }
        
        prompt.append("请生成3-7个执行步骤。");
        return prompt.toString();
    }
    
    private List<Map<String, Object>> createDefaultSteps(String taskObjective) {
        List<Map<String, Object>> steps = new ArrayList<>();
        
        Map<String, Object> step1 = new LinkedHashMap<>();
        step1.put("step_index", 1);
        step1.put("step_name", "收集基础信息");
        step1.put("step_goal", "获取诊断所需的基础信息");
        step1.put("required_tool", "查询基础信息");
        step1.put("branch_condition", null);
        step1.put("depends_on", Collections.emptyList());
        steps.add(step1);
        
        Map<String, Object> step2 = new LinkedHashMap<>();
        step2.put("step_index", 2);
        step2.put("step_name", "分析问题");
        step2.put("step_goal", "根据收集的信息分析问题原因");
        step2.put("required_tool", "问题分析");
        step2.put("branch_condition", null);
        step2.put("depends_on", Collections.singletonList(1));
        steps.add(step2);
        
        Map<String, Object> step3 = new LinkedHashMap<>();
        step3.put("step_index", 3);
        step3.put("step_name", "生成结论");
        step3.put("step_goal", "生成诊断结论和建议");
        step3.put("required_tool", "结论生成");
        step3.put("branch_condition", null);
        step3.put("depends_on", Collections.singletonList(2));
        steps.add(step3);
        
        return steps;
    }
    
    private List<Map<String, Object>> normalizeSteps(List<Map<String, Object>> steps) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        
        for (int i = 0; i < steps.size(); i++) {
            Map<String, Object> step = steps.get(i);
            Map<String, Object> normalizedStep = new LinkedHashMap<>();
            
            normalizedStep.put("step_index", step.getOrDefault("step_index", i + 1));
            normalizedStep.put("step_name", step.getOrDefault("step_name", "步骤" + (i + 1)));
            normalizedStep.put("step_goal", step.getOrDefault("step_goal", ""));
            normalizedStep.put("required_tool", step.getOrDefault("required_tool", ""));
            normalizedStep.put("branch_condition", step.get("branch_condition"));
            normalizedStep.put("depends_on", step.getOrDefault("depends_on", Collections.emptyList()));
            
            normalized.add(normalizedStep);
        }
        
        return normalized;
    }
}
