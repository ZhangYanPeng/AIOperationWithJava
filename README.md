# Spring Boot 智能诊断系统

基于Spring Boot 3.2和AgentScope Java v1.0实现的智能诊断系统。

## 项目概述

本项目是对原Python FastAPI后端的完整重构,采用Spring Boot框架和AgentScope Java实现层级化的智能体架构,提供智能诊断能力。

### 核心特性

- **层级化智能体架构**: 支持配置驱动的多层智能体协作
- **响应式编程**: 基于Project Reactor实现非阻塞异步处理
- **SSE流式输出**: 实时推送诊断过程和结果
- **知识库管理**: 集成Elasticsearch,支持向量检索和全文检索
- **Kafka消息驱动**: 支持消息触发和HTTP API触发
- **API文档自动生成**: 基于SpringDoc OpenAPI自动生成接口文档

## 技术栈

- **应用框架**: Spring Boot 3.2+
- **智能体框架**: AgentScope Java 1.0+
- **Java版本**: OpenJDK 21
- **构建工具**: Maven 3.8+
- **搜索引擎**: Elasticsearch 8.17
- **消息队列**: Apache Kafka
- **响应式框架**: Project Reactor 3.x
- **API文档**: SpringDoc OpenAPI 2.x

## 项目结构

```
spring-boot-diagnosis-system/
├── src/main/java/com/company/diagnosis/
│   ├── config/                 # 配置类
│   ├── controller/             # 控制器
│   ├── service/                # 服务层
│   ├── agent/                  # 智能体
│   │   ├── base/              # 基类
│   │   ├── layer1/            # 接口调用层
│   │   └── layer2/            # 执行层
│   ├── model/                  # 数据模型
│   │   ├── context/           # 上下文
│   │   ├── config/            # 配置模型
│   │   ├── event/             # 事件
│   │   ├── dto/               # 数据传输对象
│   │   └── entity/            # 实体
│   ├── factory/                # 工厂类
│   ├── tool/                   # 工具类
│   └── DiagnosisApplication.java
├── src/main/resources/
│   ├── application.yml         # 主配置
│   ├── application-dev.yml     # 开发环境配置
│   ├── application-prod.yml    # 生产环境配置
│   ├── agent-config.yml        # 智能体配置
│   ├── prompts/                # 提示词模板
│   └── logback-spring.xml      # 日志配置
├── src/test/                   # 测试代码
├── knowledge-base/             # 知识库
└── pom.xml                     # Maven配置
```

## 快速开始

### 前置条件

- JDK 21
- Maven 3.8+
- Elasticsearch 8.17
- Kafka (可选,用于消息触发)

### 配置

1. 复制配置文件模板:
```bash
cp src/main/resources/application-dev.yml src/main/resources/application-local.yml
```

2. 修改配置:
编辑`application-local.yml`,配置Elasticsearch、Kafka和LLM相关参数

### 构建

```bash
mvn clean package
```

### 运行

```bash
# 开发环境
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 或使用JAR
java -jar target/spring-boot-diagnosis-system-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev
```

### 访问

- 应用地址: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- API文档: http://localhost:8080/v3/api-docs
- Actuator: http://localhost:8080/actuator

## 智能体架构

### 层级设计

系统采用严格的层级调用架构:

```
第N层 (可选层,配置驱动)
  └─→ 调用第N-1层
      └─→ 调用第N-2层
          └─→ ...
              └─→ 第2层 (基础执行层,必选)
                  └─→ 第1层 (接口调用层,必选)
                      └─→ 外部Tool API
```

**核心原则**:
- 每层只能调用其直接下层
- 第1层是唯一与外部API交互的层
- 第2~N层通过层层委托实现复杂业务逻辑
- 支持通过配置文件动态增加层级

### 配置驱动

智能体通过`agent-config.yml`配置文件定义,支持:
- 动态添加新层级
- 配置每层的模型参数
- 指定知识库类型
- 配置提示词模板
- 设置超时和重试策略

## API接口

### 诊断API

#### 启动诊断

