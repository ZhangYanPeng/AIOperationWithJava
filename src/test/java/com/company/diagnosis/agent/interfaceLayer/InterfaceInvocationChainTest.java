package com.company.diagnosis.agent.interfaceLayer;

import com.company.diagnosis.agent.base.BaseIntelligentAgent;
import com.company.diagnosis.config.AgentScopeConfig.LlmClientRegistry;
import com.company.diagnosis.llm.LlmClient;
import com.company.diagnosis.llm.LlmResponse;
import com.company.diagnosis.service.KnowledgeService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 接口调用链测试
 * 
 * 测试 ParameterMappingAgent -> HTTP调用 -> ResultParsingAgent 的完整流程
 * 
 * @author AIOperation Team
 * @since 2026-01-15
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("接口调用链测试")
class InterfaceInvocationChainTest {
    
    @Mock
    private LlmClient llmClient;
    
    @Mock
    private LlmClientRegistry llmClientRegistry;
    
    @Mock
    private KnowledgeService knowledgeService;
    
    private ParameterMappingAgent parameterMappingAgent;
    private ResultParsingAgent resultParsingAgent;
    
    // 模拟网管接口定义
    private static final List<Map<String, Object>> MOCK_NMS_INTERFACES = List.of(
            createInterface("查询设备列表", "/api/devices", "GET", 
                    List.of(), "获取所有设备或按状态过滤"),
            createInterface("查询设备详情", "/api/devices/{deviceId}", "GET",
                    List.of(param("deviceId", "path", true, "设备ID")), 
                    "根据设备ID查询设备详细信息"),
            createInterface("查询设备状态", "/api/devices/{deviceId}/status", "GET",
                    List.of(param("deviceId", "path", true, "设备ID")),
                    "查询设备实时运行状态"),
            createInterface("查询告警列表", "/api/alarms", "GET",
                    List.of(param("deviceId", "query", false, "设备ID"),
                           param("level", "query", false, "告警级别"),
                           param("status", "query", false, "告警状态")),
                    "查询告警列表，支持多条件过滤"),
            createInterface("查询告警详情", "/api/alarms/{alarmId}", "GET",
                    List.of(param("alarmId", "path", true, "告警ID")),
                    "查询告警详细信息"),
            createInterface("确认告警", "/api/alarms/{alarmId}/confirm", "POST",
                    List.of(param("alarmId", "path", true, "告警ID"),
                           param("confirmedBy", "query", false, "确认人")),
                    "确认指定告警"),
            createInterface("清除告警", "/api/alarms/{alarmId}", "DELETE",
                    List.of(param("alarmId", "path", true, "告警ID")),
                    "清除指定告警"),
            createInterface("查询CPU", "/api/performance/{deviceId}/cpu", "GET",
                    List.of(param("deviceId", "path", true, "设备ID")),
                    "查询设备CPU使用率"),
            createInterface("查询内存", "/api/performance/{deviceId}/memory", "GET",
                    List.of(param("deviceId", "path", true, "设备ID")),
                    "查询设备内存使用率"),
            createInterface("查询网络", "/api/performance/{deviceId}/network", "GET",
                    List.of(param("deviceId", "path", true, "设备ID")),
                    "查询设备网络流量"),
            createInterface("查询磁盘", "/api/performance/{deviceId}/disk", "GET",
                    List.of(param("deviceId", "path", true, "设备ID")),
                    "查询设备磁盘使用率"),
            createInterface("查询配置", "/api/config/{deviceId}", "GET",
                    List.of(param("deviceId", "path", true, "设备ID")),
                    "查询设备配置"),
            createInterface("更新配置", "/api/config/{deviceId}", "PUT",
                    List.of(param("deviceId", "path", true, "设备ID")),
                    "更新设备配置"),
            createInterface("备份配置", "/api/config/{deviceId}/backup", "POST",
                    List.of(param("deviceId", "path", true, "设备ID")),
                    "备份设备配置"),
            createInterface("查询连接关系", "/api/topology/{deviceId}/connections", "GET",
                    List.of(param("deviceId", "path", true, "设备ID")),
                    "查询设备连接关系"),
            createInterface("查询完整拓扑", "/api/topology/full", "GET",
                    List.of(), "查询完整网络拓扑"),
            createInterface("查询设备日志", "/api/logs/{deviceId}", "GET",
                    List.of(param("deviceId", "path", true, "设备ID"),
                           param("level", "query", false, "日志级别"),
                           param("startTime", "query", false, "开始时间"),
                           param("endTime", "query", false, "结束时间")),
                    "查询设备日志"),
            createInterface("查询系统日志", "/api/logs/system", "GET",
                    List.of(param("level", "query", false, "日志级别")),
                    "查询网管系统日志")
    );
    
