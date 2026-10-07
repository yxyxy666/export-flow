# 功能规格：订单管理与后端异步导出流程

**功能标识**：`002-order-management`（文档目录标识，未创建 Git 分支）  
**日期／版本**：2026-10-07／草稿 0.3  
**状态**：规格待评审；两次实施门禁均未批准。  
**输入**：准备订单界面前后端规格，完成查询筛选等能力与后端架构流程；负责人追加要求本次加入真实 Excel 实现。列明文件、新增／修改函数、参数、返回值和调用关系。

## 规格交付与阶段边界

本期规划交付真实订单查询、筛选、分页、选择、导出配置、任务创建及幂等找回；后端从可运行工程、MySQL 迁移到事务 Outbox、RabbitMQ 发布、自动超时关闭、任务快照查询均需真实实现。Worker 实际消费并认领任务，稳定分批读取订单，持久化可复用数据块与断点，使用 Apache POI SXSSF 生成真正的 Excel，关闭校验并安全发布产物，再提交成功与元数据。明确失败保留实际进度及安全错误；消费者只在已提交终态或权威证明无需处理后确认消费。

本次交付演示环境启用真实消费者，生成能力必须通过启动检查，不保留未实现返回值。任务回执提供单次手动刷新、成功文件信息及同源下载入口，形成“查询→创建→消费→生成→下载”的可演示链路。完整任务列表、按需 SSE 和失败任务重试页面仍留后续；不以当前回执取代产品完整任务管理页。新增范围设计见 [Excel 与恢复方案](excel-design.md)，执行权、异常接管及文件对账子集已按本次明确决策在[ADR-0008](../../docs/adr/0008-worker-execution-lease-and-recovery.md)接受；暂停与显式恢复不纳入当前MVP。

完整结构、逐文件职责和函数设计由 [plan.md](plan.md) 及其直接链接的 [function-design.md](function-design.md) 承载。数据见 [data-model.md](data-model.md)，边界见 [contracts/README.md](contracts/README.md)，测试、审批与追踪见 [tasks.md](tasks.md)。所有附属产物随三份核心文档共同评审。

