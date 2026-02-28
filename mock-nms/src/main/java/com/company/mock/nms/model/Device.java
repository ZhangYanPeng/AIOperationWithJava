package com.company.mock.nms.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 设备信息模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "设备信息")
public class Device {
    
    @Schema(description = "设备ID", example = "DEV001")
    private String deviceId;
    
    @Schema(description = "设备名称", example = "核心交换机-01")
    private String deviceName;
    
    @Schema(description = "设备类型", example = "SWITCH")
    private String deviceType;
    
    @Schema(description = "设备型号", example = "Huawei S12700")
    private String model;
    
    @Schema(description = "IP地址", example = "192.168.1.1")
    private String ipAddress;
    
    @Schema(description = "设备状态", example = "online")
    private String status;
    
    @Schema(description = "所属区域", example = "数据中心A区")
    private String region;
    
    @Schema(description = "机房位置", example = "机房1-机柜A01")
    private String location;
    
    @Schema(description = "厂商", example = "华为")
    private String vendor;
    
    @Schema(description = "系统版本", example = "V200R019C10")
    private String systemVersion;
    
    @Schema(description = "上线时间")
    private LocalDateTime onlineTime;
    
    @Schema(description = "最后更新时间")
    private LocalDateTime lastUpdateTime;
    
    @Schema(description = "扩展属性")
    private Map<String, Object> attributes;
}
