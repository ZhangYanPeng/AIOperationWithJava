package com.company.diagnosis.agent.base;

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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * BaseIntelligentAgent 单元测试
 * 
 * 测试范围：
 * - 知识检索方法
 * - LLM调用方法
 * - 事件推送方法
 * - 错误处理方法
 * - 辅助方法
 * 
 * @author Diagnosis System
 * @since 2026-01-13
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BaseIntelligentAgent 单元测试")
class BaseIntelligentAgentTest {

    @Mock
    private KnowledgeService knowledgeService;

    @Mock
    private LlmClientRegistry llmClientRegistry;

    @Mock
    private LlmClient llmClient;

    private TestAgent testAgent;

    /**
     * 用于测试的具体Agent实现
     */
    static class TestAgent extends BaseIntelligentAgent {
        
        public TestAgent(String agentName) {
            super(agentName);
        }
        
        @Override
        public Mono<Map<String, Object>> process(Map<String, Object> input) {
            return Mono.just(successResponse(Map.of("processed", true)));
        }
        
        // 暴露 protected 方法用于测试
        public Mono<String> testRetrieveKnowledge(String query, String type, int topK, String fallback) {
            return retrieveKnowledge(query, type, topK, fallback);
        }
        
        public Mono<AgentLlmResponse> testCallLlmJson(String prompt, String systemPrompt, 
                double temperature, int retryOnFailure) {
            return callLlmJson(prompt, systemPrompt, temperature, retryOnFailure);
        }
        
        public Flux<String> testCallLlmStream(String prompt, String systemPrompt, double temperature) {
            return callLlmStream(prompt, systemPrompt, temperature);
        }
        
        public Map<String, Object> testYieldEvent(String eventType, Map<String, Object> data) {
            return yieldEvent(eventType, data);
        }
        
        public Map<String, Object> testYieldThinkingEvent(String message, String stage) {
            return yieldThinkingEvent(message, stage);
        }
        
        public Map<String, Object> testYieldKnowledgeEvent(String knowledgeType, String content, String message) {
            return yieldKnowledgeEvent(knowledgeType, content, message);
        }
        
        public Map<String, Object> testYieldErrorEvent(String errorMsg, String errorType) {
            return yieldErrorEvent(errorMsg, errorType);
        }
        
        public String testHandleException(Exception error, String operationName) {
            return handleException(error, operationName);
        }
        
        public Map<String, Object> testErrorResponse(String errorMsg) {
            return errorResponse(errorMsg);
        }
        
        public Map<String, Object> testSuccessResponse(Map<String, Object> data) {
            return successResponse(data);
        }
        
        // 用于注入依赖
        public void setKnowledgeService(KnowledgeService service) {
            this.knowledgeService = service;
        }
        
        public void setLlmClientRegistry(LlmClientRegistry registry) {
            this.llmClientRegistry = registry;
        }
    }

    @BeforeEach
    void setUp() {
        testAgent = new TestAgent("TestAgent");
        testAgent.setKnowledgeService(knowledgeService);
        testAgent.setLlmClientRegistry(llmClientRegistry);
    }

    // ==================== 构造函数测试 ====================
    
    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTests {
        
        @Test
        @DisplayName("应该正确初始化智能体名称")
        void shouldInitializeAgentName() {
            TestAgent agent = new TestAgent("MyTestAgent");
            assertThat(agent.getAgentName()).isEqualTo("MyTestAgent");
        }
    }

    // ==================== 知识检索测试 ====================
    
    @Nested
    @DisplayName("知识检索测试")
    class KnowledgeRetrievalTests {
        
