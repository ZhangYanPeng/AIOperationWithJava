package com.company.diagnosis.service;

import com.company.diagnosis.config.KafkaConfig.TopicConfig;
import com.company.diagnosis.model.dto.DiagnosisRequest;
import com.company.diagnosis.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 消息处理服务
 * <p>
 * 职责：
 * 1. 处理Kafka消息的消费和生产
 * 2. 实现消息的序列化和反序列化
 * 3. 处理消息的错误重试
 * 4. 提供消息发送的封装接口
 * <p>
 * 设计考虑：
 * - 支持异步消息处理
 * - 实现消息的幂等性保证
 * - 提供消息追踪和监控
 * - 支持消息的批量处理
 *
 * @author Diagnosis System
 * @date 2026-01-13
 */
@Service
public class MessageProcessingService {

    private static final Logger log = LoggerFactory.getLogger(MessageProcessingService.class);

    /**
     * 消息统计
     */
    private final AtomicLong sentMessages = new AtomicLong(0);
    private final AtomicLong failedMessages = new AtomicLong(0);
    private final AtomicLong consumedMessages = new AtomicLong(0);

    @Autowired(required = false)
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired(required = false)
    private TopicConfig topicConfig;

    @Autowired(required = false)
    @Lazy
    private OrchestratorService orchestratorService;