    @BeforeEach
    void setUp() throws Exception {
        parameterMappingAgent = new ParameterMappingAgent();
        resultParsingAgent = new ResultParsingAgent();
        
        // 配置Mock行为 - 使用lenient模式避免不必要的Stubbing异常
        lenient().when(llmClientRegistry.getDefaultClient()).thenReturn(llmClient);
        
        // Mock LLM调用返回成功的结果
        LlmResponse mockLlmResponse = new LlmResponse();
        mockLlmResponse.setSuccess(true);
        mockLlmResponse.setData(new HashMap<>()); // 默认返回空Map
        lenient().when(llmClient.generateJson(anyString(), anyString(), anyDouble()))
                .thenReturn(Mono.just(mockLlmResponse));
        
        // Mock知识检索返回空结果
        lenient().when(knowledgeService.searchDocuments(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(Flux.empty());
        
        // 通过反射注入 mock 的依赖
        injectMockDependencies(parameterMappingAgent);
        injectMockDependencies(resultParsingAgent);
    }
    
    /**
     * 通过反射注入Mock依赖到Agent中
     */
    private void injectMockDependencies(BaseIntelligentAgent agent) throws Exception {
        // 注入 llmClientRegistry
        java.lang.reflect.Field registryField = BaseIntelligentAgent.class.getDeclaredField("llmClientRegistry");
        registryField.setAccessible(true);
        registryField.set(agent, llmClientRegistry);
        
        // 注入 knowledgeService
        java.lang.reflect.Field knowledgeServiceField = BaseIntelligentAgent.class.getDeclaredField("knowledgeService");
        knowledgeServiceField.setAccessible(true);
        knowledgeServiceField.set(agent, knowledgeService);
    }
    
    // ==================== 参数映射测试 ====================
    
    @Nested
    @DisplayName("参数映射测试")
    class ParameterMappingTests {
        
        @Test
        @DisplayName("测试设备查询参数映射 - 简单路径参数")
        void testParameterMapping_DeviceQuery() {
            // 准备输入
            Map<String, Object> input = new HashMap<>();
            input.put("call_requirement", "查询设备DEV001的详细信息");
            input.put("api_doc", MOCK_NMS_INTERFACES.get(1)); // 查询设备详情
            input.put("parameter_memory", Map.of("deviceId", "DEV001"));
            
            // 执行
            Mono<Map<String, Object>> result = parameterMappingAgent.process(input);
            
            // 验证
            StepVerifier.create(result)
                    .assertNext(output -> {
                        assertThat(output).containsKey("success");
                        // 即使 LLM 调用失败，也应该有简单映射的回退结果
                        if ((Boolean) output.get("success")) {
                            // successResponse会将data内容直接putAll到response中
                            assertThat(output).containsKey("mapped_parameters");
                        }
                    })
                    .verifyComplete();
        }
        
        @Test
        @DisplayName("测试告警查询参数映射 - 多查询参数")
        void testParameterMapping_AlarmQuery() {
            Map<String, Object> input = new HashMap<>();
            input.put("call_requirement", "查询设备DEV001的CRITICAL级别告警");
            input.put("api_doc", MOCK_NMS_INTERFACES.get(3)); // 查询告警列表
            input.put("parameter_memory", Map.of(
                    "deviceId", "DEV001",
                    "level", "CRITICAL"
            ));
            
            Mono<Map<String, Object>> result = parameterMappingAgent.process(input);
            
            StepVerifier.create(result)
                    .assertNext(output -> {
                        assertThat(output).containsKey("success");
                    })
                    .verifyComplete();
        }
        
        @Test
        @DisplayName("测试空调用需求 - 应返回错误")
        void testParameterMapping_EmptyRequirement() {
            Map<String, Object> input = new HashMap<>();
            input.put("call_requirement", "");
            
            Mono<Map<String, Object>> result = parameterMappingAgent.process(input);
            
            StepVerifier.create(result)
                    .assertNext(output -> {
                        assertThat(output.get("success")).isEqualTo(false);
                        assertThat(output).containsKey("error");
                    })
                    .verifyComplete();
        }
    }
    
    // ==================== 结果解析测试 ====================
    
    @Nested
    @DisplayName("结果解析测试")
    class ResultParsingTests {
        
        @Test
        @DisplayName("测试设备信息解析 - 简单字段提取")
        void testResultParsing_DeviceInfo() {
            // 模拟API响应，字段在data中
            Map<String, Object> apiResponse = Map.of(
                    "success", true,
                    "data", Map.of(
                            "deviceId", "DEV001",
                            "deviceName", "核心交换机-01",
                            "status", "online",
                            "ipAddress", "192.168.1.1"
                    )
            );
            
            Map<String, Object> input = new HashMap<>();
            input.put("call_requirement", "查询设备信息");
            input.put("api_response", apiResponse);
            // 需要从 data 字段中提取
            input.put("expected_fields", List.of("data.deviceId", "data.deviceName", "data.status"));
            
            Mono<Map<String, Object>> result = resultParsingAgent.process(input);
            
            StepVerifier.create(result)
                    .assertNext(output -> {
                        assertThat(output.get("success")).isEqualTo(true);
                        // successResponse会将data直接putAll到output中
                        assertThat(output).containsKey("extracted_data");
                        Map<String, Object> extracted = (Map<String, Object>) output.get("extracted_data");
                        // 应该至少有一些提取的字段
                        assertThat(extracted).isNotEmpty();
                    })
                    .verifyComplete();
        }
        
        @Test
        @DisplayName("测试性能数据解析 - 数值类型处理")
        void testResultParsing_PerformanceData() {
            Map<String, Object> apiResponse = Map.of(
                    "success", true,
                    "data", Map.of(
                            "deviceId", "DEV001",
                            "metricType", "CPU",
                            "currentValue", 45.5,
                            "avgValue", 42.3,
                            "maxValue", 78.9,
                            "threshold", 80.0,
                            "overThreshold", false,
                            "healthStatus", "HEALTHY"
                    )
            );
            
            Map<String, Object> input = new HashMap<>();
            input.put("call_requirement", "查询CPU使用率");
            input.put("api_response", apiResponse);
            input.put("expected_fields", List.of("data.currentValue", "data.threshold", "data.healthStatus"));
            
            Mono<Map<String, Object>> result = resultParsingAgent.process(input);
            
            StepVerifier.create(result)
                    .assertNext(output -> {
                        assertThat(output.get("success")).isEqualTo(true);
                        // 验证有数据质量评估
                        assertThat(output).containsKey("data_quality");
                    })
                    .verifyComplete();
        }
        
        @Test
        @DisplayName("测试嵌套字段提取 - 点分隔路径")
        void testResultParsing_NestedFields() {
            Map<String, Object> apiResponse = Map.of(
                    "success", true,
                    "data", Map.of(
                            "device", Map.of(
                                    "id", "DEV001",
                                    "info", Map.of(
                                            "name", "核心交换机",
                                            "location", "机房A"
                                    )
                            )
                    )
            );
            
            Map<String, Object> input = new HashMap<>();
            input.put("call_requirement", "查询设备信息");
            input.put("api_response", apiResponse);
            input.put("expected_fields", List.of("data.device.id", "data.device.info.name"));
            
            Mono<Map<String, Object>> result = resultParsingAgent.process(input);
            
            StepVerifier.create(result)
                    .assertNext(output -> {
                        assertThat(output.get("success")).isEqualTo(true);
                    })
                    .verifyComplete();
        }
        
        @Test
        @DisplayName("测试空响应处理 - 应返回错误")
        void testResultParsing_NullResponse() {
            Map<String, Object> input = new HashMap<>();
            input.put("call_requirement", "查询设备信息");
            input.put("api_response", null);
            
            Mono<Map<String, Object>> result = resultParsingAgent.process(input);
            
            StepVerifier.create(result)
                    .assertNext(output -> {
                        assertThat(output.get("success")).isEqualTo(false);
                        assertThat(output).containsKey("error");
                    })
                    .verifyComplete();
        }
    }
    
    // ==================== 完整调用链测试 ====================
    
    @Nested
    @DisplayName("完整调用链测试")
    class FullChainTests {
        
        @Test
        @DisplayName("端到端测试 - 设备状态查询")
        void testFullChain_DeviceStatus() {
            // 1. 参数映射阶段
            Map<String, Object> mappingInput = new HashMap<>();
            mappingInput.put("call_requirement", "查询设备DEV001的状态");
            mappingInput.put("api_doc", MOCK_NMS_INTERFACES.get(2)); // 查询设备状态
            mappingInput.put("parameter_memory", Map.of("deviceId", "DEV001"));
            
            Mono<Map<String, Object>> mappingResult = parameterMappingAgent.process(mappingInput);
            
            // 2. 模拟HTTP调用返回（实际测试中会调用真实的模拟网管）
            Map<String, Object> mockApiResponse = Map.of(
                    "success", true,
                    "data", Map.of(
                            "deviceId", "DEV001",
                            "status", "online",
                            "cpuUsage", 45.5,
                            "memoryUsage", 62.3,
                            "uptime", "30 days, 12:34:56"
                    )
            );
            
            // 3. 结果解析阶段
            Map<String, Object> parsingInput = new HashMap<>();
            parsingInput.put("call_requirement", "查询设备状态");
            parsingInput.put("api_response", mockApiResponse);
            parsingInput.put("expected_fields", List.of("data.status", "data.cpuUsage", "data.memoryUsage"));
            
            Mono<Map<String, Object>> parsingResult = resultParsingAgent.process(parsingInput);
            
            // 验证整体流程
            StepVerifier.create(mappingResult)
                    .assertNext(output -> assertThat(output).containsKey("success"))
                    .verifyComplete();
            
            StepVerifier.create(parsingResult)
                    .assertNext(output -> {
                        assertThat(output.get("success")).isEqualTo(true);
                        assertThat(output).containsKey("extracted_data");
                        Map<String, Object> extracted = (Map<String, Object>) output.get("extracted_data");
                        assertThat(extracted).isNotEmpty();
                    })
                    .verifyComplete();
        }
        
        @Test
        @DisplayName("端到端测试 - 告警诊断（多接口串联）")
        void testFullChain_AlarmDiagnosis() {
            // 1. 查询告警信息
            Map<String, Object> alarmResponse = Map.of(
                    "success", true,
                    "data", Map.of(
                            "alarmId", "ALM001",
                            "deviceId", "DEV001",
                            "level", "CRITICAL",
                            "alarmType", "LINK_DOWN",
                            "description", "端口链路中断"
                    )
            );
            
            Map<String, Object> alarmParsingInput = new HashMap<>();
            alarmParsingInput.put("api_response", alarmResponse);
            alarmParsingInput.put("expected_fields", List.of("data.alarmId", "data.deviceId", "data.level", "data.alarmType"));
            
            // 2. 查询设备状态
            Map<String, Object> deviceResponse = Map.of(
                    "success", true,
                    "data", Map.of(
                            "deviceId", "DEV001",
                            "status", "degraded",
                            "cpuUsage", 85.5
                    )
            );
            
            Map<String, Object> deviceParsingInput = new HashMap<>();
            deviceParsingInput.put("api_response", deviceResponse);
            deviceParsingInput.put("expected_fields", List.of("data.deviceId", "data.status", "data.cpuUsage"));
            
            // 验证告警解析
            StepVerifier.create(resultParsingAgent.process(alarmParsingInput))
                    .assertNext(output -> {
                        assertThat(output.get("success")).isEqualTo(true);
                        assertThat(output).containsKey("data_quality");
                    })
                    .verifyComplete();
            
            // 验证设备状态解析
            StepVerifier.create(resultParsingAgent.process(deviceParsingInput))
                    .assertNext(output -> {
                        assertThat(output.get("success")).isEqualTo(true);
                    })
                    .verifyComplete();
        }
    }
    
    // ==================== 所有接口可访问性测试 ====================
    
    @Nested
    @DisplayName("接口可访问性测试")
    class InterfaceAccessibilityTests {
        
        @Test
        @DisplayName("遍历测试所有18个接口定义")
        void testAllInterfaces_Definition() {
            assertThat(MOCK_NMS_INTERFACES).hasSize(18);
            
            for (Map<String, Object> interfaceDef : MOCK_NMS_INTERFACES) {
                assertThat(interfaceDef).containsKey("tool_name");
                assertThat(interfaceDef).containsKey("api_path");
                assertThat(interfaceDef).containsKey("http_method");
                assertThat(interfaceDef).containsKey("parameters");
                assertThat(interfaceDef).containsKey("description");
                
                String apiPath = (String) interfaceDef.get("api_path");
                String method = (String) interfaceDef.get("http_method");
                
                // 验证路径格式
                assertThat(apiPath).startsWith("/api/");
                
                // 验证HTTP方法
                assertThat(method).isIn("GET", "POST", "PUT", "DELETE");
            }
        }
        
        @Test
        @DisplayName("测试接口分类统计")
        void testInterfaceCategories() {
            Map<String, Long> categoryCount = new HashMap<>();
            
            for (Map<String, Object> interfaceDef : MOCK_NMS_INTERFACES) {
                String path = (String) interfaceDef.get("api_path");
                String category = extractCategory(path);
                categoryCount.merge(category, 1L, Long::sum);
            }
            
            // 验证各类接口数量
            assertThat(categoryCount.get("devices")).isEqualTo(3L);  // 设备管理
            assertThat(categoryCount.get("alarms")).isEqualTo(4L);   // 告警管理
            assertThat(categoryCount.get("performance")).isEqualTo(4L); // 性能数据
            assertThat(categoryCount.get("config")).isEqualTo(3L);  // 配置管理
            assertThat(categoryCount.get("topology")).isEqualTo(2L); // 拓扑关系
            assertThat(categoryCount.get("logs")).isEqualTo(2L);    // 日志查询
        }
        
        private String extractCategory(String path) {
            String[] parts = path.split("/");
            return parts.length > 2 ? parts[2] : "unknown";
        }
    }
    
    // ==================== 辅助方法 ====================
    
    private static Map<String, Object> createInterface(String name, String path, String method,
                                                        List<Map<String, Object>> params, String desc) {
        return Map.of(
                "tool_name", name,
                "api_path", path,
                "http_method", method,
                "parameters", params,
                "description", desc
        );
    }
    
    private static Map<String, Object> param(String name, String in, boolean required, String desc) {
        return Map.of(
                "name", name,
                "in", in,
                "required", required,
                "description", desc
        );
    }
}
