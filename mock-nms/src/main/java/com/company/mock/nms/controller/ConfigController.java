package com.company.mock.nms.controller;

import com.company.mock.nms.model.DeviceConfig;
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
 * 配置管理控制器
 */
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
@Tag(name = "配置管理", description = "设备配置查询、更新、备份接口")
public class ConfigController {
    
    private final MockDataService mockDataService;
    
    @GetMapping("/{deviceId}")
    @Operation(
            summary = "查询设备配置",
            description = "查询指定设备的当前配置信息",
            responses = {
                    @ApiResponse(responseCode = "200", description = "查询成功"),
                    @ApiResponse(responseCode = "404", description = "设备不存在")
            }
    )
    public ResponseEntity<Map<String, Object>> getDeviceConfig(
            @Parameter(description = "设备ID", required = true, example = "DEV001")
            @PathVariable String deviceId) {
        
        return mockDataService.getDeviceConfig(deviceId)
                .map(config -> ResponseEntity.ok(Map.<String, Object>of(
                        "success", true,
                        "data", config
                )))
                .orElse(ResponseEntity.status(404).body(Map.of(
                        "success", false,
                        "error", "设备配置不存在: " + deviceId
                )));
    }
    
    @PutMapping("/{deviceId}")
    @Operation(
            summary = "更新设备配置",
            description = "更新指定设备的配置信息，支持部分更新",
            responses = {
                    @ApiResponse(responseCode = "200", description = "更新成功"),
                    @ApiResponse(responseCode = "404", description = "设备不存在")
            }
    )
    public ResponseEntity<Map<String, Object>> updateDeviceConfig(
            @Parameter(description = "设备ID", required = true, example = "DEV001")
            @PathVariable String deviceId,
            @RequestBody Map<String, Object> configData) {
        
        DeviceConfig updated = mockDataService.updateDeviceConfig(deviceId, configData);
        if (updated == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "error", "设备配置不存在: " + deviceId
            ));
        }
        
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "配置更新成功",
                "data", updated
        ));
    }
    
    @PostMapping("/{deviceId}/backup")
    @Operation(
            summary = "备份设备配置",
            description = "备份指定设备的当前配置到服务器",
            responses = {
                    @ApiResponse(responseCode = "200", description = "备份成功"),
                    @ApiResponse(responseCode = "404", description = "设备不存在")
            }
    )
    public ResponseEntity<Map<String, Object>> backupDeviceConfig(
            @Parameter(description = "设备ID", required = true, example = "DEV001")
            @PathVariable String deviceId) {
        
        String backupPath = mockDataService.backupDeviceConfig(deviceId);
        if (backupPath == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "error", "设备配置不存在: " + deviceId
            ));
        }
        
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "配置备份成功",
                "deviceId", deviceId,
                "backupPath", backupPath
        ));
    }
}
