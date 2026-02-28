package com.company.diagnosis.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
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

    private static final Logger log = LoggerFactory.getLogger(OpenApiConfig.class);

    @Value("${server.port:8080}")
    private int serverPort;

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
        OpenAPI openAPI = new OpenAPI()
                .info(new Info()
                        .title("智能诊断系统 API")
                        .description("基于AgentScope的智能诊断系统RESTful API文档\n\n" +
                                "## 功能模块\n" +
                                "- **诊断服务**: 提供告警诊断、故障分析等核心功能\n" +
                                "- **知识库管理**: 管理诊断手册、推理规则、工具接口文档等\n" +
                                "- **会话管理**: 管理诊断会话的生命周期\n" +
                                "- **工具服务**: 提供外部工具调用代理\n\n" +
                                "## 技术特点\n" +
                                "- 响应式编程(Spring WebFlux + Project Reactor)\n" +
                                "- SSE流式输出支持\n" +
                                "- 多LLM提供商支持(DashScope/OpenAI/Ollama)")
                        .version("1.0.0")
                        .contact(createContact())
                        .license(createLicense()))
                .servers(createServers());
        
        log.info("OpenAPI配置完成");
        return openAPI;
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
                .group("1-diagnosis")
                .displayName("诊断服务")
                .pathsToMatch("/api/diagnosis/**")
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
                .group("2-knowledge")
                .displayName("知识库管理")
                .pathsToMatch("/api/knowledge/**")
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
                .group("3-session")
                .displayName("会话管理")
                .pathsToMatch("/api/sessions/**")
                .build();
    }

    /**
     * 创建工具API分组
     * 
     * 功能说明:
     * 将工具调用相关的API归为一组
     * 
     * @return GroupedOpenApi 工具API分组
     */
    @Bean
    public GroupedOpenApi toolApi() {
        return GroupedOpenApi.builder()
                .group("4-tool")
                .displayName("工具服务")
                .pathsToMatch("/api/tools/**")
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
                .group("0-all")
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
        return new Contact()
                .name("诊断系统开发团队")
                .email("diagnosis-team@company.com")
                .url("https://github.com/company/diagnosis-system");
    }

    /**
     * 创建许可证信息
     * 
     * @return License 许可证对象
     */
    private License createLicense() {
        return new License()
                .name("Apache 2.0")
                .url("https://www.apache.org/licenses/LICENSE-2.0");
    }

    /**
     * 创建服务器列表
     * 
     * @return List<Server> 服务器列表
     */
    private List<Server> createServers() {
        return Arrays.asList(
                new Server()
                        .url("http://localhost:" + serverPort)
                        .description("本地开发环境"),
                new Server()
                        .url("http://dev-api.company.com")
                        .description("开发测试环境"),
                new Server()
                        .url("https://api.company.com")
                        .description("生产环境")
        );
    }
}
