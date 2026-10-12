# 任务：全量选择、全部反选与日期时间控件

**版本／日期**：0.1已批准／2026-10-12。输入[规格](spec.md)、[计划](plan.md)、[状态](data-model.md)与[契约](contracts/export-scope.md)。测试阶段获批；2026-10-13：功能实施、代码review、功能回归及质量门禁已完成；性能/规模和原浏览器免验保持，免验不记实测通过。

## 审批记录

| 门禁 | 范围 | 状态 | 负责人／日期／依据 |
| --- | --- | --- | --- |
| 规格审批 | 三份核心及状态/契约0.1；US1至US3 | 已批准 | 项目负责人；2026-10-12在具体规格审批请求后回复“开始吧”，授权测试先行 |
| 测试阶段审批 | TC-301至TC-316及受影响旧回归、红灯证据/缺口；规格0.1 | 已批准 | 项目负责人；2026-10-12在具体测试阶段及覆盖缺口审批请求后回复“继续开发”，授权本次生产实施；未到达断言须实现后补验，保留性能与规模免验 |

负责人已明确跨页全选、逐条取消、全部反选及AntD日期控件；这属于需求澄清，不追认为上述任一审批。订单002的历史授权、免验与性能取消记录保留，不代替003新增行为门禁。

## 按阶段任务

- [x] T-001 核对原状态/API/持久化/日期边界，准备本规格、目录/函数及产品增量草稿；纯准备，AC/SC不适用。
- [x] T-002 已编写并执行前端TC-301至TC-311及旧回归调整，不写生产代码；结果与未到达断言见下表。
- [x] T-003 已编写并执行后端TC-312至TC-316，复用来源和用例隔离，取得红灯及旧行为基线；门禁材料已准备，提交第二次审批。
- [x] T-004 已实现US1的选择mode/reducer/Hook/页面/表格，保留当前页工具，容量拒绝可见。
- [x] T-005 实现US2前端的排除字段、schema、冻结和导出范围编排，仍使用唯一业务API。
- [x] T-006 实现US3两个DatePicker及桥接，增加已锁定dayjs直接依赖并调整原表单测试操作。
- [x] T-007 实现US2后端输入/范围/计数/规范化/幂等兼容。
- [x] T-008 实现US2任务关联角色、快照、配置身份与批读SQL，验证真实文件/恢复，保持原事务。
- [x] T-009 按工具链执行功能回归及质量门禁，review所有本次代码，修正问题且保留必要红/绿证据；不运行性能专项。
- [x] T-010 同步002的HTTP/OpenAPI/设计附录、新规格及README/实际验证记录；只记录实际结果，不覆盖旧门禁或免验。

## AC—实现任务映射

