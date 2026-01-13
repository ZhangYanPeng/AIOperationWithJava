package com.company.diagnosis.agent.base;

import com.agentscope.agent.AgentBase;
import com.agentscope.message.Msg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
 * - 符合AgentScope推荐模式：继承AgentBase，实现reply()方法
 * 
 * @author AIOperation Team
 * @since 2026-01-13
 */
public abstract class BaseIntelligentAgent extends AgentBase {
    
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
     * LLM客户端（注入）
     */
    @Autowired
    protected LlmClient llmClient;
    
    /**
     * 构造函数
     * 
     * @param agentName 智能体名称
     */
    public BaseIntelligentAgent(String agentName) {
        super();
        this.agentName = agentName;
        logger.info("智能体 {} 初始化成功", agentName);
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
     * 
     * 实现方式：
     * 1. 调用knowledgeService.retrieve()查询ES
     * 2. 解析返回结果，提取content字段
     * 3. 如果未找到，返回fallbackMessage
     * 4. 异常时记录日志并返回fallbackMessage
     */
    protected Mono<String> retrieveKnowledge(
            String query, 
            String agentType, 
            int topK, 
            String fallbackMessage) {
        // TODO: 实现知识检索逻辑
        return Mono.just(fallbackMessage);
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
     *         - success: Boolean（是否成功）
     *         - data: Map（解析后的JSON数据）
     *         - error: String（错误信息）
     * 
     * 实现方式：
     * 1. 构造请求对象（包含prompt、systemPrompt、temperature）
     * 2. 循环重试：调用llmClient.generateJson()
     * 3. 解析响应，验证JSON格式
     * 4. 失败时记录日志，达到重试上限后返回错误
     */
    protected Mono<LlmResponse> callLlmJson(
            String prompt, 
            String systemPrompt, 
            double temperature, 
            int retryOnFailure) {
        // TODO: 实现LLM JSON调用逻辑
        return Mono.just(new LlmResponse(false, null, "Not implemented"));
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
     * 
     * 实现方式：
     * 1. 构造请求对象
     * 2. 调用llmClient.streamText()获取Flux流
     * 3. 过滤空片段
     * 4. 异常时记录日志，返回空流
     */
    protected Flux<String> callLlmStream(
            String prompt, 
            String systemPrompt, 
            double temperature) {
        // TODO: 实现LLM流式调用逻辑
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
     * 
     * 实现方式：
     * 1. 创建Map对象
     * 2. 添加type、agent、timestamp字段
     * 3. 合并传入的data
     * 4. 返回完整事件对象
     */
    protected Map<String, Object> yieldEvent(String eventType, Map<String, Object> data) {
        // TODO: 实现事件推送逻辑
        return Map.of("type", eventType, "agent", agentName);
    }
    
    /**
     * 推送思考过程事件
     * 
     * 功能：推送agent_thinking事件，标记智能体推理过程
     * 
     * @param message 思考内容
     * @param stage 阶段标识（如"llm_streaming"、"knowledge_retrieval"）
     * @return Map<String, Object> 思考事件字典
     * 
     * 实现方式：
     * 1. 调用yieldEvent()
     * 2. 设置type="agent_thinking"
     * 3. 添加stage和message字段
     */
    protected Map<String, Object> yieldThinkingEvent(String message, String stage) {
        // TODO: 实现思考事件推送逻辑
        return yieldEvent("agent_thinking", Map.of("message", message, "stage", stage));
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
     * 
     * 实现方式：
     * 1. 如果message为空，生成默认描述："检索到{knowledgeType}"
     * 2. 调用yieldEvent()
     * 3. 设置type="knowledge_retrieved"
     * 4. 添加knowledge_type、knowledge_content、message字段
     */
    protected Map<String, Object> yieldKnowledgeEvent(
            String knowledgeType, 
            String content, 
            String message) {
        // TODO: 实现知识事件推送逻辑
        return yieldEvent("knowledge_retrieved", Map.of("knowledge_type", knowledgeType));
    }
    
    /**
     * 推送错误事件
     * 
     * 功能：推送error事件，通知前端错误信息
     * 
     * @param errorMsg 错误信息
     * @param errorType 错误类型（可选，如"LlmCallFailed"、"KnowledgeRetrievalFailed"）
     * @return Map<String, Object> 错误事件字典
     * 
     * 实现方式：
     * 1. 调用yieldEvent()
     * 2. 设置type="error"
     * 3. 添加error和error_type字段
     */
    protected Map<String, Object> yieldErrorEvent(String errorMsg, String errorType) {
        // TODO: 实现错误事件推送逻辑
        return yieldEvent("error", Map.of("error", errorMsg));
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
     * 
     * 实现方式：
     * 1. 构造错误信息："{operationName}失败: {error.getMessage()}"
     * 2. 使用logger.error()记录完整堆栈
     * 3. 返回错误信息字符串
     */
    protected String handleException(Exception error, String operationName) {
        // TODO: 实现异常处理逻辑
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
     * 
     * 实现方式：
     * 1. 使用Jackson ObjectMapper解析JSON
     * 2. 成功则返回Optional.of(map)
     * 3. 失败时记录警告日志，返回Optional.empty()
     */
    protected Optional<Map<String, Object>> safeJsonParse(String text) {
        // TODO: 实现JSON安全解析逻辑
        return Optional.empty();
    }
    
    // ==================== AgentScope标准接口 ====================
    
    /**
     * AgentScope标准reply方法（必须实现）
     * 
     * 功能：处理消息，返回响应
     * 
     * @param msg 输入消息（Msg对象）
     * @return Mono<Msg> 输出消息
     * 
     * 实现方式：
     * 子类必须重写此方法，实现具体业务逻辑
     */
    @Override
    public abstract Mono<Msg> reply(Msg msg);
    
    // ==================== 辅助方法 ====================
    
    /**
     * 构造错误响应消息
     * 
     * 功能：创建包含错误信息的Msg对象
     * 
     * @param errorMsg 错误信息
     * @return Msg 错误消息对象
     * 
     * 实现方式：
     * 1. 构造JSON字符串：{"success": false, "error": errorMsg}
     * 2. 创建Msg对象：name=agentName, role="assistant", content=json
     * 3. 返回Msg对象
     */
    protected Msg errorResponse(String errorMsg) {
        // TODO: 实现错误响应构造逻辑
        return Msg.builder()
                .name(agentName)
                .role("assistant")
                .content("{\"success\": false, \"error\": \"" + errorMsg + "\"}")
                .build();
    }
    
    /**
     * 构造成功响应消息
     * 
     * 功能：创建包含成功数据的Msg对象
     * 
     * @param data 响应数据（Map）
     * @return Msg 成功消息对象
     * 
     * 实现方式：
     * 1. 构造JSON字符串：{"success": true, "data": data}
     * 2. 创建Msg对象：name=agentName, role="assistant", content=json
     * 3. 返回Msg对象
     */
    protected Msg successResponse(Map<String, Object> data) {
        // TODO: 实现成功响应构造逻辑
        return Msg.builder()
                .name(agentName)
                .role("assistant")
                .content("{\"success\": true}")
                .build();
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
