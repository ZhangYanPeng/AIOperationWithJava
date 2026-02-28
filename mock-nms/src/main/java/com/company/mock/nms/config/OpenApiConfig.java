package com.company.mock.nms.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 配置类
 * 
 * 配置 Swagger UI 和 OpenAPI 文档生成
 * 
 * @author AIOperation Team
 * @since 2026-01-15
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("模拟网管系统 API")
                        .version("1.0.0")
                        .description("""
                                模拟网管系统接口文档
                                
                                提供以下功能模块：
                                - 设备管理：设备查询、状态查询
                                - 告警管理：告警查询、确认、清除
                                - 性能数据：CPU、内存、网络、磁盘监控
                                - 配置管理：配置查询、更新、备份
                                - 拓扑关系：连接关系、完整拓扑
                                - 日志查询：设备日志、系统日志
                                """)
                        .contact(new Contact()
                                .name("AIOperation Team")
                                .email("support@company.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8081")
                                .description("本地开发服务器")
                ));
    }
}
