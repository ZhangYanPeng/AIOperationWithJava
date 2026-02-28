package com.company.diagnosis.controller;

import com.company.diagnosis.service.KnowledgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库管理控制器
 * <p>
 * 职责：
 * 1. 提供知识库文档的CRUD接口
 * 2. 支持知识检索功能
 * 3. 支持批量导入知识文档
 * 4. 提供知识库统计信息
 * <p>
 * 设计考虑：
 * - 使用响应式编程处理批量操作
 * - 支持分页查询
 * - 提供多种检索方式(关键词、语义、混合)
 *
 * @author Diagnosis System
 * @date 2026-01-13
 */
@RestController
@RequestMapping("/api/v1/knowledge")
@Tag(name = "知识库管理", description = "管理诊断知识库文档")
public class KnowledgeController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeController.class);

    @Autowired
    private KnowledgeService knowledgeService;

    /**
     * 添加知识文档
     */
    @PostMapping("/documents")
    @Operation(summary = "添加知识文档", description = "向知识库添加单个知识文档")
    public Mono<Map<String, Object>> addDocument(@RequestBody Map<String, Object> document) {
        log.info("添加知识文档: keys={}", document.keySet());

        if (document == null || document.isEmpty()) {
            return Mono.just(errorResponse("文档内容不能为空"));
        }

        String type = (String) document.getOrDefault("type", "domain_knowledge");

        return knowledgeService.addDocument(type, document)
                .onErrorResume(e -> {
                    log.error("添加文档失败", e);
                    return Mono.just(errorResponse("添加失败: " + e.getMessage()));
                });
    }

    /**
     * 批量导入知识文档
     */
    @PostMapping("/documents/batch")
    @Operation(summary = "批量导入文档", description = "批量导入多个知识文档到知识库")
    public Mono<Map<String, Object>> batchAddDocuments(@RequestBody List<Map<String, Object>> documents) {
        log.info("批量导入知识文档: count={}", documents != null ? documents.size() : 0);

        if (documents == null || documents.isEmpty()) {
            return Mono.just(errorResponse("文档列表不能为空"));
        }

        // 提取type,默认为domain_knowledge
        String type = "domain_knowledge";
        if (!documents.isEmpty() && documents.get(0).containsKey("type")) {
            type = (String) documents.get(0).get("type");
        }

        return knowledgeService.batchAddDocuments(type, documents)
                .onErrorResume(e -> {
                    log.error("批量导入失败", e);
                    return Mono.just(errorResponse("批量导入失败: " + e.getMessage()));
                });
    }

    /**
     * 更新知识文档
     */
    @PutMapping("/documents/{docId}")
    @Operation(summary = "更新知识文档", description = "更新指定ID的知识文档内容")
    public Mono<Map<String, Object>> updateDocument(
            @PathVariable String docId,
            @RequestBody Map<String, Object> document) {
        log.info("更新知识文档: docId={}", docId);

        if (!StringUtils.hasText(docId)) {
            return Mono.just(errorResponse("文档ID不能为空"));
        }

        if (document == null || document.isEmpty()) {
            return Mono.just(errorResponse("文档内容不能为空"));
        }

        return knowledgeService.updateDocument(docId, document)
                .onErrorResume(e -> {
                    log.error("更新文档失败", e);
                    return Mono.just(errorResponse("更新失败: " + e.getMessage()));
                });
    }

    /**
     * 删除知识文档
     */
    @DeleteMapping("/documents/{docId}")
    @Operation(summary = "删除知识文档", description = "从知识库删除指定ID的文档")
    public Mono<Map<String, Object>> deleteDocument(@PathVariable String docId) {
        log.info("删除知识文档: docId={}", docId);

        if (!StringUtils.hasText(docId)) {
            return Mono.just(errorResponse("文档ID不能为空"));
        }

        return knowledgeService.deleteDocument(docId)
                .onErrorResume(e -> {
                    log.error("删除文档失败", e);
                    return Mono.just(errorResponse("删除失败: " + e.getMessage()));
                });
    }

    /**
     * 获取知识文档详情
     */
    @GetMapping("/documents/{docId}")
    @Operation(summary = "获取文档详情", description = "根据文档ID获取完整的文档内容")
    public Mono<Map<String, Object>> getDocument(@PathVariable String docId) {
        log.info("获取知识文档: docId={}", docId);

        if (!StringUtils.hasText(docId)) {
            return Mono.just(errorResponse("文档ID不能为空"));
        }

        return knowledgeService.getDocument(docId)
                .onErrorResume(e -> {
                    log.error("获取文档失败", e);
                    return Mono.just(errorResponse("获取失败: " + e.getMessage()));
                });
    }

    /**
     * 检索知识文档
     */
    @GetMapping("/search")
    @Operation(summary = "检索知识文档", description = "根据查询条件检索知识库,支持关键词和语义检索")
    public Flux<Map<String, Object>> searchDocuments(
            @RequestParam String query,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "5") Integer topK,
            @RequestParam(defaultValue = "keyword") String searchMode) {
        log.info("检索知识文档: query={}, type={}, topK={}, mode={}", query, type, topK, searchMode);

        if (!StringUtils.hasText(query)) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "查询内容不能为空");
            return Flux.just(error);
        }

        return knowledgeService.searchDocuments(query, type, topK, searchMode)
                .onErrorResume(e -> {
                    log.error("检索失败", e);
                    Map<String, Object> error = new HashMap<>();
                    error.put("error", "检索失败: " + e.getMessage());
                    return Flux.just(error);
                });
    }

    /**
     * 获取知识库统计信息
     */
    @GetMapping("/statistics")
    @Operation(summary = "获取知识库统计", description = "获取各类型知识库的文档数量统计")
    public Mono<Map<String, Object>> getStatistics() {
        log.info("获取知识库统计");

        return knowledgeService.getStatistics()
                .onErrorResume(e -> {
                    log.error("获取统计失败", e);
                    return Mono.just(errorResponse("获取统计失败: " + e.getMessage()));
                });
    }

    /**
     * 分页查询知识文档
     */
    @GetMapping("/documents")
    @Operation(summary = "分页查询文档", description = "分页获取指定类型的知识文档列表")
    public Mono<Map<String, Object>> listDocuments(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        log.info("分页查询知识文档: type={}, page={}, size={}", type, page, size);

        return knowledgeService.listDocuments(type, page, size)
                .onErrorResume(e -> {
                    log.error("查询失败", e);
                    return Mono.just(errorResponse("查询失败: " + e.getMessage()));
                });
    }

    private Map<String, Object> errorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        response.put("timestamp", Instant.now().toString());
        return response;
    }
}
