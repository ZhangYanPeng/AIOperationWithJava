package com.company.mock.nms.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 拓扑连接关系模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "拓扑连接关系")
public class TopologyLink {
    
    @Schema(description = "连接ID", example = "LINK001")
    private String linkId;
    
    @Schema(description = "源设备ID", example = "DEV001")
    private String sourceDeviceId;
    
    @Schema(description = "源设备名称", example = "核心交换机-01")
    private String sourceDeviceName;
    
    @Schema(description = "源端口", example = "GigabitEthernet0/0/1")
    private String sourcePort;
    
    @Schema(description = "目标设备ID", example = "DEV002")
    private String targetDeviceId;
    
    @Schema(description = "目标设备名称", example = "汇聚交换机-01")
    private String targetDeviceName;
    
    @Schema(description = "目标端口", example = "GigabitEthernet0/0/24")
    private String targetPort;
    
    @Schema(description = "链路类型", example = "FIBER", allowableValues = {"FIBER", "COPPER", "WIRELESS"})
    private String linkType;
    
    @Schema(description = "链路带宽(Mbps)", example = "10000")
    private Integer bandwidth;
    
    @Schema(description = "链路状态", example = "UP", allowableValues = {"UP", "DOWN", "DEGRADED"})
    private String linkStatus;
    
    @Schema(description = "链路利用率(%)", example = "35.5")
    private Double utilization;
}
