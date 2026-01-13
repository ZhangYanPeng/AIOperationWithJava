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
    private Map<String, Object> parameterMemory = new ConcurrentHashMapWrapper<>();

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
    private Map<Integer, List<StepMemory>> stepsMemory = new ConcurrentHashMapWrapper<>();

    /**
     * 执行历史
     * 记录所有操作的时间线
     */
    @Builder.Default
    private List<ExecutionRecord> executionHistory = Collections.synchronizedList(new ArrayList<>());

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
    private Map<String, Object> metadata = new ConcurrentHashMapWrapper<>();

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
        private List<Map<String, Object>> layer1Results = Collections.synchronizedList(new ArrayList<>());

        /**
         * 第2层(基础执行层)结果列表
         */
        private List<Map<String, Object>> layer2Results = Collections.synchronizedList(new ArrayList<>());

        /**
         * 第3层(高级执行层)结果列表
         */
        private List<Map<String, Object>> layer3Results = Collections.synchronizedList(new ArrayList<>());

        /**
         * 第N层结果(动态层级)
         * Key为层级编号,Value为该层结果列表
         */
        private Map<Integer, List<Map<String, Object>>> dynamicLayerResults = new ConcurrentHashMapWrapper<>();

        /**
         * 添加层级结果
         * 
         * @param layer 层级编号
         * @param result 结果对象
         */
        public void addResult(Integer layer, Map<String, Object> result) {
            if (layer == null || result == null) {
                return;
            }
            switch (layer) {
                case 1 -> layer1Results.add(result);
                case 2 -> layer2Results.add(result);
                case 3 -> layer3Results.add(result);
                default -> {
                    dynamicLayerResults.computeIfAbsent(layer, 
                        k -> Collections.synchronizedList(new ArrayList<>())).add(result);
                }
            }
        }

        /**
         * 获取指定层级的结果列表
         * 
         * @param layer 层级编号
         * @return List 该层的结果列表
         */
        public List<Map<String, Object>> getResultsByLayer(Integer layer) {
            if (layer == null) {
                return Collections.emptyList();
            }
            return switch (layer) {
                case 1 -> layer1Results;
                case 2 -> layer2Results;
                case 3 -> layer3Results;
                default -> dynamicLayerResults.getOrDefault(layer, Collections.emptyList());
            };
        }
        
        /**
         * 获取最后一个结果
         *
         * @param layer 层级编号
         * @return 最后一个结果，如果没有则返回null
         */
        public Map<String, Object> getLastResult(Integer layer) {
            List<Map<String, Object>> results = getResultsByLayer(layer);
            if (results.isEmpty()) {
                return null;
            }
            return results.get(results.size() - 1);
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
        if (key != null && value != null) {
            parameterMemory.put(key, value);
            updatedAt = LocalDateTime.now();
        }
    }

    /**
     * 从参数记忆获取参数
     * 
     * @param key 参数键
     * @return Object 参数值
     */
    public Object getParameter(String key) {
        return key != null ? parameterMemory.get(key) : null;
    }
    
    /**
     * 从参数记忆获取参数并转换类型
     *
     * @param key 参数键
     * @param clazz 目标类型
     * @param <T> 类型参数
     * @return 参数值
     */
    @SuppressWarnings("unchecked")
    public <T> T getParameter(String key, Class<T> clazz) {
        Object value = getParameter(key);
        if (value == null) {
            return null;
        }
        if (clazz.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    /**
     * 添加步骤记忆
     * 
     * @param layer 层级
     * @param stepMemory 步骤记忆对象
     */
    public void addStepMemory(Integer layer, StepMemory stepMemory) {
        if (layer != null && stepMemory != null) {
            stepsMemory.computeIfAbsent(layer, k -> Collections.synchronizedList(new ArrayList<>()))
                       .add(stepMemory);
            updatedAt = LocalDateTime.now();
        }
    }

    /**
     * 获取指定层级的步骤记忆列表
     * 
     * @param layer 层级
     * @return List 步骤记忆列表
     */
    public List<StepMemory> getStepMemories(Integer layer) {
        return layer != null ? stepsMemory.getOrDefault(layer, Collections.emptyList()) : Collections.emptyList();
    }

    /**
     * 添加执行记录
     * 
     * @param record 执行记录对象
     */
    public void addExecutionRecord(ExecutionRecord record) {
        if (record != null) {
            if (record.getRecordId() == null) {
                record.setRecordId(UUID.randomUUID().toString());
            }
            if (record.getTimestamp() == null) {
                record.setTimestamp(LocalDateTime.now());
            }
            executionHistory.add(record);
            updatedAt = LocalDateTime.now();
        }
    }

    /**
     * 更新诊断状态
     * 
     * @param newStatus 新状态
     */
    public void updateStatus(DiagnosisStatus newStatus) {
        if (newStatus != null) {
            this.status = newStatus;
            this.updatedAt = LocalDateTime.now();
        }
    }
    
    /**
     * 添加层级结果
     *
     * @param layer 层级编号
     * @param result 结果
     */
    public void addLayerResult(Integer layer, Map<String, Object> result) {
        layerResults.addResult(layer, result);
        updatedAt = LocalDateTime.now();
    }
    
    /**
     * 创建新的诊断上下文
     *
     * @param sessionId 会话ID
     * @param requestId 请求ID
     * @param diagnosisType 诊断类型
     * @param problem 问题描述
     * @return 诊断上下文
     */
    public static DiagnosisContext create(String sessionId, String requestId, 
                                          String diagnosisType, String problem) {
        return DiagnosisContext.builder()
                .sessionId(sessionId)
                .requestId(requestId)
                .diagnosisType(diagnosisType)
                .originalProblem(problem)
                .currentLayer(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .status(DiagnosisStatus.CREATED)
                .build();
    }
    
    /**
     * 线程安全的HashMap包装类
     */
    private static class ConcurrentHashMapWrapper<K, V> extends java.util.concurrent.ConcurrentHashMap<K, V> {
        public ConcurrentHashMapWrapper() {
            super();
        }
    }
}
