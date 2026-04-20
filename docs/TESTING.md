# Testing Guide

这份文档只回答三件事：

1. 这个项目的测试分几层。
2. 本地怎么跑测试。
3. 如果测试突然红了，先查什么。

## 测试基线

当前测试主路径不依赖 MySQL、Redis、RabbitMQ、XXL-Job Admin。

测试环境默认来自：

- `src/test/resources/application.yml`
- `src/test/resources/sql/schema.sql`

测试配置的关键点：

- 数据库使用 H2 内存库
- `spring.flyway.enabled=false`
- `spring.sql.init.mode=embedded`
- MyBatis XML 通过 `mybatis-plus.mapper-locations=classpath*:mybatis/*.xml` 显式加载

这意味着本地跑 `mvn test` 时，不需要先起 Docker 依赖。

## 测试分层

### 1. 应用层测试

这类测试主要验证业务规则，不关心真实 MySQL 或 MQ 是否在线。

重点覆盖：

- 工作流状态流转
- 发布、回滚、审核等业务语义
- 错误码与权限边界
- 任务和事件生成逻辑

常见测试类：

- `ContentWorkflowApplicationServiceTest`
- `ContentWorkflowStateMachineTest`
- `PublishIdempotencyAndDiffTest`
- `WorkflowRecoveryServiceTest`

### 2. 持久层测试

这类测试验证 MyBatis-Plus 映射、自定义 XML SQL、表结构约束和仓储读写契约。

核心测试类：

- `PersistenceLayerTest`

这层最容易因为以下原因出问题：

- 新增了表字段，但忘了同步测试 schema
- 新增了自定义 Mapper XML，但测试配置没覆盖到
- H2 和 MySQL 语法差异导致 SQL 在测试里不兼容

### 3. Web / 鉴权测试

这类测试主要验证：

- JWT 认证
- 角色权限
- 参数解析
- 统一错误响应

常见测试类：

- `ContentWorkflowAuthorizationTest`
- `ContentWorkflowJwtAuthenticationTest`
- `WorkflowAuthenticationControllerTest`
- `WorkflowRequestContextPropagationTest`

### 4. tracing / worker / messaging 测试

这类测试验证跨线程、消息头、worker 执行期的 trace 与 request id 语义。

常见测试类：

- `WorkflowLogContextMdcRestoreTest`
- `PublishTaskWorkerMdcPropagationTest`
- `WorkflowMessagingTraceContextContractTest`
- `WorkflowXxlJobHandlersTracePropagationTest`

## 本地运行方式

### 全量测试

```powershell
mvn test
```

如果 Maven 不在 `PATH`：

```powershell
& "D:\MAVEN\apache-maven-3.9.6\bin\mvn.cmd" test
```

### 只编译测试代码

```powershell
mvn -DskipTests compile
```

### 只跑单个测试类

```powershell
mvn -Dtest=PersistenceLayerTest test
```

### 只跑单个测试方法

```powershell
mvn -Dtest=PersistenceLayerTest#mappers_canPersistAndQuery test
```

### 只排查鉴权相关

```powershell
mvn -Dtest=ContentWorkflowJwtAuthenticationTest,WorkflowAuthenticationControllerTest,ContentWorkflowAuthorizationTest test
```

## 写代码时最容易忘的同步点

### 1. 改了 MySQL/Flyway，也要同步测试 schema

主运行路径看的是：

- `src/main/resources/db/migration/mysql`

测试路径看的是：

- `src/test/resources/sql/schema.sql`

如果你只改了 Flyway，没有同步测试 schema，最先红的通常是持久层测试。

### 2. 改了 `src/main/resources/sql/schema.sql`，不代表测试就自动跟上

这个项目现在有一份测试专用 H2 schema。

原因很现实：

- 主 schema 可能包含 MySQL 方言
- 测试需要 H2 兼容写法

所以测试 schema 需要单独维护，不是冗余文件。

### 3. 新增自定义 Mapper XML 时，要确认测试能加载到

测试环境里显式开启了：

```yaml
mybatis-plus:
  mapper-locations: classpath*:mybatis/*.xml
```

如果你新增 XML 后命名、路径或 namespace 不一致，持久层测试会直接报：

- `Invalid bound statement (not found)`

### 4. 改了安全返回结构时，不要手写一个裸 `ObjectMapper`

项目里错误响应包含 `OffsetDateTime` 等 Java Time 类型。

如果你在测试里自己 `new ObjectMapper()`，很容易触发序列化失败。优先使用：

- Spring 注入的 `ObjectMapper`
- 或 `JsonMapper.builder().findAndAddModules().build()`

## 常见失败与排查

### 1. `PersistenceLayerTest` 报 SQL 或 bound statement 错误

先按这个顺序查：

1. `src/test/resources/application.yml` 里的 `mapper-locations` 还在不在
2. XML 文件路径是不是 `src/main/resources/mybatis/*.xml`
3. interface 的 namespace 和 XML 的 namespace 是否一致
4. 测试 schema 是否带上了最新列
5. SQL 是否用了 H2 不支持的 MySQL 方言

### 2. Web 鉴权测试从 200 变成 403

这通常不是框架坏了，而是角色不匹配。

例如：

- 接口需要 `DRAFT_WRITE`
- 你在测试里注入的是 `OPERATOR`

那现在 403 才是正确结果。

### 3. 登录或 JWT 测试突然全挂

先检查：

- `demo` 账号是否还存在于测试上下文
- JWT secret 是否被测试覆盖了
- 你是不是改了登录接口返回结构
- 你是不是用错了 `ObjectMapper`

### 4. tracing 测试失败，但业务功能没坏

优先确认：

- 新增线程池、scheduler、listener 时，有没有用统一的 `WorkflowLogContext`
- 是否绕开了现有 helper，自己手写了一套 trace header 解析

## 推荐的本地回归顺序

### 改了业务规则

先跑：

```powershell
mvn -Dtest=ContentWorkflowApplicationServiceTest,ContentWorkflowStateMachineTest test
```

### 改了 Mapper / SQL / 表结构

先跑：

```powershell
mvn -Dtest=PersistenceLayerTest test
```

### 改了鉴权、登录、JWT

先跑：

```powershell
mvn -Dtest=WorkflowAuthenticationControllerTest,ContentWorkflowJwtAuthenticationTest,ContentWorkflowAuthorizationTest test
```

### 改了 tracing / worker / scheduler / messaging

先跑：

```powershell
mvn -Dtest=WorkflowLogContextMdcRestoreTest,PublishTaskWorkerMdcPropagationTest,WorkflowMessagingTraceContextContractTest,WorkflowXxlJobHandlersTracePropagationTest test
```

最后再跑一次全量：

```powershell
mvn test
```

## 什么时候需要起外部依赖再测

大多数单元测试和集成测试不需要。

只有你在做下面这些事情时，才建议额外手动起依赖做联调：

- 验证真实 MySQL + Flyway 启动
- 验证 Redis readiness 和缓存命中行为
- 验证 RabbitMQ outbox relay 的真实投递
- 验证 XXL-Job Admin 的真实触发

这类验证更适合配合 `docs/OPERATIONS.md` 里的启动说明来做，不属于默认 `mvn test` 范畴。
