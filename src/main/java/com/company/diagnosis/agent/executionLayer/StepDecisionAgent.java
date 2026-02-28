package com.company.diagnosis.agent.executionLayer;

import com.company.diagnosis.agent.base.BaseIntelligentAgent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * 步骤决策智能体（执行层）
 * 
 * 职责：
 * - 根据当前执行状态、步骤列表、上次执行结果，决定下一步行动
 * - 判断是否继续执行或结束流程
 * - 处理分支条件，选择下一步执行的步骤索引
 * 
 * 输入数据结构：
 * {
 *   "steps": [...],
 *   "current_state": {
 *     "executed_steps": [1, 2],
 *     "current_step_index": 2,
 *     "execution_history": [...]
 *   },
 *   "last_step_result": {...},
 *   "parameter_memory": {...}
 * }
 * 
 * 输出数据结构：
 * {
 *   "success": true/false,
 *   "should_continue": true/false,
 *   "next_step_index": 3,
 *   "termination_reason": "所有步骤已完成",
 *   "decision_reasoning": "根据上一步结果...",
 *   "error": "错误信息（如果success=false）"
 * }
 * 
 * @author AIOperation Team
 * @since 2026-01-13
 */
@Component
public class StepDecisionAgent extends BaseIntelligentAgent {
    
    private static final String SYSTEM_PROMPT = """
        你是一个流程决策专家。根据当前执行状态和上一步结果,决定下一步行动。
        
        判断逻辑:
        1. 如果所有步骤已执行完毕,返回should_continue=false
        2. 如果上一步失败且无法继续,返回should_continue=false
        3. 如果存在分支条件,根据条件选择下一步
        4. 否则,选择下一个未执行的步骤
        
        请以JSON格式输出:
        {
            "should_continue": true/false,
            "next_step_index": 下一步序号或null,
            "termination_reason": "终止原因(如果should_continue=false)",
            "decision_reasoning": "决策理由"
        }
        """;
    
    public StepDecisionAgent() {
        super("StepDecisionAgent");
    }
    
    @Override
    public Mono<Map<String, Object>> process(Map<String, Object> input) {
        logger.info("[{}] 开始步骤决策", agentName);
        
        // 1. 提取输入
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) input.get("steps");
        if (steps == null || steps.isEmpty()) {
            return Mono.just(errorResponse("步骤列表不能为空"));
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> currentState = (Map<String, Object>) input.getOrDefault("current_state", new HashMap<>());
        
        @SuppressWarnings("unchecked")
        Map<String, Object> lastStepResult = (Map<String, Object>) input.get("last_step_result");
        
        // 2. 基于规则的快速决策
        Map<String, Object> quickDecision = makeQuickDecision(steps, currentState, lastStepResult);
        if (quickDecision != null) {
            logger.info("[{}] 快速决策: shouldContinue={}, nextStep={}",
                    agentName, quickDecision.get("should_continue"), quickDecision.get("next_step_index"));
            return Mono.just(successResponse(quickDecision));
        }
        
        // 3. 复杂情况需要LLM决策
        String prompt = buildPrompt(steps, currentState, lastStepResult);
        
        return callLlmJson(prompt, SYSTEM_PROMPT, 0.2, 2)
                .map(response -> {
                    if (!response.isSuccess()) {
                        // 失败时使用默认决策
                        return successResponse(makeDefaultDecision(steps, currentState));
                    }
                    
                    Map<String, Object> llmData = response.getData();
                    if (llmData == null) {
                        return successResponse(makeDefaultDecision(steps, currentState));
                    }
                    
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("should_continue", llmData.getOrDefault("should_continue", false));
                    result.put("next_step_index", llmData.get("next_step_index"));
                    result.put("termination_reason", llmData.get("termination_reason"));
                    result.put("decision_reasoning", llmData.get("decision_reasoning"));
                    
                    logger.info("[{}] LLM决策: shouldContinue={}", agentName, result.get("should_continue"));
                    return successResponse(result);
                })
                .onErrorResume(e -> {
                    String error = handleException((Exception) e, "步骤决策");
                    return Mono.just(errorResponse(error));
                });
    }
    
