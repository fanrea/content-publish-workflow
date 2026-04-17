# 可观测性说明

项目已经具备一套适合本地演示、压测观察和基本排障的可观测性能力。虽然它还不是完整生产级监控平台，但骨架已经齐全。

## 一、当前已接入能力

- Spring Boot Actuator
- Micrometer
- Prometheus 指标暴露
- Logback 日志配置
- 可选独立管理端口
- Grafana 面板模板

## 二、默认可用端点

默认可用的 Actuator 端点：

- `/actuator/health`
- `/actuator/health/liveness`
- `/actuator/health/readiness`
- `/actuator/metrics`
- `/actuator/prometheus`

## 三、`ops` profile 打开后会增加什么

启用 `ops` profile 后，系统会进一步暴露：

- `loggers`
- `env`
- `configprops`
- `threaddump`
- `httpexchanges`

同时建议通过：

- `MANAGEMENT_PORT`

把管理端口与业务端口拆开。

## 四、推荐启动方式

```bash
SPRING_PROFILES_ACTIVE=ops,mysql,redis
SERVER_PORT=8080
MANAGEMENT_PORT=8081
```

这样通常会形成：

- 业务端口：`8080`
- 管理端口：`8081`

## 五、readiness 与 liveness 的意义

### 1. liveness

用于回答：

“进程还活着吗？”

### 2. readiness

用于回答：

“服务现在可以接流量吗？”

默认 readiness 检查：

- `ping`
- `db`

启用 `redis` 后还会检查：

- `redis`

## 六、Prometheus 指标说明

项目已经暴露标准 Prometheus 指标，常用观察维度包括：

### 1. HTTP 请求

- 请求总量
- 延迟
- 分位数
- 错误率

### 2. JVM

- 堆内存
- 非堆内存
- GC 暂停
- 线程

### 3. Tomcat

- 当前线程数
- 最大线程数
- 连接数

## 七、Grafana 资产

仓库内已有现成资产：

- `docs/observability/prometheus.yml`
- `docs/observability/grafana-dashboard.json`

可以直接用于：

- 本地 Prometheus 抓取
- 导入 Grafana 面板

## 八、日志定位

当前日志配置文件位于：

- `src/main/resources/logback-spring.xml`

日志主要用于配合：

- `traceId`
- `requestId`
- 审计日志

一起完成排障。

建议排问题时不要只看控制台文本日志，也要同时查：

- 发布日志接口
- outbox 状态
- 任务表状态

## 九、适合监控的业务场景

### 1. 发布慢

重点看：

- 发布接口延迟
- 任务执行耗时
- 数据库延迟

### 2. 发布卡住

重点看：

- `PUBLISHING` 状态持续时间
- 是否有任务卡在 `RUNNING`
- outbox 是否卡在 `FAILED` 或 `DEAD`

### 3. 恢复频繁

重点看：

- 失败任务数量
- 死信 outbox 数量
- 哪类任务最容易失败

## 十、后续可继续增强的方向

- 增加业务自定义指标
- 增加任务失败率与恢复次数指标
- 增加 outbox 堆积指标
- 增加缓存命中率指标
- 对接真正的链路追踪系统
