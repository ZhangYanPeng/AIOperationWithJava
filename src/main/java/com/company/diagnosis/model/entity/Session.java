package com.company.diagnosis.model.entity;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 会话实体类
 * <p>
 * 职责：
 * 表示一个诊断会话的持久化数据
 *
 * @author Diagnosis System
 * @date 2026-01-13
 */
@Data
public class Session {
    private String sessionId;
    private String requestId;
    private String status;
    private Map<String, Object> context;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
