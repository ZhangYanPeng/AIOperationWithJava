package com.company.mock.nms.controller;

import com.company.mock.nms.model.TopologyLink;
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
 * 拓扑关系控制器
 */
@RestController
@RequestMapping("/api/topology")
@RequiredArgsConstructor
@Tag(name = "拓扑关系", description = "网络拓扑、设备连接关系查询接口")
public class TopologyController {
    
    private final MockDataService mockDataService;
    
    @GetMapping("/{deviceId}/connections")
    @Operation(
            summary = "查询设备连接关系",
            description = "查询指定设备的所有网络连接关系（包括作为源设备和目标设备的连接）",
            responses = {
                    @ApiResponse(responseCode = "200", description = "查询成功")
            }
    )
    public ResponseEntity<Map<String, Object>> getDeviceConnections(
            @Parameter(description = "设备ID", required = true, example = "DEV001")
            @PathVariable String deviceId) {
        
        List<TopologyLink> connections = mockDataService.getDeviceConnections(deviceId);
        
        return ResponseEntity.ok(Map.of(
                "success", true,
                "deviceId", deviceId,
                "total", connections.size(),
                "connections", connections
        ));
    }
    
    @GetMapping("/full")
    @Operation(
            summary = "查询完整拓扑",
            description = "查询整个网络的完整拓扑结构，包括所有设备和连接关系",
            responses = {
                    @ApiResponse(responseCode = "200", description = "查询成功")
            }
    )
    public ResponseEntity<Map<String, Object>> getFullTopology() {
        
        List<TopologyLink> topology = mockDataService.getFullTopology();
        
        return ResponseEntity.ok(Map.of(
                "success", true,
                "total", topology.size(),
                "links", topology
        ));
    }
}
