package com.company.diagnosis.controller;

import com.company.diagnosis.service.KnowledgeService;
import com.company.diagnosis.validator.FileValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
public class KnowledgeController {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeController.class);

    @Autowired
    private KnowledgeService knowledgeService;

    @Autowired
    private FileValidator fileValidator;

    /**
     * 添加知识文档
     * <p>
     * 功能说明：
     * 向知识库添加单个知识文档
     *
     * @param document 知识文档对象，包含title、content、type等
     * @return 添加结果Map，包含docId、success等
     */
    @PostMapping("/documents")
    public Mono<Map<String, Object>> addDocument(@RequestBody Map<String, Object> document) {
        // 验证文档格式
        if (document == null || document.isEmpty()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "文档内容不能为空"));
        }
        
        if (!document.containsKey("content") || document.get("content") == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "文档内容字段(content)不能为空"));
        }
        
        // 获取知识库类型
        String type = (String) document.getOrDefault("type", "domain-knowledge");
        
        return knowledgeService.addDocument(type, document)
                .doOnSuccess(result -> logger.info("添加知识文档成功: type={}", type))
                .doOnError(e -> logger.error("添加知识文档失败: {}", e.getMessage()));
    }

    /**
     * 批量导入知识文档
     * <p>
     * 功能说明：
     * 批量导入多个知识文档到知识库
     *
     * @param request 批量导入请求，包含documents列表和type
     * @return 导入结果Map，包含total、success、failed等
     */
    @PostMapping("/documents/batch")
    public Mono<Map<String, Object>> batchAddDocuments(@RequestBody Map<String, Object> request) {
        // 验证请求格式
        if (request == null || !request.containsKey("documents")) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求必须包含documents字段"));
        }
        
        Object docs = request.get("documents");
        if (!(docs instanceof List)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "documents必须是列表"));
        }
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> documents = (List<Map<String, Object>>) docs;
        
        if (documents.isEmpty()) {
            return Mono.just(Map.of(
                    "success", true,
                    "total", 0,
                    "imported", 0,
                    "message", "文档列表为空"
            ));
        }
        
        String type = (String) request.getOrDefault("type", "domain-knowledge");
        
        return knowledgeService.batchAddDocuments(type, documents)
                .map(result -> {
                    Map<String, Object> response = new HashMap<>(result);
                    response.put("total", documents.size());
                    return response;
                })
                .doOnSuccess(result -> logger.info("批量导入完成: total={}", documents.size()))
                .doOnError(e -> logger.error("批量导入失败: {}", e.getMessage()));
    }

    /**
     * 更新知识文档
     * <p>
     * 功能说明：
     * 更新指定ID的知识文档内容
     *
     * @param docId 文档ID
     * @param document 更新后的文档内容
     * @return 更新结果Map
     */
    @PutMapping("/documents/{docId}")
    public Mono<Map<String, Object>> updateDocument(
            @PathVariable String docId,
            @RequestBody Map<String, Object> document) {
        // 验证docId
        if (docId == null || docId.trim().isEmpty()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "文档ID不能为空"));
        }
        
        if (document == null || document.isEmpty()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "更新内容不能为空"));
        }
        
        return knowledgeService.updateDocument(docId, document)
                .doOnSuccess(result -> logger.info("更新知识文档成功: docId={}", docId))
                .doOnError(e -> logger.error("更新知识文档失败: docId={}, error={}", docId, e.getMessage()));
    }

    /**
     * 删除知识文档
     * <p>
     * 功能说明：
     * 从知识库删除指定ID的文档
     *
     * @param docId 文档ID
     * @return 删除结果Map
     */
    @DeleteMapping("/documents/{docId}")
    public Mono<Map<String, Object>> deleteDocument(@PathVariable String docId) {
        // 验证docId
        if (docId == null || docId.trim().isEmpty()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "文档ID不能为空"));
        }
        
        return knowledgeService.deleteDocument(docId)
                .doOnSuccess(result -> logger.info("删除知识文档: docId={}, success={}", docId, result.get("success")))
                .doOnError(e -> logger.error("删除知识文档失败: docId={}, error={}", docId, e.getMessage()));
    }

    /**
     * 获取知识文档详情
     * <p>
     * 功能说明：
     * 根据文档ID获取完整的文档内容
     *
     * @param docId 文档ID
     * @return 文档详情Map
     */
    @GetMapping("/documents/{docId}")
    public Mono<Map<String, Object>> getDocument(@PathVariable String docId) {
        // 验证docId
        if (docId == null || docId.trim().isEmpty()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "文档ID不能为空"));
        }
        
        return knowledgeService.getDocument(docId)
                .flatMap(result -> {
                    if (Boolean.FALSE.equals(result.get("success"))) {
                        return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "文档不存在"));
                    }
                    return Mono.just(result);
                });
    }

    /**
     * 检索知识文档
     * <p>
     * 功能说明：
     * 根据查询条件检索知识库，支持关键词和语义检索
     *
     * @param query 查询关键词
     * @param type 知识库类型(可选)
     * @param topK 返回Top-K结果
     * @param mode 检索模式(keyword/semantic/hybrid)
     * @return 检索结果列表
     */
    @GetMapping("/search")
    public Flux<Map<String, Object>> searchDocuments(
            @RequestParam String query,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "5") Integer topK,
            @RequestParam(defaultValue = "keyword") String mode) {
        // 验证查询参数
        if (query == null || query.trim().isEmpty()) {
            return Flux.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "查询关键词不能为空"));
        }
        
        if (topK <= 0 || topK > 100) {
            topK = 5;
        }
        
        return knowledgeService.searchDocuments(query, type, topK, mode)
                .doOnComplete(() -> logger.debug("知识检索完成: query={}", query));
    }

    /**
     * 获取知识库统计信息
     * <p>
     * 功能说明：
     * 获取各类型知识库的文档数量统计
     *
     * @return 统计信息Map，包含各类型的文档数量
     */
    @GetMapping("/statistics")
    public Mono<Map<String, Object>> getStatistics() {
        return knowledgeService.getStatistics()
                .doOnSuccess(stats -> logger.debug("获取知识库统计: {}", stats.get("totalDocuments")));
    }

    /**
     * 分页查询知识文档
     * <p>
     * 功能说明：
     * 分页获取指定类型的知识文档列表
     *
     * @param type 知识库类型(可选)
     * @param page 页码
     * @param size 每页大小
     * @return 分页结果Map，包含total、page、size、data等
     */
    @GetMapping("/documents")
    public Mono<Map<String, Object>> listDocuments(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        // 验证分页参数
        if (page <= 0) {
            page = 1;
        }
        if (size <= 0 || size > 100) {
            size = 10;
        }
        
        return knowledgeService.listDocuments(type, page, size)
                .doOnSuccess(result -> logger.debug("分页查询知识文档: page={}, size={}", page, result.get("count")));
    }

    /**
     * 按类型搜索知识文档
     * <p>
     * 功能说明：
     * 在指定的多个知识库类型中进行搜索
     *
     * @param request 搜索请求，包含query和types
     * @return 搜索结果列表
     */
    @PostMapping("/search")
    public Flux<Map<String, Object>> searchByTypes(@RequestBody Map<String, Object> request) {
        String query = (String) request.get("query");
        if (query == null || query.trim().isEmpty()) {
            return Flux.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "查询关键词不能为空"));
        }
        
        @SuppressWarnings("unchecked")
        List<String> types = (List<String>) request.get("types");
        Integer topK = (Integer) request.getOrDefault("topK", 5);
        
        return knowledgeService.searchByTypes(query, types, topK);
    }

    /**
     * 验证文档内容
     * <p>
     * 功能说明：
     * 在导入前验证文档格式是否正确
     *
     * @param document 待验证的文档
     * @return 验证结果
     */
    @PostMapping("/validate")
    public Mono<Map<String, Object>> validateDocument(@RequestBody Map<String, Object> document) {
        return Mono.fromCallable(() -> {
            Map<String, Object> result = new HashMap<>();
            
            boolean valid = true;
            StringBuilder errors = new StringBuilder();
            
            if (!document.containsKey("content") || document.get("content") == null) {
                valid = false;
                errors.append("缺少content字段; ");
            }
            
            String fileName = (String) document.get("fileName");
            if (fileName != null) {
                if (!fileValidator.validateFileName(fileName)) {
                    valid = false;
                    errors.append("文件名不合法; ");
                }
            }
            
            result.put("valid", valid);
            if (!valid) {
                result.put("errors", errors.toString().trim());
            }
            
            return result;
        });
    }
}
