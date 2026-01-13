package com.company.diagnosis.model.event;

import lombok.Data;
import lombok.Builder;
import java.time.LocalDateTime;
import java.util.Map;

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
     * 创建会话创建事件
     * 
     * @param sessionId 会话ID
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent sessionCreated(String sessionId) {
        // TODO: 待实现
        return null;
    }

    /**
     * 创建诊断触发事件
     * 
     * @param sessionId 会话ID
     * @param diagnosisType 诊断类型
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent diagnosisTriggered(String sessionId, String diagnosisType) {
        // TODO: 待实现
        return null;
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
        // TODO: 待实现
        return null;
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
        // TODO: 待实现
        return null;
    }

    /**
     * 创建LLM流式输出事件
     * 
     * @param sessionId 会话ID
     * @param chunk 输出片段
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent llmStreaming(String sessionId, String chunk) {
        // TODO: 待实现
        return null;
    }

    /**
     * 创建诊断完成事件
     * 
     * @param sessionId 会话ID
     * @param conclusion 最终结论
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent diagnosisComplete(String sessionId, String conclusion) {
        // TODO: 待实现
        return null;
    }

    /**
     * 创建错误事件
     * 
     * @param sessionId 会话ID
     * @param errorMessage 错误消息
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent error(String sessionId, String errorMessage) {
        // TODO: 待实现
        return null;
    }
}
package com.company.diagnosis.model.event;

import lombok.Data;
import lombok.Builder;
import java.time.LocalDateTime;
import java.util.Map;

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
     * 创建会话创建事件
     * 
     * @param sessionId 会话ID
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent sessionCreated(String sessionId) {
        // TODO: 待实现
        return null;
    }

    /**
     * 创建诊断触发事件
     * 
     * @param sessionId 会话ID
     * @param diagnosisType 诊断类型
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent diagnosisTriggered(String sessionId, String diagnosisType) {
        // TODO: 待实现
        return null;
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
        // TODO: 待实现
        return null;
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
        // TODO: 待实现
        return null;
    }

    /**
     * 创建LLM流式输出事件
     * 
     * @param sessionId 会话ID
     * @param chunk 输出片段
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent llmStreaming(String sessionId, String chunk) {
        // TODO: 待实现
        return null;
    }

    /**
     * 创建诊断完成事件
     * 
     * @param sessionId 会话ID
     * @param conclusion 最终结论
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent diagnosisComplete(String sessionId, String conclusion) {
        // TODO: 待实现
        return null;
    }

    /**
     * 创建错误事件
     * 
     * @param sessionId 会话ID
     * @param errorMessage 错误消息
     * @return DiagnosisEvent 事件对象
     */
    public static DiagnosisEvent error(String sessionId, String errorMessage) {
        // TODO: 待实现
        return null;
    }
}
