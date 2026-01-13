package com.company.diagnosis.agent.executionLayer;

import com.agentscope.message.Msg;
import com.company.diagnosis.agent.base.BaseIntelligentAgent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 步骤决策智能体（执行层）
 * 
 * 职责：
 * - 根据当前执行状态、步骤列表、上次执行结果，决定下一步行动
 * - 判断是否继续执行或结束流程
 * - 处理分支条件，选择下一步执行的步骤索引
 * 
 * 输入数据结构（Msg.content的JSON）：
 * {
 *   "steps": [...],
 *   "current_state": {
 *     "executed_steps": [1, 2],
 *     "current_step_index": 2,
 *     "execution_history": [...]
 *   },
 *   "last_step_result": {...},
 *   "parameter_memory": {...}
 * }
 * 
 * 输出数据结构（Msg.content的JSON）：
 * {
 *   "success": true/false,
 *   "should_continue": true/false,
 *   "next_step_index": 3,
 *   "termination_reason": "所有步骤已完成",
 *   "decision_reasoning": "根据上一步结果...",
 *   "error": "错误信息（如果success=false）"
 * }
 * 
 * @author AIOperation Team
 * @since 2026-01-13
 */
@Component
public class StepDecisionAgent extends BaseIntelligentAgent {
    
    public StepDecisionAgent() {
        super("StepDecisionAgent");
    }
    
    @Override
    public Mono<Msg> reply(Msg msg) {
        // TODO: 实现步骤决策逻辑
        return Mono.just(errorResponse("Not implemented"));
    }
}
