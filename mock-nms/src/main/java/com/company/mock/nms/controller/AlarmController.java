package com.company.mock.nms.controller;

import com.company.mock.nms.model.Alarm;
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
 * 告警管理控制器
 */
@RestController
@RequestMapping("/api/alarms")
@RequiredArgsConstructor
@Tag(name = "告警管理", description = "告警查询、确认、清除相关接口")
public class AlarmController {
    
    private final MockDataService mockDataService;
    
    @GetMapping
    @Operation(
            summary = "查询告警列表",
            description = "查询告警列表，支持按设备ID、级别、状态过滤",
            responses = {
                    @ApiResponse(responseCode = "200", description = "查询成功")
            }
    )
    public ResponseEntity<Map<String, Object>> listAlarms(
            @Parameter(description = "设备ID")
            @RequestParam(required = false) String deviceId,
            @Parameter(description = "告警级别（CRITICAL/MAJOR/MINOR/WARNING/INFO）")
            @RequestParam(required = false) String level,
            @Parameter(description = "告警状态（ACTIVE/CONFIRMED/CLEARED）")
            @RequestParam(required = false) String status) {
        
        List<Alarm> alarms = mockDataService.getAlarmsByFilter(deviceId, level, status);
        
        return ResponseEntity.ok(Map.of(
                "success", true,
                "total", alarms.size(),
                "data", alarms
        ));
    }
    
    @GetMapping("/{alarmId}")
    @Operation(
            summary = "查询告警详情",
            description = "根据告警ID查询告警的详细信息",
            responses = {
                    @ApiResponse(responseCode = "200", description = "查询成功"),
                    @ApiResponse(responseCode = "404", description = "告警不存在")
            }
    )
    public ResponseEntity<Map<String, Object>> getAlarm(
            @Parameter(description = "告警ID", required = true, example = "ALM20260115001")
            @PathVariable String alarmId) {
        
        return mockDataService.getAlarmById(alarmId)
                .map(alarm -> ResponseEntity.ok(Map.<String, Object>of(
                        "success", true,
                        "data", alarm
                )))
                .orElse(ResponseEntity.status(404).body(Map.of(
                        "success", false,
                        "error", "告警不存在: " + alarmId
                )));
    }
    
    @PostMapping("/{alarmId}/confirm")
    @Operation(
            summary = "确认告警",
            description = "确认指定的告警，将告警状态从ACTIVE变为CONFIRMED",
            responses = {
                    @ApiResponse(responseCode = "200", description = "确认成功"),
                    @ApiResponse(responseCode = "400", description = "确认失败"),
                    @ApiResponse(responseCode = "404", description = "告警不存在")
            }
    )
    public ResponseEntity<Map<String, Object>> confirmAlarm(
            @Parameter(description = "告警ID", required = true, example = "ALM20260115001")
            @PathVariable String alarmId,
            @Parameter(description = "确认人")
            @RequestParam(defaultValue = "admin") String confirmedBy) {
        
        if (mockDataService.getAlarmById(alarmId).isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "error", "告警不存在: " + alarmId
            ));
        }
        
        boolean result = mockDataService.confirmAlarm(alarmId, confirmedBy);
        if (result) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "告警确认成功",
                    "alarmId", alarmId,
                    "confirmedBy", confirmedBy
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "告警确认失败，可能告警已不是ACTIVE状态"
            ));
        }
    }
    
    @DeleteMapping("/{alarmId}")
    @Operation(
            summary = "清除告警",
            description = "清除指定的告警，将告警状态变为CLEARED",
            responses = {
                    @ApiResponse(responseCode = "200", description = "清除成功"),
                    @ApiResponse(responseCode = "404", description = "告警不存在")
            }
    )
    public ResponseEntity<Map<String, Object>> clearAlarm(
            @Parameter(description = "告警ID", required = true, example = "ALM20260115001")
            @PathVariable String alarmId) {
        
        if (mockDataService.getAlarmById(alarmId).isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "error", "告警不存在: " + alarmId
            ));
        }
        
        boolean result = mockDataService.clearAlarm(alarmId);
        return ResponseEntity.ok(Map.of(
                "success", result,
                "message", result ? "告警清除成功" : "告警清除失败",
                "alarmId", alarmId
        ));
    }
}
