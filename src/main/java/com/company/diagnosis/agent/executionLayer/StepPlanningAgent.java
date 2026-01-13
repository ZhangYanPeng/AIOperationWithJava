package com.company.diagnosis.agent.executionLayer;

import com.agentscope.message.Msg;
import com.company.diagnosis.agent.base.BaseIntelligentAgent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 步骤规划智能体（执行层）
 * 
 * 职责：
 * - 根据任务目标和检索到的知识，生成粗粒度执行步骤列表
 * - 标注步骤间的分支条件和依赖关系
 * - 提供步骤执行指导
 * 
 * 输入数据结构（Msg.content的JSON）：
 * {
 *   "task_objective": "诊断M机单播通道中断问题",
 *   "retrieved_knowledge": "诊断策略文本...",
 *   "parameter_memory": {...}
 * }
 * 
 * 输出数据结构（Msg.content的JSON）：
 * {
 *   "success": true/false,
 *   "steps": [
 *     {
 *       "step_index": 1,
 *       "step_name": "查询设备信息",
 *       "step_goal": "获取设备基本信息",
 *       "required_tool": "查询设备信息",
 *       "branch_condition": null,
 *       "depends_on": []
 *     }
 *   ],
 *   "error": "错误信息（如果success=false）"
 * }
 * 
 * @author AIOperation Team
 * @since 2026-01-13
 */
@Component
public class StepPlanningAgent extends BaseIntelligentAgent {
    
    public StepPlanningAgent() {
        super("StepPlanningAgent");
    }
    
    @Override
    public Mono<Msg> reply(Msg msg) {
        // TODO: 实现步骤规划逻辑
        return Mono.just(errorResponse("Not implemented"));
    }
}
