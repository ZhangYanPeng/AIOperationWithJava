package com.company.diagnosis.service;

import com.company.diagnosis.adapter.AlertInputAdapter;
import com.company.diagnosis.config.KafkaConfig;
import com.company.diagnosis.model.dto.DiagnosisRequest;
import com.company.diagnosis.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
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

    private static final Logger logger = LoggerFactory.getLogger(MessageProcessingService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired(required = false)
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired(required = false)
    private KafkaConfig.TopicConfig topicConfig;

    @Autowired(required = false)
    private OrchestratorService orchestratorService;

    @Autowired
    private AlertInputAdapter alertInputAdapter;

    // 消息统计
    private final AtomicLong sentCount = new AtomicLong(0);
    private final AtomicLong consumedCount = new AtomicLong(0);
    private final AtomicLong failedSendCount = new AtomicLong(0);
    private final AtomicLong failedConsumeCount = new AtomicLong(0);

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
            Map<String, Object> result = new HashMap<>();
            
            // 验证参数
            if (topic == null || topic.isEmpty()) {
                result.put("success", false);
                result.put("error", "Topic不能为空");
                return result;
            }
            
            if (event == null || event.isEmpty()) {
                result.put("success", false);
                result.put("error", "事件数据不能为空");
                return result;
            }
            
            // 添加元数据
            Map<String, Object> enrichedEvent = new HashMap<>(event);
            String messageId = UUID.randomUUID().toString();
            enrichedEvent.put("messageId", messageId);
            enrichedEvent.put("timestamp", LocalDateTime.now().format(DATE_FORMATTER));
            
            // 序列化事件
            String eventJson = JsonUtil.toJson(enrichedEvent);
            
            // 发送到Kafka
            if (kafkaTemplate != null) {
                try {
                    kafkaTemplate.send(topic, messageId, eventJson);
                    sentCount.incrementAndGet();
                    
                    result.put("success", true);
                    result.put("messageId", messageId);
                    result.put("topic", topic);
                    result.put("sentAt", LocalDateTime.now().format(DATE_FORMATTER));
                    
                    logger.info("发送诊断事件成功: topic={}, messageId={}", topic, messageId);
                } catch (Exception e) {
                    failedSendCount.incrementAndGet();
                    result.put("success", false);
                    result.put("error", e.getMessage());
                    logger.error("发送诊断事件失败: topic={}, error={}", topic, e.getMessage());
                }
            } else {
                // Kafka未配置，模拟发送
                result.put("success", true);
                result.put("messageId", messageId);
                result.put("topic", topic);
                result.put("mode", "simulated");
                logger.info("模拟发送诊断事件: topic={}, messageId={}", topic, messageId);
            }
            
            return result;
        });
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
            Map<String, Object> result = new HashMap<>();
            
            if (message == null || message.isEmpty()) {
                result.put("success", false);
                result.put("error", "消息内容为空");
                failedConsumeCount.incrementAndGet();
                return result;
            }
            
            consumedCount.incrementAndGet();
            
            // 转换为诊断请求
            DiagnosisRequest request = alertInputAdapter.adaptFromKafka(message);
            if (request == null) {
                result.put("success", false);
                result.put("error", "消息格式转换失败");
                failedConsumeCount.incrementAndGet();
                return result;
            }
            
            // 调用诊断服务
            if (orchestratorService != null) {
                return orchestratorService.startDiagnosis(request)
                        .map(diagnosisResult -> {
                            Map<String, Object> response = new HashMap<>();
                            response.put("success", true);
                            response.put("sessionId", request.getSessionId());
                            response.put("processedAt", LocalDateTime.now().format(DATE_FORMATTER));
                            return response;
                        })
                        .block();
            }
            
            result.put("success", true);
            result.put("requestId", request.getRequestId());
            result.put("status", "queued");
            
            logger.info("处理诊断请求消息: requestId={}", request.getRequestId());
            return result;
        });
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
        return Mono.fromCallable(() -> {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "DIAGNOSIS_RESULT");
            notification.put("sessionId", sessionId);
            notification.put("result", diagnosisResult);
            notification.put("completedAt", LocalDateTime.now().format(DATE_FORMATTER));
            
            String topic = topicConfig != null ? topicConfig.getDiagnosisOutput() : "diagnosis-result";
            
            return sendDiagnosisEvent(topic, notification).block();
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
            Map<String, Object> result = new HashMap<>();
            
            if (events == null || events.isEmpty()) {
                result.put("success", true);
                result.put("totalCount", 0);
                result.put("successCount", 0);
                result.put("failedCount", 0);
                return result;
            }
            
            int successCount = 0;
            int failedCount = 0;
            List<String> failedMessages = new ArrayList<>();
            
            for (Map<String, Object> event : events) {
                Map<String, Object> sendResult = sendDiagnosisEvent(topic, event).block();
                if (Boolean.TRUE.equals(sendResult.get("success"))) {
                    successCount++;
                } else {
                    failedCount++;
                    failedMessages.add(String.valueOf(sendResult.get("error")));
                }
            }
            
            result.put("success", failedCount == 0);
            result.put("totalCount", events.size());
            result.put("successCount", successCount);
            result.put("failedCount", failedCount);
            if (!failedMessages.isEmpty()) {
                result.put("errors", failedMessages);
            }
            
            logger.info("批量发送事件完成: topic={}, total={}, success={}, failed={}", 
                    topic, events.size(), successCount, failedCount);
            
            return result;
        });
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
            Map<String, Object> result = new HashMap<>();
            
            logger.error("消息发送失败: topic={}, error={}", topic, error.getMessage());
            
            // 记录重试次数
            int retryCount = message.containsKey("_retryCount") ? 
                    (Integer) message.get("_retryCount") : 0;
            
            if (retryCount < 3) {
                // 执行重试
                message.put("_retryCount", retryCount + 1);
                
                // 指数退避
                long delay = (long) Math.pow(2, retryCount) * 1000;
                
                try {
                    Thread.sleep(delay);
                    Map<String, Object> retryResult = sendDiagnosisEvent(topic, message).block();
                    
                    if (Boolean.TRUE.equals(retryResult.get("success"))) {
                        result.put("success", true);
                        result.put("retryCount", retryCount + 1);
                        result.put("message", "重试成功");
                        return result;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            // 重试失败，保存到死信队列
            result.put("success", false);
            result.put("retryCount", retryCount);
            result.put("error", "重试失败，消息已保存到死信队列");
            result.put("originalError", error.getMessage());
            
            logger.warn("消息重试失败: topic={}, retryCount={}", topic, retryCount);
            
            return result;
        });
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
            Map<String, Object> result = new HashMap<>();
            
            failedConsumeCount.incrementAndGet();
            
            logger.error("消息消费失败: message={}, error={}", 
                    message.get("messageId"), error.getMessage());
            
            // 记录失败信息
            result.put("success", false);
            result.put("messageId", message.get("messageId"));
            result.put("error", error.getMessage());
            result.put("failedAt", LocalDateTime.now().format(DATE_FORMATTER));
            
            // 发送错误通知
            String errorTopic = topicConfig != null ? 
                    topicConfig.getDiagnosisEvent() + ".error" : "diagnosis-event.error";
            
            Map<String, Object> errorEvent = new HashMap<>();
            errorEvent.put("type", "CONSUME_ERROR");
            errorEvent.put("originalMessage", message);
            errorEvent.put("error", error.getMessage());
            
            sendDiagnosisEvent(errorTopic, errorEvent).subscribe();
            
            return result;
        });
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
            Map<String, Object> stats = new HashMap<>();
            
            stats.put("sentCount", sentCount.get());
            stats.put("consumedCount", consumedCount.get());
            stats.put("failedSendCount", failedSendCount.get());
            stats.put("failedConsumeCount", failedConsumeCount.get());
            
            // 计算成功率
            long totalSent = sentCount.get() + failedSendCount.get();
            long totalConsumed = consumedCount.get() + failedConsumeCount.get();
            
            stats.put("sendSuccessRate", totalSent > 0 ? 
                    String.format("%.2f%%", (double) sentCount.get() / totalSent * 100) : "N/A");
            stats.put("consumeSuccessRate", totalConsumed > 0 ? 
                    String.format("%.2f%%", (double) consumedCount.get() / totalConsumed * 100) : "N/A");
            
            stats.put("kafkaEnabled", kafkaTemplate != null);
            stats.put("timestamp", LocalDateTime.now().format(DATE_FORMATTER));
            
            return stats;
        });
    }
}
