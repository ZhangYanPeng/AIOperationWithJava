package com.company.diagnosis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot诊断系统主启动类
 * 
 * 功能描述:
 * - 应用程序入口
 * - 自动配置Spring Boot组件
 * - 启动嵌入式服务器
 * 
 * @author System
 * @since 2026-01-13
 */
@SpringBootApplication
public class DiagnosisApplication {

    /**
     * 主方法
     * 
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(DiagnosisApplication.class, args);
    }
}
