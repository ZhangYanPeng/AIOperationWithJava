package com.company.diagnosis.model.event;

import lombok.Data;
import lombok.Builder;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 诊断事件类
 * <p>
 * 功能描述:
 * - 定义诊断过程中的各种事件
 * - 用于SSE流式推送
 * - 支持前端实时展示
 * <p>
 * 设计考虑:
 * - 提供静态工厂方法创建各类事件
 * - 事件包含时间戳和唯一ID便于追踪
 * - 支持携带扩展数据
 *
 * @author Diagnosis System
 * @since 2026-01-13
 */
@Data
@Builder
public class DiagnosisEvent {

    /**
     * 事件ID
     */
    private String eventId;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 事件类型
     */
    private EventType type;

    /**
     * 事件数据
     */
    @Builder.Default
    private Map<String, Object> data = new HashMap<>();

    /**
     * 事件时间戳
     */
    private LocalDateTime timestamp;

    /**
     * 层级(如果是层级相关事件)
     */
    private Integer layer;

    /**
     * 智能体名称(如果是智能体相关事件)
     */
    private String agentName;

    /**
     * 事件消息
     */
    private String message;

    /**
     * 事件类型枚举
     */
    public enum EventType {
        /**
         * 会话创建
         */
        SESSION_CREATED,

        /**
         * 诊断触发
         */
        DIAGNOSIS_TRIGGERED,

        /**
         * 层级开始
         */
        LAYER_START,

        /**
         * 知识检索完成
         */
        KNOWLEDGE_RETRIEVED,

        /**
         * 规划完成
         */
        PLANNING_COMPLETE,

        /**
         * 步骤开始
         */
        STEP_START,

        /**
         * 工具调用
         */
        TOOL_INVOCATION,

        /**
         * 步骤完成
         */
        STEP_COMPLETE,

        /**
         * 层级完成
         */
        LAYER_COMPLETE,

        /**
         * 智能体思考过程
         */
        AGENT_THINKING,

        /**
         * LLM流式输出片段
         */
        LLM_STREAMING,

        /**
         * 诊断完成
         */
        DIAGNOSIS_COMPLETE,

        /**
         * 诊断失败
         */
        DIAGNOSIS_FAILED,

        /**
         * 错误事件
         */
        ERROR,

        /**
         * 心跳事件
         */
        HEARTBEAT
    }

    // ==================== 静态工厂方法 ====================

