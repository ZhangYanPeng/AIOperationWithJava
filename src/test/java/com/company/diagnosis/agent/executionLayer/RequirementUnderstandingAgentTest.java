package com.company.diagnosis.agent.executionLayer;

import com.company.diagnosis.agent.base.BaseIntelligentAgent;
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
 * RequirementUnderstandingAgent 单元测试
 * 
 * 测试范围：
 * - 正常需求理解流程
 * - 输入验证
 * - LLM调用和响应处理
 * - 错误处理
 * 
 * @author Diagnosis System
 * @since 2026-01-13
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RequirementUnderstandingAgent 单元测试")
class RequirementUnderstandingAgentTest {

    @Mock
    private KnowledgeService knowledgeService;

    @Mock
    private LlmClientRegistry llmClientRegistry;

    @Mock
    private LlmClient llmClient;

    private RequirementUnderstandingAgent agent;

    @BeforeEach
    void setUp() {
        agent = new RequirementUnderstandingAgent();
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
            assertThat(agent.getAgentName()).isEqualTo("RequirementUnderstandingAgent");
        }
    }

    // ==================== 输入验证测试 ====================
    
    @Nested
    @DisplayName("输入验证测试")
    class InputValidationTests {
        
        @Test
        @DisplayName("当输入文本为空时应该返回错误")
        void shouldReturnErrorWhenInputTextIsEmpty() {
            // Given
            Map<String, Object> input = new HashMap<>();
            input.put("input_text", "");
            
            // When & Then
            StepVerifier.create(agent.process(input))
                    .assertNext(result -> {
                        assertThat(result).containsEntry("success", false);
                        assertThat(result.get("error").toString()).contains("输入文本不能为空");
                    })
                    .verifyComplete();
        }
        
        @Test
        @DisplayName("当输入文本为null时应该返回错误")
        void shouldReturnErrorWhenInputTextIsNull() {
            // Given
            Map<String, Object> input = new HashMap<>();
            // input_text not set
            
            // When & Then
            StepVerifier.create(agent.process(input))
                    .assertNext(result -> {
                        assertThat(result).containsEntry("success", false);
                    })
                    .verifyComplete();
        }
        
        @Test
        @DisplayName("当输入文本只有空格时应该返回错误")
        void shouldReturnErrorWhenInputTextIsBlank() {
            // Given
            Map<String, Object> input = Map.of("input_text", "   ");
            
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
        @DisplayName("应该成功理解需求并返回结构化结果")
        void shouldUnderstandRequirementSuccessfully() {
            // Given
            Map<String, Object> input = Map.of(
                    "input_text", "诊断M机单播通道中断问题，设备ID为123",
                    "context", Map.of("parameter_memory", Map.of("region", "华东"))
            );
            
            Map<String, Object> llmResponseData = Map.of(
                    "task_objective", "诊断M机单播通道中断",
                    "key_constraints", List.of("设备ID=123", "检查网络连通性"),
                    "expected_output", "诊断结论+根因+建议"
            );
            
            when(llmClientRegistry.getDefaultClient()).thenReturn(llmClient);
            when(llmClient.generateJson(anyString(), anyString(), anyDouble()))
                    .thenReturn(Mono.just(LlmResponse.success(llmResponseData, "raw")));
            
            // When & Then
            StepVerifier.create(agent.process(input))
                    .assertNext(result -> {
                        assertThat(result).containsEntry("success", true);
                        assertThat(result).containsKey("task_objective");
                        assertThat(result).containsKey("key_constraints");
                        assertThat(result).containsKey("expected_output");
                        assertThat(result.get("task_objective")).isEqualTo("诊断M机单播通道中断");
                    })
                    .verifyComplete();
        }
        
        @Test
        @DisplayName("当LLM返回部分数据时应该使用默认值")
        void shouldUseDefaultsWhenLlmReturnsPartialData() {
            // Given
            Map<String, Object> input = Map.of("input_text", "测试诊断需求");
            
            // LLM只返回task_objective
            Map<String, Object> llmResponseData = Map.of(
                    "task_objective", "测试任务"
            );
            
            when(llmClientRegistry.getDefaultClient()).thenReturn(llmClient);
            when(llmClient.generateJson(anyString(), anyString(), anyDouble()))
                    .thenReturn(Mono.just(LlmResponse.success(llmResponseData, "raw")));
            
            // When & Then
            StepVerifier.create(agent.process(input))
                    .assertNext(result -> {
                        assertThat(result).containsEntry("success", true);
                        assertThat(result.get("key_constraints")).isInstanceOf(List.class);
                        assertThat(result.get("expected_output")).isNotNull();
                    })
                    .verifyComplete();
        }
    }

    // ==================== LLM错误处理测试 ====================
    
    @Nested
    @DisplayName("LLM错误处理测试")
    class LlmErrorHandlingTests {
        
        @Test
        @DisplayName("当LLM调用失败时应该返回错误响应")
        void shouldReturnErrorWhenLlmCallFails() {
            // Given
            Map<String, Object> input = Map.of("input_text", "测试输入");
            
            when(llmClientRegistry.getDefaultClient()).thenReturn(llmClient);
            when(llmClient.generateJson(anyString(), anyString(), anyDouble()))
                    .thenReturn(Mono.just(LlmResponse.error("API限流", "rate_limit")));
            
            // When & Then
            StepVerifier.create(agent.process(input))
                    .assertNext(result -> {
                        assertThat(result).containsEntry("success", false);
                        assertThat(result.get("error").toString()).contains("需求理解失败");
                    })
                    .verifyComplete();
        }
        
        @Test
        @DisplayName("当LLM返回空数据时应该返回错误响应")
        void shouldReturnErrorWhenLlmReturnsNullData() {
            // Given
            Map<String, Object> input = Map.of("input_text", "测试输入");
            
            LlmResponse emptyDataResponse = LlmResponse.builder()
                    .success(true)
                    .data(null)
                    .build();
            
            when(llmClientRegistry.getDefaultClient()).thenReturn(llmClient);
            when(llmClient.generateJson(anyString(), anyString(), anyDouble()))
                    .thenReturn(Mono.just(emptyDataResponse));
            
            // When & Then
            StepVerifier.create(agent.process(input))
                    .assertNext(result -> {
                        assertThat(result).containsEntry("success", false);
                        assertThat(result.get("error").toString()).contains("LLM返回数据为空");
                    })
                    .verifyComplete();
        }
        
        @Test
        @DisplayName("当LLM调用抛出异常时应该优雅处理")
        void shouldHandleLlmException() {
            // Given
            Map<String, Object> input = Map.of("input_text", "测试输入");
            
            when(llmClientRegistry.getDefaultClient()).thenReturn(llmClient);
            when(llmClient.generateJson(anyString(), anyString(), anyDouble()))
                    .thenReturn(Mono.error(new RuntimeException("网络连接失败")));
            
            // When & Then
            StepVerifier.create(agent.process(input))
                    .assertNext(result -> {
                        assertThat(result).containsEntry("success", false);
                        assertThat(result.get("error").toString()).contains("需求理解");
                    })
                    .verifyComplete();
        }
    }

    // ==================== 上下文处理测试 ====================
    
    @Nested
    @DisplayName("上下文处理测试")
    class ContextProcessingTests {
        
        @Test
        @DisplayName("应该正确处理包含历史步骤的上下文")
        void shouldProcessContextWithHistorySteps() {
            // Given
            Map<String, Object> context = new HashMap<>();
            context.put("history_steps", List.of("step1", "step2"));
            context.put("parameter_memory", Map.of("deviceId", "123"));
            
            Map<String, Object> input = Map.of(
                    "input_text", "继续诊断",
                    "context", context
            );
            
            Map<String, Object> llmResponseData = Map.of(
                    "task_objective", "继续诊断任务",
                    "key_constraints", List.of(),
                    "expected_output", "诊断结论"
            );
            
            when(llmClientRegistry.getDefaultClient()).thenReturn(llmClient);
            when(llmClient.generateJson(anyString(), anyString(), anyDouble()))
                    .thenReturn(Mono.just(LlmResponse.success(llmResponseData, "raw")));
            
            // When & Then
            StepVerifier.create(agent.process(input))
                    .assertNext(result -> {
                        assertThat(result).containsEntry("success", true);
                    })
                    .verifyComplete();
            
            // 验证LLM被调用，并且prompt包含了上下文信息
            verify(llmClient).generateJson(
                    argThat(prompt -> prompt.contains("已知参数")),
                    anyString(),
                    anyDouble()
            );
        }
        
        @Test
        @DisplayName("当没有上下文时应该正常处理")
        void shouldProcessWithoutContext() {
            // Given
            Map<String, Object> input = Map.of("input_text", "简单诊断需求");
            
            Map<String, Object> llmResponseData = Map.of(
                    "task_objective", "简单诊断",
                    "key_constraints", List.of(),
                    "expected_output", "诊断结论"
            );
            
            when(llmClientRegistry.getDefaultClient()).thenReturn(llmClient);
            when(llmClient.generateJson(anyString(), anyString(), anyDouble()))
                    .thenReturn(Mono.just(LlmResponse.success(llmResponseData, "raw")));
            
            // When & Then
            StepVerifier.create(agent.process(input))
                    .assertNext(result -> {
                        assertThat(result).containsEntry("success", true);
                    })
                    .verifyComplete();
        }
    }
}
