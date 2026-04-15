# Observability（Actuator / Prometheus / Logging）

本项目提供可直接展示给面试官的基础运维资产：
- Actuator 健康检查与指标暴露（Prometheus）
- `ops` / `loadtest` profile 的运行配置样例
- Prometheus scrape 配置样例与 Grafana dashboard 模板
- 统一日志格式（logback）

## 一键开启（推荐）

建议把 actuator 放到独立端口，避免业务端口暴露过多运维信息：

```bash
SPRING_PROFILES_ACTIVE=ops,mysql,redis
SERVER_PORT=8080
MANAGEMENT_PORT=8081
```

业务端口：
- `http://127.0.0.1:8080`

运维端口：
- `http://127.0.0.1:8081/actuator/health/readiness`
- `http://127.0.0.1:8081/actuator/prometheus`

安全提示：
- `ops` profile 会额外暴露 `env/configprops/loggers` 等端点
- 仅建议在内网、或网关鉴权后开放

## Actuator 端点清单

默认（`application.yml`）：
- `GET /actuator/health`
- `GET /actuator/health/liveness`
- `GET /actuator/health/readiness`
- `GET /actuator/metrics`
- `GET /actuator/prometheus`

`ops`（`application-ops.yml`）：
- 额外暴露：`loggers/env/configprops/threaddump/httpexchanges`

## Prometheus

使用 `docs/observability/prometheus.yml` 作为样例：
- scrape `MANAGEMENT_PORT` 上的 `/actuator/prometheus`
- 建议按环境设置 `job_name` 与 `labels`

指标里你最常看的几个维度：
- HTTP 延迟：`http_server_requests_seconds_*`
- JVM 内存：`jvm_memory_used_bytes`
- GC：`jvm_gc_pause_seconds_*`
- Tomcat 线程：`tomcat_threads_*`
- 连接：`tomcat_connections_*`

## Grafana Dashboard

仓库提供了一个最小 dashboard 模板：
- `docs/observability/grafana-dashboard.json`

导入方式：
1. 在 Grafana 添加 Prometheus datasource
2. Import dashboard JSON
3. 选择对应 datasource

## Logging（logback）

日志格式配置在：
- `src/main/resources/logback-spring.xml`

特点：
- 单行、可 grep、可直接贴到简历/面试材料
- 如果上游引入 tracing/request-id，日志会自动带上 `traceId/spanId/requestId`（没有则显示 `-`）



