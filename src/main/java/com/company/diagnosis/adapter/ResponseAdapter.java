package com.company.diagnosis.adapter;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 响应适配器
 * <p>
 * 职责：
 * 1. 将底层接口调用的原始响应转换为上层智能体可用的结构
 * 2. 处理不同接口返回格式的差异
 * 3. 统一错误码和错误信息结构
 */
@Component
public class ResponseAdapter {

    /**
     * 适配HTTP接口响应
     *
     * @param rawResponse 接口原始响应（已解析为Map）
     * @param spec 接口规范信息（如字段映射规则）
     * @return 统一结构的响应数据
     */
    public Map<String, Object> adaptHttpResponse(Map<String, Object> rawResponse, Map<String, Object> spec) {
        // TODO: 待实现
        return null;
    }
}
