package com.company.diagnosis.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka配置类
 * 
 * 功能描述:
 * - 配置Kafka生产者和消费者
 * - 配置消息监听容器
 * - 提供Kafka操作Bean
 * 
 * 设计考虑:
 * - 支持手动提交offset
 * - 支持串行处理消息
 * - 支持异常处理和重试
 * - 配置合理的超时和批量参数
 * 
 * @author System
 * @since 2026-01-13
 */
@Configuration
@EnableKafka
@ConfigurationProperties(prefix = "spring.kafka")
@Data
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    /**
     * Kafka服务器地址
     */
    private String bootstrapServers = "localhost:9092";

    /**
     * 消费者配置
     */
    private ConsumerProperties consumer = new ConsumerProperties();

    /**
     * 生产者配置
     */
    private ProducerProperties producer = new ProducerProperties();

    /**
     * Topic配置
     */
    private TopicConfig topics = new TopicConfig();

    /**
     * 创建Kafka消费者工厂
     * 
     * 功能说明:
     * 1. 配置消费者基础参数
     * 2. 配置反序列化器
     * 3. 配置消费者组
     * 4. 配置offset策略
     * 
     * @return ConsumerFactory Kafka消费者工厂
     */
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        
        // 基础配置
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        
        // 消费者组配置
        config.put(ConsumerConfig.GROUP_ID_CONFIG, 
                consumer.getGroupId() != null ? consumer.getGroupId() : "diagnosis-agent-group");
        
        // Offset策略: latest表示从最新消息开始消费
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, 
                consumer.getAutoOffsetReset() != null ? consumer.getAutoOffsetReset() : "latest");
        
        // 禁用自动提交,使用手动提交确保消息处理完成后才确认
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        
        // 每次poll最多获取1条消息,确保串行处理
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 
                consumer.getMaxPollRecords() != null ? consumer.getMaxPollRecords() : 1);
        
        // 反序列化器配置
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        
        log.info("创建Kafka消费者工厂: bootstrapServers={}, groupId={}, maxPollRecords={}", 
                bootstrapServers, config.get(ConsumerConfig.GROUP_ID_CONFIG), 
                config.get(ConsumerConfig.MAX_POLL_RECORDS_CONFIG));
        
        return new DefaultKafkaConsumerFactory<>(config);
    }

    /**
     * 创建Kafka监听容器工厂
     * 
     * 功能说明:
     * 1. 配置监听容器
     * 2. 配置ACK模式为手动
     * 3. 配置并发级别为1(串行)
     * 4. 配置异常处理器
     * 
     * @return ConcurrentKafkaListenerContainerFactory 监听容器工厂
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = 
                new ConcurrentKafkaListenerContainerFactory<>();
        
        // 设置消费者工厂
        factory.setConsumerFactory(consumerFactory());
        
        // 设置ACK模式为手动确认
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        
        // 设置并发级别为1,确保消息串行处理
        factory.setConcurrency(1);
        
        // 配置错误处理器: 重试3次,间隔1秒
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                new FixedBackOff(1000L, 3L)
        );
        factory.setCommonErrorHandler(errorHandler);
        
        log.info("创建Kafka监听容器工厂: ackMode=MANUAL, concurrency=1");
        
        return factory;
    }

    /**
     * 创建Kafka生产者工厂
     * 
     * 功能说明:
     * 1. 配置生产者基础参数
     * 2. 配置序列化器
     * 3. 配置确认模式
     * 4. 配置重试参数
     * 
     * @return ProducerFactory Kafka生产者工厂
     */
    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        
        // 基础配置
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        
        // ACK模式: all表示等待所有副本确认
        config.put(ProducerConfig.ACKS_CONFIG, 
                producer.getAcks() != null ? producer.getAcks() : "all");
        
        // 重试次数
        config.put(ProducerConfig.RETRIES_CONFIG, 
                producer.getRetries() != null ? producer.getRetries() : 3);
        
        // 序列化器配置
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        
        // 启用幂等性,避免重复消息
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        
        log.info("创建Kafka生产者工厂: bootstrapServers={}, acks={}, retries={}", 
                bootstrapServers, config.get(ProducerConfig.ACKS_CONFIG), 
                config.get(ProducerConfig.RETRIES_CONFIG));
        
        return new DefaultKafkaProducerFactory<>(config);
    }

    /**
     * 创建Kafka模板
     * 
     * 功能说明:
     * 提供简化的Kafka消息发送API
     * 
     * @return KafkaTemplate Kafka操作模板
     */
    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        KafkaTemplate<String, String> template = new KafkaTemplate<>(producerFactory());
        log.info("创建Kafka模板");
        return template;
    }

    /**
     * 消费者配置类
     */
    @Data
    public static class ConsumerProperties {
        private String groupId;
        private String autoOffsetReset;
        private Boolean enableAutoCommit;
        private Integer maxPollRecords;
        private String keyDeserializer;
        private String valueDeserializer;
    }

    /**
     * 生产者配置类
     */
    @Data
    public static class ProducerProperties {
        private String keySerializer;
        private String valueSerializer;
        private String acks;
        private Integer retries;
    }

    /**
     * Topic配置类
     * 
     * 定义系统使用的Kafka Topic名称
     */
    @Data
    public static class TopicConfig {
        /**
         * 告警输入Topic
         */
        private String alertInput = "alert2agent";

        /**
         * 诊断结果输出Topic
         */
        private String diagnosisOutput = "diagnosis-result";

        /**
         * 诊断事件Topic
         */
        private String diagnosisEvent = "diagnosis-event";
    }

    /**
     * 创建Topic配置提供器Bean
     * 
     * @return TopicConfig Topic配置对象
     */
    @Bean
    public TopicConfig topicConfig() {
        log.info("Topic配置: alertInput={}, diagnosisOutput={}, diagnosisEvent={}", 
                topics.getAlertInput(), topics.getDiagnosisOutput(), topics.getDiagnosisEvent());
        return topics;
    }
}
