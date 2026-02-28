package com.company.mock.nms.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 设备配置模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "设备配置")
public class DeviceConfig {
    
    @Schema(description = "设备ID", example = "DEV001")
    private String deviceId;
    
    @Schema(description = "配置版本", example = "v2.3.1")
    private String configVersion;
    
    @Schema(description = "配置内容")
    private Map<String, Object> configData;
    
    @Schema(description = "是否为当前配置", example = "true")
    private Boolean isCurrent;
    
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    
    @Schema(description = "最后修改时间")
    private LocalDateTime lastModifyTime;
    
    @Schema(description = "修改人", example = "admin")
    private String modifiedBy;
    
    @Schema(description = "配置描述", example = "启用OSPF路由协议")
    private String description;
    
    @Schema(description = "备份路径", example = "/backup/DEV001/config_20260115.txt")
    private String backupPath;
}
