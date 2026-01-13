package com.company.diagnosis.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
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

    /**
     * Kafka服务器地址
     */
    private String bootstrapServers;

    /**
     * 消费者配置
     */
    private ConsumerProperties consumer;

    /**
     * 生产者配置
     */
    private ProducerProperties producer;

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
        // TODO: 待实现
        // 1. 创建配置Map
        // 2. 设置bootstrap-servers
        // 3. 设置group-id
        // 4. 设置auto-offset-reset
        // 5. 设置enable-auto-commit为false(手动提交)
        // 6. 设置max-poll-records为1(串行处理)
        // 7. 设置反序列化器
        // 8. 返回new DefaultKafkaConsumerFactory<>(config)
        return null;
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
        // TODO: 待实现
        // 1. 创建ConcurrentKafkaListenerContainerFactory实例
        // 2. 设置consumerFactory
        // 3. 设置ACK模式为MANUAL
        // 4. 设置并发级别为1
        // 5. 配置错误处理器
        // 6. 返回factory
        return null;
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
        // TODO: 待实现
        // 1. 创建配置Map
        // 2. 设置bootstrap-servers
        // 3. 设置acks为all
        // 4. 设置retries
        // 5. 设置序列化器
        // 6. 返回new DefaultKafkaProducerFactory<>(config)
        return null;
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
        // TODO: 待实现
        // 返回new KafkaTemplate<>(producerFactory())
        return null;
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
        return topics;
    }
}
package com.company.diagnosis.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
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

    /**
     * Kafka服务器地址
     */
    private String bootstrapServers;

    /**
     * 消费者配置
     */
    private ConsumerProperties consumer;

    /**
     * 生产者配置
     */
    private ProducerProperties producer;

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
        // TODO: 待实现
        // 1. 创建配置Map
        // 2. 设置bootstrap-servers
        // 3. 设置group-id
        // 4. 设置auto-offset-reset
        // 5. 设置enable-auto-commit为false(手动提交)
        // 6. 设置max-poll-records为1(串行处理)
        // 7. 设置反序列化器
        // 8. 返回new DefaultKafkaConsumerFactory<>(config)
        return null;
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
        // TODO: 待实现
        // 1. 创建ConcurrentKafkaListenerContainerFactory实例
        // 2. 设置consumerFactory
        // 3. 设置ACK模式为MANUAL
        // 4. 设置并发级别为1
        // 5. 配置错误处理器
        // 6. 返回factory
        return null;
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
        // TODO: 待实现
        // 1. 创建配置Map
        // 2. 设置bootstrap-servers
        // 3. 设置acks为all
        // 4. 设置retries
        // 5. 设置序列化器
        // 6. 返回new DefaultKafkaProducerFactory<>(config)
        return null;
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
        // TODO: 待实现
        // 返回new KafkaTemplate<>(producerFactory())
        return null;
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
        return topics;
    }
}
