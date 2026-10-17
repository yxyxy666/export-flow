# 技术计划

[需求](spec.md) → [契约](contracts.md) → [任务及证据](tasks.md)。Java21/Spring Boot、MyBatis/MySQL、RabbitMQ、Redis、React/TypeScript沿用现有版本。高风险原因及回滚见[ADR](../../docs/adr/0012-user-resume-and-live-progress.md)。

实体及生命周期见[数据模型](data-model.md)，实际操作见[quickstart](quickstart.md)。

## 变更目录树与逐文件职责

以下均本期实施；现有文件修改，标“新增”的文件新增。包目录缩写只省略已有共同前缀`backend/src/main/java/com/exportflow/`。

```text
backend/src/main/java/com/exportflow/
  ExportFlowApplication.java                         启用Spring调度，无新增自有函数
  exporttasks/
    domain/TaskManagementModels.java                 新增ResumeRequest/ResumeResult记录及校验
    port/TaskManagementRepository.java               新增继续持久端口
    application/TaskManagementService.java            继续用例及UNKNOWN错误映射
    api/TaskManagementController.java                继续HTTP入口，复用严格JSON控制请求解析
    api/TaskManagementApiModels.java                 继续结果DTO，无行为函数
    infrastructure/persistence/
      MyBatisTaskManagementRepository.java           继续事务、回执和跨动作ID校验
      TaskManagementMapper.java                      继续CAS与回执SQL、记录类型
      TaskManagementPersistenceMapper.java            PAUSED开放RESUME
  dispatch/infrastructure/persistence/CommandOutboxMapper.java  原命令重开SQL
  worker/infrastructure/persistence/
    TaskExecutionMapper.java                         已有执行记录重新认领SQL
    MyBatisTaskExecutionRepository.java              PENDING首次/继续认领分支
    MyBatisAssemblyRepairRepository.java             部分集合修复和收集阶段提交
  worker/port/AssemblyRepairRepository.java            新增核验后继续收集端口
  worker/application/AssemblyRepairService.java        部分断点审计、恢复准备流程
  worker/application/ExportDataCollectionService.java  继续期间禁止旧前缀全量换代分支
  worker/infrastructure/excel/PoiExportFileGenerator.java  收集前调用恢复核验
backend/src/main/resources/db/migration/
  V7__task_resume_operations.sql                      新增继续回执表，无函数
frontend/src/
  shared/api/api.ts                                  继续HTTP适配
  features/export-tasks/
    models/export-task-schema.ts                     动作枚举新增RESUME，无函数
    types/export-task.types.ts                       TaskListItem动作类型新增RESUME，无函数
    models/task-list-model.ts                        继续按钮可用性
    hooks/use-task-actions.ts                        按动作区分请求、UNKNOWN同操作找回
    pages/task-management-page.tsx                   继续确认、动作成功后刷新活动元信息
    components/task-table.tsx                        继续按钮与互斥提交
    __tests__/task-resume.test.tsx                    新增继续/空闲唤醒/回退进度/100%测试
    __tests__/task-actions.test.tsx                   旧测试说明更新为服务端未授权不显示继续
  features/orders/__tests__/order-boundaries.test.tsx  旧离页用例允许目标任务页一次只读快照
backend/src/test/java/com/exportflow/
  worker/TaskResumeTest.java                          新增真实MySQL、文件、控制一致性回归
  progress/AutomaticTaskProgressTest.java              新增生产自动调度→MQ→SSE→100%链路
  exporttasks/TaskManagementMigrationTest.java          增加V6至V7升级验证并保持空库/旧版路径
docs/product/acceptance-criteria.md                   唯一验收定义
docs/product/order-export-prd.md                     任务继续与实时通知增量入口
README.md                                           任务页现状与增量入口
docs/adr/0010-task-pause-control-barrier.md             指向后续扩展，不改历史决策
docs/adr/0011-selective-block-repair-and-progress.md    指向部分恢复扩展
specs/004-task-management/contracts/http-sse.md         指向新契约，保留原范围
docs/decisions-pending.md                             继续需求已明确，不再列预留事项
docs/adr/0012-user-resume-and-live-progress.md          新增跨模块决策
docs/validation/005-task-resume-and-live-progress/     新增真实证据记录
specs/005-task-resume-and-live-progress/               新增本规格产物及contracts/openapi.yaml（机器可读输入输出结构，无函数）
```

