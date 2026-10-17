# 任务规格审查与修订记录

**日期**：2026-10-17　**审查对象**：004版本0.1→0.2及0.3需求增量。  
**结论**：原稿存在暂停回执、监督器、SSE空闲同步及事务快照等实质缺口，已修正文档；0.3及测试阶段均已于2026-10-17获批，生产实施已授权。以下原审查发现保留历史语境，当前状态见[审批记录](tasks.md#审批记录)与[实际证据](../../docs/validation/004-task-management/verification.md)。

关联：[规格](spec.md)、[计划](plan.md)、[任务与门禁](tasks.md)、[数据模型](data-model.md)、[函数清单](function-design.md)、[HTTP/SSE](contracts/http-sse.md)、[执行协议](contracts/execution-control.md)、[OpenAPI](contracts/openapi.yaml)。

## 审查依据

按[项目入口](../../AGENTS.md)、[宪法](../../.specify/memory/constitution.md)、[规格规则](../../docs/rules/specification.md)、[测试规则](../../docs/rules/testing.md)及前后端、构建、脚本、容器、命名规则审查。需求依据为[产品文档](../../docs/product/order-export-prd.md)、[唯一验收基线](../../docs/product/acceptance-criteria.md)和[待确认事项](../../docs/decisions-pending.md)。

架构核对覆盖已接受ADR中的分层、数据库、原命令保留、文件/执行恢复和[通知决策](../../docs/adr/0009-engineering-runtime-and-progress-notification.md)，尤其[执行权ADR](../../docs/adr/0008-worker-execution-lease-and-recovery.md)；同时核对002/003规格的创建、Worker、Excel、断点、文件、错误协议及现有源码。候选[暂停ADR](../../docs/adr/0010-task-pause-control-barrier.md)不作为已接受决策。

## 发现与修正

| 编号／优先级 | 触发条件与后果 | 依据 | 0.2修正及验证计划 |
| --- | --- | --- | --- |
| R-01／P1 | 用户暂停使guard=false，原handler在处理生成结果前HOLD，PAUSE_STOPPED无法提交，任务可能停在PAUSING | [handler](../../backend/src/main/java/com/exportflow/worker/application/ExportCommandHandler.java) | 停止回执先分流；补generate返回与complete之间的暂停竞态，只有资源关闭证明才确认；TC-404/405 |
| R-02／P1 | heartbeat及checkAttempts均对续租失败close，暂停delivery可能在回执前失效 | [监督器](../../backend/src/main/java/com/exportflow/worker/application/ExecutionSupervisor.java) | 推进能力与通道有效性分开；USER_PAUSE保留delivery；补start/checkClaims/checkAttempts/close/finish；TC-404/405 |
| R-03／P1 | 暂停对账没有启动/调度/扫描端口；用control失效当无执行事实会过早确认 | [执行仓储](../../backend/src/main/java/com/exportflow/worker/infrastructure/persistence/MyBatisTaskExecutionRepository.java)及[执行ADR](../../docs/adr/0008-worker-execution-lease-and-recovery.md) | 启动及原周期调用，候选只读扫描后锁内裁决；无回执须核对原lease_until并推进fence；TC-405验证非计时入口，到期分支EX-002 |
| R-04／P1 | 多条count/page/activity查询在READ COMMITTED下可能撕裂；原稿改RR与已接受决策冲突 | [ADR-0009](../../docs/adr/0009-engineering-runtime-and-progress-notification.md#本地工程与测试) | 保留READ COMMITTED，单条CTE SELECT含空页元信息；TC-401/406 |
| R-05／P1 | 最后终态／PAUSED通知尚在dispatcher中，服务端先idle，前端停在RUNNING且不再重连 | [SSE决策](../../docs/adr/0009-engineering-runtime-and-progress-notification.md#后续sse与redis方向) | 接受idle关闭旧连接后一次最终权威快照，无活动保持空闲；补通知未到达场景TC-403/411 |
| R-06／P1 | 仅向内存队列enqueue就ACK，进程崩溃丢本地广播；无界慢连接堆积；同实例ID竞争消费漏通知 | [通知ADR](../../docs/adr/0009-engineering-runtime-and-progress-notification.md#后续sse与redis方向)及[后端规则](../../docs/rules/backend.md) | 等待实际发送结果再ACK，失败保留；有界队列溢出关闭；durable独立队列+exclusive consumer防重复ID；TC-406/411，无压力测量 |
| R-07／P2 | Markdown契约未满足OpenAPI要求；PauseResult与HTTP错误的outcome混用 | [API规则](../../docs/rules/backend.md#api-与数据约束) | 新增OpenAPI 3.1；200只APPLIED/REPLAY，409 REJECTED，503 UNKNOWN；创建/下载复用原契约；TC-401/407/408 |
| R-08／P1 | 原ExportTask/SQL record/列列表不含controlVersion，设计实施时可能默认为0丢失屏障 | [领域任务](../../backend/src/main/java/com/exportflow/exporttasks/domain/ExportTask.java)、[记录映射](../../backend/src/main/java/com/exportflow/exporttasks/infrastructure/persistence/ExportTaskPersistenceMapper.java)、[SQL](../../backend/src/main/resources/mappers/ExportTaskMapper.xml) | 补四个受影响文件、类型与函数，查询/通知独立持久映射，不跨层混用DTO；补mapper精确签名；TC-404/405 |
| R-09／P2 | 非成功file_name为空导致展示名/搜索口径不一致；未知状态与严格Zod枚举冲突；列表total精度未定义 | [产品文件名与搜索](../../docs/product/order-export-prd.md)、[现有摘要映射](../../backend/src/main/java/com/exportflow/exporttasks/infrastructure/persistence/ExportTaskPersistenceMapper.java) | 同一香港时间展示名称参与搜索，LIKE字面转义；未知状态禁动作；total/版本保精度及页码上界；TC-401/407/408 |
| R-10／P2 | 多项验收映射的测试任务只有T-002/等残缺值；缺口未标责任与审批；测试命令阶段不清 | [测试规则](../../docs/rules/testing.md)及[原任务表](tasks.md) | 补全28项双向任务映射、缺口责任/依据、分阶段命令与精确旧回归选择；编译阻塞不冒充红灯；迁移空库/V4验证纳入实施后；不预先勾选 |
| R-11／P2 | V5未给存量任务登记通知；PAUSED可能被误作终态清理通知水位；旧操作重放旧PAUSING覆盖已见PAUSED | [通知/永久记录原则](../../docs/adr/0009-engineering-runtime-and-progress-notification.md)及[模型](data-model.md) | 停写迁移中初始化存量通知，PAUSED保留水位；幂等事实与当前摘要分开；TC-406/407 |

优先级表示原稿缺口影响，表中修正均已写入0.2设计；不表示已有运行证明。具体验收定义继续只在产品基线，以上为设计失效分析和用例细化。

## 规格审查时的审批与验证边界（历史）

本次没有运行基础设施、性能、超时、延时或业务测试，没有新增生产类、迁移或测试代码。文档结构/链接/OpenAPI引用检查只属于纯文档验证，结果见[审计报告](../../artifacts/004-task-management/document-review-check.json)。

性能与计时验证按负责人本会话指令免验，不用虚拟时钟补测；项目级规则草案仍待审批。候选发送队列上限256、暂停消费确认与崩溃隔离语义需随规格/候选ADR审查。两次阶段审批未通过；测试阶段若有接口/类型加载阻塞，必须如实记录覆盖缺口供负责人评审，不能先写生产空壳。

原0.2范围为列表/SSE/暂停；0.3新增装配自动修复，增删改、失败后用户手动重试、用户继续均预留。外部设备在无活动快照之后新建任务仍无法自动唤醒，不增加轮询。到期崩溃对账保留设计但未测，不宣称任意阻塞线程已物理退出或本期具有暂停时长保证。

## 0.3需求增量核对（2026-10-17）

| 问题／冲突 | 本次设计修订 | 追踪 |
| --- | --- | --- |
| 校验后重算与显示高水位冲突 | 保留历史指标，新增持久可信计数；版本单调而进度可下降，缓存／SSE／HTTP同字段 | [进度增量](../../docs/product/acceptance-criteria.md#ac-090)；TC-416 |
| 坏块后健康块不能因连续前缀限制被丢弃 | 分段唯一选择，保留任意健康块，替换仅坏段；原收集游标与非连续可信量分离 | [修复ADR](../../docs/adr/0011-selective-block-repair-and-progress.md)；TC-415 |
| 原块三元唯一键阻止同代次历史替换 | V6改为普通索引，分段PK／selection CAS裁决，旧查询改读有效选择，禁止旧Worker混跑 | [模型](data-model.md#选择性重建模型)；TC-414/415 |
| 文件无登记或登记缺失时不能凭磁盘继续 | 独立不可变分段清单与封存摘要；基线缺失／源变化明确失败，数据库不可读UNKNOWN | [异常矩阵](contracts/assembly-repair.md#数据库记录缺失的恢复依据)；TC-414/415 |
| POI工作文件并无可恢复断点 | 明确只复用可信数据块，新Workbook重放；仅候选坏不扣减健康行数 | [生命周期](contracts/assembly-repair.md#哪些文件可以复用)；TC-415/416 |
| 自动修复不能无限循环或绕过暂停 | 同任务持久候选3轮预算，原owner/fence/control，停止关闭后走原回执；不自动继续PAUSED | [修复控制](contracts/assembly-repair.md#自动重试事务和并发)；TC-415 |
| 黄色提示不能只在本地短暂显示 | 持久修复状态贯穿重装配，hover/focus文案，暂停／终态／刷新生命周期 | [提示](../../docs/product/acceptance-criteria.md#ac-091)；TC-417 |

上述内容是规格修订，无生产或测试实施证据；旧版本机械核对统计不能代表0.3，当前文档检查报告单独刷新。需求确认不等于候选ADR接受或两次开发门禁批准。

## 实施核对（2026-10-17）

两次审批已取得，当前实现及函数清单已同步；原表描述规格审查时风险与计划，不作为运行结论。实施修正包括Servlet初始化后再发送SSE、发布与消费入口分离、缓存故障回源、相同稳定ID拒绝、legacy可信量清零、READY双重完整可信检查、修复历史终态收口，以及再次CHECKING保留重建提示。暂停回执为activity锁串行保护下只读／追加，不取得独立回执行锁，插入与状态及通知同事务；实际锁序见数据模型。红灯、夹具错误和最终门禁见[验证记录](../../docs/validation/004-task-management/verification.md)。