    /**
     * 基于规则的快速决策
     */
    private Map<String, Object> makeQuickDecision(
            List<Map<String, Object>> steps,
            Map<String, Object> currentState,
            Map<String, Object> lastStepResult) {
        
        @SuppressWarnings("unchecked")
        List<Integer> executedSteps = (List<Integer>) currentState.getOrDefault("executed_steps", new ArrayList<>());
        int currentIndex = ((Number) currentState.getOrDefault("current_step_index", 0)).intValue();
        
        // 情况1: 所有步骤已完成
        if (executedSteps.size() >= steps.size()) {
            Map<String, Object> decision = new LinkedHashMap<>();
            decision.put("should_continue", false);
            decision.put("next_step_index", null);
            decision.put("termination_reason", "所有步骤已执行完毕");
            decision.put("decision_reasoning", "已执行" + executedSteps.size() + "个步骤,流程完成");
            return decision;
        }
        
        // 情况2: 上一步失败
        if (lastStepResult != null && Boolean.FALSE.equals(lastStepResult.get("success"))) {
            // 检查是否可以跳过失败步骤继续
            boolean canContinue = lastStepResult.get("can_skip") != null 
                    && Boolean.TRUE.equals(lastStepResult.get("can_skip"));
            
            if (!canContinue) {
                Map<String, Object> decision = new LinkedHashMap<>();
                decision.put("should_continue", false);
                decision.put("next_step_index", null);
                decision.put("termination_reason", "步骤执行失败: " + lastStepResult.get("error"));
                decision.put("decision_reasoning", "上一步执行失败且无法跳过");
                return decision;
            }
        }
        
        // 情况3: 简单的顺序执行(无分支条件)
        boolean hasNoBranches = steps.stream()
                .allMatch(step -> step.get("branch_condition") == null);
        
        if (hasNoBranches && currentIndex < steps.size()) {
            int nextIndex = currentIndex + 1;
            if (nextIndex <= steps.size()) {
                Map<String, Object> decision = new LinkedHashMap<>();
                decision.put("should_continue", true);
                decision.put("next_step_index", nextIndex);
                decision.put("termination_reason", null);
                decision.put("decision_reasoning", "顺序执行下一步");
                return decision;
            }
        }
        
        // 其他复杂情况返回null,使用LLM决策
        return null;
    }
    
    /**
     * 默认决策(LLM失败时使用)
     */
    private Map<String, Object> makeDefaultDecision(List<Map<String, Object>> steps, Map<String, Object> currentState) {
        @SuppressWarnings("unchecked")
        List<Integer> executedSteps = (List<Integer>) currentState.getOrDefault("executed_steps", new ArrayList<>());
        int currentIndex = ((Number) currentState.getOrDefault("current_step_index", 0)).intValue();
        
        Map<String, Object> decision = new LinkedHashMap<>();
        
        if (currentIndex >= steps.size()) {
            decision.put("should_continue", false);
            decision.put("next_step_index", null);
            decision.put("termination_reason", "所有步骤已完成");
        } else {
            decision.put("should_continue", true);
            decision.put("next_step_index", currentIndex + 1);
            decision.put("termination_reason", null);
        }
        decision.put("decision_reasoning", "默认顺序执行");
        
        return decision;
    }
    
    private String buildPrompt(List<Map<String, Object>> steps, Map<String, Object> currentState, Map<String, Object> lastStepResult) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请根据以下信息决定下一步行动:\n\n");
        
        prompt.append("【步骤列表】\n");
        for (Map<String, Object> step : steps) {
            prompt.append(String.format("- 步骤%s: %s\n", step.get("step_index"), step.get("step_name")));
        }
        
        prompt.append("\n【当前状态】\n").append(currentState).append("\n");
        
        if (lastStepResult != null) {
            prompt.append("\n【上一步结果】\n").append(lastStepResult).append("\n");
        }
        
        prompt.append("\n请决定是否继续执行以及下一步。");
        return prompt.toString();
    }
}
