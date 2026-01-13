package com.company.diagnosis.agent.executionLayer;

import com.company.diagnosis.agent.base.BaseIntelligentAgent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * 需求理解智能体（执行层）
 * 
 * 职责：
 * - 解析上层传入的结构化文本输入
 * - 提取任务目标、关键约束、期望输出
 * 
 * 输入数据结构（Map）：
 * {
 *   "input_text": "诊断M机单播通道中断问题，设备ID为123",
 *   "context": {
 *     "history_steps": [...],
 *     "parameter_memory": {...}
 *   }
 * }
 * 
 * 输出数据结构（Map）：
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
    
    public RequirementUnderstandingAgent() {
        super("RequirementUnderstandingAgent");
    }
    
    @Override
    public Mono<Map<String, Object>> execute(Map<String, Object> input) {
        return Mono.fromCallable(() -> {
            logger.info("需求理解智能体开始执行");
            
            try {
                // 提取输入参数
                String inputText = (String) input.get("input_text");
                // 也支持problem字段
                if (inputText == null) {
                    inputText = (String) input.get("problem");
                }
                
                @SuppressWarnings("unchecked")
                Map<String, Object> context = (Map<String, Object>) input.getOrDefault("context", new HashMap<>());
                
                if (inputText == null || inputText.isEmpty()) {
                    return errorResponse("输入文本不能为空");
                }
                
                // 分析需求
                Map<String, Object> understanding = analyzeRequirement(inputText, context);
                
                logger.info("需求理解完成: {}", understanding.get("task_objective"));
                return successResponse(understanding);
                
            } catch (Exception e) {
                String error = handleException(e, "需求理解");
                return errorResponse(error);
            }
        });
    }
    
    @Override
    public Flux<Map<String, Object>> executeStream(Map<String, Object> input) {
        return Flux.create(sink -> {
            sink.next(yieldThinkingEvent("分析诊断需求", "requirement_analysis"));
            
            execute(input).subscribe(
                    result -> {
                        sink.next(yieldResultEvent(result));
                        sink.complete();
                    },
                    error -> {
                        sink.next(yieldErrorEvent(error.getMessage(), "RequirementUnderstandingError"));
                        sink.complete();
                    }
            );
        });
    }
    
    /**
     * 分析诊断需求
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> analyzeRequirement(String inputText, Map<String, Object> context) {
        Map<String, Object> result = new HashMap<>();
        
        // 提取任务目标
        String taskObjective = extractTaskObjective(inputText);
        result.put("task_objective", taskObjective);
        
        // 提取关键约束
        List<String> keyConstraints = extractKeyConstraints(inputText);
        result.put("key_constraints", keyConstraints);
        
        // 确定期望输出格式
        String expectedOutput = determineExpectedOutput(inputText);
        result.put("expected_output", expectedOutput);
        
        // 提取参数
        Map<String, Object> extractedParams = extractParameters(inputText);
        result.put("extracted_parameters", extractedParams);
        
        // 识别诊断类型
        String diagnosisType = inferDiagnosisType(inputText);
        result.put("diagnosis_type", diagnosisType);
        
        // 确定诊断范围
        List<String> scope = determineDiagnosisScope(inputText);
        result.put("scope", scope);
        
        return result;
    }
    
    /**
     * 提取任务目标
     */
    private String extractTaskObjective(String inputText) {
        // 简化：直接使用输入文本作为目标
        // 实际应用中可以使用NLP提取
        if (inputText.length() > 100) {
            return inputText.substring(0, 100) + "...";
        }
        return inputText;
    }
    
    /**
     * 提取关键约束
     */
    private List<String> extractKeyConstraints(String inputText) {
        List<String> constraints = new ArrayList<>();
        
        // 提取设备约束
        java.util.regex.Pattern devicePattern = java.util.regex.Pattern.compile("设备[ID|id|Id]?[：:为]?\\s*([A-Za-z0-9_-]+)");
        java.util.regex.Matcher deviceMatcher = devicePattern.matcher(inputText);
        if (deviceMatcher.find()) {
            constraints.add("设备ID必须为" + deviceMatcher.group(1));
        }
        
        // 提取通道约束
        java.util.regex.Pattern channelPattern = java.util.regex.Pattern.compile("通道[号]?[：:为]?\\s*(\\d+)");
        java.util.regex.Matcher channelMatcher = channelPattern.matcher(inputText);
        if (channelMatcher.find()) {
            constraints.add("通道号必须为" + channelMatcher.group(1));
        }
        
        // 根据问题类型添加约束
        String lower = inputText.toLowerCase();
        if (lower.contains("中断") || lower.contains("断开")) {
            constraints.add("需要检查网络连通性");
        }
        if (lower.contains("故障") || lower.contains("异常")) {
            constraints.add("需要检查设备状态");
        }
        
        return constraints;
    }
    
    /**
     * 确定期望输出
     */
    private String determineExpectedOutput(String inputText) {
        return "诊断结论+根本原因+建议措施";
    }
    
    /**
     * 提取参数
     */
    private Map<String, Object> extractParameters(String inputText) {
        Map<String, Object> params = new HashMap<>();
        
        // 提取设备ID
        java.util.regex.Pattern devicePattern = java.util.regex.Pattern.compile("设备[ID|id|Id]?[：:为]?\\s*([A-Za-z0-9_-]+)");
        java.util.regex.Matcher deviceMatcher = devicePattern.matcher(inputText);
        if (deviceMatcher.find()) {
            params.put("deviceId", deviceMatcher.group(1));
        }
        
        // 提取通道号
        java.util.regex.Pattern channelPattern = java.util.regex.Pattern.compile("通道[号]?[：:为]?\\s*(\\d+)");
        java.util.regex.Matcher channelMatcher = channelPattern.matcher(inputText);
        if (channelMatcher.find()) {
            params.put("channelNum", Integer.parseInt(channelMatcher.group(1)));
        }
        
        // 提取IP地址
        java.util.regex.Pattern ipPattern = java.util.regex.Pattern.compile("(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})");
        java.util.regex.Matcher ipMatcher = ipPattern.matcher(inputText);
        if (ipMatcher.find()) {
            params.put("ipAddress", ipMatcher.group(1));
        }
        
        return params;
    }
    
    /**
     * 推断诊断类型
     */
    private String inferDiagnosisType(String inputText) {
        String lower = inputText.toLowerCase();
        
        if (lower.contains("通道") && (lower.contains("中断") || lower.contains("断开"))) {
            return "通道中断诊断";
        }
        if (lower.contains("设备") && (lower.contains("故障") || lower.contains("异常"))) {
            return "设备故障诊断";
        }
        if (lower.contains("网络") || lower.contains("连接")) {
            return "网络连接诊断";
        }
        if (lower.contains("性能") || lower.contains("慢")) {
            return "性能诊断";
        }
        
        return "通用诊断";
    }
    
    /**
     * 确定诊断范围
     */
    private List<String> determineDiagnosisScope(String inputText) {
        List<String> scope = new ArrayList<>();
        
        String lower = inputText.toLowerCase();
        
        if (lower.contains("设备") || lower.contains("device")) {
            scope.add("device_info");
        }
        if (lower.contains("通道") || lower.contains("channel")) {
            scope.add("channel_status");
        }
        if (lower.contains("网络") || lower.contains("network")) {
            scope.add("network_connectivity");
        }
        if (lower.contains("配置") || lower.contains("config")) {
            scope.add("configuration");
        }
        
        if (scope.isEmpty()) {
            scope.add("general");
        }
        
        return scope;
    }
}
