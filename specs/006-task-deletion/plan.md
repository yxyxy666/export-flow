# 实施计划：任务统一删除

**分支**：`006-task-deletion` | **日期**：2026-10-18 | **规格**：[spec.md](spec.md)

## 摘要

本功能给任务页增加一个删除入口和一个统一删除接口。后端在同一权威事务中区分运行中删除与终态删除：运行中先建立控制屏障并等待安全停止，终态直接进入清理作业；两者最终都硬删除任务业务数据库记录和文件，并通过独立技术墓碑处理迟到消息。

## 技术背景

- **语言/版本**：Java 21、TypeScript；沿用Spring Boot、MyBatis、React、Ant Design。
- **存储**：MySQL为任务状态事实源，RabbitMQ为已发送命令的交付边界，Redis为版本化进度缓存，任务专属文件目录保存块和Excel产物。
- **测试**：JUnit Jupiter、Spring Boot Test、MockMvc、Vitest、Testing Library；并发、外键、消息、缓存和文件边界使用既有真实隔离环境。
- **风险等级**：高风险，涉及数据删除、异步执行权、消息迟到、文件清理和并发一致性；遵循[ADR-0008](../../docs/adr/0008-worker-execution-lease-and-recovery.md)、[ADR-0009](../../docs/adr/0009-engineering-runtime-and-progress-notification.md)并新增本功能的删除决策记录。

## 宪法检查

| 检查项 | 状态与依据 |
| --- | --- |
| 风险等级与架构依据 | 高风险；数据删除和并发控制，采用墓碑、控制屏障和异步清理 |
| 目录、逐文件与函数草稿完整 | 已在本计划列出；不代表实现授权 |
| 三份核心规格审批 | 用户已明确授权按规格继续实施 |
| 测试先行 | 已完成；前端删除测试先红后绿，后端单元测试通过 |
| 测试阶段审批 | 按用户授权由模型自检后进入实现；真实依赖阻塞见[tasks.md](tasks.md) |

## 项目结构

```text
specs/006-task-deletion/
├── spec.md
├── plan.md
├── tasks.md
├── research.md
├── data-model.md
└── contracts/http.md

backend/src/main/java/com/exportflow/exporttasks/
├── api/TaskDeletionApiModels.java                 # 新增请求/响应DTO
├── api/TaskManagementController.java              # 增加DELETE入口
├── application/TaskDeletionService.java           # 统一分流与结果编排
├── domain/TaskDeletionModels.java                 # 删除状态、模式、回执值类型
├── infrastructure/persistence/TaskDeletionMapper.java
├── infrastructure/persistence/TaskDeletionPersistenceMapper.java
├── infrastructure/persistence/MyBatisTaskDeletionRepository.java
└── port/TaskDeletionRepository.java

backend/src/main/resources/
├── db/migration/V8__task_deletion.sql              # 墓碑、操作、清理作业字段/表
└── mappers/TaskDeletionMapper.xml                  # 锁、分流、清理和墓碑SQL

backend/src/test/java/com/exportflow/exporttasks/
├── TaskDeletionContractTest.java
├── TaskDeletionMigrationTest.java
├── TaskDeletionMySqlTest.java
├── TaskDeletionConcurrencyTest.java
├── TaskDeletionMessageRaceTest.java
└── TaskDeletionCleanupTest.java

frontend/src/features/export-tasks/
├── components/task-table.tsx                       # 统一删除按钮
├── hooks/use-task-deletion.ts                      # 提交、UNKNOWN和回执找回
├── models/task-list-model.ts                       # DELETE动作和删除中展示
├── types/export-task.types.ts                      # DTO、状态和删除回执类型
└── __tests__/task-deletion.test.tsx                # 组件与交互测试

frontend/src/shared/api/
└── api.ts                                           # deleteExportTask请求函数
```

## 逐文件职责清单

