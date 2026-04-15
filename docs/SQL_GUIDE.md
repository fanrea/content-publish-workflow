# SQL Guide（建表脚本与使用说明）

本项目当前阶段只提供建表 SQL（用于本地开发/评审/联调），不引入迁移系统；如果要用于长期演进，建议后续接入 Flyway 或 Liquibase。

## 脚本文件说明

- `sql/schema.sql`
  - 面向 MySQL 8.x 的“重置脚本”（`DROP + CREATE`）。
  - 包含 InnoDB/charset 以及更贴近生产的索引定义。
  - 适合：本地 MySQL 一键重建、代码评审、压测环境快速初始化。

- `sql/seed_demo.sql`
  - 演示/压测用数据灌入脚本（可选）。
  - 用于生成稳定的数据量，便于列表页/统计接口压测与指标观察。

- `src/main/resources/sql/schema.sql`
  - 面向 embedded DB（默认 H2，MySQL mode）的初始化脚本。
  - 尽量使用兼容语法，避免 MySQL 专属语法。
  - 由 Spring Boot 在 embedded DB 场景下自动加载（见 `application.yml` 的 `spring.sql.init.*`）。

结论：
- 使用 MySQL 时，以 `sql/schema.sql` 为准。
- 使用 H2 演示/单测时，以 `src/main/resources/sql/schema.sql` 为准。

## MySQL 初始化示例

1. 创建数据库（示例库名：`cpw`）

```sql
CREATE DATABASE IF NOT EXISTS cpw DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

2. 执行建表脚本

```bash
mysql -uroot -p cpw < sql/schema.sql
```

3. （可选）灌入演示数据

```bash
mysql -uroot -p cpw < sql/seed_demo.sql
```

## 关键索引说明（面向前端列表页）

`content_draft` 提供了面向列表分页的索引组合：
- 按状态筛选：`idx_content_draft_status (workflow_status)`
- 按时间排序：`idx_content_draft_updated_at (updated_at)`、`idx_content_draft_created_at (created_at)`
- 常用组合：`idx_content_draft_status_updated (workflow_status, updated_at)`、`idx_content_draft_status_created (workflow_status, created_at)`

说明：
- 列表页常见模式是“按状态 Tab 过滤 + 按更新时间倒序分页”，组合索引可以显著减少 filesort/回表成本。
- `keyword` 的 `%like%` 模糊检索通常无法有效利用普通索引；如需高质量检索，建议接入 ES，或者评估 MySQL FULLTEXT（需要中文分词与相关性排序策略）。


