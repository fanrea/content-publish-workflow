# 测试说明

项目的测试不是只验证“接口能不能跑”，而是尽量把工作流规则、持久化契约和恢复行为做成可执行说明。

## 一、测试目标

当前测试主要覆盖三类问题：

- 工作流状态机是否正确
- 错误码是否稳定
- 持久化层是否和领域模型一致

## 二、应用层测试

应用层测试重点验证业务规则和状态推进。

典型测试关注点：

- 不同状态下能否执行提交审核、审核、发布、回滚、下线
- 审核驳回后是否正确记录驳回意见
- 发布是否产生快照和任务
- 回滚是否生成新发布版本，而不是覆盖旧版本
- 幂等发布是否复用已有命令
- 失败任务恢复后是否回到 `PENDING`

相关测试文件主要包括：

- `ContentWorkflowStateMachineTest`
- `ContentWorkflowErrorCodesTest`
- `ContentWorkflowHistoryTest`
- `PublishIdempotencyAndDiffTest`
- `ContentWorkflowAuditTrailTest`
- `WorkflowRecoveryServiceTest`
- `PublishTaskWorkerEventDispatchTest`

## 三、持久化测试

持久化测试重点验证：

- JPA 实体能否正确落库
- Repository 查询方法是否可用
- 关键约束与字段映射是否匹配

当前代表性测试文件：

- `PersistenceLayerTest`

这类测试通常运行在 `@DataJpaTest` 环境下，默认使用 H2。

## 四、权限与接口测试

接口相关测试重点验证：

- 请求头权限控制
- 受保护接口是否只允许正确角色访问
- 恢复接口的权限边界是否符合预期

相关文件包括：

- `ContentWorkflowAuthorizationTest`
- `WorkflowRecoveryControllerAuthorizationTest`

## 五、为什么这套测试有价值

因为工作流系统最怕的不是“接口报错”，而是：

- 状态流转悄悄被改坏
- 错误码不稳定导致前端判断失效
- 快照和版本语义被破坏
- 恢复能力只在 happy path 上可用

这些问题都更适合通过测试长期兜底。

## 六、运行方式

理论上使用 Maven 执行：

```bash
mvn test
```

如果本地没有安装 Maven，需要先准备 Maven 环境或补 Maven Wrapper。

## 七、后续建议补的测试

- MySQL profile 启动校验测试
- Redis 缓存命中与失效测试
- Outbox relay 与消费者联动测试
- XXL-Job handler 集成测试
- 更完整的恢复时间线测试
