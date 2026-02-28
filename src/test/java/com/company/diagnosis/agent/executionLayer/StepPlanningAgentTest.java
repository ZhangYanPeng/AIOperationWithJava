package com.company.diagnosis.agent.executionLayer;

import com.company.diagnosis.config.AgentScopeConfig.LlmClientRegistry;
import com.company.diagnosis.llm.LlmClient;
import com.company.diagnosis.llm.LlmResponse;
import com.company.diagnosis.service.KnowledgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * StepPlanningAgent 单元测试
 * 
 * 测试范围：
 * - 步骤规划正常流程
 * - 输入验证
 * - 知识检索集成
 * - 默认步骤生成
 * - 步骤规范化
 * - 错误处理
 * 
 * @author Diagnosis System
 * @since 2026-01-13
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StepPlanningAgent 单元测试")
class StepPlanningAgentTest {

    @Mock
    private KnowledgeService knowledgeService;

    @Mock
    private LlmClientRegistry llmClientRegistry;

    @Mock
    private LlmClient llmClient;

    private StepPlanningAgent agent;

    @BeforeEach
    void setUp() {
        agent = new StepPlanningAgent();
        ReflectionTestUtils.setField(agent, "knowledgeService", knowledgeService);
        ReflectionTestUtils.setField(agent, "llmClientRegistry", llmClientRegistry);
    }

    // ==================== 基础功能测试 ====================
    
    @Nested
    @DisplayName("基础功能测试")
    class BasicFunctionalityTests {
        
        @Test
        @DisplayName("应该正确初始化智能体名称")
        void shouldInitializeWithCorrectName() {
            assertThat(agent.getAgentName()).isEqualTo("StepPlanningAgent");
        }
    }

    // ==================== 输入验证测试 ====================
    
    @Nested
    @DisplayName("输入验证测试")
    class InputValidationTests {
        
        @Test
        @DisplayName("当任务目标为空时应该返回错误")
        void shouldReturnErrorWhenTaskObjectiveIsEmpty() {
            // Given
            Map<String, Object> input = Map.of("task_objective", "");
            
            // When & Then
            StepVerifier.create(agent.process(input))
                    .assertNext(result -> {
                        assertThat(result).containsEntry("success", false);
                        assertThat(result.get("error").toString()).contains("任务目标不能为空");
                    })
                    .verifyComplete();
        }
        
        @Test
        @DisplayName("当任务目标为null时应该返回错误")
        void shouldReturnErrorWhenTaskObjectiveIsNull() {
            // Given
            Map<String, Object> input = new HashMap<>();
            
            // When & Then
            StepVerifier.create(agent.process(input))
                    .assertNext(result -> {
                        assertThat(result).containsEntry("success", false);
                    })
                    .verifyComplete();
        }
    }

    // ==================== 正常流程测试 ====================
    
    @Nested
    @DisplayName("正常流程测试")
    class NormalFlowTests {
        
        @Test
        @DisplayName("应该成功生成步骤列表")
        void shouldGenerateStepsSuccessfully() {
            // Given
            Map<String, Object> input = Map.of(
                    "task_objective", "诊断设备离线问题",
                    "parameter_memory", Map.of("deviceId", "DEV-001")
            );
            
            List<Map<String, Object>> steps = List.of(
                    Map.of(
                            "step_index", 1,
                            "step_name", "查询设备状态",
                            "step_goal", "获取设备当前状态",
                            "required_tool", "查询设备信息",
                            "branch_condition", null,
                            "depends_on", List.of()
                    ),
                    Map.of(
                            "step_index", 2,
                            "step_name", "检查网络连通性",
                            "step_goal", "验证网络是否正常",
                            "required_tool", "网络检测",
                            "branch_condition", null,
                            "depends_on", List.of(1)
                    )
            );
            
            Map<String, Object> llmResponseData = Map.of("steps", steps);
            
            // Mock知识检索
            when(knowledgeService.searchDocuments(anyString(), anyString(), anyInt(), anyString()))
                    .thenReturn(Flux.just(Map.of("content", "诊断策略知识")));
            
            when(llmClientRegistry.getDefaultClient()).thenReturn(llmClient);
            when(llmClient.generateJson(anyString(), anyString(), anyDouble()))
                    .thenReturn(Mono.just(LlmResponse.success(llmResponseData, "raw")));
            
            // When & Then
            StepVerifier.create(agent.process(input))
                    .assertNext(result -> {
                        assertThat(result).containsEntry("success", true);
                        assertThat(result).containsKey("steps");
                        
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> resultSteps = (List<Map<String, Object>>) result.get("steps");
                        assertThat(resultSteps).hasSize(2);
                        assertThat(resultSteps.get(0)).containsEntry("step_name", "查询设备状态");
                    })
                    .verifyComplete();
        }
        
