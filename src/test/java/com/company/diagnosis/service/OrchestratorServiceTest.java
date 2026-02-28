package com.company.diagnosis.service;

import com.company.diagnosis.config.AgentScopeConfig.LlmClientRegistry;
import com.company.diagnosis.model.context.DiagnosisContext;
import com.company.diagnosis.model.dto.DiagnosisRequest;
import com.company.diagnosis.loader.AgentConfigLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OrchestratorService 单元测试
 * 
 * 测试范围：
 * - 诊断流程启动（同步/异步/流式）
 * - 层级执行和验证
 * - 会话管理
 * - 结果合并
 * - 错误处理
 * 
 * @author Diagnosis System
 * @since 2026-01-13
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrchestratorService 单元测试")
class OrchestratorServiceTest {

    @Mock
    private LlmClientRegistry llmClientRegistry;

    @Mock
    private AgentConfigLoader agentConfigLoader;

    @Mock
    private SseConnectionManager sseConnectionManager;

    @Mock
    private ReportGenerationService reportGenerationService;

    @Mock
    private MessageProcessingService messageProcessingService;

    @Mock
    private Scheduler ioScheduler;

    @InjectMocks
    private OrchestratorService orchestratorService;

    private DiagnosisRequest createTestRequest() {
        DiagnosisRequest request = new DiagnosisRequest();
        request.setAlertId("ALERT-001");
        request.setRequestId("REQ-001");
        request.setDiagnosisType("设备故障诊断");
        request.setProblem("设备离线无法连接");
        
        Map<String, Object> alertData = new HashMap<>();
        alertData.put("deviceId", "DEV-001");
        alertData.put("severity", "HIGH");
        request.setAlertData(alertData);
        
        return request;
    }

    // ==================== 层级验证测试 ====================
    
    @Nested
    @DisplayName("层级调用验证测试")
    class LayerCallValidationTests {
        
        @Test
        @DisplayName("第N层调用第N-1层应该合法")
        void shouldValidateCorrectLayerCall() {
            // When
            Boolean result = orchestratorService.validateLayerCall(3, 2);
            
            // Then
            assertThat(result).isTrue();
        }
        
        @Test
        @DisplayName("第N层调用第N-2层应该不合法")
        void shouldInvalidateSkippedLayerCall() {
            // When
            Boolean result = orchestratorService.validateLayerCall(3, 1);
            
            // Then
            assertThat(result).isFalse();
        }
        
        @Test
        @DisplayName("第1层调用非第0层应该不合法")
        void shouldInvalidateLayer1CallingNonZero() {
            // When
            Boolean result = orchestratorService.validateLayerCall(1, 2);
            
            // Then
            assertThat(result).isFalse();
        }
        
        @Test
        @DisplayName("第1层调用第0层（外部API）应该合法")
        void shouldValidateLayer1CallingExternalApi() {
            // When
            Boolean result = orchestratorService.validateLayerCall(1, 0);
            
            // Then
            assertThat(result).isTrue();
        }
        
        @Test
        @DisplayName("层级参数为null应该返回false")
        void shouldReturnFalseForNullLayers() {
            // When & Then
            assertThat(orchestratorService.validateLayerCall(null, 1)).isFalse();
            assertThat(orchestratorService.validateLayerCall(2, null)).isFalse();
            assertThat(orchestratorService.validateLayerCall(null, null)).isFalse();
        }
        
        @Test
        @DisplayName("第2层调用第1层应该合法")
        void shouldValidateLayer2CallingLayer1() {
            // When
            Boolean result = orchestratorService.validateLayerCall(2, 1);
            
            // Then
            assertThat(result).isTrue();
        }
    }

    // ==================== 层级链确定测试 ====================
    
    @Nested
    @DisplayName("层级链确定测试")
    class LayerChainDeterminationTests {
        
        @Test
        @DisplayName("应该返回默认层级链")
        void shouldReturnDefaultLayerChain() {
            // Given
            DiagnosisContext context = DiagnosisContext.create("session-1", "测试问题", "测试诊断");
            
            // When & Then
            StepVerifier.create(orchestratorService.determineLayerChain(context))
                    .assertNext(layerChain -> {
                        assertThat(layerChain).isNotEmpty();
                        assertThat(layerChain).contains("orchestrator");
                        assertThat(layerChain).contains("analyzer");
                        assertThat(layerChain).contains("collector");
                    })
                    .verifyComplete();
        }
        
