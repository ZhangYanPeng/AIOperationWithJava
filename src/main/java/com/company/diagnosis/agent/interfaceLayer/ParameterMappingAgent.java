package com.company.diagnosis.agent.interfaceLayer;

import com.company.diagnosis.agent.base.BaseIntelligentAgent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * 参数映射智能体（接口调用层）
 * 
 * 职责：
 * - 理解上层传入的接口调用需求（自然语言描述或结构化字段）
 * - 根据接口文档定义，将需求映射到具体参数
 * - 判断参数传递方式（路径参数、查询参数、请求体）
 * 
 * 输入数据结构：
 * {
 *   "call_requirement": "查询设备ID为123的通道状态",
 *   "api_doc": { ... },  // 已解析的接口文档
 *   "parameter_memory": { ... }  // 从上层传递的参数记忆
 * }
 * 
 * 输出数据结构：
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
    
    private static final String SYSTEM_PROMPT = """
        你是一个API参数映射专家。根据调用需求和接口文档,将需求映射到具体的API参数。
        
        请以JSON格式输出:
        {
            "mapped_parameters": {
                "参数名1": "参数值1",
                "参数名2": "参数值2"
            },
            "transfer_mode": {
                "参数名1": "path/query/body",
                "参数名2": "path/query/body"
            },
            "api_endpoint": "/api/xxx",
            "http_method": "GET/POST/PUT/DELETE"
        }
        
        注意:
        - 参数名要与API文档定义一致
        - 参数值要从需求中准确提取
        - transfer_mode说明参数传递方式:
          - path: 路径参数
          - query: URL查询参数
          - body: 请求体参数
        - 如需必填参数缺失,在mapped_parameters中标注为null
        """;
    
    public ParameterMappingAgent() {
        super("ParameterMappingAgent");
    }
    
    @Override
    public Mono<Map<String, Object>> process(Map<String, Object> input) {
        logger.info("[{}] 开始参数映射", agentName);
        
        // 1. 提取输入
        String callRequirement = (String) input.get("call_requirement");
        if (callRequirement == null || callRequirement.trim().isEmpty()) {
            return Mono.just(errorResponse("调用需求不能为空"));
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> apiDoc = (Map<String, Object>) input.get("api_doc");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> paramMemory = (Map<String, Object>) input.getOrDefault("parameter_memory", new HashMap<>());
        
        // 2. 如果没有API文档,尝试从知识库检索
        Mono<String> apiDocMono;
        if (apiDoc == null || apiDoc.isEmpty()) {
            // 从调用需求中提取关键词检索API文档
            apiDocMono = retrieveKnowledge(callRequirement, "tool", 1, "");
        } else {
            apiDocMono = Mono.just(apiDoc.toString());
        }
        
        return apiDocMono.flatMap(docContent -> {
            // 3. 构建提示词
            String prompt = buildPrompt(callRequirement, docContent, paramMemory);
            
            // 4. 调用LLM进行参数映射
            return callLlmJson(prompt, SYSTEM_PROMPT, 0.2, 2);
        })
        .map(response -> {
            if (!response.isSuccess()) {
                // 失败时尝试简单映射
                return successResponse(generateSimpleMapping(callRequirement, paramMemory));
            }
            
            Map<String, Object> llmData = response.getData();
            if (llmData == null) {
                return successResponse(generateSimpleMapping(callRequirement, paramMemory));
            }
            
            // 5. 构建输出
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("mapped_parameters", llmData.getOrDefault("mapped_parameters", new HashMap<>()));
            result.put("transfer_mode", llmData.getOrDefault("transfer_mode", new HashMap<>()));
            result.put("api_endpoint", llmData.get("api_endpoint"));
            result.put("http_method", llmData.getOrDefault("http_method", "GET"));
            
            logger.info("[{}] 参数映射完成: endpoint={}", agentName, result.get("api_endpoint"));
            return successResponse(result);
        })
        .onErrorResume(e -> {
            String error = handleException((Exception) e, "参数映射");
            return Mono.just(errorResponse(error));
        });
    }
    
    private String buildPrompt(String callRequirement, String apiDocContent, Map<String, Object> paramMemory) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请根据以下信息进行API参数映射:\n\n");
        
        prompt.append("【调用需求】\n").append(callRequirement).append("\n\n");
        
        if (!apiDocContent.isEmpty()) {
            prompt.append("【接口文档】\n").append(apiDocContent).append("\n\n");
        }
        
        if (!paramMemory.isEmpty()) {
            prompt.append("【可用参数】\n").append(paramMemory).append("\n\n");
        }
        
        prompt.append("请将调用需求映射到具体的API参数。");
        return prompt.toString();
    }
    
    private Map<String, Object> generateSimpleMapping(String callRequirement, Map<String, Object> paramMemory) {
        Map<String, Object> result = new LinkedHashMap<>();
        
        // 直接使用参数记忆中的参数
        Map<String, Object> mappedParameters = new HashMap<>(paramMemory);
        result.put("mapped_parameters", mappedParameters);
        
        // 默认都作为查询参数
        Map<String, String> transferMode = new HashMap<>();
        for (String key : paramMemory.keySet()) {
            transferMode.put(key, "query");
        }
        result.put("transfer_mode", transferMode);
        
        result.put("api_endpoint", "/api/tool/execute");
        result.put("http_method", "POST");
        
        return result;
    }
}
