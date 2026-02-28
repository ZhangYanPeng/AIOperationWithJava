package com.company.mock.nms.controller;

import com.company.mock.nms.model.DevicePolicy;
import com.company.mock.nms.service.MockDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 设备策略管理控制器
 */
@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
@Tag(name = "设备策略管理", description = "设备安全策略、通信策略、QoS策略查询接口")
public class DevicePolicyController {
    
    private final MockDataService mockDataService;
    
    @GetMapping("/{deviceId}/policies")
    @Operation(
            summary = "查询设备策略",
            description = "查询设备的所有策略配置，包括安全策略、通信策略和QoS策略",
            responses = {
                    @ApiResponse(responseCode = "200", description = "查询成功"),
                    @ApiResponse(responseCode = "404", description = "设备不存在")
            }
    )
    public ResponseEntity<Map<String, Object>> getDevicePolicies(
            @Parameter(description = "设备ID", required = true, example = "DEV001")
            @PathVariable String deviceId) {
        
        DevicePolicy policy = mockDataService.getDevicePolicy(deviceId);
        if (policy == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "error", "设备不存在: " + deviceId
            ));
        }
        
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", policy
        ));
    }
    
    @GetMapping("/{deviceId}/policies/security")
    @Operation(
            summary = "查询设备安全策略",
            description = "查询设备的安全策略列表",
            responses = {
                    @ApiResponse(responseCode = "200", description = "查询成功"),
                    @ApiResponse(responseCode = "404", description = "设备不存在")
            }
    )
    public ResponseEntity<Map<String, Object>> getSecurityPolicies(
            @Parameter(description = "设备ID", required = true, example = "DEV001")
            @PathVariable String deviceId) {
        
        DevicePolicy policy = mockDataService.getDevicePolicy(deviceId);
        if (policy == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "error", "设备不存在: " + deviceId
            ));
        }
        
        return ResponseEntity.ok(Map.of(
                "success", true,
                "deviceId", deviceId,
                "data", policy.getSecurityPolicies()
        ));
    }
    
    @GetMapping("/{deviceId}/policies/communication")
    @Operation(
            summary = "查询设备通信策略",
            description = "查询设备的通信策略列表",
            responses = {
                    @ApiResponse(responseCode = "200", description = "查询成功"),
                    @ApiResponse(responseCode = "404", description = "设备不存在")
            }
    )
    public ResponseEntity<Map<String, Object>> getCommunicationPolicies(
            @Parameter(description = "设备ID", required = true, example = "DEV001")
            @PathVariable String deviceId) {
        
        DevicePolicy policy = mockDataService.getDevicePolicy(deviceId);
        if (policy == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "error", "设备不存在: " + deviceId
            ));
        }
        
        return ResponseEntity.ok(Map.of(
                "success", true,
                "deviceId", deviceId,
                "data", policy.getCommunicationPolicies()
        ));
    }
    
    @GetMapping("/{deviceId}/policies/qos")
    @Operation(
            summary = "查询设备QoS策略",
            description = "查询设备的QoS策略列表",
            responses = {
                    @ApiResponse(responseCode = "200", description = "查询成功"),
                    @ApiResponse(responseCode = "404", description = "设备不存在")
            }
    )
    public ResponseEntity<Map<String, Object>> getQosPolicies(
            @Parameter(description = "设备ID", required = true, example = "DEV001")
            @PathVariable String deviceId) {
        
        DevicePolicy policy = mockDataService.getDevicePolicy(deviceId);
        if (policy == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "error", "设备不存在: " + deviceId
            ));
        }
        
        return ResponseEntity.ok(Map.of(
                "success", true,
                "deviceId", deviceId,
                "data", policy.getQosPolicies()
        ));
    }
}
