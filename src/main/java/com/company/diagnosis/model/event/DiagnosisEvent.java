package com.company.diagnosis.model.event;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 诊断事件类
 * 
 * 功能描述:
 * - 定义诊断过程中的各种事件
 * - 用于SSE流式推送
 * - 支持前端实时展示
 * 
 * @author System
 * @since 2026-01-13
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
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
    private Map<String, Object> data;

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

    /**
     * 生成唯一事件ID
     */
    private static String generateEventId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

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
                .message("会话创建成功")
                .data(Map.of("sessionId", sessionId))
                .build();
    }

    /**
     * 创建诊断触发事件
     * 
     * @param sessionId 会话ID
     * @param diagnosisType 诊断类型
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent diagnosisTriggered(String sessionId, String diagnosisType) {
        Map<String, Object> data = new HashMap<>();
        data.put("diagnosisType", diagnosisType);
        
        return DiagnosisEvent.builder()
                .eventId(generateEventId())
                .sessionId(sessionId)
                .type(EventType.DIAGNOSIS_TRIGGERED)
                .timestamp(LocalDateTime.now())
                .message("诊断流程已触发: " + diagnosisType)
                .data(data)
                .build();
    }

    /**
     * 创建层级开始事件
     * 
     * @param sessionId 会话ID
     * @param layer 层级
     * @param agentName 智能体名称
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent layerStart(String sessionId, Integer layer, String agentName) {
        Map<String, Object> data = new HashMap<>();
        data.put("layer", layer);
        data.put("agentName", agentName);
        
        return DiagnosisEvent.builder()
                .eventId(generateEventId())
                .sessionId(sessionId)
                .type(EventType.LAYER_START)
                .timestamp(LocalDateTime.now())
                .layer(layer)
                .agentName(agentName)
                .message("开始执行第" + layer + "层: " + agentName)
                .data(data)
                .build();
    }

    /**
     * 创建层级完成事件
     * 
     * @param sessionId 会话ID
     * @param layer 层级
     * @param agentName 智能体名称
     * @param result 执行结果
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent layerComplete(String sessionId, Integer layer, String agentName, Object result) {
        Map<String, Object> data = new HashMap<>();
        data.put("layer", layer);
        data.put("agentName", agentName);
        data.put("result", result);
        
        return DiagnosisEvent.builder()
                .eventId(generateEventId())
                .sessionId(sessionId)
                .type(EventType.LAYER_COMPLETE)
                .timestamp(LocalDateTime.now())
                .layer(layer)
                .agentName(agentName)
                .message("第" + layer + "层执行完成: " + agentName)
                .data(data)
                .build();
    }

    /**
     * 创建步骤开始事件
     * 
     * @param sessionId 会话ID
     * @param stepNumber 步骤编号
     * @param stepName 步骤名称
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent stepStart(String sessionId, Integer stepNumber, String stepName) {
        Map<String, Object> data = new HashMap<>();
        data.put("stepNumber", stepNumber);
        data.put("stepName", stepName);
        
        return DiagnosisEvent.builder()
                .eventId(generateEventId())
                .sessionId(sessionId)
                .type(EventType.STEP_START)
                .timestamp(LocalDateTime.now())
                .message("开始执行步骤" + stepNumber + ": " + stepName)
                .data(data)
                .build();
    }

    /**
     * 创建步骤完成事件
     * 
     * @param sessionId 会话ID
     * @param stepNumber 步骤编号
     * @param stepName 步骤名称
     * @param result 执行结果
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent stepComplete(String sessionId, Integer stepNumber, String stepName, Object result) {
        Map<String, Object> data = new HashMap<>();
        data.put("stepNumber", stepNumber);
        data.put("stepName", stepName);
        data.put("result", result);
        
        return DiagnosisEvent.builder()
                .eventId(generateEventId())
                .sessionId(sessionId)
                .type(EventType.STEP_COMPLETE)
                .timestamp(LocalDateTime.now())
                .message("步骤" + stepNumber + "执行完成: " + stepName)
                .data(data)
                .build();
    }

    /**
     * 创建知识检索完成事件
     * 
     * @param sessionId 会话ID
     * @param knowledgeType 知识类型
     * @param resultCount 结果数量
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent knowledgeRetrieved(String sessionId, String knowledgeType, int resultCount) {
        Map<String, Object> data = new HashMap<>();
        data.put("knowledgeType", knowledgeType);
        data.put("resultCount", resultCount);
        
        return DiagnosisEvent.builder()
                .eventId(generateEventId())
                .sessionId(sessionId)
                .type(EventType.KNOWLEDGE_RETRIEVED)
                .timestamp(LocalDateTime.now())
                .message("知识检索完成: " + knowledgeType + ", 找到" + resultCount + "条结果")
                .data(data)
                .build();
    }

    /**
     * 创建工具调用事件
     * 
     * @param sessionId 会话ID
     * @param toolName 工具名称
     * @param parameters 调用参数
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent toolInvocation(String sessionId, String toolName, Map<String, Object> parameters) {
        Map<String, Object> data = new HashMap<>();
        data.put("toolName", toolName);
        data.put("parameters", parameters);
        
        return DiagnosisEvent.builder()
                .eventId(generateEventId())
                .sessionId(sessionId)
                .type(EventType.TOOL_INVOCATION)
                .timestamp(LocalDateTime.now())
                .message("调用工具: " + toolName)
                .data(data)
                .build();
    }

    /**
     * 创建智能体思考事件
     * 
     * @param sessionId 会话ID
     * @param agentName 智能体名称
     * @param thinking 思考内容
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent agentThinking(String sessionId, String agentName, String thinking) {
        Map<String, Object> data = new HashMap<>();
        data.put("agentName", agentName);
        data.put("thinking", thinking);
        
        return DiagnosisEvent.builder()
                .eventId(generateEventId())
                .sessionId(sessionId)
                .type(EventType.AGENT_THINKING)
                .timestamp(LocalDateTime.now())
                .agentName(agentName)
                .message(thinking)
                .data(data)
                .build();
    }

    /**
     * 创建LLM流式输出事件
     * 
     * @param sessionId 会话ID
     * @param chunk 输出片段
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent llmStreaming(String sessionId, String chunk) {
        Map<String, Object> data = new HashMap<>();
        data.put("chunk", chunk);
        
        return DiagnosisEvent.builder()
                .eventId(generateEventId())
                .sessionId(sessionId)
                .type(EventType.LLM_STREAMING)
                .timestamp(LocalDateTime.now())
                .message(chunk)
                .data(data)
                .build();
    }

    /**
     * 创建规划完成事件
     * 
     * @param sessionId 会话ID
     * @param steps 规划的步骤列表
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent planningComplete(String sessionId, java.util.List<String> steps) {
        Map<String, Object> data = new HashMap<>();
        data.put("steps", steps);
        data.put("stepCount", steps.size());
        
        return DiagnosisEvent.builder()
                .eventId(generateEventId())
                .sessionId(sessionId)
                .type(EventType.PLANNING_COMPLETE)
                .timestamp(LocalDateTime.now())
                .message("规划完成，共" + steps.size() + "个步骤")
                .data(data)
                .build();
    }

    /**
     * 创建诊断完成事件
     * 
     * @param sessionId 会话ID
     * @param conclusion 最终结论
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent diagnosisComplete(String sessionId, String conclusion) {
        Map<String, Object> data = new HashMap<>();
        data.put("conclusion", conclusion);
        
        return DiagnosisEvent.builder()
                .eventId(generateEventId())
                .sessionId(sessionId)
                .type(EventType.DIAGNOSIS_COMPLETE)
                .timestamp(LocalDateTime.now())
                .message("诊断完成")
                .data(data)
                .build();
    }

    /**
     * 创建诊断失败事件
     * 
     * @param sessionId 会话ID
     * @param errorMessage 错误消息
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent diagnosisFailed(String sessionId, String errorMessage) {
        Map<String, Object> data = new HashMap<>();
        data.put("error", errorMessage);
        
        return DiagnosisEvent.builder()
                .eventId(generateEventId())
                .sessionId(sessionId)
                .type(EventType.DIAGNOSIS_FAILED)
                .timestamp(LocalDateTime.now())
                .message("诊断失败: " + errorMessage)
                .data(data)
                .build();
    }

    /**
     * 创建错误事件
     * 
     * @param sessionId 会话ID
     * @param errorMessage 错误消息
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent error(String sessionId, String errorMessage) {
        Map<String, Object> data = new HashMap<>();
        data.put("error", errorMessage);
        
        return DiagnosisEvent.builder()
                .eventId(generateEventId())
                .sessionId(sessionId)
                .type(EventType.ERROR)
                .timestamp(LocalDateTime.now())
                .message(errorMessage)
                .data(data)
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
                .data(Map.of("timestamp", LocalDateTime.now().toString()))
                .build();
    }

    /**
     * 转换为Map格式（用于SSE推送）
     * 
     * @return Map格式的事件数据
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("eventId", eventId);
        map.put("sessionId", sessionId);
        map.put("type", type != null ? type.name() : null);
        map.put("message", message);
        map.put("timestamp", timestamp != null ? timestamp.toString() : null);
        
        if (layer != null) {
            map.put("layer", layer);
        }
        if (agentName != null) {
            map.put("agentName", agentName);
        }
        if (data != null) {
            map.put("data", data);
        }
        
        return map;
    }
}
