# 技术调查：列表、可靠通知与安全暂停

**版本**：0.3　**日期**：2026-10-17　**状态**：规格0.3及测试阶段已获负责人批准，实施与适用验证已完成；保留调查及测试先行历史。
关联：[规格](spec.md)、[计划](plan.md)、[任务](tasks.md)、[暂停协议](contracts/execution-control.md)。


审批依据：负责人先以“可以按照当前规格开始了”批准规格0.3，随后对测试材料及六类缺口处理方案回复“继续”，批准测试阶段并授权本期生产实施。范围与日期见[审批记录](tasks.md#审批记录)；项目级效率规则草案仍独立待批。

## 规格准备时核对事实（历史基线）

| 事实 | 代码／文档依据 | 影响 |
| --- | --- | --- |
| 任务页已有路由，仍是占位 | frontend/src/features/export-tasks/pages/task-management-page.tsx | 原路由升级，不增加业务页 |
| 任务API只有创建与下载 | ExportTaskController.java | 新增列表/暂停/SSE接口，不增详情 |
| 任务当前四态、命令task/type唯一 | V2__create_export_tasks_and_outbox.sql | 新状态需新迁移；本期保留原消息版本与唯一键 |
| 块登记同时更新实际计数与version | MyBatisCheckpointRepository.commitBlock | 原事务登记进度通知，不报告未提交缓冲 |
| guard当前只检查本地有效性，ACK晚于终态 | ExportCommandHandler、ExecutionSupervisor | 暂停需控制屏障及独立安全停止确认 |
| 可信块/READY和旧fence隔离已有基础 | [ADR-0008](../../docs/adr/0008-worker-execution-lease-and-recovery.md) | 暂停保留保护引用，禁把暂停变失败或自动恢复 |
| Redis starter尚未声明 | backend/pom.xml | 按既定通知方向添加Spring Boot BOM管理依赖 |

## 方案与状态

可靠通知复用[已接受ADR-0009](../../docs/adr/0009-engineering-runtime-and-progress-notification.md#后续sse与redis方向)，不采用轮询或仅进程内广播；其具体字段、快照桥接和迁移已随本规格批准并实施，实际结果见[验证记录](../../docs/validation/004-task-management/verification.md)。

暂停不采用“接口直接改PAUSED却放任旧线程写入”，推荐PAUSING控制版本屏障→安全停止回执→PAUSED；其消费确认边界由[已接受ADR](../../docs/adr/0010-task-pause-control-barrier.md)单独评审，不冒充既有架构批准。

负责人已明确仅列表/SSE/暂停。取消本期名称/删除/失败后用户手动重试/继续方案，不新增命令schemaVersion=2、runGeneration或多代Outbox唯一键。暂停后的恢复需后续规格，不暗中通过异常接管重新执行PAUSED任务。

## 验证取舍

复用已有真实MySQL/RabbitMQ/Redis隔离环境与少量只读订单；关键事务、协议和文件仍需功能证据。本期没有连接中间件或新建夹具。性能、超时、延时与租约到期等计时用例按负责人指令不执行，普通并发用屏障，不用等待时长作为断言。具体排除与恢复条件见[任务](tasks.md#验证排除与覆盖缺口)。

## Review复核

现有handler的guard总闸、监督器双重close、控制字段映射、READ COMMITTED及尚无Redis测试封装等事实已进一步核对，问题与修正见[review](review.md)。不将静态源码调查写成运行验证；发送队列上限256已随规格接受，既有时间参数仍保留而免验。

## 0.3装配修复设计依据

现有ExportDataCollectionService.collect在validatePrefix判坏后以空可信前缀startGeneration，可能全量重建；PoiExportFileGenerator对装配数据异常返回KNOWN_FAILURE，其他I/O可能UNKNOWN，均不等同本次自动局部修复。DataBlockCodec.Reader在EOF核验大小／SHA／行数，不能仅exists判可信。当前先收集全部块再装配，本次保留该顺序，无新增运行巡检或逐SSE读盘。

V4块登记只有(task,dataGeneration,sequence)唯一键和文件摘要，没有独立分段恢复基线。直接在同代次新增替换会冲突，仅保留连续前缀又会丢弃后方健康块。候选方案V6分段清单+唯一当前选择、不可变区间内容摘要、替换历史，以及当前可信计数独立于高水位。存量补齐必须读盘验证，清单与块同时缺失不得伪造恢复依据。只读来源不保证外部不变，重读摘要不符明确失败。

这些是基于源码与既有ADR的设计调查，没有执行运行验证；方案与预算见[已接受ADR](../../docs/adr/0011-selective-block-repair-and-progress.md)、[修复契约](contracts/assembly-repair.md)和[任务](tasks.md)，规格及实施已获审批。
