package com.company.mock.nms.service;

import com.company.mock.nms.model.*;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 模拟数据服务
 * 
 * 提供模拟的网管数据，支持测试场景
 * 
 * @author AIOperation Team
 * @since 2026-01-15
 */
@Service
public class MockDataService {
    
    // 模拟数据存储
    private final Map<String, Device> devices = new ConcurrentHashMap<>();
    private final Map<String, Alarm> alarms = new ConcurrentHashMap<>();
    private final Map<String, DeviceConfig> configs = new ConcurrentHashMap<>();
    private final Map<String, DevicePolicy> policies = new ConcurrentHashMap<>();
    private final List<TopologyLink> topologyLinks = new ArrayList<>();
    private final List<LogEntry> deviceLogs = new ArrayList<>();
    private final List<LogEntry> systemLogs = new ArrayList<>();
    
    public MockDataService() {
        initMockData();
    }
    
    /**
     * 初始化模拟数据
     */
    private void initMockData() {
        // 初始化设备
        initDevices();
        // 初始化告警
        initAlarms();
        // 初始化配置
        initConfigs();
        // 初始化策略
        initPolicies();
        // 初始化拓扑
        initTopology();
        // 初始化日志
        initLogs();
    }
    
    private void initDevices() {
        String[] types = {"SWITCH", "ROUTER", "FIREWALL", "SERVER", "STORAGE"};
        String[] vendors = {"华为", "思科", "H3C", "锐捷", "中兴"};
        String[] statuses = {"online", "online", "online", "offline", "degraded"};
        String[] regions = {"数据中心A区", "数据中心B区", "办公区", "DMZ区"};
        
        for (int i = 1; i <= 20; i++) {
            String deviceId = String.format("DEV%03d", i);
            Device device = Device.builder()
                    .deviceId(deviceId)
                    .deviceName(String.format("设备-%03d", i))
                    .deviceType(types[i % types.length])
                    .model(String.format("Model-%s-%d", types[i % types.length], i))
                    .ipAddress(String.format("192.168.%d.%d", (i / 256) + 1, i % 256))
                    .status(statuses[i % statuses.length])
                    .region(regions[i % regions.length])
                    .location(String.format("机房%d-机柜A%02d", (i / 10) + 1, i % 10))
                    .vendor(vendors[i % vendors.length])
                    .systemVersion(String.format("V%d.%d.%d", i % 3 + 1, i % 5, i % 10))
                    .onlineTime(LocalDateTime.now().minusDays(i * 10))
                    .lastUpdateTime(LocalDateTime.now().minusMinutes(i * 5))
                    .attributes(Map.of(
                            "sn", "SN" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                            "managementIp", String.format("10.0.%d.%d", i / 256, i % 256)
                    ))
                    .build();
            devices.put(deviceId, device);
        }
    }
    
    private void initAlarms() {
        String[] levels = {"CRITICAL", "MAJOR", "MINOR", "WARNING", "INFO"};
        String[] types = {"LINK_DOWN", "CPU_HIGH", "MEMORY_HIGH", "POWER_FAIL", "FAN_FAIL", "INTERFACE_ERROR"};
        String[] statuses = {"ACTIVE", "ACTIVE", "CONFIRMED", "CLEARED"};
        
        for (int i = 1; i <= 30; i++) {
            String alarmId = String.format("ALM%s%03d", LocalDateTime.now().toLocalDate().toString().replace("-", ""), i);
            String deviceId = String.format("DEV%03d", (i % 20) + 1);
            String level = levels[i % levels.length];
            String type = types[i % types.length];
            String status = statuses[i % statuses.length];
            
            Alarm alarm = Alarm.builder()
                    .alarmId(alarmId)
                    .deviceId(deviceId)
                    .level(level)
                    .alarmType(type)
                    .title(getAlarmTitle(type))
                    .description(getAlarmDescription(type, deviceId))
                    .source("SNMP_TRAP")
                    .status(status)
                    .occurTime(LocalDateTime.now().minusHours(i * 2))
                    .confirmTime("CONFIRMED".equals(status) || "CLEARED".equals(status) ? 
                            LocalDateTime.now().minusHours(i) : null)
                    .clearTime("CLEARED".equals(status) ? LocalDateTime.now().minusMinutes(i * 30) : null)
                    .confirmedBy("CONFIRMED".equals(status) || "CLEARED".equals(status) ? "admin" : null)
                    .impact(getAlarmImpact(level))
                    .attributes(Map.of("rawData", "模拟原始告警数据"))
                    .build();
            alarms.put(alarmId, alarm);
        }
    }
    
