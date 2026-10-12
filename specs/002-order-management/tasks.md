# 任务：订单管理与后端异步流程

**输入**：[spec.md](spec.md)、[plan.md](plan.md)及全部附属产物。  
**版本／日期**：0.4（既有来源修订）／2026-10-12；原审批范围与日期保留在下方记录。
**状态**：规格已批准；测试先行证据为0.4-test-03。2026-10-11负责人明确“可以开始开发了”，开发和代码review已完成，本轮收尾；当前功能回归和正式质量门禁通过，性能已取消，剩余验证已由负责人确认本轮无需验证，不表示整个规格验收通过。默认整个范围统一审批，不按章节规避门禁。

2026-10-11早先规格修订：按负责人要求将全部前端业务 API 统一设计到 `shared/api/api.ts`，更新目标树、函数归属及T-021/T-022/T-023/T-034；当时仅修改规格文档。后续按负责人“只是前端不用关注”确认前端只处理创建结果，取消详情接口和详情页，后端仍完成全部链路；产品基线和契约已同步。本会话明确授权开发后，已实施统一API并删除无用占位，见[本次证据](../../docs/validation/002-order-management/api-implementation.md)。

当前实施状态：前端153项回归、类型检查和构建通过；后端生产模块已齐备。2026-10-12按负责人要求删除造数工具，全部订单直接读取容器内exportflow.orders的110000条已有记录，当前来源复验见[来源记录](../../docs/validation/002-order-management/provided-data.md)；200000/200001规模因数据不足无法执行时只记前置条件缺口，不计功能失败。详细历史批次与失败记录见[实施进度](../../docs/validation/002-order-management/implementation-progress.md)，内部职责见[实施补充](implementation-notes.md)。统一代码review发现项及红绿证据见[review记录](../../docs/validation/002-order-management/code-review.md)，尚未宣称全规格验收完成。

## 审批记录

