package com.company.diagnosis.agent.base;

import com.company.diagnosis.config.AgentScopeConfig.LlmClientRegistry;
import com.company.diagnosis.llm.LlmClient;
import com.company.diagnosis.llm.LlmResponse;
import com.company.diagnosis.service.KnowledgeService;
import com.company.diagnosis.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

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
 * - 符合AgentScope推荐模式：实现reply()方法
 * 
 * @author AIOperation Team
 * @since 2026-01-13
 */
public abstract class BaseIntelligentAgent {
    
    protected static final Logger logger = LoggerFactory.getLogger(BaseIntelligentAgent.class);
    
    /**
     * 智能体名称
     */
    protected final String agentName;

    /**
     * 执行记录（用于记录当前执行的详细信息）
     */
    protected final ThreadLocal<ExecutionTrace> executionTrace = ThreadLocal.withInitial(ExecutionTrace::new);
    
    /**
     * 知识检索服务（注入）
     */
    @Autowired(required = false)
    protected KnowledgeService knowledgeService;
    
    /**
     * LLM客户端注册表（注入）
     */
    @Autowired(required = false)
    protected LlmClientRegistry llmClientRegistry;
    
    /**
     * 构造函数
     * 
     * @param agentName 智能体名称
     */
    public BaseIntelligentAgent(String agentName) {
        this.agentName = agentName;
        logger.info("智能体 {} 初始化", agentName);
    }
    
    // ==================== 知识检索方法 ====================
    
    /**
     * 通用知识检索方法
     * 
     * 功能：从ES知识库检索相关知识
     * 
     * @param query 查询文本
     * @param knowledgeType 知识类型
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
            String knowledgeType, 
            int topK, 
            String fallbackMessage) {
        
        if (knowledgeService == null) {
            logger.warn("[{}] KnowledgeService未注入,返回默认消息", agentName);
            return Mono.just(fallbackMessage);
        }
        
        logger.debug("[{}] 开始知识检索: type={}, query={}", agentName, knowledgeType, query);
        
        return knowledgeService.searchDocuments(query, knowledgeType, topK, "keyword")
                .map(doc -> {
                    // 提取content字段
                    Object content = doc.get("content");
                    return content != null ? content.toString() : "";
                })
                .filter(content -> !content.isEmpty())
                .collectList()
                .map(contents -> {
                    if (contents.isEmpty()) {
                        logger.info("[{}] 未检索到相关知识,使用默认消息", agentName);
                        return fallbackMessage;
                    }
                    String result = String.join("\n\n---\n\n", contents);
                    logger.debug("[{}] 检索到{}条知识记录", agentName, contents.size());
                    return result;
                })
                .onErrorResume(e -> {
                    logger.error("[{}] 知识检索失败: {}", agentName, e.getMessage());
                    return Mono.just(fallbackMessage);
                });
    }
    
    // ==================== LLM调用方法 ====================
    
    /**
     * 获取LLM客户端
     */
    protected LlmClient getLlmClient() {
        if (llmClientRegistry == null) {
            throw new IllegalStateException("LlmClientRegistry未注入");
        }
        return llmClientRegistry.getDefaultClient();
    }
    
    /**
     * 调用LLM获取JSON格式输出
     * 
     * 功能：发送提示词到LLM，获取结构化JSON响应
     * 
     * @param prompt 用户提示词
     * @param systemPrompt 系统提示词（可选）
     * @param temperature 温度参数（0.0-1.0）
     * @param retryOnFailure 失败重试次数
     * @return Mono<AgentLlmResponse> LLM响应对象
     */
    protected Mono<AgentLlmResponse> callLlmJson(
            String prompt, 
            String systemPrompt, 
            double temperature, 
            int retryOnFailure) {
        
        logger.debug("[{}] 调用LLM JSON: temperature={}, retries={}", agentName, temperature, retryOnFailure);
        
        // 记录LLM输入
        executionTrace.get().recordLlmInput(prompt);
        
        if (llmClientRegistry == null) {
            logger.error("[{}] LlmClientRegistry未注入", agentName);
            return Mono.just(new AgentLlmResponse(false, null, "LLM客户端未配置"));
        }
        
        LlmClient client = getLlmClient();
        
        return client.generateJson(prompt, systemPrompt, temperature)
                .map(response -> {
                    if (response.isSuccess()) {
                        logger.debug("[{}] LLM调用成功", agentName);
                        // 记录LLM输出
                        String output = JsonUtil.toJson(response.getParsedJson());
                        executionTrace.get().recordLlmOutput(output);
                        return new AgentLlmResponse(true, response.getParsedJson(), null);
                    } else {
                        logger.warn("[{}] LLM返回错误: {}", agentName, response.getError());
                        executionTrace.get().recordLlmOutput("Error: " + response.getError());
                        return new AgentLlmResponse(false, null, response.getError());
                    }
                })
                .retry(retryOnFailure)
                .onErrorResume(e -> {
                    logger.error("[{}] LLM调用异常: {}", agentName, e.getMessage());
                    executionTrace.get().recordLlmOutput("Exception: " + e.getMessage());
                    return Mono.just(new AgentLlmResponse(false, null, e.getMessage()));
                });
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
        
        logger.debug("[{}] 调用LLM流式输出: temperature={}", agentName, temperature);
        
        if (llmClientRegistry == null) {
            logger.error("[{}] LlmClientRegistry未注入", agentName);
            return Flux.empty();
        }
        
        LlmClient client = getLlmClient();
        
        return client.streamText(prompt, systemPrompt, temperature)
                .filter(text -> text != null && !text.isEmpty())
                .doOnError(e -> logger.error("[{}] LLM流式调用异常: {}", agentName, e.getMessage()))
                .onErrorResume(e -> Flux.empty());
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
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", eventType);
        event.put("agent", agentName);
        event.put("timestamp", Instant.now().toString());
        
        if (data != null) {
            event.putAll(data);
        }
        
        logger.debug("[{}] 生成事件: type={}", agentName, eventType);
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
        
        String effectiveMessage = (message != null && !message.isEmpty()) 
                ? message 
                : "检索到" + knowledgeType;
        
        Map<String, Object> data = new HashMap<>();
        data.put("knowledge_type", knowledgeType);
        data.put("knowledge_content", content);
        data.put("message", effectiveMessage);
        
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
        if (errorType != null && !errorType.isEmpty()) {
            data.put("error_type", errorType);
        }
        
        logger.warn("[{}] 生成错误事件: type={}, msg={}", agentName, errorType, errorMsg);
        return yieldEvent("error", data);
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
        String errorMsg = String.format("[%s] %s失败: %s", agentName, operationName, error.getMessage());
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
        try {
            Map<String, Object> result = JsonUtil.extractAndParseJson(text);
            return Optional.ofNullable(result);
        } catch (Exception e) {
            logger.warn("[{}] JSON解析失败: {}", agentName, e.getMessage());
            return Optional.empty();
        }
    }
    
