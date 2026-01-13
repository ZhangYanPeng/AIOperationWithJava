package com.company.diagnosis.exception;

/**
 * 智能体异常类
 * <p>
 * 职责：
 * 表示智能体执行过程中发生的异常
 *
 * @author Diagnosis System
 * @date 2026-01-13
 */
public class AgentException extends RuntimeException {
    
    public AgentException(String message) {
        super(message);
    }
    
    public AgentException(String message, Throwable cause) {
        super(message, cause);
    }
}