        @Test
        @DisplayName("应该成功检索知识并合并结果")
        void shouldRetrieveKnowledgeSuccessfully() {
            // Given
            Map<String, Object> doc1 = Map.of("content", "知识内容1");
            Map<String, Object> doc2 = Map.of("content", "知识内容2");
            
            when(knowledgeService.searchDocuments(anyString(), anyString(), anyInt(), anyString()))
                    .thenReturn(Flux.just(doc1, doc2));
            
            // When & Then
            StepVerifier.create(testAgent.testRetrieveKnowledge("测试查询", "diagnosis", 5, "默认消息"))
                    .assertNext(result -> {
                        assertThat(result).contains("知识内容1");
                        assertThat(result).contains("知识内容2");
                        assertThat(result).contains("---"); // 分隔符
                    })
                    .verifyComplete();
            
            verify(knowledgeService).searchDocuments("测试查询", "diagnosis", 5, "keyword");
        }
        
        @Test
        @DisplayName("当未检索到知识时应该返回默认消息")
        void shouldReturnFallbackWhenNoKnowledgeFound() {
            // Given
            when(knowledgeService.searchDocuments(anyString(), anyString(), anyInt(), anyString()))
                    .thenReturn(Flux.empty());
            
            // When & Then
            StepVerifier.create(testAgent.testRetrieveKnowledge("测试", "tool", 5, "默认回退消息"))
                    .expectNext("默认回退消息")
                    .verifyComplete();
        }
        
        @Test
        @DisplayName("当KnowledgeService未注入时应该返回默认消息")
        void shouldReturnFallbackWhenServiceNotInjected() {
            // Given
            testAgent.setKnowledgeService(null);
            
            // When & Then
            StepVerifier.create(testAgent.testRetrieveKnowledge("测试", "tool", 5, "服务未配置"))
                    .expectNext("服务未配置")
                    .verifyComplete();
        }
        
        @Test
        @DisplayName("当检索发生异常时应该返回默认消息")
        void shouldReturnFallbackOnException() {
            // Given
            when(knowledgeService.searchDocuments(anyString(), anyString(), anyInt(), anyString()))
                    .thenReturn(Flux.error(new RuntimeException("ES连接失败")));
            
            // When & Then
            StepVerifier.create(testAgent.testRetrieveKnowledge("测试", "tool", 5, "异常回退消息"))
                    .expectNext("异常回退消息")
                    .verifyComplete();
        }
    }

    // ==================== LLM调用测试 ====================
    
    @Nested
    @DisplayName("LLM JSON调用测试")
    class LlmJsonCallTests {
        
        @Test
        @DisplayName("应该成功调用LLM并返回JSON结果")
        void shouldCallLlmAndReturnJson() {
            // Given
            Map<String, Object> responseData = Map.of("result", "success", "data", "test");
            LlmResponse llmResponse = LlmResponse.success(responseData, "raw response");
            
            when(llmClientRegistry.getDefaultClient()).thenReturn(llmClient);
            when(llmClient.generateJson(anyString(), anyString(), anyDouble()))
                    .thenReturn(Mono.just(llmResponse));
            
            // When & Then
            StepVerifier.create(testAgent.testCallLlmJson("用户提示", "系统提示", 0.5, 2))
                    .assertNext(response -> {
                        assertThat(response.isSuccess()).isTrue();
                        assertThat(response.getData()).containsEntry("result", "success");
                        assertThat(response.getError()).isNull();
                    })
                    .verifyComplete();
        }
        
        @Test
        @DisplayName("当LLM返回错误时应该返回错误响应")
        void shouldReturnErrorWhenLlmFails() {
            // Given
            LlmResponse llmResponse = LlmResponse.error("API调用失败", "api_error");
            
            when(llmClientRegistry.getDefaultClient()).thenReturn(llmClient);
            when(llmClient.generateJson(anyString(), anyString(), anyDouble()))
                    .thenReturn(Mono.just(llmResponse));
            
            // When & Then
            StepVerifier.create(testAgent.testCallLlmJson("用户提示", "系统提示", 0.5, 0))
                    .assertNext(response -> {
                        assertThat(response.isSuccess()).isFalse();
                        assertThat(response.getError()).isEqualTo("API调用失败");
                    })
                    .verifyComplete();
        }
        
