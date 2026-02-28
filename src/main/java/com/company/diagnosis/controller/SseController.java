package com.company.diagnosis.controller;

import com.company.diagnosis.service.SseConnectionManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * SSE（Server-Sent Events）流式输出控制器
 * <p>
 * 职责：
 * 1. 管理SSE连接的生命周期
 * 2. 提供流式数据推送接口
 * 3. 支持多客户端并发连接
 * 4. 处理连接异常和重连
 * <p>
 * 设计考虑：
 * - 使用Flux实现流式输出
 * - 支持心跳保持连接
 * - 提供连接状态查询
 * - 优雅处理连接断开
 *
 * @author Diagnosis System
 * @date 2026-01-13
 */
@RestController
@RequestMapping("/api/v1/sse")
@Tag(name = "SSE连接管理", description = "管理Server-Sent Events连接")
public class SseController {

    private static final Logger log = LoggerFactory.getLogger(SseController.class);

    @Autowired
    private SseConnectionManager sseConnectionManager;

    /**
     * 建立SSE连接
     */
    @GetMapping(value = "/connect/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "建立SSE连接", description = "建立SSE长连接,用于接收诊断过程的实时推送")
    public Flux<Map<String, Object>> connect(@PathVariable String sessionId) {
        log.info("建立SSE连接: sessionId={}", sessionId);

        if (!StringUtils.hasText(sessionId)) {
            Map<String, Object> error = new HashMap<>();
            error.put("type", "error");
            error.put("error", "会话ID不能为空");
            return Flux.just(error);
        }

        return sseConnectionManager.registerConnection(sessionId)
                .doOnSubscribe(s -> log.info("SSE连接已建立: sessionId={}", sessionId))
                .doOnCancel(() -> log.info("SSE连接已取消: sessionId={}", sessionId))
                .doOnComplete(() -> log.info("SSE连接已完成: sessionId={}", sessionId));
    }

    /**
     * 向指定会话推送消息
     */
    @PostMapping("/send/{sessionId}")
    @Operation(summary = "发送消息", description = "向指定会话的所有SSE连接推送消息")
    public Mono<Map<String, Object>> sendMessage(
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> message) {
        log.info("发送SSE消息: sessionId={}", sessionId);

        if (!StringUtils.hasText(sessionId)) {
            return Mono.just(errorResponse("会话ID不能为空"));
        }

        if (message == null || message.isEmpty()) {
            return Mono.just(errorResponse("消息内容不能为空"));
        }

        return sseConnectionManager.sendToSession(sessionId, message)
                .map(success -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", success);
                    result.put("sessionId", sessionId);
                    result.put("timestamp", Instant.now().toString());
                    return result;
                });
    }

    /**
     * 广播消息到所有连接
     */
    @PostMapping("/broadcast")
    @Operation(summary = "广播消息", description = "向所有活跃的SSE连接广播消息")
    public Mono<Map<String, Object>> broadcast(@RequestBody Map<String, Object> message) {
        log.info("广播SSE消息");

        if (message == null || message.isEmpty()) {
            return Mono.just(errorResponse("消息内容不能为空"));
        }

        return sseConnectionManager.broadcast(message);
    }

    /**
     * 关闭SSE连接
     */
    @PostMapping("/close/{sessionId}")
    @Operation(summary = "关闭连接", description = "主动关闭指定会话的SSE连接")
    public Mono<Map<String, Object>> closeConnection(@PathVariable String sessionId) {
        log.info("关闭SSE连接: sessionId={}", sessionId);

        if (!StringUtils.hasText(sessionId)) {
            return Mono.just(errorResponse("会话ID不能为空"));
        }

        return sseConnectionManager.closeConnection(sessionId)
                .map(success -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", success);
                    result.put("sessionId", sessionId);
                    result.put("message", success ? "连接已关闭" : "连接不存在");
                    result.put("timestamp", Instant.now().toString());
                    return result;
                });
    }

    /**
     * 获取连接状态
     */
    @GetMapping("/status/{sessionId}")
    @Operation(summary = "获取连接状态", description = "查询指定会话的SSE连接状态")
    public Mono<Map<String, Object>> getConnectionStatus(@PathVariable String sessionId) {
        log.info("查询SSE连接状态: sessionId={}", sessionId);

        if (!StringUtils.hasText(sessionId)) {
            return Mono.just(errorResponse("会话ID不能为空"));
        }

        Map<String, Object> status = new HashMap<>();
        status.put("sessionId", sessionId);
        status.put("isConnected", sseConnectionManager.isConnectionActive(sessionId));
        status.put("timestamp", Instant.now().toString());

        return Mono.just(status);
    }

    /**
     * 获取所有活跃连接
     */
    @GetMapping("/connections")
    @Operation(summary = "获取活跃连接列表", description = "获取当前所有活跃的SSE连接信息")
    public Flux<Map<String, Object>> listActiveConnections() {
        log.info("获取活跃SSE连接列表");

        return sseConnectionManager.listActiveConnections()
                .map(sessionId -> {
                    Map<String, Object> conn = new HashMap<>();
                    conn.put("sessionId", sessionId);
                    conn.put("isActive", true);
                    return conn;
                });
    }

    /**
     * 获取连接统计信息
     */
    @GetMapping("/statistics")
    @Operation(summary = "获取连接统计", description = "获取SSE连接的统计信息")
    public Mono<Map<String, Object>> getStatistics() {
        log.info("获取SSE连接统计");
        return sseConnectionManager.getStatistics();
    }

    private Map<String, Object> errorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        response.put("timestamp", Instant.now().toString());
        return response;
    }
}
