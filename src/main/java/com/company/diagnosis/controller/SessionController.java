package com.company.diagnosis.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
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
@Tag(name = "会话管理", description = "管理诊断会话的生命周期")
public class SessionController {

    private static final Logger log = LoggerFactory.getLogger(SessionController.class);

    /**
     * 会话存储（简化实现,生产环境应使用持久化存储）
     */
    private final ConcurrentHashMap<String, Map<String, Object>> sessionStore = new ConcurrentHashMap<>();

    /**
     * 创建新会话
     */
    @PostMapping
    @Operation(summary = "创建会话", description = "创建一个新的诊断会话,返回会话ID")
    public Mono<Map<String, Object>> createSession(@RequestBody(required = false) Map<String, Object> metadata) {
        log.info("创建新会话");

        String sessionId = UUID.randomUUID().toString().replace("-", "");
        Instant now = Instant.now();

        Map<String, Object> session = new LinkedHashMap<>();
        session.put("sessionId", sessionId);
        session.put("status", "CREATED");
        session.put("createdAt", now.toString());
        session.put("updatedAt", now.toString());
        session.put("metadata", metadata != null ? metadata : new HashMap<>());
        session.put("context", new HashMap<>());
        session.put("history", new ArrayList<>());

        sessionStore.put(sessionId, session);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("sessionId", sessionId);
        response.put("createdAt", now.toString());
        response.put("message", "会话创建成功");

        log.info("会话创建成功: sessionId={}", sessionId);
        return Mono.just(response);
    }

    /**
     * 获取会话详情
     */
    @GetMapping("/{sessionId}")
    @Operation(summary = "获取会话详情", description = "根据会话ID获取完整的会话信息")
    public Mono<Map<String, Object>> getSession(@PathVariable String sessionId) {
        log.info("获取会话详情: sessionId={}", sessionId);

        if (!StringUtils.hasText(sessionId)) {
            return Mono.just(errorResponse("会话ID不能为空"));
        }

        Map<String, Object> session = sessionStore.get(sessionId);
        if (session == null) {
            Map<String, Object> notFound = new LinkedHashMap<>();
            notFound.put("success", false);
            notFound.put("error", "会话不存在");
            notFound.put("sessionId", sessionId);
            return Mono.just(notFound);
        }

        return Mono.just(session);
    }

    /**
     * 获取会话上下文
     */
    @GetMapping("/{sessionId}/context")
    @Operation(summary = "获取会话上下文", description = "获取会话的DiagnosisContext对象")
    public Mono<Map<String, Object>> getSessionContext(@PathVariable String sessionId) {
        log.info("获取会话上下文: sessionId={}", sessionId);

        if (!StringUtils.hasText(sessionId)) {
            return Mono.just(errorResponse("会话ID不能为空"));
        }

        Map<String, Object> session = sessionStore.get(sessionId);
        if (session == null) {
            return Mono.just(errorResponse("会话不存在"));
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>) session.getOrDefault("context", new HashMap<>());
        
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("sessionId", sessionId);
        response.put("context", context);

        return Mono.just(response);
    }