    private String getAlarmTitle(String type) {
        return switch (type) {
            case "LINK_DOWN" -> "端口链路中断";
            case "CPU_HIGH" -> "CPU使用率过高";
            case "MEMORY_HIGH" -> "内存使用率过高";
            case "POWER_FAIL" -> "电源故障";
            case "FAN_FAIL" -> "风扇故障";
            case "INTERFACE_ERROR" -> "接口错误";
            default -> "未知告警";
        };
    }
    
    private String getAlarmDescription(String type, String deviceId) {
        return switch (type) {
            case "LINK_DOWN" -> String.format("设备%s的GigabitEthernet0/0/1端口链路状态变为Down", deviceId);
            case "CPU_HIGH" -> String.format("设备%s的CPU使用率超过阈值，当前使用率95%%", deviceId);
            case "MEMORY_HIGH" -> String.format("设备%s的内存使用率超过阈值，当前使用率90%%", deviceId);
            case "POWER_FAIL" -> String.format("设备%s的电源模块1发生故障", deviceId);
            case "FAN_FAIL" -> String.format("设备%s的风扇模块2转速异常", deviceId);
            case "INTERFACE_ERROR" -> String.format("设备%s的接口发生CRC错误", deviceId);
            default -> "未知告警描述";
        };
    }
    
    private String getAlarmImpact(String level) {
        return switch (level) {
            case "CRITICAL" -> "严重影响业务，需立即处理";
            case "MAJOR" -> "影响部分业务功能";
            case "MINOR" -> "可能影响业务性能";
            case "WARNING" -> "需关注，暂不影响业务";
            default -> "无明显影响";
        };
    }
    
    private void initConfigs() {
        for (String deviceId : devices.keySet()) {
            DeviceConfig config = DeviceConfig.builder()
                    .deviceId(deviceId)
                    .configVersion("v1.0." + deviceId.hashCode() % 100)
                    .configData(Map.of(
                            "hostname", "device-" + deviceId,
                            "enableSNMP", true,
                            "snmpCommunity", "public",
                            "enableSSH", true,
                            "sshPort", 22,
                            "ntpServer", "ntp.company.com",
                            "syslogServer", "192.168.1.100"
                    ))
                    .isCurrent(true)
                    .createTime(LocalDateTime.now().minusDays(30))
                    .lastModifyTime(LocalDateTime.now().minusDays(1))
                    .modifiedBy("admin")
                    .description("标准配置模板")
                    .backupPath("/backup/" + deviceId + "/config_latest.txt")
                    .build();
            configs.put(deviceId, config);
        }
    }
    
