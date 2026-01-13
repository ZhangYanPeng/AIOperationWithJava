package com.company.diagnosis.agent.interfaceLayer;

import com.agentscope.message.Msg;
import com.company.diagnosis.agent.base.BaseIntelligentAgent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 结果解析智能体（接口调用层）
 * 
 * 职责：
 * - 根据上层调用需求，理解需要提取的字段
 * - 从接口返回的JSON数据中提取关键信息
 * - 返回结构化的解析结果
 * 
 * 输入数据结构（Msg.content的JSON）：
 * {
 *   "call_requirement": "查询设备ID为123的通道状态",
 *   "api_response": { ... },  // 接口返回的JSON数据
 *   "expected_fields": ["deviceId", "channelStatus", "lastUpdateTime"]
 * }
 * 
 * 输出数据结构（Msg.content的JSON）：
 * {
 *   "success": true/false,
 *   "extracted_data": {
 *     "deviceId": "123",
 *     "channelStatus": "online",
 *     "lastUpdateTime": "2026-01-13T14:00:00"
 *   },
 *   "missing_fields": [],
 *   "error": "错误信息（如果success=false）"
 * }
 * 
 * @author AIOperation Team
 * @since 2026-01-13
 */
@Component
public class ResultParsingAgent extends BaseIntelligentAgent {
    
    public ResultParsingAgent() {
        super("ResultParsingAgent");
    }
    
    @Override
    public Mono<Msg> reply(Msg msg) {
        // TODO: 实现结果解析逻辑
        return Mono.just(errorResponse("Not implemented"));
    }
}
