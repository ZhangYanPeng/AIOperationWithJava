package com.company.mock.nms.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 日志条目模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "日志条目")
public class LogEntry {
    
    @Schema(description = "日志ID", example = "LOG20260115000001")
    private String logId;
    
    @Schema(description = "设备ID（系统日志为空）", example = "DEV001")
    private String deviceId;
    
    @Schema(description = "日志级别", example = "WARNING", allowableValues = {"DEBUG", "INFO", "WARNING", "ERROR", "CRITICAL"})
    private String level;
    
    @Schema(description = "日志来源", example = "SYSLOG")
    private String source;
    
    @Schema(description = "日志模块", example = "INTERFACE")
    private String module;
    
    @Schema(description = "日志内容", example = "Interface GigabitEthernet0/0/1 state changed to Down")
    private String message;
    
    @Schema(description = "记录时间")
    private LocalDateTime timestamp;
    
    @Schema(description = "扩展字段")
    private Map<String, Object> fields;
}
