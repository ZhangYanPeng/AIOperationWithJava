package com.company.mock.nms.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备策略模型
 * 包含安全策略、通信策略等多种策略类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "设备策略信息")
public class DevicePolicy {
    
    @Schema(description = "设备ID", example = "DEV001")
    private String deviceId;
    
    @Schema(description = "设备名称", example = "核心交换机-01")
    private String deviceName;
    
    @Schema(description = "策略版本", example = "v2.1.0")
    private String policyVersion;
    
    @Schema(description = "最后更新时间")
    private LocalDateTime lastUpdateTime;
    
    @Schema(description = "安全策略列表")
    private List<SecurityPolicy> securityPolicies;
    
    @Schema(description = "通信策略列表")
    private List<CommunicationPolicy> communicationPolicies;
    
    @Schema(description = "QoS策略列表")
    private List<QosPolicy> qosPolicies;
    
    /**
     * 安全策略
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "安全策略")
    public static class SecurityPolicy {
        
        @Schema(description = "策略ID", example = "SEC001")
        private String policyId;
        
        @Schema(description = "策略名称", example = "允许内网访问")
        private String policyName;
        
        @Schema(description = "策略优先级", example = "100")
        private int priority;
        
        @Schema(description = "源IP地址", example = "192.168.1.2")
        private String sourceIp;
        
        @Schema(description = "源端口", example = "any")
        private String sourcePort;
        
        @Schema(description = "目标IP地址", example = "10.0.0.0/8")
        private String destinationIp;
        
        @Schema(description = "目标端口", example = "443")
        private String destinationPort;
        
        @Schema(description = "协议类型", example = "TCP")
        private String protocol;
        
        @Schema(description = "动作", example = "ALLOW")
        private String action;
        
        @Schema(description = "是否启用", example = "true")
        private boolean enabled;
        
        @Schema(description = "描述")
        private String description;
        
        @Schema(description = "命中次数", example = "12580")
        private long hitCount;
        
        @Schema(description = "最后命中时间")
        private LocalDateTime lastHitTime;
    }
    
    /**
     * 通信策略
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "通信策略")
    public static class CommunicationPolicy {
        
        @Schema(description = "策略ID", example = "COM001")
        private String policyId;
        
        @Schema(description = "策略名称", example = "VLAN间通信")
        private String policyName;
        
        @Schema(description = "源VLAN", example = "10")
        private int sourceVlan;
        
        @Schema(description = "目标VLAN", example = "20")
        private int destinationVlan;
        
        @Schema(description = "带宽限制(Mbps)", example = "1000")
        private int bandwidthLimit;
        
        @Schema(description = "是否允许", example = "true")
        private boolean allowed;
        
        @Schema(description = "路由方式", example = "DIRECT")
        private String routingMode;
        
        @Schema(description = "描述")
        private String description;
    }
    
    /**
     * QoS策略
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "QoS策略")
    public static class QosPolicy {
        
        @Schema(description = "策略ID", example = "QOS001")
        private String policyId;
        
        @Schema(description = "策略名称", example = "语音优先")
        private String policyName;
        
        @Schema(description = "流量类型", example = "VOICE")
        private String trafficType;
        
        @Schema(description = "优先级队列", example = "5")
        private int priorityQueue;
        
        @Schema(description = "保障带宽(Mbps)", example = "100")
        private int guaranteedBandwidth;
        
        @Schema(description = "最大带宽(Mbps)", example = "500")
        private int maxBandwidth;
        
        @Schema(description = "DSCP值", example = "46")
        private int dscpValue;
        
        @Schema(description = "丢弃策略", example = "TAIL_DROP")
        private String dropPolicy;
        
        @Schema(description = "描述")
        private String description;
    }
}
