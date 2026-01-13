package com.company.diagnosis.controller;

import com.company.diagnosis.model.dto.DiagnosisRequest;
import com.company.diagnosis.service.OrchestratorService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
public class DiagnosisController {

    private static final Logger logger = LoggerFactory.getLogger(DiagnosisController.class);

    @Autowired
    private OrchestratorService orchestratorService;

    /**
     * 启动诊断任务（同步接口）
     * <p>
     * 功能说明：
     * 接收诊断请求，同步执行诊断流程，返回完整诊断结果
     *
     * @param request 诊断请求对象，包含告警信息、会话ID等
     * @return 诊断结果Map，包含sessionId、requestId、finalReport等
     */
    @PostMapping("/start")
    public Mono<ResponseEntity<Map<String, Object>>> startDiagnosis(
            @Valid @RequestBody DiagnosisRequest request) {
        
        logger.info("收到诊断请求: type={}, problem={}", 
                request.getDiagnosisType(), 
                request.getProblem() != null && request.getProblem().length() > 50 ? 
                        request.getProblem().substring(0, 50) + "..." : request.getProblem());
        
        return orchestratorService.startDiagnosis(request)
                .map(result -> {
                    result.put("success", true);
                    return ResponseEntity.ok(result);
                })
                .onErrorResume(e -> {
                    logger.error("诊断执行失败: {}", e.getMessage(), e);
                    return Mono.just(ResponseEntity.internalServerError()
                            .body(Map.of(
                                    "success", false,
                                    "error", e.getMessage()
                            )));
                });
    }

    /**
     * 启动诊断任务（异步接口）
     * <p>
     * 功能说明：
     * 接收诊断请求，异步启动诊断流程，立即返回任务ID
     *
     * @param request 诊断请求对象
     * @return 任务信息Map，包含sessionId、requestId、status等
     */
    @PostMapping("/start-async")
    public Mono<ResponseEntity<Map<String, Object>>> startDiagnosisAsync(
            @Valid @RequestBody DiagnosisRequest request) {
        
        logger.info("收到异步诊断请求: type={}", request.getDiagnosisType());
        
        return orchestratorService.startDiagnosisAsync(request)
                .map(result -> {
                    result.put("success", true);
                    return ResponseEntity.accepted().body(result);
                })
                .onErrorResume(e -> {
                    logger.error("异步诊断启动失败: {}", e.getMessage(), e);
                    return Mono.just(ResponseEntity.internalServerError()
                            .body(Map.of(
                                    "success", false,
                                    "error", e.getMessage()
                            )));
                });
    }

    /**
     * SSE流式诊断接口
     * <p>
     * 功能说明：
     * 使用Server-Sent Events协议，实时推送诊断过程和结果
     *
     * @param request 诊断请求对象
     * @return 诊断过程事件流，包含各阶段的执行结果
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Map<String, Object>> streamDiagnosis(
            @Valid @RequestBody DiagnosisRequest request) {
        
        logger.info("收到流式诊断请求: type={}", request.getDiagnosisType());
        
        return orchestratorService.startDiagnosisStream(request)
                .onErrorResume(e -> {
                    logger.error("流式诊断失败: {}", e.getMessage(), e);
                    return Flux.just(Map.of(
                            "type", "error",
                            "error", e.getMessage()
                    ));
                });
    }

    /**
     * 查询诊断任务状态
     * <p>
     * 功能说明：
     * 根据会话ID查询诊断任务的当前状态和进度
     *
     * @param sessionId 会话ID
     * @return 任务状态Map，包含status、progress、currentLayer等
     */
    @GetMapping("/status/{sessionId}")
    public Mono<ResponseEntity<Map<String, Object>>> getDiagnosisStatus(
            @PathVariable String sessionId) {
        
        logger.debug("查询诊断状态: sessionId={}", sessionId);
        
        return orchestratorService.getDiagnosisStatus(sessionId)
                .map(result -> {
                    if (Boolean.TRUE.equals(result.get("success"))) {
                        return ResponseEntity.ok(result);
                    } else {
                        return ResponseEntity.notFound().build();
                    }
                })
                .onErrorResume(e -> {
                    logger.error("查询状态失败: sessionId={}, error={}", sessionId, e.getMessage());
                    return Mono.just(ResponseEntity.internalServerError()
                            .body(Map.of(
                                    "success", false,
                                    "error", e.getMessage()
                            )));
                });
    }

    /**
     * 获取诊断结果
     * <p>
     * 功能说明：
     * 根据会话ID获取完整的诊断结果
     *
     * @param sessionId 会话ID
     * @return 诊断结果Map，包含所有层级的执行结果和最终报告
     */
    @GetMapping("/result/{sessionId}")
    public Mono<ResponseEntity<Map<String, Object>>> getDiagnosisResult(
            @PathVariable String sessionId) {
        
        logger.debug("获取诊断结果: sessionId={}", sessionId);
        
        return orchestratorService.getDiagnosisResult(sessionId)
                .map(result -> {
                    if (result.containsKey("error")) {
                        return ResponseEntity.notFound().build();
                    }
                    result.put("success", true);
                    return ResponseEntity.ok(result);
                })
                .onErrorResume(e -> {
                    logger.error("获取结果失败: sessionId={}, error={}", sessionId, e.getMessage());
                    return Mono.just(ResponseEntity.internalServerError()
                            .body(Map.of(
                                    "success", false,
                                    "error", e.getMessage()
                            )));
                });
    }

    /**
     * 停止诊断任务
     * <p>
     * 功能说明：
     * 强制停止正在执行的诊断任务
     *
     * @param sessionId 会话ID
     * @return 操作结果Map，包含success、message等
     */
    @PostMapping("/stop/{sessionId}")
    public Mono<ResponseEntity<Map<String, Object>>> stopDiagnosis(
            @PathVariable String sessionId) {
        
        logger.info("停止诊断任务: sessionId={}", sessionId);
        
        return orchestratorService.stopDiagnosis(sessionId)
                .map(result -> {
                    if (Boolean.TRUE.equals(result.get("success"))) {
                        return ResponseEntity.ok(result);
                    } else {
                        return ResponseEntity.badRequest().body(result);
                    }
                })
                .onErrorResume(e -> {
                    logger.error("停止诊断失败: sessionId={}, error={}", sessionId, e.getMessage());
                    return Mono.just(ResponseEntity.internalServerError()
                            .body(Map.of(
                                    "success", false,
                                    "error", e.getMessage()
                            )));
                });
    }
    
    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, Object>>> healthCheck() {
        return Mono.just(ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "diagnosis-service"
        )));
    }
}
