package com.company.diagnosis.agent.interfaceLayer;

import com.company.diagnosis.agent.base.BaseIntelligentAgent;
import com.company.diagnosis.util.JsonUtil;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 参数映射智能体（接口调用层）
 * 
 * 职责：
 * - 理解上层传入的接口调用需求（自然语言描述或结构化字段）
 * - 根据接口文档定义，将需求映射到具体参数
 * - 判断参数传递方式（路径参数、查询参数、请求体）
 * 
 * 输入数据结构（Map）：
 * {
 *   "call_requirement": "查询设备ID为123的通道状态",
 *   "api_doc": { ... },  // 已解析的接口文档
 *   "parameter_memory": { ... }  // 从上层传递的参数记忆
 * }
 * 
 * 输出数据结构（Map）：
 * {
 *   "success": true/false,
 *   "mapped_parameters": {
 *     "deviceId": "123",
 *     "includeStatus": true
 *   },
 *   "transfer_mode": {
 *     "deviceId": "path",
 *     "includeStatus": "query"
 *   },
 *   "error": "错误信息（如果success=false）"
 * }
 * 
 * @author AIOperation Team
 * @since 2026-01-13
 */
@Component
public class ParameterMappingAgent extends BaseIntelligentAgent {
    
    public ParameterMappingAgent() {
        super("ParameterMappingAgent");
    }
    
    @Override
    public Mono<Map<String, Object>> execute(Map<String, Object> input) {
        return Mono.fromCallable(() -> {
            logger.info("参数映射智能体开始执行");
            
            try {
                // 提取输入参数
                String callRequirement = (String) input.get("call_requirement");
                @SuppressWarnings("unchecked")
                Map<String, Object> apiDoc = (Map<String, Object>) input.get("api_doc");
                @SuppressWarnings("unchecked")
                Map<String, Object> parameterMemory = (Map<String, Object>) input.getOrDefault("parameter_memory", new HashMap<>());
                
                if (callRequirement == null || callRequirement.isEmpty()) {
                    return errorResponse("调用需求不能为空");
                }
                
                // 执行参数映射
                Map<String, Object> mappingResult = mapParameters(callRequirement, apiDoc, parameterMemory);
                
                logger.info("参数映射完成: {}", mappingResult);
                return successResponse(mappingResult);
                
            } catch (Exception e) {
                String error = handleException(e, "参数映射");
                return errorResponse(error);
            }
        });
    }
    
    @Override
    public Flux<Map<String, Object>> executeStream(Map<String, Object> input) {
        return Flux.create(sink -> {
            // 发送开始事件
            sink.next(yieldThinkingEvent("开始分析调用需求", "parameter_mapping_start"));
            
            execute(input).subscribe(
                    result -> {
                        sink.next(yieldResultEvent(result));
                        sink.complete();
                    },
                    error -> {
                        sink.next(yieldErrorEvent(error.getMessage(), "ParameterMappingError"));
                        sink.complete();
                    }
            );
        });
    }
    
    /**
     * 执行参数映射
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> mapParameters(String callRequirement, 
                                               Map<String, Object> apiDoc,
                                               Map<String, Object> parameterMemory) {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> mappedParameters = new HashMap<>();
        Map<String, String> transferMode = new HashMap<>();
        
        // 从API文档中提取参数定义
        if (apiDoc != null) {
            List<Map<String, Object>> parameters = (List<Map<String, Object>>) apiDoc.get("parameters");
            if (parameters != null) {
                for (Map<String, Object> param : parameters) {
                    String paramName = (String) param.get("name");
                    String paramIn = (String) param.getOrDefault("in", "query");
                    Boolean required = (Boolean) param.getOrDefault("required", false);
                    
                    // 尝试从参数记忆中获取值
                    Object value = parameterMemory.get(paramName);
                    
                    // 如果参数记忆中没有，尝试从需求中提取
                    if (value == null) {
                        value = extractParameterFromRequirement(callRequirement, paramName);
                    }
                    
                    if (value != null) {
                        mappedParameters.put(paramName, value);
                        transferMode.put(paramName, paramIn);
                    } else if (required) {
                        // 必填参数缺失，使用默认值或占位符
                        Object defaultValue = param.get("default");
                        if (defaultValue != null) {
                            mappedParameters.put(paramName, defaultValue);
                            transferMode.put(paramName, paramIn);
                        }
                    }
                }
            }
        }
        
        // 如果没有API文档，从需求和参数记忆中智能提取
        if (apiDoc == null || mappedParameters.isEmpty()) {
            // 将参数记忆中的所有参数作为查询参数
            parameterMemory.forEach((key, value) -> {
                if (value != null) {
                    mappedParameters.put(key, value);
                    transferMode.put(key, "query");
                }
            });
        }
        
        result.put("mapped_parameters", mappedParameters);
        result.put("transfer_mode", transferMode);
        result.put("call_requirement", callRequirement);
        
        return result;
    }
    
    /**
     * 从需求文本中提取参数值
     */
    private Object extractParameterFromRequirement(String requirement, String paramName) {
        if (requirement == null || paramName == null) {
            return null;
        }
        
        // 简单的参数提取逻辑
        // 查找类似 "参数名为XXX" 或 "参数名=XXX" 的模式
        String lowerReq = requirement.toLowerCase();
        String lowerParam = paramName.toLowerCase();
        
        // 常见的ID参数
        if (lowerParam.contains("id")) {
            // 尝试提取数字
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\b(\\d+)\\b");
            java.util.regex.Matcher matcher = pattern.matcher(requirement);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        
        return null;
    }
}