        @Test
        @DisplayName("当LlmClientRegistry未注入时应该返回错误响应")
        void shouldReturnErrorWhenRegistryNotInjected() {
            // Given
            testAgent.setLlmClientRegistry(null);
            
            // When & Then
            StepVerifier.create(testAgent.testCallLlmJson("用户提示", "系统提示", 0.5, 0))
                    .assertNext(response -> {
                        assertThat(response.isSuccess()).isFalse();
                        assertThat(response.getError()).contains("LLM客户端未配置");
                    })
                    .verifyComplete();
        }
    }
    
    @Nested
    @DisplayName("LLM流式调用测试")
    class LlmStreamCallTests {
        
        @Test
        @DisplayName("应该成功流式调用LLM")
        void shouldStreamLlmOutputSuccessfully() {
            // Given
            when(llmClientRegistry.getDefaultClient()).thenReturn(llmClient);
            when(llmClient.streamText(anyString(), anyString(), anyDouble()))
                    .thenReturn(Flux.just("Hello", " ", "World"));
            
            // When & Then
            StepVerifier.create(testAgent.testCallLlmStream("用户提示", "系统提示", 0.5))
                    .expectNext("Hello")
                    .expectNext(" ")
                    .expectNext("World")
                    .verifyComplete();
        }
        
        @Test
        @DisplayName("当LlmClientRegistry未注入时应该返回空流")
        void shouldReturnEmptyFluxWhenRegistryNotInjected() {
            // Given
            testAgent.setLlmClientRegistry(null);
            
            // When & Then
            StepVerifier.create(testAgent.testCallLlmStream("用户提示", "系统提示", 0.5))
                    .verifyComplete();
        }
        
        @Test
        @DisplayName("应该过滤空字符串")
        void shouldFilterEmptyStrings() {
            // Given
            when(llmClientRegistry.getDefaultClient()).thenReturn(llmClient);
            when(llmClient.streamText(anyString(), anyString(), anyDouble()))
                    .thenReturn(Flux.just("Hello", "", null, "World"));
            
            // When & Then
            StepVerifier.create(testAgent.testCallLlmStream("用户提示", "系统提示", 0.5))
                    .expectNext("Hello")
                    .expectNext("World")
                    .verifyComplete();
        }
    }

    // ==================== 事件推送测试 ====================
    
    @Nested
    @DisplayName("事件推送测试")
    class EventYieldTests {
        
        @Test
        @DisplayName("yieldEvent应该正确构建事件对象")
        void yieldEventShouldBuildEventCorrectly() {
            // Given
            Map<String, Object> data = Map.of("key1", "value1", "key2", 123);
            
            // When
            Map<String, Object> event = testAgent.testYieldEvent("custom_event", data);
            
            // Then
            assertThat(event).containsEntry("type", "custom_event");
            assertThat(event).containsEntry("agent", "TestAgent");
            assertThat(event).containsKey("timestamp");
            assertThat(event).containsEntry("key1", "value1");
            assertThat(event).containsEntry("key2", 123);
        }
        
        @Test
        @DisplayName("yieldThinkingEvent应该正确构建思考事件")
        void yieldThinkingEventShouldBuildCorrectly() {
            // When
            Map<String, Object> event = testAgent.testYieldThinkingEvent("正在分析问题...", "analysis");
            
            // Then
            assertThat(event).containsEntry("type", "agent_thinking");
            assertThat(event).containsEntry("message", "正在分析问题...");
            assertThat(event).containsEntry("stage", "analysis");
            assertThat(event).containsEntry("agent", "TestAgent");
        }
        
        @Test
        @DisplayName("yieldKnowledgeEvent应该正确构建知识事件")
        void yieldKnowledgeEventShouldBuildCorrectly() {
            // When
            Map<String, Object> event = testAgent.testYieldKnowledgeEvent("诊断策略", "策略内容...", "自定义消息");
            
            // Then
            assertThat(event).containsEntry("type", "knowledge_retrieved");
            assertThat(event).containsEntry("knowledge_type", "诊断策略");
            assertThat(event).containsEntry("knowledge_content", "策略内容...");
            assertThat(event).containsEntry("message", "自定义消息");
        }
        