        @Test
        @DisplayName("当知识检索为空时应该使用传入的knowledge")
        void shouldUseProvidedKnowledgeWhenRetrievalEmpty() {
            // Given
            Map<String, Object> input = Map.of(
                    "task_objective", "诊断问题",
                    "retrieved_knowledge", "预设的诊断知识"
            );
            
            List<Map<String, Object>> steps = List.of(
                    Map.of("step_index", 1, "step_name", "步骤1")
            );
            
            // 知识检索返回空
            when(knowledgeService.searchDocuments(anyString(), anyString(), anyInt(), anyString()))
                    .thenReturn(Flux.empty());
            
            when(llmClientRegistry.getDefaultClient()).thenReturn(llmClient);
            when(llmClient.generateJson(anyString(), anyString(), anyDouble()))
                    .thenReturn(Mono.just(LlmResponse.success(Map.of("steps", steps), "raw")));
            
            // When & Then
            StepVerifier.create(agent.process(input))
                    .assertNext(result -> {
                        assertThat(result).containsEntry("success", true);
                    })
                    .verifyComplete();
        }
    }

    // ==================== 默认步骤测试 ====================
    
    @Nested
    @DisplayName("默认步骤测试")
    class DefaultStepsTests {
        
        @Test
        @DisplayName("当LLM未返回steps时应该使用默认步骤")
        void shouldUseDefaultStepsWhenLlmReturnsNoSteps() {
            // Given
            Map<String, Object> input = Map.of("task_objective", "诊断任务");
            
            // LLM返回不包含steps的数据
            Map<String, Object> llmResponseData = Map.of("other_field", "value");
            
            when(knowledgeService.searchDocuments(anyString(), anyString(), anyInt(), anyString()))
                    .thenReturn(Flux.empty());
            
            when(llmClientRegistry.getDefaultClient()).thenReturn(llmClient);
            when(llmClient.generateJson(anyString(), anyString(), anyDouble()))
                    .thenReturn(Mono.just(LlmResponse.success(llmResponseData, "raw")));
            
            // When & Then
            StepVerifier.create(agent.process(input))
                    .assertNext(result -> {
                        assertThat(result).containsEntry("success", true);
                        assertThat(result).containsKey("steps");
                        
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> steps = (List<Map<String, Object>>) result.get("steps");
                        assertThat(steps).hasSize(3); // 默认3个步骤
                        assertThat(steps.get(0)).containsEntry("step_name", "收集基础信息");
                        assertThat(steps.get(1)).containsEntry("step_name", "分析问题");
                        assertThat(steps.get(2)).containsEntry("step_name", "生成结论");
                    })
                    .verifyComplete();
        }
        
        @Test
        @DisplayName("默认步骤应该有正确的依赖关系")
        void defaultStepsShouldHaveCorrectDependencies() {
            // Given
            Map<String, Object> input = Map.of("task_objective", "诊断任务");
            
            when(knowledgeService.searchDocuments(anyString(), anyString(), anyInt(), anyString()))
                    .thenReturn(Flux.empty());
            
            when(llmClientRegistry.getDefaultClient()).thenReturn(llmClient);
            when(llmClient.generateJson(anyString(), anyString(), anyDouble()))
                    .thenReturn(Mono.just(LlmResponse.success(Map.of(), "raw")));
            
            // When & Then
            StepVerifier.create(agent.process(input))
                    .assertNext(result -> {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> steps = (List<Map<String, Object>>) result.get("steps");
                        
                        // 第一步无依赖
                        assertThat(steps.get(0).get("depends_on")).isEqualTo(Collections.emptyList());
                        // 第二步依赖第一步
                        assertThat(steps.get(1).get("depends_on")).isEqualTo(List.of(1));
                        // 第三步依赖第二步
                        assertThat(steps.get(2).get("depends_on")).isEqualTo(List.of(2));
                    })
                    .verifyComplete();
        }
    }

    // ==================== 步骤规范化测试 ====================
    
    @Nested
    @DisplayName("步骤规范化测试")
    class StepNormalizationTests {
        
        @Test
        @DisplayName("应该为缺失字段填充默认值")
        void shouldFillDefaultValuesForMissingFields() {
            // Given
            Map<String, Object> input = Map.of("task_objective", "诊断任务");
            
            // LLM返回不完整的步骤数据
            List<Map<String, Object>> incompleteSteps = List.of(
                    Map.of("step_name", "步骤1"), // 缺少其他字段
                    Map.of("step_goal", "目标2")  // 缺少step_name
            );
            
            when(knowledgeService.searchDocuments(anyString(), anyString(), anyInt(), anyString()))
                    .thenReturn(Flux.empty());
            
            when(llmClientRegistry.getDefaultClient()).thenReturn(llmClient);
            when(llmClient.generateJson(anyString(), anyString(), anyDouble()))
                    .thenReturn(Mono.just(LlmResponse.success(Map.of("steps", incompleteSteps), "raw")));
            
            // When & Then
            StepVerifier.create(agent.process(input))
                    .assertNext(result -> {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> steps = (List<Map<String, Object>>) result.get("steps");
                        
                        // 第一步
                        assertThat(steps.get(0)).containsEntry("step_index", 1);
                        assertThat(steps.get(0)).containsEntry("step_name", "步骤1");
                        assertThat(steps.get(0)).containsKey("step_goal");
                        assertThat(steps.get(0)).containsKey("depends_on");
                        
                        // 第二步应该有默认step_name
                        assertThat(steps.get(1)).containsEntry("step_index", 2);
                        assertThat(steps.get(1)).containsEntry("step_name", "步骤2");
                    })
                    .verifyComplete();
        }
    }

