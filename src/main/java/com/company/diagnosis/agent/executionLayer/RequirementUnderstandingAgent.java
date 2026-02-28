package com.company.diagnosis.agent.executionLayer;

import com.company.diagnosis.agent.base.BaseIntelligentAgent;
import com.company.diagnosis.util.PromptUtil;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * 需求理解智能体（执行层）
 * 
 * 职责：
 * - 解析上层传入的结构化文本输入
 * - 提取任务目标、关键约束、期望输出
 * 
 * 输入数据结构：
 * {
 *   "input_text": "诊断M机单播通道中断问题，设备ID为123",
 *   "context": {
 *     "history_steps": [...],
 *     "parameter_memory": {...}
 *   }
 * }
 * 
 * 输出数据结构：
 * {
 *   "success": true/false,
 *   "task_objective": "诊断M机单播通道中断问题",
 *   "key_constraints": ["设备ID必须为123", "需要检查网络连通性"],
 *   "expected_output": "诊断结论+根本原因+建议措施",
 *   "error": "错误信息（如果success=false）"
 * }
 * 
 * @author AIOperation Team
 * @since 2026-01-13
 */
@Component
public class RequirementUnderstandingAgent extends BaseIntelligentAgent {
    
    private static final String SYSTEM_PROMPT = """
        你是一个需求理解专家。你的任务是分析用户输入的诊断需求,提取以下信息:
        1. task_objective: 核心任务目标(简洁明确)
        2. key_constraints: 关键约束条件列表(如设备ID、时间范围等)
        3. expected_output: 期望的输出格式描述
        
        请以JSON格式输出,包含以下字段:
        {
            "task_objective": "任务目标描述",
            "key_constraints": ["约束1", "约束2"],
            "expected_output": "期望输出描述"
        }
        
        注意:
        - 保持简洁,避免冗余信息
        - 约束条件要具体、可验证
        - 如果输入不清晰,基于上下文合理推断
        """;
    
    public RequirementUnderstandingAgent() {
        super("RequirementUnderstandingAgent");
    }
    
    @Override
    public Mono<Map<String, Object>> process(Map<String, Object> input) {
        logger.info("[{}] 开始处理需求理解任务", agentName);
        
        // 1. 提取输入文本
        String inputText = (String) input.get("input_text");
        if (inputText == null || inputText.trim().isEmpty()) {
            logger.warn("[{}] 输入文本为空", agentName);
            return Mono.just(errorResponse("输入文本不能为空"));
        }
        
        // 2. 提取上下文信息
        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>) input.getOrDefault("context", new HashMap<>());
        
        // 3. 构建提示词
        String prompt = buildPrompt(inputText, context);
        
        // 4. 调用LLM进行需求理解
        return callLlmJson(prompt, SYSTEM_PROMPT, 0.3, 2)
                .map(response -> {
                    if (!response.isSuccess()) {
                        logger.error("[{}] LLM调用失败: {}", agentName, response.getError());
                        return errorResponse("需求理解失败: " + response.getError());
                    }
                    
                    Map<String, Object> llmData = response.getData();
                    if (llmData == null) {
                        return errorResponse("LLM返回数据为空");
                    }
                    
                    // 5. 构建输出
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("task_objective", llmData.getOrDefault("task_objective", inputText));
                    result.put("key_constraints", llmData.getOrDefault("key_constraints", new ArrayList<>()));
                    result.put("expected_output", llmData.getOrDefault("expected_output", "诊断结论和建议"));
                    
                    logger.info("[{}] 需求理解完成: objective={}", agentName, result.get("task_objective"));
                    return successResponse(result);
                })
                .onErrorResume(e -> {
                    String error = handleException((Exception) e, "需求理解");
                    return Mono.just(errorResponse(error));
                });
    }
    
    private String buildPrompt(String inputText, Map<String, Object> context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请分析以下诊断需求:\n\n");
        prompt.append("【用户输入】\n").append(inputText).append("\n\n");
        
        // 添加上下文信息
        if (!context.isEmpty()) {
            prompt.append("【上下文信息】\n");
            @SuppressWarnings("unchecked")
            Map<String, Object> paramMemory = (Map<String, Object>) context.get("parameter_memory");
            if (paramMemory != null && !paramMemory.isEmpty()) {
                prompt.append("已知参数: ").append(paramMemory).append("\n");
            }
        }
        
        prompt.append("\n请提取任务目标、关键约束和期望输出。");
        return prompt.toString();
    }
}
