package com.company.diagnosis.model.context;

import lombok.Data;
import lombok.Builder;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 诊断上下文类
 * <p>
 * 功能描述:
 * - 维护整个诊断流程的上下文信息
 * - 存储各层执行结果和参数记忆
 * - 支持层级隔离和全局共享数据
 * <p>
 * 设计考虑:
 * - 线程安全(如需并发访问)
 * - 支持层级结果隔离
 * - 支持全局参数记忆共享
 * - 记录完整执行历史
 *
 * @author Diagnosis System
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
     * 告警ID,关联告警信息
     */
    private String alertId;

    /**
     * 诊断开始时间(Instant类型,用于精确时间计算)
     */
    private Instant startTime;

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
     * <p>
     * 示例:
     * - deviceId: "设备001"
     * - channelNum: 1
     * - ipAddress: "192.168.1.100"
     */
    @Builder.Default
    private Map<String, Object> parameterMemory = new ConcurrentHashMap<>();

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
    private Map<Integer, List<StepMemory>> stepsMemory = new ConcurrentHashMap<>();

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
    private Map<String, Object> metadata = new ConcurrentHashMap<>();

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
        private Map<Integer, List<Map<String, Object>>> dynamicLayerResults = new ConcurrentHashMap<>();

        /**
         * 添加层级结果
         *
         * @param layer  层级编号
         * @param result 结果对象
         */
        public void addResult(Integer layer, Map<String, Object> result) {
            if (layer == null || result == null) {
                return;
            }
            switch (layer) {
                case 1:
                    layer1Results.add(result);
                    break;
                case 2:
                    layer2Results.add(result);
                    break;
                case 3:
                    layer3Results.add(result);
                    break;
                default:
                    // 动态层级处理
                    dynamicLayerResults
                            .computeIfAbsent(layer, k -> Collections.synchronizedList(new ArrayList<>()))
                            .add(result);
                    break;
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
            switch (layer) {
                case 1:
                    return new ArrayList<>(layer1Results);
                case 2:
                    return new ArrayList<>(layer2Results);
                case 3:
                    return new ArrayList<>(layer3Results);
                default:
                    List<Map<String, Object>> dynamicResults = dynamicLayerResults.get(layer);
                    return dynamicResults != null ? new ArrayList<>(dynamicResults) : Collections.emptyList();
            }
        }

        /**
         * 获取所有层级的最新结果
         *
         * @return Map，key为层级，value为最新结果
         */
        public Map<Integer, Map<String, Object>> getLatestResultsPerLayer() {
            Map<Integer, Map<String, Object>> latestResults = new HashMap<>();

            if (!layer1Results.isEmpty()) {
                latestResults.put(1, layer1Results.get(layer1Results.size() - 1));
            }
            if (!layer2Results.isEmpty()) {
                latestResults.put(2, layer2Results.get(layer2Results.size() - 1));
            }
            if (!layer3Results.isEmpty()) {
                latestResults.put(3, layer3Results.get(layer3Results.size() - 1));
            }

            for (Map.Entry<Integer, List<Map<String, Object>>> entry : dynamicLayerResults.entrySet()) {
                List<Map<String, Object>> results = entry.getValue();
                if (!results.isEmpty()) {
                    latestResults.put(entry.getKey(), results.get(results.size() - 1));
                }
            }

            return latestResults;
        }

        /**
         * 清除所有层级结果
         */
        public void clear() {
            layer1Results.clear();
            layer2Results.clear();
            layer3Results.clear();
            dynamicLayerResults.clear();
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

        /**
         * 智能体名称
         */
        private String agentName;

        /**
         * 层级
         */
        private Integer layer;

        /**
         * LLM调用输入（Prompt）
         */
        private String llmInput;

        /**
         * LLM调用输出
         */
        private String llmOutput;

        /**
         * HTTP调用请求
         */
        private Map<String, Object> httpRequest;

        /**
         * HTTP调用响应
         */
        private Map<String, Object> httpResponse;

        /**
         * 计算步骤执行耗时（毫秒）
         *
         * @return 耗时毫秒数，如果步骤未完成则返回-1
         */
        public long getDurationMs() {
            if (startTime == null || endTime == null) {
                return -1;
            }
            return java.time.Duration.between(startTime, endTime).toMillis();
        }
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

        /**
         * 创建执行记录的便捷方法
         *
         * @param operationType 操作类型
         * @param layer         层级
         * @param agentName     智能体名称
         * @param description   描述
         * @return ExecutionRecord实例
         */
        public static ExecutionRecord create(String operationType, Integer layer,
                                             String agentName, String description) {
            return ExecutionRecord.builder()
                    .recordId(UUID.randomUUID().toString())
                    .timestamp(LocalDateTime.now())
                    .operationType(operationType)
                    .layer(layer)
                    .agentName(agentName)
                    .description(description)
                    .build();
        }
    }

    /**
     * 添加参数到参数记忆
     *
     * @param key   参数键
     * @param value 参数值
     */
    public void addParameter(String key, Object value) {
        if (key != null && value != null) {
            parameterMemory.put(key, value);
            this.updatedAt = LocalDateTime.now();
        }
    }

    /**
     * 批量添加参数到参数记忆
     *
     * @param params 参数Map
     */
    public void addParameters(Map<String, Object> params) {
        if (params != null && !params.isEmpty()) {
            parameterMemory.putAll(params);
            this.updatedAt = LocalDateTime.now();
        }
    }

    /**
     * 从参数记忆获取参数
     *
     * @param key 参数键
     * @return Object 参数值，不存在时返回null
     */
    public Object getParameter(String key) {
        return key != null ? parameterMemory.get(key) : null;
    }

    /**
     * 从参数记忆获取参数（带默认值）
     *
     * @param key          参数键
     * @param defaultValue 默认值
     * @return Object 参数值
     */
    public Object getParameter(String key, Object defaultValue) {
        Object value = getParameter(key);
        return value != null ? value : defaultValue;
    }

    /**
     * 从参数记忆获取字符串参数
     *
     * @param key 参数键
     * @return String 参数值
     */
    public String getParameterAsString(String key) {
        Object value = getParameter(key);
        return value != null ? String.valueOf(value) : null;
    }

    /**
     * 检查参数是否存在
     *
     * @param key 参数键
     * @return boolean 是否存在
     */
    public boolean hasParameter(String key) {
        return key != null && parameterMemory.containsKey(key);
    }

    /**
     * 添加步骤记忆
     *
     * @param layer      层级
     * @param stepMemory 步骤记忆对象
     */
    public void addStepMemory(Integer layer, StepMemory stepMemory) {
        if (layer != null && stepMemory != null) {
            stepsMemory
                    .computeIfAbsent(layer, k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(stepMemory);
            this.updatedAt = LocalDateTime.now();
        }
    }

    /**
     * 获取指定层级的步骤记忆列表
     *
     * @param layer 层级
     * @return List 步骤记忆列表
     */
    public List<StepMemory> getStepMemories(Integer layer) {
        if (layer == null) {
            return Collections.emptyList();
        }
        List<StepMemory> memories = stepsMemory.get(layer);
        return memories != null ? new ArrayList<>(memories) : Collections.emptyList();
    }

    /**
     * 获取所有层级的步骤记忆
     *
     * @return Map，key为层级，value为步骤列表
     */
    public Map<Integer, List<StepMemory>> getAllStepMemories() {
        Map<Integer, List<StepMemory>> result = new HashMap<>();
        for (Map.Entry<Integer, List<StepMemory>> entry : stepsMemory.entrySet()) {
            result.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return result;
    }

    /**
     * 添加执行记录
     *
     * @param record 执行记录对象
     */
    public void addExecutionRecord(ExecutionRecord record) {
        if (record != null) {
            executionHistory.add(record);
            this.updatedAt = LocalDateTime.now();
        }
    }

    /**
     * 添加执行记录（便捷方法）
     *
     * @param operationType 操作类型
     * @param layer         层级
     * @param agentName     智能体名称
     * @param description   描述
     */
    public void addExecutionRecord(String operationType, Integer layer,
                                   String agentName, String description) {
        addExecutionRecord(ExecutionRecord.create(operationType, layer, agentName, description));
    }

    /**
     * 获取执行历史
     *
     * @return 执行记录列表的副本
     */
    public List<ExecutionRecord> getExecutionHistory() {
        return new ArrayList<>(executionHistory);
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
     * 设置最终结论并完成诊断
     *
     * @param conclusion 诊断结论
     */
    public void complete(String conclusion) {
        this.finalConclusion = conclusion;
        this.status = DiagnosisStatus.COMPLETED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 设置错误信息并标记失败
     *
     * @param errorMessage 错误信息
     */
    public void fail(String errorMessage) {
        this.errorMessage = errorMessage;
        this.status = DiagnosisStatus.FAILED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 添加元数据
     *
     * @param key   键
     * @param value 值
     */
    public void addMetadata(String key, Object value) {
        if (key != null && value != null) {
            metadata.put(key, value);
        }
    }

    /**
     * 获取元数据
     *
     * @param key 键
     * @return 值
     */
    public Object getMetadata(String key) {
        return key != null ? metadata.get(key) : null;
    }

    /**
     * 创建新的诊断上下文
     *
     * @param sessionId       会话ID
     * @param originalProblem 原始问题
     * @param diagnosisType   诊断类型
     * @return DiagnosisContext实例
     */
    public static DiagnosisContext create(String sessionId, String originalProblem, String diagnosisType) {
        return DiagnosisContext.builder()
                .sessionId(sessionId)
                .requestId(UUID.randomUUID().toString())
                .originalProblem(originalProblem)
                .diagnosisType(diagnosisType)
                .currentLayer(1)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .status(DiagnosisStatus.CREATED)
                .parameterMemory(new ConcurrentHashMap<>())
                .layerResults(new LayerResults())
                .stepsMemory(new ConcurrentHashMap<>())
                .executionHistory(Collections.synchronizedList(new ArrayList<>()))
                .metadata(new ConcurrentHashMap<>())
                .build();
    }

    /**
     * 判断诊断是否已完成
     *
     * @return boolean 是否已完成
     */
    public boolean isFinished() {
        return status == DiagnosisStatus.COMPLETED ||
               status == DiagnosisStatus.FAILED ||
               status == DiagnosisStatus.CANCELLED ||
               status == DiagnosisStatus.TIMEOUT;
    }

    /**
     * 判断诊断是否正在运行
     *
     * @return boolean 是否正在运行
     */
    public boolean isRunning() {
        return status == DiagnosisStatus.RUNNING;
    }

    // ==================== OrchestratorService 兼容方法 ====================

    /**
     * 添加执行步骤(简化方法,供OrchestratorService使用)
     *
     * @param layerName   层级名称
     * @param status      步骤状态
     * @param description 步骤描述
     */
    public void addStep(String layerName, String status, String description) {
        StepStatus stepStatus;
        try {
            stepStatus = StepStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            stepStatus = StepStatus.RUNNING;
        }
        
        StepMemory step = StepMemory.builder()
                .stepName(layerName)
                .description(description)
                .status(stepStatus)
                .startTime(LocalDateTime.now())
                .build();
        
        // 使用当前层级,默认为1
        Integer layer = currentLayer != null ? currentLayer : 1;
        addStepMemory(layer, step);
    }

    /**
     * 添加层级执行结果(供OrchestratorService使用)
     *
     * @param layerName 层级名称
     * @param result    执行结果
     */
    public void addLayerResult(String layerName, Map<String, Object> result) {
        if (layerName == null || result == null) {
            return;
        }
        
        // 将结果存储到元数据中,使用layerName作为key
        String key = "layer_result_" + layerName;
        metadata.put(key, result);
        
        // 同时添加到layerResults(如果能解析出层级数字)
        try {
            int layerNum = parseLayerNumber(layerName);
            layerResults.addResult(layerNum, result);
        } catch (NumberFormatException e) {
            // 如果层级名不是数字,只存储到metadata
        }
        
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 获取所有层级结果(Map格式,供OrchestratorService使用)
     *
     * @return Map,key为层级名称,value为该层结果
     */
    public Map<String, Map<String, Object>> getLayerResultsAsMap() {
        Map<String, Map<String, Object>> result = new HashMap<>();
        
        // 从metadata中提取层级结果
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            if (entry.getKey().startsWith("layer_result_") && entry.getValue() instanceof Map) {
                String layerName = entry.getKey().substring("layer_result_".length());
                @SuppressWarnings("unchecked")
                Map<String, Object> layerResult = (Map<String, Object>) entry.getValue();
                result.put(layerName, layerResult);
            }
        }
        
        return result;
    }

    /**
     * 获取步骤记忆列表(供OrchestratorService使用)
     *
     * @return 所有步骤记忆的扁平化列表
     */
    public List<StepMemory> getStepMemory() {
        List<StepMemory> allSteps = new ArrayList<>();
        for (List<StepMemory> layerSteps : stepsMemory.values()) {
            allSteps.addAll(layerSteps);
        }
        return allSteps;
    }

    /**
     * 解析层级名称中的数字
     *
     * @param layerName 层级名称
     * @return 层级数字
     */
    private int parseLayerNumber(String layerName) {
        // 尝试从层级名称中提取数字
        if (layerName.matches(".*\\d+.*")) {
            String num = layerName.replaceAll("\\D+", "");
            return Integer.parseInt(num);
        }
        
        // 预定义的层级映射
        switch (layerName.toLowerCase()) {
            case "orchestrator":
                return 3;
            case "analyzer":
                return 2;
            case "collector":
                return 1;
            default:
                throw new NumberFormatException("无法解析层级: " + layerName);
        }
    }
}
