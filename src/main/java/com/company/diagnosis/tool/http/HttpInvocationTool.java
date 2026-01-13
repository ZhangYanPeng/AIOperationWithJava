package com.company.diagnosis.tool.http;

import com.company.diagnosis.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP调用工具
 * <p>
 * 职责：
 * 1. 封装HTTP请求的发送
 * 2. 支持GET、POST等HTTP方法
 * 3. 处理请求和响应的序列化
 * 4. 提供错误重试机制
 *
 * @author Diagnosis System
 * @date 2026-01-13
 */
@Component
public class HttpInvocationTool {

    private static final Logger logger = LoggerFactory.getLogger(HttpInvocationTool.class);

    private final WebClient webClient;
    
    @Value("${tool-api.timeout:60000}")
    private long timeout;
    
    @Value("${tool-api.retry.max-attempts:3}")
    private int maxRetryAttempts;
    
    @Value("${tool-api.retry.backoff-delay:1000}")
    private long backoffDelay;

    @Autowired
    public HttpInvocationTool(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 发送HTTP GET请求
     *
     * @param url 请求URL
     * @param headers 请求头
     * @return 响应数据
     */
    public Mono<Map<String, Object>> get(String url, Map<String, String> headers) {
        return executeRequest(HttpMethod.GET, url, headers, null);
    }

    /**
     * 发送HTTP POST请求
     *
     * @param url 请求URL
     * @param headers 请求头
     * @param body 请求体
     * @return 响应数据
     */
    public Mono<Map<String, Object>> post(String url, Map<String, String> headers, Object body) {
        return executeRequest(HttpMethod.POST, url, headers, body);
    }
    
    /**
     * 发送HTTP PUT请求
     *
     * @param url 请求URL
     * @param headers 请求头
     * @param body 请求体
     * @return 响应数据
     */
    public Mono<Map<String, Object>> put(String url, Map<String, String> headers, Object body) {
        return executeRequest(HttpMethod.PUT, url, headers, body);
    }
    
    /**
     * 发送HTTP DELETE请求
     *
     * @param url 请求URL
     * @param headers 请求头
     * @return 响应数据
     */
    public Mono<Map<String, Object>> delete(String url, Map<String, String> headers) {
        return executeRequest(HttpMethod.DELETE, url, headers, null);
    }
    
    /**
     * 执行HTTP请求
     *
     * @param method HTTP方法
     * @param url 请求URL
     * @param headers 请求头
     * @param body 请求体
     * @return 响应数据
     */
    @SuppressWarnings("unchecked")
    private Mono<Map<String, Object>> executeRequest(HttpMethod method, String url, 
                                                      Map<String, String> headers, Object body) {
        logger.debug("发送HTTP请求: method={}, url={}", method, url);
        
        WebClient.RequestBodySpec requestSpec = webClient
                .method(method)
                .uri(url);
        
        // 添加自定义请求头
        if (headers != null && !headers.isEmpty()) {
            headers.forEach(requestSpec::header);
        }
        
        // 添加请求体
        WebClient.ResponseSpec responseSpec;
        if (body != null && (method == HttpMethod.POST || method == HttpMethod.PUT)) {
            responseSpec = requestSpec
                    .bodyValue(body)
                    .retrieve();
        } else {
            responseSpec = requestSpec.retrieve();
        }
        
        return responseSpec
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(timeout))
                .retryWhen(Retry.backoff(maxRetryAttempts, Duration.ofMillis(backoffDelay))
                        .filter(this::isRetryableException)
                        .doBeforeRetry(signal -> 
                                logger.warn("HTTP请求重试: attempt={}, url={}", 
                                        signal.totalRetries() + 1, url)))
                .map(responseBody -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("statusCode", 200);
                    
                    // 尝试解析为JSON
                    if (responseBody != null && !responseBody.isEmpty()) {
                        if (JsonUtil.isValidJson(responseBody)) {
                            Map<String, Object> jsonData = JsonUtil.jsonToMap(responseBody);
                            result.put("data", jsonData);
                        } else {
                            result.put("data", responseBody);
                        }
                    }
                    
                    logger.debug("HTTP请求成功: url={}", url);
                    return result;
                })
                .onErrorResume(WebClientResponseException.class, e -> {
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("success", false);
                    errorResult.put("statusCode", e.getStatusCode().value());
                    errorResult.put("error", e.getMessage());
                    errorResult.put("responseBody", e.getResponseBodyAsString());
                    
                    logger.error("HTTP请求失败: url={}, status={}, error={}", 
                            url, e.getStatusCode(), e.getMessage());
                    return Mono.just(errorResult);
                })
                .onErrorResume(Exception.class, e -> {
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("success", false);
                    errorResult.put("error", e.getMessage());
                    errorResult.put("errorType", e.getClass().getSimpleName());
                    
                    logger.error("HTTP请求异常: url={}, error={}", url, e.getMessage(), e);
                    return Mono.just(errorResult);
                });
    }
    
    /**
     * 判断是否可重试的异常
     */
    private boolean isRetryableException(Throwable throwable) {
        if (throwable instanceof WebClientResponseException) {
            WebClientResponseException e = (WebClientResponseException) throwable;
            int statusCode = e.getStatusCode().value();
            // 服务器错误和部分客户端错误可重试
            return statusCode >= 500 || statusCode == 408 || statusCode == 429;
        }
        // 超时和连接异常可重试
        return throwable instanceof java.util.concurrent.TimeoutException ||
               throwable instanceof java.net.ConnectException;
    }
    
    /**
     * 构建带查询参数的URL
     *
     * @param baseUrl 基础URL
     * @param queryParams 查询参数
     * @return 完整URL
     */
    public String buildUrlWithParams(String baseUrl, Map<String, String> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return baseUrl;
        }
        
        StringBuilder urlBuilder = new StringBuilder(baseUrl);
        urlBuilder.append(baseUrl.contains("?") ? "&" : "?");
        
        queryParams.forEach((key, value) -> {
            if (value != null) {
                urlBuilder.append(key).append("=").append(value).append("&");
            }
        });
        
        // 移除最后一个&
        if (urlBuilder.charAt(urlBuilder.length() - 1) == '&') {
            urlBuilder.deleteCharAt(urlBuilder.length() - 1);
        }
        
        return urlBuilder.toString();
    }
    
    /**
     * 替换URL中的路径参数
     *
     * @param urlTemplate URL模板 (如 /api/devices/{deviceId})
     * @param pathParams 路径参数
     * @return 替换后的URL
     */
    public String replacePathParams(String urlTemplate, Map<String, String> pathParams) {
        if (pathParams == null || pathParams.isEmpty()) {
            return urlTemplate;
        }
        
        String result = urlTemplate;
        for (Map.Entry<String, String> entry : pathParams.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        
        return result;
    }
}
