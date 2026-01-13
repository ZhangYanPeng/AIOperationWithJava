package com.company.diagnosis.service;

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
import java.time.LocalDateTime;
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

    private static final Logger logger = LoggerFactory.getLogger(SseConnectionManager.class);

    /**
     * 存储所有活跃连接的Map
     * key: sessionId, value: Sink
     */
    private final ConcurrentHashMap<String, Sinks.Many<Map<String, Object>>> connections = new ConcurrentHashMap<>();
    
    /**
     * 存储心跳任务的Map
     */
    private final ConcurrentHashMap<String, Disposable> heartbeatTasks = new ConcurrentHashMap<>();
    
    /**
     * 存储连接创建时间
     */
    private final ConcurrentHashMap<String, LocalDateTime> connectionTimes = new ConcurrentHashMap<>();
    
    /**
     * 连接统计
     */
    private final AtomicLong totalConnectionCount = new AtomicLong(0);
    
    @Value("${diagnosis.sse.heartbeat-interval:30000}")
    private long heartbeatInterval;
    
    @Value("${diagnosis.sse.timeout:600000}")
    private long connectionTimeout;

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
        // 如果已存在连接，先关闭旧连接
        if (connections.containsKey(sessionId)) {
            closeConnection(sessionId).subscribe();
        }
        
        // 创建新的Sink
        Sinks.Many<Map<String, Object>> sink = Sinks.many().multicast().onBackpressureBuffer(1000);
        connections.put(sessionId, sink);
        connectionTimes.put(sessionId, LocalDateTime.now());
        totalConnectionCount.incrementAndGet();
        
        logger.info("SSE连接注册成功: sessionId={}", sessionId);
        
        // 发送连接成功消息
        Map<String, Object> connectedMsg = new HashMap<>();
        connectedMsg.put("type", "connected");
        connectedMsg.put("sessionId", sessionId);
        connectedMsg.put("timestamp", LocalDateTime.now().toString());
        sink.tryEmitNext(connectedMsg);
        
        // 启动心跳任务
        startHeartbeat(sessionId);
        
        // 返回事件流，添加超时和清理逻辑
        return sink.asFlux()
                .timeout(Duration.ofMillis(connectionTimeout))
                .doOnCancel(() -> {
                    logger.info("SSE连接被取消: sessionId={}", sessionId);
                    cleanupConnection(sessionId);
                })
                .doOnTerminate(() -> {
                    logger.info("SSE连接终止: sessionId={}", sessionId);
                    cleanupConnection(sessionId);
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
            Sinks.Many<Map<String, Object>> sink = connections.get(sessionId);
            if (sink == null) {
                logger.warn("SSE连接不存在: sessionId={}", sessionId);
                return false;
            }
            
            // 添加时间戳
            Map<String, Object> msgWithTimestamp = new HashMap<>(message);
            msgWithTimestamp.put("timestamp", LocalDateTime.now().toString());
            
            Sinks.EmitResult result = sink.tryEmitNext(msgWithTimestamp);
            if (result.isFailure()) {
                logger.warn("SSE消息发送失败: sessionId={}, result={}", sessionId, result);
                return false;
            }
            
            logger.debug("SSE消息发送成功: sessionId={}, type={}", sessionId, message.get("type"));
            return true;
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
        return Mono.fromCallable(() -> {
            int successCount = 0;
            int failCount = 0;
            
            for (Map.Entry<String, Sinks.Many<Map<String, Object>>> entry : connections.entrySet()) {
                Map<String, Object> msgWithTimestamp = new HashMap<>(message);
                msgWithTimestamp.put("timestamp", LocalDateTime.now().toString());
                
                Sinks.EmitResult result = entry.getValue().tryEmitNext(msgWithTimestamp);
                if (result.isSuccess()) {
                    successCount++;
                } else {
                    failCount++;
                }
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("totalConnections", connections.size());
            result.put("successCount", successCount);
            result.put("failCount", failCount);
            
            logger.info("SSE广播完成: 成功={}, 失败={}", successCount, failCount);
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
            Sinks.Many<Map<String, Object>> sink = connections.remove(sessionId);
            if (sink == null) {
                return false;
            }
            
            // 发送关闭消息
            Map<String, Object> closeMsg = new HashMap<>();
            closeMsg.put("type", "disconnected");
            closeMsg.put("sessionId", sessionId);
            closeMsg.put("timestamp", LocalDateTime.now().toString());
            sink.tryEmitNext(closeMsg);
            
            // 完成流
            sink.tryEmitComplete();
            
            // 清理资源
            cleanupConnection(sessionId);
            
            logger.info("SSE连接关闭成功: sessionId={}", sessionId);
            return true;
        });
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
        Sinks.Many<Map<String, Object>> sink = connections.get(sessionId);
        return sink != null && sink.currentSubscriberCount() > 0;
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
            stats.put("totalConnections", totalConnectionCount.get());
            stats.put("heartbeatInterval", heartbeatInterval);
            stats.put("connectionTimeout", connectionTimeout);
            
            // 连接详情
            Map<String, Object> connectionDetails = new HashMap<>();
            connectionTimes.forEach((sessionId, time) -> {
                connectionDetails.put(sessionId, Map.of(
                        "connectedAt", time.toString(),
                        "active", isConnectionActive(sessionId)
                ));
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
        Map<String, Object> heartbeatMsg = new HashMap<>();
        heartbeatMsg.put("type", "heartbeat");
        heartbeatMsg.put("sessionId", sessionId);
        
        return sendToSession(sessionId, heartbeatMsg);
    }

    /**
     * 清理所有连接
     * <p>
     * 功能说明：
     * 关闭所有SSE连接，用于系统关闭时的清理
     *
     * @return 清理结果
     */
    @PreDestroy
    public Mono<Map<String, Object>> closeAllConnections() {
        return Mono.fromCallable(() -> {
            int closedCount = 0;
            
            for (String sessionId : connections.keySet()) {
                closeConnection(sessionId).subscribe();
                closedCount++;
            }
            
            // 取消所有心跳任务
            heartbeatTasks.values().forEach(Disposable::dispose);
            heartbeatTasks.clear();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("closedConnections", closedCount);
            
            logger.info("所有SSE连接已关闭: count={}", closedCount);
            return result;
        });
    }
    
    /**
     * 启动心跳任务
     */
    private void startHeartbeat(String sessionId) {
        Disposable heartbeat = Flux.interval(Duration.ofMillis(heartbeatInterval))
                .flatMap(tick -> sendHeartbeat(sessionId))
                .subscribe(
                        success -> {
                            if (!success) {
                                logger.debug("心跳发送失败，连接可能已断开: sessionId={}", sessionId);
                            }
                        },
                        error -> logger.error("心跳任务异常: sessionId={}, error={}", sessionId, error.getMessage())
                );
        
        heartbeatTasks.put(sessionId, heartbeat);
    }
    
    /**
     * 清理连接资源
     */
    private void cleanupConnection(String sessionId) {
        // 取消心跳任务
        Disposable heartbeat = heartbeatTasks.remove(sessionId);
        if (heartbeat != null) {
            heartbeat.dispose();
        }
        
        // 移除连接时间记录
        connectionTimes.remove(sessionId);
        
        // 确保从连接Map中移除
        connections.remove(sessionId);
    }
    
    /**
     * 获取活跃连接数
     *
     * @return 活跃连接数
     */
    public int getActiveConnectionCount() {
        return connections.size();
    }
}