```http
POST /api/diagnosis/start
Content-Type: application/json

{
  "diagnosisType": "设备故障诊断",
  "problem": "M机单播通道中断",
  "parameters": {
    "deviceId": "设备001",
    "channelNum": 1
  }
}
```

响应: SSE事件流

### 知识库API

详见Swagger UI文档。

## 开发指南

### 添加新的智能体层级

1. 在`agent-config.yml`中添加新层级配置:

```yaml
execution-layers:
  - name: "NewLayerAgent"
    layer: 4
    enabled: true
    lowerLayerAgent: "PreviousLayerAgent"
    # ... 其他配置
```

2. 创建对应的智能体类(继承BaseIntelligentAgent或ExecutionLayerAgent)

3. 实现execute方法

### 添加新的工具函数

在智能体类中使用`@Tool`注解声明工具函数:

```java
@Tool(description = "工具描述")
public String myTool(String param) {
    // TODO: 实现
    return null;
}
```

## 测试

```bash
# 运行所有测试
mvn test

# 运行指定测试
mvn test -Dtest=DiagnosisServiceTest
```

## 部署

### Docker部署

TODO: 待补充Dockerfile

### 生产环境配置

使用`application-prod.yml`配置文件,确保:
- 配置正确的Elasticsearch集群地址
- 配置正确的Kafka集群地址
- 配置LLM API密钥
- 禁用Swagger UI
- 配置合适的日志级别

## 故障排查

### 常见问题

1. **Elasticsearch连接失败**
   - 检查ES服务是否启动
   - 检查用户名密码是否正确
   - 检查网络连接

2. **Kafka消费失败**
   - 检查Kafka服务是否启动
   - 检查topic是否存在
   - 检查消费者组配置

3. **智能体执行超时**
   - 增加超时配置
   - 检查LLM服务是否正常
   - 检查网络延迟

## 许可证

TODO: 待补充

## 联系方式

TODO: 待补充

---

**注意**: 本项目当前处于框架搭建阶段,所有方法使用TODO占位,待后续实现具体业务逻辑。
# Spring Boot 智能诊断系统

基于Spring Boot 3.2和AgentScope Java v1.0实现的智能诊断系统。

## 项目概述

本项目是对原Python FastAPI后端的完整重构,采用Spring Boot框架和AgentScope Java实现层级化的智能体架构,提供智能诊断能力。

### 核心特性

- **层级化智能体架构**: 支持配置驱动的多层智能体协作
- **响应式编程**: 基于Project Reactor实现非阻塞异步处理
- **SSE流式输出**: 实时推送诊断过程和结果
- **知识库管理**: 集成Elasticsearch,支持向量检索和全文检索
- **Kafka消息驱动**: 支持消息触发和HTTP API触发
- **API文档自动生成**: 基于SpringDoc OpenAPI自动生成接口文档

## 技术栈

- **应用框架**: Spring Boot 3.2+
- **智能体框架**: AgentScope Java 1.0+
- **Java版本**: OpenJDK 21
- **构建工具**: Maven 3.8+
- **搜索引擎**: Elasticsearch 8.17
- **消息队列**: Apache Kafka
- **响应式框架**: Project Reactor 3.x
- **API文档**: SpringDoc OpenAPI 2.x

## 项目结构

```
spring-boot-diagnosis-system/
├── src/main/java/com/company/diagnosis/
│   ├── config/                 # 配置类
│   ├── controller/             # 控制器
│   ├── service/                # 服务层
│   ├── agent/                  # 智能体
│   │   ├── base/              # 基类
│   │   ├── layer1/            # 接口调用层
│   │   └── layer2/            # 执行层
│   ├── model/                  # 数据模型
│   │   ├── context/           # 上下文
│   │   ├── config/            # 配置模型
│   │   ├── event/             # 事件
│   │   ├── dto/               # 数据传输对象
│   │   └── entity/            # 实体
│   ├── factory/                # 工厂类
│   ├── tool/                   # 工具类
│   └── DiagnosisApplication.java
├── src/main/resources/
│   ├── application.yml         # 主配置
│   ├── application-dev.yml     # 开发环境配置
│   ├── application-prod.yml    # 生产环境配置
│   ├── agent-config.yml        # 智能体配置
│   ├── prompts/                # 提示词模板
│   └── logback-spring.xml      # 日志配置
├── src/test/                   # 测试代码
├── knowledge-base/             # 知识库
└── pom.xml                     # Maven配置
```

