package com.company.diagnosis.model.context;

import lombok.Data;
import lombok.Builder;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 诊断上下文类
 * 
 * 功能描述:
 * - 维护整个诊断流程的上下文信息
 * - 存储各层执行结果和参数记忆
 * - 支持层级隔离和全局共享数据
 * 
 * 设计考虑:
 * - 线程安全(如需并发访问)
 * - 支持层级结果隔离
 * - 支持全局参数记忆共享
 * - 记录完整执行历史
 * 
 * @author System
 * @since 2026-01-13
 */
@Data
@Builder
public class DiagnosisContext {

    /**
     * 会话ID,全局唯一标识
     */
    private String sessionId;

    /**
     * 请求ID,用于追踪单次诊断请求
     */
    private String requestId;

    /**
     * 原始问题描述
     */
    private String originalProblem;

    /**
     * 诊断类型(如"设备故障诊断"、"通道中断诊断")
     */
    private String diagnosisType;

    /**
     * 当前执行层级
     */
    private Integer currentLayer;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 诊断状态
     */
    private DiagnosisStatus status;

    /**
     * 参数记忆(全局共享)
     * 所有层都可以读写,避免重复提取相同参数
     * 
     * 示例:
     * - deviceId: "设备001"
     * - channelNum: 1
     * - ipAddress: "192.168.1.100"
     */
    @Builder.Default
    private Map<String, Object> parameterMemory = new HashMap<>();

    /**
     * 各层结果堆栈
     * 按层级分别存储每层的执行结果
     */
    @Builder.Default
    private LayerResults layerResults = new LayerResults();

    /**
     * 步骤记忆
     * 记录每层执行的步骤列表
     */
    @Builder.Default
    private Map<Integer, List<StepMemory>> stepsMemory = new HashMap<>();

    /**
     * 执行历史
     * 记录所有操作的时间线
     */
    @Builder.Default
    private List<ExecutionRecord> executionHistory = new ArrayList<>();

    /**
     * 最终结论
     */
    private String finalConclusion;

    /**
     * 错误信息(如果诊断失败)
     */
    private String errorMessage;

    /**
     * 元数据
     * 存储额外的上下文信息
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * 诊断状态枚举
     */
    public enum DiagnosisStatus {
        /**
         * 已创建
         */
        CREATED,
        /**
         * 执行中
         */
        RUNNING,
        /**
         * 已完成
         */
        COMPLETED,
        /**
         * 失败
         */
        FAILED,
        /**
         * 已取消
         */
        CANCELLED,
        /**
         * 超时
         */
        TIMEOUT
    }

    /**
     * 层级结果类
     * 分层存储各层的执行结果
     */
    @Data
    public static class LayerResults {
        /**
         * 第1层(接口调用层)结果列表
         */
        @Builder.Default
        private List<Map<String, Object>> layer1Results = new ArrayList<>();

        /**
         * 第2层(基础执行层)结果列表
         */
        @Builder.Default
        private List<Map<String, Object>> layer2Results = new ArrayList<>();

        /**
         * 第3层(高级执行层)结果列表
         */
        @Builder.Default
        private List<Map<String, Object>> layer3Results = new ArrayList<>();

        /**
         * 第N层结果(动态层级)
         * Key为层级编号,Value为该层结果列表
         */
        @Builder.Default
        private Map<Integer, List<Map<String, Object>>> dynamicLayerResults = new HashMap<>();

        /**
         * 添加层级结果
         * 
         * @param layer 层级编号
         * @param result 结果对象
         */
        public void addResult(Integer layer, Map<String, Object> result) {
            // TODO: 待实现
            // 根据layer将result添加到对应的结果列表
        }

        /**
         * 获取指定层级的结果列表
         * 
         * @param layer 层级编号
         * @return List 该层的结果列表
         */
        public List<Map<String, Object>> getResultsByLayer(Integer layer) {
            // TODO: 待实现
            return null;
        }
    }

    /**
     * 步骤记忆类
     * 记录单个执行步骤的信息
     */
    @Data
    @Builder
    public static class StepMemory {
        /**
         * 步骤编号
         */
        private Integer stepNumber;

        /**
         * 步骤名称
         */
        private String stepName;

        /**
         * 步骤描述
         */
        private String description;

        /**
         * 开始时间
         */
        private LocalDateTime startTime;

        /**
         * 结束时间
         */
        private LocalDateTime endTime;

        /**
         * 步骤状态
         */
        private StepStatus status;

