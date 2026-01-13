package com.company.diagnosis.controller;

import com.company.diagnosis.service.SseConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
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
public class SseController {

    private static final Logger logger = LoggerFactory.getLogger(SseController.class);

    @Autowired
    private SseConnectionManager sseConnectionManager;

    /**
     * 建立SSE连接
     * <p>
     * 功能说明：
     * 建立SSE长连接，用于接收诊断过程的实时推送
     *
     * @param sessionId 会话ID
     * @return SSE事件流
     */
    @GetMapping(value = "/connect/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Map<String, Object>> connect(@PathVariable String sessionId) {
        // 验证sessionId
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return Flux.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "会话ID不能为空"));
        }
        
        logger.info("SSE连接请求: sessionId={}", sessionId);
        
        // 注册连接并返回事件流
        return sseConnectionManager.registerConnection(sessionId)
                .doOnSubscribe(subscription -> logger.info("SSE连接已建立: sessionId={}", sessionId))
                .doOnCancel(() -> logger.info("SSE连接被取消: sessionId={}", sessionId))
                .doOnError(e -> logger.error("SSE连接错误: sessionId={}, error={}", sessionId, e.getMessage()));
    }

    /**
     * 向指定会话推送消息
     * <p>
     * 功能说明：
     * 向指定会话的所有SSE连接推送消息
     *
     * @param sessionId 会话ID
     * @param message 消息内容
     * @return 推送结果Map
     */
    @PostMapping("/send/{sessionId}")
    public Mono<Map<String, Object>> sendMessage(
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> message) {
        // 验证参数
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "会话ID不能为空"));
        }
        
        if (message == null || message.isEmpty()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "消息内容不能为空"));
        }
        
        return sseConnectionManager.sendToSession(sessionId, message)
                .map(success -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", success);
                    result.put("sessionId", sessionId);
                    result.put("timestamp", LocalDateTime.now().toString());
                    if (!success) {
                        result.put("error", "连接不存在或发送失败");
                    }
                    return result;
                });
    }

    /**
     * 广播消息到所有连接
     * <p>
     * 功能说明：
     * 向所有活跃的SSE连接广播消息
     *
     * @param message 广播消息内容
     * @return 广播结果Map，包含接收者数量等
     */
    @PostMapping("/broadcast")
    public Mono<Map<String, Object>> broadcast(@RequestBody Map<String, Object> message) {
        // 验证消息格式
        if (message == null || message.isEmpty()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "广播消息不能为空"));
        }
        
        return sseConnectionManager.broadcast(message)
                .doOnSuccess(result -> logger.info("广播消息完成: 成功={}", result.get("successCount")));
    }

    /**
     * 关闭SSE连接
     * <p>
     * 功能说明：
     * 主动关闭指定会话的SSE连接
     *
     * @param sessionId 会话ID
     * @return 关闭结果Map
     */
    @PostMapping("/close/{sessionId}")
    public Mono<Map<String, Object>> closeConnection(@PathVariable String sessionId) {
        // 验证sessionId
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "会话ID不能为空"));
        }
        
        return sseConnectionManager.closeConnection(sessionId)
                .map(success -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", success);
                    result.put("sessionId", sessionId);
                    result.put("closedAt", LocalDateTime.now().toString());
                    if (!success) {
                        result.put("message", "连接不存在");
                    }
                    return result;
                })
                .doOnSuccess(result -> logger.info("关闭SSE连接: sessionId={}, success={}", 
                        sessionId, result.get("success")));
    }

    /**
     * 获取连接状态
     * <p>
     * 功能说明：
     * 查询指定会话的SSE连接状态
     *
     * @param sessionId 会话ID
     * @return 连接状态Map，包含isConnected、connectTime等
     */
    @GetMapping("/status/{sessionId}")
    public Mono<Map<String, Object>> getConnectionStatus(@PathVariable String sessionId) {
        // 验证sessionId
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "会话ID不能为空"));
        }
        
        return Mono.fromCallable(() -> {
            Map<String, Object> status = new HashMap<>();
            status.put("sessionId", sessionId);
            status.put("isConnected", sseConnectionManager.isConnectionActive(sessionId));
            status.put("checkedAt", LocalDateTime.now().toString());
            return status;
        });
    }

    /**
     * 获取所有活跃连接
     * <p>
     * 功能说明：
     * 获取当前所有活跃的SSE连接信息
     *
     * @return 连接列表，每项包含sessionId、connectTime等
     */
    @GetMapping("/connections")
    public Flux<Map<String, Object>> listActiveConnections() {
        return sseConnectionManager.listActiveConnections()
                .map(sessionId -> {
                    Map<String, Object> connection = new HashMap<>();
                    connection.put("sessionId", sessionId);
                    connection.put("active", sseConnectionManager.isConnectionActive(sessionId));
                    return connection;
                });
    }

    /**
     * 获取连接统计信息
     * <p>
     * 功能说明：
     * 获取SSE连接的统计信息
     *
     * @return 统计信息Map，包含总连接数、活跃数等
     */
    @GetMapping("/statistics")
    public Mono<Map<String, Object>> getStatistics() {
        return sseConnectionManager.getStatistics()
                .doOnSuccess(stats -> logger.debug("获取SSE统计: activeConnections={}", 
                        stats.get("activeConnections")));
    }

    /**
     * 发送心跳
     * <p>
     * 功能说明：
     * 手动触发向指定会话发送心跳
     *
     * @param sessionId 会话ID
     * @return 心跳发送结果
     */
    @PostMapping("/heartbeat/{sessionId}")
    public Mono<Map<String, Object>> sendHeartbeat(@PathVariable String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "会话ID不能为空"));
        }
        
        return sseConnectionManager.sendHeartbeat(sessionId)
                .map(success -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", success);
                    result.put("sessionId", sessionId);
                    result.put("timestamp", LocalDateTime.now().toString());
                    return result;
                });
    }

    /**
     * 关闭所有连接
     * <p>
     * 功能说明：
     * 关闭所有SSE连接（管理员操作）
     *
     * @return 关闭结果
     */
    @PostMapping("/close-all")
    public Mono<Map<String, Object>> closeAllConnections() {
        logger.warn("关闭所有SSE连接");
        return sseConnectionManager.closeAllConnections();
    }
}
