package com.company.diagnosis.service;

import com.company.diagnosis.config.ElasticsearchConfig.IndexNameProvider;
import com.company.diagnosis.tool.es.ElasticsearchTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 知识库服务
 * <p>
 * 职责：
 * 1. 管理知识库文档的增删改查
 * 2. 提供知识检索功能（关键词、语义、混合检索）
 * 3. 支持多种知识库类型的管理
 * 4. 提供知识库统计和分析
 * <p>
 * 设计考虑：
 * - 集成Elasticsearch实现高效检索
 * - 支持向量检索实现语义搜索
 * - 提供缓存机制提升查询性能
 * - 支持批量操作
 *
 * @author Diagnosis System
 * @date 2026-01-13
 */
@Service
public class KnowledgeService {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeService.class);

    @Autowired
    private ElasticsearchTool elasticsearchTool;
    
    @Autowired(required = false)
    private IndexNameProvider indexNameProvider;

    /**
     * 添加知识文档
     * <p>
     * 功能说明：
     * 向指定类型的知识库添加单个文档
     *
     * @param type 知识库类型（如kb_tool_interface, kb_diagnosis_manual等）
     * @param document 文档内容Map，包含title、content、metadata等
     * @return 添加结果Map，包含docId
     */
    public Mono<Map<String, Object>> addDocument(String type, Map<String, Object> document) {
        String index = resolveIndexName(type);
        
        // 添加时间戳
        Map<String, Object> docWithMetadata = new HashMap<>(document);
        docWithMetadata.put("createdAt", LocalDateTime.now().toString());
        docWithMetadata.put("updatedAt", LocalDateTime.now().toString());
        
        String docId = document.containsKey("_id") ? 
                String.valueOf(document.get("_id")) : UUID.randomUUID().toString();
        docWithMetadata.remove("_id");
        
        return elasticsearchTool.indexDocument(index, docId, docWithMetadata)
                .doOnSuccess(result -> logger.info("添加知识文档成功: type={}, docId={}", type, docId))
                .doOnError(e -> logger.error("添加知识文档失败: type={}, error={}", type, e.getMessage()));
    }

    /**
     * 批量添加知识文档
     * <p>
     * 功能说明：
     * 批量导入多个知识文档
     *
     * @param type 知识库类型
     * @param documents 文档列表
     * @return 批量添加结果，包含成功数量、失败列表等
     */
    public Mono<Map<String, Object>> batchAddDocuments(String type, List<Map<String, Object>> documents) {
        String index = resolveIndexName(type);
        
        // 为每个文档添加时间戳
        List<Map<String, Object>> docsWithMetadata = documents.stream()
                .map(doc -> {
                    Map<String, Object> newDoc = new HashMap<>(doc);
                    newDoc.put("createdAt", LocalDateTime.now().toString());
                    newDoc.put("updatedAt", LocalDateTime.now().toString());
                    if (!newDoc.containsKey("_id")) {
                        newDoc.put("_id", UUID.randomUUID().toString());
                    }
                    return newDoc;
                })
                .toList();
        
        return elasticsearchTool.bulkIndex(index, docsWithMetadata)
                .doOnSuccess(result -> logger.info("批量添加知识文档完成: type={}, count={}", type, documents.size()))
                .doOnError(e -> logger.error("批量添加知识文档失败: type={}, error={}", type, e.getMessage()));
    }

    /**
     * 更新知识文档
     * <p>
     * 功能说明：
     * 更新指定ID的知识文档
     *
     * @param docId 文档ID
     * @param document 更新后的文档内容
     * @return 更新结果
     */
    public Mono<Map<String, Object>> updateDocument(String docId, Map<String, Object> document) {
        String type = (String) document.getOrDefault("_type", "domain-knowledge");
        String index = resolveIndexName(type);
        
        // 更新时间戳
        Map<String, Object> docWithMetadata = new HashMap<>(document);
        docWithMetadata.put("updatedAt", LocalDateTime.now().toString());
        docWithMetadata.remove("_type");
        docWithMetadata.remove("_id");
        
        return elasticsearchTool.updateDocument(index, docId, docWithMetadata)
                .doOnSuccess(result -> logger.info("更新知识文档成功: docId={}", docId))
                .doOnError(e -> logger.error("更新知识文档失败: docId={}, error={}", docId, e.getMessage()));
    }

    /**
     * 删除知识文档
     * <p>
     * 功能说明：
     * 从知识库删除指定文档
     *
     * @param docId 文档ID
     * @return 删除结果
     */
    public Mono<Map<String, Object>> deleteDocument(String docId) {
        // 尝试从所有索引删除
        return Flux.fromIterable(getAllIndexNames())
                .flatMap(index -> elasticsearchTool.deleteDocument(index, docId))
                .filter(result -> Boolean.TRUE.equals(result.get("success")))
                .next()
                .defaultIfEmpty(Map.of("success", false, "error", "文档不存在"))
                .doOnSuccess(result -> {
                    if (Boolean.TRUE.equals(result.get("success"))) {
                        logger.info("删除知识文档成功: docId={}", docId);
                    }
                });
    }

    /**
     * 获取知识文档详情
     * <p>
     * 功能说明：
     * 根据文档ID获取完整文档内容
     *
     * @param docId 文档ID
     * @return 文档详情Map
     */
    public Mono<Map<String, Object>> getDocument(String docId) {
        // 从所有索引查找
        return Flux.fromIterable(getAllIndexNames())
                .flatMap(index -> elasticsearchTool.getDocument(index, docId))
                .filter(result -> Boolean.TRUE.equals(result.get("success")))
                .next()
                .defaultIfEmpty(Map.of("success", false, "error", "文档不存在"));
    }

    /**
     * 检索知识文档
     * <p>
     * 功能说明：
     * 根据查询条件检索知识库，支持多种检索模式
     *
     * @param query 查询关键词或语义描述
     * @param type 知识库类型（可选）
     * @param topK 返回Top-K结果
     * @param searchMode 检索模式：keyword（关键词）、semantic（语义）、hybrid（混合）
     * @return 检索结果列表，每项包含文档内容和相关度分数
     */
    public Flux<Map<String, Object>> searchDocuments(String query, String type, Integer topK, String searchMode) {
        int limit = topK != null ? topK : 10;
        String mode = searchMode != null ? searchMode : "keyword";
        
        List<String> indicesToSearch = type != null ? 
                List.of(resolveIndexName(type)) : getAllIndexNames();
        
        Map<String, Object> searchQuery = buildSearchQuery(query, mode);
        
        return Flux.fromIterable(indicesToSearch)
                .flatMap(index -> elasticsearchTool.search(index, searchQuery, limit))
                .sort((a, b) -> {
                    Double scoreA = (Double) a.getOrDefault("_score", 0.0);
                    Double scoreB = (Double) b.getOrDefault("_score", 0.0);
                    return scoreB.compareTo(scoreA);
                })
                .take(limit)
                .doOnComplete(() -> logger.debug("知识检索完成: query={}, type={}, mode={}", query, type, mode));
    }

    /**
     * 分页查询知识文档
     * <p>
     * 功能说明：
     * 分页获取指定类型的知识文档列表
     *
     * @param type 知识库类型（可选）
     * @param page 页码（从1开始）
     * @param size 每页大小
     * @return 分页结果Map，包含total、page、size、data等
     */
    public Mono<Map<String, Object>> listDocuments(String type, Integer page, Integer size) {
        int pageNum = page != null && page > 0 ? page : 1;
        int pageSize = size != null && size > 0 ? size : 10;
        
        String index = type != null ? resolveIndexName(type) : getAllIndexNames().get(0);
        
        Map<String, Object> query = new HashMap<>();
        query.put("type", "match_all");
        
        return elasticsearchTool.search(index, query, pageSize * pageNum)
                .skip((long) (pageNum - 1) * pageSize)
                .take(pageSize)
                .collectList()
                .map(docs -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("page", pageNum);
                    result.put("size", pageSize);
                    result.put("data", docs);
                    result.put("count", docs.size());
                    return result;
                });
    }

    /**
     * 获取知识库统计信息
     * <p>
     * 功能说明：
     * 统计各类型知识库的文档数量和存储信息
     *
     * @return 统计信息Map，包含各类型的文档数量、总大小等
     */
    public Mono<Map<String, Object>> getStatistics() {
        return Flux.fromIterable(getAllIndexNames())
                .flatMap(index -> {
                    Map<String, Object> query = new HashMap<>();
                    query.put("type", "match_all");
                    return elasticsearchTool.search(index, query, 0)
                            .count()
                            .map(count -> Map.entry(index, count));
                })
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .map(indexCounts -> {
                    Map<String, Object> stats = new HashMap<>();
                    stats.put("indexCounts", indexCounts);
                    stats.put("totalDocuments", indexCounts.values().stream().mapToLong(Long::longValue).sum());
                    stats.put("indexCount", indexCounts.size());
                    return stats;
                });
    }

    /**
     * 生成文档向量嵌入
     * <p>
     * 功能说明：
     * 为文档内容生成向量嵌入，用于语义检索
     *
     * @param content 文档内容
     * @return 向量嵌入数组
     */
    public Mono<float[]> generateEmbedding(String content) {
        // TODO: 集成实际的嵌入模型API（如DashScope Embedding）
        // 当前返回一个占位符实现
        return Mono.fromCallable(() -> {
            logger.debug("生成向量嵌入: content长度={}", content != null ? content.length() : 0);
            // 返回一个简单的占位向量
            return new float[768]; // 假设嵌入维度为768
        });
    }

    /**
     * 计算向量相似度
     * <p>
     * 功能说明：
     * 计算两个向量之间的相似度
     *
     * @param vector1 向量1
     * @param vector2 向量2
     * @return 相似度分数（0-1之间）
     */
    public Float calculateSimilarity(float[] vector1, float[] vector2) {
        if (vector1 == null || vector2 == null || vector1.length != vector2.length) {
            return 0.0f;
        }
        
        // 计算余弦相似度
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        
        for (int i = 0; i < vector1.length; i++) {
            dotProduct += vector1[i] * vector2[i];
            norm1 += vector1[i] * vector1[i];
            norm2 += vector2[i] * vector2[i];
        }
        
        if (norm1 == 0 || norm2 == 0) {
            return 0.0f;
        }
        
        return (float) (dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2)));
    }
    
    /**
     * 根据知识类型检索
     *
     * @param query 查询内容
     * @param knowledgeTypes 知识类型列表
     * @param topK 返回数量
     * @return 检索结果
     */
    public Flux<Map<String, Object>> searchByTypes(String query, List<String> knowledgeTypes, Integer topK) {
        if (knowledgeTypes == null || knowledgeTypes.isEmpty()) {
            return searchDocuments(query, null, topK, "keyword");
        }
        
        int limit = topK != null ? topK : 10;
        
        return Flux.fromIterable(knowledgeTypes)
                .flatMap(type -> searchDocuments(query, type, limit, "keyword"))
                .sort((a, b) -> {
                    Double scoreA = (Double) a.getOrDefault("_score", 0.0);
                    Double scoreB = (Double) b.getOrDefault("_score", 0.0);
                    return scoreB.compareTo(scoreA);
                })
                .take(limit);
    }
    
    /**
     * 解析索引名称
     */
    private String resolveIndexName(String type) {
        if (indexNameProvider != null) {
            return indexNameProvider.getIndexByType(type);
        }
        
        // 默认索引映射
        return switch (type) {
            case "tool", "tool-interface" -> "kb_tool_interface";
            case "diagnosis", "diagnosis-manual" -> "kb_diagnosis_manual";
            case "reasoning", "reasoning-rules" -> "kb_reasoning_rule";
            case "conclusion", "conclusion-analysis" -> "kb_conclusion_analysis";
            case "domain", "domain-knowledge" -> "kb_domain_knowledge";
            default -> type.startsWith("kb_") ? type : "kb_" + type;
        };
    }
    
    /**
     * 获取所有索引名称
     */
    private List<String> getAllIndexNames() {
        return List.of(
                "kb_tool_interface",
                "kb_diagnosis_manual",
                "kb_reasoning_rule",
                "kb_conclusion_analysis",
                "kb_domain_knowledge"
        );
    }
    
    /**
     * 构建搜索查询
     */
    private Map<String, Object> buildSearchQuery(String query, String mode) {
        Map<String, Object> searchQuery = new HashMap<>();
        
        switch (mode) {
            case "keyword" -> {
                searchQuery.put("type", "multi_match");
                searchQuery.put("queryText", query);
                searchQuery.put("fields", List.of("content", "title", "description"));
            }
            case "semantic" -> {
                // 语义检索需要向量支持
                searchQuery.put("type", "multi_match");
                searchQuery.put("queryText", query);
                searchQuery.put("fields", List.of("content"));
            }
            case "hybrid" -> {
                // 混合检索
                searchQuery.put("type", "multi_match");
                searchQuery.put("queryText", query);
                searchQuery.put("fields", List.of("content", "title", "description", "keywords"));
            }
            default -> {
                searchQuery.put("type", "multi_match");
                searchQuery.put("queryText", query);
            }
        }
        
        return searchQuery;
    }
}