| 文件路径 | 变更与阶段 | 所属层和职责 | 函数或主要内容 | 实现任务与测试 |
| --- | --- | --- | --- | --- |
| `backend/.../api/TaskDeletionApiModels.java` | 新增/测试后实现 | API DTO | `DeleteTaskRequestDto`、`DeleteTaskResponseDto`、`DeleteTaskStateDto`字段和严格输入约束 | T-603/T-604；TC-601、TC-602 |
| `backend/.../api/TaskManagementController.java` | 修改/测试后实现 | HTTP入口 | `delete()`映射统一DELETE并映射错误 | T-603；TC-601、TC-603 |
| `backend/.../application/TaskDeletionService.java` | 新增/测试后实现 | 应用用例 | 委托统一删除、异常分类和事务边界 | T-604；TC-604、TC-605 |
| `backend/.../domain/TaskDeletionModels.java` | 新增/测试后实现 | 领域类型 | `DeletionState`、`DeletionMode`、`DeleteTaskRequest`、`DeleteTaskResult` | T-602；TC-604、TC-606 |
| `backend/.../port/TaskDeletionRepository.java` | 新增/测试后实现 | 端口 | 统一删除、操作回放、清理领取和墓碑查询签名 | T-604/T-605；TC-605至TC-610 |
| `backend/.../infrastructure/persistence/TaskDeletionMapper.java` | 新增/测试后实现 | MyBatis映射接口 | 锁任务、锁操作、更新屏障、创建作业、查询墓碑 | T-605；TC-607、TC-608 |
| `backend/.../infrastructure/persistence/TaskDeletionMapper.xml` | 新增/测试后实现 | SQL资源 | 固定锁序、版本CAS、子表清理、文件引用保护查询 | T-605/T-607；TC-608至TC-612 |
| `backend/.../infrastructure/persistence/MyBatisTaskDeletionRepository.java` | 新增/测试后实现 | 持久化适配器 | `requestDelete`、`claimCleanup`、`completeCleanup`、`markStopped` | T-605/T-607/T-608；TC-609至TC-616 |
| `backend/.../infrastructure/persistence/TaskDeletionPersistenceMapper.java` | 新增/测试后实现 | 行到领域映射 | 删除操作/作业/墓碑行转换，禁止泄漏内部字段 | T-605；TC-602、TC-610 |
| `backend/.../resources/db/migration/V8__task_deletion.sql` | 新增/测试后实现 | 数据库迁移 | 增加删除字段、操作表、清理作业表、索引和检查约束 | T-601；TC-604、TC-605 |
| `backend/.../exporttasks/application/TaskManagementService.java` | 修改/测试后实现 | 任务应用服务 | 将删除动作接入统一服务边界，保留查询/暂停/继续语义 | T-604；TC-603、TC-606 |
| `backend/.../worker/application/ExecutionSupervisor.java` | 修改/测试后实现 | 执行控制 | `stopReason()`识别删除屏障，停止新读写并关闭通道 | T-607；TC-611、TC-612 |
| `backend/.../worker/application/ExportCommandHandler.java` | 修改/测试后实现 | 消息消费 | 删除墓碑、删除屏障和过期命令的确认/拒绝策略 | T-607；TC-613、TC-614 |
| `backend/.../worker/infrastructure/persistence/TaskExecutionMapper.java` | 修改/测试后实现 | 执行SQL | 将删除状态纳入认领、心跳、完成和安全停止CAS | T-607；TC-611、TC-615 |
| `backend/.../worker/application/OrphanCleanupService.java` | 修改/测试后实现 | 清理调度 | 领取删除作业、DB清理、文件候选清理和可重试失败 | T-608；TC-616、TC-617 |
| `frontend/src/shared/api/api.ts` | 修改/测试后实现 | API client | `deleteExportTask(taskId, request, signal)`序列化和错误归一化 | T-603；TC-609 |
| `frontend/.../types/export-task.types.ts` | 修改/测试后实现 | 协议类型 | `DELETE`动作、删除状态和回执类型 | T-602；TC-609 |
| `frontend/.../models/task-list-model.ts` | 修改/测试后实现 | ViewModel | `canDelete`和删除中状态展示规则 | T-602；TC-609、TC-611 |
| `frontend/.../hooks/use-task-deletion.ts` | 新增/测试后实现 | 交互Hook | `deleteTask`、UNKNOWN找回、互斥和刷新回调 | T-603；TC-609至TC-611 |
| `frontend/.../components/task-table.tsx` | 修改/测试后实现 | 表格展示 | 单一删除按钮、禁用状态和可访问名称 | T-603；TC-609 |
| `frontend/.../pages/task-management-page.tsx` | 修改/测试后实现 | 页面编排 | 删除确认、回执提示、列表刷新和SSE衔接 | T-603/T-609；TC-610、TC-611 |
| `frontend/.../__tests__/task-deletion.test.tsx` | 新增/规格批准后测试先行 | 前端组件测试 | AC-096、AC-100、AC-101覆盖 | T-609；TC-609至TC-611 |

## 函数设计草稿