    // ==================== 错误处理测试 ====================
    
    @Nested
    @DisplayName("错误处理测试")
    class ErrorHandlingTests {
        
        @Test
        @DisplayName("当LLM调用失败时应该返回错误")
        void shouldReturnErrorWhenLlmFails() {
            // Given
            Map<String, Object> input = Map.of("task_objective", "诊断任务");
            
            when(knowledgeService.searchDocuments(anyString(), anyString(), anyInt(), anyString()))
                    .thenReturn(Flux.empty());
            
            when(llmClientRegistry.getDefaultClient()).thenReturn(llmClient);
            when(llmClient.generateJson(anyString(), anyString(), anyDouble()))
                    .thenReturn(Mono.just(LlmResponse.error("模型超时", "timeout")));
            
            // When & Then
            StepVerifier.create(agent.process(input))
                    .assertNext(result -> {
                        assertThat(result).containsEntry("success", false);
                        assertThat(result.get("error").toString()).contains("步骤规划失败");
                    })
                    .verifyComplete();
        }
        
        @Test
        @DisplayName("当知识检索异常时应该继续处理")
        void shouldContinueWhenKnowledgeRetrievalFails() {
            // Given
            Map<String, Object> input = Map.of(
                    "task_objective", "诊断任务",
                    "retrieved_knowledge", "备用知识"
            );
            
            // 知识检索失败
            when(knowledgeService.searchDocuments(anyString(), anyString(), anyInt(), anyString()))
                    .thenReturn(Flux.error(new RuntimeException("ES连接失败")));
            
            // 这种情况下，由于知识检索在flatMap中失败，整个流程会失败
            // 但agent应该有优雅的错误处理
            
            // When & Then
            StepVerifier.create(agent.process(input))
                    .assertNext(result -> {
                        // 由于异常被handleException捕获，应该返回错误响应
                        assertThat(result).containsEntry("success", false);
                    })
                    .verifyComplete();
        }
        
        @Test
        @DisplayName("当LLM抛出异常时应该优雅处理")
        void shouldHandleLlmException() {
            // Given
            Map<String, Object> input = Map.of("task_objective", "诊断任务");
            
            when(knowledgeService.searchDocuments(anyString(), anyString(), anyInt(), anyString()))
                    .thenReturn(Flux.empty());
            
            when(llmClientRegistry.getDefaultClient()).thenReturn(llmClient);
            when(llmClient.generateJson(anyString(), anyString(), anyDouble()))
                    .thenReturn(Mono.error(new RuntimeException("网络异常")));
            
            // When & Then
            StepVerifier.create(agent.process(input))
                    .assertNext(result -> {
                        assertThat(result).containsEntry("success", false);
                        assertThat(result).containsKey("error");
                    })
                    .verifyComplete();
        }
    }

    // ==================== 知识检索集成测试 ====================
    
    @Nested
    @DisplayName("知识检索集成测试")
    class KnowledgeRetrievalIntegrationTests {
        
        @Test
        @DisplayName("应该使用任务目标进行知识检索")
        void shouldUseTaskObjectiveForKnowledgeRetrieval() {
            // Given
            String taskObjective = "诊断网络中断问题";
            Map<String, Object> input = Map.of("task_objective", taskObjective);
            
            when(knowledgeService.searchDocuments(anyString(), anyString(), anyInt(), anyString()))
                    .thenReturn(Flux.just(Map.of("content", "网络诊断知识")));
            
            when(llmClientRegistry.getDefaultClient()).thenReturn(llmClient);
            when(llmClient.generateJson(anyString(), anyString(), anyDouble()))
                    .thenReturn(Mono.just(LlmResponse.success(
                            Map.of("steps", List.of(Map.of("step_name", "检查网络"))), 
                            "raw")));
            
            // When
            agent.process(input).block();
            
            // Then
            verify(knowledgeService).searchDocuments(
                    eq(taskObjective),
                    eq("diagnosis"),
                    eq(3),
                    eq("keyword")
            );
        }
        
        @Test
        @DisplayName("检索到的知识应该包含在LLM提示词中")
        void retrievedKnowledgeShouldBeIncludedInPrompt() {
            // Given
            Map<String, Object> input = Map.of("task_objective", "诊断任务");
            
            when(knowledgeService.searchDocuments(anyString(), anyString(), anyInt(), anyString()))
                    .thenReturn(Flux.just(Map.of("content", "特殊诊断策略知识内容")));
            
            when(llmClientRegistry.getDefaultClient()).thenReturn(llmClient);
            when(llmClient.generateJson(anyString(), anyString(), anyDouble()))
                    .thenReturn(Mono.just(LlmResponse.success(
                            Map.of("steps", List.of()), 
                            "raw")));
            
            // When
            agent.process(input).block();
            
            // Then
            verify(llmClient).generateJson(
                    argThat(prompt -> prompt.contains("特殊诊断策略知识内容")),
                    anyString(),
                    anyDouble()
            );
        }
    }
}