风险为**高风险**：首次真实持久化、幂等唯一约束、Outbox 多实例领取、超时与认领并发一致性。既有基础骨架审批不覆盖本期。规格批准只允许测试先行；测试阶段门禁通过且负责人再次明确批准后，才实施生产代码，依据 [AGENTS.md](../../AGENTS.md) 与[测试规则](../../docs/rules/testing.md#测试先行阶段与审批)。本次仅生成文档。

## 用户场景与测试

### 用户故事一：查找并选择订单（P1）

运营人员进入订单管理，输入筛选条件，分页浏览，跨页勾选订单。优先解决真实数据可查询、输入有效、结果稳定问题。可独立通过前端组件测试、接口契约及真实 MySQL 查询测试验证，不依赖 Excel 或任务消费者。

验收关联：[`AC-001`](../../docs/product/acceptance-criteria.md#ac-001)、[`AC-002`](../../docs/product/acceptance-criteria.md#ac-002)、[`AC-003`](../../docs/product/acceptance-criteria.md#ac-003)、[`AC-004`](../../docs/product/acceptance-criteria.md#ac-004)、[`AC-005`](../../docs/product/acceptance-criteria.md#ac-005)、[`AC-020`](../../docs/product/acceptance-criteria.md#ac-020)、[`AC-021`](../../docs/product/acceptance-criteria.md#ac-021)、[`AC-022`](../../docs/product/acceptance-criteria.md#ac-022)、[`AC-023`](../../docs/product/acceptance-criteria.md#ac-023)、[`AC-024`](../../docs/product/acceptance-criteria.md#ac-024)。已确认增强纳入本规格：[`AC-033`](../../docs/product/acceptance-criteria.md#ac-033)、[`AC-034`](../../docs/product/acceptance-criteria.md#ac-034)、[`AC-035`](../../docs/product/acceptance-criteria.md#ac-035)、[`AC-061`](../../docs/product/acceptance-criteria.md#ac-061)、[`AC-065`](../../docs/product/acceptance-criteria.md#ac-065)。默认排序仍遵循已确认基线；排序切换不改默认值。

### 用户故事二：提交可找回的导出申请（P1）

用户选择已勾选订单或已提交筛选范围，配置字段并确认；明确成功后清空选择并进入任务管理。明确失败保留输入；网络超时或提交不明时保留同次提交和原幂等键，用原载荷找回结果。可独立使用前端 MSW 与真实数据库创建接口测试验证，不等待消息或文件。

验收关联：[`AC-006`](../../docs/product/acceptance-criteria.md#ac-006)、[`AC-007`](../../docs/product/acceptance-criteria.md#ac-007)、[`AC-008`](../../docs/product/acceptance-criteria.md#ac-008)、[`AC-009`](../../docs/product/acceptance-criteria.md#ac-009)、[`AC-010`](../../docs/product/acceptance-criteria.md#ac-010)、[`AC-011`](../../docs/product/acceptance-criteria.md#ac-011)、[`AC-019`](../../docs/product/acceptance-criteria.md#ac-019)、[`AC-025`](../../docs/product/acceptance-criteria.md#ac-025)、[`AC-030`](../../docs/product/acceptance-criteria.md#ac-030)、[`AC-050`](../../docs/product/acceptance-criteria.md#ac-050)、[`AC-051`](../../docs/product/acceptance-criteria.md#ac-051)。已确认字段配置和幂等冲突 [`AC-060`](../../docs/product/acceptance-criteria.md#ac-060)、[`AC-064`](../../docs/product/acceptance-criteria.md#ac-064) 纳入本规格，完整定义仅在[验收基线](../../docs/product/acceptance-criteria.md#订单管理分阶段交付候选)维护；原未实现阶段候选撤出本次映射，编号不复用。

### 用户故事三：后台可靠交接并观察受理结果（P1）

即使 RabbitMQ 暂不可用，已提交任务仍可查；后台在窗口内补投，到期按权威状态关闭。Worker 生成真实文件，前端回执可刷新权威状态并下载成功产物。通过真实 MySQL、RabbitMQ 和文件系统验证命令重复、执行中断、断点恢复、文件发布与终态提交等窗口；测试替身只作补充。完整任务管理页仍另行规格。

验收关联：[`AC-052`](../../docs/product/acceptance-criteria.md#ac-052)、[`AC-053`](../../docs/product/acceptance-criteria.md#ac-053)、[`AC-054`](../../docs/product/acceptance-criteria.md#ac-054)、[`AC-055`](../../docs/product/acceptance-criteria.md#ac-055)、[`AC-056`](../../docs/product/acceptance-criteria.md#ac-056)、[`AC-049`](../../docs/product/acceptance-criteria.md#ac-049)。真实文件、下载、元数据与永久保留关联 [`AC-013`](../../docs/product/acceptance-criteria.md#ac-013)、[`AC-014`](../../docs/product/acceptance-criteria.md#ac-014)、[`AC-015`](../../docs/product/acceptance-criteria.md#ac-015)、[`AC-018`](../../docs/product/acceptance-criteria.md#ac-018)。已确认新增条目 [`AC-067`](../../docs/product/acceptance-criteria.md#ac-067)、[`AC-068`](../../docs/product/acceptance-criteria.md#ac-068)、[`AC-069`](../../docs/product/acceptance-criteria.md#ac-069)、[`AC-070`](../../docs/product/acceptance-criteria.md#ac-070)、[`AC-071`](../../docs/product/acceptance-criteria.md#ac-071) 纳入当前规格，产品口径与架构决策已确认。

### 用户故事四：导航与错误可恢复（P2）

沿用既有侧栏与 404，订单查询错误可手动重试，创建错误不能清空输入。关联 [`AC-029`](../../docs/product/acceptance-criteria.md#ac-029)、[`AC-041`](../../docs/product/acceptance-criteria.md#ac-041)，只回归本期受影响导航。

### 边界情况

- 筛选编辑不影响当前结果，只有成功校验并提交后更新查询键；筛选导出读取已提交条件，不能读取编辑中表单或当前页数据。
- 新筛选、重置清空选择；翻页保留选择；排序切换回第一页，保留同一筛选内的选择。超出结果范围的页显示空页及总数，不循环自动请求。
- 时间与金额允许只填一侧；范围错误、未知枚举、非法分页、非两位定点金额在前后端分别拒绝；旧响应不得覆盖新查询。
- 结果尚未加载、错误或过期筛选切换中，不允许依据旧总数提交筛选导出；空范围、无字段、非法字段、重复字段、不存在订单由后端拒绝且不留下任务。
- 同键异载荷、并发唯一约束、事务明确回滚与提交结果不明分别处理。响应不明期间固定提交快照，提供“重试确认”，不能用编辑后的内容复用旧键。
- 已发送但等待消费者的任务不能因等待超过十分钟失败；未发送、未开工的到期任务由独立扫描器处理；迟到发布确认不能复活关闭命令。
- 关键字可能含客户姓名／手机号，不写入 URL 或长期存储；其刷新与前进后退限制在契约明确。

### 验收标准测试映射与覆盖缺口

逐条 AC—实现、AC—测试及 SC—验证映射见 [tasks.md](tasks.md#ac实现任务映射)；测试设计尚未转为代码。完整任务页/SSE的延期范围与尚未执行的环境/测试验证缺口见 [tasks.md](tasks.md#覆盖缺口与阶段完成检查)，不得用 mock 文件替代本次真实 Excel 验收。

## 需求

- **FR-001**：以现有订单路由替换文字占位，采用 Ant Design 组件组合筛选、选择工具、表格、分页和导出字段对话框；查询请求走真实后端。
- **FR-002**：前端分离编辑表单、已提交查询、跨页选择、对话框草稿和冻结提交；已提交非敏感条件与分页映射 URL。关键字只在内存。
- **FR-003**：订单接口与创建时范围校验复用同一筛选语义；只读合成数据由明确运行的可重复脚本初始化，不能启动时自动造数。
- **FR-004**：任务、范围快照、有序字段、幂等结果与创建命令同事务提交；只在提交确认后响应；重放读取当前摘要。
- **FR-005**：dispatcher 真实发布、确认与正确路由核对、领取租约、持久退避、坏记录隔离及独立到期扫描均实现；时间与并发裁决由 MySQL 决定。
- **FR-006**：Worker 与 SXSSF 实现通过端口隔离；本期真实生成文件，采用有界源数据批次与工作簿窗口，不加载完整订单或工作簿到内存。执行权、断点、发布和异常恢复遵循 [Excel方案](excel-design.md)，消费者在能力和架构门禁满足后启用。
- **FR-007**：任务只读回执从 MySQL 读取任务 ID、范围数量、所选字段、状态、实际进度和安全错误，不使用轮询、SSE 或 Redis 推测业务状态。
- **FR-008**：所有协议、数据模型、状态、函数及失败出口在附属产物中定义；技术与参数逐项在[决策记录](decision-record.md)定稿；未执行能力验证据实登记。
- **FR-009**：生成按固定范围及字段顺序，状态／渠道中文与未知回退、金额精度、客户原值、香港时间及文件名遵循产品基线；下载只解析 MySQL 成功产物引用，禁止前端 Blob 缓冲。
- **FR-010**：不可变数据块断点可跨进程重启恢复；旧执行身份不能登记断点、进度或结果；就绪产物对账优先于重建。异常接管只使用原未确认命令，禁止扫描 RUNNING 新建恢复 Outbox；没有可信断点时，只有证明重复安全才建立新数据代次。
- **FR-011**：Excel写入期间逐批把已完成行及缓冲刷入磁盘临时文件；全部完成后将关闭、校验通过的.xlsx.part原子改名为正式.xlsx，再登记权威结果。临时与正式文件身份、刷盘函数及失败处理见[Excel方案](excel-design.md#sxssf内容与资源)和[函数清单](function-design.md)。

关键实体：订单、查询条件、分页、导出范围、字段列表、冻结提交、任务、幂等登记、命令 Outbox、投递尝试、执行权、生成结果。字段与生命周期见 [data-model.md](data-model.md)。

## 非目标

本期不实现用户暂停恢复、失败任务重试页面、完整任务列表、SSE、跨实例通知、Redis 实际缓存适配、生产部署或新增 Compose。不引入暂停状态或恢复命令，不将半成品 XLSX 当断点。异常断点恢复属于本次方案；Redis 缓存和连续前端进度通知另行规格，本期回执采用显式单次刷新。

## 成功标准

本期验证 [`SC-001`](../../docs/product/acceptance-criteria.md#sc-001)、[`SC-002`](../../docs/product/acceptance-criteria.md#sc-002)、[`SC-003`](../../docs/product/acceptance-criteria.md#sc-003)、[`SC-004`](../../docs/product/acceptance-criteria.md#sc-004)、[`SC-005`](../../docs/product/acceptance-criteria.md#sc-005)、[`SC-006`](../../docs/product/acceptance-criteria.md#sc-006)、[`SC-007`](../../docs/product/acceptance-criteria.md#sc-007)。文件指标必须使用真实产物和获批参考环境测量；已确认交付指标 [`SC-008`](../../docs/product/acceptance-criteria.md#sc-008)、[`SC-009`](../../docs/product/acceptance-criteria.md#sc-009)、[`SC-010`](../../docs/product/acceptance-criteria.md#sc-010) 纳入本次验证映射。持续通知与完整任务列表另行验收，不能把手动回执视为连续更新已完成。

## 已确认前提与待确认事项

已确认依据为 [PRD](../../docs/product/order-export-prd.md)、[前后端规则](../../docs/rules/backend.md)、已接受 [ADR-0002](../../docs/adr/0002-frontend-stack-and-test-scope.md) 至 [ADR-0007](../../docs/adr/0007-outbox-dispatch-retry.md)。[学习稿](../../DATA-FLOW.md)仅帮助展开处理顺序，候选图不能替代批准。

已确认本次需求包括真实 Excel 和真实消费，不再待选“留空还是实现”。负责人已明确“解决下所有待决策项，按你的建议来”。执行恢复子集、依赖版本、测试工具、隔离配置、异常消息、参数和参考环境全部定稿，见[决策记录](decision-record.md)、[版本表](dependency-versions.md)、[ADR-0008](../../docs/adr/0008-worker-execution-lease-and-recovery.md)与[ADR-0009](../../docs/adr/0009-engineering-runtime-and-progress-notification.md)。没有技术待选项；连接、兼容、文件系统、测试与性能尚未验证。决定技术方案不等于已批准整个规格或测试阶段，两次开发门禁保持待审。
