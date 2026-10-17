# ADR-0010 用户暂停控制屏障与持久停止确认

2026-10-17增量：[ADR-0012](0012-user-resume-and-live-progress.md)已接受显式用户继续，扩展本记录的封存解除边界；以下保留004原决策背景。

- 状态：已接受（2026-10-17随004规格0.3批准）；测试阶段与生产实施已获负责人明确批准，见004任务审批记录
- 日期：2026-10-14
- 范围：仅任务列表、SSE和暂停；显式继续及增删改不纳入
- 关联：[规格](../../specs/004-task-management/spec.md)、[计划](../../specs/004-task-management/plan.md)、[任务](../../specs/004-task-management/tasks.md)、[执行协议](../../specs/004-task-management/contracts/execution-control.md)、[既有执行ADR](0008-worker-execution-lease-and-recovery.md)、[通知ADR](0009-engineering-runtime-and-progress-notification.md)

## 问题与决策

既有Worker四态且只有终态才正常ACK；直接改PAUSED无法证明旧生成线程停止。新增PAUSING/PAUSED和controlVersion，所有权威登记受控制屏障约束。RUNNING先请求暂停，Worker安全停止后持久确认PAUSED并推进fence；PENDING未认领可直接暂停并封存原投递权。

消费确认新增明确边界：只有PAUSED及原命令封存已提交可核对才ACK，暂停不是生成成功。原命令、任务、断点与受保护文件永久保留；PAUSING未知状态不确认。Worker崩溃只做停止对账，不重新生成PAUSING/PAUSED。完成与暂停按数据库提交顺序裁决。

暂停请求使用独立expectedControlVersion，避免进度version的频繁变化导致合法暂停被连续拒绝。重放operationId先读取持久回执，后续控制变化不重新执行旧操作。保留schemaVersion=1、原task/type唯一键；本期没有继续命令协议。

通知继续沿用已接受独立进度Outbox和每实例持久队列，所有可观察事务加入统一活动版本锁。具体字段、锁序与函数见[模型](../../specs/004-task-management/data-model.md)和[函数清单](../../specs/004-task-management/function-design.md)。

2026-10-14审查补充：停止回执必须先于普通guard权限总闸处理，监督器USER_PAUSE保留delivery等待资源关闭；启动消费前及原监督周期调用只停止不生成的暂停对账。停止确认校验身份与checkpoint，不以旧lease尚有效作为前提；无回执的崩溃对账必须读原lease_until，不能把控制失效误作线程退出。READ COMMITTED保持已接受基线，列表用单条SQL快照。SSE idle后的最终快照、实际发送后通知ACK及有界发送详见[审查记录](../../specs/004-task-management/review.md)。发送队列上限256已于2026-10-17随0.3规格接受；测试先行材料仍须单独审批。

## 影响与回退

涉及状态约束、认领/续租/块/READY/完成控制检查、原命令封存及暂停ACK。新ADR必须明确接受才可约束实施；规格和测试阶段仍分别审批。性能及计时部分按本轮排除清单，不作为虚假通过证据。

升级前停止新受理与消费，保留现有命令及业务数据，执行兼容迁移后启动支持六态的版本；原v1消息仍可读取。通过功能开关关闭新暂停入口可以停止新增控制，但已有PAUSING必须由兼容版本收敛。出现PAUSING/PAUSED后不能直接退回只识别四态的旧二进制；先部署支持新状态的兼容版本，保持原记录，不清表/清队列、不重置fence、不降级迁移。继续功能未来另行设计，不能用回退把暂停任务改回PENDING。

## 装配修复的控制边界补充（2026-10-17）

本决策只裁决暂停；004本期新增装配自动修复由已接受的[ADR-0011](0011-selective-block-repair-and-progress.md)单独裁决。其清单封存、修复计划、替换块选择、可信进度和产物登记同样受controlVersion/owner/fence约束，暂停先提交则不得登记；资源关闭后的停止回执仍优先于普通guard。PAUSED保留修复计划而不自动重建，不借修复绕过用户继续边界。

## 接受记录（2026-10-17）

负责人在本会话明确“可以按照当前规格开始了”，接受当前004规格0.3及其关联决策。该记录取代上文拟定阶段的待审批状态，仅授权测试先行，不代替测试阶段材料评审及生产实施授权。见[审批记录](../../specs/004-task-management/tasks.md#审批记录)。

2026-10-17负责人随后对测试阶段材料及六类缺口处理方案回复“继续”，已批准测试阶段并授权本期生产实施；该后续记录不改变第一次审批仅进入测试先行的历史范围。实际绿灯及限制见[验证记录](../validation/004-task-management/verification.md)。