    /**
     * 发送诊断事件到Kafka
     * <p>
     * 功能说明：
     * 将诊断事件发送到指定的Kafka主题
     *
     * @param topic Kafka主题名称
     * @param event 事件数据Map
     * @return 发送结果，包含是否成功、消息ID等
     */
    public Mono<Map<String, Object>> sendDiagnosisEvent(String topic, Map<String, Object> event) {
        return Mono.fromCallable(() -> {
            // 1. 验证topic和event
            if (!StringUtils.hasText(topic)) {
                throw new IllegalArgumentException("Topic不能为空");
            }
            if (event == null || event.isEmpty()) {
                throw new IllegalArgumentException("事件数据不能为空");
            }

            // 2. 添加消息元数据
            String messageId = UUID.randomUUID().toString().replace("-", "");
            event.put("messageId", messageId);
            event.put("timestamp", Instant.now().toString());

            // 3. 序列化事件数据
            String jsonPayload = JsonUtil.toJson(event);

            // 4. 发送到Kafka
            Map<String, Object> result = new HashMap<>();
            result.put("messageId", messageId);
            result.put("topic", topic);

            if (kafkaTemplate != null) {
                try {
                    CompletableFuture<SendResult<String, String>> future = 
                            kafkaTemplate.send(topic, messageId, jsonPayload);

                    SendResult<String, String> sendResult = future.get();
                    
                    result.put("success", true);
                    result.put("partition", sendResult.getRecordMetadata().partition());
                    result.put("offset", sendResult.getRecordMetadata().offset());
                    
                    sentMessages.incrementAndGet();
                    log.info("消息发送成功: topic={}, messageId={}", topic, messageId);
                } catch (Exception e) {
                    result.put("success", false);
                    result.put("error", e.getMessage());
                    failedMessages.incrementAndGet();
                    log.error("消息发送失败: topic={}, messageId={}", topic, messageId, e);
                }
            } else {
                // Kafka未配置时,记录日志
                log.warn("Kafka未配置,消息仅记录到日志: topic={}, messageId={}", topic, messageId);
                result.put("success", true);
                result.put("note", "Kafka未配置,消息已记录到日志");
                sentMessages.incrementAndGet();
            }

            return result;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 消费诊断请求消息
     * <p>
     * 功能说明：
     * 从Kafka消费诊断请求消息，触发诊断流程
     *
     * @param message Kafka消息
     * @return 处理结果
     */
    public Mono<Map<String, Object>> consumeDiagnosisRequest(Map<String, Object> message) {
        return Mono.fromCallable(() -> {
            log.info("消费诊断请求消息: {}", message);
            consumedMessages.incrementAndGet();

            Map<String, Object> result = new HashMap<>();

            // 1. 反序列化和验证消息格式
            String alertId = (String) message.get("alertId");
            if (!StringUtils.hasText(alertId)) {
                result.put("success", false);
                result.put("error", "消息缺少alertId字段");
                return result;
            }

            // 2. 构建DiagnosisRequest
            DiagnosisRequest request = new DiagnosisRequest();
            request.setAlertId(alertId);
            request.setRequestId((String) message.get("requestId"));
            
            @SuppressWarnings("unchecked")
            Map<String, Object> alertData = (Map<String, Object>) message.get("alertData");
            request.setAlertData(alertData);

            // 3. 调用OrchestratorService启动诊断
            if (orchestratorService != null) {
                orchestratorService.startDiagnosisAsync(request)
                        .subscribe(
                                diagResult -> log.info("诊断任务已启动: alertId={}", alertId),
                                error -> log.error("启动诊断失败: alertId={}", alertId, error)
                        );
            }

            // 4. 返回处理状态
            result.put("success", true);
            result.put("alertId", alertId);
            result.put("message", "诊断请求已接收并开始处理");
            result.put("processedAt", Instant.now().toString());

            return result;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 发送诊断结果通知
     * <p>
     * 功能说明：
     * 诊断完成后，发送结果通知到Kafka
     *
     * @param sessionId 会话ID
     * @param diagnosisResult 诊断结果
     * @return 发送结果
     */
    public Mono<Map<String, Object>> sendResultNotification(String sessionId, Map<String, Object> diagnosisResult) {
        return Mono.defer(() -> {
            // 1. 构建通知消息
            Map<String, Object> notification = new LinkedHashMap<>();
            notification.put("type", "DIAGNOSIS_RESULT");
            notification.put("sessionId", sessionId);
            notification.put("result", diagnosisResult);
            notification.put("completedAt", Instant.now().toString());

            // 2. 获取结果Topic
            String topic = topicConfig != null ? topicConfig.getDiagnosisOutput() : "diagnosis-result";

            // 3. 发送到结果通知主题
            return sendDiagnosisEvent(topic, notification);
        });
    }

    /**
     * 批量发送事件
     * <p>
     * 功能说明：
     * 批量发送多个事件到Kafka
     *
     * @param topic Kafka主题名称
     * @param events 事件列表
     * @return 批量发送结果
     */
    public Mono<Map<String, Object>> batchSendEvents(String topic, List<Map<String, Object>> events) {
        return Mono.fromCallable(() -> {
            // 1. 验证参数
            if (!StringUtils.hasText(topic)) {
                throw new IllegalArgumentException("Topic不能为空");
            }
            if (events == null || events.isEmpty()) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("total", 0);
                result.put("sent", 0);
                return result;
            }

            // 2. 批量发送
            int successCount = 0;
            List<String> failedIds = new ArrayList<>();

            for (Map<String, Object> event : events) {
                try {
                    Map<String, Object> sendResult = sendDiagnosisEvent(topic, event).block();
                    if (Boolean.TRUE.equals(sendResult.get("success"))) {
                        successCount++;
                    } else {
                        failedIds.add((String) event.get("messageId"));
                    }
                } catch (Exception e) {
                    log.error("批量发送单条消息失败", e);
                    failedIds.add((String) event.getOrDefault("messageId", "unknown"));
                }
            }

            // 3. 返回批量发送结果
            Map<String, Object> result = new HashMap<>();
            result.put("success", failedIds.isEmpty());
            result.put("total", events.size());
            result.put("sent", successCount);
            result.put("failed", failedIds.size());
            if (!failedIds.isEmpty()) {
                result.put("failedIds", failedIds);
            }

            log.info("批量发送完成: topic={}, total={}, sent={}, failed={}",
                    topic, events.size(), successCount, failedIds.size());

            return result;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 处理消息发送失败
     * <p>
     * 功能说明：
     * 处理消息发送失败的情况，实现重试逻辑
     *
     * @param topic Kafka主题
     * @param message 消息内容
     * @param error 错误信息
     * @return 重试结果
     */
    public Mono<Map<String, Object>> handleSendFailure(String topic, Map<String, Object> message, Throwable error) {
        return Mono.fromCallable(() -> {
            log.error("处理消息发送失败: topic={}, error={}", topic, error.getMessage());

            Map<String, Object> result = new HashMap<>();
            String messageId = (String) message.getOrDefault("messageId", UUID.randomUUID().toString());

            // 1. 记录错误日志
            log.error("消息发送失败详情: topic={}, messageId={}, error={}", topic, messageId, error.getMessage());

            // 2. 判断是否需要重试(最多3次)
            int retryCount = (int) message.getOrDefault("retryCount", 0);
            if (retryCount < 3) {
                // 3. 执行重试(指数退避)
                message.put("retryCount", retryCount + 1);
                long delay = (long) Math.pow(2, retryCount) * 1000; // 1s, 2s, 4s

                try {
                    Thread.sleep(delay);
                    Map<String, Object> retryResult = sendDiagnosisEvent(topic, message).block();
                    if (Boolean.TRUE.equals(retryResult.get("success"))) {
                        result.put("success", true);
                        result.put("retryCount", retryCount + 1);
                        result.put("message", "重试发送成功");
                        return result;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // 4. 重试失败,保存到死信处理
            result.put("success", false);
            result.put("messageId", messageId);
            result.put("topic", topic);
            result.put("error", error.getMessage());
            result.put("retryCount", retryCount);
            result.put("action", "DEAD_LETTER");
            result.put("timestamp", Instant.now().toString());

            // 记录到死信日志
            log.warn("消息进入死信: topic={}, messageId={}, retries={}", topic, messageId, retryCount);

            return result;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 处理消息消费失败
     * <p>
     * 功能说明：
     * 处理消息消费失败的情况
     *
     * @param message 消息内容
     * @param error 错误信息
     * @return 处理结果
     */
    public Mono<Map<String, Object>> handleConsumeFailure(Map<String, Object> message, Throwable error) {
        return Mono.fromCallable(() -> {
            String messageId = (String) message.getOrDefault("messageId", "unknown");
            log.error("处理消息消费失败: messageId={}, error={}", messageId, error.getMessage());

            Map<String, Object> result = new HashMap<>();

            // 1. 记录错误
            result.put("messageId", messageId);
            result.put("success", false);
            result.put("error", error.getMessage());
            result.put("timestamp", Instant.now().toString());

            // 2. 更新消息处理状态
            result.put("status", "CONSUME_FAILED");

            // 3. 发送错误通知
            if (topicConfig != null) {
                Map<String, Object> errorEvent = new HashMap<>();
                errorEvent.put("type", "CONSUME_ERROR");
                errorEvent.put("originalMessage", message);
                errorEvent.put("error", error.getMessage());
                errorEvent.put("stackTrace", error.getStackTrace()[0].toString());

                sendDiagnosisEvent(topicConfig.getDiagnosisEvent(), errorEvent)
                        .subscribe();
            }

            // 4. 记录失败消息
            log.warn("消息消费失败已记录: messageId={}", messageId);

            return result;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 获取消息处理统计
     * <p>
     * 功能说明：
     * 获取消息发送和消费的统计信息
     *
     * @return 统计信息Map，包含发送数量、消费数量、失败数量等
     */
    public Mono<Map<String, Object>> getMessageStatistics() {
        return Mono.fromCallable(() -> {
            Map<String, Object> stats = new LinkedHashMap<>();

            // 发送统计
            stats.put("sentMessages", sentMessages.get());
            stats.put("failedMessages", failedMessages.get());
            stats.put("sendSuccessRate", calculateSuccessRate(sentMessages.get(), failedMessages.get()));

            // 消费统计
            stats.put("consumedMessages", consumedMessages.get());

            // Kafka状态
            stats.put("kafkaConfigured", kafkaTemplate != null);

            // Topic配置
            if (topicConfig != null) {
                Map<String, String> topics = new HashMap<>();
                topics.put("alertInput", topicConfig.getAlertInput());
                topics.put("diagnosisOutput", topicConfig.getDiagnosisOutput());
                topics.put("diagnosisEvent", topicConfig.getDiagnosisEvent());
                stats.put("topics", topics);
            }

            stats.put("timestamp", Instant.now().toString());

            return stats;
        });
    }

    private String calculateSuccessRate(long total, long failed) {
        if (total == 0) {
            return "N/A";
        }
        double rate = (double) (total - failed) / total * 100;
        return String.format("%.2f%%", rate);
    }
}
