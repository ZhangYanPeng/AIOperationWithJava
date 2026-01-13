package com.company.diagnosis.agent.executionLayer;

import com.company.diagnosis.agent.base.BaseIntelligentAgent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 结果生成智能体（执行层）
 * 
 * 职责：
 * - 当步骤决策智能体判断流程结束时被调用
 * - 综合所有步骤的执行历史和参数记忆，生成最终输出
 * - 格式化为符合上层要求的结构化结果
 * 
 * 输入数据结构（Map）：
 * {
 *   "task_objective": "诊断M机单播通道中断问题",
 *   "execution_history": [...],
 *   "parameter_memory": {...},
 *   "expected_output_format": "诊断结论+根本原因+建议措施"
 * }
 * 
 * 输出数据结构（Map）：
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
    
    public ResultGenerationAgent() {
        super("ResultGenerationAgent");
    }
    
    @Override
    public Mono<Map<String, Object>> execute(Map<String, Object> input) {
        return Mono.fromCallable(() -> {
            logger.info("结果生成智能体开始执行");
            
            try {
                // 提取输入参数
                String taskObjective = (String) input.get("task_objective");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> executionHistory = (List<Map<String, Object>>) input.get("execution_history");
                @SuppressWarnings("unchecked")
                Map<String, Object> parameterMemory = (Map<String, Object>) input.getOrDefault("parameter_memory", new HashMap<>());
                String expectedOutputFormat = (String) input.get("expected_output_format");
                
                // 生成最终结果
                Map<String, Object> finalResult = generateFinalResult(taskObjective, executionHistory, parameterMemory);
                
                // 生成摘要
                String summary = generateSummary(executionHistory);
                
                Map<String, Object> resultData = new HashMap<>();
                resultData.put("final_result", finalResult);
                resultData.put("summary", summary);
                resultData.put("generated_at", LocalDateTime.now().toString());
                
                logger.info("诊断结果生成完成: {}", summary);
                return successResponse(resultData);
                
            } catch (Exception e) {
                String error = handleException(e, "结果生成");
                return errorResponse(error);
            }
        });
    }
    
    @Override
    public Flux<Map<String, Object>> executeStream(Map<String, Object> input) {
        return Flux.create(sink -> {
            sink.next(yieldThinkingEvent("综合分析诊断结果", "result_analysis"));
            sink.next(yieldThinkingEvent("生成诊断报告", "report_generation"));
            
            execute(input).subscribe(
                    result -> {
                        sink.next(yieldResultEvent(result));
                        sink.complete();
                    },
                    error -> {
                        sink.next(yieldErrorEvent(error.getMessage(), "ResultGenerationError"));
                        sink.complete();
                    }
            );
        });
    }
    
    /**
     * 生成最终结果
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> generateFinalResult(String taskObjective,
                                                     List<Map<String, Object>> executionHistory,
                                                     Map<String, Object> parameterMemory) {
        Map<String, Object> finalResult = new HashMap<>();
        
        // 收集所有发现
        List<String> evidence = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        
        if (executionHistory != null) {
            for (Map<String, Object> step : executionHistory) {
                Boolean success = (Boolean) step.getOrDefault("success", false);
                if (success) {
                    Map<String, Object> data = (Map<String, Object>) step.get("data");
                    if (data != null) {
                        String finding = extractFinding(data);
                        if (finding != null) {
                            evidence.add(finding);
                        }
                    }
                } else {
                    String error = (String) step.get("error");
                    if (error != null) {
                        errors.add(error);
                    }
                }
            }
        }
        
        // 生成诊断结论
        String diagnosisConclusion = generateConclusion(taskObjective, evidence, errors);
        finalResult.put("diagnosis_conclusion", diagnosisConclusion);
        
        // 分析根本原因
        String rootCause = analyzeRootCause(taskObjective, evidence);
        finalResult.put("root_cause", rootCause);
        
        // 收集证据
        finalResult.put("evidence", evidence);
        
        // 生成建议
        List<String> recommendations = generateRecommendations(rootCause, errors);
        finalResult.put("recommendations", recommendations);
        
        // 诊断状态
        String status = errors.isEmpty() ? "completed" : "partial";
        finalResult.put("status", status);
        
        return finalResult;
    }
    
    /**
     * 从步骤数据中提取发现
     */
    private String extractFinding(Map<String, Object> data) {
        if (data.containsKey("status")) {
            return "状态: " + data.get("status");
        }
        if (data.containsKey("result")) {
            return "结果: " + data.get("result");
        }
        if (data.containsKey("message")) {
            return String.valueOf(data.get("message"));
        }
        if (data.containsKey("summary")) {
            return String.valueOf(data.get("summary"));
        }
        return null;
    }
    
    /**
     * 生成诊断结论
     */
    private String generateConclusion(String taskObjective, List<String> evidence, List<String> errors) {
        if (evidence.isEmpty() && errors.isEmpty()) {
            return "诊断流程已完成，但未收集到足够的证据";
        }
        
        if (!errors.isEmpty()) {
            return String.format("诊断过程中遇到%d个错误，结论可能不完整。基于现有证据的初步结论：%s",
                    errors.size(), taskObjective != null ? "针对" + taskObjective + "的诊断已完成" : "诊断已完成");
        }
        
        return String.format("基于%d项证据，%s的诊断已完成", evidence.size(),
                taskObjective != null ? taskObjective : "问题");
    }
    
    /**
     * 分析根本原因
     */
    private String analyzeRootCause(String taskObjective, List<String> evidence) {
        if (evidence.isEmpty()) {
            return "未能确定根本原因，建议进行更详细的诊断";
        }
        
        // 基于任务目标和证据推断根因
        String lower = taskObjective != null ? taskObjective.toLowerCase() : "";
        
        if (lower.contains("中断") || lower.contains("断开")) {
            return "可能的根本原因：网络连接异常或服务端故障";
        }
        if (lower.contains("超时")) {
            return "可能的根本原因：网络延迟过高或服务响应缓慢";
        }
        if (lower.contains("故障") || lower.contains("异常")) {
            return "可能的根本原因：设备硬件故障或配置错误";
        }
        
        return "根据诊断证据分析：" + String.join("; ", evidence.subList(0, Math.min(3, evidence.size())));
    }
    
    /**
     * 生成建议
     */
    private List<String> generateRecommendations(String rootCause, List<String> errors) {
        List<String> recommendations = new ArrayList<>();
        
        // 根据根因生成建议
        if (rootCause.contains("网络")) {
            recommendations.add("检查网络连接和防火墙配置");
            recommendations.add("测试网络连通性");
        }
        if (rootCause.contains("服务")) {
            recommendations.add("检查服务状态和日志");
            recommendations.add("尝试重启相关服务");
        }
        if (rootCause.contains("设备")) {
            recommendations.add("检查设备物理状态");
            recommendations.add("查看设备日志");
        }
        if (rootCause.contains("配置")) {
            recommendations.add("核对配置参数");
            recommendations.add("与标准配置进行对比");
        }
        
        // 如果有错误，添加通用建议
        if (!errors.isEmpty()) {
            recommendations.add("建议排查诊断过程中的错误后重新诊断");
        }
        
        // 通用建议
        recommendations.add("如问题持续存在，建议联系技术支持");
        
        return recommendations;
    }
    
    /**
     * 生成摘要
     */
    private String generateSummary(List<Map<String, Object>> executionHistory) {
        int totalSteps = executionHistory != null ? executionHistory.size() : 0;
        
        if (totalSteps == 0) {
            return "诊断完成，未执行具体步骤";
        }
        
        long successSteps = executionHistory.stream()
                .filter(step -> Boolean.TRUE.equals(step.get("success")))
                .count();
        
        return String.format("诊断完成，共执行%d个步骤，成功%d个", totalSteps, successSteps);
    }
}
