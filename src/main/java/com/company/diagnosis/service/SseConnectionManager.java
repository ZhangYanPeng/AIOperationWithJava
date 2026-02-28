package com.company.diagnosis.service;

import com.company.diagnosis.model.event.DiagnosisEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SSE连接管理器
 * <p>
 * 职责：
 * 1. 管理所有活跃的SSE连接
 * 2. 提供消息推送接口
 * 3. 处理连接的创建、维护和关闭
 * 4. 实现连接的心跳机制
 * <p>
 * 设计考虑：
 * - 使用Sinks实现消息广播
 * - 支持多客户端并发连接
 * - 提供连接状态管理
 * - 实现优雅断开和资源清理
 *
 * @author Diagnosis System
 * @date 2026-01-13
 */
@Service
public class SseConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(SseConnectionManager.class);

    /**
     * 存储所有活跃连接的Map
     * key: sessionId, value: ConnectionInfo
     */
    private final ConcurrentHashMap<String, ConnectionInfo> connections = new ConcurrentHashMap<>();

    /**
     * 存储心跳任务的Map
     */
    private final ConcurrentHashMap<String, Disposable> heartbeatTasks = new ConcurrentHashMap<>();

    /**
     * 连接统计
     */
    private final AtomicLong totalConnections = new AtomicLong(0);
    private final AtomicLong totalMessages = new AtomicLong(0);

    @Value("${diagnosis.sse.heartbeat-interval:30000}")
    private long heartbeatInterval;

    @Value("${diagnosis.sse.timeout:600000}")
    private long connectionTimeout;

    /**
     * 连接信息内部类
     */
    private static class ConnectionInfo {
        final Sinks.Many<Map<String, Object>> sink;
        final Instant connectedAt;
        Instant lastActivityAt;

        ConnectionInfo(Sinks.Many<Map<String, Object>> sink) {
            this.sink = sink;
            this.connectedAt = Instant.now();
            this.lastActivityAt = Instant.now();
        }

        void updateActivity() {
            this.lastActivityAt = Instant.now();
        }
    }

    /**
     * 注册新的SSE连接
     * <p>
     * 功能说明：
     * 为指定会话创建并注册一个新的SSE连接
     *
     * @param sessionId 会话ID
     * @return SSE事件流
     */
    public Flux<Map<String, Object>> registerConnection(String sessionId) {
        log.info("注册SSE连接: sessionId={}", sessionId);

        // 1. 如果已存在连接,先关闭旧连接
        if (connections.containsKey(sessionId)) {
            log.info("关闭已存在的连接: sessionId={}", sessionId);
            closeConnectionInternal(sessionId);
        }

        // 2. 创建Sinks.Many
        Sinks.Many<Map<String, Object>> sink = Sinks.many().multicast().onBackpressureBuffer(256);

        // 3. 存储到connections Map
        ConnectionInfo connectionInfo = new ConnectionInfo(sink);
        connections.put(sessionId, connectionInfo);
        totalConnections.incrementAndGet();

        // 4. 发送连接成功消息
        Map<String, Object> connectedEvent = DiagnosisEvent.connected(sessionId).toMap();
        sink.tryEmitNext(connectedEvent);

        // 5. 启动心跳任务
        startHeartbeat(sessionId);

        log.info("SSE连接注册成功: sessionId={}, 当前活跃连接数={}", sessionId, connections.size());

        // 6. 返回Flux,带有超时和清理逻辑
        return sink.asFlux()
                .timeout(Duration.ofMillis(connectionTimeout))
                .doOnCancel(() -> {
                    log.info("SSE连接取消: sessionId={}", sessionId);
                    closeConnectionInternal(sessionId);
                })
                .doOnError(error -> {
                    log.warn("SSE连接错误: sessionId={}, error={}", sessionId, error.getMessage());
                    closeConnectionInternal(sessionId);
                })
                .doOnComplete(() -> {
                    log.info("SSE连接完成: sessionId={}", sessionId);
                    closeConnectionInternal(sessionId);
                });
    }

    /**
     * 向指定会话发送消息
     * <p>
     * 功能说明：
     * 向指定会话的所有SSE连接推送消息
     *
     * @param sessionId 会话ID
     * @param message 消息内容
     * @return 发送结果
     */
    public Mono<Boolean> sendToSession(String sessionId, Map<String, Object> message) {
        return Mono.fromCallable(() -> {
            ConnectionInfo connInfo = connections.get(sessionId);

            if (connInfo == null) {
                log.warn("发送消息失败: 连接不存在, sessionId={}", sessionId);
                return false;
            }

            // 更新最后活动时间
            connInfo.updateActivity();

            // 发送消息
            Sinks.EmitResult result = connInfo.sink.tryEmitNext(message);

            if (result.isSuccess()) {
                totalMessages.incrementAndGet();
                log.debug("消息发送成功: sessionId={}", sessionId);
                return true;
            } else {
                log.warn("消息发送失败: sessionId={}, result={}", sessionId, result);
                // 如果发送失败,可能连接已断开
                if (result == Sinks.EmitResult.FAIL_TERMINATED || result == Sinks.EmitResult.FAIL_CANCELLED) {
                    closeConnectionInternal(sessionId);
                }
                return false;
            }
        });
    }

    /**
     * 广播消息到所有连接
     * <p>
     * 功能说明：
     * 向所有活跃的SSE连接广播消息
     *
     * @param message 广播消息
     * @return 广播结果，包含接收者数量
     */
    public Mono<Map<String, Object>> broadcast(Map<String, Object> message) {
        return Flux.fromIterable(connections.keySet())
                .flatMap(sessionId -> sendToSession(sessionId, message)
                        .map(success -> success ? 1 : 0))
                .reduce(0, Integer::sum)
                .map(successCount -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("total", connections.size());
                    result.put("success", successCount);
                    result.put("failed", connections.size() - successCount);
                    result.put("timestamp", Instant.now().toString());

                    log.info("广播完成: total={}, success={}", connections.size(), successCount);
                    return result;
                });
    }

    /**
     * 关闭SSE连接
     * <p>
     * 功能说明：
     * 关闭指定会话的SSE连接并清理资源
     *
     * @param sessionId 会话ID
     * @return 关闭结果
     */
    public Mono<Boolean> closeConnection(String sessionId) {
        return Mono.fromCallable(() -> {
            return closeConnectionInternal(sessionId);
        });
    }

    /**
     * 内部关闭连接方法
     */
    private boolean closeConnectionInternal(String sessionId) {
        // 1. 停止心跳任务
        Disposable heartbeat = heartbeatTasks.remove(sessionId);
        if (heartbeat != null && !heartbeat.isDisposed()) {
            heartbeat.dispose();
        }

        // 2. 从connections移除并关闭Sink
        ConnectionInfo connInfo = connections.remove(sessionId);
        if (connInfo == null) {
            return false;
        }

        // 3. 发送关闭事件并完成Sink
        try {
            connInfo.sink.tryEmitComplete();
        } catch (Exception e) {
            log.warn("关闭Sink时发生异常: sessionId={}", sessionId, e);
        }

        log.info("SSE连接已关闭: sessionId={}, 当前活跃连接数={}", sessionId, connections.size());
        return true;
    }

    /**
     * 检查连接是否活跃
     * <p>
     * 功能说明：
     * 检查指定会话的连接是否存在且活跃
     *
     * @param sessionId 会话ID
     * @return 是否活跃
     */
    public Boolean isConnectionActive(String sessionId) {
        ConnectionInfo connInfo = connections.get(sessionId);
        if (connInfo == null) {
            return false;
        }

        // 检查连接是否超时
        long idleTime = Duration.between(connInfo.lastActivityAt, Instant.now()).toMillis();
        if (idleTime > connectionTimeout) {
            log.info("连接已超时: sessionId={}, idleTime={}ms", sessionId, idleTime);
            closeConnectionInternal(sessionId);
            return false;
        }

        return true;
    }

    /**
     * 获取所有活跃连接
     * <p>
     * 功能说明：
     * 获取当前所有活跃连接的会话ID列表
     *
     * @return 活跃连接的会话ID列表
     */
    public Flux<String> listActiveConnections() {
        return Flux.fromIterable(connections.keySet());
    }

    /**
     * 获取连接统计信息
     * <p>
     * 功能说明：
     * 获取SSE连接的统计信息
     *
     * @return 统计信息Map
     */
    public Mono<Map<String, Object>> getStatistics() {
        return Mono.fromCallable(() -> {
            Map<String, Object> stats = new HashMap<>();
            stats.put("activeConnections", connections.size());
            stats.put("totalConnections", totalConnections.get());
            stats.put("totalMessages", totalMessages.get());
            stats.put("heartbeatInterval", heartbeatInterval);
            stats.put("connectionTimeout", connectionTimeout);
            stats.put("timestamp", Instant.now().toString());

            // 连接详情
            Map<String, Object> connectionDetails = new HashMap<>();
            connections.forEach((sessionId, info) -> {
                Map<String, Object> detail = new HashMap<>();
                detail.put("connectedAt", info.connectedAt.toString());
                detail.put("lastActivityAt", info.lastActivityAt.toString());
                detail.put("idleMillis", Duration.between(info.lastActivityAt, Instant.now()).toMillis());
                connectionDetails.put(sessionId, detail);
            });
            stats.put("connections", connectionDetails);

            return stats;
        });
    }

    /**
     * 发送心跳消息
     * <p>
     * 功能说明：
     * 向指定会话发送心跳消息，保持连接活跃
     *
     * @param sessionId 会话ID
     * @return 心跳发送结果
     */
    public Mono<Boolean> sendHeartbeat(String sessionId) {
        Map<String, Object> heartbeatMessage = DiagnosisEvent.heartbeat(sessionId).toMap();
        return sendToSession(sessionId, heartbeatMessage);
    }

    /**
     * 启动心跳任务
     */
    private void startHeartbeat(String sessionId) {
        Disposable heartbeatTask = Flux.interval(Duration.ofMillis(heartbeatInterval))
                .flatMap(tick -> sendHeartbeat(sessionId))
                .subscribe(
                        success -> {
                            if (!success) {
                                log.warn("心跳发送失败,关闭连接: sessionId={}", sessionId);
                                closeConnectionInternal(sessionId);
                            }
                        },
                        error -> {
                            log.warn("心跳任务异常: sessionId={}", sessionId, error);
                            closeConnectionInternal(sessionId);
                        }
                );

        heartbeatTasks.put(sessionId, heartbeatTask);
        log.debug("心跳任务已启动: sessionId={}, interval={}ms", sessionId, heartbeatInterval);
    }

    /**
     * 清理所有连接
     * <p>
     * 功能说明：
     * 关闭所有SSE连接，用于系统关闭时的清理
     *
     * @return 清理结果
     */
    public Mono<Map<String, Object>> closeAllConnections() {
        return Mono.fromCallable(() -> {
            log.info("开始清理所有SSE连接: 当前连接数={}", connections.size());

            int closedCount = 0;
            for (String sessionId : connections.keySet()) {
                if (closeConnectionInternal(sessionId)) {
                    closedCount++;
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("closed", closedCount);
            result.put("timestamp", Instant.now().toString());

            log.info("SSE连接清理完成: closed={}", closedCount);
            return result;
        });
    }

    /**
     * 应用关闭时清理资源
     */
    @PreDestroy
    public void cleanup() {
        log.info("应用关闭,清理SSE连接...");
        closeAllConnections().block();
    }
}
