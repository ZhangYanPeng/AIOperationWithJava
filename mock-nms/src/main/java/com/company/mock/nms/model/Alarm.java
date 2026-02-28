package com.company.mock.nms.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 告警信息模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "告警信息")
public class Alarm {
    
    @Schema(description = "告警ID", example = "ALM20260115001")
    private String alarmId;
    
    @Schema(description = "关联设备ID", example = "DEV001")
    private String deviceId;
    
    @Schema(description = "告警级别", example = "CRITICAL", allowableValues = {"CRITICAL", "MAJOR", "MINOR", "WARNING", "INFO"})
    private String level;
    
    @Schema(description = "告警类型", example = "LINK_DOWN")
    private String alarmType;
    
    @Schema(description = "告警标题", example = "端口链路中断")
    private String title;
    
    @Schema(description = "告警描述", example = "GigabitEthernet0/0/1端口链路状态变为Down")
    private String description;
    
    @Schema(description = "告警来源", example = "SNMP_TRAP")
    private String source;
    
    @Schema(description = "告警状态", example = "ACTIVE", allowableValues = {"ACTIVE", "CONFIRMED", "CLEARED"})
    private String status;
    
    @Schema(description = "发生时间")
    private LocalDateTime occurTime;
    
    @Schema(description = "确认时间")
    private LocalDateTime confirmTime;
    
    @Schema(description = "清除时间")
    private LocalDateTime clearTime;
    
    @Schema(description = "确认人")
    private String confirmedBy;
    
    @Schema(description = "影响范围", example = "影响下游10台设备的网络连接")
    private String impact;
    
    @Schema(description = "扩展属性")
    private Map<String, Object> attributes;
}
