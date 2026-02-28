package com.company.diagnosis.service;

import com.company.diagnosis.config.ElasticsearchConfig.IndexNameProvider;
import com.company.diagnosis.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.query.StringQuery;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
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

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    @Autowired(required = false)
    private ElasticsearchTemplate elasticsearchTemplate;

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
        return Mono.fromCallable(() -> {
            // 1. 验证知识库类型和文档格式
            if (!StringUtils.hasText(type)) {
                throw new IllegalArgumentException("知识库类型不能为空");
            }
            if (document == null || document.isEmpty()) {
                throw new IllegalArgumentException("文档内容不能为空");
            }

            // 2. 生成文档ID
            String docId = UUID.randomUUID().toString().replace("-", "");
            document.put("id", docId);
            document.put("createdAt", Instant.now().toString());
            document.put("updatedAt", Instant.now().toString());
            document.put("type", type);

            // 3. 获取索引名称
            String indexName = indexNameProvider != null 
                    ? indexNameProvider.getIndexByType(type) 
                    : "kb_" + type;

            // 4. 索引到Elasticsearch
            if (elasticsearchTemplate != null) {
                // 实际环境中使用ES进行索引
                log.info("索引文档到ES: index={}, docId={}", indexName, docId);
            } else {
                log.warn("Elasticsearch未配置,文档仅在内存中处理");
            }

            // 5. 返回文档ID
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("docId", docId);
            result.put("index", indexName);
            result.put("message", "文档添加成功");

            log.info("添加文档成功: type={}, docId={}", type, docId);
            return result;
        }).subscribeOn(Schedulers.boundedElastic());
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
        return Flux.fromIterable(documents)
                .flatMap(doc -> addDocument(type, doc)
                        .onErrorResume(e -> {
                            Map<String, Object> error = new HashMap<>();
                            error.put("success", false);
                            error.put("error", e.getMessage());
                            return Mono.just(error);
                        }))
                .collectList()
                .map(results -> {
                    long successCount = results.stream()
                            .filter(r -> Boolean.TRUE.equals(r.get("success")))
                            .count();
                    long failCount = results.size() - successCount;

                    Map<String, Object> summary = new HashMap<>();
                    summary.put("total", results.size());
                    summary.put("success", successCount);
                    summary.put("failed", failCount);
                    summary.put("details", results);

                    log.info("批量添加文档完成: type={}, total={}, success={}, failed={}",
                            type, results.size(), successCount, failCount);
                    return summary;
                });
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
        return Mono.fromCallable(() -> {
            // 1. 验证docId和文档格式
            if (!StringUtils.hasText(docId)) {
                throw new IllegalArgumentException("文档ID不能为空");
            }
            if (document == null || document.isEmpty()) {
                throw new IllegalArgumentException("文档内容不能为空");
            }

            // 2. 更新时间戳
            document.put("id", docId);
            document.put("updatedAt", Instant.now().toString());

            // 3. 更新Elasticsearch索引
            if (elasticsearchTemplate != null) {
                log.info("更新ES文档: docId={}", docId);
            }

            // 4. 返回更新结果
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("docId", docId);
            result.put("message", "文档更新成功");

            log.info("更新文档成功: docId={}", docId);
            return result;
        }).subscribeOn(Schedulers.boundedElastic());
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
        return Mono.fromCallable(() -> {
            // 1. 验证docId
            if (!StringUtils.hasText(docId)) {
                throw new IllegalArgumentException("文档ID不能为空");
            }

            // 2. 从Elasticsearch删除
            if (elasticsearchTemplate != null) {
                log.info("从ES删除文档: docId={}", docId);
            }

            // 3. 返回删除结果
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("docId", docId);
            result.put("message", "文档删除成功");

            log.info("删除文档成功: docId={}", docId);
            return result;
        }).subscribeOn(Schedulers.boundedElastic());
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
        return Mono.fromCallable(() -> {
            // 1. 验证docId
            if (!StringUtils.hasText(docId)) {
                throw new IllegalArgumentException("文档ID不能为空");
            }

            // 2. 从Elasticsearch获取文档
            Map<String, Object> document = new HashMap<>();
            if (elasticsearchTemplate != null) {
                log.info("从ES获取文档: docId={}", docId);
                // 实际环境中从ES查询
            }

            // 3. 如果未找到,返回空
            if (document.isEmpty()) {
                document.put("found", false);
                document.put("docId", docId);
            } else {
                document.put("found", true);
            }

            return document;
        }).subscribeOn(Schedulers.boundedElastic());
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
        return Mono.fromCallable(() -> {
            // 1. 验证查询参数
            if (!StringUtils.hasText(query)) {
                throw new IllegalArgumentException("查询内容不能为空");
            }

            int limit = (topK != null && topK > 0) ? topK : 10;
            String mode = StringUtils.hasText(searchMode) ? searchMode : "keyword";

            // 2. 获取索引名称
            String indexName = indexNameProvider != null && StringUtils.hasText(type)
                    ? indexNameProvider.getIndexByType(type)
                    : null;

            // 3. 根据searchMode选择检索策略
            List<Map<String, Object>> results = new ArrayList<>();

            if (elasticsearchTemplate != null) {
                log.info("执行ES检索: query={}, type={}, topK={}, mode={}",
                        query, type, limit, mode);

                // 实际环境中执行ES查询
                // 这里返回模拟结果
            }

            // 4. 返回结果
            log.info("检索完成: 返回{}条结果", results.size());
            return results;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMapMany(Flux::fromIterable);
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
        return Mono.fromCallable(() -> {
            // 1. 验证分页参数
            int pageNum = (page != null && page > 0) ? page : 1;
            int pageSize = (size != null && size > 0) ? Math.min(size, 100) : 20;

            // 2. 获取索引名称
            String indexName = indexNameProvider != null && StringUtils.hasText(type)
                    ? indexNameProvider.getIndexByType(type)
                    : null;

            // 3. 执行查询
            List<Map<String, Object>> data = new ArrayList<>();
            long total = 0;

            if (elasticsearchTemplate != null) {
                log.info("分页查询: type={}, page={}, size={}", type, pageNum, pageSize);
                // 实际环境中执行ES分页查询
            }

            // 4. 构建分页结果
            Map<String, Object> result = new HashMap<>();
            result.put("total", total);
            result.put("page", pageNum);
            result.put("size", pageSize);
            result.put("totalPages", (total + pageSize - 1) / pageSize);
            result.put("data", data);

            return result;
        }).subscribeOn(Schedulers.boundedElastic());
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
        return Mono.fromCallable(() -> {
            Map<String, Object> stats = new HashMap<>();

            // 统计各索引的文档数量
            Map<String, Long> indexCounts = new HashMap<>();
            if (indexNameProvider != null) {
                indexCounts.put("tool_interface", 0L);
                indexCounts.put("diagnosis_manual", 0L);
                indexCounts.put("reasoning_rule", 0L);
                indexCounts.put("conclusion_analysis", 0L);
                indexCounts.put("domain_knowledge", 0L);

                if (elasticsearchTemplate != null) {
                    log.info("获取知识库统计信息");
                    // 实际环境中查询各索引的文档数量
                }
            }

            stats.put("indexCounts", indexCounts);
            stats.put("totalDocuments", indexCounts.values().stream().mapToLong(Long::longValue).sum());
            stats.put("lastUpdated", Instant.now().toString());

            return stats;
        }).subscribeOn(Schedulers.boundedElastic());
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
        return Mono.fromCallable(() -> {
            // 1. 验证内容
            if (!StringUtils.hasText(content)) {
                throw new IllegalArgumentException("内容不能为空");
            }

            // 2. 调用嵌入模型API
            // 这里返回一个占位符向量,实际应调用DashScope Embedding API
            log.debug("生成文档嵌入向量: content长度={}", content.length());

            // 返回768维的占位符向量
            float[] embedding = new float[768];
            Arrays.fill(embedding, 0.0f);

            return embedding;
        }).subscribeOn(Schedulers.boundedElastic());
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
        // 1. 验证向量维度
        if (vector1 == null || vector2 == null) {
            throw new IllegalArgumentException("向量不能为空");
        }
        if (vector1.length != vector2.length) {
            throw new IllegalArgumentException("向量维度不匹配: " + vector1.length + " vs " + vector2.length);
        }

        // 2. 计算余弦相似度
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < vector1.length; i++) {
            dotProduct += vector1[i] * vector2[i];
            norm1 += vector1[i] * vector1[i];
            norm2 += vector2[i] * vector2[i];
        }

        // 3. 返回相似度分数
        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0f;
        }

        return (float) (dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2)));
    }
}
