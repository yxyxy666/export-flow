# 数据模型与恢复生命周期

关联：[需求](spec.md)、[计划](plan.md)、[控制及文件契约](contracts.md)、[决策](../../docs/adr/0012-user-resume-and-live-progress.md)。

## 新增持久实体

V7仅增加`task_resume_operations`，InnoDB及既有排序规则：

| 字段 | 类型／约束 | 用途 |
| --- | --- | --- |
| operation_id | CHAR(36)，二进制排序，主键 | 同继续操作唯一；与暂停回执跨动作检查 |
| task_id | CHAR(36)，非空，外键export_tasks，RESTRICT | 原任务身份与保护引用 |
| request_hash | BINARY(32)，非空 | 原任务ID和控制前置版本的固定长度前缀SHA256 |
| expected_control_version | BIGINT，非空且非负 | 用户确认时冻结的控制前置条件 |
| result_json | JSON，非空 | 原提交结果留证；重放另读当前任务摘要 |
| created_at | DATETIME(3)，非空 | 数据库UTC提交时刻 |

不重写V1至V6，不改原暂停回执编码。共用activity锁串行化跨动作ID检查，再由各表唯一约束裁决同动作重放；回执和状态/命令/进度通知同事务，不能因插入失败留下半恢复任务。

## 既有字段行为扩展

- export_tasks：仅PAUSED→PENDING，control_version与version加一，清暂停时刻，phase先CHECKING；trusted_processed_count不凭点击继续递增。任务编号、配置、范围、created_at和修复预算保留。
- command_outbox：原event_id/type/schema/attempt_no保留；CLOSED/USER_PAUSED重开READY，清租约/token、sent/closed元数据；投递期限以本次显式继续重新建立。迟到旧token CAS失败。
- task_execution：不存在时首次插入，已存在时owner更新、fence加一、当前controlVersion及本次执行窗口更新；recovery_count和progress_version保留。旧owner/fence/control写入口拒绝。
- export_checkpoints：保留代、版本、收集前缀数量和末游标；部分恢复不能把未收集尾部当损坏。集合被替换时用既有segment.active_block_id选中结果，不全量换代。
- export_data_segments：独立sequence、前后游标、行数、配置、formatVersion、contentChecksum为重建依据；不能拿孤立磁盘文件反推可信基线。当前selected block登记缺失时仍可按独立基线重读来源。
- export_task_repairs/export_repair_segments：沿用repairEpoch及三轮持久预算；部分收集也允许建立选择修复计划，同轮替换CAS且可信行数只增加一次，暂停再继续不重置。

## 状态及文件责任

用户暂停后数据块和登记受保护；继续重新认领、核验→必要的REBUILDING→COLLECTING或完整CHECKING→ASSEMBLING→校验/READY→采用/SUCCEEDED。异常且无独立依据明确FAILED；数据库结果不明保留UNKNOWN供原消息恢复，不创建虚假记录。

数据收集服务写不可变块并登记，文件在既有file-root任务及代目录，登记提交后才计可信量；未登记文件不计进度且不提升数据库断点。部分核验取健康段行数，允许进度下降。SXSSF临时工作文件由每次工作簿新建和关闭清理，不持久续写；健康数据块用于重新装配。成功产物由原文件存储发布、数据库采用后才可下载。原保护文件清理由既有存储/垃圾回收边界管理，本功能不删除受保护引用；详见[生命周期契约](contracts.md#文件及恢复)。
