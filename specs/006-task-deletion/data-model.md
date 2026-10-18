# 数据模型：统一删除控制、墓碑与清理

**状态**：草稿，随[`spec.md`](spec.md)、[`plan.md`](plan.md)和[`tasks.md`](tasks.md)一并评审。

## 任务删除字段

在清理完成后，`export_tasks`及任务业务子记录必须删除。清理进行期间临时增加以下字段；原有`status`继续表达导出执行状态，删除生命周期单独表达，避免把用户删除伪造为`FAILED`。

| 字段 | 类型 | 约束和含义 |
| --- | --- | --- |
| `deletion_state` | `VARCHAR(20)` | `NONE`、`REQUESTED`、`STOPPING`、`CLEANUP_PENDING`、`CLEANING`、`DELETED`；默认`NONE` |
| `deletion_mode` | `VARCHAR(20)` | `ACTIVE_STOP`或`TERMINAL_CLEANUP`；受理时写入，便于审计和恢复 |
| `deletion_operation_id` | `CHAR(36)` | 当前删除操作身份；与操作表关联 |
| `deletion_requested_at` | `DATETIME(3)` | 数据库时间，删除首次受理时间 |
| `deleted_at` | `DATETIME(3)` | 清理完成时间；写入独立技术墓碑，不写回已删除的任务根行 |
| `deletion_error_code` | `VARCHAR(64)` | 最近一次可安全重试的清理错误分类，可为空 |

任务根行进入`CLEANUP_PENDING/CLEANING`后仍只在清理事务内可见；清理完成后整行删除。正常任务查询不返回删除中的任务，内部消息核对改读独立技术墓碑。

## 独立技术墓碑

新增`task_deletion_tombstones`，不对`export_tasks`建立外键，以便任务业务记录硬删除后仍能裁决迟到消息：

| 字段 | 类型/约束 |
| --- | --- |
| `task_id` | 主键UUID；不建立到任务表的外键 |
| `task_no_hash` | 任务编号摘要；禁止保存客户载荷 |
| `operation_id` | 最近一次删除操作身份 |
| `deleted_at` | 数据库时间 |
| `expires_at` | 待负责人确认的墓碑过期时间 |
| `state` | `ACTIVE`或`EXPIRED` |

墓碑只用于迟到命令、旧操作和下载/控制入口拒绝；不保存字段配置、订单范围、文件名、文件路径、产物引用或可下载数据。墓碑过期策略必须先解决队列中迟到消息的最长生命周期，再允许物理删除墓碑。

## 删除操作表

新增`task_deletion_operations`。该表不对`export_tasks`建立外键，避免任务业务记录删除时阻塞清理；由`task_id`和`operation_id`关联独立墓碑：

| 字段 | 约束 |
| --- | --- |
| `operation_id` | 主键UUID |
| `task_id` | UUID逻辑关联，不建立到`export_tasks`的外键 |
| `request_hash` | 任务ID、预期版本和协议版本的SHA-256摘要 |
| `expected_version` | 用户操作时看到的任务摘要版本 |
| `mode` | `ACTIVE_STOP`或`TERMINAL_CLEANUP` |
| `state` | `ACCEPTED`、`STOPPING`、`CLEANUP_PENDING`、`CLEANING`、`COMPLETED`、`FAILED_RETRYABLE` |
| `result_json` | 最近一次安全回执；禁止写入物理路径和执行身份 |
| `created_at`、`updated_at` | 数据库时间 |

同一`operation_id`只能对应一个任务和一个请求摘要；相同操作重放返回已有回执，冲突重放返回`OPERATION_ID_REUSED`。

## 删除清理作业表

新增`task_deletion_jobs`，用于把数据库清理和文件清理从用户请求事务中分离；该表同样不对`export_tasks`建立外键：

| 字段 | 约束 |
| --- | --- |
| `job_id` | 主键UUID |
| `task_id` | UUID逻辑关联独立墓碑 |
| `operation_id` | 外键指向删除操作 |
| `phase` | `STOP_WAIT`、`DB_CLEANUP`、`FILE_CLEANUP`、`DONE`、`RETRYABLE_FAILURE` |
| `attempt_no` | 有界递增，不能因重启清零 |
| `next_attempt_at` | 数据库时间退避点 |
| `last_error_code` | 稳定安全错误分类 |
| `lease_owner`、`lease_until` | 清理Worker租约，避免重复清理 |
| `created_at`、`updated_at` | 数据库时间 |

## 删除后的引用规则

- `export_tasks`任务根行和所有任务业务子表必须在清理事务内按固定锁序删除；不能依赖数据库级级联删除。
- 删除操作、清理作业和技术墓碑与任务表解耦，避免技术记录的外键阻止业务任务硬删除。
- 物理文件删除必须先由清理作业确认无`export_artifacts`、`export_data_blocks`、checkpoint、修复段和候选记录保护引用，再调用文件存储端口。
- 进度缓存和SSE通知不是权威状态；删除提交后必须通过任务活动版本和进度Outbox告知列表更新，缓存清理失败不能回滚数据库删除。