        /**
         * 步骤输入
         */
        private Map<String, Object> input;

        /**
         * 步骤输出
         */
        private Map<String, Object> output;

        /**
         * 错误信息(如果步骤失败)
         */
        private String error;
    }

    /**
     * 步骤状态枚举
     */
    public enum StepStatus {
        PENDING, RUNNING, COMPLETED, FAILED, SKIPPED
    }

    /**
     * 执行记录类
     * 记录操作历史
     */
    @Data
    @Builder
    public static class ExecutionRecord {
        /**
         * 记录ID
         */
        private String recordId;

        /**
         * 时间戳
         */
        private LocalDateTime timestamp;

        /**
         * 操作类型
         */
        private String operationType;

        /**
         * 层级
         */
        private Integer layer;

        /**
         * 智能体名称
         */
        private String agentName;

        /**
         * 操作描述
         */
        private String description;

        /**
         * 操作结果
         */
        private Map<String, Object> result;
    }

    /**
     * 添加参数到参数记忆
     * 
     * @param key 参数键
     * @param value 参数值
     */
    public void addParameter(String key, Object value) {
        // TODO: 待实现
    }

    /**
     * 从参数记忆获取参数
     * 
     * @param key 参数键
     * @return Object 参数值
     */
    public Object getParameter(String key) {
        // TODO: 待实现
        return null;
    }

    /**
     * 添加步骤记忆
     * 
     * @param layer 层级
     * @param stepMemory 步骤记忆对象
     */
    public void addStepMemory(Integer layer, StepMemory stepMemory) {
        // TODO: 待实现
    }

    /**
     * 获取指定层级的步骤记忆列表
     * 
     * @param layer 层级
     * @return List 步骤记忆列表
     */
    public List<StepMemory> getStepMemories(Integer layer) {
        // TODO: 待实现
        return null;
    }

    /**
     * 添加执行记录
     * 
     * @param record 执行记录对象
     */
    public void addExecutionRecord(ExecutionRecord record) {
        // TODO: 待实现
    }

    /**
     * 更新诊断状态
     * 
     * @param newStatus 新状态
     */
    public void updateStatus(DiagnosisStatus newStatus) {
        // TODO: 待实现
        // 更新status和updatedAt
    }
}
package com.company.diagnosis.model.context;

import lombok.Data;
import lombok.Builder;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 诊断上下文类
 * 
 * 功能描述:
 * - 维护整个诊断流程的上下文信息
 * - 存储各层执行结果和参数记忆
 * - 支持层级隔离和全局共享数据
 * 
 * 设计考虑:
 * - 线程安全(如需并发访问)
 * - 支持层级结果隔离
 * - 支持全局参数记忆共享
 * - 记录完整执行历史
 * 
 * @author System
 * @since 2026-01-13
 */
@Data
@Builder
public class DiagnosisContext {

    /**
     * 会话ID,全局唯一标识
     */
    private String sessionId;

    /**
     * 请求ID,用于追踪单次诊断请求
     */
    private String requestId;

    /**
     * 原始问题描述
     */
    private String originalProblem;

    /**
     * 诊断类型(如"设备故障诊断"、"通道中断诊断")
     */
    private String diagnosisType;

    /**
     * 当前执行层级
     */
    private Integer currentLayer;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 诊断状态
     */
    private DiagnosisStatus status;

    /**
     * 参数记忆(全局共享)
     * 所有层都可以读写,避免重复提取相同参数
     * 
     * 示例:
     * - deviceId: "设备001"
     * - channelNum: 1
     * - ipAddress: "192.168.1.100"
     */
    @Builder.Default
    private Map<String, Object> parameterMemory = new HashMap<>();

    /**
     * 各层结果堆栈
     * 按层级分别存储每层的执行结果
     */
    @Builder.Default
    private LayerResults layerResults = new LayerResults();

    /**
     * 步骤记忆
     * 记录每层执行的步骤列表
     */
    @Builder.Default
    private Map<Integer, List<StepMemory>> stepsMemory = new HashMap<>();

    /**
     * 执行历史
     * 记录所有操作的时间线
     */
    @Builder.Default
    private List<ExecutionRecord> executionHistory = new ArrayList<>();

    /**
     * 最终结论
     */
    private String finalConclusion;

    /**
     * 错误信息(如果诊断失败)
     */
    private String errorMessage;