## 快速开始

### 前置条件

- JDK 21
- Maven 3.8+
- Elasticsearch 8.17
- Kafka (可选,用于消息触发)

### 配置

1. 复制配置文件模板:
```bash
cp src/main/resources/application-dev.yml src/main/resources/application-local.yml
```

2. 修改配置:
编辑`application-local.yml`,配置Elasticsearch、Kafka和LLM相关参数

### 构建

```bash
mvn clean package
```

### 运行

```bash
# 开发环境
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 或使用JAR
java -jar target/spring-boot-diagnosis-system-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev
```

### 访问

- 应用地址: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- API文档: http://localhost:8080/v3/api-docs
- Actuator: http://localhost:8080/actuator

## 智能体架构

### 层级设计

系统采用严格的层级调用架构:

```
第N层 (可选层,配置驱动)
  └─→ 调用第N-1层
      └─→ 调用第N-2层
          └─→ ...
              └─→ 第2层 (基础执行层,必选)
                  └─→ 第1层 (接口调用层,必选)
                      └─→ 外部Tool API
```

**核心原则**:
- 每层只能调用其直接下层
- 第1层是唯一与外部API交互的层
- 第2~N层通过层层委托实现复杂业务逻辑
- 支持通过配置文件动态增加层级

### 配置驱动

智能体通过`agent-config.yml`配置文件定义,支持:
- 动态添加新层级
- 配置每层的模型参数
- 指定知识库类型
- 配置提示词模板
- 设置超时和重试策略

## API接口

### 诊断API

#### 启动诊断

```http
POST /api/diagnosis/start
Content-Type: application/json

{
  "diagnosisType": "设备故障诊断",
  "problem": "M机单播通道中断",
  "parameters": {
    "deviceId": "设备001",
    "channelNum": 1
  }
}
```

响应: SSE事件流

### 知识库API

详见Swagger UI文档。

## 开发指南

### 添加新的智能体层级

1. 在`agent-config.yml`中添加新层级配置:

```yaml
execution-layers:
  - name: "NewLayerAgent"
    layer: 4
    enabled: true
    lowerLayerAgent: "PreviousLayerAgent"
    # ... 其他配置
```

2. 创建对应的智能体类(继承BaseIntelligentAgent或ExecutionLayerAgent)

3. 实现execute方法

### 添加新的工具函数

在智能体类中使用`@Tool`注解声明工具函数:

```java
@Tool(description = "工具描述")
public String myTool(String param) {
    // TODO: 实现
    return null;
}
```

## 测试

```bash
# 运行所有测试
mvn test

# 运行指定测试
mvn test -Dtest=DiagnosisServiceTest
```

## 部署

### Docker部署

TODO: 待补充Dockerfile

### 生产环境配置

使用`application-prod.yml`配置文件,确保:
- 配置正确的Elasticsearch集群地址
- 配置正确的Kafka集群地址
- 配置LLM API密钥
- 禁用Swagger UI
- 配置合适的日志级别

## 故障排查

### 常见问题

1. **Elasticsearch连接失败**
   - 检查ES服务是否启动
   - 检查用户名密码是否正确
   - 检查网络连接

2. **Kafka消费失败**
   - 检查Kafka服务是否启动
   - 检查topic是否存在
   - 检查消费者组配置

3. **智能体执行超时**
   - 增加超时配置
   - 检查LLM服务是否正常
   - 检查网络延迟

## 许可证

TODO: 待补充

## 联系方式

TODO: 待补充

---

**注意**: 本项目当前处于框架搭建阶段,所有方法使用TODO占位,待后续实现具体业务逻辑。