    /**
     * 创建会话创建事件
     *
     * @param sessionId 会话ID
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent sessionCreated(String sessionId) {
        return DiagnosisEvent.builder()
                .eventId(generateEventId())
                .sessionId(sessionId)
                .type(EventType.SESSION_CREATED)
                .timestamp(LocalDateTime.now())
                .message("会话已创建")
                .data(Map.of("sessionId", sessionId))
                .build();
    }

    /**
     * 创建诊断触发事件
     *
     * @param sessionId     会话ID
     * @param diagnosisType 诊断类型
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent diagnosisTriggered(String sessionId, String diagnosisType) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("diagnosisType", diagnosisType);
        eventData.put("startTime", LocalDateTime.now().toString());

        return DiagnosisEvent.builder()
                .eventId(generateEventId())
                .sessionId(sessionId)
                .type(EventType.DIAGNOSIS_TRIGGERED)
                .timestamp(LocalDateTime.now())
                .message("诊断已触发: " + diagnosisType)
                .data(eventData)
                .build();
    }

    /**
     * 创建层级开始事件
     *
     * @param sessionId 会话ID
     * @param layer     层级
     * @param agentName 智能体名称
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent layerStart(String sessionId, Integer layer, String agentName) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("layer", layer);
        eventData.put("agentName", agentName);

        return DiagnosisEvent.builder()
                .eventId(generateEventId())
                .sessionId(sessionId)
                .type(EventType.LAYER_START)
                .timestamp(LocalDateTime.now())
                .layer(layer)
                .agentName(agentName)
                .message("开始执行第" + layer + "层: " + agentName)
                .data(eventData)
                .build();
    }

    /**
     * 创建知识检索完成事件
     *
     * @param sessionId     会话ID
     * @param agentName     智能体名称
     * @param knowledgeType 知识类型
     * @param resultCount   检索结果数量
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent knowledgeRetrieved(String sessionId, String agentName,
                                                    String knowledgeType, int resultCount) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("knowledgeType", knowledgeType);
        eventData.put("resultCount", resultCount);

        return DiagnosisEvent.builder()
                .eventId(generateEventId())
                .sessionId(sessionId)
                .type(EventType.KNOWLEDGE_RETRIEVED)
                .timestamp(LocalDateTime.now())
                .agentName(agentName)
                .message("检索到" + resultCount + "条" + knowledgeType + "知识")
                .data(eventData)
                .build();
    }

    /**
     * 创建知识检索完成事件（带详细内容）
     *
     * @param sessionId       会话ID
     * @param agentName       智能体名称
     * @param knowledgeType   知识类型
     * @param knowledgeContent 知识内容
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent knowledgeRetrieved(String sessionId, String agentName,
                                                    String knowledgeType, String knowledgeContent) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("knowledgeType", knowledgeType);
        eventData.put("knowledgeContent", knowledgeContent);

        return DiagnosisEvent.builder()
                .eventId(generateEventId())
                .sessionId(sessionId)
                .type(EventType.KNOWLEDGE_RETRIEVED)
                .timestamp(LocalDateTime.now())
                .agentName(agentName)
                .message("检索到" + knowledgeType)
                .data(eventData)
                .build();
    }

    /**
     * 创建规划完成事件
     *
     * @param sessionId 会话ID
     * @param agentName 智能体名称
     * @param stepCount 步骤数量
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent planningComplete(String sessionId, String agentName, int stepCount) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("stepCount", stepCount);

        return DiagnosisEvent.builder()
                .eventId(generateEventId())
                .sessionId(sessionId)
                .type(EventType.PLANNING_COMPLETE)
                .timestamp(LocalDateTime.now())
                .agentName(agentName)
                .message("规划完成，共" + stepCount + "个步骤")
                .data(eventData)
                .build();
    }

    /**
     * 创建步骤开始事件
     *
     * @param sessionId  会话ID
     * @param stepNumber 步骤编号
     * @param stepName   步骤名称
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent stepStart(String sessionId, Integer stepNumber, String stepName) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("stepNumber", stepNumber);
        eventData.put("stepName", stepName);

        return DiagnosisEvent.builder()
                .eventId(generateEventId())
                .sessionId(sessionId)
                .type(EventType.STEP_START)
                .timestamp(LocalDateTime.now())
                .message("开始步骤" + stepNumber + ": " + stepName)
                .data(eventData)
                .build();
    }

    /**
     * 创建步骤完成事件
     *
     * @param sessionId  会话ID
     * @param stepNumber 步骤编号
     * @param stepName   步骤名称
     * @param success    是否成功
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent stepComplete(String sessionId, Integer stepNumber,
                                              String stepName, boolean success) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("stepNumber", stepNumber);
        eventData.put("stepName", stepName);
        eventData.put("success", success);

        return DiagnosisEvent.builder()
                .eventId(generateEventId())
                .sessionId(sessionId)
                .type(EventType.STEP_COMPLETE)
                .timestamp(LocalDateTime.now())
                .message("步骤" + stepNumber + (success ? "完成" : "失败") + ": " + stepName)
                .data(eventData)
                .build();
    }

    /**
     * 创建工具调用事件
     *
     * @param sessionId 会话ID
     * @param agentName 智能体名称
     * @param toolName  工具名称
     * @param params    调用参数
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent toolInvocation(String sessionId, String agentName,
                                                String toolName, Map<String, Object> params) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("toolName", toolName);
        eventData.put("params", params);

        return DiagnosisEvent.builder()
                .eventId(generateEventId())
                .sessionId(sessionId)
                .type(EventType.TOOL_INVOCATION)
                .timestamp(LocalDateTime.now())
                .agentName(agentName)
                .message("调用工具: " + toolName)
                .data(eventData)
                .build();
    }

    /**
     * 创建层级完成事件
     *
     * @param sessionId 会话ID
     * @param layer     层级
     * @param agentName 智能体名称
     * @param success   是否成功
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent layerComplete(String sessionId, Integer layer,
                                               String agentName, boolean success) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("layer", layer);
        eventData.put("success", success);

        return DiagnosisEvent.builder()
                .eventId(generateEventId())
                .sessionId(sessionId)
                .type(EventType.LAYER_COMPLETE)
                .timestamp(LocalDateTime.now())
                .layer(layer)
                .agentName(agentName)
                .message("第" + layer + "层执行" + (success ? "完成" : "失败"))
                .data(eventData)
                .build();
    }

    /**
     * 创建智能体思考事件
     *
     * @param sessionId     会话ID
     * @param agentName     智能体名称
     * @param thinkingStage 思考阶段
     * @param content       思考内容
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent agentThinking(String sessionId, String agentName,
                                               String thinkingStage, String content) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("stage", thinkingStage);
        eventData.put("content", content);

        return DiagnosisEvent.builder()
                .eventId(generateEventId())
                .sessionId(sessionId)
                .type(EventType.AGENT_THINKING)
                .timestamp(LocalDateTime.now())
                .agentName(agentName)
                .message(content)
                .data(eventData)
                .build();
    }

    /**
     * 创建LLM流式输出事件
     *
     * @param sessionId 会话ID
     * @param chunk     输出片段
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent llmStreaming(String sessionId, String chunk) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("chunk", chunk);

        return DiagnosisEvent.builder()
                .eventId(generateEventId())
                .sessionId(sessionId)
                .type(EventType.LLM_STREAMING)
                .timestamp(LocalDateTime.now())
                .message(chunk)
                .data(eventData)
                .build();
    }

    /**
     * 创建LLM流式输出事件（带智能体信息）
     *
     * @param sessionId 会话ID
     * @param agentName 智能体名称
     * @param chunk     输出片段
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent llmStreaming(String sessionId, String agentName, String chunk) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("chunk", chunk);

        return DiagnosisEvent.builder()
                .eventId(generateEventId())
                .sessionId(sessionId)
                .type(EventType.LLM_STREAMING)
                .timestamp(LocalDateTime.now())
                .agentName(agentName)
                .message(chunk)
                .data(eventData)
                .build();
    }

    /**
     * 创建诊断完成事件
     *
     * @param sessionId  会话ID
     * @param conclusion 最终结论
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent diagnosisComplete(String sessionId, String conclusion) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("conclusion", conclusion);
        eventData.put("completedAt", LocalDateTime.now().toString());

        return DiagnosisEvent.builder()
                .eventId(generateEventId())
                .sessionId(sessionId)
                .type(EventType.DIAGNOSIS_COMPLETE)
                .timestamp(LocalDateTime.now())
                .message("诊断完成")
                .data(eventData)
                .build();
    }

    /**
     * 创建诊断失败事件
     *
     * @param sessionId    会话ID
     * @param errorMessage 错误信息
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent diagnosisFailed(String sessionId, String errorMessage) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("error", errorMessage);
        eventData.put("failedAt", LocalDateTime.now().toString());

        return DiagnosisEvent.builder()
                .eventId(generateEventId())
                .sessionId(sessionId)
                .type(EventType.DIAGNOSIS_FAILED)
                .timestamp(LocalDateTime.now())
                .message("诊断失败: " + errorMessage)
                .data(eventData)
                .build();
    }

    /**
     * 创建错误事件
     *
     * @param sessionId    会话ID
     * @param errorMessage 错误消息
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent error(String sessionId, String errorMessage) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("error", errorMessage);

        return DiagnosisEvent.builder()
                .eventId(generateEventId())
                .sessionId(sessionId)
                .type(EventType.ERROR)
                .timestamp(LocalDateTime.now())
                .message(errorMessage)
                .data(eventData)
                .build();
    }

    /**
     * 创建错误事件（带错误类型）
     *
     * @param sessionId    会话ID
     * @param errorMessage 错误消息
     * @param errorType    错误类型
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent error(String sessionId, String errorMessage, String errorType) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("error", errorMessage);
        eventData.put("errorType", errorType);

        return DiagnosisEvent.builder()
                .eventId(generateEventId())
                .sessionId(sessionId)
                .type(EventType.ERROR)
                .timestamp(LocalDateTime.now())
                .message(errorMessage)
                .data(eventData)
                .build();
    }

    /**
     * 创建心跳事件
     *
     * @param sessionId 会话ID
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent heartbeat(String sessionId) {
        return DiagnosisEvent.builder()
                .eventId(generateEventId())
                .sessionId(sessionId)
                .type(EventType.HEARTBEAT)
                .timestamp(LocalDateTime.now())
                .message("heartbeat")
                .data(Map.of("timestamp", System.currentTimeMillis()))
                .build();
    }

    /**
     * 创建通用事件
     *
     * @param sessionId 会话ID
     * @param type      事件类型
     * @param message   消息
     * @param data      数据
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent of(String sessionId, EventType type,
                                    String message, Map<String, Object> data) {
        return DiagnosisEvent.builder()
                .eventId(generateEventId())
                .sessionId(sessionId)
                .type(type)
                .timestamp(LocalDateTime.now())
                .message(message)
                .data(data != null ? data : new HashMap<>())
                .build();
    }

    /**
     * 生成事件ID
     *
     * @return 事件ID
     */
    private static String generateEventId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 转换为SSE数据格式
     *
     * @return SSE格式的数据字符串
     */
    public String toSseData() {
        return com.company.diagnosis.util.JsonUtil.toJson(this);
    }

