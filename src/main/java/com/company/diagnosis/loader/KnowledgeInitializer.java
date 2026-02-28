package com.company.diagnosis.loader;

import com.company.diagnosis.service.KnowledgeService;
import com.company.diagnosis.util.ApiDocGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 知识库初始化器
 * 
 * 在应用启动时自动从模拟网管同步接口文档到知识库
 * 
 * @author AIOperation Team
 * @since 2026-01-15
 */
@Component
public class KnowledgeInitializer implements ApplicationRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(KnowledgeInitializer.class);
    
    private final KnowledgeService knowledgeService;
    private final ApiDocGenerator apiDocGenerator;
    
    @Value("${tool-api.base-url:http://localhost:8081}")
    private String mockNmsBaseUrl;
    
    @Value("${knowledge.auto-sync:false}")
    private boolean autoSync;
    
    public KnowledgeInitializer(KnowledgeService knowledgeService, ApiDocGenerator apiDocGenerator) {
        this.knowledgeService = knowledgeService;
        this.apiDocGenerator = apiDocGenerator;
    }
    
    @Override
    public void run(ApplicationArguments args) {
        if (!autoSync) {
            logger.info("知识库自动同步已禁用，跳过初始化");
            return;
        }
        
        logger.info("开始初始化知识库...");
        
        try {
            syncFromMockNms();
            logger.info("知识库初始化完成");
        } catch (Exception e) {
            logger.warn("知识库初始化失败（可能模拟网管未启动）: {}", e.getMessage());
        }
    }
    
    /**
     * 从模拟网管同步接口文档
     */
    public void syncFromMockNms() {
        String openApiUrl = mockNmsBaseUrl + "/v3/api-docs";
        logger.info("从模拟网管同步接口文档: {}", openApiUrl);
        
        apiDocGenerator.generateFromOpenApi(openApiUrl)
                .flatMap(docs -> {
                    if (docs.isEmpty()) {
                        logger.warn("未获取到任何接口文档");
                        return reactor.core.publisher.Mono.empty();
                    }
                    
                    logger.info("获取到 {} 个接口文档，开始导入知识库", docs.size());
                    return knowledgeService.batchAddDocuments("tool_interface", docs);
                })
                .doOnSuccess(result -> {
                    if (result != null) {
                        logger.info("接口文档导入完成: {}", result);
                    }
                })
                .doOnError(e -> logger.error("接口文档导入失败: {}", e.getMessage()))
                .subscribe();
    }
    
    /**
     * 手动触发同步（供 REST API 调用）
     */
    public Map<String, Object> triggerSync() {
        try {
            String openApiUrl = mockNmsBaseUrl + "/v3/api-docs";
            List<Map<String, Object>> docs = apiDocGenerator.generateFromOpenApi(openApiUrl).block();
            
            if (docs == null || docs.isEmpty()) {
                return Map.of(
                        "success", false,
                        "message", "未获取到任何接口文档"
                );
            }
            
            Object result = knowledgeService.batchAddDocuments("tool_interface", docs).block();
            
            return Map.of(
                    "success", true,
                    "message", "同步完成",
                    "documentCount", docs.size(),
                    "result", result != null ? result : "无返回结果"
            );
            
        } catch (Exception e) {
            logger.error("手动同步失败: {}", e.getMessage(), e);
            return Map.of(
                    "success", false,
                    "message", "同步失败: " + e.getMessage()
            );
        }
    }
}