    private void initPolicies() {
        String[] protocols = {"TCP", "UDP", "ICMP", "HTTP", "HTTPS", "SSH", "FTP"};
        String[] actions = {"ALLOW", "DENY", "LOG"};
        String[] routingModes = {"DIRECT", "NAT", "PROXY", "VPN"};
        String[] trafficTypes = {"VOICE", "VIDEO", "DATA", "MANAGEMENT", "DEFAULT"};
        String[] dropPolicies = {"TAIL_DROP", "WRED", "PRIORITY_DROP"};
        
        for (String deviceId : devices.keySet()) {
            Device device = devices.get(deviceId);
            
            // 为每个设备生成安全策略
            List<DevicePolicy.SecurityPolicy> securityPolicies = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                String sourceIp = String.format("192.168.%d.%d", i, i * 2);
                securityPolicies.add(DevicePolicy.SecurityPolicy.builder()
                        .policyId(String.format("SEC%s%03d", deviceId.substring(3), i))
                        .policyName(getSecurityPolicyName(i))
                        .priority(i * 100)
                        .sourceIp(sourceIp)
                        .sourcePort(i == 1 ? "any" : String.valueOf(1000 + i * 100))
                        .destinationIp(String.format("10.0.%d.0/24", i))
                        .destinationPort(getDestinationPort(i))
                        .protocol(protocols[i % protocols.length])
                        .action(actions[i % actions.length])
                        .enabled(i != 3)
                        .description(String.format("安全策略%d - %s", i, getSecurityPolicyName(i)))
                        .hitCount(ThreadLocalRandom.current().nextLong(1000, 50000))
                        .lastHitTime(LocalDateTime.now().minusMinutes(i * 30))
                        .build());
            }
            
            // 为每个设备生成通信策略
            List<DevicePolicy.CommunicationPolicy> communicationPolicies = new ArrayList<>();
            for (int i = 1; i <= 3; i++) {
                communicationPolicies.add(DevicePolicy.CommunicationPolicy.builder()
                        .policyId(String.format("COM%s%03d", deviceId.substring(3), i))
                        .policyName(String.format("VLAN%d到VLAN%d通信", i * 10, i * 10 + 10))
                        .sourceVlan(i * 10)
                        .destinationVlan(i * 10 + 10)
                        .bandwidthLimit(1000 * i)
                        .allowed(true)
                        .routingMode(routingModes[i % routingModes.length])
                        .description(String.format("VLAN间通信策略%d", i))
                        .build());
            }
            
            // 为每个设备生成QoS策略
            List<DevicePolicy.QosPolicy> qosPolicies = new ArrayList<>();
            for (int i = 1; i <= 4; i++) {
                qosPolicies.add(DevicePolicy.QosPolicy.builder()
                        .policyId(String.format("QOS%s%03d", deviceId.substring(3), i))
                        .policyName(getQosPolicyName(i))
                        .trafficType(trafficTypes[i % trafficTypes.length])
                        .priorityQueue(7 - i)
                        .guaranteedBandwidth(100 * i)
                        .maxBandwidth(500 * i)
                        .dscpValue(getDscpValue(i))
                        .dropPolicy(dropPolicies[i % dropPolicies.length])
                        .description(String.format("QoS策略%d - %s流量优先级配置", i, trafficTypes[i % trafficTypes.length]))
                        .build());
            }
            
            DevicePolicy policy = DevicePolicy.builder()
                    .deviceId(deviceId)
                    .deviceName(device.getDeviceName())
                    .policyVersion("v2.1.0")
                    .lastUpdateTime(LocalDateTime.now().minusDays(1))
                    .securityPolicies(securityPolicies)
                    .communicationPolicies(communicationPolicies)
                    .qosPolicies(qosPolicies)
                    .build();
            
            policies.put(deviceId, policy);
        }
    }
    
    private String getSecurityPolicyName(int index) {
        return switch (index) {
            case 1 -> "允许内网访问";
            case 2 -> "Web服务访问控制";
            case 3 -> "SSH远程管理";
            case 4 -> "数据库访问限制";
            case 5 -> "默认拒绝策略";
            default -> "策略" + index;
        };
    }
    
    private String getDestinationPort(int index) {
        return switch (index) {
            case 1 -> "any";
            case 2 -> "80,443";
            case 3 -> "22";
            case 4 -> "3306,5432";
            case 5 -> "any";
            default -> "any";
        };
    }
    
    private String getQosPolicyName(int index) {
        return switch (index) {
            case 1 -> "语音优先";
            case 2 -> "视频会议保障";
            case 3 -> "业务数据传输";
            case 4 -> "管理流量";
            default -> "默认策略";
        };
    }
    
    private int getDscpValue(int index) {
        return switch (index) {
            case 1 -> 46;  // EF - 语音
            case 2 -> 34;  // AF41 - 视频
            case 3 -> 26;  // AF31 - 业务数据
            case 4 -> 16;  // CS2 - 管理
            default -> 0;
        };
    }
    
    private void initTopology() {
        // 创建核心-汇聚-接入的典型网络拓扑
        String[] linkTypes = {"FIBER", "COPPER"};
        int[] bandwidths = {10000, 1000, 100};
        
        // 核心到汇聚
        for (int i = 1; i <= 4; i++) {
            topologyLinks.add(TopologyLink.builder()
                    .linkId("LINK" + String.format("%03d", topologyLinks.size() + 1))
                    .sourceDeviceId("DEV001")
                    .sourceDeviceName("核心交换机-01")
                    .sourcePort("TenGigabitEthernet0/0/" + i)
                    .targetDeviceId(String.format("DEV%03d", i + 1))
                    .targetDeviceName(String.format("汇聚交换机-%02d", i))
                    .targetPort("TenGigabitEthernet0/0/1")
                    .linkType("FIBER")
                    .bandwidth(10000)
                    .linkStatus(i == 3 ? "DOWN" : "UP")
                    .utilization(ThreadLocalRandom.current().nextDouble(10, 70))
                    .build());
        }
        
        // 汇聚到接入
        for (int i = 2; i <= 5; i++) {
            for (int j = 1; j <= 3; j++) {
                int targetId = 5 + (i - 2) * 3 + j;
                if (targetId <= 20) {
                    topologyLinks.add(TopologyLink.builder()
                            .linkId("LINK" + String.format("%03d", topologyLinks.size() + 1))
                            .sourceDeviceId(String.format("DEV%03d", i))
                            .sourceDeviceName(String.format("汇聚交换机-%02d", i - 1))
                            .sourcePort("GigabitEthernet0/0/" + j)
                            .targetDeviceId(String.format("DEV%03d", targetId))
                            .targetDeviceName(String.format("接入交换机-%02d", targetId - 5))
                            .targetPort("GigabitEthernet0/0/24")
                            .linkType("COPPER")
                            .bandwidth(1000)
                            .linkStatus("UP")
                            .utilization(ThreadLocalRandom.current().nextDouble(20, 60))
                            .build());
                }
            }
        }
    }
    
    private void initLogs() {
        String[] levels = {"DEBUG", "INFO", "WARNING", "ERROR"};
        String[] modules = {"INTERFACE", "ROUTING", "SYSTEM", "SECURITY", "SNMP"};
        
        // 设备日志
        for (int i = 0; i < 100; i++) {
            String deviceId = String.format("DEV%03d", (i % 20) + 1);
            deviceLogs.add(LogEntry.builder()
                    .logId("LOG" + LocalDateTime.now().toLocalDate().toString().replace("-", "") + String.format("%06d", i))
                    .deviceId(deviceId)
                    .level(levels[i % levels.length])
                    .source("SYSLOG")
                    .module(modules[i % modules.length])
                    .message(String.format("设备%s的%s模块日志信息-%d", deviceId, modules[i % modules.length], i))
                    .timestamp(LocalDateTime.now().minusMinutes(i * 10))
                    .fields(Map.of("facility", "local7", "severity", i % 8))
                    .build());
        }
        
        // 系统日志
        for (int i = 0; i < 50; i++) {
            systemLogs.add(LogEntry.builder()
                    .logId("SYSLOG" + LocalDateTime.now().toLocalDate().toString().replace("-", "") + String.format("%06d", i))
                    .deviceId(null)
                    .level(levels[i % levels.length])
                    .source("NMS")
                    .module("SYSTEM")
                    .message(String.format("网管系统日志信息-%d", i))
                    .timestamp(LocalDateTime.now().minusMinutes(i * 5))
                    .fields(Map.of("component", "NMS-Core"))
                    .build());
        }
    }
    
    // ==================== 设备相关方法 ====================
    
    public List<Device> getAllDevices() {
        return new ArrayList<>(devices.values());
    }
    
    public List<Device> getDevicesByStatus(String status) {
        return devices.values().stream()
                .filter(d -> status == null || status.equalsIgnoreCase(d.getStatus()))
                .toList();
    }
    
    public Optional<Device> getDeviceById(String deviceId) {
        return Optional.ofNullable(devices.get(deviceId));
    }
    
    public Map<String, Object> getDeviceStatus(String deviceId) {
        Device device = devices.get(deviceId);
        if (device == null) {
            return null;
        }
        return Map.of(
                "deviceId", deviceId,
                "status", device.getStatus(),
                "cpuUsage", ThreadLocalRandom.current().nextDouble(10, 80),
                "memoryUsage", ThreadLocalRandom.current().nextDouble(20, 70),
                "uptime", "30 days, 12:34:56",
                "lastCheck", LocalDateTime.now()
        );
    }
    
    // ==================== 告警相关方法 ====================
    
    public List<Alarm> getAllAlarms() {
        return new ArrayList<>(alarms.values());
    }
    
    public List<Alarm> getAlarmsByFilter(String deviceId, String level, String status) {
        return alarms.values().stream()
                .filter(a -> deviceId == null || deviceId.equals(a.getDeviceId()))
                .filter(a -> level == null || level.equalsIgnoreCase(a.getLevel()))
                .filter(a -> status == null || status.equalsIgnoreCase(a.getStatus()))
                .toList();
    }
    
    public Optional<Alarm> getAlarmById(String alarmId) {
        return Optional.ofNullable(alarms.get(alarmId));
    }
    
    public boolean confirmAlarm(String alarmId, String confirmedBy) {
        Alarm alarm = alarms.get(alarmId);
        if (alarm == null || !"ACTIVE".equals(alarm.getStatus())) {
            return false;
        }
        alarm.setStatus("CONFIRMED");
        alarm.setConfirmTime(LocalDateTime.now());
        alarm.setConfirmedBy(confirmedBy);
        return true;
    }
    
    public boolean clearAlarm(String alarmId) {
        Alarm alarm = alarms.get(alarmId);
        if (alarm == null) {
            return false;
        }
        alarm.setStatus("CLEARED");
        alarm.setClearTime(LocalDateTime.now());
        return true;
    }
    
    // ==================== 性能相关方法 ====================
    
    public PerformanceData getCpuPerformance(String deviceId) {
        if (!devices.containsKey(deviceId)) {
            return null;
        }
        double current = ThreadLocalRandom.current().nextDouble(10, 85);
        return PerformanceData.builder()
                .deviceId(deviceId)
                .metricType("CPU")
                .currentValue(current)
                .avgValue(current - 5 + ThreadLocalRandom.current().nextDouble(0, 10))
                .maxValue(current + ThreadLocalRandom.current().nextDouble(5, 15))
                .minValue(current - ThreadLocalRandom.current().nextDouble(5, 15))
                .unit("%")
                .threshold(80.0)
                .overThreshold(current > 80)
                .collectTime(LocalDateTime.now())
                .healthStatus(current > 80 ? "WARNING" : "HEALTHY")
                .build();
    }
    
    public PerformanceData getMemoryPerformance(String deviceId) {
        if (!devices.containsKey(deviceId)) {
            return null;
        }
        double current = ThreadLocalRandom.current().nextDouble(20, 75);
        return PerformanceData.builder()
                .deviceId(deviceId)
                .metricType("MEMORY")
                .currentValue(current)
                .avgValue(current - 3 + ThreadLocalRandom.current().nextDouble(0, 6))
                .maxValue(current + ThreadLocalRandom.current().nextDouble(3, 10))
                .minValue(current - ThreadLocalRandom.current().nextDouble(3, 10))
                .unit("%")
                .threshold(85.0)
                .overThreshold(current > 85)
                .collectTime(LocalDateTime.now())
                .healthStatus(current > 85 ? "CRITICAL" : current > 70 ? "WARNING" : "HEALTHY")
                .build();
    }
    
    public PerformanceData getNetworkPerformance(String deviceId) {
        if (!devices.containsKey(deviceId)) {
            return null;
        }
        double current = ThreadLocalRandom.current().nextDouble(100, 5000);
        return PerformanceData.builder()
                .deviceId(deviceId)
                .metricType("NETWORK")
                .currentValue(current)
                .avgValue(current * 0.8)
                .maxValue(current * 1.5)
                .minValue(current * 0.3)
                .unit("Mbps")
                .threshold(8000.0)
                .overThreshold(current > 8000)
                .collectTime(LocalDateTime.now())
                .healthStatus(current > 8000 ? "WARNING" : "HEALTHY")
                .build();
    }
    
    public PerformanceData getDiskPerformance(String deviceId) {
        if (!devices.containsKey(deviceId)) {
            return null;
        }
        double current = ThreadLocalRandom.current().nextDouble(30, 70);
        return PerformanceData.builder()
                .deviceId(deviceId)
                .metricType("DISK")
                .currentValue(current)
                .avgValue(current)
                .maxValue(current + 5)
                .minValue(current - 5)
                .unit("%")
                .threshold(90.0)
                .overThreshold(current > 90)
                .collectTime(LocalDateTime.now())
                .healthStatus(current > 90 ? "CRITICAL" : current > 80 ? "WARNING" : "HEALTHY")
                .build();
    }
    
    // ==================== 配置相关方法 ====================
    
    public Optional<DeviceConfig> getDeviceConfig(String deviceId) {
        return Optional.ofNullable(configs.get(deviceId));
    }
    
    public DeviceConfig updateDeviceConfig(String deviceId, Map<String, Object> newConfig) {
        DeviceConfig config = configs.get(deviceId);
        if (config == null) {
            return null;
        }
        Map<String, Object> merged = new HashMap<>(config.getConfigData());
        merged.putAll(newConfig);
        config.setConfigData(merged);
        config.setLastModifyTime(LocalDateTime.now());
        config.setConfigVersion("v1." + (config.getConfigVersion().hashCode() % 100 + 1));
        return config;
    }
    
    public String backupDeviceConfig(String deviceId) {
        DeviceConfig config = configs.get(deviceId);
        if (config == null) {
            return null;
        }
        String backupPath = String.format("/backup/%s/config_%s.txt", 
                deviceId, LocalDateTime.now().toString().replace(":", "-"));
        config.setBackupPath(backupPath);
        return backupPath;
    }
    
    // ==================== 拓扑相关方法 ====================
    
    public List<TopologyLink> getDeviceConnections(String deviceId) {
        return topologyLinks.stream()
                .filter(link -> deviceId.equals(link.getSourceDeviceId()) || 
                               deviceId.equals(link.getTargetDeviceId()))
                .toList();
    }
    
    public List<TopologyLink> getFullTopology() {
        return new ArrayList<>(topologyLinks);
    }
    
    // ==================== 日志相关方法 ====================
    
    public List<LogEntry> getDeviceLogs(String deviceId, String level, 
                                        LocalDateTime startTime, LocalDateTime endTime) {
        return deviceLogs.stream()
                .filter(log -> deviceId.equals(log.getDeviceId()))
                .filter(log -> level == null || level.equalsIgnoreCase(log.getLevel()))
                .filter(log -> startTime == null || !log.getTimestamp().isBefore(startTime))
                .filter(log -> endTime == null || !log.getTimestamp().isAfter(endTime))
                .toList();
    }
    
    public List<LogEntry> getSystemLogs(String level, LocalDateTime startTime, LocalDateTime endTime) {
        return systemLogs.stream()
                .filter(log -> level == null || level.equalsIgnoreCase(log.getLevel()))
                .filter(log -> startTime == null || !log.getTimestamp().isBefore(startTime))
                .filter(log -> endTime == null || !log.getTimestamp().isAfter(endTime))
                .toList();
    }
    
    // ==================== 策略相关方法 ====================
    
    public DevicePolicy getDevicePolicy(String deviceId) {
        return policies.get(deviceId);
    }
}
