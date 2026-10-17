# 继续任务与自动进度验证

日期：2026-10-17。关联[需求](../../../specs/005-task-resume-and-live-progress/spec.md)、[计划](../../../specs/005-task-resume-and-live-progress/plan.md)、[任务及两次授权](../../../specs/005-task-resume-and-live-progress/tasks.md)、[决策](../../adr/0012-user-resume-and-live-progress.md)。依据负责人本轮明确指示，在规格完成、测试先行后直接实施，不伪造事后人工评审。本文记录功能正确性，不做性能或时长验收。

## 结论与适用边界

已实现PAUSED任务的幂等继续、原命令重新开放、新执行身份及恢复核验；健康块保留，不匹配段按独立基线重建，进度按核验结果重新计算。已启用生产Spring调度，真实自动链路验证了数据库提交的中间进度经Outbox、MQ、Redis及HTTP SSE到达，并在文件采用后收到成功100%及可下载结果。

适用后端用例去重70例最终通过；前端任务相关48例及订单UNKNOWN离页1例通过，类型检查和构建通过。保留首次失败及定向修正的记录。另有未修改的订单日期控件用例失败，未宣称全量前端门禁通过。

调查已确认生产入口缺少`@EnableScheduling`，虽然进度发布和暂停对账已有`@Scheduled`。旧测试手动发布遗漏了自动调度。用户任务曾出现已收集总量、通知待发的状态；本次未重跑其110000行任务，不能据此断言其所有99%停滞原因均已复现。100%仍以最终文件校验并采用成功为准，不以行数满值替代。

## 环境与保护范围

- Windows PowerShell；Java 21.0.12.1、Node 22.14.0、pnpm 10.34.6；依赖沿用锁文件及既有POM，未新增生产依赖。
- 使用用户已启动的`project-export-flow-mysql-1`、`project-export-flow-rabbitmq-1`、`project-export-flow-redis-1`，未新建或重启中间件容器。MySQL 8.4与现有Flyway的版本提示保留，实际迁移验证通过。
- 订单来源`exportflow.orders`只读；沿用隔离schema、MQ vhost/账号、Redis前缀及文件根。测试应用正常关闭，隔离服务资源按夹具精确回收，诊断文件保留，不清理用户export-data。
- 构建输出使用`artifacts/005-task-resume-and-live-progress/backend-target`；未覆盖用户运行中的backend/target jar。8081用户Java进程27568、5173用户Node进程26956保持运行，未启动遗留验证服务。
- 性能、容量、超时、延时、TTL、租约到期及退避时间按用户要求免验；用例中的有界等待和屏障只防挂死、控制事件先后，不构成时长断言。

## 测试先行与修正

本地证据根为`artifacts/005-task-resume-and-live-progress/`，日志不提交Git。

| 批次 | 实际结果 | 解释 |
| --- | --- | --- |
| backend-red.log | 9例失败 | 继续入口404、动作缺RESUME及未启用生产调度，均指向目标缺失 |
| backend-extra-red.log | 补充3例未通过 | 2例继续404；回滚夹具SQL触发器缺SUPER权限属于工具错误，改为真实事务进度写入后抛错，未修改共享数据库权限 |
| frontend-red.log | 3例失败 | 继续动作尚不支持，保留真实业务红灯 |
| backend-green.log | 单元8例、自动SSE1例通过；继续11例未全过 | enum对象与字符串比较、夹具count白名单等测试错误修正，不削弱业务断言 |
| backend-regression.log | 61例中59通过、2失败 | V6升级种子未满足V6可信计数；缺断点探针误将准备数据的4次读取计为新增读取。修正种子及改为调用前后差值，不改历史生产迁移 |
| backend-final.log | 最终定向6例通过，verify退出0 | 4条迁移路径及继续的2条受修正用例；原命令继续还补验生成、采用及成功后的幂等重放 |

初次全局pnpm实际指向Node24/pnpm11，工具失败单独保留，不当业务红灯。最终使用本机Node22和缓存固定pnpm10.34.6。前端首次类型检查指出RESUME的TypeScript类型遗漏，补齐后两套tsconfig均通过。