    // ==================== 抽象方法 ====================
    
    /**
     * 处理消息并返回响应（核心方法，子类必须实现）
     * 
     * @param input 输入消息内容（JSON格式的Map）
     * @return Mono<Map<String, Object>> 输出响应
     */
    public abstract Mono<Map<String, Object>> process(Map<String, Object> input);

    /**
     * 带追踪的process方法，自动记录输入输出
     * 
     * @param input 输入消息
     * @return Mono<Map<String, Object>> 输出响应
     */
    public Mono<Map<String, Object>> processWithTrace(Map<String, Object> input) {
        // 清空上一次执行的记录
        executionTrace.get().reset();
        // 记录输入
        executionTrace.get().recordInput(input);
        
        return process(input)
                .doOnSuccess(output -> {
                    // 记录输出
                    executionTrace.get().recordOutput(output);
                })
                .doOnError(error -> {
                    executionTrace.get().recordError(error.getMessage());
                });
    }

    /**
     * 获取当前执行追踪
     */
    public ExecutionTrace getExecutionTrace() {
        return executionTrace.get();
    }

    /**
     * 清理执行追踪
     */
    public void clearExecutionTrace() {
        executionTrace.remove();
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 构造错误响应
     * 
     * @param errorMsg 错误信息
     * @return Map<String, Object> 错误响应Map
     */
    protected Map<String, Object> errorResponse(String errorMsg) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("error", errorMsg);
        response.put("agent", agentName);
        response.put("timestamp", Instant.now().toString());
        return response;
    }
    
    /**
     * 构造成功响应
     * 
     * @param data 响应数据
     * @return Map<String, Object> 成功响应Map
     */
    protected Map<String, Object> successResponse(Map<String, Object> data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("agent", agentName);
        response.put("timestamp", Instant.now().toString());
        if (data != null) {
            response.putAll(data);
        }
        return response;
    }
    
    /**
     * 获取智能体名称
     */
    public String getAgentName() {
        return agentName;
    }
    
    // ==================== 内部类：LLM响应对象 ====================
    
    /**
     * Agent内部使用的LLM响应数据结构
     */
    protected static class AgentLlmResponse {
        private final boolean success;
        private final Map<String, Object> data;
        private final String error;
        
        public AgentLlmResponse(boolean success, Map<String, Object> data, String error) {
            this.success = success;
            this.data = data;
            this.error = error;
        }
        
        public boolean isSuccess() { return success; }
        public Map<String, Object> getData() { return data; }
        public String getError() { return error; }
    }

    /**
     * 执行追踪类，用于记录Agent执行的详细信息
     */
    public static class ExecutionTrace {
        private Map<String, Object> input;
        private Map<String, Object> output;
        private String llmInput;
        private String llmOutput;
        private Map<String, Object> httpRequest;
        private Map<String, Object> httpResponse;
        private String error;

        public void reset() {
            input = null;
            output = null;
            llmInput = null;
            llmOutput = null;
            httpRequest = null;
            httpResponse = null;
            error = null;
        }

        public void recordInput(Map<String, Object> input) {
            this.input = input != null ? new LinkedHashMap<>(input) : null;
        }

        public void recordOutput(Map<String, Object> output) {
            this.output = output != null ? new LinkedHashMap<>(output) : null;
        }

        public void recordLlmInput(String prompt) {
            this.llmInput = prompt;
        }

        public void recordLlmOutput(String response) {
            this.llmOutput = response;
        }

        public void recordHttpRequest(Map<String, Object> request) {
            this.httpRequest = request != null ? new LinkedHashMap<>(request) : null;
        }

        public void recordHttpResponse(Map<String, Object> response) {
            this.httpResponse = response != null ? new LinkedHashMap<>(response) : null;
        }

        public void recordError(String error) {
            this.error = error;
        }

        // Getters
        public Map<String, Object> getInput() { return input; }
        public Map<String, Object> getOutput() { return output; }
        public String getLlmInput() { return llmInput; }
        public String getLlmOutput() { return llmOutput; }
        public Map<String, Object> getHttpRequest() { return httpRequest; }
        public Map<String, Object> getHttpResponse() { return httpResponse; }
        public String getError() { return error; }
    }
}