| 文件与函数名称 | 参数、类型及约束 | 返回值与错误 | 中文注释草稿 | 简短流程 | 调用关系 | 阶段及任务/测试 |
| --- | --- | --- | --- | --- | --- | --- |
| `TaskDeletionService.deleteTask` | `DeleteTaskRequest(UUID taskId, UUID operationId, long expectedVersion)`；字段非空、版本非负 | `DeleteTaskResult`；业务冲突、不可用异常 | “统一删除入口只编排，不让控制器按状态分流。” | 校验→委托仓库→映射回执→分类UNKNOWN | Controller→Repository | T-604；TC-603至TC-606 |
| `MyBatisTaskDeletionRepository.requestDelete` | 同上；事务内读取任务和已有操作 | `DeleteTaskResult`；404/409/503 | “在任务锁和活动锁内按权威状态选择ACTIVE_STOP或TERMINAL_CLEANUP。” | 锁活动→锁任务→回放操作→状态分流→写屏障/作业→记录通知 | Service→Mapper、Commands、Executions、Progress | T-605；TC-607至TC-610 |
| `MyBatisTaskDeletionRepository.markStopped` | `UUID taskId, long controlVersion, StopProof proof`；证明必须匹配当前控制版本 | `CleanupTransition`；过期身份返回STALE | “只有安全停止证明才能从STOPPING进入CLEANUP_PENDING。” | 锁任务/租约→验证无有效写入→更新删除状态→登记清理 | Worker→Repository | T-607；TC-611、TC-612 |
| `MyBatisTaskDeletionRepository.claimCleanup` | `UUID jobId, String owner, Instant dbNow`；租约身份受限 | `CleanupLease`或空 | “以短事务领取清理作业，不能持锁做文件IO。” | 领取作业→检查墓碑与引用→写清理阶段→返回租约 | CleanupService→Mapper | T-608；TC-616 |
| `OrphanCleanupService.processDeletion` | `CleanupLease lease`；不可读/文件失败可重试 | `CleanupResult`；安全错误分类 | “先清数据库保护引用，再异步删除文件并提交完成。” | 校验租约→清理子表→扫描候选→物理删除→完成墓碑 | Scheduler→Repository、Storage | T-608；TC-616、TC-617 |
| `TaskDeletionMapper.toDto` | `DeleteTaskResult` | `DeleteTaskResponseDto` | “只返回操作状态和任务身份，不泄漏内部路径。” | 字段映射→版本字符串化→隐藏内部字段 | Controller | T-603；TC-601、TC-602 |
| `deleteExportTask` | `taskId:string, request:DeleteTaskRequest, signal?:AbortSignal` | `Promise<DeleteTaskResponse>`；规范化API错误 | “统一调用DELETE，提交结果不明交给Hook保存原操作。” | 序列化→请求→映射响应/错误 | useTaskDeletion→httpClient | T-603；TC-609 |
| `useTaskDeletion.submit` | `TaskListItem`及冻结`operationId/expectedVersion` | 异步状态；UNKNOWN保留原请求 | “删除提交期间行级互斥，网络失败不推断业务结果。” | 生成操作→调用API→接受回执→刷新列表或保存UNKNOWN | Page→API、list.refresh、stream.refresh | T-603/T-609；TC-609至TC-611 |
| `getTaskActions` | `TaskListItem` | `{canDelete, ...}` | “删除可用性只由服务端动作和删除状态交集决定。” | 检查DELETE动作、未知状态和删除中状态 | TaskTable | T-602；TC-609、TC-611 |

## 类型与调用链

- **前端**：`TaskTable → TaskManagementPage.confirmDelete → useTaskDeletion.submit → deleteExportTask → API`；成功或回执确认后刷新当前查询和SSE桥接快照，UNKNOWN保留原操作并禁止该行重复提交。
- **后端**：`TaskManagementController.delete → TaskDeletionService.deleteTask → TaskDeletionRepository.requestDelete → TaskDeletionMapper/CommandOutboxMapper/TaskExecutionMapper/ProgressRepository`；运行中继续由`ExecutionSupervisor/ExportCommandHandler`安全停止，清理由`OrphanCleanupService`领取作业。
- **事务边界**：请求事务只写删除屏障、操作回执、消息关闭和清理作业；Worker停止和文件IO在独立短事务/租约内完成；物理文件删除不持有业务行锁。
- **公共能力**：复用统一错误映射、`ProgressRepository`、任务活动版本、文件存储和清理候选能力，不新增无边界工具层。
- **仅设计能力**：墓碑定期归档、批量删除、权限审计和回收站恢复留后续规格。

## 失败出口与回退

- 请求事务提交未知：按同一`operationId`回放，不重新创建作业。
- Worker停止提交未知：保留STOPPING和租约隔离，下一次扫描核对权威状态，不提前清理。
- 文件删除失败：保留墓碑和RETRYABLE_FAILURE，按作业退避重试。
- 发现设计不能满足迟到消息安全边界时，回退为停止新删除受理、保留所有墓碑和作业；禁止批量删表、清空队列或重置控制版本。
