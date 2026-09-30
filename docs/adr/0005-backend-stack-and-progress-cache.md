# ADR-0005 MyBatis、Flyway 与 Redis 进度缓存方案

- 状态：已接受（MyBatis、Flyway 与 Redis 最新进度缓存选型）；跨实例通知、缓存参数与恢复契约仍待评审，不构成测试或实现授权
- 日期：2026-09-22
- 适用范围：MyBatis、Flyway 与 Redis 最新进度缓存；不改变 MySQL 事实源、任务创建 Outbox、[`ADR-0004`](0004-rabbitmq-task-queue.md) 的 RabbitMQ 命令队列及 SSE-only 产品要求。

## 背景

后端选用 Spring MVC、MyBatis 动态 SQL、Flyway schema 演进、RabbitMQ 消息队列、Redis 进度缓存、MySQL 事实源和 SSE 广播。Redis 进度缓存不是可回放的消息流。

## 决策与未决

- Spring MVC 承担 HTTP 接口，MyBatis 负责数据库访问，Flyway 管理版本化 MySQL schema。具体查询、分页和数据模型留待功能规格确定；动态 SQL 的列名与排序字段必须来自白名单，值必须参数化。
- MySQL 保存任务状态、进度、活动版本、文件元数据和错误摘要的权威值。Worker 先在 MySQL 事务中提交可观察变化，再尽力更新带版本的 Redis 最新进度缓存。在线 SSE 后端实例收到活跃任务的进度通知后，优先按任务 ID 和版本读取该缓存以取得最新进度；缓存缺失、过期或版本落后时从 MySQL 读取并按规则重建。缓存写入失败不回滚 MySQL，也不阻止通知继续走回源路径。
- Redis 只缓存最新进度，不承担任务命令队列、持久事件日志或幂等结果的唯一存储。普通订单和任务列表不默认缓存。
- SSE 在任务页活跃时向前端广播，首次连接和异常断线后用 MySQL 快照恢复；终态、下载资格及空闲关闭必须由 MySQL 核对，不能由进度缓存裁决。缓存不提供可回放游标，也不设置固定轮询；空闲事件的名称与时序留待功能契约确定。
- **未决**：Worker 提交后如何可靠通知每个 SSE 后端实例、在线漏通知如何补偿、进度 Outbox 是否保留及其发布目标、事件 ID 与重连语义、Redis TTL/容量/版本比较。技术选型确认不等于这些运行契约已获批准；不得自行指定 RabbitMQ 广播拓扑或以缓存更新替代可靠通知。

## 影响与回退

技术选型同步至 [`backend.md`](../rules/backend.md)、[`testing.md`](../rules/testing.md)、[`build.md`](../rules/build.md)、[`container.md`](../rules/container.md) 和[验收基线](../product/acceptance-criteria.md)。项目尚无实现和待迁移的进度数据；实施后更换进度通道时须先核对 MySQL 状态与在途通知，再停用旧路径。

在未决通知机制有可测试契约并获得功能规格审批前，不能把缓存与 SSE 流程作为已实施或已验收能力。
