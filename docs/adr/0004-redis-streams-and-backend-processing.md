# ADR-0004 Redis Streams 与后端异步处理链路

- 状态：已接受；任务命令队列由待评审的 [`ADR-0005`](0005-rabbitmq-task-queue.md) 提议取代，进度 Stream 由待评审的 [`ADR-0006`](0006-backend-stack-and-progress-cache.md) 提议取代；替代方案获批前不得混合实施
- 日期：2026-09-21
- 取代范围：[`ADR-0003`](0003-application-stack-and-local-runtime.md) 中“不使用 Redis/消息中间件”和“worker 直接通过数据库锁领取任务”的部分

> 本文记录原完整 Redis Streams 决策。新技术栈尚在评审，任务队列和进度缓存的目标方案分别见 [`ADR-0005`](0005-rabbitmq-task-queue.md) 与 [`ADR-0006`](0006-backend-stack-and-progress-cache.md)；未决事项必须先完成评审，不能拼接新旧方案进入实现。

## 背景

订单导出需要可靠创建任务、异步生成文件、持续发布进度并通过 SSE 推送。项目决定本期引入 Redis，并保留完整的常规后端处理链路，包括 API、事务、持久化、可靠消息、worker、文件生成、实时事件、错误处理、恢复和测试。

直接在“写 MySQL 任务”和“发送 Redis 消息”之间做双写会产生部分成功：数据库提交但消息丢失，或消息已发送但数据库回滚。因此 Redis 不能替代 MySQL 的任务事实源，可靠投递必须有事务 outbox。

## 决策

### 数据职责

- MySQL 8.4 LTS 是订单、导出任务、幂等记录、范围快照、进度、文件元数据、错误摘要和 outbox 的唯一事实源。
- Redis 8 用于 Redis Streams 任务队列和进度事件流，不作为任务最终状态、幂等结果或文件元数据的唯一存储。
- 成功创建或重试任务时，在同一 MySQL 事务中写入任务、幂等记录和命令 outbox 事件；worker 提交可观察状态或进度时，在同一事务中写入进度 outbox 事件。

### 任务投递与消费

- outbox dispatcher 周期性读取未发布事件，按事件类型将任务命令或进度事件写入对应 Redis Stream；成功写入后以条件更新标记 outbox 已发布。
- worker 使用 Redis Streams consumer group 消费任务命令，投递语义为至少一次。
- 消费者开始处理前通过 MySQL 条件更新声明任务；重复消息或重复消费必须根据权威任务状态安全忽略。
- 成功或确定失败并持久化终态后才 `XACK`。消费者异常留下的 pending 消息通过有界租约和 `XAUTOCLAIM` 恢复。
- outbox dispatcher 和 worker 都必须有批次、并发、退避、租约、最大尝试次数和死信/人工诊断策略，具体参数在实现前审批。

### 进度事件与 SSE

- worker 每次提交可观察的任务状态或进度时，在同一 MySQL 事务中追加进度 outbox；dispatcher 再投递 Redis 进度 Stream，避免状态已提交但进度事件因进程崩溃永久丢失。
- 每个 SSE 后端实例使用独立游标读取进度 Stream 并向本实例连接扇出，不得让多个实例共享一个会分摊消息的 consumer group。SSE `id` 使用 Redis Stream entry ID，以支持断线续传；业务 `eventId` 继续用于幂等诊断，二者不得混淆。
- Redis 事件流按已批准的时间和长度上限裁剪；游标已淘汰时，客户端通过一次 MySQL 快照回补，不使用轮询。
- `stream-idle` 仍以 MySQL 中的活跃任务集合和活动版本为权威判断，不能只根据 Redis 当前没有消息推断空闲。

### 失败与降级

- Redis 暂时不可用时，已提交到 MySQL 的任务、命令 outbox 和进度 outbox 不丢失；dispatcher 恢复后继续投递。
- worker 或 Redis 重启后通过 consumer group pending entries 和 MySQL 状态恢复，不能从头无界重放。
- Redis 不可用期间 SSE 实时更新可能中断，前端按异常断线策略重连；恢复后按事件 ID 或快照回补。
- 不为普通订单/任务列表默认增加 Redis 缓存。只有形成可测量瓶颈并具备失效规则后，才能另行审批缓存。

### 本地运行与依赖

- Docker Compose 增加 Redis 服务和持久化卷；前端、后端、MySQL、Redis 都需要健康检查。
- 后端使用 Spring Data Redis 与 Lettuce。Redis 8 的具体 minor/patch 和镜像 digest 在项目初始化时锁定。
- 集成测试同时使用 MySQL 8.4 LTS 和 Redis 8 的隔离实例，验证 outbox、Streams、consumer group、恢复和 SSE 回补。

## 后果

该方案增加 Redis、outbox dispatcher、consumer group 和恢复流程的复杂度，但消除了数据库与消息双写窗口，并支持跨后端实例的任务分发和实时进度事件。Redis Streams 是至少一次投递，因此 worker 和状态更新必须保持幂等；不能把 `XACK` 当作业务成功事实。

MySQL 仍然保存永久任务记录和文件元数据，Redis Stream 可以有界裁剪。即使 Redis 数据丢失，只要 MySQL 和文件卷仍在，任务事实不会消失；未发布命令/进度 outbox 可以重新投递，已裁剪进度可以通过快照恢复。

## 关联

- [`backend.md`](../rules/backend.md)
- [`testing.md`](../rules/testing.md)
- [`container.md`](../rules/container.md)
- [`端到端数据流学习稿`](../../DATA-FLOW.md)（非功能规格）
- [`ADR-0005 RabbitMQ 导出任务队列`](0005-rabbitmq-task-queue.md)
- [`ADR-0006 后端栈与进度缓存方案`](0006-backend-stack-and-progress-cache.md)
