package com.company.diagnosis.agent.executionLayer;

import com.company.diagnosis.agent.base.BaseIntelligentAgent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
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
 * 输入数据结构（Map）：
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
 * 输出数据结构（Map）：
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
    
    public StepDecisionAgent() {
        super("StepDecisionAgent");
    }
    
    @Override
    public Mono<Map<String, Object>> execute(Map<String, Object> input) {
        return Mono.fromCallable(() -> {
            logger.info("步骤决策智能体开始执行");
            
            try {
                // 提取输入参数
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> steps = (List<Map<String, Object>>) input.get("steps");
                @SuppressWarnings("unchecked")
                Map<String, Object> currentState = (Map<String, Object>) input.getOrDefault("current_state", new HashMap<>());
                @SuppressWarnings("unchecked")
                Map<String, Object> lastStepResult = (Map<String, Object>) input.get("last_step_result");
                @SuppressWarnings("unchecked")
                Map<String, Object> parameterMemory = (Map<String, Object>) input.getOrDefault("parameter_memory", new HashMap<>());
                
                if (steps == null || steps.isEmpty()) {
                    return errorResponse("步骤列表不能为空");
                }
                
                // 做出决策
                Map<String, Object> decision = makeDecision(steps, currentState, lastStepResult, parameterMemory);
                
                logger.info("步骤决策完成: shouldContinue={}, nextStep={}", 
                        decision.get("should_continue"), decision.get("next_step_index"));
                return successResponse(decision);
                
            } catch (Exception e) {
                String error = handleException(e, "步骤决策");
                return errorResponse(error);
            }
        });
    }
    
    @Override
    public Flux<Map<String, Object>> executeStream(Map<String, Object> input) {
        return Flux.create(sink -> {
            sink.next(yieldThinkingEvent("分析当前执行状态", "decision_analysis"));
            
            execute(input).subscribe(
                    result -> {
                        sink.next(yieldResultEvent(result));
                        sink.complete();
                    },
                    error -> {
                        sink.next(yieldErrorEvent(error.getMessage(), "StepDecisionError"));
                        sink.complete();
                    }
            );
        });
    }
    
    /**
     * 做出步骤决策
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> makeDecision(List<Map<String, Object>> steps,
                                              Map<String, Object> currentState,
                                              Map<String, Object> lastStepResult,
                                              Map<String, Object> parameterMemory) {
        Map<String, Object> decision = new HashMap<>();
        
        // 获取当前状态
        List<Integer> executedSteps = (List<Integer>) currentState.getOrDefault("executed_steps", new ArrayList<>());
        Integer currentStepIndex = (Integer) currentState.getOrDefault("current_step_index", 0);
        
        // 检查上一步结果
        boolean lastStepSuccess = true;
        if (lastStepResult != null) {
            lastStepSuccess = Boolean.TRUE.equals(lastStepResult.get("success"));
        }
        
        // 如果上一步失败，决定是否重试或终止
        if (!lastStepSuccess && currentStepIndex > 0) {
            String errorMsg = lastStepResult != null ? 
                    (String) lastStepResult.get("error") : "未知错误";
            
            // 检查是否可重试
            int retryCount = (Integer) currentState.getOrDefault("retry_count_" + currentStepIndex, 0);
            if (retryCount < 2) {
                decision.put("should_continue", true);
                decision.put("next_step_index", currentStepIndex);
                decision.put("action", "retry");
                decision.put("decision_reasoning", "上一步执行失败，尝试重试");
                return decision;
            } else {
                decision.put("should_continue", false);
                decision.put("termination_reason", "步骤执行失败且达到最大重试次数: " + errorMsg);
                decision.put("decision_reasoning", "多次重试后仍然失败，终止流程");
                return decision;
            }
        }
        
        // 查找下一个待执行的步骤
        Integer nextStepIndex = findNextStep(steps, executedSteps);
        
        if (nextStepIndex == null) {
            // 所有步骤已完成
            decision.put("should_continue", false);
            decision.put("termination_reason", "所有步骤已完成");
            decision.put("decision_reasoning", "所有规划的步骤都已成功执行");
        } else {
            // 检查步骤的依赖是否满足
            Map<String, Object> nextStep = steps.stream()
                    .filter(s -> nextStepIndex.equals(s.get("step_index")))
                    .findFirst()
                    .orElse(null);
            
            if (nextStep != null) {
                List<Integer> dependsOn = (List<Integer>) nextStep.getOrDefault("depends_on", List.of());
                boolean dependenciesMet = executedSteps.containsAll(dependsOn);
                
                if (dependenciesMet) {
                    decision.put("should_continue", true);
                    decision.put("next_step_index", nextStepIndex);
                    decision.put("next_step_name", nextStep.get("step_name"));
                    decision.put("decision_reasoning", "依赖已满足，继续执行下一步");
                } else {
                    // 查找可以执行的其他步骤
                    Integer alternativeStep = findAlternativeStep(steps, executedSteps, dependsOn);
                    if (alternativeStep != null) {
                        decision.put("should_continue", true);
                        decision.put("next_step_index", alternativeStep);
                        decision.put("decision_reasoning", "当前步骤依赖未满足，先执行其他步骤");
                    } else {
                        decision.put("should_continue", false);
                        decision.put("termination_reason", "存在无法满足的依赖");
                        decision.put("decision_reasoning", "无法找到可执行的步骤");
                    }
                }
            }
        }
        
        return decision;
    }
    
    /**
     * 查找下一个待执行的步骤
     */
    private Integer findNextStep(List<Map<String, Object>> steps, List<Integer> executedSteps) {
        for (Map<String, Object> step : steps) {
            Integer stepIndex = (Integer) step.get("step_index");
            if (!executedSteps.contains(stepIndex)) {
                return stepIndex;
            }
        }
        return null;
    }
    
    /**
     * 查找可替代执行的步骤
     */
    @SuppressWarnings("unchecked")
    private Integer findAlternativeStep(List<Map<String, Object>> steps, 
                                         List<Integer> executedSteps,
                                         List<Integer> blockedDependencies) {
        for (Map<String, Object> step : steps) {
            Integer stepIndex = (Integer) step.get("step_index");
            if (executedSteps.contains(stepIndex)) {
                continue;
            }
            
            List<Integer> dependsOn = (List<Integer>) step.getOrDefault("depends_on", List.of());
            if (executedSteps.containsAll(dependsOn)) {
                return stepIndex;
            }
        }
        return null;
    }
}
