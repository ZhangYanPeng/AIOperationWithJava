package com.company.diagnosis.agent.executionLayer;

import com.agentscope.message.Msg;
import com.company.diagnosis.agent.base.BaseIntelligentAgent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 需求理解智能体（执行层）
 * 
 * 职责：
 * - 解析上层传入的结构化文本输入
 * - 提取任务目标、关键约束、期望输出
 * 
 * 输入数据结构（Msg.content的JSON）：
 * {
 *   "input_text": "诊断M机单播通道中断问题，设备ID为123",
 *   "context": {
 *     "history_steps": [...],
 *     "parameter_memory": {...}
 *   }
 * }
 * 
 * 输出数据结构（Msg.content的JSON）：
 * {
 *   "success": true/false,
 *   "task_objective": "诊断M机单播通道中断问题",
 *   "key_constraints": ["设备ID必须为123", "需要检查网络连通性"],
 *   "expected_output": "诊断结论+根本原因+建议措施",
 *   "error": "错误信息（如果success=false）"
 * }
 * 
 * @author AIOperation Team
 * @since 2026-01-13
 */
@Component
public class RequirementUnderstandingAgent extends BaseIntelligentAgent {
    
    public RequirementUnderstandingAgent() {
        super("RequirementUnderstandingAgent");
    }
    
    @Override
    public Mono<Msg> reply(Msg msg) {
        // TODO: 实现需求理解逻辑
        return Mono.just(errorResponse("Not implemented"));
    }
}
