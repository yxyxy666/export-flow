# 测试用例完整性审计

本页保留补充前的审计基线。**最新状态：34组场景及8组断言已补充源码或手工用例，逐项见[0.4-test-03对照](scenario-completion.md)；实际运行阻塞与结果见[执行报告](red-results.md)。** 下文“尚无/不足/未落地”均指补充前状态，不能继续据此判当前未编写。

日期：2026-10-11。审计对象为规格0.4及证据版本`0.4-test-02`对应的当前测试源码，归属[任务进度](../../../specs/002-order-management/tasks.md#测试进度与证据)。本次只检查源码和文档，未新增测试、未重跑业务测试、未修改生产实现。

## 判断方法与范围

逐项对照[产品验收基线](../../product/acceptance-criteria.md)、[HTTP契约](../../../specs/002-order-management/contracts/http-api.md)、[消息契约](../../../specs/002-order-management/contracts/export-command.md)、[Worker契约](../../../specs/002-order-management/contracts/worker-boundary.md)、[数据模型](../../../specs/002-order-management/data-model.md)、[Excel设计](../../../specs/002-order-management/excel-design.md)与[测试规则](../../rules/testing.md)，检查7份前端功能/导航测试、19份后端业务测试及测试装配。以实际输入、故障注入和断言判定覆盖，文件名、测试名、注释中的标准链接和TC映射均不能单独证明行为已覆盖。

下面的M项是**尚无对应子场景用例**，A项是**已有相关用例，但断言或装配不足**；各行是一组待补场景，不等于一个测试方法。P1优先补数据丢失、重复执行、错误终态和文件误删风险；P2补业务边界、交互和产品度量。全部仍由实施贡献者负责补齐，没有已批准免测项；现有运行阻塞另见[覆盖缺口](coverage-gaps.md#运行阻塞)，不混算为未编写。

## 尚无用例：订单页面与创建交互

依据：[HTTP状态与请求竞争](../../../specs/002-order-management/contracts/http-api.md#url编辑表单和请求竞争)、[冻结状态机](../../../specs/002-order-management/contracts/http-api.md#冻结提交状态机)、[前端规则](../../rules/frontend.md)。相关源码：[页面测试](../../../frontend/src/features/orders/__tests__/order-management.test.tsx)、[查询模型测试](../../../frontend/src/features/orders/__tests__/order-query.test.ts)、[导出页面测试](../../../frontend/src/features/orders/__tests__/order-export.test.tsx)、[导出状态测试](../../../frontend/src/features/orders/__tests__/order-export-state.test.tsx)、[API测试](../../../frontend/src/features/orders/__tests__/order-api.test.ts)。

| 项目／优先级 | 待补子场景与可判定结果 | 追踪 |
| --- | --- | --- |
| M-01／P2 | 状态、渠道、香港时间和金额一起经真实表单提交；请求参数正确且只发一次。校验失败保留URL、已选集合及旧结果，而非只检查未请求与aria-invalid | TC-202/204/205；[`AC-002`](../../product/acceptance-criteria.md#ac-002)、[`AC-005`](../../product/acceptance-criteria.md#ac-005) |
| M-02／P2 | 刷新、前进、后退恢复表单时清除选择及未提交导出配置并提示关键字重新输入；普通分页/排序写URL时保留内存关键字；混合合法与非法URL只规范化非法项并提示 | TC-206/207；[`AC-065`](../../product/acceptance-criteria.md#ac-065) |
| M-03／P1 | 新查询、POP导航取消旧请求；分别变更keyword、页码、页大小、排序后请求及缓存不串用。现有离页取消和一次新旧查询乱序场景不能覆盖这些组合 | TC-211；[请求竞争契约](../../../specs/002-order-management/contracts/http-api.md#url编辑表单和请求竞争) |
| M-04／P2 | 取消后重新打开恢复九列默认值；明确拒绝后重开保留同次配置、修改后提交使用新键；选择部分字段按页面列顺序发送且无重复；非法字段配置不发送POST | TC-208/209/210；[`AC-060`](../../product/acceptance-criteria.md#ac-060)、[`AC-030`](../../product/acceptance-criteria.md#ac-030) |
| M-05／P1 | SUBMITTING和UNKNOWN时分页、排序、行选择、全选/反选及筛选输入全部冻结；尝试open新范围、setFields或连续双击“重试确认”不能破坏旧提交或并发补发；离页告知结果未知而不宣称后台取消 | TC-209/210；[`AC-051`](../../product/acceptance-criteria.md#ac-051)、[`AC-072`](../../product/acceptance-criteria.md#ac-072) |
| M-06／P2 | 对话框初始焦点、键盘操作、成功后的焦点恢复；加载/失效结果禁用原因直接可见；创建成功恢复全部编辑控件。取消恢复焦点已有用例，不能替代成功路径 | TC-208/210/226；[`AC-007`](../../product/acceptance-criteria.md#ac-007)、[`AC-072`](../../product/acceptance-criteria.md#ac-072) |
| M-07／P2 | caller已提前abort、非JSON/坏JSON响应和HTTP错误经requestJson到页面完整分类；查询成功协议的非法分页/total/金额/日期及创建成功协议的非法UUID/重复字段/超上限数量被拒绝；失败路径也释放定时器/监听 | TC-210/211；[HTTP契约](../../../specs/002-order-management/contracts/http-api.md)、[数据模型](../../../specs/002-order-management/data-model.md#通用规则) |
| M-08／P2 | 观测console及访问/诊断日志，验证关键字、客户值、完整载荷和幂等键不泄露；当前URL和空长期存储断言没有覆盖日志出口 | TC-206/214/223；[`AC-065`](../../product/acceptance-criteria.md#ac-065)、[前端规则](../../rules/frontend.md)、[Excel诊断约束](../../../specs/002-order-management/excel-design.md#异常执行与竞态裁决) |

## 尚无用例：订单查询、输入和创建事务

相关源码：[查询](../../../backend/src/test/java/com/exportflow/orders/OrderQueryMySqlTest.java)、[查询协议](../../../backend/src/test/java/com/exportflow/orders/OrderApiContractTest.java)、[创建协议](../../../backend/src/test/java/com/exportflow/exporttasks/ExportCreationContractTest.java)、[幂等](../../../backend/src/test/java/com/exportflow/exporttasks/IdempotencyMySqlTest.java)、[创建事务](../../../backend/src/test/java/com/exportflow/exporttasks/CreateTransactionMySqlTest.java)。

| 项目／优先级 | 待补子场景与可判定结果 | 追踪 |
| --- | --- | --- |
| M-09／P2 | 超过一页的真实MySQL数据验证20/50/100分页无重漏；keyword分别命中订单号、姓名、手机号，含反斜杠等literal输入；时间/金额各单边、最大合法金额、各枚举筛选与全字段DTO字符串类型 | TC-214/215；[`AC-004`](../../product/acceptance-criteria.md#ac-004)、[`AC-022`](../../product/acceptance-criteria.md#ac-022)、[`AC-023`](../../product/acceptance-criteria.md#ac-023)、[`AC-035`](../../product/acceptance-criteria.md#ac-035) |
| M-10／P1 | 缺失/过短/过长/非法字符幂等键；缺字段、null、畸形JSON、未知scope、非法或数值订单ID；POST内filter独立拒绝非法枚举/时间/金额/未知字段；字符串长度及16MiB精确UTF8字节边界。每次明确拒绝核对全部创建表无残留 | TC-214/216；[`AC-061`](../../product/acceptance-criteria.md#ac-061)、[OpenAPI](../../../specs/002-order-management/contracts/openapi.yaml)、[HTTP预算](../../../specs/002-order-management/contracts/http-api.md#已定协议参数) |
| M-11／P1 | SELECTED关联ID完整去重固定、FILTERED不写关联表；插入选择关联/幂等登记失败整体回滚；不同摘要同键并发仍只保留胜出的一个任务；空条件删除/订单号大小写敏感的规范化等价与冲突边界 | TC-216/217/218；[`AC-050`](../../product/acceptance-criteria.md#ac-050)、[`AC-064`](../../product/acceptance-criteria.md#ac-064)、[规范化与范围冻结](../../../specs/002-order-management/data-model.md#规范化与范围冻结) |
| M-12／P1 | 实际MQ不可用或生成器被屏障阻塞时创建仍已提交并及时响应；恢复后原任务原Outbox继续执行。当前返回PENDING不证明HTTP未等待MQ/生成，broker重启保留消息也不证明发布故障后重试恢复 | TC-218/220/228；[`AC-011`](../../product/acceptance-criteria.md#ac-011)、[`SC-010`](../../product/acceptance-criteria.md#sc-010) |
| M-13／P2 | 真实应用runtime账户对orders写入及DDL被拒绝，独立迁移/seed身份可执行自己的职责；数据库计数约束、永久引用禁止级联删除及服务重启后原幂等结果/文件仍可读取 | TC-213/215/217/234；[数据模型约束](../../../specs/002-order-management/data-model.md#持久化关系设计逻辑表)、[`AC-018`](../../product/acceptance-criteria.md#ac-018) |

## 尚无用例：Outbox和RabbitMQ

相关源码：[Publisher](../../../backend/src/test/java/com/exportflow/dispatch/RabbitMqPublisherTest.java)、[投递](../../../backend/src/test/java/com/exportflow/dispatch/OutboxDispatchMySqlTest.java)、[到期](../../../backend/src/test/java/com/exportflow/dispatch/OutboxTimeoutMySqlTest.java)。依据：[消息契约](../../../specs/002-order-management/contracts/export-command.md)、[持久状态裁决](../../../specs/002-order-management/data-model.md#状态与数据库裁决)。

| 项目／优先级 | 待补子场景与可判定结果 | 追踪 |
| --- | --- | --- |
| M-14／P1 | confirm ACK与mandatory return在同一correlation归并，不因回调顺序误标SENT；confirm nack/超时/连接中断均保持可核对；旧token迟到成功和失败不能改新领取或CLOSED记录 | TC-220/221；[`AC-053`](../../product/acceptance-criteria.md#ac-053)、[`AC-056`](../../product/acceptance-criteria.md#ac-056) |
| M-15／P1 | 领取提交后、发布前强制退出：尝试历史和crashRetryAt已持久；重启不能绕过退避；两个dispatcher竞争仅一方获有效领取；共享MQ故障有界停批，单条序列化失败不阻断好记录 | TC-221；[`AC-054`](../../product/acceptance-criteria.md#ac-054)、[`AC-055`](../../product/acceptance-criteria.md#ac-055) |
| M-16／P1 | confirm成功后标SENT提交异常/回读不可用，优先核对且凭据有效时重试标记，不能立即再发布；DB故障不伪造状态；重试eventId保持而leaseToken更新 | TC-220/221；[`AC-053`](../../product/acceptance-criteria.md#ac-053)、[发布成功证据](../../../specs/002-order-management/contracts/export-command.md#发布成功证据) |
| M-17／P1 | deadline与claim/标SENT用屏障控制两种先后，锁内DB时间裁决；未SENT已RUNNING/终态只关闭原Outbox不再补投；真实启动优先扫到期及五秒独立扫描（父fixture移除了调度器） | TC-221/222；[`AC-052`](../../product/acceptance-criteria.md#ac-052)、[`AC-053`](../../product/acceptance-criteria.md#ac-053) |
| M-18／P1 | 错误额外目标绑定、queue expiry/drop-head/autoDelete/exclusive、operator policy及management不可读均不能当合规；坏策略运行时出现会阻止成功标记并告警；容量解除后原命令可重试 | TC-220；[`AC-056`](../../product/acceptance-criteria.md#ac-056)、[拓扑契约](../../../specs/002-order-management/contracts/export-command.md#消息) |

## 尚无用例：Worker执行权和确认

相关源码：[消息处理](../../../backend/src/test/java/com/exportflow/worker/ExportCommandHandlerTest.java)、[执行租约](../../../backend/src/test/java/com/exportflow/worker/ExecutionLeaseMySqlTest.java)、[生成能力](../../../backend/src/test/java/com/exportflow/worker/GeneratorBoundaryTest.java)、[运行配置](../../../backend/src/test/java/com/exportflow/RuntimeConfigurationTest.java)。依据：[Worker契约](../../../specs/002-order-management/contracts/worker-boundary.md)、[已定执行参数](../../../specs/002-order-management/excel-design.md#本期具体建议参数)。

| 项目／优先级 | 待补子场景与可判定结果 | 追踪 |
| --- | --- | --- |
| M-19／P1 | DB不可读、认领提交不明先核对，未确认权利不生成不ACK；READY/KNOWN_FAILURE/UNKNOWN各编排分支；终态提交不明回读后再ACK，不能用生成函数返回当终态证明 | TC-223/224/225；[`AC-049`](../../product/acceptance-criteria.md#ac-049)、[`AC-068`](../../product/acceptance-criteria.md#ac-068) |
| M-20／P1 | 5秒认领监督只处理未认领PENDING而不关闭正常RUNNING；5分钟attempt到期/心跳拒绝或UNKNOWN后停新读写；15秒停机顺序及未停止线程不提前释放执行权；broker ACK timeout与claim timeout独立 | TC-224/225；[执行权与异常恢复](../../../specs/002-order-management/contracts/worker-boundary.md#执行权与异常恢复) |
| M-21／P1 | 旧owner/fence不只不能续租，还不能写块、进度、READY或终态；同owner旧checkpointVersion乱序提交拒绝；正常心跳续租以及DB时间与宿主时钟偏差下仍正确 | TC-224/235；[`AC-068`](../../product/acceptance-criteria.md#ac-068)、[块提交契约](../../../specs/002-order-management/contracts/worker-boundary.md#断点产物与确认) |
| M-22／P1 | 存储/原子移动/生成能力缺失时启用消费者拒绝启动且不认领任务；任务不存在、命令缺字段/超16KiB/错误type等坏消息停止槽、受控关通道并告警、保留原消息且不立即循环重入 | TC-213/223/225；[生成能力边界](../../../specs/002-order-management/contracts/worker-boundary.md#实际执行链)、[未知消息处理](../../../specs/002-order-management/contracts/export-command.md#消费确认与未知消息) |

## 尚无用例：真实文件、进度和下载

相关源码：[Excel内容](../../../backend/src/test/java/com/exportflow/worker/ExcelContentTest.java)、[资源](../../../backend/src/test/java/com/exportflow/worker/ExcelResourceTest.java)、[进度](../../../backend/src/test/java/com/exportflow/worker/ExportProgressMySqlTest.java)、[下载](../../../backend/src/test/java/com/exportflow/worker/DownloadExportFileTest.java)。依据：[Excel设计](../../../specs/002-order-management/excel-design.md)。

| 项目／优先级 | 待补子场景与可判定结果 | 追踪 |
| --- | --- | --- |
| M-23／P2 | 超32767字符、非法控制字符、块每行/每块字节预算、最大精度金额、所有已知枚举及负号开头原文；无效值安全失败而非截断；损坏ZIP、错表头/列数/行数/公式单元格不能校验成功 | TC-230/233；[`AC-067`](../../product/acceptance-criteria.md#ac-067)、[内容与校验](../../../specs/002-order-management/excel-design.md#sxssf内容与资源) |
| M-24／P1 | 真实跨1000行的SELECTED关联批读与不同时间keyset不重漏；源条数与受理总数不符安全失败；批读后释放连接，长文件工作不持数据库事务 | TC-230/233/235；[`SC-007`](../../product/acceptance-criteria.md#sc-007)、[数据读取与块断点](../../../specs/002-order-management/excel-design.md#数据读取与块断点) |
| M-25／P1 | 每块末真实sheet临时文件出现已完成行和缓冲、行窗口有界；flush/force/close/dispose/磁盘满/原子move失败不登记READY或成功且保护可信块；正常及失败释放scratch，崩溃scratch只在确认attempt停止后清理 | TC-230/231/236；[`AC-071`](../../product/acceptance-criteria.md#ac-071) |
| M-26／P1 | 内存完成但块未登记不能增加进度；非成功最高99；失败及恢复重建保留processedHighWater，不以高水位作续写游标；成功才100，安全错误和结束时间同终态持久化 | TC-232/235；[`SC-005`](../../product/acceptance-criteria.md#sc-005)、[`AC-068`](../../product/acceptance-criteria.md#ac-068) |
| M-27／P1 | RUNNING/FAILED下载均409；DB不可读503、权限错误安全500、实际大小不符不能照常下载；路径穿越/符号链接/Windows reparse拒绝；真实HTTP客户端断连后控制器关闭句柄，流期间无事务/执行租约 | TC-234；[`AC-014`](../../product/acceptance-criteria.md#ac-014)、[同源下载契约](../../../specs/002-order-management/contracts/http-api.md#同源文件下载) |

## 尚无用例：恢复、就绪对账和清理

相关源码：[恢复](../../../backend/src/test/java/com/exportflow/worker/CheckpointRecoveryTest.java)、[清理](../../../backend/src/test/java/com/exportflow/worker/ArtifactCleanupRaceTest.java)、[kill屏障](../../../tests/backend/src/test/java/com/exportflow/testing/WorkerFaultBarrierMain.java)。依据：[断点与确认](../../../specs/002-order-management/contracts/worker-boundary.md#断点产物与确认)、[文件身份和清理](../../../specs/002-order-management/excel-design.md#文件身份就绪登记及下载)。

| 项目／优先级 | 待补子场景与可判定结果 | 追踪 |
| --- | --- | --- |
| M-28／P1 | 块落盘未登记、块登记提交不明、装配中、发布未READY、READY已登记未成功、成功提交不明、成功后ACK丢失各窗口；恢复保留同任务/原命令、复用可信块或READY、最终仅一个权威结果 | TC-235；[`AC-010`](../../product/acceptance-criteria.md#ac-010)、[`AC-068`](../../product/acceptance-criteria.md#ac-068)、[`AC-069`](../../product/acceptance-criteria.md#ac-069) |
| M-29／P1 | 块SHA/大小/序号/游标/版本/配置不符、尾块截断、不同代次混入；暂不可读归UNKNOWN且不破坏引用；无可信前缀仅在安全证明后新建代次；READY证明与实际文件不符不能采用 | TC-235；[`AC-068`](../../product/acceptance-criteria.md#ac-068)、[`AC-069`](../../product/acceptance-criteria.md#ac-069) |
| M-30／P1 | 并发接管失败不扣预算；重启后退避/次数不重置；预算耗尽或明确不可恢复由合法裁决权提交失败后ACK；失权者迟到错误不能覆写新owner结果 | TC-224/235；[`AC-068`](../../product/acceptance-criteria.md#ac-068) |
| M-31／P1 | 用真实行锁/屏障使READY采用与清理竞争；有效attempt、READY、RUNNING断点及成功引用分别保护；无引用过grace候选才删除、未到grace不删除、候选批量上限与重复清理安全；无DB登记磁盘残留先登记再裁决 | TC-236；[`AC-018`](../../product/acceptance-criteria.md#ac-018)、[`AC-069`](../../product/acceptance-criteria.md#ac-069) |

## 尚未落地的验证程序

这部分已有[任务级方案](../../../specs/002-order-management/tasks.md#sc验证映射)和[参考环境/采样规则](../../../specs/002-order-management/decision-record.md#参考环境及事实核对)，不能说完全没有测试设计；但当前没有相应可执行程序或逐步手工记录。脚本按既定T-030阶段落地，本次审计不提前生成生产阶段工具。

| 项目／优先级 | 待落地的验证 | 追踪 |
| --- | --- | --- |
| M-32／P2 | 查询和创建各场景预热30次/200样本、nearest-rank P95、完整原始样本和环境摘要；涵盖大SELECTED载荷及MQ隔离故障 | TC-227/228；[`SC-001`](../../product/acceptance-criteria.md#sc-001)、[`SC-002`](../../product/acceptance-criteria.md#sc-002) |
| M-33／P2 | 110000/200000九列分别5次串行全流程计时且每次达标；512MB进程堆峰值采样、scratch资源证据。现有全量准确性和512MB用例已存在，但不是这套性能/采样验证 | TC-230/231；[`SC-003`](../../product/acceptance-criteria.md#sc-003)、[`SC-004`](../../product/acceptance-criteria.md#sc-004) |
| M-34／P2 | Chrome/Edge真实版本、1280×720各区域与弹窗、键盘/焦点逐步检查及截图；直接浏览器URL下载可打开检查。已有原则性步骤，缺具体执行记录表与实际证据 | TC-226/234；[`SC-008`](../../product/acceptance-criteria.md#sc-008)、[quickstart](../../../specs/002-order-management/quickstart.md) |

TC-229的测试/类型检查/构建命令已在项目工具链和任务中定义；目前缺实现后的成功执行及报告，**不另算没有测试用例**。远程CI、前端任务详情/状态/下载/SSE、用户暂停与显式恢复、跨主机存储不属于本期缺测。

## 已有用例但不足以证明声明

| 项目 | 源码证据 | 不足及补强方向 |
| --- | --- | --- |
| A-01 页面区域与展示 | [order-management.test.tsx](../../../frontend/src/features/orders/__tests__/order-management.test.tsx)、[order-query.test.ts](../../../frontend/src/features/orders/__tests__/order-query.test.ts) | “四个区域”只找按钮/表格，没核对分页区域与九个业务列；未知status/channel只断言Object.values包含一个“未知”，无法同时证明两列回退。需逐区域/逐字段断言 |
| A-02 跨页导出与拒绝保留选择 | [order-export.test.tsx](../../../frontend/src/features/orders/__tests__/order-export.test.tsx) | 名称写“跨页ID”实际只选一页一行；“拒绝保留配置与选择”走FILTERED且无选择，只断言字段。需真实跨页选取后核对全部ID，以及带选择的拒绝路径 |
| A-03 深冻结与新范围保护 | [order-export-state.test.tsx](../../../frontend/src/features/orders/__tests__/order-export-state.test.tsx) | 深拷贝有断言，但Object.isFrozen只检查request顶层；“新范围”测试未调用open新范围，冻结也未测setFields。需嵌套scope/filter/orderIds/fields不可修改及所有破坏入口断言 |
| A-04 超时租约与无补投 | [OutboxTimeoutMySqlTest.java](../../../backend/src/test/java/com/exportflow/dispatch/OutboxTimeoutMySqlTest.java) | “DespiteLiveLease”实际未设置live lease；SENT等待测试只检查数据库状态，未再次运行dispatcher或观测消息数。需构造有效租约并断言撤销，以及扫描后无第二条发布 |
| A-05 消息完整性与停止槽 | [RabbitMqPublisherTest.java](../../../backend/src/test/java/com/exportflow/dispatch/RabbitMqPublisherTest.java)、[ExportCommandHandlerTest.java](../../../backend/src/test/java/com/exportflow/worker/ExportCommandHandlerTest.java) | Publisher只检查taskId/schemaVersion/deliveryMode，缺精确载荷白名单、messageId/contentType/稳定eventId；坏消息只查不ACK/不丢弃/无产物，没有证明停止槽、关通道、告警和无立即requeue |
| A-06 能力边界 | [GeneratorBoundaryTest.java](../../../backend/src/test/java/com/exportflow/worker/GeneratorBoundaryTest.java)、[RuntimeConfigurationTest.java](../../../backend/src/test/java/com/exportflow/RuntimeConfigurationTest.java) | 名称声称不可用生成器不能启用，但仅检查真实生成器isAvailable=true、唯一Bean和目录存在；consumer配置只检查环境值false，未证明容器未消费，也没覆盖不可用启动拒绝 |
| A-07 句柄关闭及文件格式 | [DownloadExportFileTest.java](../../../backend/src/test/java/com/exportflow/worker/DownloadExportFileTest.java)、[测试下载helper](../../../tests/backend/src/test/java/com/exportflow/testing/ProductionBackendFixture.java)、[ExcelContentTest.java](../../../backend/src/test/java/com/exportflow/worker/ExcelContentTest.java) | 写失败用例由测试finally主动close，无关闭后不可读/真实资源释放断言，不能证明实现自动关闭。下载头未核对ASCII fallback和解码后的准确文件名。工作表只数一个，未断言名称“订单”；DataFormatter文本相等和无公式不完全证明金额等单元格STRING类型 |
| A-08 清理竞态与耗尽ACK | [ArtifactCleanupRaceTest.java](../../../backend/src/test/java/com/exportflow/worker/ArtifactCleanupRaceTest.java)、[CheckpointRecoveryTest.java](../../../backend/src/test/java/com/exportflow/worker/CheckpointRecoveryTest.java) | “Race”只有成功后顺序清理，未竞争READY采用；断点保护也只在已成功任务下检查。预算耗尽只测FAILED/finished_at，没有错误码、实际进度及提交后ACK顺序 |

上述断言缺口分别归入原TC和原T-011至T-019任务，补强不另建产品标准。用例名称应在补齐前收窄到实际证明范围。

## 已编写但运行受阻，不列为无用例

- 前端HTTP十秒边界/取消、离页abort、乱序响应、跨页选择工具、成功留订单页、UNKNOWN同键核对、通知抛错仍保留CONFIRMED已有源码。
- 后端SeedOrders核对/回滚、同键并发及规范化、真实成功/投递超时后幂等重放、outbox插入失败/创建提交不明、MQ目标缺失/容量拒绝/重启/TTL拒绝已有源码。
- 110000/200000九列逐行SAX核对、独立512MB子进程、首块提交后kill恢复且保留原代次/块引用、成功文件缺失410已有源码。

补充前0.4-test-02运行是前端36个已收集用例中30个预期行为失败、6个通过，另4个套件导入阻塞；后端83个中4个环境检查通过、79个业务用例因入口缺失跳过。运行细节与限制见[实际结果](red-results.md)。未取得运行证据不意味着没写用例，有源码也不意味着本页所列遗漏已经覆盖。
