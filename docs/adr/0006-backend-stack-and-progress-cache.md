# ADR-0006 MyBatis、Flyway 与 Redis 进度缓存方案

- 状态：待项目负责人评审；仅记录目标方案，不构成测试或实现授权
- 日期：2026-09-22
- 拟取代范围：[`ADR-0004`](0004-redis-streams-and-backend-processing.md) 中 Redis progress Stream、Stream ID 回补及其 SSE 跨实例扇出决定；不改变 MySQL 事实源、任务创建 outbox、待评审的 [`ADR-0005`](0005-rabbitmq-task-queue.md) 命令队列目标和 SSE-only 产品要求。

## 背景

用户提供的后端学习参考图以 Spring MVC、MyBatis 动态 SQL、Flyway schema 演进、RabbitMQ 消息队列、Redis 进度缓存、MySQL 事实源和 SSE 广播为技术栈。现有 Redis 进度 Stream 方案与参考图的缓存职责不同，不能把 Redis 缓存直接称为可回放的消息流。

## 待评审方案

- Spring MVC 接收 DTO、参数校验和统一异常映射；MyBatis 明确表达筛选、Keyset 分页、条件状态更新和 outbox 查询；Flyway 管理版本化 MySQL schema。动态排序与筛选只能使用白名单及参数化值。
- MySQL 保存任务状态、进度、活动版本、文件元数据和错误摘要的权威值。Worker 先在 MySQL 事务中提交可观察变化，再尽力更新带版本的 Redis 最新进度缓存。缓存写入失败不回滚 MySQL；缓存缺失、过期或版本落后时从 MySQL 读取并按规则重建。
- Redis 只缓存最新进度，不承担任务命令队列、持久事件日志或幂等结果的唯一存储。普通订单和任务列表不默认缓存。
- SSE 在任务页活跃时向前端广播，首次连接和异常断线后用 MySQL 快照恢复，不依赖 Redis Stream ID，也不设置固定轮询。`stream-idle` 仍须以 MySQL 活跃任务集合与活动版本复核。
- **未决**：worker 提交后如何可靠通知每个 SSE 后端实例、在线漏通知如何补偿、进度 outbox 是否保留及其发布目标、事件 ID 与重连语义、Redis TTL/容量/版本比较。截图没有规定这些机制，评审前不得自行指定 RabbitMQ 广播拓扑或以缓存更新替代可靠通知。

## 影响与回退

通过本 ADR 后，须同步 [`backend.md`](../rules/backend.md)、[`testing.md`](../rules/testing.md)、[`build.md`](../rules/build.md)、[`container.md`](../rules/container.md)、[验收基线](../product/acceptance-criteria.md)中涉及进度 Stream 的验证口径，并明确取代旧 ADR 的范围。项目尚无实现和待迁移的进度数据，评审前修改方案无需数据迁移；实施后更换进度通道时须先核对 MySQL 状态与在途通知，再停用旧路径。

在本 ADR 获明确批准且未决通知机制有可测试契约之前，不能把拟议缓存流程作为已实施或已验收能力。
