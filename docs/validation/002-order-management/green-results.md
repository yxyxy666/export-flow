# 历史自动化回归与质量证据

> 2026-10-12最终状态：负责人确认本轮剩余验证无需执行，性能已取消；原“待验／待补／未验证”为历史事实，不再是本轮待办。范围和日期见[免验确认](../../../specs/002-order-management/tasks.md#本轮免验确认)，免验不标实测通过。

> 2026-10-12状态更新：下文316项等通过统计属于旧数据方案的历史证据；当前已删除订单造数，直接读取负责人容器中的110000条订单。当前复验及200000规模数据缺口以[来源记录](provided-data.md)为准，不沿用历史通过结论。

当前功能结果：300通过、0失败、0错误，2条来源不足及2条默认关闭性能记录，见[逐类汇总](provided-results.md)。04受影响回归通过正式质量门禁；110000行Excel五次独立测量成功，API性能已按负责人要求取消，不判达标，见[收尾记录](provided-data.md#本轮收尾与性能取消)。以下旧批次保留用于历史追踪。

日期：2026-10-11。归属[任务](../../../specs/002-order-management/tasks.md#测试进度与证据)；review修复见[代码复查](code-review.md)，实际共享环境见[服务记录](shared-services.md)。历史红灯与被中断批次保留，不把其中部分结果写成整批通过。

## 已完成的自动化验证

- 前端：`frontend-review-green-03.log`，153/153通过；`frontend-review-typecheck.log`、`frontend-review-build.log`退出0。Vite体积提示仍保留。
- 后端：按测试类采用完整报告，318条记录中316条通过，失败0、错误0、默认关闭的性能套件记录2条。原始汇总为`shared-regression-results.json`，以下50个套件来自其明确报告路径；不声称一次完整长批次全部成功。
- 正式Wrapper最后门禁：`backend-shared-verify-04.log` BUILD SUCCESS，Unit9与Worker15通过，Spotless132文件、Checkstyle零violation、SpotBugs零BugInstance/零error。JaCoCo [HTML](../../../backend/target/site/jacoco/index.html)及[XML](../../../backend/target/site/jacoco/jacoco.xml)已生成，不以覆盖率百分比代替行为验收。
- 后端专项补验：`backend-shared-policy-green-02.log`24/24；`backend-shared-batch-green.log`27/27；受影响恢复窗口在`backend-shared-verify-02.log`有完整报告。

## 后端逐套件结果

| 套件 | 记录／通过／跳过 | 失败／错误 |
| --- | --- | --- |
| com.exportflow.dispatch.BrokerPolicyBoundaryTest | 9／9／0 | 0／0 |
| com.exportflow.dispatch.DispatchSchedulingTest | 2／2／0 | 0／0 |
| com.exportflow.dispatch.OutboxDeadlineRaceTest | 6／6／0 | 0／0 |
| com.exportflow.dispatch.OutboxDispatchMySqlTest | 1／1／0 | 0／0 |
| com.exportflow.dispatch.OutboxFailureWindowsTest | 9／9／0 | 0／0 |
| com.exportflow.dispatch.OutboxTimeoutMySqlTest | 4／4／0 | 0／0 |
| com.exportflow.dispatch.PublisherCorrelationTest | 5／5／0 | 0／0 |
| com.exportflow.dispatch.RabbitMqPublisherTest | 5／5／0 | 0／0 |
| com.exportflow.exporttasks.CreateTransactionMySqlTest | 4／4／0 | 0／0 |
| com.exportflow.exporttasks.CreationAsyncBoundaryTest | 2／2／0 | 0／0 |
| com.exportflow.exporttasks.CreationInputBoundaryTest | 37／37／0 | 0／0 |
| com.exportflow.exporttasks.CreationResultMySqlTest | 3／3／0 | 0／0 |
| com.exportflow.exporttasks.ExportCreationContractTest | 9／9／0 | 0／0 |
| com.exportflow.exporttasks.IdempotencyMySqlTest | 4／4／0 | 0／0 |
| com.exportflow.orders.ApiPerformanceTest | 1／0／1 | 0／0 |
| com.exportflow.orders.OrderApiContractTest | 14／14／0 | 0／0 |
| com.exportflow.orders.OrderQueryBoundaryTest | 22／22／0 | 0／0 |
| com.exportflow.orders.OrderQueryMySqlTest | 14／14／0 | 0／0 |
| com.exportflow.RuntimeCapabilityTest | 5／5／0 | 0／0 |
| com.exportflow.RuntimeConfigurationTest | 2／2／0 | 0／0 |
| com.exportflow.RuntimeIsolationUnitTest | 1／1／0 | 0／0 |
| com.exportflow.RuntimeMonitorUnitTest | 1／1／0 | 0／0 |
| com.exportflow.RuntimeSecurityTest | 5／5／0 | 0／0 |
| com.exportflow.testing.TestEnvironmentTest | 4／4／0 | 0／0 |
| com.exportflow.worker.ArtifactCleanupConcurrencyTest | 6／6／0 | 0／0 |
| com.exportflow.worker.ArtifactCleanupRaceTest | 1／1／0 | 0／0 |
| com.exportflow.worker.BadCommandIsolationTest | 4／4／0 | 0／0 |
| com.exportflow.worker.BlockResourceUnitTest | 1／1／0 | 0／0 |
| com.exportflow.worker.CheckpointInvariantTest | 1／1／0 | 0／0 |
| com.exportflow.worker.CheckpointRecoveryTest | 2／2／0 | 0／0 |
| com.exportflow.worker.DataCollectionGuardUnitTest | 1／1／0 | 0／0 |
| com.exportflow.worker.DeliveryIsolationUnitTest | 2／2／0 | 0／0 |
| com.exportflow.worker.DownloadBoundaryTest | 13／13／0 | 0／0 |
| com.exportflow.worker.DownloadExportFileTest | 3／3／0 | 0／0 |
| com.exportflow.worker.DurabilityGuardTest | 5／5／0 | 0／0 |
| com.exportflow.worker.ExcelBoundaryTest | 13／13／0 | 0／0 |
| com.exportflow.worker.ExcelContentTest | 24／24／0 | 0／0 |
| com.exportflow.worker.ExcelPerformanceTest | 1／0／1 | 0／0 |
| com.exportflow.worker.ExcelResourceTest | 1／1／0 | 0／0 |
| com.exportflow.worker.ExcelValidationBoundaryTest | 13／13／0 | 0／0 |
| com.exportflow.worker.ExecutionLeaseMySqlTest | 2／2／0 | 0／0 |
| com.exportflow.worker.ExecutionSupervisionTest | 7／7／0 | 0／0 |
| com.exportflow.worker.ExportCommandHandlerTest | 5／5／0 | 0／0 |
| com.exportflow.worker.ExportProgressMySqlTest | 1／1／0 | 0／0 |
| com.exportflow.worker.GeneratorBoundaryTest | 1／1／0 | 0／0 |
| com.exportflow.worker.RecoveryFailureWindowsTest | 23／23／0 | 0／0 |
| com.exportflow.worker.ShutdownOrderUnitTest | 2／2／0 | 0／0 |
| com.exportflow.worker.WorkbookLifecycleTest | 1／1／0 | 0／0 |
| SeedTransactionUnitTest | 1／1／0 | 0／0 |
| com.exportflow.worker.WorkerFailureWindowsTest | 15／15／0 | 0／0 |

## 验收边界

110000/200000行九列逐行准确性、独立512MiB进程无OOM、五分钟执行监督、真实kill/恢复/产物采用与清理竞态已取得自动化证据；见以上套件报告。[文件准确性](../../product/acceptance-criteria.md#sc-007)与[独立堆稳定性](../../product/acceptance-criteria.md#sc-004)已有实测，[查询性能](../../product/acceptance-criteria.md#sc-001)、[创建性能](../../product/acceptance-criteria.md#sc-002)与[五次文件性能](../../product/acceptance-criteria.md#sc-003)按独立性能批次判定，不能以回归耗时代替。

[浏览器用例](browser-checklist.md)因UI工具中止尚无目标Chrome/Edge手工证据；共享broker进程重启持久性未执行，连接断开重交付不能替代进程重启。前端任务状态展示、SSE及下载界面留后续规格。本记录不表示整个规格验收通过。
