# 启动与验证计划

**版本**：0.3　**日期**：2026-10-17　**状态**：规格、测试阶段及实施范围均已批准；实施验证见[实际记录](../../docs/validation/004-task-management/verification.md)。
关联[规格](spec.md)、[计划](plan.md)、[任务](tasks.md)、[协议](contracts/http-sse.md)及[修复](contracts/assembly-repair.md)。

## 前置与阶段

在仓库根执行，Java21、Node22及pnpm10.34.6；复用负责人现有三个MySQL/RabbitMQ/Redis容器和只读订单，不新建容器或改共享配置。`.env.local`及进程环境外注入凭据，不能写入证据。文件根保留`export-data`。测试使用独立schema、账号、vhost、Redis UUID前缀及临时目录。

标准入口为`scripts/run-project.ps1`，操作见[脚本说明](../../scripts/README.md)。`EXPORT_FLOW_BACKEND_PORT`供后端与Vite代理共同读取，默认8080，本机因Steam端口冲突已设8081。新增`EXPORT_FLOW_INSTANCE_ID`须在同一环境内稳定且每API实例唯一；`EXPORT_FLOW_PROGRESS_ENABLED`默认开启；Redis连接使用`.env.example`中的`EXPORT_FLOW_REDIS_*`。MQ账号须有当前vhost内进度交换机和本实例队列的权限。缓存不可用时回源MySQL，不把缓存作为文件或进度的事实源。不要用旧四态应用写新六态；迁移和回退见[计划](plan.md)。运行修复与烟测见[记录](../../docs/validation/004-task-management/runtime-startup-fix.md)。

## 本地使用流程（实现后）

1. 在订单页按已有范围创建导出，成功后留在原页并发出本浏览器创建通知。
2. 进入`/export-tasks`，读权威列表快照，按全环境活跃状态建立单条SSE；搜索文件名／任务号，按六态筛选和分页。
3. 对等待中／生成中任务确认暂停；PAUSING只表示受理，PAUSED才表示安全停止。暂停结果不明时保留原operationId找回。
4. 装配发现块损坏或缺登记时，同任务全段对账，提交较低可信进度，保留健康块、只重建坏段；黄色信息按钮hover／focus提示正在重建损坏文件。来源基线变化或恢复依据缺失明确失败，持久预算最多3轮。
5. 成功采用后100%并允许已有下载；暂停不下载半成品。用户继续、增删改和失败后手动重试均预留。

## 计划验证命令

下列已用于本轮实施验证，不代表运行全项目全部测试。聚焦修正只重跑受影响范围，失败和夹具错误日志保留。前端build已含typecheck，不再单独重复。

```powershell
npm exec --yes --package=pnpm@10.34.6 -- pnpm --dir frontend install --frozen-lockfile
npm exec --yes --package=pnpm@10.34.6 -- pnpm --dir frontend test:run src/features/export-tasks/__tests__ src/features/orders/__tests__/order-management.test.tsx src/features/orders/__tests__/order-export.test.tsx src/features/orders/__tests__/order-export-state.test.tsx src/features/orders/__tests__/navigation.test.tsx
npm exec --yes --package=pnpm@10.34.6 -- pnpm --dir frontend build
$taskEvidenceRoot = Join-Path (Get-Location) 'artifacts/004-task-management'
$taskBuildRoot = Join-Path $taskEvidenceRoot 'backend-target'
$taskUnitTests = 'TaskManagementUnitTest,AssemblyRepairUnitTest,DeliveryIsolationUnitTest,ProgressDeliveryUnitTest,SseDeliveryUnitTest,DataCollectionGuardUnitTest,BlockResourceUnitTest'
$taskRegressionTests = 'TaskManagementMigrationTest,TaskManagementContractTest,TaskPauseMySqlTest,TaskPauseFileTest,TaskProgressIntegrationTest,AssemblyRepairMySqlTest,AssemblyRepairFileTest,CreateTransactionMySqlTest,IdempotencyMySqlTest,CreationInputBoundaryTest,CheckpointInvariantTest,DownloadExportFileTest,DownloadBoundaryTest#TC234_everyNonSuccessStateRejectsDownload+TC234_downloadDependencyAndMetadataErrorsAreSafe+TC234_handleFailureAutomaticallyClosesInputAndDoesNotCloseFrameworkOutput+TC234_symlinkOrWindowsJunctionCannotEscapeConfiguredRoot+TC234_restartKeepsFileAndOriginalIdempotencyResult+TC234_serverLogicalReferenceRejectsTraversalAndAbsoluteOwnerSegments'
./backend/mvnw.cmd -f backend/pom.xml "-Dexportflow.evidenceRoot=$taskEvidenceRoot" "-Dexportflow.buildDirectory=$taskBuildRoot" "-Dtest=$taskUnitTests" "-Dit.test=$taskRegressionTests" verify
```

Wrapper verify保留Surefire、Failsafe、Spotless、Checkstyle、SpotBugs和JaCoCo；不覆盖正在运行的jar。同一buildDirectory禁止并发编译。新增／修正断言后只补编译和对应方法验证，质量检查无生产变化时不重跑已绿全部业务用例；实际追加命令与结果统一见验证记录。Failsafe必须同时执行integration-test及verify，不能只看integration-test进程退出码。

不执行性能、容量、超时、延时、宽限、TTL、lease到期或退避用例，也不用虚拟时钟替代；不执行`check-order-performance.ps1`、ApiPerformanceTest、ExcelPerformanceTest、ShutdownOrderUnitTest或其他含计时断言的旧类。有限框架等待只用于防挂，不断言业务耗时。免验标准保持未验证，不以构建时间代替性能证据。

## 证据与限制

本地原始日志／JUnit XML／JaCoCo／依赖树／有效POM／SHA256归`artifacts/004-task-management`；可提交脱敏汇总见[verification](../../docs/validation/004-task-management/verification.md)，缺口处理见[coverage-gaps](../../docs/validation/004-task-management/coverage-gaps.md)。原测试先行65项基础用例及红灯保留，实施后绿灯追加，不覆盖历史。

1280×720浏览器检查使用真实页面及合成HTTP/SSE响应，仅证明布局、黄色图标及键盘focus；实际MySQL／Rabbit／Redis和SSE传输由独立集成测试证明，没有新增浏览器端到端门禁。空闲后其他设备创建任务不会自行唤醒，用户手动刷新；同浏览器创建、可见和联网可唤醒。本期不提供继续按钮，暂停状态保留待后续规格。

## 装配修复验证流程（已执行）

TC-414至417在隔离任务中受控删除／损坏数据块、移除登记或恢复基线、改变内存来源返回值、损坏候选、注入替换提交后响应丢失及暂停屏障。确认健康首尾块ID保持、坏段更换、进度先下降后推进、预算跨重启保留，最终XLSX全部9个选中字段逐格与只读来源基线核对。V4／V5升级只在独立schema运行，不改历史迁移或来源订单。具体断言及结果见上述证据。