| 门禁 | 范围与版本 | 状态 | 批准人 | 日期 | 结论与依据 |
| --- | --- | --- | --- | --- | --- |
| 规格审批 | spec/plan/tasks、research/decision-record/dependency-versions/data-model/function-design/function-design-reference/excel-design/quickstart/contracts 0.4 | 已批准 | 项目负责人 | 2026-10-11 | 本会话明确澄清“我的意思是叫你进入规格实施阶段”，据此批准现有规格并进入测试先行；不提前批准尚未提交证据的测试阶段 |
| 技术/产品决策 | ADR-0008执行权/异常恢复/块断点/就绪对账子集、ADR-0009、D-01至D-12与产品口径 | 已批准决策 | 项目负责人 | 2026-10-07 | 明确要求“解决下所有待决策项，按你的建议来”；见[决策记录](decision-record.md)，不授权测试或生产代码 |
| API组织与前端范围修订 | D-13/D-14；创建成功留在订单页、取消详情接口及详情页、前端仅创建而后端完成链路 | 已确认需求与设计方向 | 项目负责人 | 2026-10-11 | 明确要求集中API、“不要任务详情的东西”“只是前端不用关注”；见[决策记录](decision-record.md)，不替代规格或测试阶段审批 |
| 测试先行阶段审批及开发授权 | 规格0.4；既有0.4-test-03测试与运行缺口；按T-020至T-034顺序实施 | 已明确授权开始开发 | 项目负责人 | 2026-10-11 | 本会话明确“继续规格文档吧，可以开始开发了，但是要记得吧api的独立处理了”，随后要求“不要需要的要删除”；据此进入实施并集中API、删除旧占位。保留G-01至G-03及原始测试证据，逐项恢复执行；未执行或跳过的测试不标通过，授权不代替验收结论 |
| 本轮收尾及剩余验证 | 本期交付；性能取消与剩余验证免验 | 本轮已收尾；剩余无需验证 | 项目负责人 | 2026-10-12 | 本会话明确“标记为不用验证吧”；见[本轮免验确认](#本轮免验确认)，不将免验记作实测达标 |

## 实施与测试任务

### 阶段一：规格和前置决策

- [x] T-001 [文档准备；产品验收不适用] 阅读治理、产品、规则、ADR、学习稿和实际骨架；高风险分级，创建核心及附属文档；仅文档完成，不代表获批。
- [x] T-002 [决策定稿；不直接交付产品行为] 按负责人本次明确授权定稿D-01至D-12及其他待决方向，已同步产品、ADR与工程规则；[决策记录](decision-record.md)和[精确版本](dependency-versions.md)留存依据。资源实际准备与验证在后续获批阶段执行，未标环境通过。
- [x] T-003 [规格门禁] 项目负责人于2026-10-11明确批准现有0.4核心及附属规格，依据见上方审批记录；进入测试先行阶段。

### 阶段二：所有故事的测试先行

- [x] T-010 [测试工具准备；不直接实现产品行为] 规格批准后准备Vitest/MSW/Testing Library、tests/backend测试harness和合成夹具；文件详见[逐文件表](function-design-reference.md#逐文件职责清单)。后端正式pom、Wrapper、入口、生产迁移和生成实现不可提前生成。
- [x] T-011 [US1] 编写订单筛选/分页/选择/URL/展示/取消测试 TC-201至TC-207、TC-211；逐条反向引用下方AC映射。
- [x] T-012 [US2/US3/US4] 编写导出配置、冻结提交、同键核对、成功留在订单页及静态导航测试 TC-208至TC-210、TC-212。
- [x] T-013 [US1/基础] 编写后端启动/HTTP与真实订单查询测试 TC-213至TC-215；按已定测试工具准备真实MySQL隔离并验证。
- [x] T-014 [US2/US3] 编写范围、幂等、原子创建、UNKNOWN及创建响应直接确认测试 TC-216至TC-219；不以mock repository证明唯一约束。
- [x] T-015 [US3] 编写真实RabbitMQ/MySQL故障与期限测试 TC-220至TC-222；屏障控制竞态，实际策略不合规必须测到拒绝。
- [x] T-016 [US3] 编写Worker消息/执行权/真实生成能力边界测试 TC-223至TC-225；测试替身只放测试目录，不伪装真实Excel。
- [x] T-018 [US3/文件] 编写真实Excel、独立堆资源、实际进度、逐行准确性和下载用例 TC-230至TC-234；真实POI与文件，不以替身满足文件指标。
- [x] T-019 [US3/恢复] 编写断点/就绪/成功提交窗口、kill、旧fence、恢复预算与清理互斥 TC-235、TC-236，使用真实MySQL/RabbitMQ/文件系统和可控故障。
- [ ] T-017 [测试证据与门禁] 运行可执行测试，保留命令、环境、预期行为失败、已有回归通过及阻塞；正式工程缺失不能当红灯。测试先行历史见[测试结果](../../docs/validation/002-order-management/red-results.md)，已补充子场景及运行阻塞见[覆盖缺口](../../docs/validation/002-order-management/coverage-gaps.md)。负责人已明确授权开发，统一API结果见[本次证据](../../docs/validation/002-order-management/api-implementation.md)；本任务保留未完成，后续逐项恢复真实装配并核验全部证据。

### 阶段三：获第二次明确审批后的最小实现

下列任务已获负责人明确开发授权，先落实统一API和旧占位删除，再按依赖推进。函数设计评审从[创建任务流程](function-design.md#一创建任务)进入；各任务所覆盖的AC由后面的逐项映射反向定义，逐文件／函数归属在[实现对照附录](function-design-reference.md)中明确，不自行增加未评审签名。

- [x] T-020 [共享基础] 修改AppProviders，增加QueryClient；实现http-client/api-error、分页类型、定点金额及UTC/香港格式化，配置dev代理和获批生产依赖。2026-10-11共享基础、依赖及格式化已实现，153项前端回归、类型检查与构建通过。
- [x] T-021 [US1] 修改订单页面、表单、表格、useOrders、展示模型与类型；新增 `shared/api/api.ts` 并实现订单查询API，删除原订单API注释占位；新增查询/URL/选择Hook、纯函数和schema，落实已提交状态与页面内存边界。
- [x] T-022 [US2] 实现导出字段对话框、用例与Hook及类型；在同一 `shared/api/api.ts` 实现任务创建API，删除原任务API注释占位；固定幂等提交、错误分类和原键原载荷重试。 提交用例只接收CreateExportTaskApi并返回快照；Hook内部applySubmissionResult统一确认/同键重试的页面反馈，页面操作异常不改写已确认成功。
- [x] T-023 [US4/静态导航] 复用任务管理文字占位页、菜单、路由和404，回归导航一致性；本期不新增任务详情、状态查询、进度展示、下载按钮、SSE或轮询。2026-10-11删除未使用的任务表格/查询Hook注释占位；4项导航回归通过，见[本次证据](../../docs/validation/002-order-management/api-implementation.md)。
- [x] T-024 [后端基础] 正式初始化Maven/Wrapper/SpringBoot入口、外层配置、错误模型及处理器，生成V1/V2迁移；确认隔离schema及准确版本后才执行，不自动写示例资源。
- [x] T-025 [US1] 实现OrderController/映射/QueryService/领域/端口/MyBatis记录/XML；删除造数脚本／工具及合成SQL，直接适配exportflow.orders并验证只读数据、稳定查询及边界；本轮复验记录见[来源设计](provided-data.md)。
- [x] T-026 [US2/US3] 实现CreateExportTaskService/CreateExportTransaction、范围与摘要、任务/字段领域、幂等和任务持久适配、创建响应协议和DTO映射；取消详情GET，保留内部幂等核对读取；包含事务冲突后新事务回读。
- [x] T-027 [US3] 实现Outbox domain/repository/MyBatis/XML、发布端口/AMQP/codec、调度与拓扑有效策略核验；包含尝试预留、退避、坏记录隔离、确认与回写核对。
- [x] T-028 [US3] 实现独立OutboxTimeoutService和原子超时/关闭SQL；固定锁序与DB时间，启动恢复优先扫描，SENT/已开工保护。
- [x] T-029 [US3；架构前置] 实现真实消费者、handler/TaskExecutionService/ExecutionSupervisor、执行权/MyBatis/XML/V3与手动ACK。支持首次认领、过期有界接管、心跳/失权/单次期限；只有合法终态提交才ACK，不扫描RUNNING补投。

- [x] T-032 [US3/数据] 实现OrderRepository真实keyset批读、ExportDataCollectionService、DataBlockCodec、CheckpointRepository/MyBatis/XML/V4断点部分；块先持久化，再原子引用/游标/计数/版本。
- [x] T-033 [US3/文件] 实现真实PoiExportFileGenerator、ExcelRowProjector/Assembler/Validator、FileStorage/Local适配、ArtifactRepository/Mapper/XML/V4产物部分及OrphanCleanupService；按有效执行权登记READY/采用/失败并保护引用。 文件端口resolve只定位，统一校验器校验新临时及已有READY文件；新文件就绪登记后不重复校验。
- [x] T-034 [US3/下载] 实现DownloadExportFileService/DownloadHandle、Controller下载和OpenAPI；前端下载操作留后续任务规格；后端独立核对成功引用、响应头和实际文件，不持事务传输大文件。

### 阶段四：验证与收尾

- [x] T-030 [跨故事；含负责人免验收尾] 适用自动化和正式质量门禁已完成；剩余手工、broker重启、同时间戳边界及性能本轮无需验证，规模不足只记录；实际通过与免验分别归档，见[本轮免验确认](#本轮免验确认)。
- [x] T-031 [文档同步；阶段说明关联下方映射] 实施完成后同步AGENTS项目地图、README/模块README、学习稿实际阶段、验证记录及待决项；保留原基础骨架证据。核对文件、函数、AC/SC和测试双向映射，未满足项不标完成。

## AC—实现任务映射

| AC链接 | 实现任务 | 覆盖说明 |
| --- | --- | --- |
| [`AC-001`](../../docs/product/acceptance-criteria.md#ac-001) | T-021、T-025 | 初次查询与默认页 |
| [`AC-002`](../../docs/product/acceptance-criteria.md#ac-002) | T-021、T-025 | 提交条件与AND语义 |
| [`AC-003`](../../docs/product/acceptance-criteria.md#ac-003) | T-021 | 重置状态和立即请求 |
| [`AC-004`](../../docs/product/acceptance-criteria.md#ac-004) | T-021、T-025 | 页码／大小切换 |
| [`AC-005`](../../docs/product/acceptance-criteria.md#ac-005) | T-021、T-025 | 两端范围校验 |
| [`AC-020`](../../docs/product/acceptance-criteria.md#ac-020) | T-021 | 跨页与新筛选选择生命周期 |
| [`AC-021`](../../docs/product/acceptance-criteria.md#ac-021) | T-021、T-025 | 订单号精确匹配 |
| [`AC-022`](../../docs/product/acceptance-criteria.md#ac-022) | T-020、T-021、T-025 | UTC与闭区间 |
| [`AC-023`](../../docs/product/acceptance-criteria.md#ac-023) | T-020、T-021、T-025 | 定点金额与包含边界 |
| [`AC-024`](../../docs/product/acceptance-criteria.md#ac-024) | T-025 | 默认双列稳定排序 |
| [`AC-033`](../../docs/product/acceptance-criteria.md#ac-033) | T-021 | 已确认选择工具 |
| [`AC-034`](../../docs/product/acceptance-criteria.md#ac-034) | T-021、T-025 | 已确认升降序切换；不更改默认 |
| [`AC-035`](../../docs/product/acceptance-criteria.md#ac-035) | T-021、T-025 | 已确认keyword OR与其余AND |
| [`AC-006`](../../docs/product/acceptance-criteria.md#ac-006) | T-022、T-026 | SELECTED任务创建；真实生成纳入本期 |
| [`AC-007`](../../docs/product/acceptance-criteria.md#ac-007) | T-022 | 禁用及可见原因 |
| [`AC-008`](../../docs/product/acceptance-criteria.md#ac-008) | T-022、T-026 | FILTERED完整条件无分页 |
| [`AC-009`](../../docs/product/acceptance-criteria.md#ac-009) | T-022、T-026 | 空结果拒绝 |
| [`AC-010`](../../docs/product/acceptance-criteria.md#ac-010) | T-026、T-029、T-032、T-033 | 持久幂等、重复命令与唯一成功产物；真实文件窗口 |
| [`AC-011`](../../docs/product/acceptance-criteria.md#ac-011) | T-026 | 大范围异步受理，不等待文件 |
| [`AC-019`](../../docs/product/acceptance-criteria.md#ac-019) | T-022、T-026 | 上限边界与数量提示 |
| [`AC-072`](../../docs/product/acceptance-criteria.md#ac-072) | T-022、T-026 | 创建接口直接返回已提交结果，订单页成功提示、关闭配置并保留查询状态，无跳转或额外任务查询 |
| [`AC-030`](../../docs/product/acceptance-criteria.md#ac-030) | T-022 | 明确失败保留草稿 |
| [`AC-050`](../../docs/product/acceptance-criteria.md#ac-050) | T-026 | 提交确认和真实可查记录 |
| [`AC-051`](../../docs/product/acceptance-criteria.md#ac-051) | T-022、T-026 | 未知结果同次提交找回 |
| [`AC-052`](../../docs/product/acceptance-criteria.md#ac-052) | T-028 | 独立到期扫描和原子关闭 |
| [`AC-053`](../../docs/product/acceptance-criteria.md#ac-053) | T-027、T-028、T-029 | 共同锁序／期限与旧回调 |
| [`AC-054`](../../docs/product/acceptance-criteria.md#ac-054) | T-027 | 单条坏记录隔离 |
| [`AC-055`](../../docs/product/acceptance-criteria.md#ac-055) | T-027 | 领取即持久尝试与崩溃退避 |
| [`AC-056`](../../docs/product/acceptance-criteria.md#ac-056) | T-027 | 真实broker保留与策略验证 |
| [`AC-049`](../../docs/product/acceptance-criteria.md#ac-049) | T-027、T-029、T-032、T-033 | 排队保留、认领后真实执行、未确认重交付、ACK晚于终态 |
| [`AC-029`](../../docs/product/acceptance-criteria.md#ac-029) | T-023 | 回归未知路由恢复 |
| [`AC-041`](../../docs/product/acceptance-criteria.md#ac-041) | T-021、T-023 | 回归URL、菜单、标题一致 |
| [`AC-060`](../../docs/product/acceptance-criteria.md#ac-060) | T-022、T-026 | 已确认字段集合、有序与至少一项 |
| [`AC-061`](../../docs/product/acceptance-criteria.md#ac-061) | T-025、T-026 | 已确认独立后端拒绝且无残留 |
| [`AC-064`](../../docs/product/acceptance-criteria.md#ac-064) | T-026 | 已确认同键异载荷409与等价规范化 |
| [`AC-065`](../../docs/product/acceptance-criteria.md#ac-065) | T-021 | 已确认URL恢复与关键字保护 |
| [`AC-013`](../../docs/product/acceptance-criteria.md#ac-013) | T-033、T-034 | 本期只验证后端成功文件直接下载；任务页下载操作后续验收 |
| [`AC-014`](../../docs/product/acceptance-criteria.md#ac-014) | T-034 | 本期后端独立拒绝非成功下载；前端禁用及原因显示延期，不能判整条通过 |
| [`AC-015`](../../docs/product/acceptance-criteria.md#ac-015) | T-029、T-033 | 本期后端真实结果元数据持久化；前端显示延期，不能判整条通过 |
| [`AC-018`](../../docs/product/acceptance-criteria.md#ac-018) | T-033、T-034 | 永久保留配置、跨重启下载与清理不误删；不以短期测试假证无限时间 |
| [`AC-067`](../../docs/product/acceptance-criteria.md#ac-067) | T-029、T-032、T-033、T-034 | 本期真实Excel闭环，格式/内容/结果可核对 |
| [`AC-068`](../../docs/product/acceptance-criteria.md#ac-068) | T-029、T-032、T-033 | 异常接管、断点/安全重建、预算、旧身份与ACK |
| [`AC-069`](../../docs/product/acceptance-criteria.md#ac-069) | T-032、T-033 | 持久引用保护、就绪对账及采用清理互斥 |
| [`AC-071`](../../docs/product/acceptance-criteria.md#ac-071) | T-033 | Excel已完成批次刷入临时文件，结束后校验与原子转换 |

## AC—测试用例映射

本表记录目标覆盖层级，不表示各子场景已全部编写或执行；实际状态见[测试进度与证据](#测试进度与证据)，未编写与断言不足见[源码审计](../../docs/validation/002-order-management/coverage-audit.md)。

| AC链接 | 测试用例 | 测试任务 | 层级／预期证据 |
| --- | --- | --- | --- |
| [`AC-001`](../../docs/product/acceptance-criteria.md#ac-001) | TC-201、TC-215 | T-011、T-013 | component、integration |
| [`AC-002`](../../docs/product/acceptance-criteria.md#ac-002) | TC-202、TC-215 | T-011、T-013 | component/integration、integration |
| [`AC-003`](../../docs/product/acceptance-criteria.md#ac-003) | TC-202 | T-011 | component/integration |
| [`AC-004`](../../docs/product/acceptance-criteria.md#ac-004) | TC-203、TC-215 | T-011、T-013 | component、integration |
| [`AC-005`](../../docs/product/acceptance-criteria.md#ac-005) | TC-204、TC-205、TC-214 | T-011、T-013 | unit、component、contract |
| [`AC-020`](../../docs/product/acceptance-criteria.md#ac-020) | TC-207 | T-011 | unit/component |
| [`AC-021`](../../docs/product/acceptance-criteria.md#ac-021) | TC-204、TC-215 | T-011、T-013 | unit、integration |
| [`AC-022`](../../docs/product/acceptance-criteria.md#ac-022) | TC-204、TC-215 | T-011、T-013 | unit、integration |
| [`AC-023`](../../docs/product/acceptance-criteria.md#ac-023) | TC-204、TC-215 | T-011、T-013 | unit、integration |
| [`AC-024`](../../docs/product/acceptance-criteria.md#ac-024) | TC-215 | T-013 | integration |
| [`AC-033`](../../docs/product/acceptance-criteria.md#ac-033) | TC-207 | T-011 | unit/component |
| [`AC-034`](../../docs/product/acceptance-criteria.md#ac-034) | TC-203、TC-215 | T-011、T-013 | component、integration |
| [`AC-035`](../../docs/product/acceptance-criteria.md#ac-035) | TC-202、TC-215 | T-011、T-013 | component/integration、integration |
| [`AC-006`](../../docs/product/acceptance-criteria.md#ac-006) | TC-209、TC-216 | T-012、T-014 | unit/integration、contract/integration |
| [`AC-007`](../../docs/product/acceptance-criteria.md#ac-007) | TC-208 | T-012 | component |
| [`AC-008`](../../docs/product/acceptance-criteria.md#ac-008) | TC-209、TC-216 | T-012、T-014 | unit/integration、contract/integration |
| [`AC-009`](../../docs/product/acceptance-criteria.md#ac-009) | TC-208、TC-216 | T-012、T-014 | component、contract/integration |
| [`AC-010`](../../docs/product/acceptance-criteria.md#ac-010) | TC-217、TC-223、TC-235 | T-014、T-016、T-019 | integration、unit/contract/integration |
| [`AC-011`](../../docs/product/acceptance-criteria.md#ac-011) | TC-216、TC-228 | T-014、T-030 | contract/integration、performance |
| [`AC-019`](../../docs/product/acceptance-criteria.md#ac-019) | TC-208、TC-216 | T-012、T-014 | component、contract/integration |
| [`AC-072`](../../docs/product/acceptance-criteria.md#ac-072) | TC-210、TC-219 | T-012、T-014 | frontend integration/contract、真实MySQL contract；成功仅凭POST响应，保留订单页和查询状态 |
| [`AC-030`](../../docs/product/acceptance-criteria.md#ac-030) | TC-210 | T-012 | integration/contract |
| [`AC-050`](../../docs/product/acceptance-criteria.md#ac-050) | TC-218、TC-219 | T-014 | integration、integration/contract |
| [`AC-051`](../../docs/product/acceptance-criteria.md#ac-051) | TC-210、TC-218 | T-012、T-014 | integration/contract、integration |
| [`AC-052`](../../docs/product/acceptance-criteria.md#ac-052) | TC-222 | T-015 | integration |
| [`AC-053`](../../docs/product/acceptance-criteria.md#ac-053) | TC-221、TC-222、TC-224 | T-015、T-016 | integration |
| [`AC-054`](../../docs/product/acceptance-criteria.md#ac-054) | TC-221 | T-015 | integration |
| [`AC-055`](../../docs/product/acceptance-criteria.md#ac-055) | TC-221 | T-015 | integration |
| [`AC-056`](../../docs/product/acceptance-criteria.md#ac-056) | TC-220 | T-015 | integration |
| [`AC-049`](../../docs/product/acceptance-criteria.md#ac-049) | TC-220、TC-221、TC-223、TC-224、TC-235 | T-015、T-016、T-019 | integration、unit/contract/integration；TC-221补充已RUNNING旧命令不占新PENDING投递候选 |
| [`AC-029`](../../docs/product/acceptance-criteria.md#ac-029) | TC-212 | T-012 | component/integration |
| [`AC-041`](../../docs/product/acceptance-criteria.md#ac-041) | TC-212 | T-012 | component/integration |
| [`AC-060`](../../docs/product/acceptance-criteria.md#ac-060) | TC-208、TC-216 | T-012、T-014 | component、contract/integration |
| [`AC-061`](../../docs/product/acceptance-criteria.md#ac-061) | TC-214、TC-216 | T-013、T-014 | contract、contract/integration |
| [`AC-064`](../../docs/product/acceptance-criteria.md#ac-064) | TC-217 | T-014 | integration |
| [`AC-065`](../../docs/product/acceptance-criteria.md#ac-065) | TC-206 | T-011 | unit/component |
| [`AC-013`](../../docs/product/acceptance-criteria.md#ac-013) | TC-234 | T-018 | 后端contract/integration/manual；直接HTTP/浏览器URL真实下载；任务界面点击下载留后续 |
| [`AC-014`](../../docs/product/acceptance-criteria.md#ac-014) | TC-234 | T-018 | 后端contract/integration验证非成功拒绝；前端禁用及可见原因延期 |
| [`AC-015`](../../docs/product/acceptance-criteria.md#ac-015) | TC-232、TC-234 | T-018 | 后端integration验证真实元数据及下载头；前端元数据显示延期 |
| [`AC-018`](../../docs/product/acceptance-criteria.md#ac-018) | TC-234、TC-236 | T-018、T-019 | contract/integration/manual、integration |
| [`AC-067`](../../docs/product/acceptance-criteria.md#ac-067) | TC-225、TC-230、TC-233、TC-234 | T-016、T-018 | unit/integration、integration/performance、integration、contract/integration/manual |
| [`AC-068`](../../docs/product/acceptance-criteria.md#ac-068) | TC-223、TC-224、TC-232、TC-235 | T-016、T-018、T-019 | unit/contract/integration、integration |
| [`AC-069`](../../docs/product/acceptance-criteria.md#ac-069) | TC-235、TC-236 | T-019 | integration |
| [`AC-071`](../../docs/product/acceptance-criteria.md#ac-071) | TC-230、TC-231、TC-235、TC-236 | T-018、T-019 | integration/resource/fault，验证批间临时文件已有内容、完成前无正式引用、原子改名及失败恢复 |

## SC—验证映射

本表原“已通过”是2026-10-11历史数据方案的状态；2026-10-12来源调整后的实际结果和规模缺口以[来源复验](../../docs/validation/002-order-management/provided-data.md)为准。

参考环境和采样方法统一采用[已定稿方案](decision-record.md#参考环境及事实核对)，版本采用[精确版本表](dependency-versions.md)。以下记录实际完成范围与剩余度量；首轮测试先行证据保留在[历史结果](../../docs/validation/002-order-management/red-results.md)，当前报告来源见[自动化汇总](../../docs/validation/002-order-management/green-results.md)。

| SC链接 | 验证用例／任务 | 运行环境 | 证据计划／判定边界 |
| --- | --- | --- | --- |
| [`SC-001`](../../docs/product/acceptance-criteria.md#sc-001) | TC-227／T-030 | 真实MySQL8.4与获批参考环境、固定110000只读订单 | 负责人确认本轮无需性能验证；原API测量已取消，未生成完整P95／原始报告，不标达标，见[收尾记录](../../docs/validation/002-order-management/provided-data.md#本轮收尾与性能取消) |
| [`SC-002`](../../docs/product/acceptance-criteria.md#sc-002) | TC-228／T-030 | 同环境与真实创建事务，MQ正常/隔离故障，大SELECTED/FILTERED请求 | 负责人确认本轮无需性能验证；原API测量已取消，大SELECTED样本未收齐，MQ不可用场景未执行，不标达标，见[收尾记录](../../docs/validation/002-order-management/provided-data.md#本轮收尾与性能取消) |
| [`SC-003`](../../docs/product/acceptance-criteria.md#sc-003) | TC-230／T-030 | 获批硬件/存储，真实110000与200000行9列；各5次、完整源读取到校验发布计时 | 内容及可打开验证已通过，performance-excel-110000.json五次独立测量全部成功，10.055–12.625秒；200000行因来源不足仅记缺口，见[来源证据](../../docs/validation/002-order-management/provided-data.md) |
| [`SC-004`](../../docs/product/acceptance-criteria.md#sc-004) | TC-231／T-030 | 独立Java21进程最大堆512MB，真实110000行9列及所有中间块/验证 | ExcelResourceTest独立512MiB进程及五次110000行9列性能全部完成且无OOM；五次堆峰值400–457.404MiB，见[来源证据](../../docs/validation/002-order-management/provided-data.md) |
| [`SC-005`](../../docs/product/acceptance-criteria.md#sc-005) | TC-232／T-030 | 真实生成与失败，MySQL进度/终态与安全错误 | 本期验证后端成功100/失败实际进度和错误摘要，excel-content.md；前端展示及持续通知留后续，本期不判完整标准通过 |
| [`SC-006`](../../docs/product/acceptance-criteria.md#sc-006) | TC-217、TC-235／T-030 | 隔离真实MySQL唯一键、并发屏障、MQ重复和恢复 | 真实并发幂等、重复命令及恢复唯一产物已通过，见[当前汇总](../../docs/validation/002-order-management/green-results.md) |
| [`SC-007`](../../docs/product/acceptance-criteria.md#sc-007) | TC-230、TC-233／T-030 | 固定源与真实xlsx流式逐行核对 | ExcelContentTest24项中23通过、1来源规模缺口；110000行9列与原来源流式逐行核对通过，200000行未执行，见[来源证据](../../docs/validation/002-order-management/provided-data.md) |
| [`SC-008`](../../docs/product/acceptance-criteria.md#sc-008) | TC-226／T-030 | 1280×720与ADR目标浏览器矩阵 | 负责人确认本轮无需浏览器视觉／焦点手工验证；无实测证据，见[检查表](../../docs/validation/002-order-management/browser-checklist.md) |
| [`SC-009`](../../docs/product/acceptance-criteria.md#sc-009) | TC-229／T-030 | 锁定前后端与获批测试工具、真实依赖 | 153项前端、typecheck/build通过；后端当前来源300项功能通过，04正式Wrapper质量门禁通过。性能已取消，剩余手工由负责人确认本轮无需验证，见[当前功能结果](../../docs/validation/002-order-management/provided-results.md) |
| [`SC-010`](../../docs/product/acceptance-criteria.md#sc-010) | TC-215、TC-217、TC-218、TC-220、TC-221、TC-235／T-030 | 隔离真实MySQL/MQ/文件故障恢复 | 当前来源真实查询／创建／MySQL／RabbitMQ投递及连接故障恢复通过；候选饥饿修复后04批次复验通过。共享broker进程重启持久性由负责人确认本轮无需验证，未实测，见[当前功能结果](../../docs/validation/002-order-management/provided-results.md) |

原TC-230至TC-233从预留恢复为本期正式用例，含义保持对应文件/资源/进度/准确性验证。参考环境方案已定稿；实际产物/运行验证未完成时不能标通过。本期SC后端最终值验证不等于[`AC-012`](../../docs/product/acceptance-criteria.md#ac-012)的持续任务列表更新已交付。

## 测试进度与证据

下表2026-10-11通过结果保留为历史证据，当前来源改动复验见[来源记录](../../docs/validation/002-order-management/provided-data.md)。

**当前阶段**：本轮开发、review及文档收尾已完成；前端153项与类型／构建通过，当前来源后端功能300项通过及04正式质量门禁通过。性能已取消，剩余验证由负责人确认本轮无需执行，见[本轮免验确认](#本轮免验确认)。下方测试先行阶段未完成项和旧316项统计保留为历史，不作为当前待办或实测通过依据。

**测试先行历史（0.4-test-03）**：测试环境已检查，运行缺口及完整性评审未完成，彼时尚未获得开发授权。**业务测试代码**：原审计34组场景及8组断言已逐项补充源码/手工用例，具体见[补充对照](../../docs/validation/002-order-management/scenario-completion.md)；真实装配与自动化结果见[当前汇总](../../docs/validation/002-order-management/green-results.md)，未完成手工项及进程重启仍明确留缺口。**最近业务测试执行日期**：2026-10-11。实际报告见[执行结果](../../docs/validation/002-order-management/red-results.md)，逐点缺口见[源码审计](../../docs/validation/002-order-management/coverage-audit.md)；前端加载阻塞和后端入口缺失跳过单独记录，不冒充行为红灯。

| 用例／测试任务 | 标准链接 | 目标文件／验证 | 编写／执行 | 实际结果、环境与证据 |
| --- | --- | --- | --- | --- |
| TC-201／T-011 | [`AC-001`](../../docs/product/acceptance-criteria.md#ac-001) | `frontend/src/test/setup.ts`、`frontend/src/test/handlers.ts`、`frontend/src/features/orders/__tests__/order-management.test.tsx`；首次渲染、无条件第一页、四个区域  补充：[order-boundaries](../../frontend/src/features/orders/__tests__/order-boundaries.test.tsx) | 已编写／已执行通过；2026-10-11 | [当前自动化汇总](../../docs/validation/002-order-management/green-results.md)；原始红灯及测试先行阻塞保留在[历史结果](../../docs/validation/002-order-management/red-results.md)，不作为当前状态。 |
| TC-202／T-011 | [`AC-002`](../../docs/product/acceptance-criteria.md#ac-002)、[`AC-003`](../../docs/product/acceptance-criteria.md#ac-003)、[`AC-035`](../../docs/product/acceptance-criteria.md#ac-035) | `frontend/src/features/orders/__tests__/order-management.test.tsx`；条件AND提交、重置立即查询、keyword与其余筛选  补充：[order-boundaries](../../frontend/src/features/orders/__tests__/order-boundaries.test.tsx) | 已编写／已执行通过；2026-10-11 | [当前自动化汇总](../../docs/validation/002-order-management/green-results.md)；原始红灯及测试先行阻塞保留在[历史结果](../../docs/validation/002-order-management/red-results.md)，不作为当前状态。 |
| TC-203／T-011 | [`AC-004`](../../docs/product/acceptance-criteria.md#ac-004)、[`AC-034`](../../docs/product/acceptance-criteria.md#ac-034) | `frontend/src/features/orders/__tests__/order-management.test.tsx`；20/50/100、翻页保留、排序回第一页  补充：[order-boundaries](../../frontend/src/features/orders/__tests__/order-boundaries.test.tsx) | 已编写／已执行通过；2026-10-11 | [当前自动化汇总](../../docs/validation/002-order-management/green-results.md)；原始红灯及测试先行阻塞保留在[历史结果](../../docs/validation/002-order-management/red-results.md)，不作为当前状态。 |
| TC-204／T-011 | [`AC-005`](../../docs/product/acceptance-criteria.md#ac-005)、[`AC-021`](../../docs/product/acceptance-criteria.md#ac-021)、[`AC-022`](../../docs/product/acceptance-criteria.md#ac-022)、[`AC-023`](../../docs/product/acceptance-criteria.md#ac-023) | `frontend/src/features/orders/__tests__/order-query.test.ts`；日期/金额/枚举/DTO映射及范围校验  补充：[order-boundaries](../../frontend/src/features/orders/__tests__/order-boundaries.test.tsx) | 已编写／已执行通过；2026-10-11 | [当前自动化汇总](../../docs/validation/002-order-management/green-results.md)；原始红灯及测试先行阻塞保留在[历史结果](../../docs/validation/002-order-management/red-results.md)，不作为当前状态。 |
| TC-205／T-011 | [`AC-005`](../../docs/product/acceptance-criteria.md#ac-005) | `frontend/src/features/orders/__tests__/order-management.test.tsx`；无效范围不请求，字段错误可访问  补充：[order-boundaries](../../frontend/src/features/orders/__tests__/order-boundaries.test.tsx) | 已编写／已执行通过；2026-10-11 | [当前自动化汇总](../../docs/validation/002-order-management/green-results.md)；原始红灯及测试先行阻塞保留在[历史结果](../../docs/validation/002-order-management/red-results.md)，不作为当前状态。 |
| TC-206／T-011 | [`AC-065`](../../docs/product/acceptance-criteria.md#ac-065) | `frontend/src/features/orders/__tests__/order-query.test.ts`；URL非法值恢复、历史导航、关键字不泄漏  补充：[order-boundaries](../../frontend/src/features/orders/__tests__/order-boundaries.test.tsx) | 已编写／已执行通过；2026-10-11 | [当前自动化汇总](../../docs/validation/002-order-management/green-results.md)；原始红灯及测试先行阻塞保留在[历史结果](../../docs/validation/002-order-management/red-results.md)，不作为当前状态。 |
| TC-207／T-011 | [`AC-020`](../../docs/product/acceptance-criteria.md#ac-020)、[`AC-033`](../../docs/product/acceptance-criteria.md#ac-033) | `frontend/src/features/orders/__tests__/order-selection.test.ts`；跨页集合与全选反选清空、新筛选清空  补充：[order-boundaries](../../frontend/src/features/orders/__tests__/order-boundaries.test.tsx) | 已编写／已执行通过；2026-10-11 | [当前自动化汇总](../../docs/validation/002-order-management/green-results.md)；原始红灯及测试先行阻塞保留在[历史结果](../../docs/validation/002-order-management/red-results.md)，不作为当前状态。 |
| TC-208／T-012 | [`AC-007`](../../docs/product/acceptance-criteria.md#ac-007)、[`AC-009`](../../docs/product/acceptance-criteria.md#ac-009)、[`AC-019`](../../docs/product/acceptance-criteria.md#ac-019)、[`AC-060`](../../docs/product/acceptance-criteria.md#ac-060) | `frontend/src/features/orders/__tests__/order-export.test.tsx`；字段默认九列／最后一项、disabled理由、焦点  补充：[order-boundaries](../../frontend/src/features/orders/__tests__/order-boundaries.test.tsx) | 已编写／已执行通过；2026-10-11 | [当前自动化汇总](../../docs/validation/002-order-management/green-results.md)；原始红灯及测试先行阻塞保留在[历史结果](../../docs/validation/002-order-management/red-results.md)，不作为当前状态。 |
| TC-209／T-012 | [`AC-006`](../../docs/product/acceptance-criteria.md#ac-006)、[`AC-008`](../../docs/product/acceptance-criteria.md#ac-008) | `frontend/src/features/orders/__tests__/order-export.test.tsx`、`frontend/src/features/orders/__tests__/order-export-state.test.tsx`；两种范围与有序字段、重复确认冻结；确认/同键重试共用Hook反馈，页面操作异常仍保留CONFIRMED且不重复POST  补充：[order-boundaries](../../frontend/src/features/orders/__tests__/order-boundaries.test.tsx) | 已编写／已执行通过；2026-10-11 | [当前自动化汇总](../../docs/validation/002-order-management/green-results.md)；原始红灯及测试先行阻塞保留在[历史结果](../../docs/validation/002-order-management/red-results.md)，不作为当前状态。 |
| TC-210／T-012 | [`AC-072`](../../docs/product/acceptance-criteria.md#ac-072)、[`AC-030`](../../docs/product/acceptance-criteria.md#ac-030)、[`AC-051`](../../docs/product/acceptance-criteria.md#ac-051) | `frontend/src/features/orders/__tests__/order-export.test.tsx`、`frontend/src/features/orders/__tests__/order-export-state.test.tsx`、`frontend/src/features/orders/__tests__/order-api.test.ts`；201/200只凭创建响应成功提示及任务编号、清选择/关闭配置、保留当前筛选/keyword/分页/排序、不跳转、不追加任务GET/SSE/轮询、确认/同键重试共用Hook反馈、页面操作异常仍保留成功且不重发POST、拒绝保留、超时/断连/取消同键找回  补充：[order-boundaries](../../frontend/src/features/orders/__tests__/order-boundaries.test.tsx) | 已编写／已执行通过；2026-10-11 | [当前自动化汇总](../../docs/validation/002-order-management/green-results.md)；原始红灯及测试先行阻塞保留在[历史结果](../../docs/validation/002-order-management/red-results.md)，不作为当前状态。 |
| TC-211／T-011 | 工具/状态基础验证，无新增独立产品编号 | `frontend/src/features/orders/__tests__/order-management.test.tsx`、`frontend/src/features/orders/__tests__/order-api.test.ts`；取消与乱序、加载/空/错恢复、query key隔离、HTTP超时/取消/响应协议  补充：[order-boundaries](../../frontend/src/features/orders/__tests__/order-boundaries.test.tsx) | 已编写／已执行通过；2026-10-11 | [当前自动化汇总](../../docs/validation/002-order-management/green-results.md)；原始红灯及测试先行阻塞保留在[历史结果](../../docs/validation/002-order-management/red-results.md)，不作为当前状态。 |
| TC-212／T-012 | [`AC-029`](../../docs/product/acceptance-criteria.md#ac-029)、[`AC-041`](../../docs/product/acceptance-criteria.md#ac-041) | `frontend/src/test/setup.ts`、`frontend/src/test/handlers.ts`、`frontend/src/features/orders/__tests__/navigation.test.tsx`；订单/静态任务页菜单与标题、404/恢复，任务页无业务请求 | 已编写／已执行通过；2026-10-11 | [当前自动化汇总](../../docs/validation/002-order-management/green-results.md)；原始红灯及测试先行阻塞保留在[历史结果](../../docs/validation/002-order-management/red-results.md)，不作为当前状态。 |
| TC-213／T-013 | 工具/状态基础验证，无新增独立产品编号 | `backend/src/test/java/com/exportflow/RuntimeConfigurationTest.java`；真实入口与配置、资源隔离、消费者能力开关  补充：[RuntimeSecurityTest](../../backend/src/test/java/com/exportflow/RuntimeSecurityTest.java)、[RuntimeCapabilityTest](../../backend/src/test/java/com/exportflow/RuntimeCapabilityTest.java) | 已编写／当前来源复验通过；2026-10-12 | [当前来源结果](../../docs/validation/002-order-management/provided-results.md)；原始红灯及测试先行阻塞保留在[历史结果](../../docs/validation/002-order-management/red-results.md)，不作为当前状态。 |
| TC-214／T-013 | [`AC-005`](../../docs/product/acceptance-criteria.md#ac-005)、[`AC-061`](../../docs/product/acceptance-criteria.md#ac-061) | `backend/src/test/java/com/exportflow/orders/OrderApiContractTest.java`；JSON16MiB边界/413且事务未进入/完整尾部检查；HTTP非法分页/枚举/时间/金额、错误码和脱敏  补充：[CreationInputBoundaryTest](../../backend/src/test/java/com/exportflow/exporttasks/CreationInputBoundaryTest.java)、[RuntimeSecurityTest](../../backend/src/test/java/com/exportflow/RuntimeSecurityTest.java)、[OrderQueryBoundaryTest](../../backend/src/test/java/com/exportflow/orders/OrderQueryBoundaryTest.java) | 已编写／当前来源复验通过；2026-10-12 | [当前来源结果](../../docs/validation/002-order-management/provided-results.md)；原始红灯及测试先行阻塞保留在[历史结果](../../docs/validation/002-order-management/red-results.md)，不作为当前状态。 |
| TC-215／T-013 | [`AC-001`](../../docs/product/acceptance-criteria.md#ac-001)、[`AC-002`](../../docs/product/acceptance-criteria.md#ac-002)、[`AC-004`](../../docs/product/acceptance-criteria.md#ac-004)、[`AC-021`](../../docs/product/acceptance-criteria.md#ac-021)、[`AC-022`](../../docs/product/acceptance-criteria.md#ac-022)、[`AC-023`](../../docs/product/acceptance-criteria.md#ac-023)、[`AC-024`](../../docs/product/acceptance-criteria.md#ac-024)、[`AC-034`](../../docs/product/acceptance-criteria.md#ac-034)、[`AC-035`](../../docs/product/acceptance-criteria.md#ac-035)、[`SC-010`](../../docs/product/acceptance-criteria.md#sc-010) | `backend/src/test/java/com/exportflow/orders/OrderQueryMySqlTest.java`；ProvidedOrdersTest真实来源／字段映射／禁止写入；真实MySQL完整筛选、精确/子串/边界、稳定排序  补充：[OrderQueryBoundaryTest](../../backend/src/test/java/com/exportflow/orders/OrderQueryBoundaryTest.java) | 已编写／当前来源复验通过；2026-10-12 | [当前来源结果](../../docs/validation/002-order-management/provided-results.md)；原始红灯及测试先行阻塞保留在[历史结果](../../docs/validation/002-order-management/red-results.md)，不作为当前状态。 |
| TC-216／T-014 | [`AC-006`](../../docs/product/acceptance-criteria.md#ac-006)、[`AC-008`](../../docs/product/acceptance-criteria.md#ac-008)、[`AC-009`](../../docs/product/acceptance-criteria.md#ac-009)、[`AC-011`](../../docs/product/acceptance-criteria.md#ac-011)、[`AC-019`](../../docs/product/acceptance-criteria.md#ac-019)、[`AC-060`](../../docs/product/acceptance-criteria.md#ac-060)、[`AC-061`](../../docs/product/acceptance-criteria.md#ac-061) | `backend/src/test/java/com/exportflow/exporttasks/ExportCreationContractTest.java`；创建合法/空/字段/不存在/超限/200000边界、事务无残留  补充：[CreationInputBoundaryTest](../../backend/src/test/java/com/exportflow/exporttasks/CreationInputBoundaryTest.java) | 已编写／当前来源复验通过；2026-10-12 | [当前来源结果](../../docs/validation/002-order-management/provided-results.md)；原始红灯及测试先行阻塞保留在[历史结果](../../docs/validation/002-order-management/red-results.md)，不作为当前状态。 |
| TC-217／T-014 | [`AC-010`](../../docs/product/acceptance-criteria.md#ac-010)、[`AC-064`](../../docs/product/acceptance-criteria.md#ac-064)、[`SC-006`](../../docs/product/acceptance-criteria.md#sc-006)、[`SC-010`](../../docs/product/acceptance-criteria.md#sc-010) | `backend/src/test/java/com/exportflow/exporttasks/IdempotencyMySqlTest.java`；真实唯一约束并发、同键同请求、异字段顺序冲突  补充：[CreationInputBoundaryTest](../../backend/src/test/java/com/exportflow/exporttasks/CreationInputBoundaryTest.java)、[RuntimeSecurityTest](../../backend/src/test/java/com/exportflow/RuntimeSecurityTest.java) | 已编写／当前来源复验通过；2026-10-12 | [当前来源结果](../../docs/validation/002-order-management/provided-results.md)；原始红灯及测试先行阻塞保留在[历史结果](../../docs/validation/002-order-management/red-results.md)，不作为当前状态。 |
| TC-218／T-014 | [`AC-050`](../../docs/product/acceptance-criteria.md#ac-050)、[`AC-051`](../../docs/product/acceptance-criteria.md#ac-051)、[`SC-010`](../../docs/product/acceptance-criteria.md#sc-010) | `backend/src/test/java/com/exportflow/exporttasks/CreateTransactionMySqlTest.java`；同事务回滚、响应前提交、丢响应与提交不明回读  补充：[CreationInputBoundaryTest](../../backend/src/test/java/com/exportflow/exporttasks/CreationInputBoundaryTest.java)、[CreationAsyncBoundaryTest](../../backend/src/test/java/com/exportflow/exporttasks/CreationAsyncBoundaryTest.java) | 已编写／当前来源复验通过；2026-10-12 | [当前来源结果](../../docs/validation/002-order-management/provided-results.md)；原始红灯及测试先行阻塞保留在[历史结果](../../docs/validation/002-order-management/red-results.md)，不作为当前状态。 |
| TC-219／T-014 | [`AC-050`](../../docs/product/acceptance-criteria.md#ac-050)、[`AC-072`](../../docs/product/acceptance-criteria.md#ac-072) | `backend/src/test/java/com/exportflow/exporttasks/CreationResultMySqlTest.java`；创建201/同键200只返回已提交标识/配置，后端状态变化后的重放仍同任务；无详情GET或Location | 已编写／当前来源复验通过；2026-10-12 | [当前来源结果](../../docs/validation/002-order-management/provided-results.md)；原始红灯及测试先行阻塞保留在[历史结果](../../docs/validation/002-order-management/red-results.md)，不作为当前状态。 |
| TC-220／T-015 | [`AC-056`](../../docs/product/acceptance-criteria.md#ac-056)、[`AC-049`](../../docs/product/acceptance-criteria.md#ac-049)、[`SC-010`](../../docs/product/acceptance-criteria.md#sc-010) | `backend/src/test/java/com/exportflow/dispatch/RabbitMqPublisherTest.java`；真实MQ confirm/return/policy/容量/重启/队列等待保留  补充：[BrokerPolicyBoundaryTest](../../backend/src/test/java/com/exportflow/dispatch/BrokerPolicyBoundaryTest.java)、[PublisherCorrelationTest](../../backend/src/test/java/com/exportflow/dispatch/PublisherCorrelationTest.java)、[OutboxFailureWindowsTest](../../backend/src/test/java/com/exportflow/dispatch/OutboxFailureWindowsTest.java) | 已编写／当前来源复验通过；2026-10-12 | [当前来源结果](../../docs/validation/002-order-management/provided-results.md)；原始红灯及测试先行阻塞保留在[历史结果](../../docs/validation/002-order-management/red-results.md)，不作为当前状态。 连接丢失及恢复已验证，共享broker进程重启已由负责人确认本轮无需验证，历史缺口见[记录](../../docs/validation/002-order-management/coverage-gaps.md)。 |
| TC-221／T-015 | [`AC-049`](../../docs/product/acceptance-criteria.md#ac-049)、[`AC-053`](../../docs/product/acceptance-criteria.md#ac-053)、[`AC-054`](../../docs/product/acceptance-criteria.md#ac-054)、[`AC-055`](../../docs/product/acceptance-criteria.md#ac-055)、[`SC-010`](../../docs/product/acceptance-criteria.md#sc-010) | `backend/src/test/java/com/exportflow/dispatch/OutboxDispatchMySqlTest.java`；真实MySQL/MQ领取崩溃、旧token、持久退避、坏记录隔离；已RUNNING旧命令不占新PENDING候选／等待计数  补充：[OutboxFailureWindowsTest](../../backend/src/test/java/com/exportflow/dispatch/OutboxFailureWindowsTest.java)、[OutboxDeadlineRaceTest](../../backend/src/test/java/com/exportflow/dispatch/OutboxDeadlineRaceTest.java) | 已编写／当前来源复验通过；2026-10-12 | [当前来源结果](../../docs/validation/002-order-management/provided-results.md)；原始红灯及测试先行阻塞保留在[历史结果](../../docs/validation/002-order-management/red-results.md)，不作为当前状态。 |
| TC-222／T-015 | [`AC-052`](../../docs/product/acceptance-criteria.md#ac-052)、[`AC-053`](../../docs/product/acceptance-criteria.md#ac-053) | `backend/src/test/java/com/exportflow/dispatch/OutboxTimeoutMySqlTest.java`；固定窗口/扫描启动周期、到期与认领/SENT竞态、失败关闭同事务  补充：[OutboxDeadlineRaceTest](../../backend/src/test/java/com/exportflow/dispatch/OutboxDeadlineRaceTest.java)、[DispatchSchedulingTest](../../backend/src/test/java/com/exportflow/dispatch/DispatchSchedulingTest.java) | 已编写／当前来源复验通过；2026-10-12 | [当前来源结果](../../docs/validation/002-order-management/provided-results.md)；原始红灯及测试先行阻塞保留在[历史结果](../../docs/validation/002-order-management/red-results.md)，不作为当前状态。 |
| TC-223／T-016 | [`AC-010`](../../docs/product/acceptance-criteria.md#ac-010)、[`AC-049`](../../docs/product/acceptance-criteria.md#ac-049)、[`AC-068`](../../docs/product/acceptance-criteria.md#ac-068) | `backend/src/test/java/com/exportflow/worker/ExportCommandHandlerTest.java`；消息校验、权威终态ACK、重复/不可读不提前确认  补充：[WorkerFailureWindowsTest](../../backend/src/test/java/com/exportflow/worker/WorkerFailureWindowsTest.java)、[BadCommandIsolationTest](../../backend/src/test/java/com/exportflow/worker/BadCommandIsolationTest.java)、[RuntimeSecurityTest](../../backend/src/test/java/com/exportflow/RuntimeSecurityTest.java) | 已编写／已执行通过；2026-10-11 | [当前自动化汇总](../../docs/validation/002-order-management/green-results.md)；原始红灯及测试先行阻塞保留在[历史结果](../../docs/validation/002-order-management/red-results.md)，不作为当前状态。 |
| TC-224／T-016 | [`AC-053`](../../docs/product/acceptance-criteria.md#ac-053)、[`AC-049`](../../docs/product/acceptance-criteria.md#ac-049)、[`AC-068`](../../docs/product/acceptance-criteria.md#ac-068) | `backend/src/test/java/com/exportflow/worker/ExecutionLeaseMySqlTest.java`；真实MySQL owner/fence/lease、续租拒绝/UNKNOWN、进度版本、认领监测、单次尝试期限及到期接管预算  补充：[ExecutionSupervisionTest](../../backend/src/test/java/com/exportflow/worker/ExecutionSupervisionTest.java)、[RuntimeCapabilityTest](../../backend/src/test/java/com/exportflow/RuntimeCapabilityTest.java)、[DurabilityGuardTest](../../backend/src/test/java/com/exportflow/worker/DurabilityGuardTest.java)、[WorkerFailureWindowsTest](../../backend/src/test/java/com/exportflow/worker/WorkerFailureWindowsTest.java) | 已编写／已执行通过；2026-10-11 | [当前自动化汇总](../../docs/validation/002-order-management/green-results.md)；原始红灯及测试先行阻塞保留在[历史结果](../../docs/validation/002-order-management/red-results.md)，不作为当前状态。 |
| TC-225／T-016 | [`AC-067`](../../docs/product/acceptance-criteria.md#ac-067) | `backend/src/test/java/com/exportflow/RuntimeConfigurationTest.java`、`backend/src/test/java/com/exportflow/worker/GeneratorBoundaryTest.java`；真实生成能力门禁，READY/明确失败/UNKNOWN与资源关闭，终态先于ACK  补充：[WorkerFailureWindowsTest](../../backend/src/test/java/com/exportflow/worker/WorkerFailureWindowsTest.java)、[RuntimeCapabilityTest](../../backend/src/test/java/com/exportflow/RuntimeCapabilityTest.java)、[ExecutionSupervisionTest](../../backend/src/test/java/com/exportflow/worker/ExecutionSupervisionTest.java) | 已编写／已执行通过；2026-10-11 | [当前自动化汇总](../../docs/validation/002-order-management/green-results.md)；原始红灯及测试先行阻塞保留在[历史结果](../../docs/validation/002-order-management/red-results.md)，不作为当前状态。 |
| TC-226／T-030 | [`SC-008`](../../docs/product/acceptance-criteria.md#sc-008) | 验证目录；1280×720及目标浏览器页面、键盘/焦点与主要操作截图  补充：[browser-checklist.md](../../docs/validation/002-order-management/browser-checklist.md) | 手工用例已编写／负责人确认本轮无需验证；2026-10-12 | [浏览器检查表](../../docs/validation/002-order-management/browser-checklist.md)；负责人确认无需执行，未用HTTP/jsdom替代手工通过。 |
| TC-227／T-030 | [`SC-001`](../../docs/product/acceptance-criteria.md#sc-001) | 执行后验证目录（本次不存在）；固定110000数据下常用查询P95，保存全部样本  补充：[ApiPerformanceTest](../../backend/src/test/java/com/exportflow/orders/ApiPerformanceTest.java) | 性能程序已编写／负责人确认本轮无需验证；原测量已取消；2026-10-12 | [共享环境](../../docs/validation/002-order-management/shared-services.md)；测量已中止且无完整P95／JSON；不再补测，原中止日志保留，不计功能失败，不标性能达标。 |
| TC-228／T-030 | [`AC-011`](../../docs/product/acceptance-criteria.md#ac-011)、[`SC-002`](../../docs/product/acceptance-criteria.md#sc-002) | 执行后验证目录（本次不存在）；创建接口P95与大范围/大ID载荷异步响应  补充：[ApiPerformanceTest](../../backend/src/test/java/com/exportflow/orders/ApiPerformanceTest.java)、[CreationAsyncBoundaryTest](../../backend/src/test/java/com/exportflow/exporttasks/CreationAsyncBoundaryTest.java) | 性能程序已编写／负责人确认本轮无需验证；原测量已取消；2026-10-12 | [共享环境](../../docs/validation/002-order-management/shared-services.md)；测量已中止且无完整P95／JSON；不再补测，原中止日志保留，不计功能失败，不标性能达标。 |
| TC-229／T-030 | [`SC-009`](../../docs/product/acceptance-criteria.md#sc-009) | 执行后验证目录（本次不存在）；全部本期自动化结果、类型检查和构建退出码汇总 | 前端及正式Wrapper质量门禁通过；性能已取消，剩余手工本轮无需验证 | [当前自动化汇总](../../docs/validation/002-order-management/green-results.md)；原始红灯及测试先行阻塞保留在[历史结果](../../docs/validation/002-order-management/red-results.md)，不作为当前状态。 |
| TC-230／T-018 | [`AC-067`](../../docs/product/acceptance-criteria.md#ac-067)、[`SC-003`](../../docs/product/acceptance-criteria.md#sc-003)、[`SC-007`](../../docs/product/acceptance-criteria.md#sc-007)、[`AC-071`](../../docs/product/acceptance-criteria.md#ac-071) | `backend/src/test/java/com/exportflow/worker/ExcelContentTest.java`；真实.xlsx结构/可打开与110000/200000行9列完整生成耗时（包含块落盘、校验和发布） ；批间临时sheet已写内容、完成前无正式引用、最终原子改名；新临时文件使用统一校验，登记后直接提交成功  补充：[ExcelBoundaryTest](../../backend/src/test/java/com/exportflow/worker/ExcelBoundaryTest.java)、[ExcelValidationBoundaryTest](../../backend/src/test/java/com/exportflow/worker/ExcelValidationBoundaryTest.java)、[ExcelPerformanceTest](../../backend/src/test/java/com/exportflow/worker/ExcelPerformanceTest.java) | 110000行内容／资源及五次独立性能通过；200000行仅记来源不足 | [来源证据](../../docs/validation/002-order-management/provided-data.md)；110000行准确性与独立512MiB无OOM已验证，200000行未执行，性能使用独立五次样本。 |
| TC-231／T-018 | [`SC-004`](../../docs/product/acceptance-criteria.md#sc-004)、[`AC-071`](../../docs/product/acceptance-criteria.md#ac-071) | `backend/src/test/java/com/exportflow/worker/ExcelResourceTest.java`；独立512MB JVM真实110000导出，无OOM；资源采样与scratch关闭证据 ；已完成行刷出后释放内存  补充：[ExcelBoundaryTest](../../backend/src/test/java/com/exportflow/worker/ExcelBoundaryTest.java)、[ExcelValidationBoundaryTest](../../backend/src/test/java/com/exportflow/worker/ExcelValidationBoundaryTest.java)、[ExcelPerformanceTest](../../backend/src/test/java/com/exportflow/worker/ExcelPerformanceTest.java)、[WorkerSampledMain](../../tests/backend/src/test/java/com/exportflow/testing/WorkerSampledMain.java) | 110000行内容／资源及五次独立性能通过；200000行仅记来源不足 | [来源证据](../../docs/validation/002-order-management/provided-data.md)；110000行准确性与独立512MiB无OOM已验证，200000行未执行，性能使用独立五次样本。 |
| TC-232／T-018 | [`AC-015`](../../docs/product/acceptance-criteria.md#ac-015)、[`AC-068`](../../docs/product/acceptance-criteria.md#ac-068)、[`SC-005`](../../docs/product/acceptance-criteria.md#sc-005) | `backend/src/test/java/com/exportflow/worker/ExportProgressMySqlTest.java`；块同事务进度、非成功最高99、成功100、失败保留实际高水位  补充：[ExcelBoundaryTest](../../backend/src/test/java/com/exportflow/worker/ExcelBoundaryTest.java)、[DurabilityGuardTest](../../backend/src/test/java/com/exportflow/worker/DurabilityGuardTest.java) | 已编写／当前来源复验通过；2026-10-12 | [当前来源结果](../../docs/validation/002-order-management/provided-results.md)；原始红灯及测试先行阻塞保留在[历史结果](../../docs/validation/002-order-management/red-results.md)，不作为当前状态。 |
| TC-233／T-018 | [`AC-067`](../../docs/product/acceptance-criteria.md#ac-067)、[`SC-007`](../../docs/product/acceptance-criteria.md#sc-007) | `backend/src/test/java/com/exportflow/worker/ExcelContentTest.java`；真实源到文件逐行/字段/表头/顺序/金额/香港时区/客户原值/未知/公式文本准确性  补充：[ExcelBoundaryTest](../../backend/src/test/java/com/exportflow/worker/ExcelBoundaryTest.java)、[ExcelValidationBoundaryTest](../../backend/src/test/java/com/exportflow/worker/ExcelValidationBoundaryTest.java) | 已编写／当前来源复验通过；2026-10-12 | [当前来源结果](../../docs/validation/002-order-management/provided-results.md)；原始红灯及测试先行阻塞保留在[历史结果](../../docs/validation/002-order-management/red-results.md)，不作为当前状态。 |
| TC-234／T-018 | [`AC-013`](../../docs/product/acceptance-criteria.md#ac-013)、[`AC-014`](../../docs/product/acceptance-criteria.md#ac-014)、[`AC-015`](../../docs/product/acceptance-criteria.md#ac-015)、[`AC-018`](../../docs/product/acceptance-criteria.md#ac-018)、[`AC-067`](../../docs/product/acceptance-criteria.md#ac-067) | `backend/src/test/java/com/exportflow/worker/DownloadExportFileTest.java`；真实HTTP下载流/安全头/路径/状态/缺失及浏览器直接链接，不用Blob  补充：[DownloadBoundaryTest](../../backend/src/test/java/com/exportflow/worker/DownloadBoundaryTest.java)、[browser-checklist.md](../../docs/validation/002-order-management/browser-checklist.md) | 真实HTTP下载自动化通过／目标浏览器手工负责人确认本轮无需验证 | [当前来源结果](../../docs/validation/002-order-management/provided-results.md)、[浏览器检查表](../../docs/validation/002-order-management/browser-checklist.md)；缺失文件410错误码复验通过。 |
| TC-235／T-019 | [`AC-010`](../../docs/product/acceptance-criteria.md#ac-010)、[`AC-049`](../../docs/product/acceptance-criteria.md#ac-049)、[`AC-068`](../../docs/product/acceptance-criteria.md#ac-068)、[`AC-069`](../../docs/product/acceptance-criteria.md#ac-069)、[`SC-006`](../../docs/product/acceptance-criteria.md#sc-006)、[`SC-010`](../../docs/product/acceptance-criteria.md#sc-010)、[`AC-071`](../../docs/product/acceptance-criteria.md#ac-071) | `backend/src/test/java/com/exportflow/worker/CheckpointRecoveryTest.java`；真实MySQL/MQ/文件kill与提交不明、连续断点/安全重建/READY采用/旧身份/恢复耗尽；READY复用校验实际引用/大小/SHA/行数/配置、暂不可读归UNKNOWN不误判损坏；临时写入中断不下载半成品  补充：[RecoveryFailureWindowsTest](../../backend/src/test/java/com/exportflow/worker/RecoveryFailureWindowsTest.java)、[DurabilityGuardTest](../../backend/src/test/java/com/exportflow/worker/DurabilityGuardTest.java)、[ExcelValidationBoundaryTest](../../backend/src/test/java/com/exportflow/worker/ExcelValidationBoundaryTest.java) | 已编写／当前来源复验通过；2026-10-12 | [当前来源结果](../../docs/validation/002-order-management/provided-results.md)；原始红灯及测试先行阻塞保留在[历史结果](../../docs/validation/002-order-management/red-results.md)，不作为当前状态。 连接丢失及恢复已验证，共享broker进程重启已由负责人确认本轮无需验证，历史缺口见[记录](../../docs/validation/002-order-management/coverage-gaps.md)。 |
| TC-236／T-019 | [`AC-018`](../../docs/product/acceptance-criteria.md#ac-018)、[`AC-069`](../../docs/product/acceptance-criteria.md#ac-069)、[`AC-071`](../../docs/product/acceptance-criteria.md#ac-071) | `backend/src/test/java/com/exportflow/worker/ArtifactCleanupRaceTest.java`；关闭/发布失败、成功提交不明、采用/清理竞态、永久引用与scratch残留保护 ；刷盘/改名失败拒绝READY/成功  补充：[ArtifactCleanupConcurrencyTest](../../backend/src/test/java/com/exportflow/worker/ArtifactCleanupConcurrencyTest.java) | 已编写／当前来源复验通过；2026-10-12 | [当前来源结果](../../docs/validation/002-order-management/provided-results.md)；原始红灯及测试先行阻塞保留在[历史结果](../../docs/validation/002-order-management/red-results.md)，不作为当前状态。 |

测试组内按具体条件拆TC子场景，名称/注释反向引用对应AC；不同字段、每个错误/边界及故障窗口应独立断言。不能用一个“文件存在”断言替代可打开、内容和成功登记，也不能以测试替身满足文件指标。

### 覆盖缺口与阶段完成检查

任务页详情、进行中状态、结果元数据与下载操作均不在本期前端范围；后端仍完整实现。[`AC-013`](../../docs/product/acceptance-criteria.md#ac-013)、[`AC-014`](../../docs/product/acceptance-criteria.md#ac-014)、[`AC-015`](../../docs/product/acceptance-criteria.md#ac-015)和[`SC-005`](../../docs/product/acceptance-criteria.md#sc-005)只验证后端子集，前端行为留后续任务管理规格，不标整条通过。[`AC-025`](../../docs/product/acceptance-criteria.md#ac-025)与[`AC-070`](../../docs/product/acceptance-criteria.md#ac-070)为撤出历史条目，不进入本期实施或测试映射。

| 范围 | 缺口与影响 | 替代验证／恢复条件 | 负责人／评审 |
| --- | --- | --- | --- |
| TC-213至TC-236 | 当前来源适用自动化及正式质量门禁已完成；剩余验证已明确免验 | 保留实测证据、来源规模缺口和免验范围，见[本轮免验确认](#本轮免验确认) | 项目负责人2026-10-12确认本轮无需剩余验证 |
| [`AC-068`](../../docs/product/acceptance-criteria.md#ac-068)、[`AC-069`](../../docs/product/acceptance-criteria.md#ac-069) | 执行恢复/断点/产物/清理子集及未知消息处置已实施并通过自动化验证 | 按两次门禁进入测试及生产；真实依赖验证所有kill/提交不明/旧身份窗口，不能用架构批准替代证据 | 已实施并取得真实kill/提交不明/旧身份证据；broker进程重启由负责人确认本轮无需验证 |
| [`SC-001`](../../docs/product/acceptance-criteria.md#sc-001)、[`SC-002`](../../docs/product/acceptance-criteria.md#sc-002)、[`SC-003`](../../docs/product/acceptance-criteria.md#sc-003)、[`SC-004`](../../docs/product/acceptance-criteria.md#sc-004) | 参考环境方案已在[决策记录](decision-record.md#参考环境及事实核对)确认；110000行Excel五次实测已通过，API专项由负责人取消，200000行不足仅记缺口 | 保留原始JSON及512MiB进程证据，API无完整指标不得判定通过，见[来源证据](../../docs/validation/002-order-management/provided-data.md) | 负责人确认本轮无需性能验证；原API测量已取消，未生成完整P95／原始报告，不标达标，见[收尾记录](../../docs/validation/002-order-management/provided-data.md#本轮收尾与性能取消) |
| [`AC-013`](../../docs/product/acceptance-criteria.md#ac-013)／TC-234 | 本期无前端下载组件；直接浏览器URL不能用组件测试覆盖 | 后端真实HTTP流与头自动验证，加直接HTTP/浏览器URL手工下载、可打开与文件名；无浏览器E2E门禁，执行后保留证据 | 负责人确认浏览器直接下载手工本轮无需验证；2026-10-12 |
| [`SC-008`](../../docs/product/acceptance-criteria.md#sc-008)／TC-226 | 目标已定Windows稳定Chrome/Edge，实际矩阵未验证 | 1280×720与键盘/焦点，两浏览器各记真实版本，未覆盖项明确列出 | 负责人确认本轮无需验证；2026-10-12 |
| [`AC-012`](../../docs/product/acceptance-criteria.md#ac-012)、[`AC-016`](../../docs/product/acceptance-criteria.md#ac-016) | 持续任务列表通知与失败重试页面另规格，不属于本次通过范围 | 本期只验证后端真实进度/最终值/错误，不提供前端任务状态查询；完整任务页与SSE另做两次审批，不能标整条完成 | 项目负责人；范围明确，后续规格 |

- [ ] 测试工具、隔离服务及实际文件目录正常；版本、配置、能力核验完成。
- [ ] 全部本期故事及真实文件/恢复用例已逐项补充；正式装配核验及完整性评审待完成，不能据源码清单自行判门禁通过。
- [ ] 可执行测试已运行，行为预期失败、已有通过和环境/编译阻塞分别有命令和证据。
- [ ] 未执行项/缺口说明影响、替代验证和恢复条件，明确提交评审。
- [x] 负责人明确授权开始开发，范围、版本、日期和依据已记录；运行缺口保留并逐项恢复验证。

## 依赖与执行顺序

T-001文档准备→T-002架构/参数/环境→T-003规格审批→T-010测试准备→全部T-011至T-016及T-018/T-019→T-017第二门禁。未明确批准不得实施生产。

生产批准后T-020/T-024→T-021/T-025→T-022/T-026→T-027/T-028及T-029执行控制→T-032数据断点→T-033真实Excel/产物→T-034后端下载与T-023静态导航回归→T-030/T-031验收收尾。消费者只有完整能力与架构门禁满足后启用，不能提前运行无结果保证的长任务。无新增源代码阶段免审批例外。

## 本次文档验证记录

- 2026-10-07 草稿0.1：读取产品/治理/规则/ADR/学习稿与实际骨架，确认初始状态；只读docker ps三容器healthy，未修改资源。
- 2026-10-07 草稿0.1：727处本地链接/锚点、39项AC映射、158项文件与189项签名结构自检通过；仅为旧草稿文档证据，不作为0.2扩大范围的验证。
- 2026-10-07 草稿0.2：负责人明确本次规格加入真实Excel。同步核心、函数、数据/协议、Excel方案、产品与候选编号；所有测试和生产实现仍未开始，审批仍未批准。
- 2026-10-07 草稿0.2：只读文档结构检查退出码0。16份Markdown的884处本地链接／锚点均可解析，AC／SC引用均为链接；验收基线79个编号唯一，撤出编号不在活跃映射中。44项本期AC与需求、实现任务及测试映射一致，10项SC有验证映射，36项计划用例有未编写／未执行进度记录。
- 2026-10-07 草稿0.2：185项逐文件清单与计划目录树叶节点一致，265项函数／端口签名及清单内自有调用关系结构核对通过；Markdown表格列数一致。OpenAPI可解析，4个operationId唯一，41处内部引用可解析；这是结构校验，不是接口运行或外部规范验证器的符合性结论。
- 2026-10-07 草稿0.2：`git -c core.safecrlf=false diff --check`退出码0；工作区仅文档变动，未生成生产代码、迁移、测试或实际验证报告，未修改中间件资源。文档检查不改变任何审批状态，不作为业务测试或产品验收证据。

- 2026-10-07 草稿0.3：负责人明确接受建议，技术/产品决策已批准；D-01至D-12、ADR子集、运行/测试/构建/SSE方向及延期范围已定稿。Maven/npm元数据与本机硬件只读核对，未安装依赖、创建资源或运行业务测试；两次开发门禁仍未批准。

- 2026-10-07 草稿0.3：负责人追加已完成Excel部分写入临时文件、全部完成后转换正式结果，新增临时刷盘函数与验收映射；逐批SXSSF刷行/缓冲及.xlsx.part原子改名明确，测试仍未编写/执行。
- 2026-10-07 草稿0.3：只读文档校验退出码0。检查根README/学习稿、docs及本规格共40份Markdown，1168处本地链接/锚点可解析、表格列数一致、AC/SC引用均为链接；验收基线80个编号唯一，撤出条目不在当前实施映射。45项AC与需求/实施/测试一致且测试进度反向引用完整，10项SC有环境与证据计划，36项用例仍未编写/执行。
- 2026-10-07 草稿0.3：186项文件清单与计划目标树一致，271项函数/端口签名及清单内自有调用核对通过；OpenAPI0.3.0-draft可解析，4个operationId唯一，42处内部引用可解析。独立JDBC初始化工具、JSON长度限制、临时刷盘/改名已列入设计和对应测试计划，没有生成这些文件或代码。
- 2026-10-07 草稿0.3：git diff --check退出码0，工作区仅文档变动。以上是文档结构与追踪检查，不是接口符合性、集成、文件/资源或性能验收；当前Docker context的服务状态、依赖运行兼容与所有业务测试仍未验证，审批状态未自动改变。
- 2026-10-11 API组织修订：只读Node.js文档核对退出码0；目标树与185项逐文件职责清单一致，271项函数/端口签名保留，四项前端业务API函数集中在同一设计章节。五份受影响规格的本地链接及锚点可解析，统一API文件标记为未来新增，原占位删除列为获批实施任务。实际代码已恢复，规格和测试阶段审批状态保持待审批。
- 2026-10-11 草稿0.4：按负责人明确的前后端范围修订产品基线、规格、模型、契约、函数及任务，仅修改文档。只读Node.js结构核对退出码0：16份Markdown的本地链接/锚点与114个表格一致，185项文件清单与目标树一致，267项函数/端口签名；44项AC的实施/测试映射及36项测试进度反向引用一致。OpenAPI有3个唯一操作和37处可解析内部引用，取消详情GET及Location，创建响应只含创建标识/配置。前端仅queryOrders/createExportTask，后端执行/文件/恢复/下载链路仍设计完整；测试未编写或执行，两次门禁仍未批准。
- 2026-10-11 评审组织与函数精简：计划首先列出6步核心流程、12个关键函数的输入输出及拆分理由，完整目录移至文末，类型化签名作为按需查阅的实现附录。删除文件存储端口及其实现的4项产物仓储转发签名，生成器直接使用ArtifactRepository；成功提交只核对事务外已验证产物及数据库身份，不重复长文件IO。只读Node.js检查退出码0：16份Markdown、117个表格及938处本地链接/锚点一致；185项文件与目标树一致，签名减至263项；44项AC的实施/测试映射与36项测试进度反向引用完整，OpenAPI仍为3个操作、37处内部引用。本次只修改规格文档，未编写或运行业务测试，不改变审批状态。
- 2026-10-11 函数设计稿修订：按负责人确认的方向，function-design.md改为按业务调用顺序组织，创建/投递/执行主链路只展开10个入口，查询/下载3个入口按需阅读；完整逐文件及辅助签名移入function-design-reference.md，计划不重复函数列表。合并未知结果重试转发，删除retryOrderExport；断点提交是唯一进度写入，删除无本期观察调用方的ProgressSink、ProgressUpdate及服务/仓储重复report链，保留心跳、失权检查、真实进度、恢复与清理；30项适配实现的相同职责/步骤引用端口，不重复展开。只读Node.js核对退出码0：17份Markdown、118个表格和973处本地链接/锚点一致；184项文件与目标树一致，完整签名258项，正文13项签名与附录一致；44项AC和36项测试进度反向映射完整，OpenAPI仍有3个操作、37处内部引用。本次只修订文档，未编写或运行业务测试，审批状态不变。

- 2026-10-11 设计复查修正：文件端口只用resolve安全定位，ExcelFileValidator.validate统一接收Path/StorageRef/TaskSnapshot/ExecutionGuard，返回ValidatedWorkbook，生成器将明确损坏与依赖暂不可读/失权分别归为KNOWN_FAILURE/UNKNOWN；已有READY须核对数据库证明，新文件校验发布并登记后直接进入成功事务，流程图不再重复校验。submitOrderExport只接收CreateExportTaskApi并返回提交快照，Hook内部applySubmissionResult统一确认/同键重试的状态及页面反馈，先保存成功再处理页面操作，页面异常不改写成功或重发。TC-209/210/230/235补充对应计划场景，未生成测试代码。只读文档核对退出码0：17份Markdown、118个表格及973处本地链接/锚点一致，184项文件与目录树一致，258项完整签名及13项正文签名一致；44项AC与36项测试进度反向映射完整，OpenAPI仍为3个操作/37处内部引用。三处修正的职责、签名和流程专项核对通过；仅文档，业务测试及两次门禁状态不变。

- 2026-10-11 测试准备（0.4-test-01）：负责人在本会话明确要求“进入规格实施阶段”，规格审批通过；新增测试工具、合成夹具、前后端首批用例及测试专用故障装配，未修改生产实现。锁定依赖安装、真实隔离MySQL/RabbitMQ/文件/回环HTTP工具检查已执行，实际红灯、阻塞及计数见[首轮结果](../../docs/validation/002-order-management/red-results.md)。44项AC均已在测试源码反向链接，但部分子场景未完整编码，不能据此宣称完整覆盖；[覆盖缺口](../../docs/validation/002-order-management/coverage-gaps.md)保持待评审，T-011至T-019及测试阶段门禁未完成。

- 2026-10-11 测试准备补充（0.4-test-02）：补充HTTP超时/取消/协议、跨页选择与成功反馈、SeedOrders校验/回滚、规范化与终态幂等、MQ路由/容量/重启、终态ACK/租约、完整数据量内容及下载边界。修正业务测试按用例隔离、HTTP关闭、提交故障实际触发断言和APPLIED屏障。最新实际结果为前端36个已收集用例（30个预期行为失败、6个通过）及4个加载阻塞套件；后端83个用例（4个工具通过、79个缺失入口skipped），不等于业务验收通过。剩余未编码及未验证子场景仍见[缺口清单](../../docs/validation/002-order-management/coverage-gaps.md)，测试阶段完整性门禁与第二次审批均未通过。

- 2026-10-11 证据一致性核对：55份Markdown的1488处本地链接/锚点、124处测试源码反向链接及163个表格检查通过；193项目标树叶节点与逐文件职责一致，44项本期AC实施/测试/源码引用一致。真实业务断言仍有加载或入口阻塞，静态一致性不消除覆盖缺口。原始结构检查见本地artifacts/002-order-management/traceability-check.json。

- 2026-10-11 用例完整性审计：对照当前7份前端功能/导航测试、19份后端业务测试及装配，记录34组缺失子场景/待落地验证与8组断言不足，详见[源码审计](../../docs/validation/002-order-management/coverage-audit.md)。纠正当前进度段和映射表残留的“全部未编写”描述，历史记录保留。仅修改审计及进度文档，未新增测试、未重跑业务测试、未修改生产实现；测试完整性门禁与审批状态不变。
- 2026-10-11 审计文档核验：三份受影响Markdown的450处本地链接/锚点、16个表格及34个M项/8个A项唯一编号核对通过；git diff --check退出码0。本次结果仅证明审计记录结构正确，不新增业务执行证据。

- 2026-10-11 场景补充（0.4-test-03）：按负责人“先将上述测试场景补充完整”完成原M-01至M-34及A-01至A-08的源码/验证程序/逐步手工用例对应，详见[补充对照](../../docs/validation/002-order-management/scenario-completion.md)。新增真实并发/kill/故障、资源/下载/安全与性能采样边界；正常调度与消费角色在相关集成/性能用例启用。测试编译、实际运行和类型检查结果见[报告](../../docs/validation/002-order-management/red-results.md)。运行阻塞及真实装配限制保持待评审；未修改生产实现，测试阶段与生产实施审批不变。

- 2026-10-11 0.4-test-03验证：前端61个收集用例（55个预期失败、6个通过）及4个导入阻塞套件；类型检查仅12处生产模块/导出缺失。后端304个记录（4环境通过、298入口缺失跳过、2性能开关关闭记录），显式开启性能的3个场景均因入口缺失跳过。48份Markdown、1677处本地链接/锚点、208处源码链接和153个表格检查通过；221项目标树叶节点与职责一致，44项活跃AC源码引用齐全，34个M项及8个A项均有补充对应。git diff --check通过，独立测试容器无残留；不据这些静态/工具结果批准测试阶段。

- 2026-10-11 API实施（0.4-api-01）：负责人明确授权开发并再次要求集中API、删除不需要文件。实现统一queryOrders/createExportTask、HTTP与安全错误、分页及协议类型/schema；删除两个功能API和任务表格/查询Hook占位。原33条API用例通过；新增5条边界回归先取得2条BigInt安全错误失败，再修正格式校验顺序，最终API38/导航4/MSW工具2共44条通过。完整类型检查仍有9处其他模块/导出缺失，未标T-020/T-021/T-022整体完成；[详细证据](../../docs/validation/002-order-management/api-implementation.md)保留范围及恢复条件。

### 2026-10-11复用负责人服务

负责人明确“为什么启动了这么多容器，直接用我给你准备的啊project-export-flow”。旧Wrapper全量与Worker专项人为中断；按日志精确清除本次额外6个容器，当前仅保留负责人3个健康服务。旧批次不能标全部通过。改用逐用例共享服务内schema/vhost/账号隔离；配置、网络故障与性能差异见[共享服务验证](../../docs/validation/002-order-management/shared-services.md)。前端最新153/153通过，typecheck/build通过；后端停机顺序与严格测试资源名两项取得红灯并修复为绿灯，最终回归仍待完整执行。

### 2026-10-11历史回归与数据职责清理

前端153/153与类型／构建通过；后端按类汇总316项通过，零失败／错误，2项默认关闭性能记录。正式质量门禁、JaCoCo与逐套件来源见[当前汇总](../../docs/validation/002-order-management/green-results.md)。实施任务勾选表示相应代码和自动化验证已完成；T-030/T-031仍未完成，不能把这些勾选当整个规格验收通过。

按负责人要求核对基础设施与业务表边界：不新增Compose、服务初始化、共享账号配置或Redis实现，删除迁移目录过时占位README及其设计清单项。当时保留的显式造数工具已于2026-10-12按负责人要求删除；当前直接读取示例原始订单，必要任务表仍用于应用任务隔离。详情见[只读核对](../../docs/validation/002-order-management/shared-services.md#业务数据边界核对)。

### 2026-10-12负责人纠正订单数据来源

负责人明确要求删除数据库造数，所有订单复用已提供容器；并明确不足200000的数据场景不计失败，只记录缺口。该要求替代原合成数据方案，沿用既有开发授权，设计见[复用数据](provided-data.md)。旧性能批次已停止，自己的临时schema／vhost／账号已精确回收，原始订单未改。新回归先取得写权限错误的真实红灯；源码／夹具／迁移／脚本／规格同步修订，当前验证见[来源记录](../../docs/validation/002-order-management/provided-data.md)。原316项为旧数据方案历史结果，不能标当前数据适配已经通过。

2026-10-12来源性能Review：110000行前四次生成10.1–12.5秒，第五任务停在PENDING；查明已有RUNNING旧Outbox占候选LIMIT，补OutboxDispatchMySqlTest两条真实MySQL用例分别取得候选饥饿与等待告警误计数红灯（provided-candidate-red.log，3条中2失败1通过）。修正候选在LIMIT之前筛PENDING并在锁内保持原复核，等待计数同样只计仍需命令的PENDING任务。对应T-027/T-028/T-029、TC-221；追踪[队列交付与认领](../../docs/product/acceptance-criteria.md#ac-049)与[后端闭环](../../docs/product/acceptance-criteria.md#sc-010)，等待计数为已定稿运行观察的工程辅助回归，设计见[实施补充](implementation-notes.md)。正式受影响回归及质量门禁日志provided-orders-verify-04.log，性能须重新独立测量，原失败不计为数据不足。

2026-10-12来源收尾：04受影响回归51项集成及8项单元全部通过正式质量门禁；按类功能汇总304条（300通过、0失败、0错误、4跳过），跳过为2条来源不足及2条默认关闭性能，不混入独立性能批次。候选修复后110000行9列五次独立Excel全部成功，原始日志provided-excel-performance-02.log及performance-excel-110000.json保存；200000行未执行且不计功能失败。最新源数据及性能结论见[来源记录](../../docs/validation/002-order-management/provided-data.md)，历史失败不删除。

2026-10-12负责人明确取消性能测试：停止API专项JVM及后续补测，主动中止导致Wrapper非零不计功能失败，不冒充性能通过；200000来源不足仍只记缺口。临时schema／vhost／账号已精确回收，三个用户服务健康且原订单聚合不变。开发、代码review、API集中、无用文件删除和T-031文档同步已完成，本轮收尾；T-030保留未完成，性能取消及浏览器／broker重启／同时间戳来源边界不标验收通过，见[最终记录](../../docs/validation/002-order-management/provided-data.md#本轮收尾与性能取消)。

### 本轮免验确认

2026-10-12负责人在本会话明确“标记为不用验证吧”。本轮剩余浏览器手工（含直接下载）、共享broker进程重启、真实来源同时间戳边界确认无需验证；性能按此前指令取消，来源不足200000/200001只记录且本轮不补验。免验是负责人确认的执行范围，不是实测通过；已有功能、质量门禁及实际测量证据保留，产品标准定义不改，后续任务管理/SSE分期不变。

| 范围 | 本轮结论 | 依据与日期 |
| --- | --- | --- |
| TC-226、TC-234浏览器视觉／焦点／直接下载手工 | 无需验证；没有浏览器实测报告 | 项目负责人本会话指令；2026-10-12 |
| TC-220及恢复相关共享broker进程重启持久性 | 无需验证；连接断开已有证据，进程重启未实测 | 同上 |
| TC-215、TC-233相关真实MySQL同时间戳排序／keyset边界 | 无需补验；来源无重复时间，不改写来源 | 同上 |
| TC-227、TC-228及尚未执行的独立性能 | 无需验证；当前测量已取消，未完成指标不标达标 | 此前取消性能及本次免验确认；2026-10-12 |
| 来源不足200000/200001规模 | 只记录来源条件不足，本轮不补验，不计失败 | 项目负责人此前明确指令；2026-10-12 |
| TC-224真实五分钟单调时间超时预算 | 本轮后续无需验证；指令处理前用例已完成并通过，保留已有证据，不再重跑 | 项目负责人明确“五分钟超时可以跳过”；2026-10-13，见[增量收尾记录](../../docs/validation/003-select-all-filtered/verification.md#五分钟超时免验与分段执行) |

T-030与T-031按“实际完成＋负责人免验记录”收尾；免验范围不改写全项目标准，不追认历史测试门禁为通过。

### 2026-10-12本地Make启动入口

负责人随后使用`./make dev`，已补Windows根make.cmd兼容入口；帮助、配置检查、启动命令dry-run及非零退出透传通过，不需要当前终端刷新PATH。说明和证据见[启动入口记录](../../docs/validation/002-order-management/startup-entry.md#windows相对路径兼容入口)。

负责人首先要求“容器已经启动了，项目应该设置make脚本用来启动项目”。本次为低风险工具链封装，不改变业务行为、产品标准或既有审批与免验结论。新增根Makefile、无真实凭据的.env.example及scripts/run-project.ps1；支持同时/分别启动、配置检查、依赖安装和构建，脚本复用共享服务、不创建应用库/账号/vhost、不造数、不跑性能测试。Windows PowerShell5.1解析、配置错误退出、固定pnpm构建及Make并行/Ctrl+C已核对；首次核验时应用连接资源尚缺，未将当时后端启动标为成功。逐文件职责、函数及review结论见[实施补充](implementation-notes.md#本地启动入口)，实际证据见[启动入口记录](../../docs/validation/002-order-management/startup-entry.md)。

后续负责人要求实际配置文件和示例：已准备被Git忽略的.env.local，完善.env.example，在已提供容器内补齐已定稿的应用库/专属账号/vhost。实际make dev核验修正本机JDK socket临时路径及AMQP地址不应附加vhost路径；健康UP、前端HTTP200、代理查询110000条原始订单成功，迁移1–4成功。没有创建任务、造数据或性能专项；真实密码未入文档/版本控制。配置职责及实际证据见[本机配置准备](../../docs/validation/002-order-management/startup-entry.md#本机配置准备)。

### 2026-10-13导出运行目录修正

负责人要求改用当前项目根目录的专用文件夹，明确该目录不上传Git、旧文件不用复制、由其自行重启后端。本次不新建规格，修正原规格及配置，作为T-031运行说明维护。已创建export-data，配置本机绝对路径，将application.yml默认值改为user.dir/export-data，并将整个目录加入.gitignore；示例、原规格/计划/状态/文件方案/决策/quickstart与运行规则、ADR、README已同步。未新增业务函数或测试，未复制旧文件，未启动或重启应用。配置检查和文档核验见[目录记录](../../docs/validation/002-order-management/startup-entry.md#项目内导出运行目录)。