    /**
     * 元数据
     * 存储额外的上下文信息
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * 诊断状态枚举
     */
    public enum DiagnosisStatus {
        /**
         * 已创建
         */
        CREATED,
        /**
         * 执行中
         */
        RUNNING,
        /**
         * 已完成
         */
        COMPLETED,
        /**
         * 失败
         */
        FAILED,
        /**
         * 已取消
         */
        CANCELLED,
        /**
         * 超时
         */
        TIMEOUT
    }

    /**
     * 层级结果类
     * 分层存储各层的执行结果
     */
    @Data
    public static class LayerResults {
        /**
         * 第1层(接口调用层)结果列表
         */
        @Builder.Default
        private List<Map<String, Object>> layer1Results = new ArrayList<>();

        /**
         * 第2层(基础执行层)结果列表
         */
        @Builder.Default
        private List<Map<String, Object>> layer2Results = new ArrayList<>();

        /**
         * 第3层(高级执行层)结果列表
         */
        @Builder.Default
        private List<Map<String, Object>> layer3Results = new ArrayList<>();

        /**
         * 第N层结果(动态层级)
         * Key为层级编号,Value为该层结果列表
         */
        @Builder.Default
        private Map<Integer, List<Map<String, Object>>> dynamicLayerResults = new HashMap<>();

        /**
         * 添加层级结果
         * 
         * @param layer 层级编号
         * @param result 结果对象
         */
        public void addResult(Integer layer, Map<String, Object> result) {
            // TODO: 待实现
            // 根据layer将result添加到对应的结果列表
        }

        /**
         * 获取指定层级的结果列表
         * 
         * @param layer 层级编号
         * @return List 该层的结果列表
         */
        public List<Map<String, Object>> getResultsByLayer(Integer layer) {
            // TODO: 待实现
            return null;
        }
    }

    /**
     * 步骤记忆类
     * 记录单个执行步骤的信息
     */
    @Data
    @Builder
    public static class StepMemory {
        /**
         * 步骤编号
         */
        private Integer stepNumber;

        /**
         * 步骤名称
         */
        private String stepName;

        /**
         * 步骤描述
         */
        private String description;

        /**
         * 开始时间
         */
        private LocalDateTime startTime;

        /**
         * 结束时间
         */
        private LocalDateTime endTime;

        /**
         * 步骤状态
         */
        private StepStatus status;

        /**
         * 步骤输入
         */
        private Map<String, Object> input;

        /**
         * 步骤输出
         */
        private Map<String, Object> output;

        /**
         * 错误信息(如果步骤失败)
         */
        private String error;
    }

    /**
     * 步骤状态枚举
     */
    public enum StepStatus {
        PENDING, RUNNING, COMPLETED, FAILED, SKIPPED
    }

    /**
     * 执行记录类
     * 记录操作历史
     */
    @Data
    @Builder
    public static class ExecutionRecord {
        /**
         * 记录ID
         */
        private String recordId;

        /**
         * 时间戳
         */
        private LocalDateTime timestamp;

        /**
         * 操作类型
         */
        private String operationType;

        /**
         * 层级
         */
        private Integer layer;

        /**
         * 智能体名称
         */
        private String agentName;

        /**
         * 操作描述
         */
        private String description;

        /**
         * 操作结果
         */
        private Map<String, Object> result;
    }

    /**
     * 添加参数到参数记忆
     * 
     * @param key 参数键
     * @param value 参数值
     */
    public void addParameter(String key, Object value) {
        // TODO: 待实现
    }

    /**
     * 从参数记忆获取参数
     * 
     * @param key 参数键
     * @return Object 参数值
     */
    public Object getParameter(String key) {
        // TODO: 待实现
        return null;
    }

    /**
     * 添加步骤记忆
     * 
     * @param layer 层级
     * @param stepMemory 步骤记忆对象
     */
    public void addStepMemory(Integer layer, StepMemory stepMemory) {
        // TODO: 待实现
    }

    /**
     * 获取指定层级的步骤记忆列表
     * 
     * @param layer 层级
     * @return List 步骤记忆列表
     */
    public List<StepMemory> getStepMemories(Integer layer) {
        // TODO: 待实现
        return null;
    }

    /**
     * 添加执行记录
     * 
     * @param record 执行记录对象
     */
    public void addExecutionRecord(ExecutionRecord record) {
        // TODO: 待实现
    }

    /**
     * 更新诊断状态
     * 
     * @param newStatus 新状态
     */
    public void updateStatus(DiagnosisStatus newStatus) {
        // TODO: 待实现
        // 更新status和updatedAt
    }
}
