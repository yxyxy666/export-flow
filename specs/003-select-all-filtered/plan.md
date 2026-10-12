# 实施计划：全量选择与日期时间控件

**版本／日期**：0.1已批准／2026-10-12。输入[spec.md](spec.md)；审批及测试进度见[tasks.md](tasks.md)。设计随0.1获批；测试先行已执行并获第二次批准，2026-10-13：功能实施、代码review、功能回归及质量门禁已完成；性能/规模和原浏览器免验保持，免验不记实测通过。

## 方案与工具链

React19/TypeScript7/Ant Design6/RHF/TanStack Query保持现有版本，Node22、pnpm10.34.6固定；Dayjs1.11.23已在锁文件中，增加直接依赖以支持DatePicker值类型和严格解析。Java21/Spring Boot3/MyBatis、MySQL8.4、RabbitMQ3.13及文件/恢复链保持现有架构，不增加依赖服务。业务API仍集中在shared/api/api.ts。

选择使用INCLUDE/EXCLUDE和例外集合；全局反选只翻转mode。前端按已提交filter生成既有SELECTED或扩展FILTERED请求。后端范围校验、规范化、幂等、任务关联、快照及SQL统一识别排除项，既有Worker复用readBatch并从持久化范围恢复。不新增物理数据库结构，关联角色按scope_type区别；详细语义见[data-model.md](data-model.md)和[契约](contracts/export-scope.md)。

日期使用两个DatePicker showTime，保留独立起止条件。组件值转香港墙上时间字符串，再复用toUtcInstant；控件桥接放订单models，不把只供表单使用的函数提前扩展到shared。实施时用AntD公开DatePicker.generatePicker配置Dayjs UTC墙上时间适配，年月日运算与严格文本解析均使用UTC载体，避免默认本机解析的夏令时跳时；不新增日期库或rc-picker依赖，不复制组件内部实现。无性能测试，所有验证为有限输入的功能断言及既有质量门禁。

## 风险与门禁

