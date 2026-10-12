# 函数设计草稿

**状态**：0.4设计已批准，当前进入测试先行；生产代码未实施，测试进度见[任务](tasks.md#测试进度与证据)。与[规格](spec.md)、[计划](plan.md)保持一致。自有类型见[数据模型](data-model.md)。

**从下面的创建流程开始看。** 正文只列推进流程的函数：签名说明输入输出，步骤说明内部怎么做，出口说明何时完成。辅助函数归入内部步骤，端口实现、SQL、目录和逐文件职责放在[对照附录](function-design-reference.md)。

## 一、创建任务

调用顺序：订单页确认 → submitOrderExport → createExportTask → 后端create → commit → 返回创建结果 → Hook保存CONFIRMED并统一提示任务号、清选择、关闭配置，保留订单页筛选/关键字/分页/排序。

| 参与函数与输入输出 | 内部步骤（按顺序） | 完成／失败出口 |
| --- | --- | --- |
| [submitOrderExport](function-design-reference.md#frontendsrcfeaturesordersapplicationorder-export-use-casets)<br>`submitOrderExport(submission: FrozenExportSubmission, createTask: CreateExportTaskApi, signal?: AbortSignal): Promise<FrozenExportSubmission>` | 接收冻结请求和创建API函数 → 调用POST → 返回CONFIRMED/REJECTED/UNKNOWN快照，页面操作统一交Hook。 | 成功只认POST结果；保留原请求/原键；用例不清选择、关闭配置或发通知。 |
| [createExportTask](function-design-reference.md#frontendsrcsharedapiapits)<br>`createExportTask(request: CreateExportRequest, idempotencyKey: string, signal?: AbortSignal): Promise<CreateExportResponse>` | 发送固定请求和幂等键 → 校验创建响应 → 返回创建结果。 | 不追加详情/确认GET；网络或提交结果不明返回UNKNOWN。 |
| [CreateExportTaskService.create](function-design-reference.md#backendsrcmainjavacomexportflowexporttasksapplicationcreateexporttaskservicejava)<br>`create(command: CreateExportCommand): CreateTaskResult` | 规范化请求 → 已提交幂等快查 → 新请求交commit；唯一冲突/提交异常在失败事务之外核对。 | 同键同请求返回原任务，同键不同请求拒绝；无法证明提交则UNKNOWN。 |
| [CreateExportTransaction.commit](function-design-reference.md#backendsrcmainjavacomexportflowexporttasksapplicationcreateexporttransactionjava)<br>`commit(submission: CanonicalSubmission): CreateTaskResult` | 同事务重核幂等 → 校验范围/数量 → 生成身份 → 写任务、范围、幂等、Outbox → 确认提交。 | 全部提交后返回成功；任一步失败整体回滚；RabbitMQ不参与该事务。 |

为什么拆这4个函数：提交状态转换与HTTP协议分开，页面反馈集中在Hook；后端事务外协调与原子创建事务分开。normalize、verify、身份生成、reconcile是内部步骤，不再作为正文的独立评审项，也没有另一个创建确认接口。

confirm和retryUnknown均把用例返回的快照交给Hook内部applySubmissionResult：先保存状态，成功时统一清选择、关闭配置、提示任务号、恢复焦点；页面操作异常不改写成功、不重发请求。未知结果重试复用原请求/原键。

追踪：[T-022、T-026](tasks.md#实施与测试任务)，TC-208至TC-210、TC-216至TC-218。

## 二、后台投递

调用顺序：调度扫描领取Outbox → dispatchOne → 发布确认与路由核验 → 标记SENT。创建响应不等待投递。

| 参与函数与输入输出 | 内部步骤（按顺序） | 完成／失败出口 |
| --- | --- | --- |
| [OutboxDispatchService.dispatchOne](function-design-reference.md#backendsrcmainjavacomexportflowdispatchapplicationoutboxdispatchservicejava)<br>`dispatchOne(lease: DispatchLease): DispatchOutcome` | 核对投递权及队列策略 → 事务外publish → confirm且正确路由 → 短事务markSent。 | 单条失败退避，发布/提交不明先回读；共享依赖故障停止本批。 |

投递集中在一个服务内；扫描和reconcile只是辅助步骤。到期扫描独立于MQ线程，只关闭未发送且未开工的到期命令，不误伤SENT/已开工任务，见[到期设计](function-design-reference.md#backendsrcmainjavacomexportflowdispatchapplicationoutboxtimeoutservicejava)。

追踪：[T-027、T-028](tasks.md#实施与测试任务)，TC-220至TC-222。

## 三、后台执行并生成Excel

调用顺序：监听器解码 → handle → claim提交确认 → generate → collect → 装配/关闭/校验/发布/READY登记 → complete提交成功 → 核对终态 → 监听器ACK。已有可信READY可直接复用。

| 参与函数与输入输出 | 内部步骤（按顺序） | 完成／失败出口 |
| --- | --- | --- |
| [ExportCommandHandler.handle](function-design-reference.md#backendsrcmainjavacomexportflowworkerapplicationexportcommandhandlerjava)<br>`handle(command: ExportCommandEnvelope, delivery: DeliveryContext): DeliveryDecision` | 核对任务及生成能力 → claim → generate → 对明确结果complete并核对 → 返回消费裁决。 | 已提交终态才ACK；未知或失权不ACK，活跃租约下重复消息不再生成。 |
| [TaskExecutionService.claim](function-design-reference.md#backendsrcmainjavacomexportflowworkerapplicationtaskexecutionservicejava)<br>`claim(taskId: UUID, owner: String): ClaimResult` | 按固定锁序及数据库时间核对 → 首次认领或过期且退避到期接管 → 裁决owner/fence及恢复预算 → 确认提交。 | 提交确认后才能写文件；BUSY不执行、UNKNOWN不假定获权，预算耗尽事务内失败。 |
| [PoiExportFileGenerator.generate](function-design-reference.md#backendsrcmainjavacomexportflowworkerinfrastructureexcelpoiexportfilegeneratorjava)<br>`generate(context: GenerationContext, guard: ExecutionGuard): GenerationResult` | 已有READY：安全定位正式文件 → 统一校验 → 与数据库证明核对；新文件：collect → 装配/关闭 → 安全定位临时文件 → 同一校验 → 原子改名 → READY登记，不重复校验。 | 返回READY/明确失败/UNKNOWN；临时文件不可下载，READY尚不是任务成功。 |
| [ExportDataCollectionService.collect](function-design-reference.md#backendsrcmainjavacomexportflowworkerapplicationexportdatacollectionservicejava)<br>`collect(context: GenerationContext, guard: ExecutionGuard): CompletedDataset` | 核对可信连续断点 → 分批读订单 → 数据块先落盘 → 断点及真实进度同事务提交 → 返回完整块清单。 | 失权停止；提交不明先对账，不删除可信块；Excel失败可以复用已完成数据。 |
| [TaskExecutionService.complete](function-design-reference.md#backendsrcmainjavacomexportflowworkerapplicationtaskexecutionservicejava)<br>`complete(lease: ExecutionLease, result: GenerationResult): LeaseWriteResult` | 接收事务外已验证产物 → 短事务核对执行权、READY配置和引用 → 原子采用产物并提交成功；明确失败保留进度。 | UNKNOWN不提交终态；提交不明先回读，不重复长文件校验、不删除候选产物。 |

为什么保留这5个入口：消费裁决、数据库执行权、文件生成、可恢复数据收集、成功事务各有不同完成边界。装配/校验/刷盘/关闭是generate内部步骤，完整签名放在附录；不再逐项重复解释主流程。

心跳/guard保护执行权；断点事务唯一推进进度。孤儿清理必须经数据库授权，保护成功及断点引用。这些异常保障设计见[Worker边界](contracts/worker-boundary.md)、[Excel与恢复方案](excel-design.md)，本期不接前端状态展示或SSE。

追踪：[T-029、T-032、T-033](tasks.md#实施与测试任务)，TC-223至TC-225、TC-230至TC-233、TC-235、TC-236。

## 四、查询与下载入口（按需看）

查询：已提交条件 → queryOrders → HTTP → OrderQueryService.query → 返回分页。下载：HTTP → prepare → 返回可关闭句柄 → 响应流；前端本期没有下载按钮。

| 参与函数与输入输出 | 内部步骤（按顺序） | 完成／失败出口 |
| --- | --- | --- |
| [queryOrders](function-design-reference.md#frontendsrcsharedapiapits)<br>`queryOrders(query: OrderQuery, signal?: AbortSignal): Promise<PageResult<OrderDto>>` | 编码受控参数 → GET → 校验响应结构。 | 返回订单页或ApiRequestError；查询生命周期阻止旧响应覆盖新查询。 |
| [OrderQueryService.query](function-design-reference.md#backendsrcmainjavacomexportflowordersapplicationorderqueryservicejava)<br>`query(query: OrderQuery): PageSlice<Order>` | 校验条件 → 短只读事务count/page → 稳定分页。 | 数据库不可用返回错误，不使用假数据。 |
| [DownloadExportFileService.prepare](function-design-reference.md#backendsrcmainjavacomexportflowexporttasksapplicationdownloadexportfileservicejava)<br>`prepare(taskId: UUID): DownloadHandle` | 核对SUCCEEDED及唯一成功引用 → 安全定位/可读/大小检查 → 形成文件名和下载头 → 返回句柄。 | 不存在404、非成功409、成功文件丢失410、DB不可读503；传输期间不持有事务，不自动重跑。 |

追踪：[T-021、T-025、T-034](tasks.md#实施与测试任务)，TC-201至TC-207、TC-211、TC-215、TC-234。

## 附录入口

完整签名清单与文件落点在[实现对照附录](function-design-reference.md)维护。辅助函数只写必要步骤，纯转发合并；类型、配置、SQL、执行保护和清理见附录；目录见[计划文末](plan.md#项目结构)。
