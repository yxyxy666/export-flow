# 004任务界面：测试先行与实施验证证据

**日期**：2026-10-17　**规格**：[0.3](../../../specs/004-task-management/spec.md)　**阶段**：测试阶段及缺口处理已批准，生产实施已授权；历史红灯保留，实施绿灯见后文。

**后续运行更新**：负责人报告页面404后，已定位Steam占用8080及项目MQ进度权限缺口，切换本机后端8081并同步Vite代理，列表与真实SSE烟测通过，当前服务保持运行。见[运行修复记录](runtime-startup-fix.md)；下文“未切换运行服务”描述保留为初次交付时事实。

审批与任务进度统一见[审批记录](../../../specs/004-task-management/tasks.md#审批记录)。本文件记录实际执行事实，不代表功能验收或测试阶段自动获批。测试文件、计划与[覆盖缺口](coverage-gaps.md)须一起评审。

## 测试先行环境及资源边界（历史）

使用Windows、Java 21.0.12.1、Node 22.14.0、Spring Boot 3.5.16与现有锁定Vitest 5.0.3。项目要求pnpm 10.34.6，本机全局pnpm为11.19.0；本轮直接运行现有node_modules中的工具，没有安装依赖或改锁文件。

仅复用负责人已有的`project-export-flow-mysql-1`、`project-export-flow-rabbitmq-1`、`project-export-flow-redis-1`三个容器；连接后实际创建并回收测试专属schema、用户、vhost和缓存键。没有创建中间件容器或修改共享配置、已有任务和订单来源。首次Docker引擎不可达，启动Docker Desktop后连接恢复；此环境问题没有计入业务红灯。Redis实际PING、隔离键SET/GET/DEL验证成功。

真实Spring应用使用随机回环HTTP端口和独立临时文件根；来源订单通过只读视图读取，普通用例4条、分段用例6条并将真实批次上限设为2。不造数、不复制订单；来源变化测试只在真实查询返回后修改内存结果。夹具关闭后回收数据库及消息隔离资源，失败任务临时目录保留供诊断，目录位置在本地测试日志中；未清理共享文件根。

后端编译输出写入`artifacts/004-task-management/backend-target`，未覆盖运行中的应用jar。没有修改生产源码、生产依赖或创建V5/V6迁移。性能、容量、超时、延时、TTL、退避、租约到期用例均未安排；测试屏障、Socket及SSE读流的有界退出仅防运行器挂住，不断言产品时长。

## 测试先行实际结果（历史）

| 范围 | 独立用例数 | 通过 | 首个目标行为断言失败 | 接口／迁移缺口 |
| --- | ---: | ---: | ---: | ---: |
| 前端任务组件／功能 | 28 | 0 | 28 | 0 |
| 后端单元 | 4 | 0 | 0 | 4 |
| 后端真实依赖／HTTP／文件，含既有基线 | 33 | 2 | 22 | 9 |
| 合计 | 65 | 2 | 50 | 13 |

前端先执行25项，再只运行新增3项及修正的2项，共30次用例执行；28是去重后的用例数。第二批18项被名称过滤，均已有首批执行证据，不是新增免验。后端首批25项集成和4项单元，增量只执行新增8项，没有重复执行全部集成范围。所有失败命令均保留退出码1，不使用忽略失败配置。

| 证据类别 | 实际观察 | 可以得出的结论与限制 |
| --- | --- | --- |
| 页面入口红灯 | 当前真实路由仍显示静态任务占位；列表项和列表GET请求不存在 | 证明列表入口缺失；SSE、暂停、提示等多数后续断言尚未到达，不表示每条深层分支已分别复现 |
| HTTP红灯 | 列表GET为405，暂停POST及SSE端点为404 | 证明新增接口未实现；排序、事务、暂停回执、真实SSE事件流等深层断言仍待执行 |
| 文件修复红灯 | 缺块返回UNKNOWN、损坏块／Excel候选返回KNOWN_FAILURE；缺登记走全量新代；持续损坏返回EXPORT_FILE_INVALID | 已触达真实收集、文件与装配边界，现实现不满足选择性修复及耗尽错误；后续健康块ID与最终Excel比对未全部到达 |
| 来源变化场景 | 删除隔离块后，现生成器未进入新增readSegment路径 | 证明新增恢复路径缺失；不能声称已经观察到源摘要变化的安全拒绝 |
| 接口／迁移缺口 | TaskManagementApiMapper、AssemblyRepairRepository、ProgressRepository/Service、checksumRows方法及新增表不存在 | 运行器与测试编译正常；这13项无法继续验证，单独归缺口，不计业务红灯 |
| 基线绿灯 | 真实Redis隔离键写入／读取／精确清理通过；既有GeneratorBoundaryTest通过 | 证明对应依赖和生成器装配可用；不代表完整文件正确性、Rabbit进度广播或缓存版本算法通过 |

## 测试先行实际命令与报告（历史）

以下在项目根执行，前端命令的工作目录为`frontend`。`$taskBuildRoot`取项目根下`artifacts/004-task-management/backend-target`的绝对路径，`$taskEvidenceRoot`取`artifacts/004-task-management`的绝对路径。完整结果索引见[脱敏汇总](../../../artifacts/004-task-management/red/execution-summary.json)。

| 执行 | 命令 | 退出码／报告 |
| --- | --- | --- |
| 前端首批 | `node node_modules/vitest/vitest.mjs run src/features/export-tasks/__tests__ --outputFile.json=../artifacts/004-task-management/red/frontend-results.json --outputFile.junit=../artifacts/004-task-management/red/frontend-junit.xml` | 1；[JSON](../../../artifacts/004-task-management/red/frontend-results.json)、[JUnit](../../../artifacts/004-task-management/red/frontend-junit.xml) |
| 前端增量 | `node node_modules/vitest/vitest.mjs run src/features/export-tasks/__tests__/task-query.test.ts src/features/export-tasks/__tests__/task-repair.test.tsx src/features/export-tasks/__tests__/task-stream.test.tsx -t '未知任务状态\|旧HTTP不能\|离线关闭\|刷新在途\|本标签创建' --outputFile.json=../artifacts/004-task-management/red/frontend-incremental-results.json --outputFile.junit=../artifacts/004-task-management/red/frontend-incremental-junit.xml` | 1；[JSON](../../../artifacts/004-task-management/red/frontend-incremental-results.json)、[JUnit](../../../artifacts/004-task-management/red/frontend-incremental-junit.xml)；仅选5项 |
| 前端类型检查 | `node node_modules/typescript/bin/tsc -p tsconfig.app.json --noEmit` | 0；[日志](../../../artifacts/004-task-management/red/frontend-typecheck.log) |
| 测试源码定向格式命令 | `./backend/mvnw.cmd -f backend/pom.xml '-DspotlessFiles=src/test/java/com/exportflow/exporttasks/TaskManagement*Test.java,src/test/java/com/exportflow/worker/TaskPause*Test.java,src/test/java/com/exportflow/worker/AssemblyRepair*Test.java,src/test/java/com/exportflow/progress/TaskProgressIntegrationTest.java' spotless:apply` | 0；[日志](../../../artifacts/004-task-management/red/format-tests-final.log)；未运行全量生产质量门禁 |
| 后端测试编译 | `./backend/mvnw.cmd -f backend/pom.xml "-Dexportflow.buildDirectory=$taskBuildRoot" test-compile` | 首批0；补充夹具曾出现泛型编译错误，修正显式Map类型后0；[修正后日志](../../../artifacts/004-task-management/red/backend-compile-corrected.log)，不将已修复工具错误计为红灯 |
| 后端单元 | `./backend/mvnw.cmd -f backend/pom.xml "-Dexportflow.buildDirectory=$taskBuildRoot" '-Dtest=TaskManagementUnitTest,AssemblyRepairUnitTest' surefire:test` | 1；[首批报告](../../../artifacts/004-task-management/red/initial-surefire-reports)、[日志](../../../artifacts/004-task-management/red/backend-unit.log) |
| 后端集成首批 | `./backend/mvnw.cmd -f backend/pom.xml "-Dexportflow.buildDirectory=$taskBuildRoot" "-Dexportflow.evidenceRoot=$taskEvidenceRoot" '-Dit.test=TaskManagementContractTest,TaskPauseMySqlTest,TaskPauseFileTest,TaskProgressIntegrationTest,AssemblyRepairMySqlTest,AssemblyRepairFileTest,GeneratorBoundaryTest' failsafe:integration-test failsafe:verify` | 1；[首批报告快照](../../../artifacts/004-task-management/red/initial-failsafe-reports)、[日志](../../../artifacts/004-task-management/red/backend-integration.log) |
| 后端集成增量 | 同样的Maven入口与隔离buildDirectory，仅用`-Dit.test`选择下表8个新增方法，执行`failsafe:integration-test failsafe:verify` | 1；[增量报告快照](../../../artifacts/004-task-management/red/incremental-failsafe-reports)、[日志](../../../artifacts/004-task-management/red/backend-incremental.log) |

增量精确选择器：

```text
TaskProgressIntegrationTest#TC406_rolledBackBusinessChangeCannotLeaveActivityOrNotification+TC406_oldPublishConfirmationCannotEraseNewerPendingVersion+TC411_activityCommittedBetweenIdleReadsCancelsOldIdleCandidate+TC406_failedRealPublicationRetainsNoticeAndOriginalCommand,TaskPauseMySqlTest#TC404_pauseCommittedBeforeConcurrentClaimNeverGrantsExecution,TaskPauseFileTest#TC405_pauseAfterGeneratorReturnAcknowledgesOnlyClosedResourcesAndPersistentPause,AssemblyRepairFileTest#TC415_changedSourceContentFailsWithoutChangingProvidedOrdersOrHealthyBlocks,AssemblyRepairMySqlTest#TC415_repairBudgetAndEpochSurviveProcessRestartWithoutNewCommand
```

前端两项测试修正分别允许未知状态动作隐藏或禁用、等待确实返回旧HTTP响应；另修正测试名称以匹配暂停断言。新增前端3项验证联网恢复、在途成员变化合并和本标签创建唤醒。测试专用端口反射按实参类型匹配，避免未来同参数个数重载误选。新增SSE读取防挂注解不构成超时行为测试。

## 测试阶段提交时门禁结论（历史）

环境、Java测试编译和前端类型检查已就绪，目标入口及真实文件行为有红灯。完整产品行为、深层分支、迁移、多实例投递和浏览器视觉尚未验证；具体影响、待补用例及恢复条件见[缺口清单](coverage-gaps.md)。不宣称用例完整性或功能验收已通过，也不将两个基线绿灯解释为新增功能完成。

当前提交的是测试先行评审材料。只有负责人明确评审本阶段及覆盖缺口，并批准实施范围后，才可进入生产实施；后续须先补对应缺失断言再实现受影响行为，保留本轮红灯，追加绿灯及相关质量证据。

## 实施授权与变更

负责人在测试阶段提交后回复“继续”，批准004规格0.3的测试材料、EX-001/002及六类缺口处理方案，并授权生产实施；见[审批记录](../../../specs/004-task-management/tasks.md#审批记录)。项目级效率规则草案仍未批准。当前生产代码与测试均已生成，不能继续沿用前文“无生产修改”的历史描述。

已实现列表／筛选／分页／展示元数据、真实具名SSE与Redis可降级版本缓存、持久控制屏障暂停及安全停止证明、同任务选择性修复和可信进度回退／黄色提示。V5/V6为追加迁移，原任务／命令及保护引用保留；不混跑旧四态Worker。未提供CRUD、手动失败重试或继续按钮。

## 实施环境与证据边界

仍复用前述三容器，独立任务schema／账号／vhost、Redis UUID前缀和派生index，来源订单只读。生产缓存由Spring Boot BOM管理的data-redis starter提供。已使用pnpm10.34.6冻结安装，不改依赖锁。构建输出独立于当前运行jar；没有重启用户已有8080应用。临时应用、MQ／MySQL隔离资源和缓存夹具关闭回收；测试文件根按既有夹具策略保留故障／恢复材料供诊断，不清共享export-data。

## 实施中发现与纠正

| 问题 | 分类与修正 | 证据 |
| --- | --- | --- |
| application.yml缓存前缀末尾冒号未引用 | 配置解析错误，修正为引号字符串；不能当行为红灯 | 本地backend-progress-tests.log |
| 发布端口代理隐藏@RabbitListener | 分离RabbitProgressListener，保证真实消费者注册；真实双实例fanout和SSE随后通过 | backend-progress-listener-tests.log |
| 初始化前emitter.send仅入Spring缓冲却被当已发送 | 新增MVC异步拦截激活与单连接初始化屏障，实际发送完成再ACK | red/sse-delivery-boundaries.log为接口缺口；SseDeliveryUnitTest补实际发送／溢出断言 |
| 缓存端口抛错阻断通知、重复稳定ID仍能启动 | 先补断言，缓存故障回源／继续可靠发布，broker确认后才就绪 | red/progress-cache-fallback.log、red/duplicate-instance.log |
| 回滚用例使用夹具外部DataSource不参与应用事务 | 测试夹具错误；改用应用DataSource，保留失败日志，不归生产事务红灯 | backend-progress-boundaries-corrected.log；backend-repair-final-boundaries.log中修正后通过 |
| 最终Excel断言把MySQL DATETIME强转Timestamp | 夹具类型错误；支持LocalDateTime与Timestamp，随后逐字段核对 | red/repair-pause-final-boundaries.log及red/repair-readiness-corrected-fixtures.log |
| legacy新代未清可信量、READY未查封存、修复历史未结束、完整旧块仍查来源 | 修正夹具后4项实际行为红灯，再修生产；16项修复及事务边界通过 | red/repair-readiness-corrected-fixtures.log；backend-repair-final-boundaries.log |
| 再次CHECKING抢占REBUILDING提示 | 先增加断言取得红灯，再调整优先级和暂停中文文案，8项修复前端用例通过 | red/frontend-rechecking.json；frontend-repair-final.json |
| 旧订单导航仍断言静态任务页零API | 本期明确行为变化；限定允许列表GET，保留订单请求abort和迟到响应隔离断言 | frontend-final-results.json；frontend-navigation-corrected.json |
| 断点回归按参数数量误选commitBlock重载 | 共享测试夹具错误；改为按实参兼容类型唯一匹配，保留原断点断言 | backend-final-verify.log；backend-corrected-verify.log |
| 新暂停补证查询白名单外表、将结果对象误当枚举 | 测试写法错误；使用固定SQL计数并读取kind，旧证明须WAITING且任务仍PAUSING，正确证明才APPLIED | backend-corrected-verify.log；backend-pause-corrected-verify.log |

红灯、接口缺口、配置／夹具错误分别保留，不以首个入口失败证明全部后续分支。追加断言先于对应生产变更；现有边界补证不修改已正确实现的业务语义。

## 浏览器与可访问性

在1280×720内置Chromium浏览器打开真实任务页面，使用合成协议响应展示修复66%任务；黄色信息按钮位于进度条后，可通过Tab聚焦，Tooltip显示“正在重建损坏文件”，读到颜色rgb(250,173,20)，控件可见、宽表横向滚动。hover由组件用例验证，真实浏览器验证focus。截图：[页面证据](../../../artifacts/004-task-management/task-repair-keyboard-1280x720.jpg)。临时viewport已复原，测试tab与5174视觉服务已关闭；没有关闭用户原页面或应用。

此项证明页面及键盘呈现；真实后端SSE由独立MySQL／Rabbit／Redis及回环HTTP测试验证。未新增浏览器端到端测试，不把合成响应说成完整端到端证据。

## 最终功能与质量结果

2026-10-17已完成本期实现及适用验证。一次适用回归后只重跑失败修正或新增补证范围；下表按用例去重，不重复累计执行次数，也不包含免验项。原失败日志和报告快照保留，小型脱敏[结果摘要](results.json)随文档维护。

| 范围 | 当前结果 | 实际证据 |
| --- | --- | --- |
| 前端适用回归 | 116项通过，0失败 | [去重汇总](../../../artifacts/004-task-management/frontend-current-summary.json)；首轮frontend-final-results.json及修正范围frontend-repair-final.json、frontend-navigation-corrected.json、frontend-phase-final.json |
| 后端单元 | 14项通过，0失败 | [去重汇总](../../../artifacts/004-task-management/backend-current-summary.json)；regression-surefire-reports及修正报告 |
| 后端真实依赖／HTTP／文件／迁移 | 127项通过，0失败 | 同一后端汇总；regression-failsafe-reports、corrected-failsafe-reports、final-failsafe-reports按用例覆盖；排除残留且未被本次选择的GeneratorBoundaryTest旧报告 |
| 冻结安装及前端构建 | 均退出码0；build已包含两份TypeScript配置检查 | [安装](../../../artifacts/004-task-management/frontend-frozen-install.log)、[构建](../../../artifacts/004-task-management/frontend-final-build.log) |
| 后端Wrapper verify | 最终退出码0；集成及单元未跳过 | [最终日志](../../../artifacts/004-task-management/backend-pause-corrected-verify.log)；前一修正批次backend-corrected-verify.log的暂停夹具失败已单列并修正 |
| Spotless／Checkstyle／SpotBugs | 格式检查通过；Checkstyle 0违规；SpotBugs问题及分析错误均0 | 同一最终verify日志；[SpotBugs XML](../../../artifacts/004-task-management/backend-target/spotbugsXml.xml) |
| JaCoCo | HTML/XML已生成，无百分比阈值 | [HTML](../../../artifacts/004-task-management/backend-target/site/jacoco/index.html)、[XML](../../../artifacts/004-task-management/backend-target/site/jacoco/jacoco.xml)；累积exec含历史类版本，出现不匹配提示，不能作为当前版本完整覆盖率证明，不据此宣称百分比目标达成 |
| 脚本及文档 | PowerShell语法解析通过；文档与标准映射审计、Git空白检查通过 | [脚本解析](../../../artifacts/004-task-management/script-parse-check.json)、[文档审计](../../../artifacts/004-task-management/implementation-document-check.json)；审计不代替业务断言 |

大回归后端实际执行123项集成，其中122项通过、1项共享反射夹具错误；其14项单元全通过。第一次定向补证8项集成中6项通过、2项新测试写法错误；第二次只重跑这2项并保留verify全部静态门禁，2项均通过。新增4项集成去重后为127项，迁移增强和断点重跑不重复计数。各次报告按类、测试名、同名序号合并，原始日志不改写。六类功能缺口已按[补证表](coverage-gaps.md#实施补证状态)收口。

## 收尾命令与依赖

完整适用回归及前端命令见[quickstart](../../../specs/004-task-management/quickstart.md#计划验证命令)。以下是大回归后的两次实际定向verify，均在项目根执行，使用同一独立buildDirectory；前一次退出码1、后一次退出码0，错误分类见前表。

```powershell
$taskEvidenceRoot = Join-Path (Get-Location) 'artifacts/004-task-management'
$taskBuildRoot = Join-Path $taskEvidenceRoot 'backend-target'
$taskFinalSelectors = 'TaskManagementMigrationTest,CheckpointInvariantTest,TaskPauseMySqlTest#TC407_sameOperationAcrossConcurrentTasksCommitsExactlyOnePause+TC405_staleOwnerFenceControlOrCheckpointCannotProvePauseStopped,AssemblyRepairFileTest#TC414_unreadableDatabaseIsUnknownAndNeverTreatedAsMissingRecoveryEvidence,TaskProgressIntegrationTest#TC406_realRedisAcceptsNewerLowerProgressAndRejectsMissingStaleOrCorruptReads'
./backend/mvnw.cmd -f backend/pom.xml '-Dtest=TaskManagementUnitTest' "-Dit.test=$taskFinalSelectors" "-Dexportflow.evidenceRoot=$taskEvidenceRoot" "-Dexportflow.buildDirectory=$taskBuildRoot" verify
./backend/mvnw.cmd -f backend/pom.xml '-Dtest=TaskManagementUnitTest' '-Dit.test=TaskPauseMySqlTest#TC407_sameOperationAcrossConcurrentTasksCommitsExactlyOnePause+TC405_staleOwnerFenceControlOrCheckpointCannotProvePauseStopped' "-Dexportflow.evidenceRoot=$taskEvidenceRoot" "-Dexportflow.buildDirectory=$taskBuildRoot" verify
python artifacts/004-task-management/finalize-evidence.py
python artifacts/004-task-management/audit-stage-documents.py
git diff --check
```

实际版本为Java21.0.12.1、Node22.14.0、pnpm10.34.6、Boot3.5.16、Spring Data Redis3.5.13／lettuce6.6.0.RELEASE、MyBatis3.5.19、Flyway11.7.2、mysql connector9.7.0、POI5.5.1、JUnit5.12.2和AssertJ3.27.7。见[有效POM](../../../artifacts/004-task-management/effective-pom.xml)及[依赖树](../../../artifacts/004-task-management/dependency-tree.txt)。元数据首轮命令路径引用失败保留于dependency-metadata.log，修正输出路径后dependency-metadata-corrected.log退出码0；没有更换依赖规避失败。

## 交付物与限制

后端可执行[jar](../../../artifacts/004-task-management/backend-target/export-flow-0.2.0-SNAPSHOT.jar)及前端[静态入口](../../../frontend/dist/index.html)已生成。jar SHA-256为`37c09d07fe55d0d2f96e03e1c56d3a42e1f012a31bf7dd6c114df333449cbae9`；jar、前端全部文件、锁文件与POM的实际大小和摘要见[清单](../../../artifacts/004-task-management/delivery-sha256.json)。工作区状态留于[Git状态](../../../artifacts/004-task-management/delivery-git-status.txt)，本轮未创建提交。

没有重启原8080应用，也没有在用户运行库执行V5/V6迁移；真实升级验证仅作用于隔离schema。运行本期版本需按quickstart切换兼容的API与Worker，不能混用旧四态执行器。临时视觉服务已退出，用户三项基础设施继续运行。

EX-001/002仍未验证：性能、容量、资源阈值、超时、延时、TTL、宽限、lease到期和退避时长均按负责人要求跳过，不以虚拟时钟或普通用例耗时代替。浏览器视觉使用合成响应，真实后端协议和一致性由集成测试独立证明。Vite大chunk提示未纳入性能优化；JaCoCo历史类版本提示限制覆盖统计，两者未被隐藏或改阈值消除。
