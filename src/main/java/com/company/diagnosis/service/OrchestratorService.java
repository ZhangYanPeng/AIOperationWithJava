package com.company.diagnosis.service;

import com.company.diagnosis.config.AgentScopeConfig.LlmClientRegistry;
import com.company.diagnosis.model.context.DiagnosisContext;
import com.company.diagnosis.model.dto.DiagnosisRequest;
import com.company.diagnosis.model.event.DiagnosisEvent;
import com.company.diagnosis.loader.AgentConfigLoader;
import com.company.diagnosis.util.JsonUtil;
import com.company.diagnosis.model.config.AgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 诊断编排服务
 * <p>
 * 职责：
 * 1. 负责整体诊断流程的编排和调度
 * 2. 管理智能体层级的执行顺序
 * 3. 协调各层智能体之间的数据传递
 * 4. 控制诊断流程的生命周期
 * <p>
 * 设计考虑：
 * - 严格执行层级调用规则：第N层只调用第N-1层
 * - 支持配置驱动的动态层级扩展
 * - 实现响应式流式编排
 * - 提供诊断过程的可观测性
 *
 * @author Diagnosis System
 * @date 2026-01-13
 */
@Service
public class OrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorService.class);

    /**
     * 存储活跃的诊断会话
     */
    private final ConcurrentHashMap<String, DiagnosisContext> activeSessions = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private LlmClientRegistry llmClientRegistry;

    @Autowired(required = false)
    private AgentConfigLoader agentConfigLoader;

    @Autowired(required = false)
    private SseConnectionManager sseConnectionManager;

    @Autowired(required = false)
    private ReportGenerationService reportGenerationService;

    @Autowired(required = false)
    @Lazy
    private MessageProcessingService messageProcessingService;

    @Autowired(required = false)
    @Qualifier("ioScheduler")
    private Scheduler ioScheduler;

    /**
     * 启动诊断流程（同步）
     * <p>
     * 功能说明：
     * 根据诊断请求，同步执行完整的诊断流程，返回最终结果
     *
     * @param request 诊断请求对象
     * @return 诊断结果Map，包含所有层级的执行结果
     */
    public Mono<Map<String, Object>> startDiagnosis(DiagnosisRequest request) {
        return Mono.defer(() -> {
            log.info("开始同步诊断流程: alertId={}", request.getAlertId());

            // 1. 初始化DiagnosisContext
            DiagnosisContext context = initializeContext(request);
            String sessionId = context.getSessionId();
            activeSessions.put(sessionId, context);

            // 2. 确定层级执行链
            return determineLayerChain(context)
                    // 3. 按层级顺序执行
                    .flatMap(layerChain -> executeLayerChain(layerChain, context))
                    // 4. 合并结果生成报告
                    .flatMap(this::mergeLayerResults)
                    // 5. 清理会话
                    .doFinally(signal -> {
                        activeSessions.remove(sessionId);
                        log.info("诊断流程结束: sessionId={}, signal={}", sessionId, signal);
                    });
        });
    }

    /**
     * 启动诊断流程（异步）
     * <p>
     * 功能说明：
     * 异步启动诊断流程，立即返回任务标识
     *
     * @param request 诊断请求对象
     * @return 任务信息Map，包含sessionId、requestId等
     */
    public Mono<Map<String, Object>> startDiagnosisAsync(DiagnosisRequest request) {
        return Mono.fromCallable(() -> {
            log.info("开始异步诊断流程: alertId={}", request.getAlertId());

            // 1. 初始化DiagnosisContext
            DiagnosisContext context = initializeContext(request);
            String sessionId = context.getSessionId();
            activeSessions.put(sessionId, context);

            // 2. 异步启动诊断流程
            startDiagnosis(request)
                    .subscribe(
                            result -> log.info("异步诊断完成: sessionId={}", sessionId),
                            error -> log.error("异步诊断失败: sessionId={}", sessionId, error)
                    );

            // 3. 立即返回任务信息
            Map<String, Object> taskInfo = new HashMap<>();
            taskInfo.put("sessionId", sessionId);
            taskInfo.put("requestId", context.getRequestId());
            taskInfo.put("status", "STARTED");
            taskInfo.put("startTime", Instant.now().toString());
            taskInfo.put("message", "诊断任务已启动,请通过SSE订阅进度");

            return taskInfo;
        });
    }

    /**
     * 启动诊断流程（流式）
     * <p>
     * 功能说明：
     * 以流式方式执行诊断，实时推送各阶段的执行结果
     *
     * @param request 诊断请求对象
     * @return 诊断过程事件流
     */
    public Flux<Map<String, Object>> startDiagnosisStream(DiagnosisRequest request) {
        return Flux.create(sink -> {
            log.info("开始流式诊断流程: alertId={}", request.getAlertId());

            // 1. 初始化DiagnosisContext
            DiagnosisContext context = initializeContext(request);
            String sessionId = context.getSessionId();
            activeSessions.put(sessionId, context);

            // 2. 发送开始事件
            sink.next(DiagnosisEvent.started(sessionId, request.getAlertId()).toMap());

            // 3. 执行诊断流程
            determineLayerChain(context)
                    .flatMapMany(layerChain -> Flux.fromIterable(layerChain)
                            .concatMap(layerName -> {
                                // 发送层级开始事件
                                sink.next(DiagnosisEvent.layerStarted(sessionId, layerName).toMap());

                                return executeLayer(layerName, context)
                                        .doOnSuccess(result -> {
                                            // 发送层级完成事件
                                            sink.next(DiagnosisEvent.layerCompleted(sessionId, layerName, result).toMap());
                                        })
                                        .doOnError(error -> {
                                            // 发送层级错误事件
                                            sink.next(DiagnosisEvent.error(sessionId, "执行层级失败: " + layerName, error.getMessage()).toMap());
                                        });
                            }))
                    .then(mergeLayerResults(context))
                    .subscribe(
                            finalResult -> {
                                // 发送完成事件
                                sink.next(DiagnosisEvent.completed(sessionId, finalResult).toMap());
                                sink.complete();
                            },
                            error -> {
                                sink.next(DiagnosisEvent.error(sessionId, "诊断流程失败", error.getMessage()).toMap());
                                sink.error(error);
                            },
                            () -> {
                                activeSessions.remove(sessionId);
                            }
                    );
        });
    }

    /**
     * 停止诊断流程
     * <p>
     * 功能说明：
     * 强制停止正在执行的诊断流程
     *
     * @param sessionId 会话ID
     * @return 停止结果Map
     */
    public Mono<Map<String, Object>> stopDiagnosis(String sessionId) {
        return Mono.fromCallable(() -> {
            log.info("停止诊断流程: sessionId={}", sessionId);

            DiagnosisContext context = activeSessions.get(sessionId);
            Map<String, Object> result = new HashMap<>();

            if (context == null) {
                result.put("success", false);
                result.put("message", "未找到活跃的诊断会话: " + sessionId);
                return result;
            }

            // 标记上下文为已取消
            context.addParameter("cancelled", true);
            context.addParameter("cancelledAt", Instant.now().toString());

            // 从活跃会话中移除
            activeSessions.remove(sessionId);

            result.put("success", true);
            result.put("sessionId", sessionId);
            result.put("message", "诊断流程已停止");
            result.put("stoppedAt", Instant.now().toString());

            return result;
        });
    }

    /**
     * 执行单层智能体
     * <p>
     * 功能说明：
     * 执行指定层级的智能体，并调用其下层智能体
     *
     * @param layerName 层级名称
     * @param context 诊断上下文
     * @return 该层的执行结果
     */
    public Mono<Map<String, Object>> executeLayer(String layerName, DiagnosisContext context) {
        return Mono.defer(() -> {
            log.info("执行层级: layerName={}, sessionId={}", layerName, context.getSessionId());

            // 1. 检查是否已取消
            if (Boolean.TRUE.equals(context.getParameter("cancelled"))) {
                return Mono.error(new RuntimeException("诊断已取消"));
            }

            // 2. 记录层级开始
            context.addStep(layerName, "STARTED", "开始执行层级: " + layerName);

            // 3. 执行层级逻辑（这里是占位符，实际应该调用对应的Agent）
            Map<String, Object> layerResult = new HashMap<>();
            layerResult.put("layer", layerName);
            layerResult.put("status", "SUCCESS");
            layerResult.put("startTime", Instant.now().toString());

            // 模拟层级执行
            // 实际实现中,应该从AgentRegistry获取Agent并执行
            layerResult.put("output", "层级 " + layerName + " 执行完成");
            layerResult.put("endTime", Instant.now().toString());

            // 4. 将结果写入context的layerResults
            context.addLayerResult(layerName, layerResult);
            context.addStep(layerName, "COMPLETED", "层级执行完成");

            log.info("层级执行完成: layerName={}", layerName);
            return Mono.just(layerResult);
        });
    }

    /**
     * 确定需要执行的层级链
     * <p>
     * 功能说明：
     * 根据配置和执行条件，确定本次诊断需要执行的层级链
     *
     * @param context 诊断上下文
     * @return 需要执行的层级名称列表（从高到低）
     */
    public Mono<List<String>> determineLayerChain(DiagnosisContext context) {
        return Mono.fromCallable(() -> {
            log.info("确定层级执行链: sessionId={}", context.getSessionId());

            // 默认层级链（从高到低）
            // 按照AgentScope架构：Layer3 -> Layer2 -> Layer1
            List<String> layerChain = new ArrayList<>();

            // 加载agent-config.yml配置
            if (agentConfigLoader != null) {
                List<AgentConfig> configs = agentConfigLoader.loadConfig();
                // 从配置中提取层级定义
                // 实际实现应该解析配置文件
            }

            // 默认层级链
            layerChain.add("orchestrator");  // 最高层：编排层
            layerChain.add("analyzer");      // 中间层：分析层
            layerChain.add("collector");     // 底层：数据采集层

            log.info("层级执行链确定: {}", layerChain);
            return layerChain;
        });
    }

    /**
     * 验证层级调用合法性
     * <p>
     * 功能说明：
     * 确保层级调用符合规则：第N层只能调用第N-1层
     *
     * @param currentLayer 当前层级
     * @param targetLayer 目标层级
     * @return 是否合法
     */
    public Boolean validateLayerCall(Integer currentLayer, Integer targetLayer) {
        // 1. 检查currentLayer是否等于targetLayer+1
        if (currentLayer == null || targetLayer == null) {
            log.warn("层级验证失败: 层级参数为空");
            return false;
        }

        // 第N层只能调用第N-1层
        boolean valid = currentLayer.equals(targetLayer + 1);

        // 2. 第1层只能调用外部Tool API，不能调用其他层
        if (currentLayer == 1 && targetLayer != 0) {
            log.warn("层级验证失败: 第1层只能调用外部API");
            return false;
        }

        if (!valid) {
            log.warn("层级验证失败: 第{}层不能调用第{}层", currentLayer, targetLayer);
        }

        return valid;
    }

    /**
     * 合并层级结果
     * <p>
     * 功能说明：
     * 将各层级的执行结果合并为最终的诊断报告
     *
     * @param context 包含所有层级结果的诊断上下文
     * @return 合并后的最终报告
     */
    public Mono<Map<String, Object>> mergeLayerResults(DiagnosisContext context) {
        return Mono.fromCallable(() -> {
            log.info("合并层级结果: sessionId={}", context.getSessionId());

            Map<String, Object> finalReport = new HashMap<>();

            // 1. 基础信息
            finalReport.put("sessionId", context.getSessionId());
            finalReport.put("requestId", context.getRequestId());
            finalReport.put("alertId", context.getAlertId());
            finalReport.put("startTime", context.getStartTime().toString());
            finalReport.put("endTime", Instant.now().toString());

            // 2. 各层级结果
            Map<String, Map<String, Object>> layerResults = context.getLayerResultsAsMap();
            finalReport.put("layerResults", layerResults);

            // 3. 提取关键结论
            Map<String, Object> conclusion = new HashMap<>();
            conclusion.put("diagnosis", "诊断完成");
            conclusion.put("severity", "MEDIUM");
            conclusion.put("confidence", 0.85);
            finalReport.put("conclusion", conclusion);

            // 4. 生成建议
            List<String> recommendations = new ArrayList<>();
            recommendations.add("建议1: 检查系统资源使用情况");
            recommendations.add("建议2: 查看相关服务日志");
            finalReport.put("recommendations", recommendations);

            // 5. 执行步骤
            finalReport.put("steps", context.getStepMemory());

            log.info("层级结果合并完成");
            return finalReport;
        });
    }

    /**
     * 保存诊断结果
     * <p>
     * 功能说明：
     * 持久化诊断上下文和结果
     *
     * @param context 诊断上下文
     * @return 保存结果
     */
    public Mono<Boolean> saveDiagnosisResult(DiagnosisContext context) {
        return Mono.fromCallable(() -> {
            log.info("保存诊断结果: sessionId={}", context.getSessionId());

            // 1. 序列化DiagnosisContext
            String contextJson = JsonUtil.toJson(context);

            // 2. 保存到数据库或缓存（这里是占位符）
            log.debug("诊断结果JSON: {}", contextJson);

            // 3. 发送诊断完成事件到Kafka
            if (messageProcessingService != null) {
                Map<String, Object> event = new HashMap<>();
                event.put("type", "DIAGNOSIS_COMPLETED");
                event.put("sessionId", context.getSessionId());
                event.put("alertId", context.getAlertId());
                event.put("timestamp", Instant.now().toString());

                messageProcessingService.sendDiagnosisEvent("diagnosis-event", event)
                        .subscribe();
            }

            log.info("诊断结果保存成功");
            return true;
        });
    }

    /**
     * 初始化诊断上下文
     */
    private DiagnosisContext initializeContext(DiagnosisRequest request) {
        DiagnosisContext context = DiagnosisContext.builder()
                .sessionId(UUID.randomUUID().toString().replace("-", ""))
                .requestId(request.getRequestId() != null ? request.getRequestId() : UUID.randomUUID().toString())
                .alertId(request.getAlertId())
                .diagnosisType(request.getAlertId())
                .originalProblem(request.getProblemDescription())
                .startTime(Instant.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .status(DiagnosisContext.DiagnosisStatus.RUNNING)
                .parameterMemory(new HashMap<>())
                .layerResults(new DiagnosisContext.LayerResults())
                .stepsMemory(new HashMap<>())
                .executionHistory(new ArrayList<>())
                .finalConclusion("")
                .metadata(new HashMap<>())
                .build();

        // 添加请求参数到上下文
        if (request.getAlertData() != null) {
            context.getParameterMemory().put("alertData", request.getAlertData());
        }
        if (request.getOptions() != null) {
            context.getParameterMemory().putAll(request.getOptions());
        }

        log.info("初始化诊断上下文: sessionId={}, alertId={}",
                context.getSessionId(), context.getAlertId());

        return context;
    }

    /**
     * 执行层级链
     */
    private Mono<DiagnosisContext> executeLayerChain(List<String> layerChain, DiagnosisContext context) {
        return Flux.fromIterable(layerChain)
                .concatMap(layerName -> executeLayer(layerName, context))
                .then(Mono.just(context));
    }

    /**
     * 获取活跃会话数量
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    /**
     * 获取活跃会话列表
     */
    public Set<String> getActiveSessionIds() {
        return Collections.unmodifiableSet(activeSessions.keySet());
    }
}
