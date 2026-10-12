# 审计场景补充对照

日期：2026-10-11，证据版本`0.4-test-03`。按负责人“先将上述测试场景补充完整”补充[原审计](coverage-audit.md)的34组场景和8组断言。归属[任务进度](../../../specs/002-order-management/tasks.md#测试进度与证据)，依据仍为原审计链接的产品、规格和契约；未新增产品标准或生产协议。

下面的“已编写”只表示具体输入、故障边界和断言已落入源码；生产类型尚不存在，不能据编译或名称宣称真实边界通过。困难故障由测试探针在获批端口前后注入，其余调用委托真实Bean；数据库竞争、消息保留、文件内容和进程kill使用真实资源。实际执行与装配限制见[结果](red-results.md)和[运行阻塞](coverage-gaps.md#运行阻塞)。

## 订单页面与HTTP

| 原项／TC | 源码 | 已补输入与断言 |
| --- | --- | --- |
| M-01／202、204、205 | [页面边界](../../../frontend/src/features/orders/__tests__/order-boundaries.test.tsx) | 真实组合表单提交枚举、香港时间和金额，只发一次UTC请求；逆序金额保留URL、原结果和已选行 |
| M-02／206、207 | [页面边界](../../../frontend/src/features/orders/__tests__/order-boundaries.test.tsx) | 分页/排序保留内存keyword；后退、前进、重新挂载清keyword/选择/配置并提示；混合URL保留合法项并规范化非法项 |
| M-03／211 | [页面边界](../../../frontend/src/features/orders/__tests__/order-boundaries.test.tsx)、[查询页面](../../../frontend/src/features/orders/__tests__/order-management.test.tsx) | keyword、page、pageSize、sort和POP分别取消旧signal；控制旧响应迟到，断言新参数与新结果；离页取消已有用例保留 |
| M-04／208、209、210 | [页面边界](../../../frontend/src/features/orders/__tests__/order-boundaries.test.tsx)、[导出状态](../../../frontend/src/features/orders/__tests__/order-export-state.test.tsx) | 取消后恢复九列；SELECTED拒绝保留配置/选择，纠正后新键；真实跨页两ID、部分字段原序；空/重复/未知字段不调用API |
| M-05／209、210 | [页面边界](../../../frontend/src/features/orders/__tests__/order-boundaries.test.tsx)、[导出状态](../../../frontend/src/features/orders/__tests__/order-export-state.test.tsx) | SUBMITTING/UNKNOWN冻结全部筛选、分页、排序、行/工具选择与字段；open/setFields/reset/close不得替换提交；UNKNOWN双击只一份原键请求，离页不声称后台取消 |
| M-06／208、210、226 | [页面边界](../../../frontend/src/features/orders/__tests__/order-boundaries.test.tsx)、[导出页面](../../../frontend/src/features/orders/__tests__/order-export.test.tsx)、[浏览器表](browser-checklist.md) | 初始焦点在对话框，键盘打开/取消/成功焦点恢复；加载、失效、空结果及未选择有直接可见原因；成功恢复编辑，无60秒轮询/SSE |
| M-07／210、211 | [API](../../../frontend/src/features/orders/__tests__/order-api.test.ts)、[页面边界](../../../frontend/src/features/orders/__tests__/order-boundaries.test.tsx) | 提前abort、坏/非JSON、422/503完整分类；非法分页/total/金额/日期、UUID/字段/数量拒绝；成功与失败均释放定时器及监听，页面UNKNOWN重试保留原键/载荷 |
| M-08／206、214、223 | [页面边界](../../../frontend/src/features/orders/__tests__/order-boundaries.test.tsx)、[运行安全](../../../backend/src/test/java/com/exportflow/RuntimeSecurityTest.java) | 捕获console与实际应用stdout日志，分别经过查询、创建和生成错误；不得记录合成客户、手机号、keyword、完整载荷、原键或物理根目录 |

## 查询、受理及持久身份

| 原项／TC | 源码 | 已补输入与断言 |
| --- | --- | --- |
| M-09／214、215 | [查询边界](../../../backend/src/test/java/com/exportflow/orders/OrderQueryBoundaryTest.java) | 251行真实20/50/100分页完整有序无重复；keyword三字段、反斜杠literal；时间/金额单边、最大合法金额、全部枚举、十个DTO字段字符串类型 |
| M-10／214、216 | [创建输入](../../../backend/src/test/java/com/exportflow/exporttasks/CreationInputBoundaryTest.java) | 缺失/短/长/非法key及长度端点；缺字段/null/畸形JSON/未知scope/非法ID；POST filter独立边界；文本长度、精确16MiB及多字节UTF8；拒绝后四张创建表不增加 |
| M-11／216、217、218 | [创建输入](../../../backend/src/test/java/com/exportflow/exporttasks/CreationInputBoundaryTest.java) | SELECTED真实去重关联、FILTERED无关联；关联及幂等插入故障整体回滚；同键异摘要并发一个胜者；空可选条件规范化和订单号大小写冲突 |
| M-12／218、220、228 | [异步受理](../../../backend/src/test/java/com/exportflow/exporttasks/CreationAsyncBoundaryTest.java)、[投递故障](../../../backend/src/test/java/com/exportflow/dispatch/OutboxFailureWindowsTest.java)、[API采样](../../../backend/src/test/java/com/exportflow/orders/ApiPerformanceTest.java) | 真实生成屏障未释放前HTTP已经提交返回；真实broker停止时仍受理，恢复执行原Outbox/event；功能测试有界返回与200样本P95分开判断 |
| M-13／213、215、217、234 | [运行安全](../../../backend/src/test/java/com/exportflow/RuntimeSecurityTest.java)、[下载边界](../../../backend/src/test/java/com/exportflow/worker/DownloadBoundaryTest.java) | 实际runtime只读orders且无DDL/TRUNCATE权，Flyway身份独立；非法计数/FK删除被DB拒绝；真实应用重启后原键结果和文件字节不变 |

## 投递、期限及broker

| 原项／TC | 源码 | 已补输入与断言 |
| --- | --- | --- |
| M-14／220、221 | [Spring correlation](../../../backend/src/test/java/com/exportflow/dispatch/PublisherCorrelationTest.java)、[投递故障](../../../backend/src/test/java/com/exportflow/dispatch/OutboxFailureWindowsTest.java)、[期限竞争](../../../backend/src/test/java/com/exportflow/dispatch/OutboxDeadlineRaceTest.java) | 同一CorrelationData归并return/ACK的两种观测顺序，nack/timeout/disconnect无完整证明；旧token成功/失败不改新领取，CLOSED拒绝迟到回调 |
| M-15／221 | [投递故障](../../../backend/src/test/java/com/exportflow/dispatch/OutboxFailureWindowsTest.java) | 真实子进程领取后发布前kill，持久attempt/crash退避跨重启；租约和退避均到期才重领；两dispatcher单条有效发布；共享故障按实际并发/预算停批，单条坏载荷不阻断好记录 |
| M-16／220、221 | [投递故障](../../../backend/src/test/java/com/exportflow/dispatch/OutboxFailureWindowsTest.java) | SENT真实提交后丢响应、回读不可用、提交前失败；先核对/凭证重试标记不立即再发布；DB不可读不伪写状态；稳定event、新token |
| M-17／221、222 | [期限竞争](../../../backend/src/test/java/com/exportflow/dispatch/OutboxDeadlineRaceTest.java)、[调度](../../../backend/src/test/java/com/exportflow/dispatch/DispatchSchedulingTest.java)、[原期限用例](../../../backend/src/test/java/com/exportflow/dispatch/OutboxTimeoutMySqlTest.java) | 屏障控制timeout与claim/SENT两种先后；未SENT的RUNNING/终态只关原Outbox；真实5秒独立扫描和启动先扫到期；有效租约撤销，SENT后再次扫描无补投 |
| M-18／220 | [broker策略](../../../backend/src/test/java/com/exportflow/dispatch/BrokerPolicyBoundaryTest.java) | 额外绑定、expiry/drop-head、autoDelete/exclusive/nondurable、operator TTL、management不可读；真实运行坏策略拒绝SENT并告警；容量解除后原event重投且载荷/属性精确 |

## 执行、Excel及下载

| 原项／TC | 源码 | 已补输入与断言 |
| --- | --- | --- |
| M-19／223、224、225 | [Worker故障](../../../backend/src/test/java/com/exportflow/worker/WorkerFailureWindowsTest.java) | DB不可读/claim提交不明不生成不ACK；READY/KNOWN_FAILURE/UNKNOWN；终态提交前失败/提交后响应丢失/回读不可用分别裁决；真实终态提交前无ACK，重复不再生成 |
| M-20／224、225 | [监督](../../../backend/src/test/java/com/exportflow/worker/ExecutionSupervisionTest.java)、[运行能力](../../../backend/src/test/java/com/exportflow/RuntimeCapabilityTest.java) | 5秒PENDING监测不误关正常RUNNING；heartbeat STALE/UNKNOWN停guard；保持正常心跳跨越真实5分钟单调时间的attempt边界与15秒停机边界；阻塞线程不提前释放owner；真实broker配置600000与5秒认领阈值分开，停机后不认领新消息 |
| M-21／224、235 | [持久权利](../../../backend/src/test/java/com/exportflow/worker/DurabilityGuardTest.java)、[Worker故障](../../../backend/src/test/java/com/exportflow/worker/WorkerFailureWindowsTest.java)、[运行能力](../../../backend/src/test/java/com/exportflow/RuntimeCapabilityTest.java) | 旧owner/fence不能续租/块/进度/READY/成功/失败；同owner旧checkpointVersion拒绝且记录不变；正常续租；本地lease时间过去/未来不能绕过DB权威时间 |
| M-22／213、223、225 | [运行能力](../../../backend/src/test/java/com/exportflow/RuntimeCapabilityTest.java)、[Worker故障](../../../backend/src/test/java/com/exportflow/worker/WorkerFailureWindowsTest.java)、[坏消息隔离](../../../backend/src/test/java/com/exportflow/worker/BadCommandIsolationTest.java) | 不可用生成器、真实无效文件根和原子移动能力故障拒绝启动、不认领；真实缺字段/错误type/超16KiB/不存在任务停止消费槽、告警，原消息重交付可取回，无立即循环requeue |
| M-23／230、233 | [Excel边界](../../../backend/src/test/java/com/exportflow/worker/ExcelBoundaryTest.java)、[内容](../../../backend/src/test/java/com/exportflow/worker/ExcelContentTest.java)、[校验边界](../../../backend/src/test/java/com/exportflow/worker/ExcelValidationBoundaryTest.java) | 32767字符/控制字符/最大金额/负号原文；五状态×四渠道；UTF8行1MiB和块16MiB预算；坏ZIP/表头/列数/行数/公式/数值类型/额外sheet均拒绝 |
| M-24／230、233、235 | [Excel边界](../../../backend/src/test/java/com/exportflow/worker/ExcelBoundaryTest.java)、[校验边界](../../../backend/src/test/java/com/exportflow/worker/ExcelValidationBoundaryTest.java) | 2501 SELECTED真实关联keyset批读，跨时间无重漏；每次返回无活动事务；受理后源条数变化安全失败；文件IO和下载流探针检查无长事务 |
| M-25／230、231、236 | [Excel边界](../../../backend/src/test/java/com/exportflow/worker/ExcelBoundaryTest.java)、[校验边界](../../../backend/src/test/java/com/exportflow/worker/ExcelValidationBoundaryTest.java)、[清理竞争](../../../backend/src/test/java/com/exportflow/worker/ArtifactCleanupConcurrencyTest.java) | 1000行真实sheet压缩临时文件包含首尾行、窗口释放；flush/guard/output/workbook close/dispose、存储满、force/move故障；不READY且保护块；成功/失败scratch释放，真实进程停止及grace后才清崩溃scratch |
| M-26／232、235 | [Excel边界](../../../backend/src/test/java/com/exportflow/worker/ExcelBoundaryTest.java)、[持久权利](../../../backend/src/test/java/com/exportflow/worker/DurabilityGuardTest.java)、[原进度](../../../backend/src/test/java/com/exportflow/worker/ExportProgressMySqlTest.java) | 内存行未登记不增进度；收集完成仍99；失败持久高水位/错误/结束时间；尾块损坏重建保留高水位并再次读取可信游标之后数据，成功才100 |
| M-27／234 | [下载边界](../../../backend/src/test/java/com/exportflow/worker/DownloadBoundaryTest.java) | RUNNING/FAILED409、DB503、权限/大小500安全响应；路径穿越/绝对owner、Windows junction或符号链接拒绝；真实HTTP RST及句柄写错自动关闭输入、不关框架output；流无事务，成功结果不改 |

## 恢复与清理

| 原项／TC | 源码 | 已补输入与断言 |
| --- | --- | --- |
| M-28／235 | [恢复窗口](../../../backend/src/test/java/com/exportflow/worker/RecoveryFailureWindowsTest.java)、[持久权利](../../../backend/src/test/java/com/exportflow/worker/DurabilityGuardTest.java) | 块落盘/登记前后、装配前及第二块读取的装配中、publish后、READY后、成功后ACK前真实kill；块/READY提交响应丢失核对；同任务原命令、可信块/原READY证明、唯一ADOPTED结果 |
| M-29／235 | [恢复窗口](../../../backend/src/test/java/com/exportflow/worker/RecoveryFailureWindowsTest.java)、[持久权利](../../../backend/src/test/java/com/exportflow/worker/DurabilityGuardTest.java) | SHA/大小/序号/版本/配置/游标/尾截断/混代破坏；暂不可读UNKNOWN保留引用；无安全重做证明不得换代；READY与实际SHA/大小/行数/配置不符不得采用 |
| M-30／224、235 | [恢复窗口](../../../backend/src/test/java/com/exportflow/worker/RecoveryFailureWindowsTest.java)、[持久权利](../../../backend/src/test/java/com/exportflow/worker/DurabilityGuardTest.java)、[Worker故障](../../../backend/src/test/java/com/exportflow/worker/WorkerFailureWindowsTest.java) | 并发接管仅胜者扣预算；应用重启保留次数/退避；耗尽通过真实listener在FAILED/安全错误/高水位/结束时间提交后ACK；失权者迟到错误不能覆盖新owner |
| M-31／236 | [清理竞争](../../../backend/src/test/java/com/exportflow/worker/ArtifactCleanupConcurrencyTest.java)、[原成功引用保护](../../../backend/src/test/java/com/exportflow/worker/ArtifactCleanupRaceTest.java) | MySQL真实行锁等待后竞争采用/清理；active attempt、READY、RUNNING块、成功引用保护；过grace孤儿先登记候选再许可，批上限20、年轻/DB未知不删、重复安全；cleanup胜者阻止迟到READY、旧token不能删 |

## 验证程序与断言补强

| 原项／TC | 已落地程序或补强 | 执行状态 |
| --- | --- | --- |
| M-32／227、228 | [ApiPerformanceTest](../../../backend/src/test/java/com/exportflow/orders/ApiPerformanceTest.java)：固定seed110000、各场景30预热/200原样本、nearest-rank P95、较大SELECTED及真实MQ停止；正常调度/消费开销启用 | 默认不运行；显式启用仍因生产入口不存在受阻，未产生延迟结论 |
| M-33／230、231 | [ExcelPerformanceTest](../../../backend/src/test/java/com/exportflow/worker/ExcelPerformanceTest.java)：110000/200000九列分别5次串行，生成动作至成功提交、另记等待、50ms堆采样/scratch/原始JSON；[独立512MB](../../../backend/src/test/java/com/exportflow/worker/ExcelResourceTest.java)及[子进程采样](../../../tests/backend/src/test/java/com/exportflow/testing/WorkerSampledMain.java) | 正常dispatcher/监督/consumer启用；等待真实实现，未产生耗时或堆达标结论 |
| M-34／226、234 | [Chrome/Edge逐步表](browser-checklist.md)：14项操作/结果/证据栏和真实版本/视口/环境记录 | 用例已编写，未执行，无截图或浏览器通过结论 |
| A-01 | 页面边界逐一找九列/分页/总数，查询模型同时要求两个“未知” | 源码已补；执行阻塞见报告 |
| A-02 | 页面边界真实跨页选取两个ID；SELECTED拒绝检查已选行及重开字段 | 源码已补；执行阻塞见报告 |
| A-03 | 导出状态检查scope/filter/orderIds/fields冻结与突变抛错，实际调用open/setFields | 源码已补；导入阻塞 |
| A-04 | 原期限测试真实reserve并设置未来lease，超时撤token；SENT后再次dispatcher扫描且实际队列只一条 | 源码已补；生产入口阻塞 |
| A-05 | broker策略精确载荷白名单/messageId/contentType/persistence/event；坏消息真实容器停槽、告警与原字节重交付 | 源码已补；生产入口阻塞 |
| A-06 | 实际生成能力/文件根/atomic move启动拒绝；真实listener容器手动ACK/并发/prefetch、默认测试角色停止状态 | 源码已补；生产入口阻塞 |
| A-07 | 下载边界由被测writeTo自动关闭输入、真实HTTP断连；helper核对安全ASCII/准确UTF8时间文件名；内容要求单sheet名“订单”及每个单元格STRING | 源码已补；生产入口阻塞 |
| A-08 | 真实行锁下READY采用/清理竞争及RUNNING块保护；耗尽由listener验证错误/高水位/结束时间且ACK在提交后 | 源码已补；生产入口阻塞 |

## 完成口径与后续执行

34组场景与8组断言均有上述源码或逐步手工用例对应，不再将它们记作“尚未编写”。这不是业务覆盖率或验收通过结论；G-01至G-03仍须评审。生产公开类型、Spring生命周期、RabbitTemplate重载、端口代理、POI内部临时文件探针和本机文件故障装配均须在正式实现后运行核实，禁止以测试替身补一个能通过的生产实现。

测试先行阶段的审批记录不因本表改变；本次没有生成生产入口、迁移或业务实现。复现命令见[测试设施](../../../tests/README.md)；真实性能报告、实际Excel、浏览器截图和绿灯证据在对应运行完成后才登记。
