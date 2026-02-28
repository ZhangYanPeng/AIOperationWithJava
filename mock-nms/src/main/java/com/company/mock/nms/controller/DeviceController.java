package com.company.mock.nms.controller;

import com.company.mock.nms.model.Device;
import com.company.mock.nms.service.MockDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 设备管理控制器
 */
@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
@Tag(name = "设备管理", description = "设备查询、状态管理相关接口")
public class DeviceController {
    
    private final MockDataService mockDataService;
    
    @GetMapping
    @Operation(
            summary = "查询设备列表",
            description = "查询所有设备或按状态过滤设备列表",
            responses = {
                    @ApiResponse(responseCode = "200", description = "查询成功")
            }
    )
    public ResponseEntity<Map<String, Object>> listDevices(
            @Parameter(description = "设备状态过滤（online/offline/degraded）")
            @RequestParam(required = false) String status) {
        
        List<Device> devices = status != null ? 
                mockDataService.getDevicesByStatus(status) : 
                mockDataService.getAllDevices();
        
        return ResponseEntity.ok(Map.of(
                "success", true,
                "total", devices.size(),
                "data", devices
        ));
    }
    
    @GetMapping("/{deviceId}")
    @Operation(
            summary = "查询设备详情",
            description = "根据设备ID查询设备的详细信息",
            responses = {
                    @ApiResponse(responseCode = "200", description = "查询成功"),
                    @ApiResponse(responseCode = "404", description = "设备不存在")
            }
    )
    public ResponseEntity<Map<String, Object>> getDevice(
            @Parameter(description = "设备ID", required = true, example = "DEV001")
            @PathVariable String deviceId) {
        
        return mockDataService.getDeviceById(deviceId)
                .map(device -> ResponseEntity.ok(Map.<String, Object>of(
                        "success", true,
                        "data", device
                )))
                .orElse(ResponseEntity.status(404).body(Map.of(
                        "success", false,
                        "error", "设备不存在: " + deviceId
                )));
    }
    
    @GetMapping("/{deviceId}/status")
    @Operation(
            summary = "查询设备状态",
            description = "查询设备的实时运行状态，包括CPU、内存使用率等",
            responses = {
                    @ApiResponse(responseCode = "200", description = "查询成功"),
                    @ApiResponse(responseCode = "404", description = "设备不存在")
            }
    )
    public ResponseEntity<Map<String, Object>> getDeviceStatus(
            @Parameter(description = "设备ID", required = true, example = "DEV001")
            @PathVariable String deviceId) {
        
        Map<String, Object> status = mockDataService.getDeviceStatus(deviceId);
        if (status == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "error", "设备不存在: " + deviceId
            ));
        }
        
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", status
        ));
    }
}
