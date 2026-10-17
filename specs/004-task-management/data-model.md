# 数据模型：任务列表、暂停控制与通知

**版本**：0.3　**状态**：规格0.3及测试阶段已于2026-10-17获负责人批准，当前生产实施；保留测试先行证据。
关联：[规格](spec.md)、[计划](plan.md)、[任务](tasks.md)、[HTTP/SSE](contracts/http-sse.md)、[暂停协议](contracts/execution-control.md)。


审批依据：负责人先以“可以按照当前规格开始了”批准规格0.3，随后对测试材料及六类缺口处理方案回复“继续”，批准测试阶段并授权本期生产实施。范围与日期见[审批记录](tasks.md#审批记录)；项目级效率规则草案仍独立待批。

## 类型与字段

UUID在前端为string；Instant/UtcInstant为UTC ISO8601，显示香港时间。version、activityVersion、controlVersion、repairEpoch、fileSize和列表total在协议用十进制字符串，前端BigInt比较版本；计数最多200000，可用number。控制版本仅随控制变化递增，与频繁进度版本分开，避免进度刷新令暂停前置条件不断失效。

| 类型 | 字段与约束 |
| --- | --- |
| TaskStatus | PENDING/RUNNING/PAUSING/PAUSED/SUCCEEDED/FAILED；前三种活跃，后两种终态，PAUSED不自动恢复 |
| TaskQuery | keyword可选、trim后最多100字符；status合法可选；page默认1且为1..2147483647整数；pageSize默认20，可20/50/100；排序固定 |
| TaskListItem | taskId、taskNo、scopeType、totalCount、processedCount、progressPercent、status、fields有序、fileName、fileSize可空、createdAt、finishedAt可空、errorCode/errorSummary可空、version、controlVersion、allowedActions、executionPhase、repairState、repairEpoch、repairAttempt、repairReasonCode |
| TaskPage | items、page、pageSize、total、activityVersion、hasActiveTasks；活动信息来自全环境而非查询筛选 |
| TaskEvent | activityVersion、task:TaskListItem；不带客户原始数据、owner或物理路径 |
| StreamReady/StreamIdle | connectionId、activityVersion；不承诺历史事件回放 |
| PauseRequest | taskId:UUID、operationId:UUID、expectedControlVersion:非负long |
| PauseResult | 领域结果outcome=APPLIED/REPLAY/CONFLICT/UNKNOWN、task:TaskListItem可空，冲突带安全分类；200 DTO仅APPLIED/REPLAY且task必填，其他结果映射409/503安全错误体 |
| PauseActionState | IDLE/SUBMITTING/UNKNOWN/REJECTED/CONFIRMED，UNKNOWN保存冻结PauseRequest |
| ControlLease（设计别名） | Java保留ExecutionLease名称，追加controlVersion与兼容原五参数构造，历史身份使用0；fence隔离，原命令无新增代次，新控制字段不得丢失 |
| PauseProof | taskId、owner、fence、requestedControlVersion、lastCheckpointVersion；证明同一执行者已到安全停止边界 |
| PauseReconcileResult | kind=APPLIED/ALREADY_PAUSED/WAITING/UNKNOWN，task:TaskListItem可空；WAITING不伪造已暂停回执 |
| ControlSnapshot | status、controlVersion、owner/fence可空、leaseUntil可空、checkpointVersion、readOutcome=KNOWN/UNKNOWN |
| GenerationResult扩展 | 增加PAUSE_STOPPED及PauseProof、resourcesClosed:boolean；旧三参构造兼容默认false；只有已完成资源关闭可true，不归KNOWN_FAILURE，结果不明仍UNKNOWN |
| ProgressNotice | schemaVersion=1、taskId、version、activityVersion；Rabbit最小通知，无执行指令 |
| CacheEntry | TaskListItem和version；不低于通知版本才可采用，关键裁决仍读MySQL |

领域TaskListItem及TaskPage内部的版本/文件大小/total仍用long；十进制字符串只在协议编码时产生，数据库record、领域record与HTTP DTO不能混用。新ExportTask.controlVersion必须由ExportTaskRecord、ExportTaskPersistenceMapper及ExportTaskMapper.xml完整传递；旧构造兼容值0不提供绕过暂停屏障的权限。

PauseRequest请求摘要按UUID规范形式和expectedControlVersion的长度前缀编码生成SHA-256；taskId属于摘要，operationId是持久唯一键。暂停新操作事务遵循activity→task→原command→execution，同operationId跨任务竞争先由activity全局锁串行裁决并核对只读回执，唯一约束兜底；回执插入在业务状态更新后、通知登记前完成且同事务提交，不能吞异常后提交半个暂停。无修改的幂等重放不递增任务/活动版本。持久resultJson保存受理事实，HTTP REPLAY另读当前权威摘要。

## 迁移草稿

本期拟新增V5__task_list_pause_and_progress.sql，不改V1至V4，不生成该SQL文件。命令schemaVersion=1及原task/type唯一键保持，用户继续的代次协议仅预留。

| 表／变更 | 主要字段和约束 | 数据生命周期 |
| --- | --- | --- |
| export_tasks扩展 | control_version BIGINT默认0、pause_requested_at/paused_at DATETIME(3)可空；已有version统一表示可观察版本 | 任务和文件永久保留，不增加名称／删除字段 |
| 状态约束替换 | 六态；PENDING/RUNNING/PAUSING/PAUSED的finished_at为空；终态保持原文件／错误约束 | PAUSED禁止自动认领、下载和异常生成接管 |
| task_execution扩展 | control_version、pause_ack_version；保留owner/fence/lease与预算 | 暂停确认推进fence，旧身份不再写；不重置异常预算 |
| task_pause_operations新增 | operation_id主键、task_id外键、request_hash、expected_control_version、result_json、created_at | 与暂停状态同事务提交，原操作永久可找回；不存客户载荷 |
| task_activity新增 | id=1单行、version BIGINT非负；迁移初始化后不可缺失 | 序列化创建与空闲裁决，不使用墙上时间排序 |
| task_progress_outbox新增 | task_id主键、pending_version非负、activity_version、published_version默认-1（允许-1表示从未发布）、lease_owner/token/until、next_attempt_at | 兼容创建时task.version=0的首次通知；合并最新版本；confirm后CAS推进，不误删并发更新；终态已发布确认后可清理 |
| 列表索引 | (status,created_at,task_id)，沿用已有时间/ID索引 | 参数化LIKE；不以索引基准测量作本轮验收 |

原command_outbox保留记录。暂停等待任务或安全停止确认后，当代命令标CLOSED/USER_PAUSED，撤销投递lease；已发消息迟到时凭持久暂停封存证明ACK，不能把它当生成完成。PAUSING不得被投递到期扫描判为FAILED；PAUSED不能重新投递或开工。

## 候选状态迁移

| 原状态 | 事实 | 新状态 |
| --- | --- | --- |
| PENDING | 暂停先于认领事务 | PAUSED，封存原命令，保留范围 |
| RUNNING | 暂停先于完成事务 | PAUSING，controlVersion递增并建立登记屏障 |
| PAUSING | 同执行者安全停止证明或DB证明无有效执行并推进fence | PAUSED，不设finishedAt，保存可信断点 |
| RUNNING | 完成先提交 | SUCCEEDED/FAILED，暂停409，不覆盖终态 |
| PAUSED | 重复同操作、进程重启、原消息重交付 | 保持PAUSED；本期没有继续迁移 |

PAUSING崩溃对账沿用数据库租约与隔离原则，禁止扫描重新生成；租约到期路径设计保留、执行验证免验。

## 锁序与读取

可观察写事务统一顺序：task_activity → export_tasks → command_outbox → task_execution → checkpoint → repair → segment（sequence升序）→ artifact → progress_outbox。原有创建、认领、块提交、完成、投递失败及清理路径要审查并同步；不得持有task锁后再回锁activity。构建通知在原事务，实际网络广播在事务外。

列表沿用已接受READ COMMITTED，以一条非锁定SELECT读取总数、page、activityVersion与全环境hasActiveTasks，使用同一语句快照。以CTE组合匹配结果、总数与页数据，total/activity行LEFT JOIN页数据，空结果或越界页仍返回总数／活动元信息；不存在独立count/page多语句读取，也不更改全局或局部隔离级别。任务成员变化需要重新查当前页；纯进度直接按task.version合并。queryGeneration和已见版本避免旧HTTP返回覆盖新筛选或SSE结果。

## 选择性重建模型

具体算法和生命周期见[修复契约](contracts/assembly-repair.md)，架构为[待接受候选](../../docs/adr/0011-selective-block-repair-and-progress.md)。下列V6增量仅设计，不创建迁移。

| 类型／表 | 字段、类型及不变量 | 职责／生命周期 |
| --- | --- | --- |
| executionPhase | NOT_STARTED/COLLECTING/CHECKING/REBUILDING/ASSEMBLING/COMPLETE | 六态status内的阶段，不新增任务状态；暂停冻结阶段，终态COMPLETE |
| repairState | NONE/CHECKING/REBUILDING/PAUSING/PAUSED/FAILED | 正常初次装配校验也可CHECKING；发现损坏进入REBUILDING；重新装配保持REBUILDING提示直至成功，暂停按状态覆盖提示 |
| TaskListItem增量 | 上述两枚举；repairEpoch:非负long（协议十进制string）；repairAttempt:int 0..3；repairReasonCode:String可空（安全白名单） | HTTP、SSE、Redis、SQL投影和前端schema必须一致，无物理路径或原始客户内容 |
| export_tasks增量 | trusted_processed_count BIGINT默认0，CHECK 0..total_count；execution_phase、repair_state、repair_epoch、repair_attempt、repair_reason_code | 当前可信量投影为processedCount；旧processed_count与checkpoint.processed_high_water保留历史高水位；新计数不改原checkpoint CHECK |
| export_checkpoints增量 | manifest_sealed:boolean、manifest_version:long、segment_count:int、manifest_row_count:long、manifest_digest:CHAR64可空 | 封存摘要只覆盖不可变分段字段，不包含可变块选择；数量、总量与任务一致才能装配；有序序号无空洞无重叠 |
| export_data_segments新增 | PK(task_id,data_generation,sequence)；previous_cursor_json/last_cursor_json、row_count 1..1000、configuration_hash、format_version、content_checksum CHAR64不可变；active_block_id:UUID可空、selection_version:long、verified_repair_epoch:long、state=PENDING/VERIFIED/REBUILD_REQUIRED | 分段元数据首次与块提交同事务登记；active_block_id为逻辑引用，不向块表建删除级联，块登记损坏／丢失时仍保留独立恢复依据；所有选择读取强制匹配任务／代次／序号 |
| export_task_repairs新增 | PK(task_id,repair_epoch)；attempt_no、checkpoint_version、manifest_version、reason_code、state、created_at/finished_at；UNIQUE(task_id,attempt_no) | 轮次及计划跨重启保留，同任务累计候选最多3轮，不改控制版本，不重置接管预算 |
| export_repair_segments新增 | PK(task_id,repair_epoch,sequence)；old_block_id可空、replacement_block_id可空、expected_selection_version、state | 记录坏段和原子替换结果；同段幂等提交不能重复计数；与修复历史永久保留 |
| RepairSnapshot | taskId:UUID、dataGeneration/checkpointVersion/manifestVersion/repairEpoch:long、segments:List<SegmentRef>、manifestSealed:boolean | I/O前的完整读取身份；提交前CAS重验，不能凭旧检测结论覆盖新选择 |
| SegmentRef | sequence:int、previousCursor:OrderCursor可空、lastCursor:OrderCursor、rowCount:int、contentChecksum:String、selectedBlock:BlockRef可空、selectionVersion:long | 内容摘要针对规范原始订单行，不含会随blockId变化的header；摘要版本由formatVersion限定 |
| AssemblyAudit / RepairPlan | audit: snapshot、triggerIssue可空、healthySegments、damagedSegments、trustedRows:long、fatalReason可空；plan: taskId、epoch、attemptNo、damagedSegments | 全清单检查完才发布修复进度；I/O结果不直接授权登记 |

V6需移除旧块表UNIQUE(task_id,data_generation,sequence)，保留普通查询索引及block_id/storage_ref唯一性；分段表PK保证每位置唯一有效选择，旧块行不可覆盖。已封存数据的读取必须join active_block_id，不能沿旧SELECT全部同代次块的方式重复装配；未封存的收集事务仍按checkpoint版本与唯一分段位置防重。以上是兼容变更，禁止旧Worker混跑，不能只增加表而遗漏原查询。

正常块提交同时保存不可变分段、当前选择、checkpoint、可信计数及通知；完成收集封存manifest。替换提交不推进收集游标／verified_prefix_count，只按段选择CAS更新trusted_processed_count和历史高水位、repair记录、task.version/activityVersion及通知。READY复用也须核对当前manifest与全部有效选择；旧READY缺文件或损坏不直接采纳，不删除旧READY保护记录。

V6 schema迁移只初始化状态和标记未封存，不把旧元数据当已实体验证。存量非终态trusted_processed_count初始0、phase按状态初始化；后续有效执行者完整读盘后提交可信计数，完整链验证后补齐并封存。旧成功任务保留成功计数／产物状态。暂停中及已暂停不得为补齐而自动生成。清理仍扫描并保护全部旧块记录及READY/ADOPTED，不因选择替换丢保护。

分段逻辑引用不使用数据库级联删除；损坏旧登记保留，物理文件是否可清理由全部保护引用裁决，修复选择切换不授予清理权限。新字段状态组合以函数设计增量的初始化／暂停／终态映射为准。