        @Test
        @DisplayName("层级链应该按从高到低顺序排列")
        void layerChainShouldBeOrderedHighToLow() {
            // Given
            DiagnosisContext context = DiagnosisContext.create("session-2", "测试问题", "测试诊断");
            
            // When & Then
            StepVerifier.create(orchestratorService.determineLayerChain(context))
                    .assertNext(layerChain -> {
                        // orchestrator 应该在最前面（最高层）
                        int orchestratorIndex = layerChain.indexOf("orchestrator");
                        int analyzerIndex = layerChain.indexOf("analyzer");
                        int collectorIndex = layerChain.indexOf("collector");
                        
                        assertThat(orchestratorIndex).isLessThan(analyzerIndex);
                        assertThat(analyzerIndex).isLessThan(collectorIndex);
                    })
                    .verifyComplete();
        }
    }

    // ==================== 单层执行测试 ====================
    
    @Nested
    @DisplayName("单层执行测试")
    class SingleLayerExecutionTests {
        
        @Test
        @DisplayName("应该成功执行单个层级")
        void shouldExecuteLayerSuccessfully() {
            // Given
            DiagnosisContext context = DiagnosisContext.create("session-3", "测试问题", "测试诊断");
            
            // When & Then
            StepVerifier.create(orchestratorService.executeLayer("analyzer", context))
                    .assertNext(result -> {
                        assertThat(result).containsKey("layer");
                        assertThat(result).containsKey("status");
                        assertThat(result.get("layer")).isEqualTo("analyzer");
                        assertThat(result.get("status")).isEqualTo("SUCCESS");
                    })
                    .verifyComplete();
        }
        
        @Test
        @DisplayName("当诊断已取消时应该返回错误")
        void shouldReturnErrorWhenDiagnosisCancelled() {
            // Given
            DiagnosisContext context = DiagnosisContext.create("session-4", "测试问题", "测试诊断");
            context.addParameter("cancelled", true);
            
            // When & Then
            StepVerifier.create(orchestratorService.executeLayer("analyzer", context))
                    .expectErrorMatches(error -> 
                            error instanceof RuntimeException && 
                            error.getMessage().contains("已取消"))
                    .verify();
        }
        
        @Test
        @DisplayName("执行层级后应该更新context的步骤记录")
        void shouldUpdateContextStepsAfterExecution() {
            // Given
            DiagnosisContext context = DiagnosisContext.create("session-5", "测试问题", "测试诊断");
            
            // When
            orchestratorService.executeLayer("collector", context).block();
            
            // Then
            assertThat(context.getStepMemory()).isNotEmpty();
        }
    }

    // ==================== 结果合并测试 ====================
    
    @Nested
    @DisplayName("结果合并测试")
    class ResultMergingTests {
        
        @Test
        @DisplayName("应该正确合并层级结果")
        void shouldMergeLayerResultsCorrectly() {
            // Given
            DiagnosisContext context = DiagnosisContext.create("session-6", "测试问题", "测试诊断");
            context.setSessionId("session-6");
            context.setRequestId("req-6");
            context.setAlertId("alert-6");
            
            // 添加一些层级结果
            context.addLayerResult("orchestrator", Map.of("output", "编排完成"));
            context.addLayerResult("analyzer", Map.of("output", "分析完成"));
            
            // When & Then
            StepVerifier.create(orchestratorService.mergeLayerResults(context))
                    .assertNext(finalReport -> {
                        assertThat(finalReport).containsKey("sessionId");
                        assertThat(finalReport).containsKey("layerResults");
                        assertThat(finalReport).containsKey("conclusion");
                        assertThat(finalReport).containsKey("recommendations");
                        assertThat(finalReport.get("sessionId")).isEqualTo("session-6");
                    })
                    .verifyComplete();
        }
        
        @Test
        @DisplayName("合并结果应该包含建议列表")
        void mergedResultShouldContainRecommendations() {
            // Given
            DiagnosisContext context = DiagnosisContext.create("session-7", "测试问题", "测试诊断");
            
            // When & Then
            StepVerifier.create(orchestratorService.mergeLayerResults(context))
                    .assertNext(finalReport -> {
                        Object recommendations = finalReport.get("recommendations");
                        assertThat(recommendations).isInstanceOf(List.class);
                        assertThat((List<?>) recommendations).isNotEmpty();
                    })
                    .verifyComplete();
        }
    }

    // ==================== 会话管理测试 ====================
    
    @Nested
    @DisplayName("会话管理测试")
    class SessionManagementTests {
        
