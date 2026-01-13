package com.company.diagnosis.util;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 追踪工具类
 * <p>
 * 职责：
 * 1. 生成追踪ID
 * 2. 管理追踪上下文
 * 3. 记录追踪日志
 *
 * @author Diagnosis System
 * @date 2026-01-13
 */
public class TraceUtil {

    private static final String HEX_CHARS = "0123456789abcdef";
    
    /**
     * TraceId线程本地存储
     */
    private static final ThreadLocal<String> TRACE_ID_HOLDER = new ThreadLocal<>();
    
    /**
     * SpanId线程本地存储
     */
    private static final ThreadLocal<String> SPAN_ID_HOLDER = new ThreadLocal<>();

    /**
     * 生成追踪ID
     *
     * @return 追踪ID (32位十六进制字符串)
     */
    public static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成Span ID
     *
     * @return Span ID (16位十六进制字符串)
     */
    public static String generateSpanId() {
        StringBuilder sb = new StringBuilder(16);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 16; i++) {
            sb.append(HEX_CHARS.charAt(random.nextInt(16)));
        }
        return sb.toString();
    }
    
    /**
     * 生成请求ID
     *
     * @return 请求ID
     */
    public static String generateRequestId() {
        return "req-" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    /**
     * 生成会话ID
     *
     * @return 会话ID
     */
    public static String generateSessionId() {
        return "sess-" + UUID.randomUUID().toString();
    }
    
    /**
     * 设置当前线程的TraceId
     *
     * @param traceId 追踪ID
     */
    public static void setTraceId(String traceId) {
        TRACE_ID_HOLDER.set(traceId);
    }
    
    /**
     * 获取当前线程的TraceId
     *
     * @return 追踪ID
     */
    public static String getTraceId() {
        return TRACE_ID_HOLDER.get();
    }
    
    /**
     * 设置当前线程的SpanId
     *
     * @param spanId Span ID
     */
    public static void setSpanId(String spanId) {
        SPAN_ID_HOLDER.set(spanId);
    }
    
    /**
     * 获取当前线程的SpanId
     *
     * @return Span ID
     */
    public static String getSpanId() {
        return SPAN_ID_HOLDER.get();
    }
    
    /**
     * 清除当前线程的追踪上下文
     */
    public static void clear() {
        TRACE_ID_HOLDER.remove();
        SPAN_ID_HOLDER.remove();
    }
    
    /**
     * 获取或创建TraceId
     *
     * @return 追踪ID
     */
    public static String getOrCreateTraceId() {
        String traceId = TRACE_ID_HOLDER.get();
        if (traceId == null) {
            traceId = generateTraceId();
            TRACE_ID_HOLDER.set(traceId);
        }
        return traceId;
    }
    
    /**
     * 创建新的Span并返回SpanId
     *
     * @return 新的Span ID
     */
    public static String newSpan() {
        String spanId = generateSpanId();
        SPAN_ID_HOLDER.set(spanId);
        return spanId;
    }
}
