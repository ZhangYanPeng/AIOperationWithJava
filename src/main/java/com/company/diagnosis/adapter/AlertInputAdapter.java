package com.company.diagnosis.adapter;

import com.company.diagnosis.model.dto.DiagnosisRequest;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 告警输入适配器
 * <p>
 * 职责：
 * 1. 适配不同来源的告警输入
 * 2. 转换为统一的DiagnosisRequest格式
 * 3. 验证输入数据的完整性
 *
 * @author Diagnosis System
 * @date 2026-01-13
 */
@Component
public class AlertInputAdapter {

    /**
     * 适配Kafka消息
     *
     * @param kafkaMessage Kafka消息
     * @return 诊断请求
     */
    public DiagnosisRequest adaptFromKafka(Map<String, Object> kafkaMessage) {
        // TODO: 待实现
        return null;
    }

    /**
     * 适配HTTP请求
     *
     * @param httpRequest HTTP请求数据
     * @return 诊断请求
     */
    public DiagnosisRequest adaptFromHttp(Map<String, Object> httpRequest) {
        // TODO: 待实现
        return null;
    }
}
