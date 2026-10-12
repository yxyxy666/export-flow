# 全量选择与日期控件：验证记录

日期：2026-10-12（Asia/Hong_Kong）。[规格0.1](../../../specs/003-select-all-filtered/spec.md)由负责人以“开始吧”批准进入测试先行；负责人在测试阶段及覆盖缺口审批请求后回复“继续开发”，明确授权生产实施。[任务与审批](../../../specs/003-select-all-filtered/tasks.md)记录范围。下面保留测试先行历史红灯及审批时缺口，实施结果另列，不覆盖旧证据。

## 环境与执行范围

Node22.14.0、pnpm10.34.6、Java21.0.12.1；复用project-export-flow已有MySQL/RabbitMQ/Redis三个healthy容器，无新容器、服务重启、原账号修改或来源DML。MySQL用例使用专属schema、账号、只读订单视图，RabbitMQ使用专属vhost，退出时回收自己的资源。默认每用例4笔来源；一个分批计数/写入用例使用1002笔既有来源、排除1001笔、受理1笔，不生成大文件或测量性能。Worker文件用例目标为3笔来源。

新增用例覆盖[全量范围](../../product/acceptance-criteria.md#ac-073)、[选择生命周期](../../product/acceptance-criteria.md#ac-074)、[实际导出](../../product/acceptance-criteria.md#ac-075)、[边界](../../product/acceptance-criteria.md#ac-076)、[逐条取消](../../product/acceptance-criteria.md#ac-077)、[全部反选](../../product/acceptance-criteria.md#ac-078)、[持久化兼容](../../product/acceptance-criteria.md#ac-079)和[日期控件](../../product/acceptance-criteria.md#ac-080)。包含所映射的原行为回归；测试先行时[本次质量目标](../../product/acceptance-criteria.md#sc-015)尚未达标，红灯仅证明当时尚未实现的行为；实施后的达成证据见[最终门禁](#最终功能回归与质量门禁)。

## 实际命令与结果

本节为实施前测试先行证据；最新实施结果见末节。

前端统一以npm exec --yes --package=pnpm@10.34.6 -- pnpm --dir frontend调用，报告写入artifacts/003-select-all-filtered；表中列出追加的脚本/参数，JSON与JUnit分别指定outputFile.json/outputFile.junit，避免覆盖旧002证据。

| 操作 | 实际结果 | 原始证据 |
| --- | --- | --- |
| test:run完整初次执行 | 208项，166通过、42失败；包含两个后来发现并修正的参数化用例问题，不将初次每个失败都认作业务红灯 | [初次JSON](../../../artifacts/003-select-all-filtered/frontend-red.json) |
| test:run -t 'TC-3\|组合表单提交枚举\|TC-205 开始时间' | 57项执行，13通过、44预期失败；其余151项按筛选未重跑，之前回归证据仍保留，不作为免验 | [增量JSON](../../../artifacts/003-select-all-filtered/frontend-incremental-red.json) |
| test:run src/features/orders/__tests__/order-selection.test.ts | 19项，4通过、15预期失败；旧非法输入断言改为本次安全拒绝/可见notice要求 | [最终选择JSON](../../../artifacts/003-select-all-filtered/frontend-selection-final-red.json) |
| test:run src/features/orders/__tests__/order-date-picker.test.tsx（TZ=UTC） | 8项，控件能力基线1通过、业务7预期失败；基线实际操作AntD日历、秒级确认与清空 | [最终日期JSON](../../../artifacts/003-select-all-filtered/frontend-date-ready-red.json) |
| 同日期文件，-t 'TC-311 本机夏令时'（TZ=America/New_York） | 2项业务预期失败；其他6项按筛选未重跑。当前缺少业务DatePicker，不能据此宣称夏令时转换通过 | [时区JSON](../../../artifacts/003-select-all-filtered/frontend-timezone-final-red.json) |
| typecheck | 退出0，包含新增及调整测试的类型检查 | [类型检查](../../../artifacts/003-select-all-filtered/frontend-types-final.log) |
| backend/mvnw.cmd -f tests/backend/pom.xml -Dtest=CreationInputBoundaryTest#TC312*+TC216_selectedAssociationIsExactDeduplicatedAndFilteredWritesNone,IdempotencyMySqlTest#TC313*+TC217_idOrderAndDuplicatesCanonicalizeButFieldOrderConflicts,ExportExclusionMySqlTest,ExportExclusionRecoveryTest test | 30项，14通过、16预期失败，Errors=0、Skipped=0；退出1为尚未实现行为 | [最终后端日志](../../../artifacts/003-select-all-filtered/backend-red-final.log)、[JUnit目录](../../../artifacts/003-select-all-filtered/backend-red-final-reports) |
| backend/mvnw.cmd -f tests/backend/pom.xml test-compile | 测试格式化后再次编译，退出0 | [编译日志](../../../artifacts/003-select-all-filtered/backend-test-compile.log) |
| 仅四份变更Java测试，逐文件设置-DspotlessFiles=.*类名\.java，运行backend Wrapper spotless:apply | 每次只匹配1份测试，退出0；生产目录未被格式化 | [格式日志](../../../artifacts/003-select-all-filtered/test-format-scoped.log) |

最新前端源共210项：160通过、50预期失败，未执行项0。统计取完整初次报告，按测试文件/用例顺序替换实际重跑结果；日期和选择文件使用最终全文件结果，不累加重复执行次数。汇总脚本在合并前检查用例数一致；[统计](../../../artifacts/003-select-all-filtered/test-stage-summary.json)可由node artifacts/003-select-all-filtered/summarize-tests.cjs复现。后端30项是最终四个类的JUnit结果，不累计初次28项。

## 红灯原因与测试review

- TC-301/303/304：真实reducer未返回INCLUDE/EXCLUDE，尚未支持排除/补集/容量notice；非法输入仍抛异常。
- TC-302/305/306/308：真实页面缺少“全选筛选结果”“全部反选”，因此尚不能操作后续跨页、排除导出和结果冻结分支。
- TC-307/309：真实schema拒绝excludedOrderIds，不能创建或冻结新增范围；缺省旧请求通过。
- TC-310/311及两个调整后的原日期用例：页面仍为Input，缺少日历及日期时间确认。独立AntD控件能力基线通过，排除运行器无法操作组件的原因。
- TC-312/313/314：新增合法字段返回400；原摘要字节断言已执行并通过，但空数组复用、非空版本、原键UNKNOWN、排除写入及回滚分支要实现字段后继续执行。
- TC-315：独立写入本用例任务关联后，真实keyset仍读回取消项，不同排除集合得到同一配置身份；旧SELECTED/FILTERED批读及无排除配置字节兼容通过。
- TC-316：从持久范围重启应用后真实生成器因包含额外来源行返回KNOWN_FAILURE；错误配置前缀测试已实际注入并重建，但后续同样因未排除而失败。HTTP受理失败使Worker进程中断/续写、最终Excel核对尚未达到；没有声称已生成或验证文件。

测试review已修正：Vitest把数组行展开导致的参数化输入错误；独立DatePicker基线使用与应用相同的按钮空格设置；jsdom无法计算CSS :hover时直接派发清空click验证组件处理器，不冒充真实浏览器指针验证；错误配置前缀先在测试库建立真实断点行，避免不存在断点的STALE被当作业务红灯。原始失败诊断保留在artifacts，最终统计只取修正后的证据。Google Java格式化限制为本次四个测试，其他生产代码未更改。

## 覆盖缺口与恢复条件

本表记录第二次审批提交时的覆盖缺口及补证条件；不是本次实施的最新结论，实际恢复结果另列。

| 范围 | 尚未执行的断言及影响 | 替代证据／恢复条件／负责人 |
| --- | --- | --- |
| TC-302/305/306/308 | UI入口缺失，使同一用例后续分页、反选、禁用、POST和结果处理尚未到达 | reducer及schema独立红灯已取得；实现入口后逐项重跑，项目负责人评审、实施者补证 |
| TC-307/309/312/313/314 | 输入白名单拒绝合法字段，使冻结、原键重试、原长度超界的后半段、交集计数/1000项分批与关联回滚未到达 | TC-315绕过HTTP独立验证持久读；实现边界后重跑原用例，不削弱断言；项目负责人评审 |
| TC-310/311 | 真实业务日期控件不存在，控件回显、单侧/清空/倒置、历史/重置及UTC/DST分支未到达 | 独立真实AntD基线通过；实现后在UTC和America/New_York分别运行。需核对组件内部文本解析，不能仅因RHF桥接使用UTC就假定其内部不受本机夏令时影响；实施者核验、项目负责人评审 |
| TC-316 | 当前范围包含未排除来源行，尚未验证真实Excel、Worker中断后可信前缀续写及唯一结果 | 重启应用、真实关联批读及真实生成器的独立红灯已取得；实现后仅使用3行目标文件重跑，项目负责人评审 |
| 服务端200000/200001真实上限 | 现有来源不足规模，不能证明真实上限验收；当前小范围用例不冒充规模证据 | 前端MSW元数据、内存容量和HTTP重复ID原长度边界分别验证对应行为；按用户要求记录条件不足、不造数、不计失败；项目负责人已有指令，本次不新增性能验证 |
| 浏览器指针及性能专项 | 本轮无需验证，不恢复旧浏览器手工、broker重启或大规模导出要求 | 保留原免验与取消性能记录；组件click证明事件处理，不宣称浏览器CSS交互已验证 |

测试先行提交时环境、编写、预期失败、映射和证据已准备完毕；负责人随后以“继续开发”批准该阶段及上表覆盖缺口。历史红灯证明尚未实现的行为，不计为已交付功能失败；实现后必须补验未到达断言。

## 实施与代码review

新增范围贯穿前端INCLUDE/EXCLUDE、唯一API、后端白名单/计数/幂等、任务关联、快照、keyset及恢复配置身份。SELECTED包含与FILTERED排除复用export_task_orders，不新增迁移，不写来源订单。旧canonical版本1固定字节及configuration-v1保持原值；非空排除采用新版本。

日期使用AntD公开DatePicker.generatePicker与已锁定Dayjs，内部文本解析和日历运算都以UTC承载香港墙上时间组成，最后仍经原toUtcInstant转换。没有新增rc-picker直接依赖、日期库或功能目录API。

review修正与诊断：

- 表头操作改为整批页动作，容量不足时拒绝整个操作，避免逐行回调导致部分取消或选择。
- 为排除任务加载持久关联并纳入配置身份，避免重启后恢复成完整筛选范围或错误采用异配置块；小范围真实文件/恢复用例已通过。
- 首轮前端210项有205通过、5失败，见[首轮报告](../../../artifacts/003-select-all-filtered/frontend-green.json)。日期历史/重置暴露RHF缺少显式空值时保留初始日期，已在现有红灯后修正；日期清空定位改为精确名称，避免误匹配全局“清空已选”。组合用例的时间预算问题及后继断言另行核验，不放宽业务断言或增加固定等待。
- 新增[无效日期草稿回归](../../../frontend/src/features/orders/__tests__/order-date-validation.test.tsx)，TC-310实际使用真实表单，将旧合法时间改为不存在的日期；确认阻止查询且不悄悄提交旧值/空条件，现有输入桥接通过，无为测试反向补实现。
- Windows中运行中的后端jar阻止原目录repackage，首轮[构建日志](../../../artifacts/003-select-all-filtered/backend-verify.log)记录文件占用。新增可覆盖的exportflow.buildDirectory，默认仍为backend/target；本次verify输出到artifacts独立目录，不停止正在运行的应用，不停止或重启共享容器。

性能、大规模文件、来源不足200000/200001和旧浏览器免验保持原口径。本轮仅三行新增恢复文件、1002笔计数批界断言及过滤后的常规功能回归。

## 最终功能回归与质量门禁

本轮于2026-10-12开始，2026-10-13（Asia/Hong_Kong）完成收尾；审批日期保持原记录。

前端按完整执行及仅失败范围复验合并，211项全部通过；相同文件/完整用例名称及同名参数化次序只保留最新实际执行结果，不累加重复执行、不把筛选跳过项当免验。America/New_York额外两项通过，不重复计入211项。日期重置真实缺陷已经修正；无效日期草稿也有独立通过证据。

组合用例操作两个真实AntD日期及秒级时间弹层，实际完成耗时15.93秒，原15秒运行预算中止异步流程并污染后继用例。表单按钮查询缩小到form，单条组合用例给予45秒jsdom运行预算，未修改API、业务断言或增加固定等待；两条针对性复验全部通过。此耗时是组件测试工具开销，不作为产品性能数据。

| 操作 | 结果 | 证据 |
| --- | --- | --- |
| 前端完整执行与日期修正复验合并 | 211通过、0失败 | [完整首轮](../../../artifacts/003-select-all-filtered/frontend-green.json)、[日期修正](../../../artifacts/003-select-all-filtered/frontend-date-fixed.json)、[最终组合复验](../../../artifacts/003-select-all-filtered/frontend-boundaries-final.json) |
| TZ=America/New_York，日期文件筛选TC-311本机夏令时 | 2通过、0失败，退出0 | [时区报告](../../../artifacts/003-select-all-filtered/frontend-timezone-green.json) |
| pnpm --dir frontend build（含typecheck） | 退出0，生成dist；入口体积警告保留，不提高警告阈值 | [最终构建](../../../artifacts/003-select-all-filtered/frontend-build-final.log) |
| 后端增量聚焦测试 | 30通过、0失败/错误/跳过，包含三项真实小文件/恢复 | [日志](../../../artifacts/003-select-all-filtered/backend-green.log)、[JUnit](../../../artifacts/003-select-all-filtered/backend-green-reports) |

后端剩余范围verify退出0、BUILD SUCCESS；两段已完成JUnit去重汇总：Surefire单元8项，Failsafe集成315项，失败/错误/跳过均为0。Spotless、Checkstyle与高优先级SpotBugs检查通过，JaCoCo报告和可执行jar已生成；[正式门禁日志](../../../artifacts/003-select-all-filtered/backend-verify-resumed.log)、[统计](../../../artifacts/003-select-all-filtered/implementation-summary.json)可复核。聚焦30项与正式执行含重叠用例，不能相加当独立通过数。

第一段实际命令（主动停止，已完成JUnit保留）：

```powershell
./backend/mvnw.cmd -f backend/pom.xml '-Dexportflow.buildDirectory=D:/project/ai-vibe-coding/export-flow/artifacts/003-select-all-filtered/backend-target' '-Dit.test=*,!*UnitTest,!ApiPerformanceTest,!ExcelPerformanceTest,!ExcelResourceTest,!CheckpointRecoveryTest,!ExcelContentTest#TC230_TC233_fullVolumeNineColumnsMatchEveryProvidedSourceRow,!DownloadBoundaryTest#TC234_realHttpDisconnectClosesStreamOutsideTransactionAndKeepsSuccess,!ExportCreationContractTest#TC216_twoHundredThousandSelectedIsAcceptedButLargerFilteredScopeIsRejected' '-Dexportflow.evidenceRoot=D:/project/ai-vibe-coding/export-flow/artifacts/003-select-all-filtered' verify
```

上述过滤沿用负责人性能取消、来源规模不足及历史免验；没有执行性能专项或完整来源大文件用例。新增文件/恢复只核对3笔订单；常规故障批界小文件不是性能测量。

[产物SHA-256及Git状态](../../../artifacts/003-select-all-filtered/delivery-manifest.json)记录独立目录jar、前端dist以及本次工作区状态。已有三个project-export-flow容器保持healthy，未新建或停止共享容器。Windows首次构建遇到运行jar占用，本版使用独立输出目录完成验证。使用新版本时运行./make backend；若前后端都要重新启动，使用./make dev，已有实例先Ctrl+C停止。没有自动启动或替换用户应用。

## 五分钟超时免验与分段执行

2026-10-13（Asia/Hong_Kong），负责人明确“五分钟超时可以跳过”。处理该指令时，ExecutionSupervisionTest七项已结束且全部通过，其中真实五分钟单调时间超时用例也已完成；保留此前实际证据，本轮后续不再执行该用例。免验范围同步[订单任务记录](../../../specs/002-order-management/tasks.md#本轮免验确认)，不更改产品定义。

已停止第一段Maven验证，非零退出是主动停止，不计功能失败；原[第一段日志](../../../artifacts/003-select-all-filtered/backend-verify-isolated.log)保留。停止时下一类ExportCommandHandlerTest正在启动，精确核对日志时间与表创建时间后，仅回收该用例的schema、专属账号及vhost；[回收证据](../../../artifacts/003-select-all-filtered/interrupted-resource-cleanup.json)确认专属schema不存在，原订单仍为110000条，没有停止共享容器。

第二段实际仅执行ExportCommandHandlerTest、ExportExclusionRecoveryTest、ExportProgressMySqlTest、GeneratorBoundaryTest、RecoveryFailureWindowsTest、WorkbookLifecycleTest及WorkerFailureWindowsTest，并继续正式静态检查和打包门禁。其余已完成集成类保留原始JUnit，不为收尾再次重跑；Surefire单元重复执行按报告身份去重。最终统计明确为分段实际执行的汇总，不将第一段写为BUILD SUCCESS。

第二段实际命令（已排除五分钟用例，仅补剩余范围）：

```powershell
./backend/mvnw.cmd -f backend/pom.xml '-Dexportflow.buildDirectory=D:/project/ai-vibe-coding/export-flow/artifacts/003-select-all-filtered/backend-target' '-Dit.test=ExportCommandHandlerTest,ExportExclusionRecoveryTest,ExportProgressMySqlTest,GeneratorBoundaryTest,RecoveryFailureWindowsTest,WorkbookLifecycleTest,WorkerFailureWindowsTest' '-Dexportflow.evidenceRoot=D:/project/ai-vibe-coding/export-flow/artifacts/003-select-all-filtered' verify
```
