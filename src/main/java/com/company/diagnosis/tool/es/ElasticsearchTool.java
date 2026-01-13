package com.company.diagnosis.tool.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.company.diagnosis.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * Elasticsearch工具类
 * <p>
 * 职责：
 * 1. 提供Elasticsearch的索引操作
 * 2. 提供文档的增删改查
 * 3. 提供检索功能
 * 4. 支持向量检索
 *
 * @author Diagnosis System
 * @date 2026-01-13
 */
@Component
public class ElasticsearchTool {

    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchTool.class);

    @Autowired(required = false)
    private ElasticsearchClient elasticsearchClient;
    
    @Autowired(required = false)
    private ElasticsearchTemplate elasticsearchTemplate;

    /**
     * 索引文档
     *
     * @param index 索引名称
     * @param docId 文档ID
     * @param document 文档内容
     * @return 索引结果
     */
    public Mono<Map<String, Object>> indexDocument(String index, String docId, Map<String, Object> document) {
        return Mono.fromCallable(() -> {
            Map<String, Object> result = new HashMap<>();
            
            try {
                if (elasticsearchClient == null) {
                    logger.warn("Elasticsearch客户端未初始化");
                    result.put("success", false);
                    result.put("error", "Elasticsearch客户端未初始化");
                    return result;
                }
                
                String id = docId != null ? docId : UUID.randomUUID().toString();
                
                IndexRequest<Map<String, Object>> request = IndexRequest.of(i -> i
                        .index(index)
                        .id(id)
                        .document(document)
                );
                
                IndexResponse response = elasticsearchClient.index(request);
                
                result.put("success", true);
                result.put("docId", response.id());
                result.put("index", response.index());
                result.put("version", response.version());
                result.put("result", response.result().jsonValue());
                
                logger.debug("文档索引成功: index={}, docId={}", index, id);
                
            } catch (Exception e) {
                logger.error("文档索引失败: index={}, docId={}, error={}", index, docId, e.getMessage(), e);
                result.put("success", false);
                result.put("error", e.getMessage());
            }
            
            return result;
        });
    }

    /**
     * 检索文档
     *
     * @param index 索引名称
     * @param query 查询条件
     * @param topK 返回数量
     * @return 检索结果
     */
    public Flux<Map<String, Object>> search(String index, Map<String, Object> query, Integer topK) {
        return Mono.fromCallable(() -> {
            List<Map<String, Object>> results = new ArrayList<>();
            
            try {
                if (elasticsearchClient == null) {
                    logger.warn("Elasticsearch客户端未初始化");
                    return results;
                }
                
                int size = topK != null ? topK : 10;
                
                // 构建查询
                SearchRequest request = buildSearchRequest(index, query, size);
                
                SearchResponse<Map> response = elasticsearchClient.search(request, Map.class);
                
                for (Hit<Map> hit : response.hits().hits()) {
                    Map<String, Object> doc = new HashMap<>();
                    doc.put("_id", hit.id());
                    doc.put("_index", hit.index());
                    doc.put("_score", hit.score());
                    if (hit.source() != null) {
                        doc.putAll(hit.source());
                    }
                    results.add(doc);
                }
                
                logger.debug("检索成功: index={}, 返回{}条结果", index, results.size());
                
            } catch (Exception e) {
                logger.error("检索失败: index={}, error={}", index, e.getMessage(), e);
            }
            
            return results;
        }).flatMapMany(Flux::fromIterable);
    }
    
    /**
     * 全文搜索
     *
     * @param index 索引名称
     * @param queryText 查询文本
     * @param fields 搜索字段
     * @param topK 返回数量
     * @return 检索结果
     */
    public Flux<Map<String, Object>> fullTextSearch(String index, String queryText, 
                                                     List<String> fields, Integer topK) {
        Map<String, Object> query = new HashMap<>();
        query.put("queryText", queryText);
        query.put("fields", fields != null ? fields : List.of("content", "title"));
        query.put("type", "multi_match");
        
        return search(index, query, topK);
    }
    
    /**
     * 根据ID获取文档
     *
     * @param index 索引名称
     * @param docId 文档ID
     * @return 文档内容
     */
    public Mono<Map<String, Object>> getDocument(String index, String docId) {
        return Mono.fromCallable(() -> {
            Map<String, Object> result = new HashMap<>();
            
            try {
                if (elasticsearchClient == null) {
                    logger.warn("Elasticsearch客户端未初始化");
                    result.put("success", false);
                    result.put("error", "Elasticsearch客户端未初始化");
                    return result;
                }
                
                GetRequest request = GetRequest.of(g -> g
                        .index(index)
                        .id(docId)
                );
                
                GetResponse<Map> response = elasticsearchClient.get(request, Map.class);
                
                if (response.found()) {
                    result.put("success", true);
                    result.put("_id", response.id());
                    result.put("_index", response.index());
                    if (response.source() != null) {
                        result.putAll(response.source());
                    }
                } else {
                    result.put("success", false);
                    result.put("error", "文档不存在");
                }
                
            } catch (Exception e) {
                logger.error("获取文档失败: index={}, docId={}, error={}", index, docId, e.getMessage(), e);
                result.put("success", false);
                result.put("error", e.getMessage());
            }
            
            return result;
        });
    }
    
    /**
     * 删除文档
     *
     * @param index 索引名称
     * @param docId 文档ID
     * @return 删除结果
     */
    public Mono<Map<String, Object>> deleteDocument(String index, String docId) {
        return Mono.fromCallable(() -> {
            Map<String, Object> result = new HashMap<>();
            
            try {
                if (elasticsearchClient == null) {
                    logger.warn("Elasticsearch客户端未初始化");
                    result.put("success", false);
                    result.put("error", "Elasticsearch客户端未初始化");
                    return result;
                }
                
                DeleteRequest request = DeleteRequest.of(d -> d
                        .index(index)
                        .id(docId)
                );
                
                DeleteResponse response = elasticsearchClient.delete(request);
                
                result.put("success", true);
                result.put("result", response.result().jsonValue());
                
                logger.debug("文档删除成功: index={}, docId={}", index, docId);
                
            } catch (Exception e) {
                logger.error("删除文档失败: index={}, docId={}, error={}", index, docId, e.getMessage(), e);
                result.put("success", false);
                result.put("error", e.getMessage());
            }
            
            return result;
        });
    }
    
    /**
     * 更新文档
     *
     * @param index 索引名称
     * @param docId 文档ID
     * @param document 更新内容
     * @return 更新结果
     */
    public Mono<Map<String, Object>> updateDocument(String index, String docId, Map<String, Object> document) {
        return Mono.fromCallable(() -> {
            Map<String, Object> result = new HashMap<>();
            
            try {
                if (elasticsearchClient == null) {
                    logger.warn("Elasticsearch客户端未初始化");
                    result.put("success", false);
                    result.put("error", "Elasticsearch客户端未初始化");
                    return result;
                }
                
                UpdateRequest<Map<String, Object>, Map<String, Object>> request = UpdateRequest.of(u -> u
                        .index(index)
                        .id(docId)
                        .doc(document)
                );
                
                UpdateResponse<Map<String, Object>> response = elasticsearchClient.update(request, 
                        (Class<Map<String, Object>>)(Class<?>)Map.class);
                
                result.put("success", true);
                result.put("result", response.result().jsonValue());
                result.put("version", response.version());
                
                logger.debug("文档更新成功: index={}, docId={}", index, docId);
                
            } catch (Exception e) {
                logger.error("更新文档失败: index={}, docId={}, error={}", index, docId, e.getMessage(), e);
                result.put("success", false);
                result.put("error", e.getMessage());
            }
            
            return result;
        });
    }
    
    /**
     * 批量索引文档
     *
     * @param index 索引名称
     * @param documents 文档列表
     * @return 批量索引结果
     */
    public Mono<Map<String, Object>> bulkIndex(String index, List<Map<String, Object>> documents) {
        return Mono.fromCallable(() -> {
            Map<String, Object> result = new HashMap<>();
            
            try {
                if (elasticsearchClient == null) {
                    logger.warn("Elasticsearch客户端未初始化");
                    result.put("success", false);
                    result.put("error", "Elasticsearch客户端未初始化");
                    return result;
                }
                
                BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
                
                for (Map<String, Object> doc : documents) {
                    String id = doc.containsKey("_id") ? 
                            String.valueOf(doc.get("_id")) : UUID.randomUUID().toString();
                    Map<String, Object> docCopy = new HashMap<>(doc);
                    docCopy.remove("_id");
                    
                    bulkBuilder.operations(op -> op
                            .index(idx -> idx
                                    .index(index)
                                    .id(id)
                                    .document(docCopy)
                            )
                    );
                }
                
                BulkResponse response = elasticsearchClient.bulk(bulkBuilder.build());
                
                result.put("success", !response.errors());
                result.put("took", response.took());
                result.put("itemCount", response.items().size());
                
                if (response.errors()) {
                    List<String> errors = new ArrayList<>();
                    response.items().forEach(item -> {
                        if (item.error() != null) {
                            errors.add(item.error().reason());
                        }
                    });
                    result.put("errors", errors);
                }
                
                logger.debug("批量索引完成: index={}, 文档数={}", index, documents.size());
                
            } catch (Exception e) {
                logger.error("批量索引失败: index={}, error={}", index, e.getMessage(), e);
                result.put("success", false);
                result.put("error", e.getMessage());
            }
            
            return result;
        });
    }
    
    /**
     * 构建搜索请求
     */
    @SuppressWarnings("unchecked")
    private SearchRequest buildSearchRequest(String index, Map<String, Object> queryParams, int size) {
        return SearchRequest.of(s -> {
            s.index(index).size(size);
            
            String type = (String) queryParams.getOrDefault("type", "match_all");
            
            switch (type) {
                case "multi_match" -> {
                    String queryText = (String) queryParams.get("queryText");
                    List<String> fields = (List<String>) queryParams.getOrDefault("fields", 
                            List.of("content", "title"));
                    
                    if (queryText != null && !queryText.isEmpty()) {
                        s.query(q -> q.multiMatch(m -> m
                                .query(queryText)
                                .fields(fields)
                        ));
                    }
                }
                case "match" -> {
                    String field = (String) queryParams.getOrDefault("field", "content");
                    String queryText = (String) queryParams.get("queryText");
                    
                    if (queryText != null && !queryText.isEmpty()) {
                        s.query(q -> q.match(m -> m
                                .field(field)
                                .query(queryText)
                        ));
                    }
                }
                case "term" -> {
                    String field = (String) queryParams.get("field");
                    String value = (String) queryParams.get("value");
                    
                    if (field != null && value != null) {
                        s.query(q -> q.term(t -> t
                                .field(field)
                                .value(value)
                        ));
                    }
                }
                default -> s.query(q -> q.matchAll(m -> m));
            }
            
            return s;
        });
    }
}