        @Test
        @DisplayName("yieldKnowledgeEvent应该在消息为空时自动生成消息")
        void yieldKnowledgeEventShouldAutoGenerateMessage() {
            // When
            Map<String, Object> event = testAgent.testYieldKnowledgeEvent("工具接口", "接口定义...", null);
            
            // Then
            assertThat(event).containsEntry("message", "检索到工具接口");
        }
        
        @Test
        @DisplayName("yieldErrorEvent应该正确构建错误事件")
        void yieldErrorEventShouldBuildCorrectly() {
            // When
            Map<String, Object> event = testAgent.testYieldErrorEvent("连接超时", "TimeoutError");
            
            // Then
            assertThat(event).containsEntry("type", "error");
            assertThat(event).containsEntry("error", "连接超时");
            assertThat(event).containsEntry("error_type", "TimeoutError");
            assertThat(event).containsEntry("agent", "TestAgent");
        }
    }

    // ==================== 错误处理测试 ====================
    
    @Nested
    @DisplayName("错误处理测试")
    class ErrorHandlingTests {
        
        @Test
        @DisplayName("handleException应该返回格式化的错误信息")
        void handleExceptionShouldReturnFormattedMessage() {
            // Given
            Exception exception = new RuntimeException("测试异常");
            
            // When
            String errorMsg = testAgent.testHandleException(exception, "测试操作");
            
            // Then
            assertThat(errorMsg).contains("[TestAgent]");
            assertThat(errorMsg).contains("测试操作失败");
            assertThat(errorMsg).contains("测试异常");
        }
    }

    // ==================== 响应构建测试 ====================
    
    @Nested
    @DisplayName("响应构建测试")
    class ResponseBuildingTests {
        
        @Test
        @DisplayName("errorResponse应该正确构建错误响应")
        void errorResponseShouldBuildCorrectly() {
            // When
            Map<String, Object> response = testAgent.testErrorResponse("发生错误");
            
            // Then
            assertThat(response).containsEntry("success", false);
            assertThat(response).containsEntry("error", "发生错误");
            assertThat(response).containsEntry("agent", "TestAgent");
            assertThat(response).containsKey("timestamp");
        }
        
        @Test
        @DisplayName("successResponse应该正确构建成功响应")
        void successResponseShouldBuildCorrectly() {
            // Given
            Map<String, Object> data = Map.of("result", "ok", "count", 10);
            
            // When
            Map<String, Object> response = testAgent.testSuccessResponse(data);
            
            // Then
            assertThat(response).containsEntry("success", true);
            assertThat(response).containsEntry("agent", "TestAgent");
            assertThat(response).containsEntry("result", "ok");
            assertThat(response).containsEntry("count", 10);
            assertThat(response).containsKey("timestamp");
        }
        
        @Test
        @DisplayName("successResponse应该处理null数据")
        void successResponseShouldHandleNullData() {
            // When
            Map<String, Object> response = testAgent.testSuccessResponse(null);
            
            // Then
            assertThat(response).containsEntry("success", true);
            assertThat(response).containsEntry("agent", "TestAgent");
            assertThat(response).containsKey("timestamp");
        }
    }

    // ==================== 抽象方法测试 ====================
    
    @Nested
    @DisplayName("process方法测试")
    class ProcessMethodTests {
        
        @Test
        @DisplayName("子类应该能够实现process方法")
        void subclassShouldImplementProcess() {
            // Given
            Map<String, Object> input = Map.of("query", "测试输入");
            
            // When & Then
            StepVerifier.create(testAgent.process(input))
                    .assertNext(result -> {
                        assertThat(result).containsEntry("success", true);
                        assertThat(result).containsEntry("processed", true);
                    })
                    .verifyComplete();
        }
    }
}