        @Test
        @DisplayName("初始时活跃会话数应该为0")
        void shouldHaveZeroActiveSessionsInitially() {
            // When
            int count = orchestratorService.getActiveSessionCount();
            
            // Then
            assertThat(count).isZero();
        }
        
        @Test
        @DisplayName("获取活跃会话列表应该返回不可修改的集合")
        void activeSessionIdsShouldBeUnmodifiable() {
            // When
            var sessionIds = orchestratorService.getActiveSessionIds();
            
            // Then
            assertThatThrownBy(() -> sessionIds.add("test"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    // ==================== 停止诊断测试 ====================
    
    @Nested
    @DisplayName("停止诊断测试")
    class StopDiagnosisTests {
        
        @Test
        @DisplayName("停止不存在的会话应该返回失败结果")
        void shouldReturnFailureForNonExistentSession() {
            // When & Then
            StepVerifier.create(orchestratorService.stopDiagnosis("non-existent-session"))
                    .assertNext(result -> {
                        assertThat(result).containsEntry("success", false);
                        assertThat(result.get("message").toString()).contains("未找到");
                    })
                    .verifyComplete();
        }
    }

    // ==================== 异步诊断测试 ====================
    
    @Nested
    @DisplayName("异步诊断测试")
    class AsyncDiagnosisTests {
        
        @Test
        @DisplayName("异步启动应该立即返回任务信息")
        void asyncStartShouldReturnTaskInfoImmediately() {
            // Given
            DiagnosisRequest request = createTestRequest();
            
            // When & Then
            StepVerifier.create(orchestratorService.startDiagnosisAsync(request))
                    .assertNext(taskInfo -> {
                        assertThat(taskInfo).containsKey("sessionId");
                        assertThat(taskInfo).containsKey("requestId");
                        assertThat(taskInfo).containsKey("status");
                        assertThat(taskInfo.get("status")).isEqualTo("STARTED");
                    })
                    .verifyComplete();
        }
    }

    // ==================== 流式诊断测试 ====================
    
    @Nested
    @DisplayName("流式诊断测试")
    class StreamDiagnosisTests {
        
        @Test
        @DisplayName("流式诊断应该发送开始事件")
        void streamDiagnosisShouldEmitStartEvent() {
            // Given
            DiagnosisRequest request = createTestRequest();
            
            // When & Then
            StepVerifier.create(orchestratorService.startDiagnosisStream(request).take(1))
                    .assertNext(event -> {
                        assertThat(event).containsKey("type");
                        // 第一个事件应该是开始事件
                    })
                    .verifyComplete();
        }
    }

    // ==================== 诊断结果保存测试 ====================
    
    @Nested
    @DisplayName("诊断结果保存测试")
    class SaveDiagnosisResultTests {
        
        @Test
        @DisplayName("应该成功保存诊断结果")
        void shouldSaveDiagnosisResultSuccessfully() {
            // Given
            DiagnosisContext context = DiagnosisContext.create("session-8", "测试问题", "测试诊断");
            context.setAlertId("alert-8");
            
            when(messageProcessingService.sendDiagnosisEvent(anyString(), anyMap()))
                    .thenReturn(Mono.empty());
            
            // When & Then
            StepVerifier.create(orchestratorService.saveDiagnosisResult(context))
                    .expectNext(true)
                    .verifyComplete();
        }
        
        @Test
        @DisplayName("当消息服务未配置时也应该成功保存")
        void shouldSaveSuccessfullyWithoutMessageService() {
            // Given
            DiagnosisContext context = DiagnosisContext.create("session-9", "测试问题", "测试诊断");
            
            // 创建一个没有注入 messageProcessingService 的实例
            OrchestratorService serviceWithoutMsgService = new OrchestratorService();
            
            // When & Then
            StepVerifier.create(serviceWithoutMsgService.saveDiagnosisResult(context))
                    .expectNext(true)
                    .verifyComplete();
        }
    }

    // ==================== 同步诊断测试 ====================
    
    @Nested
    @DisplayName("同步诊断测试")
    class SyncDiagnosisTests {
        
        @Test
        @DisplayName("同步诊断应该返回完整结果")
        void syncDiagnosisShouldReturnCompleteResult() {
            // Given
            DiagnosisRequest request = createTestRequest();
            
            // When & Then
            StepVerifier.create(orchestratorService.startDiagnosis(request))
                    .assertNext(result -> {
                        assertThat(result).containsKey("sessionId");
                        assertThat(result).containsKey("conclusion");
                        assertThat(result).containsKey("recommendations");
                    })
                    .verifyComplete();
        }
    }
}
