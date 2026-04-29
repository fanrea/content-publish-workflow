# 协同文档编辑系统（Collaborative Document Service）

> 仓库名与 Java 包名保留历史命名（`content-publish-workflow` / `com.contentworkflow`），当前业务定位已统一为“协同文档编辑系统”。

## 系统定位

本项目是一个面向多人协作场景的文档后端服务，目标是提供可落地、可扩展、可追问的一致性写入链路，而不是只做基础 CRUD。

当前重点覆盖四类能力：

- 实时协作编辑：基于原生 WebSocket（`/ws/docs`）承载编辑操作、增量同步、光标广播、在线成员。
- 评论协作：评论、回复、解决/重开、删除，以及编辑过程中锚点迁移。
- 成员权限：`owner / editor / viewer` 角色模型，控制读写、恢复、成员管理等权限。
- 事件扩展：通过 `DocumentEventPublisher` 抽象支持事件发布，默认可降级为空实现，可按配置接入 RocketMQ。

## 业务边界

范围内（本项目负责）：

- 协同文档的写入一致性与并发控制
- 协作态同步（增量操作追赶、在线态广播）
- 评论与成员权限协作
- 事务提交后事件发布（扩展集成点）

范围外（本项目当前不负责）：

- “内容审核/发布/下线/回滚”式发布工作流业务
- 搜索索引、推荐、计费等下游业务系统
- 完整企业认证中心（当前演示通过 `X-Editor-Id` / `X-Editor-Name` 识别操作者）

## 架构与一致性

架构说明与可追问不变量见：

- [docs/architecture-collab.md](docs/architecture-collab.md)
- [docs/storage-snapshot-roadmap.md](docs/storage-snapshot-roadmap.md)

该文档包含：

- 文本架构图（ASCII）
- 3 条系统不变量（版本单调、操作幂等、最终收敛）
- HTTP/WS 统一写路径的目标态语义

## 当前能力清单

HTTP（前缀：`/api/docs`）：

- 文档：创建、列表、详情、整篇更新、版本列表、版本恢复
- 写路径：`PUT /api/docs/{documentId}` 已统一走 operation pipeline（`applyFullReplaceOperation -> applyOperation`）
- 恢复路径：`POST /api/docs/{documentId}/restore` 已统一走 operation pipeline（owner 校验 + `changeType=RESTORE`）
- 操作：按 revision 增量查询操作（重连追赶）
- 评论：评论增删改状态、评论回复、评论统计
- 成员：成员列表、owner 设置成员角色

WebSocket（端点：`/ws/docs`）：

- `JOIN` / `LEAVE`
- `EDIT_OP`（操作写入 + ACK/APPLIED）
- `SYNC_OPS`（断线重连增量追赶）
- `CURSOR_MOVE`

## 核心技术栈

- Java 17
- Spring Boot 3.2.5
- MyBatis-Plus + MyBatis XML
- MySQL + Flyway
- Spring WebSocket（原生协议）
- Redis（可选缓存扩展）
- RocketMQ（可选事件扩展）
- H2（测试）

## 快速启动

1. 启动依赖（MySQL、Redis）：

```bash
docker compose -f compose.local.yml up -d
```

2. 启动服务：

```bash
mvn spring-boot:run
```

3. 最小联调入口：

- HTTP：`http://localhost:8080/api/docs`
- WebSocket：`ws://localhost:8080/ws/docs`

## 项目结构（核心）

```text
src/main/java/com/contentworkflow
├─ common
│  └─ websocket
└─ document
   ├─ interfaces
   ├─ application
   ├─ domain
   └─ infrastructure
```

## 面试讲解建议（30 秒版）

可以把项目表述为：

> 这是一个协同文档编辑系统后端，重点解决并发编辑一致性。写入侧采用版本检查 + 幂等键 + OT-lite 重定位；读写协作态通过 WebSocket 增量同步；评论与成员权限围绕同一文档模型演进；事件在事务提交后发布并可扩展到 RocketMQ。当前仓库名保留历史命名，但业务域已统一为协同文档。
