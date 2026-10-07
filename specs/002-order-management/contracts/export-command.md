# RabbitMQ 命令与投递契约（草稿 v1）

关联 [模型](../data-model.md)、[流程计划](../plan.md)、[Worker](worker-boundary.md)。已接受原则依据 [ADR-0004](../../../docs/adr/0004-rabbitmq-task-queue.md)、[ADR-0006](../../../docs/adr/0006-broker-owned-pending-delivery.md)、[ADR-0007](../../../docs/adr/0007-outbox-dispatch-retry.md)。以下字段/拓扑/参数已按负责人本次要求定稿；整体功能规格仍待单独审批。

## 消息

JSON 只含 eventId:UUID、taskId:UUID、type:`EXPORT_REQUESTED`、schemaVersion:1、traceId:安全关联字符串。eventId 在所有补投中固定，messageId=eventId；每次 publish correlation 包含新 leaseToken，与稳定事件身份区分。不携带客户资料、范围、有序字段、幂等键或文件路径。持久 delivery mode，contentType=application/json。

已定拓扑：vhost `/export-flow`，durable direct exchange `export-flow.commands`，唯一目标 durable classic queue `export-flow.export.requested.v1`，routing key `export.requested.v1`；禁止 autoDelete、exclusive、message TTL、queue expiry、drop-head。容量上限如启用只能 reject-publish，并验证 publisher 可收到失败。实际 RabbitMQ 3.13、有效 policy、operator policy、绑定以及无其他错误目标须启动与运行核对；management 读取权限不足也不能假装合规。拓扑设计已批准，具体资源尚未创建；按开发门禁准备隔离资源，不修改示例资源。

## 发布成功证据

publishAck=true、mandatory 未 return、目标唯一绑定经核验且目标队列有效保留策略合规，四项组合才形成正确路由与保留证明。单独 confirm ACK 可能只有 exchange 接收，不能判 SENT。confirm/return 回调在同一次 correlation 内归并，按 Spring AMQP 与 broker 锁定版本验证回调顺序，不能通过随意 sleep 猜测“没有 return”。确认超时、连接中断、策略未知均不标 SENT。

领取事务先锁 task 再锁 outbox，预留尝试和 crashRetryAt，确认提交后再发消息。开始发布前核对当前租约、未关闭及剩余窗口；无需持有行锁等待 broker。确认后单独短事务条件标记 SENT；确认已获但标记异常，当前凭据仍有效时先回读／重试标记，不立即重发。结果不明交接不能清除历史。

## 扫描与错误出口

常规扫描、租约、退避使用[决策记录](../decision-record.md)的已定值；十分钟 deadline 与五秒独立到期扫描使用已接受固定值。单条序列化／版本／路由错误记录诊断后继续其他记录；共享 MQ 连接故障有界停批退避，数据库故障停止写状态，恢复先扫到期。SENT/CLOSED 不重投；已 RUNNING 或终态未 SENT 的原命令核对后关闭，不重置任务。

目标队列策略核验失败禁止成功标记并告警；不能仅凭队列名称就相信已可靠入队。停止 dispatcher 后不删除队列、重置 deadline、批量释放无法确认的在途领取。已确认命令可等待消费，排队超过窗口也不失败；本期演示角色启用真实消费者。

## 消费确认与未知消息

手动 ACK 仅在 MySQL 终态已提交或权威证明无需处理时执行，不因收到消息、查到 RUNNING 或文件生成函数返回就 ACK。通道或 DB 不可读保持未确认，避免错误吞掉任务。重复 PENDING 竞争 MySQL 执行权；重复 RUNNING 不启动新执行，是否可安全 ACK 需权威确认不能仅靠状态名称。

畸形载荷、未知版本、任务不存在时，已决定停止受影响消费槽、保留原未确认消息、受控关闭通道并告警；不ACK、不自动死信、不立即循环requeue，人工修复/受控处置后恢复消费。单消费者可能停止全部消费，接受此保留命令的取舍。见[ADR-0009](../../../docs/adr/0009-engineering-runtime-and-progress-notification.md#命令保留与异常消息)；TC-223验证不确认、不生成及停止槽。

## 本期真实消费与恢复参数

本次真实Excel纳入规格，consumer concurrency/prefetch、claim监测、执行lease/heartbeat、attempt最大期限、恢复预算/退避及broker ACK timeout见[Excel方案](../excel-design.md#本期具体建议参数)，均已按负责人授权定稿，仍需真实版本行为验证。异常恢复只能由原未确认命令重交付触发，消息身份不变；消费者能力检查不再依赖空函数。未知消息保留原消息并停止消费槽/受控关通道/告警，人工修复后恢复；不得无限立即requeue或自动ACK。
