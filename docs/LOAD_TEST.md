# 压测说明

这份文档说明项目当前有哪些压测资产、适合压哪些接口，以及压测结果应该怎么解读。

## 一、压测目标

项目做压测的目的不是追求一个漂亮的 QPS 数字，而是验证下面几件事：

- 列表和统计接口在读多场景下是否稳定
- 写接口在工作流推进场景下是否能保持正确行为
- Redis、MySQL、Tomcat 参数是否明显影响吞吐和延迟
- 指标和日志是否足够支撑瓶颈定位

## 二、压测准备

### 1. 推荐环境

建议使用：

- MySQL
- `loadtest` profile
- `ops` profile

推荐启动方式：

```bash
SPRING_PROFILES_ACTIVE=mysql,loadtest,ops
SERVER_PORT=8080
MANAGEMENT_PORT=8081
```

### 2. 初始化数据库

先建表：

```bash
mysql -uroot -p cpw < sql/schema.sql
```

如需准备较稳定的列表页数据，再导入演示数据：

```bash
mysql -uroot -p cpw < sql/seed_demo.sql
```

## 三、现有压测脚本

脚本目录：

- `docs/loadtest/k6_read.js`
- `docs/loadtest/k6_write.js`

### 1. 读压测脚本

`k6_read.js` 主要覆盖：

- 草稿分页接口
- 草稿状态统计接口
- 草稿摘要接口

适合验证：

- 列表页性能
- 状态统计性能
- 缓存收益

### 2. 写压测脚本

`k6_write.js` 主要覆盖：

- 创建草稿
- 更新草稿
- 提交审核
- 审核通过
- 发起发布

适合验证：

- 工作流写链路的稳定性
- 发布主事务吞吐
- 并发写入时的基本行为

## 四、运行方式

### 1. 读压测

```bash
k6 run -e BASE_URL=http://127.0.0.1:8080 docs/loadtest/k6_read.js
```

### 2. 写压测

```bash
k6 run -e BASE_URL=http://127.0.0.1:8080 docs/loadtest/k6_write.js
```

## 五、压测时重点观察什么

### 1. HTTP 指标

重点看：

- 吞吐
- 平均延迟
- P95
- P99
- 错误率

### 2. JVM 指标

重点看：

- 堆内存使用
- GC 停顿
- 线程数

### 3. Tomcat 指标

重点看：

- 当前线程数
- 最大线程数
- 连接数

### 4. 数据库表现

重点关注：

- 列表接口是否有效利用索引
- 状态统计是否成为热点
- 写压测时事务提交延迟是否明显升高

## 六、推荐配合查看的监控

压测时建议同时打开：

- `/actuator/prometheus`
- Grafana 面板

相关资产：

- `docs/OBSERVABILITY.md`
- `docs/observability/prometheus.yml`
- `docs/observability/grafana-dashboard.json`

## 七、如何解读结果

### 1. 读压测延迟高

优先排查：

- MySQL 是否用了合适索引
- 是否启用 Redis
- 分页是否带了 `searchInBody=true`

### 2. 写压测延迟高

优先排查：

- 发布接口是否生成过多任务
- 审计写入是否增多
- 事务是否被数据库 I/O 卡住

### 3. 错误率高

优先排查：

- 权限请求头是否传对
- 数据状态是否冲突
- 压测脚本是否对同一草稿反复并发推进状态

## 八、压测结论应该怎么写

建议不要只写“QPS=多少”，而是至少包含：

- 哪些接口参与压测
- 压测环境是什么
- 数据规模大概多少
- 主要指标结果
- 观察到的瓶颈是什么
- 下一步优化建议是什么

这样压测才有工程价值。
