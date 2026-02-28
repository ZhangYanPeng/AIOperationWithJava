package com.company.mock.nms.controller;

import com.company.mock.nms.model.PerformanceData;
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
 * 性能数据控制器
 */
@RestController
@RequestMapping("/api/performance")
@RequiredArgsConstructor
@Tag(name = "性能数据", description = "CPU、内存、网络、磁盘性能监控接口")
public class PerformanceController {
    
    private final MockDataService mockDataService;
    
    @GetMapping("/{deviceId}/cpu")
    @Operation(
            summary = "查询CPU使用率",
            description = "查询指定设备的CPU使用率数据，包括当前值、平均值、最大值、最小值等",
            responses = {
                    @ApiResponse(responseCode = "200", description = "查询成功"),
                    @ApiResponse(responseCode = "404", description = "设备不存在")
            }
    )
    public ResponseEntity<Map<String, Object>> getCpuPerformance(
            @Parameter(description = "设备ID", required = true, example = "DEV001")
            @PathVariable String deviceId) {
        
        PerformanceData data = mockDataService.getCpuPerformance(deviceId);
        if (data == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "error", "设备不存在: " + deviceId
            ));
        }
        
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", data
        ));
    }
    
    @GetMapping("/{deviceId}/memory")
    @Operation(
            summary = "查询内存使用率",
            description = "查询指定设备的内存使用率数据，包括当前值、平均值、最大值、最小值等",
            responses = {
                    @ApiResponse(responseCode = "200", description = "查询成功"),
                    @ApiResponse(responseCode = "404", description = "设备不存在")
            }
    )
    public ResponseEntity<Map<String, Object>> getMemoryPerformance(
            @Parameter(description = "设备ID", required = true, example = "DEV001")
            @PathVariable String deviceId) {
        
        PerformanceData data = mockDataService.getMemoryPerformance(deviceId);
        if (data == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "error", "设备不存在: " + deviceId
            ));
        }
        
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", data
        ));
    }
    
    @GetMapping("/{deviceId}/network")
    @Operation(
            summary = "查询网络流量",
            description = "查询指定设备的网络流量数据，包括当前带宽使用、平均值等",
            responses = {
                    @ApiResponse(responseCode = "200", description = "查询成功"),
                    @ApiResponse(responseCode = "404", description = "设备不存在")
            }
    )
    public ResponseEntity<Map<String, Object>> getNetworkPerformance(
            @Parameter(description = "设备ID", required = true, example = "DEV001")
            @PathVariable String deviceId) {
        
        PerformanceData data = mockDataService.getNetworkPerformance(deviceId);
        if (data == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "error", "设备不存在: " + deviceId
            ));
        }
        
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", data
        ));
    }
    
    @GetMapping("/{deviceId}/disk")
    @Operation(
            summary = "查询磁盘使用率",
            description = "查询指定设备的磁盘使用率数据，包括当前值、阈值等",
            responses = {
                    @ApiResponse(responseCode = "200", description = "查询成功"),
                    @ApiResponse(responseCode = "404", description = "设备不存在")
            }
    )
    public ResponseEntity<Map<String, Object>> getDiskPerformance(
            @Parameter(description = "设备ID", required = true, example = "DEV001")
            @PathVariable String deviceId) {
        
        PerformanceData data = mockDataService.getDiskPerformance(deviceId);
        if (data == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "error", "设备不存在: " + deviceId
            ));
        }
        
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", data
        ));
    }
}
