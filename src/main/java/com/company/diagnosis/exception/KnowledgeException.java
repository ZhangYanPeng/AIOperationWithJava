package com.company.diagnosis.exception;

/**
 * 知识库异常类
 * <p>
 * 职责：
 * 表示知识库操作过程中发生的异常
 *
 * @author Diagnosis System
 * @date 2026-01-13
 */
public class KnowledgeException extends RuntimeException {
    
    public KnowledgeException(String message) {
        super(message);
    }
    
    public KnowledgeException(String message, Throwable cause) {
        super(message, cause);
    }
}
