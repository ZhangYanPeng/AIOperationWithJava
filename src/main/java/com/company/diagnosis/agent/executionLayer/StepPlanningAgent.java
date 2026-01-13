package com.company.diagnosis.agent.executionLayer;

import com.company.diagnosis.agent.base.BaseIntelligentAgent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
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
 * 输入数据结构（Map）：
 * {
 *   "task_objective": "诊断M机单播通道中断问题",
 *   "retrieved_knowledge": "诊断策略文本...",
 *   "parameter_memory": {...}
 * }
 * 
 * 输出数据结构（Map）：
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
    
    public StepPlanningAgent() {
        super("StepPlanningAgent");
    }
    
    @Override
    public Mono<Map<String, Object>> execute(Map<String, Object> input) {
        return Mono.fromCallable(() -> {
            logger.info("步骤规划智能体开始执行");
            
            try {
                // 提取输入参数
                String taskObjective = (String) input.get("task_objective");
                String retrievedKnowledge = (String) input.get("retrieved_knowledge");
                @SuppressWarnings("unchecked")
                Map<String, Object> parameterMemory = (Map<String, Object>) input.getOrDefault("parameter_memory", new HashMap<>());
                
                if (taskObjective == null || taskObjective.isEmpty()) {
                    return errorResponse("任务目标不能为空");
                }
                
                // 检索相关知识
                String knowledge = retrievedKnowledge;
                if (knowledge == null || knowledge.isEmpty()) {
                    knowledge = retrieveKnowledge(taskObjective, "diagnosis", 3, "无相关知识").block();
                }
                
                // 生成步骤计划
                List<Map<String, Object>> steps = generateSteps(taskObjective, knowledge, parameterMemory);
                
                Map<String, Object> resultData = new HashMap<>();
                resultData.put("steps", steps);
                resultData.put("task_objective", taskObjective);
                resultData.put("total_steps", steps.size());
                
                logger.info("步骤规划完成: 共{}个步骤", steps.size());
                return successResponse(resultData);
                
            } catch (Exception e) {
                String error = handleException(e, "步骤规划");
                return errorResponse(error);
            }
        });
    }
    
    @Override
    public Flux<Map<String, Object>> executeStream(Map<String, Object> input) {
        return Flux.create(sink -> {
            // 发送开始事件
            sink.next(yieldThinkingEvent("开始分析任务并规划步骤", "step_planning_start"));
            
            // 发送知识检索事件
            String taskObjective = (String) input.get("task_objective");
            if (taskObjective != null) {
                sink.next(yieldThinkingEvent("检索相关诊断知识", "knowledge_retrieval"));
            }
            
            execute(input).subscribe(
                    result -> {
                        // 发送步骤规划完成事件
                        @SuppressWarnings("unchecked")
                        Map<String, Object> data = (Map<String, Object>) result.get("data");
                        if (data != null) {
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> steps = (List<Map<String, Object>>) data.get("steps");
                            if (steps != null) {
                                for (Map<String, Object> step : steps) {
                                    sink.next(yieldThinkingEvent(
                                            "规划步骤: " + step.get("step_name"), 
                                            "step_planned"));
                                }
                            }
                        }
                        sink.next(yieldResultEvent(result));
                        sink.complete();
                    },
                    error -> {
                        sink.next(yieldErrorEvent(error.getMessage(), "StepPlanningError"));
                        sink.complete();
                    }
            );
        });
    }
    
    /**
     * 生成执行步骤
     */
    private List<Map<String, Object>> generateSteps(String taskObjective, 
                                                     String knowledge,
                                                     Map<String, Object> parameterMemory) {
        List<Map<String, Object>> steps = new ArrayList<>();
        
        // 根据任务类型生成通用步骤框架
        String lowerObjective = taskObjective.toLowerCase();
        
        int stepIndex = 1;
        
        // 步骤1: 参数收集/理解需求
        Map<String, Object> step1 = createStep(
                stepIndex++,
                "理解诊断需求",
                "分析并理解诊断任务的具体需求",
                "RequirementUnderstandingAgent",
                null,
                List.of()
        );
        steps.add(step1);
        
        // 步骤2: 信息查询
        if (lowerObjective.contains("设备") || lowerObjective.contains("device")) {
            Map<String, Object> step2 = createStep(
                    stepIndex++,
                    "查询设备信息",
                    "获取设备的基本信息和当前状态",
                    "查询设备信息接口",
                    null,
                    List.of(1)
            );
            steps.add(step2);
        }
        
        // 步骤3: 通道状态检查（如果涉及通道）
        if (lowerObjective.contains("通道") || lowerObjective.contains("channel")) {
            Map<String, Object> step3 = createStep(
                    stepIndex++,
                    "检查通道状态",
                    "查询通道的连接状态和配置信息",
                    "查询通道状态接口",
                    null,
                    List.of(stepIndex - 2)
            );
            steps.add(step3);
        }
        
        // 步骤4: 数据分析
        Map<String, Object> stepAnalysis = createStep(
                stepIndex++,
                "分析诊断数据",
                "根据收集的信息进行分析和推理",
                "StepDecisionAgent",
                null,
                steps.stream().map(s -> (Integer) s.get("step_index")).toList()
        );
        steps.add(stepAnalysis);
        
        // 步骤5: 生成结论
        Map<String, Object> stepConclusion = createStep(
                stepIndex++,
                "生成诊断结论",
                "综合分析结果，生成诊断结论和建议",
                "ResultGenerationAgent",
                null,
                List.of(stepIndex - 2)
        );
        steps.add(stepConclusion);
        
        return steps;
    }
    
    /**
     * 创建步骤对象
     */
    private Map<String, Object> createStep(int index, String name, String goal, 
                                            String tool, String branchCondition, 
                                            List<Integer> dependsOn) {
        Map<String, Object> step = new HashMap<>();
        step.put("step_index", index);
        step.put("step_name", name);
        step.put("step_goal", goal);
        step.put("required_tool", tool);
        step.put("branch_condition", branchCondition);
        step.put("depends_on", dependsOn != null ? dependsOn : List.of());
        step.put("status", "pending");
        return step;
    }
}