    /**
     * 获取会话执行历史
     */
    @GetMapping("/{sessionId}/history")
    @Operation(summary = "获取执行历史", description = "获取会话中所有层级的执行步骤历史")
    public Flux<Map<String, Object>> getSessionHistory(@PathVariable String sessionId) {
        log.info("获取会话历史: sessionId={}", sessionId);

        if (!StringUtils.hasText(sessionId)) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "会话ID不能为空");
            return Flux.just(error);
        }

        Map<String, Object> session = sessionStore.get(sessionId);
        if (session == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "会话不存在");
            return Flux.just(error);
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> history = (List<Map<String, Object>>) session.getOrDefault("history", new ArrayList<>());

        return Flux.fromIterable(history);
    }

    /**
     * 更新会话状态
     */
    @PutMapping("/{sessionId}")
    @Operation(summary = "更新会话", description = "更新会话的状态信息")
    public Mono<Map<String, Object>> updateSession(
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> updates) {
        log.info("更新会话: sessionId={}", sessionId);

        if (!StringUtils.hasText(sessionId)) {
            return Mono.just(errorResponse("会话ID不能为空"));
        }

        Map<String, Object> session = sessionStore.get(sessionId);
        if (session == null) {
            return Mono.just(errorResponse("会话不存在"));
        }

        // 更新字段
        if (updates != null) {
            updates.forEach((key, value) -> {
                if (!"sessionId".equals(key) && !"createdAt".equals(key)) {
                    session.put(key, value);
                }
            });
        }
        session.put("updatedAt", Instant.now().toString());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("sessionId", sessionId);
        response.put("message", "会话更新成功");

        return Mono.just(response);
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/{sessionId}")
    @Operation(summary = "删除会话", description = "删除指定的会话及其所有关联数据")
    public Mono<Map<String, Object>> deleteSession(@PathVariable String sessionId) {
        log.info("删除会话: sessionId={}", sessionId);

        if (!StringUtils.hasText(sessionId)) {
            return Mono.just(errorResponse("会话ID不能为空"));
        }

        Map<String, Object> removed = sessionStore.remove(sessionId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", removed != null);
        response.put("sessionId", sessionId);
        response.put("message", removed != null ? "会话删除成功" : "会话不存在");

        return Mono.just(response);
    }

    /**
     * 查询会话列表
     */
    @GetMapping
    @Operation(summary = "查询会话列表", description = "分页查询会话列表,支持按状态过滤")
    public Mono<Map<String, Object>> listSessions(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        log.info("查询会话列表: status={}, page={}, size={}", status, page, size);

        List<Map<String, Object>> allSessions = new ArrayList<>(sessionStore.values());

        // 过滤状态
        if (StringUtils.hasText(status)) {
            allSessions = allSessions.stream()
                    .filter(s -> status.equalsIgnoreCase((String) s.get("status")))
                    .toList();
        }

        // 分页
        int total = allSessions.size();
        int fromIndex = Math.max(0, (page - 1) * size);
        int toIndex = Math.min(total, fromIndex + size);

        List<Map<String, Object>> pagedSessions = fromIndex < total 
                ? allSessions.subList(fromIndex, toIndex) 
                : new ArrayList<>();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("total", total);
        response.put("page", page);
        response.put("size", size);
        response.put("totalPages", (total + size - 1) / size);
        response.put("data", pagedSessions);

        return Mono.just(response);
    }

    /**
     * 清理过期会话
     */
    @PostMapping("/cleanup")
    @Operation(summary = "清理过期会话", description = "清理超过保留期限的会话数据")
    public Mono<Map<String, Object>> cleanupSessions(
            @RequestParam(defaultValue = "30") Integer daysToKeep) {
        log.info("清理过期会话: daysToKeep={}", daysToKeep);

        Instant cutoff = Instant.now().minus(daysToKeep, ChronoUnit.DAYS);
        int cleanedCount = 0;

        Iterator<Map.Entry<String, Map<String, Object>>> iterator = sessionStore.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Map<String, Object>> entry = iterator.next();
            String createdAt = (String) entry.getValue().get("createdAt");
            if (createdAt != null) {
                try {
                    Instant sessionTime = Instant.parse(createdAt);
                    if (sessionTime.isBefore(cutoff)) {
                        iterator.remove();
                        cleanedCount++;
                    }
                } catch (Exception e) {
                    // 解析失败,跳过
                }
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("cleanedCount", cleanedCount);
        response.put("daysToKeep", daysToKeep);
        response.put("remainingCount", sessionStore.size());
        response.put("timestamp", Instant.now().toString());

        log.info("会话清理完成: cleaned={}, remaining={}", cleanedCount, sessionStore.size());
        return Mono.just(response);
    }

    private Map<String, Object> errorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        response.put("timestamp", Instant.now().toString());
        return response;
    }
}
