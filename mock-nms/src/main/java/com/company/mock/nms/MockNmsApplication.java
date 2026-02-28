package com.company.mock.nms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 模拟网管系统启动类
 * 
 * 提供模拟的网管接口，用于智能诊断系统测试
 * 
 * @author AIOperation Team
 * @since 2026-01-15
 */
@SpringBootApplication
public class MockNmsApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(MockNmsApplication.class, args);
    }
}
