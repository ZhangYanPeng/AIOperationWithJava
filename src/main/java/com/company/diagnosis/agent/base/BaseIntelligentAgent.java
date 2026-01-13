package com.company.diagnosis.agent.base;

import com.company.diagnosis.service.KnowledgeService;
import com.company.diagnosis.util.JsonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 智能体抽象基类
 * 
 * 职责：
 * - 提供统一的知识检索能力
 * - 提供统一的LLM调用能力（JSON格式和流式）
 * - 提供统一的事件推送能力
 * - 提供统一的错误处理机制
 * - 管理通用依赖（knowledge_service、llm_client）
 * 
 * 设计原则：
 * - 基类只提供通用能力，不包含具体业务逻辑
 * - 所有方法使用protected前缀，不影响子类对外接口
 * - 统一异常处理和日志记录
 * 
 * @author AIOperation Team
 * @since 2026-01-13
 */
public abstract class BaseIntelligentAgent {
    
    protected static final Logger logger = LoggerFactory.getLogger(BaseIntelligentAgent.class);
    
    /**
     * 智能体名称
     */
    protected String agentName;
    
    /**
     * 知识检索服务（注入）
     */
    @Autowired
    protected KnowledgeService knowledgeService;
    
    /**
     * 构造函数
     * 
     * @param agentName 智能体名称
     */
    public BaseIntelligentAgent(String agentName) {
        this.agentName = agentName;
        logger.info("智能体 {} 初始化成功", agentName);
    }
    
    /**
     * 获取智能体名称
     */
    public String getAgentName() {
        return agentName;
    }
    
    // ==================== 知识检索方法 ====================
    
    /**
     * 通用知识检索方法
     * 
     * 功能：从ES知识库检索相关知识
     * 
     * @param query 查询文本
     * @param agentType 知识类型
     *                  - "tool": 工具接口规范
     *                  - "diagnosis": 诊断策略
     *                  - "reasoning": 推理规则
     *                  - "conclusion": 结论分析规则
     *                  - "domain": 领域知识
     * @param topK 返回结果数量
     * @param fallbackMessage 未找到结果时的默认消息
     * @return Mono<String> 知识内容字符串
     */
    protected Mono<String> retrieveKnowledge(
            String query, 
            String agentType, 
            int topK, 
            String fallbackMessage) {
        
        if (knowledgeService == null) {
            logger.warn("知识服务未注入，返回默认消息");
            return Mono.just(fallbackMessage);
        }
        
        return knowledgeService.searchDocuments(query, agentType, topK, "keyword")
                .map(doc -> {
                    // 提取content字段
                    Object content = doc.get("content");
                    if (content != null) {
                        return content.toString();
                    }
                    // 尝试其他可能的字段
                    Object description = doc.get("description");
                    if (description != null) {
                        return description.toString();
                    }
                    return JsonUtil.toJson(doc);
                })
                .collectList()
                .map(contents -> {
                    if (contents.isEmpty()) {
                        logger.debug("知识检索无结果: query={}, type={}", query, agentType);
                        return fallbackMessage;
                    }
                    return String.join("\n\n---\n\n", contents);
                })
                .onErrorResume(e -> {
                    logger.error("知识检索失败: query={}, error={}", query, e.getMessage(), e);
                    return Mono.just(fallbackMessage);
                });
    }
    
    /**
     * 检索多种类型的知识
     */
    protected Mono<String> retrieveKnowledgeByTypes(String query, List<String> knowledgeTypes, 
                                                     int topK, String fallbackMessage) {
        if (knowledgeService == null || knowledgeTypes == null || knowledgeTypes.isEmpty()) {
            return Mono.just(fallbackMessage);
        }
        
        return knowledgeService.searchByTypes(query, knowledgeTypes, topK)
                .map(doc -> {
                    Object content = doc.get("content");
                    return content != null ? content.toString() : JsonUtil.toJson(doc);
                })
                .collectList()
                .map(contents -> contents.isEmpty() ? fallbackMessage : String.join("\n\n---\n\n", contents))
                .onErrorResume(e -> {
                    logger.error("多类型知识检索失败: error={}", e.getMessage(), e);
                    return Mono.just(fallbackMessage);
                });
    }
    
    // ==================== LLM调用方法 ====================
    
