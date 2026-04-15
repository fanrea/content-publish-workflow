# Load Test（压测与基准测试资产）

本项目的压测目标不是“跑一个漂亮数字”，而是形成一套可复用的压测资产，能向面试官展示你对：
- 基准测试方法
- 指标采集（Prometheus）
- 压测数据准备（SQL seed）
- 结果解读与瓶颈定位（延迟/RPS/错误率/线程/GC）

有完整闭环。

## 环境准备（推荐 MySQL）

1) 建表（MySQL 8.x）：

```bash
mysql -uroot -p cpw < sql/schema.sql
```

2) 可选：灌入演示数据（列表页压测更稳定）：

```bash
mysql -uroot -p cpw < sql/seed_demo.sql
```

## 启动服务（loadtest + ops）

建议启用 `loadtest` 调整线程/连接参数，同时启用 `ops` 暴露 Prometheus 直方图与更多运维端点：

```bash
SPRING_PROFILES_ACTIVE=loadtest,ops,mysql
SERVER_PORT=8080
MANAGEMENT_PORT=8081
```

压测时建议使用 `/actuator/prometheus` 观察：
- `http_server_requests_seconds_*`（吞吐/延迟）
- `jvm_*`（GC/内存）
- `tomcat_*`（线程/连接）

Prometheus/Grafana 资产见：
- `docs/OBSERVABILITY.md`
- `docs/observability/prometheus.yml`
- `docs/observability/grafana-dashboard.json`

## k6 脚本

脚本目录：
- `docs/loadtest/k6_read.js`：读压测（列表/统计/摘要）
- `docs/loadtest/k6_write.js`：写压测（创建/更新/提交审核/审核/发布）

运行示例：

```bash
# 读压测
k6 run -e BASE_URL=http://127.0.0.1:8080 docs/loadtest/k6_read.js

# 写压测（会写入数据，建议只跑短时间）
k6 run -e BASE_URL=http://127.0.0.1:8080 docs/loadtest/k6_write.js
```

说明：
- 写接口需要请求头 `X-Workflow-Role`，脚本已内置最小角色头（EDITOR/REVIEWER/OPERATOR）。
- 如果你把服务放在网关后面，可以在 k6 里追加 `X-Request-Id` 以便关联日志与指标。

## 结果解读建议（面试表达）

建议在压测记录里至少保留以下 4 个图：
- RPS（吞吐）
- P95/P99 延迟（尾延迟）
- 5xx 错误率
- JVM Heap/GC pause

并在结论里说明一次“定位与优化”的过程，例如：
- 哪个接口最慢（uri 维度）
- 是 DB、线程、连接还是 GC 先成为瓶颈
- 调整了哪些参数（Tomcat/Hikari/缓存策略/索引）以及指标变化



