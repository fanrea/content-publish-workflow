# SQL 使用说明

这份文档说明当前项目里两份 schema 脚本分别做什么，以及本地应该如何正确使用它们。

## 一、两份 schema 的职责

项目中有两份建表脚本：

### 1. `src/main/resources/sql/schema.sql`

用途：

- 给 Spring Boot 在嵌入式数据库模式下自动初始化

特点：

- 偏 H2 兼容语法
- 适合默认启动模式
- 不适合作为 MySQL 生产建表脚本直接使用

### 2. `sql/schema.sql`

用途：

- 作为 MySQL 8.x 的建表脚本

特点：

- 使用更贴近 MySQL 的语法
- 包含索引、时间精度、`ON UPDATE CURRENT_TIMESTAMP` 等
- 需要手动执行

## 二、默认模式如何使用

如果你不启用 `mysql` profile，Spring Boot 会自动执行：

```text
classpath:sql/schema.sql
```

你不需要手动导入。

## 三、MySQL 模式如何使用

### 1. 创建数据库

```sql
CREATE DATABASE cpw DEFAULT CHARACTER SET utf8mb4;
```

### 2. 执行建表

```bash
mysql -uroot -p cpw < sql/schema.sql
```

### 3. 可选执行演示数据

```bash
mysql -uroot -p cpw < sql/seed_demo.sql
```

### 4. 再启动服务

```bash
SPRING_PROFILES_ACTIVE=mysql
```

## 四、为什么 MySQL 不自动执行 schema

因为启用 `mysql` profile 后配置是：

- `spring.sql.init.mode=never`
- `spring.jpa.hibernate.ddl-auto=validate`

设计目的很明确：

- 不让服务在真实数据库上悄悄建表或改表
- 让数据库结构成为显式操作
- 启动时只做结构校验

## 五、演示数据脚本的作用

`sql/seed_demo.sql` 主要用来准备：

- 列表页数据
- 统计页数据
- 快照数据

这样更方便做：

- 联调
- 压测
- 监控展示

## 六、什么时候看 H2 脚本，什么时候看 MySQL 脚本

### 看 H2 脚本的场景

- 默认模式启动失败
- 持久化测试依赖的结构不对
- 想确认内存模式下建了哪些表

### 看 MySQL 脚本的场景

- `mysql` profile 启动校验失败
- 想手动建表
- 想核对线上或测试库结构

## 七、和 JPA 的关系

当前项目的策略不是让 JPA 自动建表，而是：

- 用 SQL 脚本定义表结构
- 用 JPA 映射校验结构是否一致

这样做有几个好处：

- 表结构更透明
- 索引和约束更可控
- 更适合讲数据库设计

## 八、常见问题

### 1. 启用 MySQL 后启动报列不存在

说明：

- 你的数据库结构与当前 JPA 实体不一致

优先检查：

- 是否执行的是最新 `sql/schema.sql`
- 是否有旧表没清理干净

### 2. 启动时 H2 可以跑，MySQL 不行

说明：

- H2 脚本和 MySQL 脚本职责不同
- H2 只适合默认演示，不代表 MySQL 已完成初始化

### 3. 为什么项目没有 Flyway

当前项目还处在演示和架构表达阶段，尚未引入正式迁移工具。后续如果进一步产品化，建议优先补 Flyway。