| 唯一标准链接 | 实现任务 | 所属故事 |
| --- | --- | --- |
| [全量范围](../../docs/product/acceptance-criteria.md#ac-073) | T-004 | US1 |
| [生命周期](../../docs/product/acceptance-criteria.md#ac-074) | T-004 | US1 |
| [导出范围](../../docs/product/acceptance-criteria.md#ac-075) | T-005、T-007、T-008 | US2 |
| [边界禁用](../../docs/product/acceptance-criteria.md#ac-076) | T-004、T-007 | US1/2 |
| [逐条取消](../../docs/product/acceptance-criteria.md#ac-077) | T-004、T-005、T-008 | US1/2 |
| [全部反选](../../docs/product/acceptance-criteria.md#ac-078) | T-004、T-005 | US1/2 |
| [持久化兼容](../../docs/product/acceptance-criteria.md#ac-079) | T-007、T-008 | US2 |
| [日期控件](../../docs/product/acceptance-criteria.md#ac-080) | T-006 | US3 |
| [原重置](../../docs/product/acceptance-criteria.md#ac-003) | T-006 | US3回归 |
| [原范围校验](../../docs/product/acceptance-criteria.md#ac-005) | T-006 | US3回归 |
| [原香港时区](../../docs/product/acceptance-criteria.md#ac-022) | T-006 | US3回归 |
| [原当前页工具](../../docs/product/acceptance-criteria.md#ac-033) | T-004 | US1回归 |
| [原筛选导出](../../docs/product/acceptance-criteria.md#ac-008) | T-005、T-007 | US2回归 |
| [上限](../../docs/product/acceptance-criteria.md#ac-019) | T-004、T-007 | US2回归 |
| [明确拒绝](../../docs/product/acceptance-criteria.md#ac-030) | T-005 | US2回归 |
| [UNKNOWN](../../docs/product/acceptance-criteria.md#ac-051) | T-005、T-007 | US2回归 |
| [成功留页](../../docs/product/acceptance-criteria.md#ac-072) | T-005 | US2回归 |

## AC—测试用例映射

| 唯一标准链接 | 用例 | 测试任务 | 层级／测试先行历史 |
| --- | --- | --- | --- |
| [全量范围](../../docs/product/acceptance-criteria.md#ac-073) | TC-301、302 | T-002 | unit/component；已编写/已执行，新增行为红灯及后续覆盖缺口见下表 |
| [生命周期](../../docs/product/acceptance-criteria.md#ac-074) | TC-302、305 | T-002 | component；已编写/已执行，新增行为红灯及后续覆盖缺口见下表 |
| [导出范围](../../docs/product/acceptance-criteria.md#ac-075) | TC-306、307、308、314、316 | T-002/003 | component/contract/integration；已编写/已执行，新增行为红灯及后续覆盖缺口见下表 |
| [边界禁用](../../docs/product/acceptance-criteria.md#ac-076) | TC-304、305、312、314 | T-002/003 | unit/component/integration；已编写/已执行，新增行为红灯及后续覆盖缺口见下表 |
| [逐条取消](../../docs/product/acceptance-criteria.md#ac-077) | TC-302、303、306、316 | T-002/003 | component/integration；已编写/已执行，新增行为红灯及后续覆盖缺口见下表 |
| [全部反选](../../docs/product/acceptance-criteria.md#ac-078) | TC-301、303、306 | T-002 | unit/component；已编写/已执行，新增行为红灯及后续覆盖缺口见下表 |
| [持久化兼容](../../docs/product/acceptance-criteria.md#ac-079) | TC-309、312、313、314、315、316 | T-002/003 | contract/真实MySQL/文件；已编写/已执行，新增行为红灯及后续覆盖缺口见下表 |
| [日期控件](../../docs/product/acceptance-criteria.md#ac-080) | TC-310、311 | T-002 | unit/component；已编写/已执行，新增行为红灯及后续覆盖缺口见下表 |
| [原重置](../../docs/product/acceptance-criteria.md#ac-003) | TC-310、311、原TC-206 | T-002 | component；新增红灯，旧回归已执行，具体结果见下表 |
| [原范围校验](../../docs/product/acceptance-criteria.md#ac-005) | TC-310、原TC-205 | T-002 | component；新增红灯，旧回归已执行，具体结果见下表 |
| [原香港时区](../../docs/product/acceptance-criteria.md#ac-022) | TC-311、原TC-204、211 | T-002 | unit/component；新增红灯，旧回归已执行，具体结果见下表 |
| [原当前页工具](../../docs/product/acceptance-criteria.md#ac-033) | TC-303、原TC-207 | T-002 | unit/component；新增红灯，旧回归已执行，具体结果见下表 |
| [原筛选导出](../../docs/product/acceptance-criteria.md#ac-008) | TC-309、314、原TC-209 | T-002/003 | contract/integration；已执行，新增红灯/原行为回归及未到达断言见下表 |
| [上限](../../docs/product/acceptance-criteria.md#ac-019) | TC-305、314 | T-002/003 | component/integration；已编写/已执行，新增行为红灯及后续覆盖缺口见下表 |
| [明确拒绝](../../docs/product/acceptance-criteria.md#ac-030) | TC-308、原TC-210 | T-002 | component；已执行，新增红灯/原行为回归及未到达断言见下表 |
| [UNKNOWN](../../docs/product/acceptance-criteria.md#ac-051) | TC-307、308、313 | T-002/003 | component/真实MySQL；已编写/已执行，新增行为红灯及后续覆盖缺口见下表 |
| [成功留页](../../docs/product/acceptance-criteria.md#ac-072) | TC-308、原TC-208 | T-002 | component；已执行，新增红灯/原行为回归及未到达断言见下表 |

## SC—验证映射

| 唯一标准链接 | 验证用例／任务 | 环境 | 证据计划 |
| --- | --- | --- | --- |
| [增量质量](../../docs/product/acceptance-criteria.md#sc-015) | TC-301至TC-316、T-009 | Node22.14.0/pnpm10.34.6、Java21.0.12.1、已有MySQL/RabbitMQ、只读来源和用例专属schema/vhost | [实际验证](../../docs/validation/003-select-all-filtered/verification.md)、[统计](../../artifacts/003-select-all-filtered/test-stage-summary.json)；预期红灯及类型/编译证据已保存；实施后前端211项、后端聚焦30项、纽约时区2项全部通过，前端构建与后端过滤verify通过，真实小文件及恢复完成；[最终统计](../../artifacts/003-select-all-filtered/implementation-summary.json)和[最终门禁](../../docs/validation/003-select-all-filtered/verification.md#最终功能回归与质量门禁)记录当前质量证据 |

## 测试进度与证据

以下保留测试先行历史；所有增量用例实施后补证通过，最新执行结果见[最终功能回归](../../docs/validation/003-select-all-filtered/verification.md#最终功能回归与质量门禁)。

| 用例／任务 | 标准反向链接 | 计划行为与文件 | 编写／执行／结果 |
| --- | --- | --- | --- |
| TC-301／T-002 | [全量范围](../../docs/product/acceptance-criteria.md#ac-073)、[全部反选](../../docs/product/acceptance-criteria.md#ac-078) | [order-selection.test.ts](../../frontend/src/features/orders/__tests__/order-selection.test.ts)；全选无ID/全局补集、双反选恢复 | 已编写／已执行（2026-10-12）／mode与全局补集预期失败；[证据](../../docs/validation/003-select-all-filtered/verification.md#红灯原因与测试review) |
| TC-302／T-002 | [范围](../../docs/product/acceptance-criteria.md#ac-073)、[生命周期](../../docs/product/acceptance-criteria.md#ac-074)、[逐条](../../docs/product/acceptance-criteria.md#ac-077) | [order-management.test.tsx](../../frontend/src/features/orders/__tests__/order-management.test.tsx)；多页当前行显示与例外回显 | 已编写／已执行（2026-10-12）／全选入口缺失；跨页后续断言未到达；[证据](../../docs/validation/003-select-all-filtered/verification.md#红灯原因与测试review) |
| TC-303／T-002 | [逐条](../../docs/product/acceptance-criteria.md#ac-077)、[反选](../../docs/product/acceptance-criteria.md#ac-078)、[旧工具](../../docs/product/acceptance-criteria.md#ac-033) | [order-selection.test.ts](../../frontend/src/features/orders/__tests__/order-selection.test.ts)；行/表头/当前页操作，两种模式 | 已编写／已执行（2026-10-12）／新模式局部操作预期失败；原INCLUDE页工具通过；[证据](../../docs/validation/003-select-all-filtered/verification.md#红灯原因与测试review) |
| TC-304／T-002 | [边界](../../docs/product/acceptance-criteria.md#ac-076) | [order-selection.test.ts](../../frontend/src/features/orders/__tests__/order-selection.test.ts)；例外容量/非法ID拒绝可见，无数据造入数据库 | 已编写／已执行（2026-10-12）／容量notice/非法输入安全拒绝预期失败；[证据](../../docs/validation/003-select-all-filtered/verification.md#红灯原因与测试review) |
| TC-305／T-002 | [生命周期](../../docs/product/acceptance-criteria.md#ac-074)、[边界](../../docs/product/acceptance-criteria.md#ac-076)、[上限](../../docs/product/acceptance-criteria.md#ac-019) | [order-management.test.tsx](../../frontend/src/features/orders/__tests__/order-management.test.tsx)；查询草稿/POP/重置/失效/空/超上限/全部排除 | 已编写／已执行（2026-10-12）／全选入口缺失；生命周期/禁用后续断言未到达；[证据](../../docs/validation/003-select-all-filtered/verification.md#红灯原因与测试review) |
| TC-306／T-002 | [范围](../../docs/product/acceptance-criteria.md#ac-075)、[逐条](../../docs/product/acceptance-criteria.md#ac-077)、[反选](../../docs/product/acceptance-criteria.md#ac-078) | [order-export.test.tsx](../../frontend/src/features/orders/__tests__/order-export.test.tsx)；真实POST的包含/排除与原已提交filter | 已编写／已执行（2026-10-12）／全选入口缺失；实际排除POST后续断言未到达；[证据](../../docs/validation/003-select-all-filtered/verification.md#红灯原因与测试review) |
| TC-307／T-002 | [范围](../../docs/product/acceptance-criteria.md#ac-075)、[UNKNOWN](../../docs/product/acceptance-criteria.md#ac-051) | [order-export-state.test.tsx](../../frontend/src/features/orders/__tests__/order-export-state.test.tsx)；例外深度冻结和原键原请求 | 已编写／已执行（2026-10-12）／schema拒绝排除；新增冻结/原键重试未到达；[证据](../../docs/validation/003-select-all-filtered/verification.md#红灯原因与测试review) |
| TC-308／T-002 | [范围](../../docs/product/acceptance-criteria.md#ac-075)、[拒绝](../../docs/product/acceptance-criteria.md#ac-030)、[UNKNOWN](../../docs/product/acceptance-criteria.md#ac-051)、[留页](../../docs/product/acceptance-criteria.md#ac-072) | [order-export.test.tsx](../../frontend/src/features/orders/__tests__/order-export.test.tsx)；成功/明确拒绝/未知的选择清理与保留 | 已编写／已执行（2026-10-12）／全选入口缺失；新增范围结果处理未到达，旧行为回归已执行；[证据](../../docs/validation/003-select-all-filtered/verification.md#红灯原因与测试review) |
| TC-309／T-002 | [兼容](../../docs/product/acceptance-criteria.md#ac-079)、[旧筛选](../../docs/product/acceptance-criteria.md#ac-008) | [order-api.test.ts](../../frontend/src/features/orders/__tests__/order-api.test.ts)；新排除schema、空/缺省及原请求边界 | 已编写／已执行（2026-10-12）／合法排除预期失败；非法/缺省旧请求通过；[证据](../../docs/validation/003-select-all-filtered/verification.md#红灯原因与测试review) |
| TC-310／T-002 | [日期](../../docs/product/acceptance-criteria.md#ac-080)、[重置](../../docs/product/acceptance-criteria.md#ac-003)、[范围校验](../../docs/product/acceptance-criteria.md#ac-005) | [order-date-picker.test.tsx](../../frontend/src/features/orders/__tests__/order-date-picker.test.tsx)；真实AntD日历/时间/单侧/清空/倒置 | 已编写／已执行（2026-10-12）／独立控件能力基线通过；业务页面缺控件预期失败；[证据](../../docs/validation/003-select-all-filtered/verification.md#红灯原因与测试review) |
| TC-311／T-002 | [日期](../../docs/product/acceptance-criteria.md#ac-080)、[重置](../../docs/product/acceptance-criteria.md#ac-003)、[香港时区](../../docs/product/acceptance-criteria.md#ac-022) | [order-date-picker.test.tsx](../../frontend/src/features/orders/__tests__/order-date-picker.test.tsx)；同文件及order-boundaries.test.tsx；不同本机时区（含夏令时跳时）、UTC、历史/重置/冻结 | 已编写／已执行（2026-10-12）／业务页面缺控件预期失败；NY重复两项同为红灯，内部解析待核验；[证据](../../docs/validation/003-select-all-filtered/verification.md#红灯原因与测试review) |
| TC-312／T-003 | [兼容](../../docs/product/acceptance-criteria.md#ac-079)、[边界](../../docs/product/acceptance-criteria.md#ac-076) | [CreationInputBoundaryTest.java](../../backend/src/test/java/com/exportflow/exporttasks/CreationInputBoundaryTest.java)；排除字段结构与输入约束 | 已编写／已执行（2026-10-12）／非法/互斥输入通过；合法/空排除预期失败，原长度后续断言未到达；[证据](../../docs/validation/003-select-all-filtered/verification.md#红灯原因与测试review) |
| TC-313／T-003 | [兼容](../../docs/product/acceptance-criteria.md#ac-079)、[UNKNOWN](../../docs/product/acceptance-criteria.md#ac-051) | [IdempotencyMySqlTest.java](../../backend/src/test/java/com/exportflow/exporttasks/IdempotencyMySqlTest.java)；旧v1字节、新集合顺序与同键变化 | 已编写／已执行（2026-10-12）／旧v1字节断言通过；新字段400，空排除复用/v2/UNKNOWN后续未到达；[证据](../../docs/validation/003-select-all-filtered/verification.md#红灯原因与测试review) |
| TC-314／T-003 | [兼容](../../docs/product/acceptance-criteria.md#ac-079)、[范围](../../docs/product/acceptance-criteria.md#ac-075)、[边界](../../docs/product/acceptance-criteria.md#ac-076)、[上限](../../docs/product/acceptance-criteria.md#ac-019)、[旧筛选](../../docs/product/acceptance-criteria.md#ac-008) | [ExportExclusionMySqlTest.java](../../backend/src/test/java/com/exportflow/exporttasks/ExportExclusionMySqlTest.java)；真实交集计数、空范围、不存在排除、只读源、应用关联事务 | 已编写／已执行（2026-10-12）／合法排除400；计数/分批关联/回滚后续未到达；[证据](../../docs/validation/003-select-all-filtered/verification.md#红灯原因与测试review) |
| TC-315／T-003 | [兼容](../../docs/product/acceptance-criteria.md#ac-079) | [ExportExclusionMySqlTest.java](../../backend/src/test/java/com/exportflow/exporttasks/ExportExclusionMySqlTest.java)；同文件；旧SELECTED/FILTERED读回、旧configuration-v1字节、新配置区别及keyset排除 | 已编写／已执行（2026-10-12）／旧批读/configuration-v1通过；持久排除keyset/配置身份预期失败；[证据](../../docs/validation/003-select-all-filtered/verification.md#红灯原因与测试review) |
| TC-316／T-003 | [兼容](../../docs/product/acceptance-criteria.md#ac-079)、[范围](../../docs/product/acceptance-criteria.md#ac-075)、[逐条](../../docs/product/acceptance-criteria.md#ac-077) | [ExportExclusionRecoveryTest.java](../../backend/src/test/java/com/exportflow/worker/ExportExclusionRecoveryTest.java)；真实少量Excel内容、重启范围恢复、不采用异配置断点 | 已编写／已执行（2026-10-12）／重启后真实生成器包含额外行预期失败；Excel与Worker续写尚未到达；[证据](../../docs/validation/003-select-all-filtered/verification.md#红灯原因与测试review) |

2026-10-12实际命令、运行环境与原始证据见[验证记录](../../docs/validation/003-select-all-filtered/verification.md#实际命令与结果)。按最终测试源汇总：前端210项，160通过、50预期失败；后端30项，14通过、16预期失败，无Errors/Skipped。NY时区2项重复执行为预期失败，不重复计数。前端typecheck、后端test-compile及仅四份Java测试的格式化退出0。统计由[汇总脚本](../../artifacts/003-select-all-filtered/summarize-tests.cjs)合并实际报告复现，不把筛选未重跑项算免验，不以红灯代替验收。

实施后计划分别运行前端test:run/typecheck/build及后端Wrapper质量门禁；后端必须按原免验过滤性能与大规模文件用例，不直接无筛选verify。保留红灯并新增绿灯证据，不打开性能开关、不生成大规模导出。

## 缺口与阶段完成检查

规格与测试阶段已批准，功能实施及代码review已完成；[覆盖缺口表](../../docs/validation/003-select-all-filtered/verification.md#覆盖缺口与恢复条件)列明早期UI/schema失败阻断的后续断言、真实文件与Worker续写尚未到达、日期组件内部文本解析的DST核验，以及责任人/替代证据/恢复条件。负责人在提交阶段材料后以“继续开发”批准本阶段及覆盖缺口，前端211项、后端增量30项及UTC/NY组件复验已补证通过，历史未到达断言已恢复执行；正式后端门禁已通过。真实数据不足200000/200001的上限来源场景仅记录条件不足，沿用负责人“不算失败、不补造数据”指令；前端容量/超限反馈使用有限内存输入及MSW元数据断言，不能冒充真实数据库规模验收。真实MySQL功能范围用现有小范围订单。原浏览器手工/性能免验保持，不新增浏览器E2E要求。

- [x] 获得规格批准且核对本次测试环境；类型与测试编译正常。
- [x] 新增用例编写完整并有验收反向引用；17项标准映射至16组具名用例，覆盖是否实际到达另列。
- [x] 新增行为取得可复现红灯，旧行为结果/工具问题分开记录；工具问题已修正并重跑。
- [x] 覆盖缺口、影响、替代断言与恢复条件材料已准备并提交负责人评审。
- [x] 负责人以“继续开发”批准本测试阶段及提交的覆盖缺口；实施后按恢复条件补证。
- [x] 测试阶段明确批准，允许进行T-004至T-008。

## 文档核验

2026-10-12已读取原项目治理、产品、领域规则、标准模板、选择模型、表单、API、幂等/快照/SQL和相关测试，确认新排除必须贯穿执行。已核对前端、任务范围与恢复ADR：未限定关联表仅用于SELECTED，范围持久化、消息定位与恢复裁决保持既有架构。补齐notice生命周期、日期桥接的本机夏令时隔离及原日期行为回归映射。

2026-10-12文档核验命令node artifacts/003-select-all-filtered/check-documents.cjs退出0；[核验报告](../../artifacts/003-select-all-filtered/document-review-check.json)检查20份文件（9份规格/产品/验证文档与11份测试源）、478个本地链接与锚点、16张Markdown表、17项验收实施/测试映射、16项具名用例及代码反向引用，错误0。git -c core.safecrlf=false diff --check退出0。报告属于文档审计，不代替功能测试或负责人审批。规格阶段初次8份文档核验通过；本轮扩大核验范围并同步实际测试证据。测试review及修正记录见[验证记录](../../docs/validation/003-select-all-filtered/verification.md#红灯原因与测试review)。此处数字和仅测试/文档的范围属于测试先行历史；生产实施、review及扩大后的文档核验另列，性能专项始终不执行。

## 实施完成与最终review

完成日期：2026-10-13（Asia/Hong_Kong）。审批及测试启动日期仍为2026-10-12。

T-004至T-010完成。前端211/211项、纽约时区2项、后端增量30项全部通过；两段功能验证合并单元8项与集成315项，失败/错误/跳过均为0，并完成格式、静态检查、覆盖率报告和可执行jar。重复执行不累计计数。[最终证据](../../docs/validation/003-select-all-filtered/verification.md#最终功能回归与质量门禁)保存确切过滤范围、命令、原始报告及产物摘要。

review修正日期重置与表头整页原子操作；检查补集生命周期、冻结、旧摘要字节、排除范围持久/重启恢复、只读来源及香港日期转换。未新增页面API、造数或容器。来源不足200000/200001仅记录，不计失败；性能及既有免验不恢复。后续任务页/SSE仍属后续规格，不是本次未完成任务。使用新版本时需重新启动后端，已启动的实例先Ctrl+C停止。

2026-10-13最终文档核验退出0：[报告](../../artifacts/003-select-all-filtered/document-review-check.json)覆盖47份文件（14份Markdown、12份测试源、21份生产源），1095个本地链接/锚点、114张表、17项标准映射和16组具名测试，错误0；git diff --check退出0。前述20份文件等数字属于测试先行历史，最终核验范围已扩大。