中风险：新增交互、向后兼容的请求字段及应用关联内容语义；不改变物理表、来源、消息或并发授权。原SELECTED、无排除FILTERED及历史断点摘要必须保持字节级兼容，新增排除参与新版本身份。多模块必须同批上线，回退边界见[状态模型](data-model.md#幂等与恢复身份)。已核对[前端决策](../../docs/adr/0002-frontend-stack-and-test-scope.md)、[任务与范围权威边界](../../docs/adr/0004-rabbitmq-task-queue.md)和[恢复决策](../../docs/adr/0008-worker-execution-lease-and-recovery.md)：关联表包含/排除角色未由ADR限定；范围仍在创建事务冻结并由Worker按taskId读回，lease/fence、可信块与READY裁决不变，不替代已接受决策。

规格0.1及测试阶段均已批准；2026-10-12负责人回复“继续开发”，覆盖缺口按恢复条件补证。目录/函数设计完整性只代表文档检查，不代表测试阶段批准。先前用户取消性能、本轮免验结论保留；不借此跳过新增可自动化行为。依据[AGENTS.md](../../AGENTS.md)和[测试规则](../../docs/rules/testing.md)逐阶段执行。

## 变更目录树

M为拟修改，A为拟新增，R为复用；生产/测试文件到对应获批阶段才产生。

```text
frontend/
├── package.json / pnpm-lock.yaml                        M
└── src/
    ├── shared/api/api.ts                                R（唯一业务API）
    ├── shared/formatters/date-time.ts                   R（香港/UTC转换）
    ├── features/export-tasks/
    │   ├── types/export-task.types.ts                   M
    │   └── models/export-task-schema.ts                 M
    └── features/orders/
        ├── types/order.types.ts                        M
        ├── models/order-selection.ts                    M
        ├── models/order-date-picker.ts                  A
        ├── hooks/use-order-selection.ts                 M
        ├── pages/order-management-page.tsx              M
        ├── components/order-table.tsx                   M
        ├── components/order-filter-form.tsx             M
        ├── application/order-export-use-case.ts         M
        └── __tests__/
            ├── order-selection.test.ts                 M
            ├── order-management.test.tsx                M
            ├── order-export.test.tsx                    M
            ├── order-export-state.test.tsx              M
            ├── order-boundaries.test.tsx                M
            ├── order-api.test.ts                        M
            ├── order-date-picker.test.tsx               A
            └── order-date-validation.test.tsx           A（review补充无效草稿回归）
backend/pom.xml                                          M（独立构建输出参数；T-009）
backend/src/
├── main/java/com/exportflow/
│   ├── exporttasks/
│   │   ├── api/ExportTaskApiMapper.java                 M
│   │   ├── domain/ExportScope.java                      M
│   │   ├── domain/ExportTaskModels.java                 M
│   │   ├── application/ExportRangeService.java          M
│   │   ├── application/ExportRequestCanonicalizer.java M
│   │   └── infrastructure/persistence/
│   │       ├── ExportTaskMapper.java                   M
│   │       ├── MyBatisExportTaskRepository.java         M
│   │       └── ExportTaskPersistenceMapper.java        M
│   ├── orders/port/OrderRepository.java                M
│   └── orders/infrastructure/persistence/
│       ├── OrderMapper.java                            M
│       └── MyBatisOrderRepository.java                 M
├── main/resources/mappers/
│   ├── OrderMapper.xml                                 M
│   └── ExportTaskMapper.xml                            M
└── test/java/com/exportflow/
    ├── exporttasks/ExportExclusionMySqlTest.java         A
    ├── exporttasks/CreationInputBoundaryTest.java        M
    ├── exporttasks/IdempotencyMySqlTest.java             M
    └── worker/ExportExclusionRecoveryTest.java           A
```

文档变更目录如下；已有002及README到实施收尾时才同步，验证记录执行后才生成，不改旧审批证据。

```text
docs/
├── product/acceptance-criteria.md                       M（唯一AC/SC定义；T-001）
├── product/order-export-prd.md                          M（需求入口；T-001）
├── decisions-pending.md                                M（澄清结论；T-001）
└── validation/003-select-all-filtered/verification.md   A（实际结果；T-009/010）
specs/
├── 003-select-all-filtered/
│   ├── spec.md / plan.md / tasks.md                     A（核心规格；T-001/010）
│   ├── data-model.md                                  A（状态和范围；T-001/010）
│   └── contracts/export-scope.md                       A（排除协议；T-001/010）
└── 002-order-management/
    ├── contracts/http-api.md / openapi.yaml            M（协议增量；T-010）
    ├── data-model.md                                  M（范围增量入口；T-010）
    └── implementation-notes.md                        M（实施证据入口；T-010）
README.md / frontend/README.md / backend/README.md       M（交互及协议说明；T-010）
```

上述文档/契约均无自有函数：核心文件分别维护需求、设计及阶段记录，数据模型维护集合与持久化身份，OpenAPI维护可选字段和边界，验证文件维护实际命令、环境、结果与review结论。共享API、查询Hook、日期格式化、Worker执行/文件/恢复及测试harness均复用，不新增转发层、迁移或造数工具。

## 逐文件职责

| 文件（相对上树目录） | 内容、函数及任务 |
| --- | --- |
| frontend/package.json、pnpm-lock.yaml | dayjs1.11.23直接依赖与冻结锁定；无自有函数；T-006 |
| orders/types/order.types.ts | OrderSelectionState增加mode及可见notice；SelectionAction扩展全局动作，SelectionView类型；无自有函数；T-004 |
| orders/models/order-selection.ts | reducer、行选中及导出范围派生，集合去重、容量约束，拒绝操作有notice；T-004 |
| orders/hooks/use-order-selection.ts | 筛选归属、数量及模式返回，不保存全量列表；T-004 |
| orders/pages/order-management-page.tsx | 全选/全部反选工具、选择数量、范围传递和禁用原因，协调旧exporter；T-004/005 |
| orders/components/order-table.tsx | 根据当前页行与选择模式派生selectedRowKeys，表头/单行仍通知页面；T-004 |
| orders/application/order-export-use-case.ts | 排除数组加入深度冻结；请求仍调共享API；T-005 |
| export-tasks/types/export-task.types.ts | FILTERED可选excludedOrderIds；无自有函数；T-005 |
| export-tasks/models/export-task-schema.ts | 严格字段白名单、字符串ID及长度上限；无自有函数；T-005 |
| orders/models/order-date-picker.ts | Dayjs和香港本地字符串桥接，customParseFormat/utc初始化，以UTC模式保留控件墙上时间组成并避免本机夏令时跳时；T-006 |
| orders/components/order-filter-form.tsx | 用DatePicker替换两个文本时间框，接RHF及原错误关联、冻结；T-006 |
| ExportTaskApiMapper.java | FILTERED可选字段白名单与ID解析；T-007 |
| ExportScope.java | 新排除字段、不变集合、旧构造器兼容及validate；T-007 |
| ExportRequestCanonicalizer.java | 旧无排除摘要原样，新排除version2、稳定排序；T-007 |
| ExportRangeService.java | 存在性及过滤交集计数，空/超上限拒绝；T-007 |
| OrderRepository.java、OrderMapper.java | countMatchingIds新增签名及参数化端口；T-007 |
| MyBatisOrderRepository.java | 1000项分批匹配计数，读取排除范围；T-007/008 |
| ExportTaskMapper.java、ExportTaskMapper.xml | 关联写/读名称中性化为ScopeOrderIds，不改表；XML无Java函数；T-008 |
| MyBatisExportTaskRepository.java | 两类范围关联同事务持久化，读取快照加载关联；T-008 |
| ExportTaskPersistenceMapper.java | 按scope_type恢复包含或排除，旧无关联兼容；T-008 |
| ExportTaskModels.java | TaskSnapshot配置身份纳入非空排除，旧v1保持；T-008 |
| backend/pom.xml | 无函数；新增exportflow.buildDirectory，默认${project.basedir}/target，可覆盖为独立构建目录，避免Windows运行中jar锁阻断质量门禁；不改变测试/格式/静态检查配置；T-009 |
| OrderMapper.xml | countMatchingIds和FILTERED的NOT EXISTS排除，参数化绑定；无Java函数；T-007/008 |
| 前端七份测试文件 | 分别覆盖集合、分页UI、请求/结果状态、边界、schema和真实日期控件；T-002 |
| 后端四份测试文件 | 输入及摘要兼容、真实MySQL/文件/重启排除范围；T-003 |

## 函数设计草稿

下表名称为拟新增或修改；每行职责即中文注释草稿，流程及调用是设计。OrderId/OrderFilter/ExportScope等复用现有类型；SelectionView={state: OrderSelectionState,count:number,notice?:string,dispatch(action):void}；ReactNode/ReactElement/Dispatch沿用React；Dayjs沿用新增直接依赖。未列框架访问器无行为变化。

| 文件与函数／类型化签名 | 中文职责、流程及调用 |
| --- | --- |
| order-selection.ts：reduceOrderSelection(state: OrderSelectionState, action: SelectionAction): OrderSelectionState | 不可变更新包含/排除集合；全局动作改mode，页/行动作只处理给定ID，校验和容量不通过时保留集合并返回notice；调用orderIdSchema，页面经Hook.dispatch调用 |
| 同文件：isOrderSelected(state: OrderSelectionState,id: OrderId): boolean | 按mode判断例外是否反向；供表格当前页派生，纯函数，无外部调用 |
| 同文件：getSelectionCount(state: OrderSelectionState,total: number): number | 返回参考数量，不伪造服务端计数；EXCLUDE做非负减法；Hook使用，无外部调用 |
| 同文件：toSelectionExportScope(state: OrderSelectionState,filter: OrderFilter): ExportScope | INCLUDE转SELECTED，EXCLUDE转FILTERED含非空排除；不含分页/排序；复制集合，由页面打开对话框前使用；schema校验由既有exporter.open/createSubmission统一执行 |
| use-order-selection.ts：useOrderSelection(filterKey: string,total: number): SelectionView | 筛选身份改变清空，翻页不变；useReducer维护模式，调用getSelectionCount，返回state/count/notice/dispatch |
| order-management-page.tsx：OrderManagementPage(): ReactElement | 新按钮调用SELECT_ALL/INVERT_ALL；展示模式和数量；isValidResult/freeze门控，导出调用toSelectionExportScope及exporter.open；复用useOrders/useOrderExport/DatePicker表单 |
| 同文件既有handleReset(): void | 清查询、两种选择及字段草稿，仍调用orders.reset/dispatch CLEAR/exporter.resetDraft；notifyCreated无行为变化 |
| order-table.tsx：OrderTable(props: OrderTableProps): ReactElement | 页面用isOrderSelected派生当前页selectedIds；AntD onSelect转发onToggle，onSelectAll通过onTogglePage(ids: readonly OrderId[],isSelected: boolean): void派发SELECT_PAGE/DESELECT_PAGE整批动作，避免容量拒绝时只完成部分表头操作；排序仍原回调 |
| order-export-use-case.ts：createSubmission(scope: ExportScope,fields: readonly ExportField[],newKey: () => string): FrozenExportSubmission | 原schema解析后额外冻结excludedOrderIds；scope/filter/fields/key冻结顺序保持；submitOrderExport复用，无改动 |
| order-date-picker.ts：parsePickerLocal(value?: string): Dayjs \| null | 以customParseFormat/utc严格解析香港墙上时间组成，空/非法返回null；UTC模式只承载控件年月日时分秒，供DatePicker.value，不推断接口时间戳 |
| 同文件：formatPickerLocal(value: Dayjs \| null): string \| undefined | 有效值格式化到秒，空返回undefined；供RHF onChange；不调用toISOString |
| 同文件：wallClockConfig（无自有公开函数）；HongKongDatePicker | AntD公开generatePicker的类型化配置；getNow(): Dayjs取香港当前年月日，getFixedDate(string): Dayjs与locale.parse(locale: string,text: string,formats: string[]): Dayjs \| null使用UTC严格解析；get/add/set年月日时分秒及毫秒、isAfter/isValidate、locale周/月格式方法分别委托Dayjs同名能力；locale键zh_CN映射zh-cn。只适配日期值，不实现日历/UI；所有返回日期维持UTC载体，表单仍经formatPickerLocal与toUtcInstant提交真实UTC |
| order-filter-form.tsx：OrderFilterForm(props: OrderFilterFormProps): ReactElement | 移除时间文本框，两个Controller接DatePicker showTime/allowClear/秒级format/showNow=false；value/onChange经桥接，转发RHF ref/onBlur及id，保留label/错误/重置/freeze；提交仍调用normalizeOrderFilter |
| ExportScope.java：ExportScope(type: String,orderIds: List<Long>,filter: OrderFilter,excludedOrderIds: List<Long>) | 防御复制，排除缺省为空，旧三参数构造器转发；不自动改变旧请求JSON身份 |
| 同文件：validate(): void | 校验互斥字段、正ID、原输入大小和范围类型；调用filter.validate，失败用既有BusinessException |
| ExportTaskApiMapper.java：toCommand(dto: CreateTaskRequestDto,key: String): CreateExportCommand | 白名单允许FILTERED可选排除，解析两种数组；调用parseOrderIds、orders.toFilter、scope.validate、字段校验 |
| 同文件：parseOrderIds(node: JsonNode,allowEmpty: boolean): List<Long> | 共用ID列表结构/原长度/Long解析校验；不允许numeric/null等；toCommand调用 |
| 同文件：checkFields(node: JsonNode,required: Set<String>,optional: Set<String>): void | 精确必需项与可选项、拒绝未知；SELECTED传空optional；不以节点总数相等替代可选字段检查 |
| ExportRequestCanonicalizer.java：normalize(command: CreateExportCommand): CanonicalSubmission | 去重排序排除；空排除手工保持旧scope三字段序列化字节，非空v2含排除；调用hash，旧hash函数不变 |
| ExportRangeService.java：verify(scope: ExportScope): ExportRangeCheck | SELECTED沿用旧路径；FILTERED先校验排除存在，再count(filter)-countMatchingIds(filter,exclusions)，用真实数量执行空/上限判定；不信任客户端数 |
| OrderRepository.java：countMatchingIds(filter: OrderFilter,ids: List<Long>): long | 同筛选计算这些ID的匹配交集；只读端口，供范围校验 |
| OrderMapper.java：countMatchingIds(filter: OrderFilter,ids: List<Long>): long | 参数化当前批次查询；绑定OrderMapper.xml，无自有执行流程 |
| MyBatisOrderRepository.java：countMatchingIds(filter: OrderFilter,ids: List<Long>): long | 校验filter，空集合0，1000项分批累加mapper；无需全量ID |
| 同文件：readBatch(taskId: UUID,scope: ExportScope,after: OrderCursor,limit: int): OrderBatch | 保持scope.validate、keyset及领域映射，mapper对FILTERED读取排除关联；签名不变 |
| ExportTaskMapper.java：insertScopeOrderIds(taskId: String,orderIds: List<Long>): int；findScopeOrderIds(taskId: String): List<Long> | 替换原insertSelected/findSelected的中性名称；XML仍操作原表；供任务仓储，其他mapper方法复用 |
| MyBatisExportTaskRepository.java：insert(task: ExportTask,scope: ExportScope,fields: List<ExportField>): void | 插任务后从SELECTED包含或FILTERED排除取集合，按1000写关联；加入调用方创建事务，不提交独立事务 |
| 同文件：findSnapshot(taskId: UUID): Optional<TaskSnapshot> | 无论范围类型都加载关联并交converter；旧FILTERED空关联自然兼容 |
| ExportTaskPersistenceMapper.java：toSnapshot(row: ExportTaskRecord,scopeOrderIds: List<Long>): TaskSnapshot | SELECTED关联放orderIds；FILTERED关联放excludedOrderIds，原filter_json仍反序列化为OrderFilter；调用scope.validate |
| ExportTaskModels.java：TaskSnapshot.configurationHash(): String | 无排除维持原configuration-v1编码；非空以v2计入稳定排除，避免错误采用旧块；SHA256流程复用 |

toRecord/toResponse/任务事务入口/dispatcher/Worker.collect等无签名或逻辑变化，明确复用：toRecord继续存原filter_json；createSubmission→createExportTask→toCommand→normalize→verify→原CreateExportTransaction→仓储关联写入→快照→readBatch→既有文件/断点。新增排除不会进入源订单UPDATE路径。

## 测试文件与函数草稿

以下函数草稿在规格阶段形成；0.1批准后已按计划生成并运行测试，实际进度与缺口见任务表。Vitest describe/it回调无参→void或Promise<void>；JUnit方法无参→void throws Exception；每个名称/注释反向链接对应验收。具体用例覆盖和映射见[tasks.md](tasks.md#测试进度与证据)。

| 测试文件 | 拟增加/调整的具名用例与调用流程 |
| --- | --- |
| order-selection.test.ts | TC-301/303/304：调用reducer/isOrderSelected，验证全局补集、各页局部变化、两次反选及200000例外容量；纯内存输入 |
| order-management.test.tsx | TC-302/305：经真实Router/AntD页面和MSW分页响应点击/翻页/筛选/POP，检查跨页勾选及禁用，不请求所有页 |
| order-export.test.tsx | TC-306/308：从页面全选取消再提交，捕获实际POST和结果提示；断言排除及未提交草稿隔离 |
| order-export-state.test.tsx | TC-307：调用createSubmission/Hook，检查排除深度冻结及UNKNOWN原键原载荷 |
| order-api.test.ts | TC-309：共享createExportTask经过真实schema/client，验证新增可选项和旧请求兼容 |
| order-boundaries.test.tsx | 原TC-205/211调整控件操作；新增日期冻结及范围失效断言，不删原边界 |
| order-date-picker.test.tsx | TC-310/311：真实DatePicker日期/时间/清空/重置/单侧及倒置输入；桥接跨本机时区（含America/New_York春季跳时对应的香港合法时间）后检查HTTP UTC参数及控件回显 |
| order-date-validation.test.tsx | TC-310：真实表单从旧合法时间编辑为不存在的日历日期，再点击查询；必须出现关联错误且不提交旧值或空条件；review补充边界，先运行，现有输入桥接已满足，无反向补实现 |
| CreationInputBoundaryTest.java | TC-312：真实HTTP映射的新排除合法、非法、重复、缺省/空、互斥与长度边界 |
| IdempotencyMySqlTest.java | TC-313：旧字节摘要固定证据、空排除复用及非空排除顺序/冲突，真实事务重试 |
| ExportExclusionMySqlTest.java | TC-314/315：harness隔离任务库，来源现有小范围，校验计数/关联/回滚/旧任务和参数化keyset，不写订单 |
| ExportExclusionRecoveryTest.java | TC-316：少量既有订单真实文件、停止并重启本用例Worker或fixture、读回快照与配置hash；验证不包含取消项，不停共享broker |

## 验证、review及回退

测试先行报告及命令见[实际验证](../../docs/validation/003-select-all-filtered/verification.md)，证据存artifacts/003-select-all-filtered；测试先行时类型检查和测试编译通过，新增行为取得预期红灯；负责人以“继续开发”批准后生产功能已实施，最新绿灯见验证记录。实施后前端test:run/typecheck/build及后端Wrapper质量门禁按任务执行，后端须过滤原免验的性能和大规模文件用例，不能直接无筛选verify。日期组件内部文本解析已使用公开适配接口隔离本机DST，真实组件UTC/NY复验通过，见[已关闭核验事项](../../docs/decisions-pending.md#日期控件内部文本解析核验)。不会运行性能开关或大规模导出。实施后review重点是补集语义、错误/冻结、旧摘要与断点兼容、关联角色、只读来源和真实日期控件，而不是增加重复页面API。回退先停止新受理/消费，保留含排除范围任务及文件，优先修复本版再恢复；只在无本版受理任务时可回退旧消费者。
