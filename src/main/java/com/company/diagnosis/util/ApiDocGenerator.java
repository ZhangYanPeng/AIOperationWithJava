package com.company.diagnosis.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;

/**
 * API文档生成工具
 * 
 * 从 OpenAPI 端点获取接口文档并转换为知识库格式
 * 
 * @author AIOperation Team
 * @since 2026-01-15
 */
@Component
public class ApiDocGenerator {
    
    private static final Logger logger = LoggerFactory.getLogger(ApiDocGenerator.class);
    
    private final ObjectMapper objectMapper;
    private final WebClient webClient;
    
    public ApiDocGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder().build();
    }
    
    /**
     * 从 OpenAPI 端点获取并解析接口文档
     * 
     * @param openApiUrl OpenAPI JSON 端点 URL
     * @return 知识库格式的接口文档列表
     */
    public Mono<List<Map<String, Object>>> generateFromOpenApi(String openApiUrl) {
        logger.info("从 OpenAPI 端点获取接口文档: {}", openApiUrl);
        
        return webClient.get()
                .uri(openApiUrl)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseOpenApiJson)
                .doOnSuccess(docs -> logger.info("成功生成 {} 个接口文档", docs.size()))
                .doOnError(e -> logger.error("生成接口文档失败: {}", e.getMessage()));
    }
    
    /**
     * 解析 OpenAPI JSON 并转换为知识库格式
     */
    private List<Map<String, Object>> parseOpenApiJson(String jsonContent) {
        List<Map<String, Object>> documents = new ArrayList<>();
        
        try {
            JsonNode root = objectMapper.readTree(jsonContent);
            JsonNode paths = root.get("paths");
            JsonNode components = root.get("components");
            
            if (paths == null) {
                logger.warn("OpenAPI 文档中没有 paths 定义");
                return documents;
            }
            
            // 遍历所有路径
            Iterator<Map.Entry<String, JsonNode>> pathIterator = paths.fields();
            while (pathIterator.hasNext()) {
                Map.Entry<String, JsonNode> pathEntry = pathIterator.next();
                String path = pathEntry.getKey();
                JsonNode pathItem = pathEntry.getValue();
                
                // 遍历该路径下的所有方法
                Iterator<Map.Entry<String, JsonNode>> methodIterator = pathItem.fields();
                while (methodIterator.hasNext()) {
                    Map.Entry<String, JsonNode> methodEntry = methodIterator.next();
                    String method = methodEntry.getKey().toUpperCase();
                    JsonNode operation = methodEntry.getValue();
                    
                    // 跳过非HTTP方法的字段
                    if (!isHttpMethod(method)) {
                        continue;
                    }
                    
                    Map<String, Object> doc = convertToKnowledgeDoc(path, method, operation, components);
                    documents.add(doc);
                }
            }
            
        } catch (Exception e) {
            logger.error("解析 OpenAPI JSON 失败: {}", e.getMessage(), e);
        }
        
        return documents;
    }
    
    private boolean isHttpMethod(String method) {
        return Set.of("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS").contains(method);
    }
    
    /**
     * 将单个接口转换为知识库文档格式
     */
    private Map<String, Object> convertToKnowledgeDoc(String path, String method, 
                                                       JsonNode operation, JsonNode components) {
        Map<String, Object> doc = new LinkedHashMap<>();
        
        // 基础信息
        String operationId = getTextValue(operation, "operationId", path + "_" + method);
        String summary = getTextValue(operation, "summary", "");
        String description = getTextValue(operation, "description", summary);
        
        doc.put("id", UUID.randomUUID().toString().replace("-", ""));
        doc.put("tool_name", summary.isEmpty() ? operationId : summary);
        doc.put("api_path", path);
        doc.put("http_method", method);
        doc.put("description", description);
        doc.put("operation_id", operationId);
        
        // 标签
        JsonNode tags = operation.get("tags");
        if (tags != null && tags.isArray()) {
            List<String> tagList = new ArrayList<>();
            tags.forEach(tag -> tagList.add(tag.asText()));
            doc.put("tags", tagList);
        }
        
        // 参数
        List<Map<String, Object>> parameters = new ArrayList<>();
        JsonNode params = operation.get("parameters");
        if (params != null && params.isArray()) {
            for (JsonNode param : params) {
                Map<String, Object> paramDoc = new LinkedHashMap<>();
                paramDoc.put("name", getTextValue(param, "name", ""));
                paramDoc.put("in", getTextValue(param, "in", "query"));
                paramDoc.put("required", param.has("required") && param.get("required").asBoolean());
                paramDoc.put("description", getTextValue(param, "description", ""));
                
                JsonNode schema = param.get("schema");
                if (schema != null) {
                    paramDoc.put("type", getTextValue(schema, "type", "string"));
                    if (schema.has("example")) {
                        paramDoc.put("example", schema.get("example").asText());
                    }
                }
                
                parameters.add(paramDoc);
            }
        }
        doc.put("parameters", parameters);
        
        // 请求体
        JsonNode requestBody = operation.get("requestBody");
        if (requestBody != null) {
            Map<String, Object> requestBodyDoc = new LinkedHashMap<>();
            requestBodyDoc.put("required", requestBody.has("required") && requestBody.get("required").asBoolean());
            requestBodyDoc.put("description", getTextValue(requestBody, "description", ""));
            
            JsonNode content = requestBody.get("content");
            if (content != null && content.has("application/json")) {
                JsonNode jsonContent = content.get("application/json");
                if (jsonContent.has("schema")) {
                    requestBodyDoc.put("schema", resolveSchema(jsonContent.get("schema"), components));
                }
            }
            
            doc.put("request_body", requestBodyDoc);
        }
        
        // 响应
        JsonNode responses = operation.get("responses");
        if (responses != null) {
            Map<String, Object> responsesDoc = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> respIterator = responses.fields();
            while (respIterator.hasNext()) {
                Map.Entry<String, JsonNode> respEntry = respIterator.next();
                String statusCode = respEntry.getKey();
                JsonNode response = respEntry.getValue();
                
                Map<String, Object> respDoc = new LinkedHashMap<>();
                respDoc.put("description", getTextValue(response, "description", ""));
                
                JsonNode respContent = response.get("content");
                if (respContent != null && respContent.has("application/json")) {
                    JsonNode jsonContent = respContent.get("application/json");
                    if (jsonContent.has("schema")) {
                        respDoc.put("schema", resolveSchema(jsonContent.get("schema"), components));
                    }
                }
                
                responsesDoc.put(statusCode, respDoc);
            }
            doc.put("responses", responsesDoc);
        }
        
        // 生成完整文本描述（用于向量检索）
        doc.put("content", generateTextContent(doc));
        
        // 元数据
        doc.put("type", "tool_interface");
        doc.put("createdAt", LocalDateTime.now().toString());
        doc.put("updatedAt", LocalDateTime.now().toString());
        
        return doc;
    }
    
    private String getTextValue(JsonNode node, String field, String defaultValue) {
        if (node != null && node.has(field)) {
            return node.get(field).asText(defaultValue);
        }
        return defaultValue;
    }
    
    private Map<String, Object> resolveSchema(JsonNode schema, JsonNode components) {
        Map<String, Object> schemaDoc = new LinkedHashMap<>();
        
        if (schema.has("$ref")) {
            // 解析引用
            String ref = schema.get("$ref").asText();
            String schemaName = ref.substring(ref.lastIndexOf("/") + 1);
            schemaDoc.put("$ref", schemaName);
            
            // 尝试获取实际定义
            if (components != null && components.has("schemas")) {
                JsonNode schemas = components.get("schemas");
                if (schemas.has(schemaName)) {
                    schemaDoc.put("resolved", parseSchemaProperties(schemas.get(schemaName)));
                }
            }
        } else {
            schemaDoc = parseSchemaProperties(schema);
        }
        
        return schemaDoc;
    }
    
    private Map<String, Object> parseSchemaProperties(JsonNode schema) {
        Map<String, Object> result = new LinkedHashMap<>();
        
        if (schema.has("type")) {
            result.put("type", schema.get("type").asText());
        }
        
        if (schema.has("properties")) {
            Map<String, Object> props = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> propIterator = schema.get("properties").fields();
            while (propIterator.hasNext()) {
                Map.Entry<String, JsonNode> propEntry = propIterator.next();
                Map<String, Object> propDoc = new LinkedHashMap<>();
                JsonNode prop = propEntry.getValue();
                
                if (prop.has("type")) {
                    propDoc.put("type", prop.get("type").asText());
                }
                if (prop.has("description")) {
                    propDoc.put("description", prop.get("description").asText());
                }
                if (prop.has("example")) {
                    propDoc.put("example", prop.get("example").asText());
                }
                
                props.put(propEntry.getKey(), propDoc);
            }
            result.put("properties", props);
        }
        
        return result;
    }
    
    /**
     * 生成完整的文本描述，用于向量检索
     */
    private String generateTextContent(Map<String, Object> doc) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("接口名称：").append(doc.get("tool_name")).append("\n");
        sb.append("接口路径：").append(doc.get("api_path")).append("\n");
        sb.append("请求方法：").append(doc.get("http_method")).append("\n");
        sb.append("功能描述：").append(doc.get("description")).append("\n");
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> params = (List<Map<String, Object>>) doc.get("parameters");
        if (params != null && !params.isEmpty()) {
            sb.append("请求参数：\n");
            for (Map<String, Object> param : params) {
                sb.append("  - ").append(param.get("name"))
                        .append(" (").append(param.get("in")).append("): ")
                        .append(param.get("description")).append("\n");
            }
        }
        
        return sb.toString();
    }
    
    /**
     * 批量生成接口文档
     * 
     * @param openApiUrls 多个 OpenAPI 端点 URL
     * @return 合并后的接口文档列表
     */
    public Mono<List<Map<String, Object>>> generateFromMultipleEndpoints(List<String> openApiUrls) {
        return Flux.fromIterable(openApiUrls)
                .flatMap(this::generateFromOpenApi)
                .collectList()
                .map(lists -> {
                    List<Map<String, Object>> merged = new ArrayList<>();
                    lists.forEach(merged::addAll);
                    return merged;
                });
    }
}