## 最终后端证据

以下按用例去重，跨批次采用最终结果，不把重复运行累计为新增覆盖。

| 测试类 | 最终通过数 | 证据 |
| --- | ---: | --- |
| TaskManagementUnitTest / ProgressDeliveryUnitTest / AssemblyRepairUnitTest | 2 / 4 / 2 | backend-green.log |
| AutomaticTaskProgressTest | 1 | backend-green.log |
| TaskManagementContractTest | 5 | backend-regression.log |
| TaskManagementMigrationTest | 4 | backend-final.log，空库及V4/V5/V6升级到V7 |
| TaskProgressIntegrationTest | 11 | backend-regression.log |
| AssemblyRepairFileTest / AssemblyRepairMySqlTest | 7 / 9 | backend-regression.log |
| TaskPauseFileTest / TaskPauseMySqlTest | 5 / 9 | backend-regression.log |
| TaskResumeTest | 11 | backend-regression.log中10例及backend-final.log补验；最终2例包含一条已通过用例的增强验证 |
| 合计 | 70 | 不代表全项目所有测试均运行 |

[继续控制](../../product/acceptance-criteria.md#ac-092)验证严格输入、状态/控制版本冲突、跨动作ID拒绝、已提交结果找回、事务回滚、并发仅一次迁移、原命令不新增、旧执行/投递身份拒绝及预算保留。

[部分恢复](../../product/acceptance-criteria.md#ac-093)实际构造收集6/8行的三块前缀，暂停后删除中间块，再继续：第一、三块身份保持，只重建中间段，再收集尾部；最终Excel行集合及顺序与原只读范围匹配。缺块登记但基线存在可重建；缺独立基线、缺断点却有进度明确失败；数据库未知不重建、不采用。缺断点断言为无新增来源读取。源改变、修复耗尽及仅候选损坏等沿用受影响既有修复回归。

[自动通知](../../product/acceptance-criteria.md#ac-094)用例以生产启动入口、自动投递及真实worker消费执行，无手动调用dispatch；在可控收集屏障之间观察2/6行、RUNNING及33%，核对Redis摘要；放行后通过同一HTTP SSE读取SUCCEEDED/100%，确认ADOPTED记录、列表100%及下载200。

最终verify同时完成Spotless（176文件洁净）、Checkstyle（0违规）、SpotBugs（0错误/警告）及JaCoCo HTML/XML报告。覆盖率无百分比阈值，不能把最后定向批次的覆盖率当成全部70例合并覆盖率。

## 最终前端证据与已知问题

| 文件/范围 | 最终通过数 | 证据 |
| --- | ---: | --- |
| task-actions / task-repair / task-stream / task-query / task-resume / task-additional | 5 / 8 / 15 / 6 / 3 / 10 | frontend-regression.log，各文件完整完成 |
| task-created | 1 | frontend-created.log |
| UNKNOWN离页边界 | 1 | frontend-boundaries-corrected.log |

[继续交互](../../product/acceptance-criteria.md#ac-095)三条新增用例验证空闲页继续后重开SSE、较低可信进度及黄色重建提示、最终100%、结果不明同动作/ID/版本找回、暂停中不开放继续。旧订单离页用例允许目标任务页一次只读列表请求，并保留严格拒绝其他意外API及原有断言。

全量前端批次包含旧时间相关用例，识别后停止，随后仅补跑适用项。日志中已完成的六个任务测试文件共47例，另task-created补1例，共48例；未把中止批次说成整批成功。测试运行器JSON/JUnit会被定向批次覆盖，汇总以保存日志为据。

范围外失败：`order-date-picker.test.tsx`的TC-311“本机夏令时跳时对应的合法香港时间不被改写，仍按香港转UTC”，因日期确认按钮不可见未通过，定向复现见`frontend-boundaries-corrected.log`、`frontend-date-final.log`。对该文件的尝试修改已撤销，订单生产实现未改；尚未确认其在本次变更前是否也失败，故不宣称是已证实的既有产品缺陷。此项仍待单独调查，全量前端门禁没有全绿。

冻结离线安装退出0见`frontend-install.log`；两个tsconfig检查退出0见`frontend-typecheck-final.log`；Vite构建退出0见`frontend-build-final.log`。当前工程无独立lint命令。已有大chunk提示保留，不进行性能改造。

## 实际命令与产物

后端在根目录使用以下参数，分别运行红灯、新增用例及受影响回归，日志文件见前表：

```powershell
$taskEvidenceRoot = Join-Path (Get-Location) 'artifacts/005-task-resume-and-live-progress'
./backend/mvnw.cmd -f backend/pom.xml "-Dexportflow.buildDirectory=$taskEvidenceRoot/backend-target" "-Dexportflow.evidenceRoot=$taskEvidenceRoot" '-Dit.test=TaskResumeTest,AutomaticTaskProgressTest' '-Dtest=TaskManagementUnitTest,AssemblyRepairUnitTest,ProgressDeliveryUnitTest' verify
./backend/mvnw.cmd -f backend/pom.xml "-Dexportflow.buildDirectory=$taskEvidenceRoot/backend-target" "-Dexportflow.evidenceRoot=$taskEvidenceRoot" '-Dit.test=TaskManagementContractTest,TaskManagementMigrationTest,TaskProgressIntegrationTest,AssemblyRepairFileTest,AssemblyRepairMySqlTest,TaskPauseFileTest,TaskPauseMySqlTest,TaskResumeTest' '-Dtest=__NoUnitTest' '-Dsurefire.failIfNoSpecifiedTests=false' verify
./backend/mvnw.cmd -f backend/pom.xml spotless:apply
./backend/mvnw.cmd -f backend/pom.xml "-Dexportflow.buildDirectory=$taskEvidenceRoot/backend-target" "-Dexportflow.evidenceRoot=$taskEvidenceRoot" '-Dit.test=TaskManagementMigrationTest,TaskResumeTest#TC501_pending*+TC504_missingCheckpoint*' '-Dtest=__NoUnitTest' '-Dsurefire.failIfNoSpecifiedTests=false' verify
```

前端最终固定工具调用如下；重现时可使用正确Node22环境下的项目固定pnpm，不需要重复全量测试：

```powershell
$taskPnpm = 'C:/Users/Administrator/AppData/Local/npm-cache/_npx/168ca33e231eaaf0/node_modules/pnpm/bin/pnpm.cjs'
node $taskPnpm --dir frontend install --frozen-lockfile --offline
node $taskPnpm --dir frontend test:run src/features/export-tasks/__tests__/task-resume.test.tsx
node $taskPnpm --dir frontend test:run src/features/export-tasks/__tests__/task-created.test.ts
node $taskPnpm --dir frontend test:run src/features/orders/__tests__/order-boundaries.test.tsx src/features/orders/__tests__/order-date-picker.test.tsx -t 'UNKNOWN离页明确|夏令时跳时对应'
node $taskPnpm --dir frontend exec tsc -p tsconfig.app.json --noEmit
node $taskPnpm --dir frontend exec tsc -p tsconfig.node.json --noEmit
node $taskPnpm --dir frontend exec vite build
```

产物为独立目录下`export-flow-0.2.0-SNAPSHOT.jar`及`frontend/dist`。本地`checksums.json`记录4个产物文件的SHA-256，`build-metadata.json`记录版本和Git状态，`dependency-tree.txt`及日志记录依赖树（命令退出0）。文档检查覆盖15个Markdown文件的323条本地链接及41处OpenAPI本地引用，未发现断链；结果见`document-check.json`。`git diff --check`通过，只有既有Windows换行转换提示。

未提交Git，未替用户重启或重新执行现有大任务；[重启步骤](../../../specs/005-task-resume-and-live-progress/quickstart.md)完成后新版本才生效。[成功标准](../../product/acceptance-criteria.md#sc-018)由上述控制、内容、通知及组件证据共同支撑，不能替代未执行的用户现场验收。