    /**
     * 调用LLM获取JSON格式输出
     * 
     * 功能：发送提示词到LLM，获取结构化JSON响应
     * 
     * @param prompt 用户提示词
     * @param systemPrompt 系统提示词（可选）
     * @param temperature 温度参数（0.0-1.0）
     * @param retryOnFailure 失败重试次数
     * @return Mono<LlmResponse> LLM响应对象
     */
    protected Mono<LlmResponse> callLlmJson(
            String prompt, 
            String systemPrompt, 
            double temperature, 
            int retryOnFailure) {
        
        // 由于LlmClient需要根据实际AgentScope API实现
        // 这里提供一个占位实现，实际项目中需要对接真实的LLM服务
        logger.debug("调用LLM JSON: prompt长度={}, temperature={}", 
                prompt != null ? prompt.length() : 0, temperature);
        
        // 占位实现：返回需要实现的提示
        return Mono.just(new LlmResponse(false, null, "LLM服务待集成"));
    }
    
    /**
     * 调用LLM流式输出
     * 
     * 功能：发送提示词到LLM，以流式方式获取文本响应
     * 
     * @param prompt 用户提示词
     * @param systemPrompt 系统提示词（可选）
     * @param temperature 温度参数（0.0-1.0）
     * @return Flux<String> 文本片段流
     */
    protected Flux<String> callLlmStream(
            String prompt, 
            String systemPrompt, 
            double temperature) {
        
        logger.debug("调用LLM流式: prompt长度={}, temperature={}", 
                prompt != null ? prompt.length() : 0, temperature);
        
        // 占位实现
        return Flux.empty();
    }
    
    // ==================== 事件推送方法 ====================
    
    /**
     * 推送通用事件
     * 
     * 功能：构造事件对象，自动添加agent和timestamp字段
     * 
     * @param eventType 事件类型（如"agent_thinking"、"knowledge_retrieved"）
     * @param data 事件数据（键值对）
     * @return Map<String, Object> 事件字典
     */
    protected Map<String, Object> yieldEvent(String eventType, Map<String, Object> data) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", eventType);
        event.put("agent", agentName);
        event.put("timestamp", LocalDateTime.now().toString());
        
        if (data != null) {
            event.putAll(data);
        }
        
