package com.company.mock.nms.controller;

import com.company.mock.nms.model.LogEntry;
import com.company.mock.nms.service.MockDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 日志查询控制器
 */
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
@Tag(name = "日志查询", description = "设备日志、系统日志查询接口")
public class LogController {
    
    private final MockDataService mockDataService;
    
    @GetMapping("/{deviceId}")
    @Operation(
            summary = "查询设备日志",
            description = "查询指定设备的日志记录，支持按级别和时间范围过滤",
            responses = {
                    @ApiResponse(responseCode = "200", description = "查询成功")
            }
    )
    public ResponseEntity<Map<String, Object>> getDeviceLogs(
            @Parameter(description = "设备ID", required = true, example = "DEV001")
            @PathVariable String deviceId,
            @Parameter(description = "日志级别（DEBUG/INFO/WARNING/ERROR/CRITICAL）")
            @RequestParam(required = false) String level,
            @Parameter(description = "开始时间（格式：yyyy-MM-dd'T'HH:mm:ss）")
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @Parameter(description = "结束时间（格式：yyyy-MM-dd'T'HH:mm:ss）")
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        
        List<LogEntry> logs = mockDataService.getDeviceLogs(deviceId, level, startTime, endTime);
        
        return ResponseEntity.ok(Map.of(
                "success", true,
                "deviceId", deviceId,
                "total", logs.size(),
                "logs", logs
        ));
    }
    
    @GetMapping("/system")
    @Operation(
            summary = "查询系统日志",
            description = "查询网管系统的日志记录，支持按级别和时间范围过滤",
            responses = {
                    @ApiResponse(responseCode = "200", description = "查询成功")
            }
    )
    public ResponseEntity<Map<String, Object>> getSystemLogs(
            @Parameter(description = "日志级别（DEBUG/INFO/WARNING/ERROR/CRITICAL）")
            @RequestParam(required = false) String level,
            @Parameter(description = "开始时间（格式：yyyy-MM-dd'T'HH:mm:ss）")
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @Parameter(description = "结束时间（格式：yyyy-MM-dd'T'HH:mm:ss）")
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        
        List<LogEntry> logs = mockDataService.getSystemLogs(level, startTime, endTime);
        
        return ResponseEntity.ok(Map.of(
                "success", true,
                "source", "NMS",
                "total", logs.size(),
                "logs", logs
        ));
    }
}
