package com.company.diagnosis.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * WebFlux配置类
 * 
 * 功能描述:
 * - 配置响应式Web框架
 * - 配置WebClient HTTP客户端
 * - 配置CORS跨域
 * - 配置响应式调度器
 * 
 * 设计考虑:
 * - 支持SSE流式输出
 * - 支持异步非阻塞处理
 * - 配置合理的线程池参数
 * - 支持背压控制
 * 
 * @author System
 * @since 2026-01-13
 */
@Configuration
@EnableWebFlux
public class WebFluxConfig implements WebFluxConfigurer {

    @Value("${diagnosis.cors.allowed-origins:*}")
    private String allowedOrigins;

    @Value("${diagnosis.webclient.connect-timeout:5000}")
    private int connectTimeout;

    @Value("${diagnosis.webclient.read-timeout:60000}")
    private int readTimeout;

    @Value("${diagnosis.webclient.write-timeout:60000}")
    private int writeTimeout;

    @Value("${tool-api.base-url:}")
    private String toolApiBaseUrl;

    @Value("${tool-api.timeout:30000}")
    private int toolApiTimeout;

    /**
     * 配置CORS跨域
     * 
     * 功能说明:
     * 1. 允许前端跨域访问
     * 2. 配置允许的源、方法、头
     * 3. 配置凭证支持
     * 
     * @param registry CORS注册表
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOrigins.split(","))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .exposedHeaders("Content-Type", "X-Request-Id", "X-Session-Id")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * 配置编解码器
     * 
     * 功能说明:
     * 配置请求和响应的编解码器,支持大文件上传
     * 
     * @param configurer 编解码配置器
     */
    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        // 设置内存缓冲区大小为100MB，支持大文件上传
        configurer.defaultCodecs().maxInMemorySize(100 * 1024 * 1024);
    }

    /**
     * 创建WebClient HTTP客户端
     * 
     * 功能说明:
     * 创建响应式HTTP客户端,用于调用外部Tool API
     * 
     * @return WebClient 响应式HTTP客户端
     */
    @Bean
    public WebClient webClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout)
                .responseTimeout(Duration.ofMillis(readTimeout))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(readTimeout, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(writeTimeout, TimeUnit.MILLISECONDS)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(50 * 1024 * 1024))
                .build();
    }

    /**
     * 创建工具API专用WebClient
     * 
     * 功能说明:
     * 为外部Tool API调用创建专用的WebClient,配置特定的超时和重试策略
     * 
     * @return WebClient 工具API专用HTTP客户端
     */
    @Bean("toolApiWebClient")
    public WebClient toolApiWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout)
                .responseTimeout(Duration.ofMillis(toolApiTimeout))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(toolApiTimeout, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(toolApiTimeout, TimeUnit.MILLISECONDS)));

        WebClient.Builder builder = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024));

        // 如果配置了baseUrl，则设置
        if (toolApiBaseUrl != null && !toolApiBaseUrl.isEmpty()) {
            builder.baseUrl(toolApiBaseUrl);
        }

        return builder.build();
    }

    /**
     * 创建并行调度器
     * 
     * 功能说明:
     * 创建用于并行处理的调度器,控制并发线程数
     * 
     * @return Scheduler 并行调度器
     */
    @Bean("parallelScheduler")
    public Scheduler parallelScheduler() {
        // 使用CPU核心数作为并行度
        int parallelism = Runtime.getRuntime().availableProcessors();
        return Schedulers.newParallel("diagnosis-parallel", parallelism);
    }

    /**
     * 创建I/O调度器
     * 
     * 功能说明:
     * 创建用于I/O密集型操作的调度器,如文件读写、网络请求
     * 
     * @return Scheduler I/O调度器
     */
    @Bean("ioScheduler")
    public Scheduler ioScheduler() {
        // 使用弹性线程池，适合I/O密集型任务
        return Schedulers.newBoundedElastic(
                Schedulers.DEFAULT_BOUNDED_ELASTIC_SIZE,
                Schedulers.DEFAULT_BOUNDED_ELASTIC_QUEUESIZE,
                "diagnosis-io"
        );
    }

    /**
     * 创建SSE专用调度器
     * 
     * 功能说明:
     * 为SSE流式推送创建专用调度器,避免阻塞主线程
     * 
     * @return Scheduler SSE调度器
     */
    @Bean("sseScheduler")
    public Scheduler sseScheduler() {
        // SSE使用单独的调度器，线程数为CPU核心数的2倍
        int threadCount = Runtime.getRuntime().availableProcessors() * 2;
        return Schedulers.newBoundedElastic(
                threadCount,
                10000, // 队列大小
                "diagnosis-sse"
        );
    }
}
