# 既有订单来源回归与质量门禁

日期：2026-10-12。来源为负责人project-export-flow容器内的exportflow.orders（110000条），不造数。当前按类汇总独立于旧316项结果，逐类XML／日志来源在本地artifacts/002-order-management/provided-final-04-reports及provided-final-04-results.json；来源、权限及规模缺口见[来源记录](provided-data.md)，实施映射见[任务](../../../specs/002-order-management/tasks.md)。

功能JUnit共304条：300通过，0失败，0错误，4跳过记录（2条数据规模不足、2条默认关闭性能专项）。数据不足不计功能失败，也不能标为通过；独立性能另行据实际度量判定。

02全量批次发现旧ALPHA订单依赖导致1条失败，03复验修正后的幂等／来源／输入／日志安全及单元测试，正式门禁通过；随后独立性能发现旧RUNNING候选阻塞新PENDING投递。新增2条真实MySQL回归在provided-candidate-red.log先失败，再修正候选与告警计数，04复验投递／期限／故障／运行／Worker和单元测试并通过正式Wrapper门禁。汇总采用各类最新相关结果，不将失败整批改写为成功，原始日志和XML仍保留。

复验命令：`backend/mvnw.cmd -f backend/pom.xml spotless:apply -Dit.test=OutboxDispatchMySqlTest,OutboxFailureWindowsTest,OutboxDeadlineRaceTest,OutboxTimeoutMySqlTest,DispatchSchedulingTest,RuntimeCapabilityTest,RuntimeConfigurationTest,RuntimeSecurityTest,WorkerFailureWindowsTest verify`。04未降低任何业务断言；Spotless、Checkstyle、SpotBugs与JaCoCo、打包结果以该日志为准。

| 套件 | 总数／通过／跳过 | 失败／错误 |
| --- | --- | --- |
| com.exportflow.dispatch.BrokerPolicyBoundaryTest | 9／9／0 | 0／0 |
| com.exportflow.dispatch.DispatchSchedulingTest | 2／2／0 | 0／0 |
| com.exportflow.dispatch.OutboxDeadlineRaceTest | 6／6／0 | 0／0 |
| com.exportflow.dispatch.OutboxDispatchMySqlTest | 3／3／0 | 0／0 |
| com.exportflow.dispatch.OutboxFailureWindowsTest | 9／9／0 | 0／0 |
| com.exportflow.dispatch.OutboxTimeoutMySqlTest | 4／4／0 | 0／0 |
| com.exportflow.dispatch.PublisherCorrelationTest | 5／5／0 | 0／0 |
| com.exportflow.dispatch.RabbitMqPublisherTest | 5／5／0 | 0／0 |
| com.exportflow.exporttasks.CreateTransactionMySqlTest | 4／4／0 | 0／0 |
| com.exportflow.exporttasks.CreationAsyncBoundaryTest | 2／2／0 | 0／0 |
| com.exportflow.exporttasks.CreationInputBoundaryTest | 37／37／0 | 0／0 |
| com.exportflow.exporttasks.CreationResultMySqlTest | 3／3／0 | 0／0 |
| com.exportflow.exporttasks.ExportCreationContractTest | 9／8／1 | 0／0 |
| com.exportflow.exporttasks.IdempotencyMySqlTest | 4／4／0 | 0／0 |
| com.exportflow.orders.ApiPerformanceTest | 1／0／1 | 0／0 |
| com.exportflow.orders.OrderApiContractTest | 14／14／0 | 0／0 |
| com.exportflow.orders.OrderQueryBoundaryTest | 14／14／0 | 0／0 |
| com.exportflow.orders.OrderQueryMySqlTest | 5／5／0 | 0／0 |
| com.exportflow.orders.ProvidedOrdersTest | 2／2／0 | 0／0 |
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
| com.exportflow.worker.ExcelContentTest | 24／23／1 | 0／0 |
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
| com.exportflow.worker.WorkerFailureWindowsTest | 15／15／0 | 0／0 |
