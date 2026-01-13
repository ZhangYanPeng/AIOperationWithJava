package com.company.diagnosis.tool.http;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

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

    /**
     * 发送HTTP GET请求
     *
     * @param url 请求URL
     * @param headers 请求头
     * @return 响应数据
     */
    public Mono<Map<String, Object>> get(String url, Map<String, String> headers) {
        // TODO: 待实现
        return null;
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
        // TODO: 待实现
        return null;
    }
}
