# ADR-0005 RabbitMQ 导出任务队列

- 状态：待项目负责人评审；RabbitMQ 任务队列选型由用户明确提出，运行方案与参数尚未审批
- 日期：2026-09-22
- 拟取代范围：[`ADR-0004`](0004-redis-streams-and-backend-processing.md) 中 Redis Streams 承担任务命令队列、consumer group、`XACK` 和 `XAUTOCLAIM` 的决定；不改变 MySQL 事实源及事务 outbox，进度方案另见待评审的 [`ADR-0006`](0006-backend-stack-and-progress-cache.md)。

## 背景

用户明确要求消息队列使用 RabbitMQ。Redis 可以用于缓存或事件流，但不再作为导出任务命令的队列。任务创建与投递仍跨 MySQL 和消息中间件，直接双写的失败窗口没有消失。

## 决策

- MySQL 仍保存任务、幂等记录、范围快照和命令 outbox。创建/重试时这些记录在同一事务提交，HTTP 不等待文件生成。
- outbox dispatcher 将未发布的任务命令发送至 RabbitMQ。只有收到 broker 的发布确认，并确认消息已正确路由后，才能条件更新 outbox 为已发布；失败保留 outbox 并有界重试。具体 exchange、queue、routing key、持久化和发布确认配置待审批。
- Worker 从 RabbitMQ 消费任务命令，按任务 ID 读取 MySQL 权威快照，并以 MySQL 条件状态更新取得执行权。消息可能重复投递；文件生成、状态推进和重复消息处理必须幂等。
- 任务到达终态并成功提交，或 MySQL 权威状态证明消息无需处理后，才手动确认消费。处理进程中断或连接断开可能导致重新投递；不能把消息确认等同业务成功。重试、租约、尝试上限、死信和在途任务对账策略待审批，不预设具体数值。
- RabbitMQ 消息只包含稳定事件 ID、任务 ID、类型、版本及必要追踪信息，不携带客户信息、完整筛选、幂等键或文件路径。
- RabbitMQ 当前只明确用于导出任务命令。进度缓存和 SSE 广播的目标方案见 [`ADR-0006`](0006-backend-stack-and-progress-cache.md)，跨实例通知机制尚未决定，不默认让工作队列分摊进度通知。
- 方案获批后，Docker Compose 和隔离集成测试须纳入 RabbitMQ；命令队列故障与 Redis 缓存故障分别验证。RabbitMQ 的版本、镜像、健康检查和数据持久化配置在初始化前确认。

## 后果与待定边界

拟议架构有 RabbitMQ 任务队列和 Redis 进度缓存两个基础设施。队列与缓存职责不同；进度广播、断线回补尚须评审，不得仅删除原 Stream 而遗漏实时通知能力。

MySQL 提交后、RabbitMQ 发布前由 outbox 补投；发布确认后、outbox 标记前可能重复发布。RabbitMQ 丢失已确认而尚未完成的命令时，仅靠未发布 outbox 不足以恢复，必须设计与 MySQL 活跃任务状态的对账或其他可验证的恢复路径。

## 评审与回退

当前尚无实现和持久化队列数据，评审前可修订选型及规则而无需数据迁移。实现后若需更换队列，必须先停止新任务投递与消费、核对 MySQL 中未完成任务及 outbox、明确在途消息处置，再切换拓扑；不能直接删除 RabbitMQ 队列或把未完成任务视为失败。

关联：[后端规则](../rules/backend.md)、[容器规则](../rules/container.md)、[测试规则](../rules/testing.md)、[数据流学习稿](../../DATA-FLOW.md)。
