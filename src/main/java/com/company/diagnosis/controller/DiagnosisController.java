package com.company.diagnosis.controller;

import com.company.diagnosis.adapter.AlertInputAdapter;
import com.company.diagnosis.model.dto.DiagnosisRequest;
import com.company.diagnosis.service.OrchestratorService;
import com.company.diagnosis.service.ReportGenerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 诊断控制器
 * <p>
 * 职责：
 * 1. 提供诊断任务的REST API接口
 * 2. 支持同步诊断和异步诊断
 * 3. 支持SSE流式输出诊断过程
 * 4. 提供诊断任务状态查询
 * <p>
 * 设计考虑：
 * - 使用响应式编程模型(Mono/Flux)处理请求
 * - 支持流式输出,提升用户体验
 * - 统一错误处理和返回格式
 *
 * @author Diagnosis System
 * @date 2026-01-13
 */
@RestController
@RequestMapping("/api/v1/diagnosis")
@Tag(name = "诊断服务", description = "提供告警诊断的核心API")
public class DiagnosisController {

    private static final Logger log = LoggerFactory.getLogger(DiagnosisController.class);

    @Autowired
    private OrchestratorService orchestratorService;

    @Autowired(required = false)
    private ReportGenerationService reportGenerationService;

    @Autowired(required = false)
    private AlertInputAdapter alertInputAdapter;

    /**
     * 启动诊断任务（同步接口）
     */
    @PostMapping("/start")
    @Operation(summary = "启动同步诊断", description = "同步执行诊断流程,返回完整诊断结果")
    public Mono<Map<String, Object>> startDiagnosis(@Valid @RequestBody DiagnosisRequest request) {
        log.info("收到同步诊断请求: alertId={}", request.getAlertId());

        return validateRequest(request)
                .flatMap(valid -> {
                    if (!valid) {
                        return Mono.just(errorResponse("请求参数验证失败"));
                    }
                    return orchestratorService.startDiagnosis(request);
                })
                .onErrorResume(e -> {
                    log.error("同步诊断失败", e);
                    return Mono.just(errorResponse("诊断执行失败: " + e.getMessage()));
                });
    }

    /**
     * 启动诊断任务（异步接口）
     */
    @PostMapping("/start-async")
    @Operation(summary = "启动异步诊断", description = "异步启动诊断流程,立即返回任务ID")
    public Mono<Map<String, Object>> startDiagnosisAsync(@Valid @RequestBody DiagnosisRequest request) {
        log.info("收到异步诊断请求: alertId={}", request.getAlertId());

        return validateRequest(request)
                .flatMap(valid -> {
                    if (!valid) {
                        return Mono.just(errorResponse("请求参数验证失败"));
                    }
                    return orchestratorService.startDiagnosisAsync(request);
                })
                .onErrorResume(e -> {
                    log.error("启动异步诊断失败", e);
                    return Mono.just(errorResponse("启动失败: " + e.getMessage()));
                });
    }

    /**
     * SSE流式诊断接口
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式诊断", description = "使用SSE协议实时推送诊断过程和结果")
    public Flux<Map<String, Object>> streamDiagnosis(@Valid @RequestBody DiagnosisRequest request) {
        log.info("收到流式诊断请求: alertId={}", request.getAlertId());

        return orchestratorService.startDiagnosisStream(request)
                .onErrorResume(e -> {
                    log.error("流式诊断失败", e);
                    Map<String, Object> error = new HashMap<>();
                    error.put("type", "error");
                    error.put("error", e.getMessage());
                    return Flux.just(error);
                });
    }

    /**
     * 查询诊断任务状态
     */
    @GetMapping("/status/{sessionId}")
    @Operation(summary = "查询诊断状态", description = "根据会话ID查询诊断任务的当前状态")
    public Mono<Map<String, Object>> getDiagnosisStatus(@PathVariable String sessionId) {
        log.info("查询诊断状态: sessionId={}", sessionId);

        if (!StringUtils.hasText(sessionId)) {
            return Mono.just(errorResponse("会话ID不能为空"));
        }

        Map<String, Object> status = new HashMap<>();
        status.put("sessionId", sessionId);

        // 检查是否为活跃会话
        if (orchestratorService.getActiveSessionIds().contains(sessionId)) {
            status.put("status", "RUNNING");
            status.put("message", "诊断正在执行中");
        } else {
            status.put("status", "UNKNOWN");
            status.put("message", "会话不存在或已完成");
        }

        status.put("timestamp", Instant.now().toString());
        return Mono.just(status);
    }

    /**
     * 获取诊断结果
     */
    @GetMapping("/result/{sessionId}")
    @Operation(summary = "获取诊断结果", description = "根据会话ID获取完整的诊断结果")
    public Mono<Map<String, Object>> getDiagnosisResult(@PathVariable String sessionId) {
        log.info("获取诊断结果: sessionId={}", sessionId);

        if (!StringUtils.hasText(sessionId)) {
            return Mono.just(errorResponse("会话ID不能为空"));
        }

        // 这里应该从存储中获取诊断结果
        // 简化实现:返回示例数据
        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("status", "COMPLETED");
        result.put("message", "请使用流式接口获取实时结果,或从存储中查询历史结果");
        result.put("timestamp", Instant.now().toString());

        return Mono.just(result);
    }

    /**
     * 停止诊断任务
     */
    @PostMapping("/stop/{sessionId}")
    @Operation(summary = "停止诊断", description = "强制停止正在执行的诊断任务")
    public Mono<Map<String, Object>> stopDiagnosis(@PathVariable String sessionId) {
        log.info("停止诊断任务: sessionId={}", sessionId);

        if (!StringUtils.hasText(sessionId)) {
            return Mono.just(errorResponse("会话ID不能为空"));
        }

        return orchestratorService.stopDiagnosis(sessionId)
                .onErrorResume(e -> {
                    log.error("停止诊断失败", e);
                    return Mono.just(errorResponse("停止失败: " + e.getMessage()));
                });
    }

    /**
     * 从HTTP请求数据启动诊断
     */
    @PostMapping("/from-alert")
    @Operation(summary = "从告警数据启动诊断", description = "适配原始告警数据并启动诊断")
    public Mono<Map<String, Object>> startFromAlert(@RequestBody Map<String, Object> alertData) {
        log.info("从告警数据启动诊断: keys={}", alertData.keySet());

        if (alertInputAdapter == null) {
            return Mono.just(errorResponse("AlertInputAdapter未配置"));
        }

        try {
            DiagnosisRequest request = alertInputAdapter.adaptFromHttp(alertData);
            return startDiagnosisAsync(request);
        } catch (Exception e) {
            log.error("适配告警数据失败", e);
            return Mono.just(errorResponse("适配失败: " + e.getMessage()));
        }
    }

    private Mono<Boolean> validateRequest(DiagnosisRequest request) {
        return Mono.fromCallable(() -> {
            if (request == null) {
                return false;
            }
            // alertId是必需的
            return StringUtils.hasText(request.getAlertId());
        });
    }

    private Map<String, Object> errorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        response.put("timestamp", Instant.now().toString());
        return response;
    }
}
