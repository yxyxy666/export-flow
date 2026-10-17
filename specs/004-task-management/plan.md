# 技术计划：任务列表、SSE进度、暂停与装配修复

**版本**：0.3　**日期**：2026-10-17　**状态**：规格0.3及测试阶段已于2026-10-17获负责人批准，当前生产实施；保留测试先行证据。
输入：[规格](spec.md)、[仓库调查](research.md)。原范围为列表/SSE/暂停，2026-10-17按负责人要求增加运行中装配异常自动修复。


审批依据：负责人先以“可以按照当前规格开始了”批准规格0.3，随后对测试材料及六类缺口处理方案回复“继续”，批准测试阶段并授权本期生产实施。范围与日期见[审批记录](tasks.md#审批记录)；项目级效率规则草案仍独立待批。

## 技术背景

使用现有Java21、Spring Boot 3.5.16、Spring MVC/SseEmitter、MyBatis/MySQL、RabbitMQ、Redis7.4；前端React19、TypeScript、TanStack Query、React Hook Form+Zod和Ant Design，版本沿用锁文件及POM。前端测试Vitest/Testing Library/MSW；后端JUnit Jupiter/AssertJ/MockMvc和真实隔离依赖。

后端已添加由现有Spring Boot BOM管理的data-redis starter，不升级共享Redis。页面复用任务路由，业务API全部归shared/api/api.ts。实际运行仍宿主机与已启动基础设施，文件根沿用项目export-data；凭据外注入，不在规格记录。

不增加性能目标，不做性能或时间阈值测试。并发裁决、事件结构和资源关闭属于功能正确性，不按耗时断言。项目级规则草案仍待审批，本功能排除清单单独列在[任务](tasks.md#验证排除与覆盖缺口)。

## 风险与宪法检查

| 检查项 | 状态与依据 |
| --- | --- |
| 风险分级 | 高风险；暂停改变执行权及消费确认边界，影响并发状态与文件登记 |
| 已接受架构 | [ADR-0008](../../docs/adr/0008-worker-execution-lease-and-recovery.md)原执行/断点；[ADR-0009](../../docs/adr/0009-engineering-runtime-and-progress-notification.md)可靠通知 |
| 新架构审批 | [ADR-0010](../../docs/adr/0010-task-pause-control-barrier.md)及[修复ADR](../../docs/adr/0011-selective-block-repair-and-progress.md)已于2026-10-17随规格接受 |
| 目录与函数设计 | 本文件完整范围树及[逐文件/函数清单](function-design.md)，配置/类型/SQL职责已列 |
| 核心与附属规格审批 | 0.3已批准，见[tasks.md](tasks.md#审批记录) |
| 测试进度/红灯 | 基础集已执行，见[实际证据](../../docs/validation/004-task-management/verification.md)及[缺口](../../docs/validation/004-task-management/coverage-gaps.md) |
| 测试阶段门禁及审批 | 已批准测试阶段及缺口处理方案；实施后逐项补证 |
| 数据/运行边界 | 已复用已有三容器和只读来源；只创建并回收隔离测试资源 |
| 规则调整 | [效率草案](../../docs/rules/testing-efficiency-proposal.md)待审批，不擅自写为生效 |

## 模块设计与调用关系

查询：TaskManagementPage → useTaskList → queryExportTasks → TaskManagementController → TaskManagementService.query → TaskManagementRepository → MySQL READ COMMITTED单语句一致快照。URL只保留已提交查询；旧queryGeneration响应丢弃；瞬时成员变化不跨页拼出虚假列表。

通知：业务原事务先活动锁 → 更新task.version → 同事务recordChange → 独立dispatcher → Redis版本写/故障回源 → Rabbit fanout → 每实例durable queue → 权威摘要 → 本地串行SSE → useTaskStream/mergeTaskEvent。所有可观察写入入口，包括初次受理、认领、进度、成功/失败、投递失败、暂停和停止确认，必须纳入通知，不能只给新API加事件。

快照/订阅：HTTP初始快照决定开流 → SSE注册本地监听/buffer → stream-ready → 一次桥接快照/事件版本合并；回源请求在途时dirty合并，纯进度不查询，成员变更在请求结束后补一次。无历史回放，不省略重连快照。接受idle后额外一次最终快照补偿尚未广播的最后状态；close原EventSource后才允许自定义退避；串行发送有界并在完成后确认通知。

暂停：行确认 → useTaskActions冻结PauseRequest → pauseExportTask → 短事务控制裁决 → PENDING直接PAUSED或RUNNING→PAUSING → guard权威检查/关闭资源 → PAUSE_STOPPED/PauseProof → 安全停止确认事务 → PAUSED/封存原命令 → 通知与可核对后ACK。结果不明走同operationId找回，不自动创建新请求。

共用能力只有统一API、日期/错误格式器及本浏览器任务创建通知；不新建无调用方的通用模块。详情、增删改、失败后用户手动重试、继续没有本期接口或函数；同任务自动修复函数见增量设计。

## 状态、持久化与契约

完整字段与锁序见[数据模型](data-model.md)，HTTP/SSE见[协议](contracts/http-sse.md)和[OpenAPI](contracts/openapi.yaml)，Worker/ACK见[执行协议](contracts/execution-control.md)。已新增V5兼容迁移，六态和控制字段、暂停幂等回执、活动行与进度Outbox；没有名称、删除或runGeneration字段。原创建响应、v1命令和task/type唯一键保留。

expectedControlVersion独立于进度version；pause新控制屏障禁止旧执行登记，安全停止回执仍核验原owner/fence与当前请求控制版本。回执优先于普通guard总闸；监督器保留暂停中的delivery，对账在启动及既有监督周期调用，细节见[执行契约](contracts/execution-control.md#worker停止回执的实际入口)。PAUSED不是业务终态，本期不恢复执行；PAUSING崩溃对账只确立停止，不重新生成。

## 文档目录

~~~text
specs/004-task-management/
├── spec.md
├── plan.md
├── tasks.md
├── research.md
├── data-model.md
├── function-design.md
├── quickstart.md
├── review.md
└── contracts/
    ├── openapi.yaml
    ├── http-sse.md
    ├── execution-control.md
    └── assembly-repair.md
~~~

产品定义归docs/product/acceptance-criteria.md；分期说明归order-export-prd.md；暂停决策归ADR-0010；状态和待评审事项归docs/decisions-pending.md。验证执行后才创建docs/validation/004-task-management/verification.md，不在规格阶段提前伪造验证文档。

## 本次拟变更及复用的文件树

以下区分规划与实际产物，[逐文件表](function-design.md#逐文件职责)标明新增、修改或复用。测试、专用夹具及下列生产代码和迁移已创建；逐文件职责和实际签名见函数清单。

~~~text
├── frontend
│   └── src
│       ├── features
│       │   ├── export-tasks
│       │   │   ├── pages
│       │   │   │   └── task-management-page.tsx
│       │   │   ├── types
│       │   │   │   └── export-task.types.ts
│       │   │   ├── models
│       │   │   │   ├── export-task-schema.ts
│       │   │   │   ├── task-query.ts
│       │   │   │   └── task-list-model.ts
│       │   │   ├── hooks
│       │   │   │   ├── use-task-list.ts
│       │   │   │   ├── use-task-stream.ts
│       │   │   │   └── use-task-actions.ts
│       │   │   ├── components
│       │   │   │   ├── task-filter.tsx
│       │   │   │   └── task-table.tsx
│       │   │   └── __tests__
│       │   │       ├── task-query.test.ts
│       │   │       ├── task-stream.test.tsx
│       │   │       └── task-actions.test.tsx
│       │   └── orders
│       │       └── pages
│       │           └── order-management-page.tsx
│       └── shared
│           ├── api
│           │   └── api.ts
│           └── events
│               └── task-created.ts
├── backend
│   ├── src
│   │   ├── main
│   │   │   ├── java
│   │   │   │   └── com
│   │   │   │       └── exportflow
│   │   │   │           ├── exporttasks
│   │   │   │           │   ├── api
│   │   │   │           │   │   ├── TaskManagementController.java
│   │   │   │           │   │   ├── TaskManagementApiModels.java
│   │   │   │           │   │   └── TaskManagementApiMapper.java
│   │   │   │           │   ├── domain
│   │   │   │           │   │   ├── TaskManagementModels.java
│   │   │   │           │   │   ├── ExportTask.java
│   │   │   │           │   │   └── ExportTaskModels.java
│   │   │   │           │   ├── application
│   │   │   │           │   │   ├── TaskManagementService.java
│   │   │   │           │   │   └── CreateExportTransaction.java
│   │   │   │           │   ├── port
│   │   │   │           │   │   ├── TaskManagementRepository.java
│   │   │   │           │   │   └── ArtifactRepository.java
│   │   │   │           │   └── infrastructure
│   │   │   │           │       └── persistence
│   │   │   │           │           ├── MyBatisTaskManagementRepository.java
│   │   │   │           │           ├── TaskManagementMapper.java
│   │   │   │           │           ├── TaskManagementPersistenceMapper.java
│   │   │   │           │           ├── ExportTaskRecord.java
│   │   │   │           │           ├── ExportTaskPersistenceMapper.java
│   │   │   │           │           └── MyBatisArtifactRepository.java
│   │   │   │           ├── progress
│   │   │   │           │   ├── api
│   │   │   │           │   │   ├── TaskEventController.java
│   │   │   │           │   │   └── TaskStreamWebConfiguration.java
│   │   │   │           │   ├── domain
│   │   │   │           │   │   └── ProgressModels.java
│   │   │   │           │   ├── application
│   │   │   │           │   │   ├── TaskProgressService.java
│   │   │   │           │   │   ├── SseConnectionRegistry.java
│   │   │   │           │   │   ├── ProgressScheduler.java
│   │   │   │           │   │   └── ProgressConsumerReadiness.java
│   │   │   │           │   ├── port
│   │   │   │           │   │   ├── ProgressRepository.java
│   │   │   │           │   │   ├── ProgressCache.java
│   │   │   │           │   │   └── ProgressPublisher.java
│   │   │   │           │   └── infrastructure
│   │   │   │           │       ├── persistence
│   │   │   │           │       │   ├── MyBatisProgressRepository.java
│   │   │   │           │       │   └── ProgressMapper.java
│   │   │   │           │       ├── cache
│   │   │   │           │       │   └── RedisProgressCache.java
│   │   │   │           │       └── messaging
│   │   │   │           │           ├── RabbitProgressTransport.java
│   │   │   │           │           └── RabbitProgressListener.java
│   │   │   │           ├── worker
│   │   │   │           │   ├── application
│   │   │   │           │   │   ├── PauseReconciliationService.java
│   │   │   │           │   │   ├── TaskExecutionService.java
│   │   │   │           │   │   ├── ExportCommandHandler.java
│   │   │   │           │   │   ├── ExecutionSupervisor.java
│   │   │   │           │   │   └── ExportDataCollectionService.java
│   │   │   │           │   ├── domain
│   │   │   │           │   │   └── ExecutionModels.java
│   │   │   │           │   ├── port
│   │   │   │           │   │   ├── TaskExecutionRepository.java
│   │   │   │           │   │   ├── ExecutionGuard.java
│   │   │   │           │   │   └── CheckpointRepository.java
│   │   │   │           │   └── infrastructure
│   │   │   │           │       ├── persistence
│   │   │   │           │       │   ├── MyBatisTaskExecutionRepository.java
│   │   │   │           │       │   ├── TaskExecutionMapper.java
│   │   │   │           │       │   ├── ExecutionLeaseRecord.java
│   │   │   │           │       │   └── MyBatisCheckpointRepository.java
│   │   │   │           │       └── excel
│   │   │   │           │           ├── PoiExportFileGenerator.java
│   │   │   │           │           └── ExcelWorkbookAssembler.java
│   │   │   │           ├── dispatch
│   │   │   │           │   ├── port
│   │   │   │           │   │   └── CommandOutboxRepository.java
│   │   │   │           │   └── infrastructure
│   │   │   │           │       └── persistence
│   │   │   │           │           ├── MyBatisCommandOutboxRepository.java
│   │   │   │           │           └── CommandOutboxMapper.java
│   │   │   │           └── config
│   │   │   │               ├── RuntimeConfiguration.java
│   │   │   │               └── RuntimeProperties.java
│   │   │   └── resources
│   │   │       ├── mappers
│   │   │       │   ├── ExportTaskMapper.xml
│   │   │       │   ├── TaskManagementMapper.xml
│   │   │       │   ├── TaskExecutionMapper.xml
│   │   │       │   └── CommandOutboxMapper.xml
│   │   │       ├── application.yml
│   │   │       └── db
│   │   │           └── migration
│   │   │               └── V5__task_list_pause_and_progress.sql
│   │   └── test
│   │       └── java
│   │           └── com
│   │               └── exportflow
│   │                   ├── exporttasks
│   │                   │   ├── TaskManagementContractTest.java
│   │                   │   └── TaskManagementUnitTest.java
│   │                   ├── worker
│   │                   │   ├── TaskPauseMySqlTest.java
│   │                   │   └── TaskPauseFileTest.java
│   │                   └── progress
│   │                       └── TaskProgressIntegrationTest.java
│   └── pom.xml
└── tests
    └── backend
        └── src
            └── test
                └── java
                    └── com
                        └── exportflow
                            └── testing
                                ├── SharedServices.java
                                ├── ProductionBackendFixture.java
                                └── ProgressRedisFixture.java
~~~

## 逐文件职责与函数草稿

完整逐文件职责、类型化签名、中文注释目的、简短流程和调用关系见[function-design.md](function-design.md#后端类型化函数与目的)，作为本计划附属清单共同审批。所有配置、类型、SQL及测试计划也在该表记录；新代码函数没有提前实现。

已有机械records访问器/构造注入与无行为变化方法无需展开。ControlLease升级需要同步CheckpointRepository与ArtifactRepository受影响端口及生成器调用方；原文件guard边界复用，上层独立归类暂停。所有实际新增函数如实施时超出草稿必须先同步设计，公开边界变更须重新确认对应审批。

## 测试准备与执行效率

规格/候选ADR获批后，按负责人本会话已明确要求的排除范围准备测试文件、MSW/连接替身、独立schema/vhost和缓存前缀；复用tests/backend现有MySQL/Rabbit隔离工具与已有来源数据，新增仅连接既有Redis的测试夹具。不得创建生产类空壳、迁移或通知拓扑来制造红灯。Redis故障作为可降级缓存，不将其可用性变为受理或启动的权威状态条件；通知关闭只停止网络dispatcher，仍登记持久通知意图。

新增功能先取得行为红灯；缺少接口导致导入/编译阻塞单独登记，不当红灯。覆盖受影响真实事务、暂停ACK、文件引用及通知边界；没有用户继续/CRUD测试，装配自动修复测试属于本期。本期排除项见tasks；不用真实等待或虚拟时钟补做超时/延时验证。

整个本期范围先完成测试材料，再统一申请第二次门禁审批，随后按T-010至T-015及T-017/018实施。开发中跑受影响测试，成功后无变化不重跑；收尾前端test:run + build（build已含typecheck），后端过滤后的verify保留真实功能集成与静态检查，不能无筛选恢复旧性能/计时用例。准确命令与排除条件见[quickstart](quickstart.md)。

## 兼容与回退

部署前停止新受理和消费，保留已有任务、原命令和文件，应用新迁移后启动兼容六态及原v1消息的版本。控制入口与通知开关可关闭，但关闭通知不能丢进度Outbox，关闭暂停入口不能把PAUSING改回RUNNING。

存在PAUSING/PAUSED后不能直接回退只识别四态的旧二进制；保留支持新状态的兼容版本并完成停止对账，不降级迁移、不清队列或引用文件、不重置fence。已暂停任务保持暂停；显式继续需要新规格和审批。

## 复杂度说明

统一活动锁与可靠跨实例通知沿用已接受方向，控制屏障为暂停正确性所需。没有宪法豁免；性能与计时验证的排除只影响证据执行，不更改业务参数。候选架构与两次阶段审批仍真实区分。

## 版本0.2审查修订

实质问题及依据见[审查记录](review.md)。核心修订为暂停停止结果优先分流、监督器保留delivery、启动停止对账、READ COMMITTED单SELECT快照、idle最终快照、实际广播后ACK、有界连接发送以及OpenAPI。新增发送队列上限256已随0.3规格接受，只作功能配置，不是性能验收。规格审批包含OpenAPI与函数补充清单；两个ADR已随0.3规格接受；第二次实施授权已取得。

## 选择性修复增量计划

装配入口读取封存清单→事务外audit→短事务beginRepair发布可信计数与通知→按坏段readSegment/写新块→commitReplacement原子选择→新Workbook装配与EOF校验→最终文件校验／发布／采用。全部健康数据块保留身份；工作文件整体重新生成。详细算法、旧READY损坏和异常矩阵见[修复契约](contracts/assembly-repair.md)。本计划与[修复ADR](../../docs/adr/0011-selective-block-repair-and-progress.md)已随0.3接受，第二次实施授权已取得，不能沿旧高水位语义实施。

以下是上方完整树的增量路径，与原树合并构成本次变更范围；同路径既有文件职责以[函数增量](function-design.md#选择性修复增量设计)补充，对应文件已在获准实施后落地。

~~~text
frontend/src/features/export-tasks/
├── models/task-list-model.ts                       # 修改：新版本低计数与repair字段
├── components/task-table.tsx                       # 修改：黄色信息图标/键盘提示
├── types/export-task.types.ts                      # 修改：修复摘要类型
├── models/export-task-schema.ts                    # 修改：修复摘要校验
└── __tests__/task-repair.test.tsx                   # 新增：TC-416/417
backend/src/main/java/com/exportflow/
├── worker/
│   ├── application/
│   │   ├── AssemblyRepairService.java               # 新增：对账和修复编排
│   │   └── ExportDataCollectionService.java         # 修改：分段基线/封存/有界重读
│   ├── domain/AssemblyRepairModels.java             # 新增：清单、审计、计划、结果类型
│   ├── port/
│   │   ├── AssemblyRepairRepository.java            # 新增：事务端口
│   │   └── CheckpointRepository.java                # 修改：VerifiedBlock重载
│   └── infrastructure/
│       ├── persistence/
│       │   ├── MyBatisAssemblyRepairRepository.java # 新增：身份校验/计划和选择CAS
│       │   ├── AssemblyRepairMapper.java            # 新增：持久化读写接口
│       │   ├── MyBatisCheckpointRepository.java     # 修改：块与清单原子提交/活跃选择读取
│       │   └── CheckpointMapper.java               # 修改：选择查询
│       ├── file/DataBlockCodec.java                 # 修改：规范内容摘要校验
│       └── excel/
│           ├── PoiExportFileGenerator.java          # 修改：有界修复循环/旧READY校验
│           └── ExcelWorkbookAssembler.java          # 修改：有效块顺序/错误分类/关闭
├── orders/
│   ├── port/OrderRepository.java                    # 修改：readSegment
│   └── infrastructure/persistence/
│       ├── MyBatisOrderRepository.java              # 修改：范围与上界校验
│       └── OrderMapper.java                        # 修改：有界分段查询
└── config/RuntimeConfiguration.java                 # 修改：组装修复服务依赖
backend/src/main/resources/
├── db/migration/V6__assembly_repair_segments.sql     # 新增；不改历史迁移
└── mappers/
    ├── AssemblyRepairMapper.xml                     # 新增：清单/轮次/选择/可信进度
    ├── CheckpointMapper.xml                         # 修改：读取当前选择
    ├── OrderMapper.xml                              # 修改：原scope AND (prev,last]
    ├── TaskManagementMapper.xml                     # 修改：可信进度与修复摘要
    └── TaskExecutionMapper.xml                      # 修改：旧高水位/可信量分别更新
backend/src/test/java/com/exportflow/worker/
├── AssemblyRepairUnitTest.java                       # 新增：TC-414/415策略
├── AssemblyRepairMySqlTest.java                      # 新增：TC-414/415/416事务
└── AssemblyRepairFileTest.java                       # 新增：TC-414/415实际文件
~~~

原树中的TaskManagementModels/ApiModels、ExportTaskRecord及所有TaskListItem持久／协议映射增补修复字段；统一映射读取trusted_processed_count。首次创建／迁移／认领、正常块提交、修复对账、替换、暂停和终态均维护字段不变量；既有TaskExecutionMapper.updateProgress保留历史高水位更新，同时按明确路径更新可信计数，修复不以GREATEST覆盖当前可信量。域与DTO分层保持不变，函数详见附属清单。

V6新增段清单、修复轮次／段计划、封存与可信计数列；解除旧块三元唯一约束后，分段PK和选择版本成为唯一有效块裁决，读取必须同步切换，禁止旧Worker混跑。清单补齐必须实际校验，已封存任务先走修复读路径，不再进入collect的旧“坏块清空全量新代”分支；未封存任务仍按旧连续前缀完成收集，不承诺尚无基线的坏块选择性恢复。失败READY保留保护记录，只允许重新生成新的候选与产物ID。

测试新增T-016先行覆盖，计入T-008/009完整门禁；T-017后端修复与T-018前端提示只在第二次明确批准后实施，完成后纳入T-014/015一次适用验证。候选预算3轮为功能参数，不新增性能、退避、超时或延时测试。

## 测试先行实际产物增量

测试先行阶段创建下列基础测试与证据；生产实施阶段增补通知发送、迁移及深层边界测试，详见函数清单与验证记录。详细职责及类型化夹具函数见[函数清单增量](function-design.md#测试先行实际夹具与职责)。

~~~text
frontend/src/features/export-tasks/__tests__/
├── task-test-support.tsx
├── task-query.test.ts
├── task-stream.test.tsx
├── task-actions.test.tsx
└── task-repair.test.tsx
backend/src/test/java/com/exportflow/
├── exporttasks/{TaskManagementContractTest,TaskManagementUnitTest}.java
├── progress/TaskProgressIntegrationTest.java
└── worker/{TaskPauseMySqlTest,TaskPauseFileTest,AssemblyRepairUnitTest,AssemblyRepairMySqlTest,AssemblyRepairFileTest}.java
tests/backend/src/test/java/com/exportflow/testing/
├── TaskManagementFixture.java
└── ProgressRedisFixture.java
docs/validation/004-task-management/
├── verification.md
└── coverage-gaps.md
~~~

测试基础集的实际结果与未到达／尚未编写变体须分别评审；不能据此树宣称用例全部完成。具体门禁状态见[任务](tasks.md#阶段门禁)。测试配置的隔离属性为export-flow.progress-cache-prefix与export-flow.instance-id；实施后生产适配器必须使用相同配置语义，缓存所有派生索引也须限定在测试前缀并登记回收，不能写共享默认前缀。

## 实施目录补充

实际新增 `progress/application/ProgressConsumerReadiness.java`、`progress/api/TaskStreamWebConfiguration.java`、`progress/infrastructure/messaging/RabbitProgressListener.java`，分别负责订阅就绪、Servlet实际发送激活和手动ACK消费；发布器仅发布。测试增加 `ProgressDeliveryUnitTest`、`application/SseDeliveryUnitTest`、`TaskManagementMigrationTest`。`.env.example`及`scripts/run-project.ps1`增加实例身份、通知开关与Redis连接变量；不修改已有本地配置。完整逐文件内容及类型化签名见[实际函数清单](function-design.md)。