复用测试夹具TaskManagementFixture、BoundaryProbe、ProgressRedisFixture及SharedServices，不创建业务替代实现。复用TaskManagementApiMapper.toPause解析相同形状输入，再构造ResumeRequest；不改旧暂停公开类型。框架构造器和机械访问器省略。

## 类型与函数设计草稿

| 文件/函数 | 类型化参数与返回 | 中文目的、流程、调用关系 | 任务/测试 |
| --- | --- | --- | --- |
| Models.ResumeRequest | taskId:UUID, operationId:UUID, expectedControlVersion:long → record | 非空、非负控制请求；控制版本与进度版本分离 | T-02/TC-501 |
| Models.ResumeResult | outcome:String, task:TaskListItem → record | 返回APPLIED或REPLAY及当前摘要 | T-02/TC-501 |
| Controller.resume | taskId:UUID, request:HttpServletRequest → ResponseEntity<ResumeResultDto>；IOException | 严格4096字节解析，共用控制输入校验→service.resume→mapper.toDto | T-02/TC-501 |
| Controller.readControlRequest | request:HttpServletRequest → PauseRequestDto；IOException | 抽出暂停/继续共同的严格JSON解析；拒绝多余内容 | T-02/TC-501 |
| Service.resume / Repository.resume | request:ResumeRequest → ResumeResult | 服务保留业务错误，其余503 UNKNOWN；持久层固定锁序→先回执→状态/版本→重开命令→CAS→回执/通知同事务 | T-02/TC-501/502 |
| MyBatisTaskManagementRepository.hash | taskId:UUID, expectedControlVersion:long → byte[] | 沿用旧暂停摘要编码；跨动作通过查两张回执表拒绝，兼容旧回执 | T-02/TC-502 |
| TaskManagementMapper.resume | id:String, version:long → int | PAUSED+控制版本CAS到PENDING，控制及摘要版本递增，phase CHECKING，保留修复标识及预算 | T-02/TC-501 |
| TaskManagementMapper.resumeOperation / insertResumeOperation | operationId:String → OperationRow?；id、taskId、hash、expectedVersion、response → int | 新表持久幂等回执，与暂停表联合查冲突 | T-02/TC-502 |
| CommandOutboxMapper.reopen | taskId:String → int | 仅CLOSED/USER_PAUSED原命令重开READY，清token/lease/sent/closeReason，重设deadline与nextAttempt；不重置attemptNo | T-02/TC-501 |
| TaskExecutionMapper.resumeLease | id:String,owner:String,now:Instant,until:Instant,deadline:Instant,next:Instant → int | 更新已有记录，fence+1并重建本次尝试窗口，保留recovery_count；claim随后设置controlVersion | T-02/TC-502 |
| ExecutionRepository.claim（修改） | taskId:UUID,owner:String,policy:ExecutionPolicy → ClaimResult | PENDING下无记录insert，有记录resumeLease；原RUNNING接管语义不变 | T-02/TC-502 |
| PersistenceMapper.toItem（修改） | row:ListRow → TaskListItem | PAUSED输出RESUME，其他动作保持状态交集 | T-02/TC-501 |
| RepairService.auditPrefix | context:GenerationContext,snapshot:RepairSnapshot,checkpoint:CheckpointSnapshot,guard:ExecutionGuard → AssemblyAudit；IOException | 校验部分集合边界及不可变分段链，再逐块实读核验；共享audit逐块流程，不把total当已收集量 | T-03/TC-503/504 |
| RepairService.prepareResume | context:GenerationContext,workspace:AttemptWorkspace,guard:ExecutionGuard → void；IOException | 当前控制版本表明继续时读取断点；封存留给既有装配审计；部分集合markChecking→auditPrefix→只重建坏段→重新审计→markCollecting；无断点有进度失败 | T-03/TC-503/504 |
| RepairService.audit / validManifest（重构） | 原签名保留；内部共享逐块检查和段链校验 | 完整清单仍要求digest/total；部分清单要求checkpoint一致；普通I/O向上传递不冒充文件损坏 | T-03/TC-503/504 |
| RepairService.auditBlocks / validSegments | snapshot:RepairSnapshot,trigger:RepairIssue?,guard:ExecutionGuard → AssemblyAudit；snapshot,expectedRows:long,lastCursor:OrderCursor?,checkCursor:boolean → boolean | 共享物理块实读及连续段链校验，部分集合另核对末游标；由audit/auditPrefix调用 | T-03/TC-503/504 |
| Collection.collect（修改） | context:GenerationContext,guard:ExecutionGuard → CompletedDataset；IOException | 继续身份下损坏前缀不触发全量换代，交恢复核验选择修复或明确失败；原首次执行流程保留 | T-03/TC-503/504 |
| RepairRepository.beginRepair（修改） | lease:ExecutionLease,audit:AssemblyAudit → RepairWriteResult | 允许已审计部分集合；同checkpoint/manifest/epoch快照CAS，不重置预算 | T-03/TC-503 |
| RepairRepository.markCollecting | lease:ExecutionLease,audit:AssemblyAudit → RepairWriteResult | 锁后比对快照/断点边界，健康总量提交trustedProcessed，phase COLLECTING，保留修复提示 | T-03/TC-503 |
| Generator.generate（修改） | context:GenerationContext,guard:ExecutionGuard → GenerationResult | prepareAttempt→prepareResume→collect→既有完整审计、装配、校验、发布；无POI工作文件复用 | T-03/TC-503/504 |
| api.resumeExportTask | taskId:string,request:PauseRequest,signal:AbortSignal → Promise<PauseResult> | 相同控制契约POST resume，校验结果schema；保留兼容旧公开类型 | T-04/TC-506 |
| useTaskActions.submit / resume | Operation{kind:PAUSE或RESUME,taskId,request,state,message?} → Promise<void>；resume(task:TaskListItem) → Promise<void>；Hook新增可选onResumed:()⇒void | 按kind选择API，UNKNOWN保留原kind/id/version，请求互斥与卸载取消沿用，成功继续后回调唤醒通道 | T-04/TC-506 |
| getTaskActions（修改） | task:TaskListItem → 原动作模型+canResume:boolean | 仅PAUSED与allowedActions同时准许继续 | T-04/TC-506 |
| Page.confirmResume | task:TaskListItem → void | 确认说明先检查文件；actions.resume成功后合并摘要并刷新活动元信息，空闲通道再建立 | T-04/TC-506 |
| TaskTable（修改） | rows:TaskListItem[],isBusy:boolean,onPause:(task)⇒void,onResume?:(task)⇒void → ReactElement | 根据合法动作呈现继续按钮，互斥禁用，不允许半成品下载 | T-04/TC-506 |
| TaskResumeTest测试及辅助方法 | 无参数JUnit方法 → void throws Exception；resume(task,operation,version) → HttpResponse<String>；pauseRunning(task,lease) → void | 真实隔离数据库验证输入、幂等、竞争、回执不明、旧执行拒绝；文件注入后验证保留健康段及进度/最终内容 | T-01/TC-501至504 |
| AutomaticTaskProgressTest | 无参数测试→void throws Exception；schedulingEnabled/consumerEnabled→boolean；nextEvent(reader)→事件 | 生产入口自动调度和消费，通过屏障观察已提交中间值，再放行最终100%/下载；Redis独立前缀，真实MQ/HTTP | T-01/TC-505 |
| task-resume.test.tsx | Vitest用例，无输入 → Promise<void> | MSW模拟继续确认/UNKNOWN找回，真实组件接收新版本低进度与100%，空闲页继续后开SSE | T-01/TC-506 |
| MigrationTest.TC409_emptyAndSupportedUpgradePreserveTasksAndBackfillTrustedProgress | previous:int（0/4/5/6）→ void throws Exception | 复用真实迁移测试，增加V6升级；验证V7且原任务/命令/断点/采用引用保留，无新来源写入 | T-05/复用TC-409 |

## 验证与运行

先新增回归并留下目标行为红灯，再按用户本次预授权实施。Maven产物使用独立artifacts/005目录，避免覆盖用户运行jar；前端pnpm test、typecheck/lint/build；后端受影响真实边界与Spotless/Checkstyle/SpotBugs/JaCoCo。只验证正确性，不重复测性能或时长；现有用户进程不重启。环境/命令/结果见[tasks](tasks.md)。
