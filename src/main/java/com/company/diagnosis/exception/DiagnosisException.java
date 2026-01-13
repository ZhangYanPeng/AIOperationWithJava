package com.company.diagnosis.exception;

/**
 * 诊断异常类
 * <p>
 * 职责：
 * 表示诊断过程中发生的异常
 *
 * @author Diagnosis System
 * @date 2026-01-13
 */
public class DiagnosisException extends RuntimeException {
    
    public DiagnosisException(String message) {
        super(message);
    }
    
    public DiagnosisException(String message, Throwable cause) {
        super(message, cause);
    }
}
