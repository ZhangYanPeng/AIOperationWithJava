package com.company.diagnosis.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenAPI配置类
 * 
 * 功能描述:
 * - 配置SpringDoc OpenAPI文档生成
 * - 定义API分组
 * - 配置Swagger UI
 * 
 * 设计考虑:
 * - 自动扫描Controller生成文档
 * - 按功能模块分组展示
 * - 提供详细的API说明和示例
 * - 支持在线测试
 * 
 * @author System
 * @since 2026-01-13
 */
@Configuration
public class OpenApiConfig {

    @Value("${spring.application.name:AI诊断系统}")
    private String applicationName;

    @Value("${server.port:8080}")
    private String serverPort;

    /**
     * 配置OpenAPI基础信息
     * 
     * 功能说明:
     * 1. 配置API标题、描述、版本
     * 2. 配置联系人信息
     * 3. 配置许可证信息
     * 4. 配置服务器列表
     * 
     * @return OpenAPI OpenAPI配置对象
     */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title(applicationName + " API文档")
                        .description("基于智能体的诊断系统API接口文档。\n\n" +
                                "主要功能模块:\n" +
                                "- 诊断服务: 提供智能诊断能力\n" +
                                "- 知识库管理: 管理诊断所需的知识文档\n" +
                                "- 会话管理: 管理诊断会话生命周期\n" +
                                "- SSE推送: 实时推送诊断进度\n")
                        .version("1.0.0")
                        .contact(createContact())
                        .license(createLicense()))
                .servers(createServers());
    }

    /**
     * 创建诊断API分组
     * 
     * 功能说明:
     * 将诊断相关的API归为一组
     * 
     * @return GroupedOpenApi 诊断API分组
     */
    @Bean
    public GroupedOpenApi diagnosisApi() {
        return GroupedOpenApi.builder()
                .group("diagnosis")
                .displayName("诊断服务")
                .pathsToMatch("/api/v1/diagnosis/**")
                .build();
    }

    /**
     * 创建知识库API分组
     * 
     * 功能说明:
     * 将知识库管理相关的API归为一组
     * 
     * @return GroupedOpenApi 知识库API分组
     */
    @Bean
    public GroupedOpenApi knowledgeApi() {
        return GroupedOpenApi.builder()
                .group("knowledge")
                .displayName("知识库管理")
                .pathsToMatch("/api/v1/knowledge/**")
                .build();
    }

    /**
     * 创建会话管理API分组
     * 
     * 功能说明:
     * 将会话管理相关的API归为一组
     * 
     * @return GroupedOpenApi 会话管理API分组
     */
    @Bean
    public GroupedOpenApi sessionApi() {
        return GroupedOpenApi.builder()
                .group("session")
                .displayName("会话管理")
                .pathsToMatch("/api/v1/sessions/**")
                .build();
    }

    /**
     * 创建SSE API分组
     * 
     * 功能说明:
     * 将SSE相关的API归为一组
     * 
     * @return GroupedOpenApi SSE API分组
     */
    @Bean
    public GroupedOpenApi sseApi() {
        return GroupedOpenApi.builder()
                .group("sse")
                .displayName("SSE推送")
                .pathsToMatch("/api/v1/sse/**")
                .build();
    }

    /**
     * 创建所有API分组
     * 
     * 功能说明:
     * 包含所有API的完整分组
     * 
     * @return GroupedOpenApi 所有API分组
     */
    @Bean
    public GroupedOpenApi allApi() {
        return GroupedOpenApi.builder()
                .group("all")
                .displayName("全部接口")
                .pathsToMatch("/api/**")
                .build();
    }

    /**
     * 创建联系人信息
     * 
     * @return Contact 联系人对象
     */
    private Contact createContact() {
        Contact contact = new Contact();
        contact.setName("诊断系统开发团队");
        contact.setEmail("diagnosis-team@company.com");
        contact.setUrl("https://www.company.com/diagnosis");
        return contact;
    }

    /**
     * 创建许可证信息
     * 
     * @return License 许可证对象
     */
    private License createLicense() {
        License license = new License();
        license.setName("Apache 2.0");
        license.setUrl("https://www.apache.org/licenses/LICENSE-2.0");
        return license;
    }

    /**
     * 创建服务器列表
     * 
     * @return List<Server> 服务器列表
     */
    private List<Server> createServers() {
        List<Server> servers = new ArrayList<>();
        
        // 本地开发环境
        Server devServer = new Server();
        devServer.setUrl("http://localhost:" + serverPort);
        devServer.setDescription("本地开发环境");
        servers.add(devServer);
        
        // 测试环境
        Server testServer = new Server();
        testServer.setUrl("https://test-api.company.com");
        testServer.setDescription("测试环境");
        servers.add(testServer);
        
        // 生产环境
        Server prodServer = new Server();
        prodServer.setUrl("https://api.company.com");
        prodServer.setDescription("生产环境");
        servers.add(prodServer);
        
        return servers;
    }
}
