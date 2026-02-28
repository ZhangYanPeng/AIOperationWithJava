package com.company.mock.nms.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 性能数据模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "性能数据")
public class PerformanceData {
    
    @Schema(description = "设备ID", example = "DEV001")
    private String deviceId;
    
    @Schema(description = "指标类型", example = "CPU", allowableValues = {"CPU", "MEMORY", "NETWORK", "DISK"})
    private String metricType;
    
    @Schema(description = "当前值", example = "45.5")
    private Double currentValue;
    
    @Schema(description = "平均值", example = "42.3")
    private Double avgValue;
    
    @Schema(description = "最大值", example = "78.9")
    private Double maxValue;
    
    @Schema(description = "最小值", example = "12.1")
    private Double minValue;
    
    @Schema(description = "单位", example = "%")
    private String unit;
    
    @Schema(description = "阈值", example = "80.0")
    private Double threshold;
    
    @Schema(description = "是否超阈值", example = "false")
    private Boolean overThreshold;
    
    @Schema(description = "采集时间")
    private LocalDateTime collectTime;
    
    @Schema(description = "健康状态", example = "HEALTHY", allowableValues = {"HEALTHY", "WARNING", "CRITICAL"})
    private String healthStatus;
}
