package com.company.diagnosis.service;

import com.company.diagnosis.agent.base.BaseIntelligentAgent;
import com.company.diagnosis.loader.AgentConfigLoader;
import com.company.diagnosis.model.config.AgentConfig;
import com.company.diagnosis.model.context.DiagnosisContext;
import com.company.diagnosis.model.dto.DiagnosisRequest;
import com.company.diagnosis.registry.AgentRegistry;
import com.company.diagnosis.util.TraceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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

    private static final Logger logger = LoggerFactory.getLogger(OrchestratorService.class);

    @Autowired
    private AgentRegistry agentRegistry;
    
    @Autowired
    private AgentConfigLoader configLoader;
    
    @Autowired
    private SseConnectionManager sseConnectionManager;
    
    /**
     * 存储活跃的诊断会话
     */
    private final ConcurrentHashMap<String, DiagnosisContext> activeSessions = new ConcurrentHashMap<>();

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
        return Mono.fromCallable(() -> {
            // 初始化上下文
            DiagnosisContext context = initializeContext(request);
            activeSessions.put(context.getSessionId(), context);
            
            logger.info("开始诊断流程: sessionId={}, problem={}", 
                    context.getSessionId(), request.getProblem());
            
            return context;
        })
        .flatMap(context -> {
            // 执行诊断流程
            return executeDiagnosisFlow(context)
                    .then(mergeLayerResults(context))
                    .doOnSuccess(result -> {
                        context.updateStatus(DiagnosisContext.DiagnosisStatus.COMPLETED);
                        saveDiagnosisResult(context).subscribe();
                    })
                    .doOnError(e -> {
                        context.updateStatus(DiagnosisContext.DiagnosisStatus.FAILED);
                        context.setErrorMessage(e.getMessage());
                    })
                    .doFinally(signal -> {
                        // 清理会话（延迟清理，保留一段时间供查询）
                        // activeSessions.remove(context.getSessionId());
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
            // 初始化上下文
            DiagnosisContext context = initializeContext(request);
            activeSessions.put(context.getSessionId(), context);
            
            // 异步执行诊断
            startDiagnosis(request).subscribe(
                    result -> logger.info("异步诊断完成: sessionId={}", context.getSessionId()),
                    error -> logger.error("异步诊断失败: sessionId={}, error={}", 
                            context.getSessionId(), error.getMessage())
            );
            
            // 立即返回任务信息
            Map<String, Object> taskInfo = new HashMap<>();
            taskInfo.put("sessionId", context.getSessionId());
            taskInfo.put("requestId", context.getRequestId());
            taskInfo.put("status", "started");
            taskInfo.put("message", "诊断任务已启动");
            taskInfo.put("createdAt", context.getCreatedAt().toString());
            
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
            // 初始化上下文
            DiagnosisContext context = initializeContext(request);
            activeSessions.put(context.getSessionId(), context);
            
            // 发送开始事件
            Map<String, Object> startEvent = new HashMap<>();
            startEvent.put("type", "diagnosis_started");
            startEvent.put("sessionId", context.getSessionId());
            startEvent.put("requestId", context.getRequestId());
            startEvent.put("timestamp", LocalDateTime.now().toString());
            sink.next(startEvent);
            
            // 注册SSE连接
            sseConnectionManager.registerConnection(context.getSessionId())
                    .subscribe(event -> {
                        if (!"heartbeat".equals(event.get("type"))) {
                            sink.next(event);
                        }
                    });
            
            // 执行诊断流程
            executeDiagnosisFlowStream(context)
                    .doOnNext(sink::next)
                    .doOnComplete(() -> {
                        // 发送完成事件
                        Map<String, Object> completeEvent = new HashMap<>();
                        completeEvent.put("type", "diagnosis_completed");
                        completeEvent.put("sessionId", context.getSessionId());
                        completeEvent.put("timestamp", LocalDateTime.now().toString());
                        sink.next(completeEvent);
                        sink.complete();
                    })
                    .doOnError(e -> {
                        // 发送错误事件
                        Map<String, Object> errorEvent = new HashMap<>();
                        errorEvent.put("type", "diagnosis_error");
                        errorEvent.put("sessionId", context.getSessionId());
                        errorEvent.put("error", e.getMessage());
                        errorEvent.put("timestamp", LocalDateTime.now().toString());
                        sink.next(errorEvent);
                        sink.complete();
                    })
                    .subscribe();
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
            Map<String, Object> result = new HashMap<>();
            
            DiagnosisContext context = activeSessions.get(sessionId);
            if (context == null) {
                result.put("success", false);
                result.put("error", "会话不存在");
                return result;
            }
            
            // 更新状态
            context.updateStatus(DiagnosisContext.DiagnosisStatus.CANCELLED);
            
            // 关闭SSE连接
            sseConnectionManager.closeConnection(sessionId).subscribe();
            
            // 保存当前状态
            saveDiagnosisResult(context).subscribe();
            
            result.put("success", true);
            result.put("sessionId", sessionId);
            result.put("message", "诊断已停止");
            
            logger.info("诊断已停止: sessionId={}", sessionId);
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
        return Mono.fromCallable(() -> {
            logger.info("执行层级: layerName={}, sessionId={}", layerName, context.getSessionId());
            
            // 获取智能体
            BaseIntelligentAgent agent = agentRegistry.getAgent(layerName);
            if (agent == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("error", "智能体不存在: " + layerName);
                return error;
            }
            
            // 准备输入
            Map<String, Object> input = prepareLayerInput(layerName, context);
            
            return input;
        })
        .flatMap(input -> {
            BaseIntelligentAgent agent = agentRegistry.getAgent(layerName);
            if (agent == null) {
                return Mono.just(Map.of("success", false, "error", "智能体不存在"));
            }
            
            return agent.execute(input)
                    .map(result -> {
                        // 更新上下文
                        AgentConfig config = agentRegistry.getAgentConfig(layerName);
                        if (config != null) {
                            context.addLayerResult(config.getLayer(), result);
                        }
                        
                        // 记录执行历史
                        DiagnosisContext.ExecutionRecord record = DiagnosisContext.ExecutionRecord.builder()
                                .timestamp(LocalDateTime.now())
                                .operationType("layer_execution")
                                .agentName(layerName)
                                .description("执行层级: " + layerName)
                                .result(result)
                                .build();
                        context.addExecutionRecord(record);
                        
                        return result;
                    });
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
            List<String> layerChain = new ArrayList<>();
            
            // 获取所有启用的配置，按层级排序
            List<AgentConfig> enabledConfigs = configLoader.loadEnabledConfigs();
            enabledConfigs.sort((a, b) -> b.getLayer().compareTo(a.getLayer())); // 从高到低
            
            for (AgentConfig config : enabledConfigs) {
                // 检查触发条件
                if (evaluateTriggerCondition(config, context)) {
                    layerChain.add(config.getName());
                }
            }
            
            logger.info("确定层级链: {}", layerChain);
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
        if (currentLayer == null || targetLayer == null) {
            return false;
        }
        
        // 第1层只能调用外部Tool API
        if (currentLayer == 1) {
            return targetLayer == 0; // 0表示外部API
        }
        
        // 第N层只能调用第N-1层
        return currentLayer == targetLayer + 1;
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
            Map<String, Object> finalReport = new HashMap<>();
            
            finalReport.put("sessionId", context.getSessionId());
            finalReport.put("requestId", context.getRequestId());
            finalReport.put("diagnosisType", context.getDiagnosisType());
            finalReport.put("originalProblem", context.getOriginalProblem());
            finalReport.put("status", context.getStatus().name());
            finalReport.put("createdAt", context.getCreatedAt().toString());
            finalReport.put("completedAt", LocalDateTime.now().toString());
            
            // 合并各层结果
            Map<String, Object> layerResultsMap = new HashMap<>();
            DiagnosisContext.LayerResults layerResults = context.getLayerResults();
            
            layerResultsMap.put("layer1", layerResults.getLayer1Results());
            layerResultsMap.put("layer2", layerResults.getLayer2Results());
            layerResultsMap.put("layer3", layerResults.getLayer3Results());
            
            finalReport.put("layerResults", layerResultsMap);
            
            // 提取最终结论（从最高层结果中）
            Map<String, Object> lastResult = layerResults.getLastResult(
                    configLoader.getTopLayer());
            if (lastResult != null) {
                finalReport.put("conclusion", lastResult.get("data"));
            }
            
            // 参数记忆
            finalReport.put("parameterMemory", context.getParameterMemory());
            
            // 执行历史
            finalReport.put("executionHistory", context.getExecutionHistory());
            
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
            // 当前实现：保存到内存
            // 实际应用中应该保存到数据库或缓存
            activeSessions.put(context.getSessionId(), context);
            
            logger.info("诊断结果已保存: sessionId={}", context.getSessionId());
            return true;
        });
    }
    
    /**
     * 获取诊断状态
     */
    public Mono<Map<String, Object>> getDiagnosisStatus(String sessionId) {
        return Mono.fromCallable(() -> {
            Map<String, Object> status = new HashMap<>();
            
            DiagnosisContext context = activeSessions.get(sessionId);
            if (context == null) {
                status.put("success", false);
                status.put("error", "会话不存在");
                return status;
            }
            
            status.put("success", true);
            status.put("sessionId", context.getSessionId());
            status.put("requestId", context.getRequestId());
            status.put("status", context.getStatus().name());
            status.put("currentLayer", context.getCurrentLayer());
            status.put("createdAt", context.getCreatedAt().toString());
            status.put("updatedAt", context.getUpdatedAt().toString());
            
            return status;
        });
    }
    
    /**
     * 获取诊断结果
     */
    public Mono<Map<String, Object>> getDiagnosisResult(String sessionId) {
        return Mono.fromCallable(() -> {
            DiagnosisContext context = activeSessions.get(sessionId);
            if (context == null) {
                return Map.of("success", false, "error", "会话不存在");
            }
            
            return context;
        }).flatMap(obj -> {
            if (obj instanceof Map) {
                return Mono.just((Map<String, Object>) obj);
            }
            return mergeLayerResults((DiagnosisContext) obj);
        });
    }
    
    // ==================== 私有方法 ====================
    
    /**
     * 初始化诊断上下文
     */
    private DiagnosisContext initializeContext(DiagnosisRequest request) {
        String sessionId = request.getSessionId() != null ? 
                request.getSessionId() : TraceUtil.generateSessionId();
        String requestId = request.getRequestId() != null ? 
                request.getRequestId() : TraceUtil.generateRequestId();
        
        DiagnosisContext context = DiagnosisContext.create(
                sessionId, requestId, 
                request.getDiagnosisType(), 
                request.getProblem());
        
        // 初始化参数
        if (request.getParameters() != null) {
            request.getParameters().forEach(context::addParameter);
        }
        
        context.updateStatus(DiagnosisContext.DiagnosisStatus.RUNNING);
        
        return context;
    }
    
    /**
     * 执行诊断流程
     */
    private Mono<Void> executeDiagnosisFlow(DiagnosisContext context) {
        return determineLayerChain(context)
                .flatMapMany(Flux::fromIterable)
                .concatMap(layerName -> executeLayer(layerName, context))
                .then();
    }
    
    /**
     * 流式执行诊断流程
     */
    private Flux<Map<String, Object>> executeDiagnosisFlowStream(DiagnosisContext context) {
        return determineLayerChain(context)
                .flatMapMany(Flux::fromIterable)
                .concatMap(layerName -> {
                    BaseIntelligentAgent agent = agentRegistry.getAgent(layerName);
                    if (agent == null) {
                        return Flux.empty();
                    }
                    
                    Map<String, Object> input = prepareLayerInput(layerName, context);
                    return agent.executeStream(input)
                            .doOnNext(event -> {
                                // 更新上下文
                                if ("result".equals(event.get("type"))) {
                                    AgentConfig config = agentRegistry.getAgentConfig(layerName);
                                    if (config != null) {
                                        context.addLayerResult(config.getLayer(), event);
                                    }
                                }
                            });
                });
    }
    
    /**
     * 准备层级输入
     */
    private Map<String, Object> prepareLayerInput(String layerName, DiagnosisContext context) {
        Map<String, Object> input = new HashMap<>();
        
        input.put("session_id", context.getSessionId());
        input.put("request_id", context.getRequestId());
        input.put("problem", context.getOriginalProblem());
        input.put("diagnosis_type", context.getDiagnosisType());
        input.put("parameter_memory", context.getParameterMemory());
        input.put("execution_history", context.getExecutionHistory());
        
        // 添加前一层的结果
        AgentConfig config = agentRegistry.getAgentConfig(layerName);
        if (config != null && config.getLayer() > 1) {
            input.put("previous_layer_results", 
                    context.getLayerResults().getResultsByLayer(config.getLayer() - 1));
        }
        
        return input;
    }
    
    /**
     * 评估触发条件
     */
    private boolean evaluateTriggerCondition(AgentConfig config, DiagnosisContext context) {
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            return false;
        }
        
        String condition = config.getTriggerCondition();
        if (condition == null || condition.isEmpty() || "always".equals(condition)) {
            return true;
        }
        
        // 简单的条件评估
        // 实际应用中可以使用表达式引擎
        return true;
    }
    
    /**
     * 获取会话上下文
     *
     * @param sessionId 会话ID
     * @return 诊断上下文，不存在返回null
     */
    public DiagnosisContext getContext(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        return activeSessions.get(sessionId);
    }
    
    /**
     * 移除会话
     *
     * @param sessionId 会话ID
     */
    public void removeSession(String sessionId) {
        if (sessionId != null) {
            activeSessions.remove(sessionId);
            logger.info("移除会话: sessionId={}", sessionId);
        }
    }
    
    /**
     * 获取活跃会话数量
     *
     * @return 活跃会话数量
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }
    
    /**
     * 获取所有活跃会话ID
     *
     * @return 会话ID列表
     */
    public List<String> getActiveSessionIds() {
        return new ArrayList<>(activeSessions.keySet());
    }
}