    /**
     * 转换为Map
     *
     * @return Map对象
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("eventId", eventId);
        map.put("sessionId", sessionId);
        map.put("type", type != null ? type.name() : null);
        map.put("data", data);
        map.put("timestamp", timestamp != null ? timestamp.toString() : null);
        map.put("layer", layer);
        map.put("agentName", agentName);
        map.put("message", message);
        return map;
    }

    /**
     * 创建连接成功事件
     *
     * @param sessionId 会话ID
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent connected(String sessionId) {
        return DiagnosisEvent.builder()
                .eventId(generateEventId())
                .sessionId(sessionId)
                .type(EventType.SESSION_CREATED)
                .timestamp(LocalDateTime.now())
                .message("连接成功")
                .data(Map.of("status", "connected"))
                .build();
    }

    /**
     * 创建诊断开始事件
     *
     * @param sessionId     会话ID
     * @param diagnosisType 诊断类型
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent started(String sessionId, String diagnosisType) {
        return diagnosisTriggered(sessionId, diagnosisType);
    }

    /**
     * 创建层级开始事件（兼容方法）
     *
     * @param sessionId 会话ID
     * @param agentName 智能体名称
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent layerStarted(String sessionId, String agentName) {
        return layerStart(sessionId, null, agentName);
    }

    /**
     * 创建层级完成事件（带结果）
     *
     * @param sessionId 会话ID
     * @param agentName 智能体名称
     * @param result    执行结果
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent layerCompleted(String sessionId, String agentName, Map<String, Object> result) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("result", result);

        return DiagnosisEvent.builder()
                .eventId(generateEventId())
                .sessionId(sessionId)
                .type(EventType.LAYER_COMPLETE)
                .timestamp(LocalDateTime.now())
                .agentName(agentName)
                .message("层级执行完成: " + agentName)
                .data(eventData)
                .build();
    }

    /**
     * 创建诊断完成事件（带结果）
     *
     * @param sessionId 会话ID
     * @param result    诊断结果
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent completed(String sessionId, Map<String, Object> result) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("result", result);
        eventData.put("completedAt", LocalDateTime.now().toString());

        return DiagnosisEvent.builder()
                .eventId(generateEventId())
                .sessionId(sessionId)
                .type(EventType.DIAGNOSIS_COMPLETE)
                .timestamp(LocalDateTime.now())
                .message("诊断完成")
                .data(eventData)
                .build();
    }
}
