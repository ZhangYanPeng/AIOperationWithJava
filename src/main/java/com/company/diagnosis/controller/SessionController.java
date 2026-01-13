package com.company.diagnosis.controller;

import com.company.diagnosis.model.context.DiagnosisContext;
import com.company.diagnosis.service.OrchestratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话管理控制器
 * <p>
 * 职责：
 * 1. 提供诊断会话的生命周期管理
 * 2. 支持会话查询和历史记录
 * 3. 提供会话上下文的访问接口
 * 4. 支持会话的持久化和恢复
 * <p>
 * 设计考虑：
 * - 会话信息包含诊断上下文、执行历史、中间结果
 * - 支持会话的分页查询
 * - 提供会话清理功能
 *
 * @author Diagnosis System
 * @date 2026-01-13
 */
@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private static final Logger logger = LoggerFactory.getLogger(SessionController.class);

    @Autowired
    private OrchestratorService orchestratorService;

    /**
     * 本地会话存储（用于会话元数据管理）
     */
    private final ConcurrentHashMap<String, Map<String, Object>> sessionMetadata = new ConcurrentHashMap<>();

    /**
     * 创建新会话
     * <p>
     * 功能说明：
     * 创建一个新的诊断会话，返回会话ID
     *
     * @param metadata 会话元数据，包含用户信息、初始配置等
     * @return 会话信息Map，包含sessionId、createTime等
     */
    @PostMapping
    public Mono<Map<String, Object>> createSession(@RequestBody(required = false) Map<String, Object> metadata) {
        return Mono.fromCallable(() -> {
            // 生成唯一sessionId
            String sessionId = UUID.randomUUID().toString();
            
            // 初始化会话元数据
            Map<String, Object> sessionInfo = new HashMap<>();
            sessionInfo.put("sessionId", sessionId);
            sessionInfo.put("createTime", LocalDateTime.now().toString());
            sessionInfo.put("status", "CREATED");
            
            if (metadata != null) {
                sessionInfo.put("userId", metadata.get("userId"));
                sessionInfo.put("clientInfo", metadata.get("clientInfo"));
                sessionInfo.putAll(metadata);
            }
            
            // 存储会话元数据
            sessionMetadata.put(sessionId, sessionInfo);
            
            logger.info("创建新会话: sessionId={}", sessionId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("sessionId", sessionId);
            response.put("createTime", sessionInfo.get("createTime"));
            response.put("status", "CREATED");
            
            return response;
        });
    }

    /**
     * 获取会话详情
     * <p>
     * 功能说明：
     * 根据会话ID获取完整的会话信息
     *
     * @param sessionId 会话ID
     * @return 会话详情Map，包含上下文、历史、状态等
     */
    @GetMapping("/{sessionId}")
    public Mono<Map<String, Object>> getSession(@PathVariable String sessionId) {
        return Mono.fromCallable(() -> {
            // 验证sessionId
            if (sessionId == null || sessionId.trim().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "会话ID不能为空");
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("sessionId", sessionId);
            
            // 从元数据获取会话信息
            Map<String, Object> metadata = sessionMetadata.get(sessionId);
            if (metadata != null) {
                response.putAll(metadata);
            }
            
            // 从OrchestratorService获取活跃会话的上下文
            DiagnosisContext context = orchestratorService.getContext(sessionId);
            if (context != null) {
                response.put("status", context.getStatus().name());
                response.put("diagnosisType", context.getDiagnosisType());
                response.put("problem", context.getProblem());
                response.put("currentLayer", context.getCurrentLayer());
                response.put("stepsCount", context.getStepsMemory().size());
                response.put("hasResults", !context.getLayerResults().isEmpty());
            } else if (metadata == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在: " + sessionId);
            }
            
            return response;
        });
    }

    /**
     * 获取会话上下文
     * <p>
     * 功能说明：
     * 获取会话的DiagnosisContext对象
     *
     * @param sessionId 会话ID
     * @return 诊断上下文Map
     */
    @GetMapping("/{sessionId}/context")
    public Mono<Map<String, Object>> getSessionContext(@PathVariable String sessionId) {
        return Mono.fromCallable(() -> {
            if (sessionId == null || sessionId.trim().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "会话ID不能为空");
            }
            
            DiagnosisContext context = orchestratorService.getContext(sessionId);
            if (context == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话上下文不存在: " + sessionId);
            }
            
            // 转换上下文为Map
            Map<String, Object> contextMap = new HashMap<>();
            contextMap.put("sessionId", context.getSessionId());
            contextMap.put("diagnosisType", context.getDiagnosisType());
            contextMap.put("problem", context.getProblem());
            contextMap.put("status", context.getStatus().name());
            contextMap.put("currentLayer", context.getCurrentLayer());
            contextMap.put("parameters", context.getParameterMemory());
            contextMap.put("layerResults", context.getLayerResults());
            contextMap.put("stepsCount", context.getStepsMemory().size());
            
            return contextMap;
        });
    }

    /**
     * 获取会话执行历史
     * <p>
     * 功能说明：
     * 获取会话中所有层级的执行步骤历史
     *
     * @param sessionId 会话ID
     * @return 执行历史列表
     */
    @GetMapping("/{sessionId}/history")
    public Flux<Map<String, Object>> getSessionHistory(@PathVariable String sessionId) {
        return Mono.fromCallable(() -> {
            if (sessionId == null || sessionId.trim().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "会话ID不能为空");
            }
            
            DiagnosisContext context = orchestratorService.getContext(sessionId);
            if (context == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在: " + sessionId);
            }
            
            return context.getStepsMemory();
        }).flatMapMany(Flux::fromIterable);
    }

    /**
     * 更新会话状态
     * <p>
     * 功能说明：
     * 更新会话的状态信息
     *
     * @param sessionId 会话ID
     * @param updates 更新内容
     * @return 更新结果Map
     */
    @PutMapping("/{sessionId}")
    public Mono<Map<String, Object>> updateSession(
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> updates) {
        return Mono.fromCallable(() -> {
            if (sessionId == null || sessionId.trim().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "会话ID不能为空");
            }
            
            if (updates == null || updates.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "更新内容不能为空");
            }
            
            // 更新元数据
            Map<String, Object> metadata = sessionMetadata.get(sessionId);
            if (metadata == null) {
                metadata = new HashMap<>();
                metadata.put("sessionId", sessionId);
                sessionMetadata.put(sessionId, metadata);
            }
            
            // 应用更新
            metadata.put("updatedAt", LocalDateTime.now().toString());
            for (Map.Entry<String, Object> entry : updates.entrySet()) {
                // 不允许更新sessionId
                if (!"sessionId".equals(entry.getKey())) {
                    metadata.put(entry.getKey(), entry.getValue());
                }
            }
            
            logger.info("更新会话: sessionId={}", sessionId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("sessionId", sessionId);
            response.put("updatedAt", metadata.get("updatedAt"));
            
            return response;
        });
    }

    /**
     * 删除会话
     * <p>
     * 功能说明：
     * 删除指定的会话及其所有关联数据
     *
     * @param sessionId 会话ID
     * @return 删除结果Map
     */
    @DeleteMapping("/{sessionId}")
    public Mono<Map<String, Object>> deleteSession(@PathVariable String sessionId) {
        return Mono.fromCallable(() -> {
            if (sessionId == null || sessionId.trim().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "会话ID不能为空");
            }
            
            // 删除元数据
            Map<String, Object> removed = sessionMetadata.remove(sessionId);
            
            // 清理OrchestratorService中的会话
            orchestratorService.removeSession(sessionId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", removed != null);
            response.put("sessionId", sessionId);
            response.put("deletedAt", LocalDateTime.now().toString());
            
            if (removed != null) {
                logger.info("删除会话成功: sessionId={}", sessionId);
            } else {
                logger.warn("会话不存在: sessionId={}", sessionId);
            }
            
            return response;
        });
    }

    /**
     * 查询会话列表
     * <p>
     * 功能说明：
     * 分页查询会话列表，支持按状态、时间等条件过滤
     *
     * @param status 会话状态(可选)
     * @param page 页码
     * @param size 每页大小
     * @return 分页结果Map
     */
    @GetMapping
    public Mono<Map<String, Object>> listSessions(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Mono.fromCallable(() -> {
            int pageNum = Math.max(1, page);
            int pageSize = Math.min(100, Math.max(1, size));
            
            // 过滤会话
            var filteredSessions = sessionMetadata.values().stream()
                    .filter(session -> {
                        if (status == null || status.isEmpty()) {
                            return true;
                        }
                        return status.equalsIgnoreCase((String) session.get("status"));
                    })
                    .skip((long) (pageNum - 1) * pageSize)
                    .limit(pageSize)
                    .toList();
            
            long total = sessionMetadata.values().stream()
                    .filter(session -> {
                        if (status == null || status.isEmpty()) {
                            return true;
                        }
                        return status.equalsIgnoreCase((String) session.get("status"));
                    })
                    .count();
            
            Map<String, Object> response = new HashMap<>();
            response.put("page", pageNum);
            response.put("size", pageSize);
            response.put("total", total);
            response.put("totalPages", (total + pageSize - 1) / pageSize);
            response.put("data", filteredSessions);
            
            return response;
        });
    }

    /**
     * 清理过期会话
     * <p>
     * 功能说明：
     * 清理超过保留期限的会话数据
     *
     * @param daysToKeep 保留天数
     * @return 清理结果Map，包含清理数量等
     */
    @PostMapping("/cleanup")
    public Mono<Map<String, Object>> cleanupSessions(
            @RequestParam(defaultValue = "30") Integer daysToKeep) {
        return Mono.fromCallable(() -> {
            LocalDateTime cutoffTime = LocalDateTime.now().minusDays(daysToKeep);
            
            long removedCount = 0;
            var iterator = sessionMetadata.entrySet().iterator();
            
            while (iterator.hasNext()) {
                var entry = iterator.next();
                Map<String, Object> session = entry.getValue();
                
                String createTimeStr = (String) session.get("createTime");
                if (createTimeStr != null) {
                    try {
                        LocalDateTime createTime = LocalDateTime.parse(createTimeStr);
                        if (createTime.isBefore(cutoffTime)) {
                            iterator.remove();
                            orchestratorService.removeSession(entry.getKey());
                            removedCount++;
                        }
                    } catch (Exception e) {
                        logger.warn("解析会话创建时间失败: {}", createTimeStr);
                    }
                }
            }
            
            logger.info("清理过期会话完成: 删除{}个会话, 保留{}天内的会话", removedCount, daysToKeep);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("removedCount", removedCount);
            response.put("daysToKeep", daysToKeep);
            response.put("cleanupTime", LocalDateTime.now().toString());
            
            return response;
        });
    }

    /**
     * 获取会话层级结果
     *
     * @param sessionId 会话ID
     * @param layer 层级名称
     * @return 层级结果
     */
    @GetMapping("/{sessionId}/layers/{layer}")
    public Mono<Map<String, Object>> getLayerResult(
            @PathVariable String sessionId,
            @PathVariable String layer) {
        return Mono.fromCallable(() -> {
            DiagnosisContext context = orchestratorService.getContext(sessionId);
            if (context == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在: " + sessionId);
            }
            
            Map<String, Object> layerResults = context.getLayerResults();
            if (!layerResults.containsKey(layer)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "层级结果不存在: " + layer);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("sessionId", sessionId);
            response.put("layer", layer);
            response.put("result", layerResults.get(layer));
            
            return response;
        });
    }

    /**
     * 获取会话参数
     *
     * @param sessionId 会话ID
     * @return 会话参数Map
     */
    @GetMapping("/{sessionId}/parameters")
    public Mono<Map<String, Object>> getSessionParameters(@PathVariable String sessionId) {
        return Mono.fromCallable(() -> {
            DiagnosisContext context = orchestratorService.getContext(sessionId);
            if (context == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在: " + sessionId);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("sessionId", sessionId);
            response.put("parameters", context.getParameterMemory());
            
            return response;
        });
    }
}