        return event;
    }
    
    /**
     * 推送思考过程事件
     * 
     * 功能：推送agent_thinking事件，标记智能体推理过程
     * 
     * @param message 思考内容
     * @param stage 阶段标识（如"llm_streaming"、"knowledge_retrieval"）
     * @return Map<String, Object> 思考事件字典
     */
    protected Map<String, Object> yieldThinkingEvent(String message, String stage) {
        Map<String, Object> data = new HashMap<>();
        data.put("message", message);
        data.put("stage", stage);
        return yieldEvent("agent_thinking", data);
    }
    
    /**
     * 推送知识检索完成事件
     * 
     * 功能：推送knowledge_retrieved事件，展示检索到的知识
     * 
     * @param knowledgeType 知识类型（如"诊断策略"、"推理规则"）
     * @param content 知识内容
     * @param message 描述信息（可选，自动生成）
     * @return Map<String, Object> 知识事件字典
     */
    protected Map<String, Object> yieldKnowledgeEvent(
            String knowledgeType, 
            String content, 
            String message) {
        
        Map<String, Object> data = new HashMap<>();
        data.put("knowledge_type", knowledgeType);
        data.put("knowledge_content", content);
        data.put("message", message != null ? message : "检索到" + knowledgeType);
        
        return yieldEvent("knowledge_retrieved", data);
    }
    
    /**
     * 推送错误事件
     * 
     * 功能：推送error事件，通知前端错误信息
     * 
     * @param errorMsg 错误信息
     * @param errorType 错误类型（可选，如"LlmCallFailed"、"KnowledgeRetrievalFailed"）
     * @return Map<String, Object> 错误事件字典
     */
    protected Map<String, Object> yieldErrorEvent(String errorMsg, String errorType) {
        Map<String, Object> data = new HashMap<>();
        data.put("error", errorMsg);
        data.put("error_type", errorType != null ? errorType : "UnknownError");
        
        return yieldEvent("error", data);
    }
    
    /**
     * 推送步骤完成事件
     */
    protected Map<String, Object> yieldStepCompletedEvent(int stepIndex, String stepName, 
                                                           Map<String, Object> result) {
        Map<String, Object> data = new HashMap<>();
        data.put("step_index", stepIndex);
        data.put("step_name", stepName);
        data.put("result", result);
        
        return yieldEvent("step_completed", data);
    }
    
    /**
     * 推送结果事件
     */
    protected Map<String, Object> yieldResultEvent(Map<String, Object> result) {
        return yieldEvent("result", result);
    }
    
    // ==================== 错误处理方法 ====================
    
    /**
     * 统一异常处理
     * 
     * 功能：捕获异常，记录日志，返回标准错误信息
     * 
     * @param error 异常对象
     * @param operationName 操作名称（用于日志标识）
     * @return String 错误信息字符串
     */
    protected String handleException(Exception error, String operationName) {
        String errorMsg = operationName + "失败: " + error.getMessage();
        logger.error(errorMsg, error);
        return errorMsg;
    }
    
    /**
     * 安全的JSON解析
     * 
     * 功能：解析JSON字符串，失败时返回Optional.empty()
     * 
     * @param text 待解析文本
     * @return Optional<Map<String, Object>> 解析结果或空
     */
    protected Optional<Map<String, Object>> safeJsonParse(String text) {
        if (text == null || text.isEmpty()) {
            return Optional.empty();
        }
        
        try {
            Map<String, Object> result = JsonUtil.jsonToMap(text);
            return result != null && !result.isEmpty() ? Optional.of(result) : Optional.empty();
        } catch (Exception e) {
            logger.warn("JSON解析失败: {}", e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * 从文本中提取JSON
     */
    protected Optional<Map<String, Object>> extractJsonFromText(String text) {
        if (text == null || text.isEmpty()) {
            return Optional.empty();
        }
        
        // 尝试直接解析
        Optional<Map<String, Object>> direct = safeJsonParse(text);
        if (direct.isPresent()) {
            return direct;
        }
        
        // 尝试从Markdown代码块中提取
        int jsonStart = text.indexOf("```json");
        int jsonEnd = text.lastIndexOf("```");
        
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            String jsonContent = text.substring(jsonStart + 7, jsonEnd).trim();
            return safeJsonParse(jsonContent);
        }
        
        // 尝试查找JSON对象
        int braceStart = text.indexOf("{");
        int braceEnd = text.lastIndexOf("}");
        
        if (braceStart >= 0 && braceEnd > braceStart) {
            String jsonContent = text.substring(braceStart, braceEnd + 1);
            return safeJsonParse(jsonContent);
        }
        
        return Optional.empty();
    }
    
    // ==================== 消息构建方法 ====================
    
    /**
     * 构造错误响应
     * 
     * @param errorMsg 错误信息
     * @return 错误响应Map
     */
    protected Map<String, Object> errorResponse(String errorMsg) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", errorMsg);
        response.put("agent", agentName);
        response.put("timestamp", LocalDateTime.now().toString());
        return response;
    }
    
    /**
     * 构造成功响应
     * 
     * @param data 响应数据
     * @return 成功响应Map
     */
    protected Map<String, Object> successResponse(Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("agent", agentName);
        response.put("timestamp", LocalDateTime.now().toString());
        
        if (data != null) {
            response.put("data", data);
        }
        
        return response;
    }
    
    // ==================== 抽象方法 ====================
    
    /**
     * 执行智能体逻辑
     * 
     * @param input 输入参数
     * @return 执行结果
     */
    public abstract Mono<Map<String, Object>> execute(Map<String, Object> input);
    
    /**
     * 流式执行智能体逻辑
     * 
     * @param input 输入参数
     * @return 事件流
     */
    public Flux<Map<String, Object>> executeStream(Map<String, Object> input) {
        // 默认实现：将execute结果包装为单元素流
        return execute(input).flux();
    }
    
    // ==================== 内部类：LLM响应对象 ====================
    
    /**
     * LLM响应数据结构
     */
    protected static class LlmResponse {
        private final boolean success;
        private final Map<String, Object> data;
        private final String error;
        
        public LlmResponse(boolean success, Map<String, Object> data, String error) {
            this.success = success;
            this.data = data;
            this.error = error;
        }
        
        public boolean isSuccess() { return success; }
        public Map<String, Object> getData() { return data; }
        public String getError() { return error; }
    }
}
