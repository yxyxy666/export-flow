# 逐文件职责与函数设计草稿

**状态**：草稿 0.3；由 [plan.md](plan.md)直接引用，与[spec.md](spec.md)、[tasks.md](tasks.md)、[决策记录](decision-record.md)及[Excel方案](excel-design.md)共同审批。新增代码文件尚未生成，修改占位是首次实现，不存在可沿用旧业务方法。类型见[data-model.md](data-model.md)，边界见[contracts](contracts/README.md)。

签名是类型化设计而非代码：`?`为可选或可空，其余参数必填；无参数明确标注，无返回使用void。框架类型、机械record构造/访问器及无行为的Bean装配不展开；业务回调、组件、Hook返回动作与端口逐项列出。新增自有函数须同步设计后评审；测试只列TC场景，不写可执行函数体。

本次包含真实Excel、数据块断点、异常有界接管、文件校验发布及下载；负责人本次已接受架构子集与技术参数，规格整体与测试阶段仍未批准，见[决策记录](decision-record.md)。只有Redis/SSE、完整任务列表与用户暂停等标为后续。生成器不再保留未实现返回分支，演示角色启用可用消费者。

## 逐文件职责清单

| 文件 | 变动与阶段 | 所属层／内容 | 实现任务／测试 |
| --- | --- | --- | --- |
| `frontend/src/app/app-providers.tsx` | 修改 | 组合：增加 QueryClientProvider；保留中文 Provider | [T-020](tasks.md#实施与测试任务)；TC-201、TC-211 |
| `frontend/src/app/query-client.ts` | 新增 | 组合：默认查询取消、重试策略与 mutation 禁止自动重试 | [T-020](tasks.md#实施与测试任务)；TC-211 |
| `frontend/src/features/orders/pages/order-management-page.tsx` | 修改占位 | 页面：订单页面组合，不直接拼 URL | [T-021](tasks.md#实施与测试任务)；TC-201、TC-202、TC-203、TC-205 |
| `frontend/src/features/orders/components/order-filter-form.tsx` | 修改占位 | 组件：React Hook Form + Zod 的七组筛选 | [T-021](tasks.md#实施与测试任务)；TC-202、TC-204、TC-206 |
| `frontend/src/features/orders/components/order-table.tsx` | 修改占位 | 组件：九业务列、选择列、time 排序、空态 | [T-021](tasks.md#实施与测试任务)；TC-203、TC-207 |
| `frontend/src/features/orders/components/export-config-dialog.tsx` | 修改占位 | 组件：九字段选择、最少一项、冻结提交与结果不明提示 | [T-022](tasks.md#实施与测试任务)；TC-208、TC-209 |
| `frontend/src/features/orders/hooks/use-orders.ts` | 修改占位 | 应用Hook：读状态与查询动作；数据由 Query 管理 | [T-021](tasks.md#实施与测试任务)；TC-201、TC-202、TC-211 |
| `frontend/src/features/orders/hooks/use-order-query-state.ts` | 新增 | 状态：URL非敏感条件与内存 keyword | [T-021](tasks.md#实施与测试任务)；TC-206、TC-211 |
| `frontend/src/features/orders/hooks/use-order-selection.ts` | 新增 | 状态：跨页选择的单一所有者 | [T-021](tasks.md#实施与测试任务)；TC-207 |
| `frontend/src/features/orders/hooks/use-order-export.ts` | 新增 | 应用Hook：对话框与提交快照所有者 | [T-022](tasks.md#实施与测试任务)；TC-208、TC-209、TC-210 |
| `frontend/src/features/orders/application/order-export-use-case.ts` | 修改占位 | 用例：无 React 依赖的创建结果编排 | [T-022](tasks.md#实施与测试任务)；TC-209、TC-210 |
| `frontend/src/features/orders/api/orders-api.ts` | 修改占位 | API：订单 DTO 序列化／运行时响应校验 | [T-021](tasks.md#实施与测试任务)；TC-204、TC-211 |
| `frontend/src/features/orders/models/order-view-model.ts` | 修改占位 | 展示模型：中文枚举和字段格式化 | [T-021](tasks.md#实施与测试任务)；TC-204 |
| `frontend/src/features/orders/models/order-query.ts` | 新增 | 纯函数：输入校验、UTC／金额规范化、URL 规则 | [T-021](tasks.md#实施与测试任务)；TC-204、TC-206 |
| `frontend/src/features/orders/models/order-selection.ts` | 新增 | 纯函数：跨页集合操作 reducer | [T-021](tasks.md#实施与测试任务)；TC-207 |
| `frontend/src/features/orders/types/order.types.ts` | 修改占位 | 类型：OrderId/OrderDto/OrderQuery/FilterForm/props/selection 等，定义见数据模型；无函数 | [T-021](tasks.md#实施与测试任务)；TC-204 |
| `frontend/src/features/orders/models/order-schema.ts` | 新增 | 边界Schema：orderFilterSchema、orderPageSchema；金额正则、枚举、日期、页大小及范围；无自有函数，声明式 Zod schema | [T-021](tasks.md#实施与测试任务)；TC-204 |
| `frontend/src/features/export-tasks/api/export-tasks-api.ts` | 修改占位 | API：创建、单任务权威回执和安全同源下载URL | [T-022](tasks.md#实施与测试任务)、[T-034](tasks.md#实施与测试任务)；TC-210、TC-234 |
| `frontend/src/features/export-tasks/types/export-task.types.ts` | 修改占位 | 类型：ExportScope/ExportField/CreateExportRequest/TaskSummary/FrozenExportSubmission/回执 props；无函数 | [T-022](tasks.md#实施与测试任务)；TC-210 |
| `frontend/src/features/export-tasks/models/export-task-schema.ts` | 新增 | 边界Schema：创建请求和任务摘要 Zod schema，未知状态作为协议错误；无自有函数 | [T-022](tasks.md#实施与测试任务)；TC-210 |
| `frontend/src/features/export-tasks/pages/task-management-page.tsx` | 修改 | 页面：本次真实任务回执、手动刷新、文件名/大小/结束时间与直接下载；完整任务列表另规格 | [T-023](tasks.md#实施与测试任务)、[T-034](tasks.md#实施与测试任务)；TC-212、TC-234 |
| `frontend/src/shared/api/http-client.ts` | 修改占位 | 共享API：订单和任务 API 共同调用 | [T-020](tasks.md#实施与测试任务)；TC-211 |
| `frontend/src/shared/api/api-error.ts` | 修改占位 | 共享API：ApiError 类型和唯一错误分类 | [T-020](tasks.md#实施与测试任务)；TC-211 |
| `frontend/src/shared/types/page-result.ts` | 修改占位 | 共享类型：PageResult<T>；Order 与 Task 单次查询共享结构；无函数 | [T-020](tasks.md#实施与测试任务)；TC-204 |
| `frontend/src/shared/formatters/money.ts` | 修改占位 | 共享展示：保持金额精度的页面格式化 | [T-020](tasks.md#实施与测试任务)；TC-204 |
| `frontend/src/shared/formatters/date-time.ts` | 修改占位 | 共享展示：仅香港时区与 UTC 协议转换 | [T-020](tasks.md#实施与测试任务)；TC-204 |
| `backend/src/main/java/com/exportflow/ExportFlowApplication.java` | 修改占位 | 入口：真实 Spring Boot 主入口 | [T-024](tasks.md#实施与测试任务)；TC-213 |
| `backend/src/main/java/com/exportflow/config/RuntimeConfiguration.java` | 修改占位 | 装配：端口/调度器与Jackson输入文档长度限制，完整消费JSON再进入Controller | [T-024](tasks.md#实施与测试任务)；TC-213、TC-214、TC-225 |
| `backend/src/main/java/com/exportflow/config/RuntimeProperties.java` | 新增 | 配置类型：DB/MQ连接引用、运行角色、演示角色consumerEnabled=true、DispatchPolicy/ExecutionPolicy、源批/窗口/字节预算/文件根/池/清理/监督字段，类型见data-model；无业务函数 | [T-024](tasks.md#实施与测试任务)；TC-213、TC-225 |
| `backend/src/main/java/com/exportflow/shared/api/ApiErrorResponse.java` | 修改占位 | 传输类型：错误体 code/message/traceId/fieldErrors/details/outcome；无自有函数 | [T-024](tasks.md#实施与测试任务)；TC-214 |
| `backend/src/main/java/com/exportflow/shared/api/ApiExceptionHandler.java` | 修改占位 | 传输：全局安全异常映射 | [T-024](tasks.md#实施与测试任务)；TC-214 |
| `backend/src/main/java/com/exportflow/shared/domain/BusinessException.java` | 新增 | 领域错误：领域安全错误code/message/fieldErrors/details；另含 outcome，不按异常类名推断事务结果；无业务函数（机械构造除外） | [T-024](tasks.md#实施与测试任务)；TC-214 |
| `backend/src/main/java/com/exportflow/shared/domain/PageSlice.java` | 新增 | 领域类型：PageRequest/PageSlice<T> 值类型；无自有函数（机械构造除外） | [T-025](tasks.md#实施与测试任务)；TC-215 |
| `backend/src/main/java/com/exportflow/orders/api/OrderController.java` | 修改占位 | 传输：GET 订单接口 | [T-025](tasks.md#实施与测试任务)；TC-214、TC-215 |
| `backend/src/main/java/com/exportflow/orders/api/OrderApiModels.java` | 新增 | 传输类型：嵌套记录 OrderQueryDto/OrderResponse/PageResult，约束与 OpenAPI 一致；无业务函数 | [T-025](tasks.md#实施与测试任务)；TC-214 |
| `backend/src/main/java/com/exportflow/orders/api/OrderApiMapper.java` | 新增 | 传输映射：DTO与领域明确转换 | [T-025](tasks.md#实施与测试任务)；TC-214 |
| `backend/src/main/java/com/exportflow/orders/application/OrderQueryService.java` | 修改占位 | 应用：短只读事务订单查询 | [T-025](tasks.md#实施与测试任务)；TC-215 |
| `backend/src/main/java/com/exportflow/orders/domain/Order.java` | 修改占位 | 领域：订单不可变模型；字段与枚举见 data-model；无业务函数 | [T-025](tasks.md#实施与测试任务)；TC-215 |
| `backend/src/main/java/com/exportflow/orders/domain/OrderQuery.java` | 新增 | 领域：OrderFilter/SortDirection 嵌套值类型与不变量；复用shared PageRequest | [T-025](tasks.md#实施与测试任务)；TC-214、TC-215 |
| `backend/src/main/java/com/exportflow/orders/port/OrderRepository.java` | 修改占位 | 端口：订单只读分页与本期生成keyset批读；SELECTED按taskId连接固定关联 | [T-025](tasks.md#实施与测试任务)、[T-032](tasks.md#实施与测试任务)；TC-215、TC-230、TC-235 |
| `backend/src/main/java/com/exportflow/orders/infrastructure/persistence/MyBatisOrderRepository.java` | 修改占位 | 适配：实现订单列表、存在计数与生成批读；数据库记录映射领域 | [T-025](tasks.md#实施与测试任务)、[T-032](tasks.md#实施与测试任务)；TC-215、TC-230、TC-235 |
| `backend/src/main/java/com/exportflow/orders/infrastructure/persistence/OrderMapper.java` | 修改占位 | SQL边界：MyBatis 方法与 XML 参数同名 | [T-025](tasks.md#实施与测试任务)、[T-032](tasks.md#实施与测试任务)；TC-215、TC-230、TC-235 |
| `backend/src/main/java/com/exportflow/orders/infrastructure/persistence/OrderRecord.java` | 新增 | 记录类型：与 orders 表字段逐项对应；无业务函数 | [T-025](tasks.md#实施与测试任务)；TC-215 |
| `backend/src/main/java/com/exportflow/orders/infrastructure/persistence/OrderPersistenceMapper.java` | 新增 | 记录映射：SQL记录与领域隔离 | [T-025](tasks.md#实施与测试任务)；TC-215 |
| `backend/src/main/java/com/exportflow/exporttasks/api/ExportTaskController.java` | 修改占位 | 传输：仅 POST创建及 GET单任务摘要 | [T-026](tasks.md#实施与测试任务)；TC-216、TC-219 |
| `backend/src/main/java/com/exportflow/exporttasks/api/ExportTaskApiModels.java` | 新增 | 传输类型：嵌套 CreateTaskRequestDto/TaskSummaryResponse；判别联合 schema；无业务函数 | [T-026](tasks.md#实施与测试任务)；TC-216 |
| `backend/src/main/java/com/exportflow/exporttasks/api/ExportTaskApiMapper.java` | 新增 | 映射：不共用传输与领域模型 | [T-026](tasks.md#实施与测试任务)；TC-216 |
| `backend/src/main/java/com/exportflow/exporttasks/application/CreateExportTaskService.java` | 修改占位 | 应用：无外层长事务的幂等创建协调 | [T-026](tasks.md#实施与测试任务)；TC-216、TC-217、TC-218 |
| `backend/src/main/java/com/exportflow/exporttasks/application/CreateExportTransaction.java` | 新增 | 事务用例：单一显式短事务；正常返回即提交已确认 | [T-026](tasks.md#实施与测试任务)；TC-217、TC-218 |
| `backend/src/main/java/com/exportflow/exporttasks/application/ExportRangeService.java` | 新增 | 应用：列表与导出范围复用订单条件 | [T-026](tasks.md#实施与测试任务)；TC-216 |
| `backend/src/main/java/com/exportflow/exporttasks/application/ExportRequestCanonicalizer.java` | 新增 | 应用纯函数：摘要规范化版本1 | [T-026](tasks.md#实施与测试任务)；TC-217 |
| `backend/src/main/java/com/exportflow/exporttasks/application/ExportTaskQueryService.java` | 修改占位 | 应用：单任务权威快照；不读Redis | [T-026](tasks.md#实施与测试任务)；TC-219 |
| `backend/src/main/java/com/exportflow/exporttasks/domain/ExportTask.java` | 修改占位 | 领域：任务状态与不变量；终态不可退回 | [T-026](tasks.md#实施与测试任务)、[T-028](tasks.md#实施与测试任务)、[T-029](tasks.md#实施与测试任务)；TC-218、TC-222、TC-224 |
| `backend/src/main/java/com/exportflow/exporttasks/domain/ExportScope.java` | 新增 | 领域：SELECTED/FILTERED 及稳定读取 OrderCursor(createdAt,id) | [T-026](tasks.md#实施与测试任务)；TC-216 |
| `backend/src/main/java/com/exportflow/exporttasks/domain/ExportField.java` | 新增 | 领域：九字段枚举，中文列名与默认序列；无POI依赖 | [T-026](tasks.md#实施与测试任务)；TC-216 |
| `backend/src/main/java/com/exportflow/exporttasks/domain/ExportTaskModels.java` | 新增 | 领域类型：TaskStatus/TaskSummary/TaskSnapshot/CreateExportCommand/CanonicalSubmission/CreateTaskResult/ExportRangeCheck/IdempotencyRecord；无业务函数 | [T-026](tasks.md#实施与测试任务)；TC-216 |
| `backend/src/main/java/com/exportflow/exporttasks/port/ExportTaskRepository.java` | 修改占位 | 端口：权威任务持久端口 | [T-026](tasks.md#实施与测试任务)、[T-028](tasks.md#实施与测试任务)；TC-218、TC-219、TC-222 |
| `backend/src/main/java/com/exportflow/exporttasks/port/IdempotencyRepository.java` | 新增 | 端口：持久唯一幂等登记 | [T-026](tasks.md#实施与测试任务)；TC-217 |
| `backend/src/main/java/com/exportflow/exporttasks/infrastructure/persistence/MyBatisExportTaskRepository.java` | 修改占位 | 适配：任务及关联范围 SQL 协调 | [T-026](tasks.md#实施与测试任务)、[T-028](tasks.md#实施与测试任务)；TC-218、TC-219、TC-222 |
| `backend/src/main/java/com/exportflow/exporttasks/infrastructure/persistence/MyBatisIdempotencyRepository.java` | 新增 | 适配：幂等SQL适配 | [T-026](tasks.md#实施与测试任务)；TC-217 |
| `backend/src/main/java/com/exportflow/exporttasks/infrastructure/persistence/ExportTaskMapper.java` | 修改占位 | SQL边界：方法与 XML 同步 | [T-026](tasks.md#实施与测试任务)、[T-028](tasks.md#实施与测试任务)；TC-217、TC-218、TC-219、TC-222 |
| `backend/src/main/java/com/exportflow/exporttasks/infrastructure/persistence/ExportTaskRecord.java` | 新增 | 记录类型：主表及JSON字段记录；无业务函数 | [T-026](tasks.md#实施与测试任务)；TC-218 |
| `backend/src/main/java/com/exportflow/exporttasks/infrastructure/persistence/ExportTaskPersistenceMapper.java` | 新增 | 映射：范围JSON版本化与领域记录隔离 | [T-026](tasks.md#实施与测试任务)；TC-218 |
| `backend/src/main/java/com/exportflow/dispatch/application/OutboxDispatchService.java` | 修改占位 | 应用：有界领取、发布与结果核对 | [T-027](tasks.md#实施与测试任务)；TC-220、TC-221 |
| `backend/src/main/java/com/exportflow/dispatch/application/OutboxTimeoutService.java` | 新增 | 应用：独立5秒到期扫描，不依赖MQ | [T-028](tasks.md#实施与测试任务)；TC-222 |
| `backend/src/main/java/com/exportflow/dispatch/application/DispatchScheduler.java` | 新增 | 调度装配：启动恢复及两个独立有界调度 | [T-027](tasks.md#实施与测试任务)、[T-028](tasks.md#实施与测试任务)；TC-221、TC-222 |
| `backend/src/main/java/com/exportflow/dispatch/domain/CommandOutbox.java` | 修改占位 | 领域：Outbox状态与当前领取凭据 | [T-027](tasks.md#实施与测试任务)；TC-221 |
| `backend/src/main/java/com/exportflow/dispatch/domain/DispatchModels.java` | 新增 | 领域类型：DispatchLease/PublishEvidence/PublishResult/DispatchOutcome/ClaimBatch/ScanResult/DeadlineScanResult/Attempt/Policy/Envelope 等见数据模型；无函数 | [T-027](tasks.md#实施与测试任务)；TC-220 |
| `backend/src/main/java/com/exportflow/dispatch/port/CommandOutboxRepository.java` | 修改占位 | 端口：封装跨任务与Outbox共同事务，防止拆事务 | [T-027](tasks.md#实施与测试任务)、[T-028](tasks.md#实施与测试任务)；TC-221、TC-222 |
| `backend/src/main/java/com/exportflow/dispatch/infrastructure/persistence/MyBatisCommandOutboxRepository.java` | 修改占位 | 适配：实现锁序、数据库时间、尝试记录与条件回写 | [T-027](tasks.md#实施与测试任务)、[T-028](tasks.md#实施与测试任务)；TC-221、TC-222 |
| `backend/src/main/java/com/exportflow/dispatch/infrastructure/persistence/CommandOutboxMapper.java` | 修改占位 | SQL边界：固定task先锁；SKIP LOCKED用于无竞争的短事务领取，禁止先锁outbox再反锁task | [T-027](tasks.md#实施与测试任务)、[T-028](tasks.md#实施与测试任务)；TC-221、TC-222 |
| `backend/src/main/java/com/exportflow/dispatch/infrastructure/persistence/CommandOutboxRecord.java` | 新增 | 记录类型：Outbox与嵌套DispatchAttemptRecord；无业务函数 | [T-027](tasks.md#实施与测试任务)；TC-221 |
| `backend/src/main/java/com/exportflow/dispatch/infrastructure/persistence/OutboxPersistenceMapper.java` | 新增 | 映射：数据库记录独立于消息和领域 | [T-027](tasks.md#实施与测试任务)；TC-221 |
| `backend/src/main/java/com/exportflow/dispatch/port/ExportCommandPublisher.java` | 修改占位 | 端口：可靠发布与目标拓扑核验 | [T-027](tasks.md#实施与测试任务)；TC-220 |
| `backend/src/main/java/com/exportflow/dispatch/infrastructure/messaging/RabbitMqExportCommandPublisher.java` | 修改占位 | 适配：不以send返回代替确认 | [T-027](tasks.md#实施与测试任务)；TC-220 |
| `backend/src/main/java/com/exportflow/dispatch/infrastructure/messaging/ExportCommandCodec.java` | 新增 | 消息边界：版本与最小字段校验 | [T-027](tasks.md#实施与测试任务)、[T-029](tasks.md#实施与测试任务)；TC-220、TC-223 |
| `backend/src/main/java/com/exportflow/worker/application/ExportCommandHandler.java` | 修改占位；架构决策已确认 | 应用：真实编排逻辑，真实生成及恢复编排 | [T-029](tasks.md#实施与测试任务)；TC-223、TC-224、TC-225 |
| `backend/src/main/java/com/exportflow/worker/application/TaskExecutionService.java` | 新增；架构决策已确认 | 应用：短事务执行权与权威状态更新 | [T-029](tasks.md#实施与测试任务)；TC-224 |
| `backend/src/main/java/com/exportflow/worker/port/TaskExecutionRepository.java` | 新增；架构决策已确认 | 端口：仅获批子集可实施 | [T-029](tasks.md#实施与测试任务)；TC-224 |
| `backend/src/main/java/com/exportflow/worker/infrastructure/persistence/MyBatisTaskExecutionRepository.java` | 新增；架构决策已确认 | 适配：执行身份与任务同事务保护 | [T-029](tasks.md#实施与测试任务)；TC-224 |
| `backend/src/main/java/com/exportflow/worker/infrastructure/persistence/TaskExecutionMapper.java` | 新增；架构决策已确认 | SQL边界：执行权条件更新，数值按定稿决策，需运行验证 | [T-029](tasks.md#实施与测试任务)；TC-224 |
| `backend/src/main/java/com/exportflow/worker/domain/ExecutionModels.java` | 新增；架构决策已确认 | 类型：ExecutionLease/Policy/ClaimResult/LeaseWriteResult/GenerationContext/GenerationResult/ArtifactProof/SafeFailure/ProgressUpdate/DeliveryContext/DeliveryDecision；无业务函数 | [T-029](tasks.md#实施与测试任务)；TC-224 |
| `backend/src/main/java/com/exportflow/worker/application/ExecutionSupervisor.java` | 新增；架构决策已确认 | 应用监督：认领监测、执行心跳、单次期限和受控停止；恢复发权由MySQL原命令接管 | [T-029](tasks.md#实施与测试任务)；TC-224 |
| `backend/src/main/java/com/exportflow/worker/infrastructure/messaging/RabbitMqExportCommandListener.java` | 修改占位；架构决策已确认 | 适配：演示角色启用真实消费，手动ACK，独立通道控制 | [T-029](tasks.md#实施与测试任务)；TC-223、TC-225 |
| `backend/src/main/java/com/exportflow/worker/port/ExportFileGenerator.java` | 修改占位 | 端口：真实生成与可信结果，失败/UNKNOWN分离 | [T-029](tasks.md#实施与测试任务)；TC-225 |
| `backend/src/main/java/com/exportflow/worker/infrastructure/excel/PoiExportFileGenerator.java` | 修改占位；真实SXSSF生成 | 适配：真实数据收集、SXSSF装配、产物校验与就绪登记 | [T-029](tasks.md#实施与测试任务)；TC-225 |
| `backend/src/main/java/com/exportflow/worker/port/ProgressSink.java` | 新增 | 端口：由应用传入生成器的进度回调 | [T-029](tasks.md#实施与测试任务)；TC-224 |
| `backend/src/main/java/com/exportflow/worker/port/ExecutionGuard.java` | 新增 | 端口：生成器批次开始前检查失权停止 | [T-029](tasks.md#实施与测试任务)；TC-224 |
| `frontend/package.json` | 修改 | 配置／资源／说明：增加已选型生产库和测试库；test/test:run脚本；精确版本见dependency-versions。测试准备仅加测试必需依赖，正式业务依赖待第二门禁；无自有业务函数 | [T-010](tasks.md#实施与测试任务)、[T-020](tasks.md#实施与测试任务)；TC-213、TC-226 |
| `frontend/pnpm-lock.yaml` | 修改 | 配置／资源／说明：安装生成版本锁；分阶段更新，冻结安装，不手写锁内容；无自有业务函数 | [T-010](tasks.md#实施与测试任务)、[T-020](tasks.md#实施与测试任务)；TC-213、TC-226 |
| `frontend/vite.config.ts` | 修改 | 配置／资源／说明：dev /api 代理目标由环境变量注入、仅本机；保留既有React配置；无自有业务函数 | [T-020](tasks.md#实施与测试任务)；TC-213、TC-226 |
| `frontend/vitest.config.ts` | 新增 | 配置／资源／说明：Vitest jsdom、Testing Library、MSW setup；不引入浏览器E2E；无自有业务函数 | [T-010](tasks.md#实施与测试任务)；TC-213、TC-226 |
| `frontend/tsconfig.app.json` | 修改 | 配置／资源／说明：测试类型与严格业务类型；范围是否包含测试按工具兼容表确认；无自有业务函数 | [T-010](tasks.md#实施与测试任务)、[T-020](tasks.md#实施与测试任务)；TC-213、TC-226 |
| `backend/pom.xml` | 修改占位 | 配置／资源／说明：正式Maven/Java21/Boot3.5.16、MyBatis/Flyway/AMQP/POI与Actuator；精确版本、Surefire/Failsafe、Spotless/Checkstyle/SpotBugs/JaCoCo配置；仅第二门禁后改为正式工程；无自有业务函数 | [T-024](tasks.md#实施与测试任务)；TC-213、TC-226 |
| `backend/mvnw` | 新增 | 配置／资源／说明：官方only-script Maven Wrapper Unix入口；生成版本/校验来源，不包含自有函数；无自有业务函数 | [T-024](tasks.md#实施与测试任务)；TC-213、TC-226 |
| `backend/mvnw.cmd` | 新增 | 配置／资源／说明：官方Maven Wrapper Windows入口；无自有函数；无自有业务函数 | [T-024](tasks.md#实施与测试任务)；TC-213、TC-226 |
| `backend/.mvn/wrapper/maven-wrapper.properties` | 新增 | 配置／资源／说明：Maven3.9.16发行包地址与校验；下载时核验官方摘要；无自有业务函数 | [T-024](tasks.md#实施与测试任务)；TC-213、TC-226 |
| `backend/src/main/resources/application.yml` | 修改占位 | 配置／资源／说明：环境变量注入DB/MQ，UTC/严格SQL，consumer关闭，独立dispatch/timeout线程与获批参数；不提交凭据；无自有业务函数 | [T-024](tasks.md#实施与测试任务)、[T-027](tasks.md#实施与测试任务)；TC-213、TC-226 |
| `backend/src/main/resources/db/migration/V1__create_orders.sql` | 新增 | 配置／资源／说明：InnoDB/utf8mb4/UTC/orders字段和索引，精确匹配排序规则；仅增量建表；无自有业务函数 | [T-024](tasks.md#实施与测试任务)；TC-213、TC-226 |
| `backend/src/main/resources/db/migration/V2__create_export_tasks_and_outbox.sql` | 新增 | 配置／资源／说明：任务/选择/幂等/Outbox/attempt表，FK/unique/check/调度索引；不含候选暂停断点；无自有业务函数 | [T-024](tasks.md#实施与测试任务)；TC-213、TC-226 |
| `backend/src/main/resources/db/migration/V3__create_task_execution.sql` | 新增；架构决策已确认 | 配置／资源：task_execution字段包括owner/fence/lease/attempt/recoveryCount/nextRecoveryAt/maxAttemptAt；架构已确认，实施仍受两次门禁，无函数 | [T-029](tasks.md#实施与测试任务)；TC-213、TC-226 |
| `backend/src/main/resources/db/migration/README.md` | 修改 | 配置／资源／说明：迁移空库、升级、只前进回退与引用规格说明；无自有业务函数 | [T-024](tasks.md#实施与测试任务)；TC-213、TC-226 |
| `backend/src/main/resources/mappers/OrderMapper.xml` | 新增 | 配置／资源／说明：count/findPage/countExisting、共享筛选片段，LIKE转义，稳定两列排序，值全参数化；无自有业务函数；新增readBatch/SELECTED JOIN/FILTERED共享片段/keyset游标 | [T-025](tasks.md#实施与测试任务)、[T-032](tasks.md#实施与测试任务)；TC-213、TC-215、TC-230、TC-235 |
| `backend/src/main/resources/mappers/ExportTaskMapper.xml` | 新增 | 配置／资源／说明：任务/关联/幂等/摘要/锁定/条件超时失败 SQL，statement与方法一一对应；无自有业务函数 | [T-026](tasks.md#实施与测试任务)、[T-028](tasks.md#实施与测试任务)；TC-213、TC-226 |
| `backend/src/main/resources/mappers/CommandOutboxMapper.xml` | 新增 | 配置／资源／说明：候选扫描/锁/DB时间/领取/attempt/标记/关闭，固定锁序；无函数，SQL见模型谓词；无自有业务函数 | [T-027](tasks.md#实施与测试任务)、[T-028](tasks.md#实施与测试任务)；TC-213、TC-226 |
| `backend/src/main/resources/mappers/TaskExecutionMapper.xml` | 新增；架构决策已确认 | 配置／资源／说明：owner/fence/lease/version 条件更新，不存在恢复扫描或永久锁；无自有业务函数 | [T-029](tasks.md#实施与测试任务)；TC-213、TC-226 |
| `scripts/seed-orders.ps1` | 新增 | 配置／资源／说明：固定种子、数量参数110000/200001、schema白名单、--help/非零退出；Java21源文件模式启动独立JDBC初始化入口，核对固定算法已有数据，不覆盖 | [T-025](tasks.md#实施与测试任务)；TC-213、TC-215 |
| `backend/tools/SeedOrders.java` | 新增（生产实施阶段） | 工具入口：Java21/JDBC PreparedStatement有界合成数据初始化；嵌套SeedOutcome/OrderSeedRow，非应用自动启动逻辑 | [T-025](tasks.md#实施与测试任务)；TC-215 |
| `scripts/check-order-performance.ps1` | 新增 | 配置／资源／说明：固定场景、预热样本、原始耗时/百分位、环境摘要；不连接生产或示例schema | [T-030](tasks.md#实施与测试任务)；TC-213、TC-226 |
| `tests/backend/pom.xml` | 新增（仅测试准备） | 配置／资源／说明：与正式工程分离的Maven测试harness；运行测试工具/SQL夹具，生产集成缺口如实登记；无自有业务函数 | [T-010](tasks.md#实施与测试任务)；TC-213、TC-226 |
| `tests/fixtures/orders.sql` | 新增（仅测试） | 配置／资源／说明：小规模合成边界数据；不替代正式迁移证据；无自有业务函数 | [T-010](tasks.md#实施与测试任务)；TC-213、TC-226 |
| `tests/fixtures/outbox.sql` | 新增（仅测试） | 配置／资源／说明：调度/到期/凭据/坏记录夹具；不复制生产实现；无自有业务函数 | [T-010](tasks.md#实施与测试任务)；TC-213、TC-226 |
| `tests/fixtures/export-requests.json` | 新增（仅测试） | 配置／资源／说明：规范化等价与不同有序字段的契约样本，不含真实个人信息；无自有业务函数 | [T-010](tasks.md#实施与测试任务)；TC-213、TC-226 |
| `README.md` | 修改 | 配置／资源／说明：实施后更新实际启动命令、规格链接和真实消费／Excel生成／下载边界；本次仅新增规格索引；无自有业务函数 | [T-031](tasks.md#实施与测试任务)；TC-213、TC-226 |
| `frontend/README.md` | 修改（实施后） | 配置／资源／说明：新增查询/导出回执说明、依赖版本、测试命令，不把静态规格改为新审批；无自有业务函数 | [T-031](tasks.md#实施与测试任务)；TC-213、TC-226 |
| `backend/README.md` | 修改（实施后） | 配置／资源／说明：可运行后端、角色开关、凭据/资源隔离、真实投递与真实Excel边界；无自有业务函数 | [T-031](tasks.md#实施与测试任务)；TC-213、TC-226 |
| `DATA-FLOW.md` | 修改（实施后） | 配置／资源／说明：同步实际阶段，学习图链接到正式规格；同步ADR实际接受子集与明确延期范围；无自有业务函数 | [T-031](tasks.md#实施与测试任务)；TC-213、TC-226 |
| `AGENTS.md` | 修改（实施后，仅地图） | 配置／资源／说明：后端可运行时同步项目地图；不修改开发原则或门禁；无自有业务函数 | [T-031](tasks.md#实施与测试任务)；TC-213、TC-226 |
| `frontend/src/test/setup.ts` | 新增（仅测试阶段） | 测试设计：注册Testing Library清理、MSW生命周期；before/after框架回调不承载业务逻辑；规格阶段只列场景，测试函数按TC条件/结果命名，不写可执行代码 | [T-010](tasks.md#实施与测试任务)；TC-201–TC-212 |
| `frontend/src/test/handlers.ts` | 新增（仅测试阶段） | 测试设计：MSW订单/创建/摘要的成功、拒绝、UNKNOWN、乱序与取消处理，测试专用，无生产替代；规格阶段只列场景，测试函数按TC条件/结果命名，不写可执行代码 | [T-011](tasks.md#实施与测试任务)；TC-201–TC-212 |
| `frontend/src/features/orders/__tests__/order-query.test.ts` | 新增（仅测试阶段） | 测试设计：范围、金额、UTC、URL安全、未知枚举及默认规范化；规格阶段只列场景，测试函数按TC条件/结果命名，不写可执行代码 | [T-011](tasks.md#实施与测试任务)；TC-204、TC-206 |
| `frontend/src/features/orders/__tests__/order-management.test.tsx` | 新增（仅测试阶段） | 测试设计：首次/组合/重置/分页/范围提示/取消乱序及错误恢复；规格阶段只列场景，测试函数按TC条件/结果命名，不写可执行代码 | [T-011](tasks.md#实施与测试任务)；TC-201、TC-202、TC-203、TC-205、TC-211 |
| `frontend/src/features/orders/__tests__/order-selection.test.ts` | 新增（仅测试阶段） | 测试设计：跨页保留、新筛选清空、全选/反选/清空；规格阶段只列场景，测试函数按TC条件/结果命名，不写可执行代码 | [T-011](tasks.md#实施与测试任务)；TC-207 |
| `frontend/src/features/orders/__tests__/order-export.test.tsx` | 新增（仅测试阶段） | 测试设计：字段最少项、disabled说明、两种范围、成功/明确拒绝/UNKNOWN同键找回；规格阶段只列场景，测试函数按TC条件/结果命名，不写可执行代码 | [T-012](tasks.md#实施与测试任务)；TC-208、TC-209、TC-210 |
| `frontend/src/features/export-tasks/__tests__/task-receipt.test.tsx` | 新增（仅测试阶段） | 测试设计：创建跳转与权威单任务回执；手动刷新一次，无定时器；规格阶段只列场景，测试函数按TC条件/结果命名，不写可执行代码 | [T-012](tasks.md#实施与测试任务)；TC-212 |
| `backend/src/test/java/com/exportflow/RuntimeConfigurationTest.java` | 新增（仅测试阶段） | 测试设计：入口配置、角色隔离、消费者能力与执行恢复启动检查；规格阶段只列场景，测试函数按TC条件/结果命名，不写可执行代码 | [T-013](tasks.md#实施与测试任务)；TC-213、TC-225 |
| `backend/src/test/java/com/exportflow/orders/OrderApiContractTest.java` | 新增（仅测试阶段） | 测试设计：MockMvc输入校验、DTO精度/UTC/枚举/错误体；规格阶段只列场景，测试函数按TC条件/结果命名，不写可执行代码 | [T-013](tasks.md#实施与测试任务)；TC-214 |
| `backend/src/test/java/com/exportflow/orders/OrderQueryMySqlTest.java` | 新增（仅测试阶段） | 测试设计：真实MySQL组合AND、keyword OR、精确订单号、边界与稳定排序；规格阶段只列场景，测试函数按TC条件/结果命名，不写可执行代码 | [T-013](tasks.md#实施与测试任务)；TC-215 |
| `backend/src/test/java/com/exportflow/exporttasks/ExportCreationContractTest.java` | 新增（仅测试阶段） | 测试设计：范围/字段/空/非法ID/超限拒绝，200000允许，独立后端校验；规格阶段只列场景，测试函数按TC条件/结果命名，不写可执行代码 | [T-014](tasks.md#实施与测试任务)；TC-216 |
| `backend/src/test/java/com/exportflow/exporttasks/IdempotencyMySqlTest.java` | 新增（仅测试阶段） | 测试设计：规范化、字段顺序、并发同键唯一约束、新事务回读；规格阶段只列场景，测试函数按TC条件/结果命名，不写可执行代码 | [T-014](tasks.md#实施与测试任务)；TC-217 |
| `backend/src/test/java/com/exportflow/exporttasks/CreateTransactionMySqlTest.java` | 新增（仅测试阶段） | 测试设计：原子回滚、提交不明回读、DB不可读UNKNOWN、响应前提交；规格阶段只列场景，测试函数按TC条件/结果命名，不写可执行代码 | [T-014](tasks.md#实施与测试任务)；TC-218 |
| `backend/src/test/java/com/exportflow/exporttasks/TaskSnapshotMySqlTest.java` | 新增（仅测试阶段） | 测试设计：创建后权威摘要、状态变化重放、404及无缓存事实依赖；规格阶段只列场景，测试函数按TC条件/结果命名，不写可执行代码 | [T-014](tasks.md#实施与测试任务)；TC-219 |
| `backend/src/test/java/com/exportflow/dispatch/RabbitMqPublisherTest.java` | 新增（仅测试阶段） | 测试设计：真实broker confirm/return/路由/policy/容量拒绝/重启保留；规格阶段只列场景，测试函数按TC条件/结果命名，不写可执行代码 | [T-015](tasks.md#实施与测试任务)；TC-220 |
| `backend/src/test/java/com/exportflow/dispatch/OutboxDispatchMySqlTest.java` | 新增（仅测试阶段） | 测试设计：真实MySQL/MQ断点、租约、持久退避、旧回调拒绝、单条隔离；规格阶段只列场景，测试函数按TC条件/结果命名，不写可执行代码 | [T-015](tasks.md#实施与测试任务)；TC-221 |
| `backend/src/test/java/com/exportflow/dispatch/OutboxTimeoutMySqlTest.java` | 新增（仅测试阶段） | 测试设计：十分钟/五秒启动周期、期限并发、SENT排除、任务/命令原子关闭；规格阶段只列场景，测试函数按TC条件/结果命名，不写可执行代码 | [T-015](tasks.md#实施与测试任务)；TC-222 |
| `backend/src/test/java/com/exportflow/worker/ExportCommandHandlerTest.java` | 新增（仅测试阶段） | 测试设计：最小消息、终态ACK、活跃重复、DB不可读与未知消息不确认；规格阶段只列场景，测试函数按TC条件/结果命名，不写可执行代码 | [T-016](tasks.md#实施与测试任务)；TC-223 |
| `backend/src/test/java/com/exportflow/worker/ExecutionLeaseMySqlTest.java` | 新增（仅测试阶段） | 测试设计：真实DB认领/心跳/旧fence/进度版本/提交不明/认领监测与RUNNING区别；规格阶段只列场景，测试函数按TC条件/结果命名，不写可执行代码 | [T-016](tasks.md#实施与测试任务)；TC-224 |
| `backend/src/test/java/com/exportflow/worker/GeneratorBoundaryTest.java` | 新增（仅测试阶段） | 测试设计：真实生成能力门禁、READY/明确失败/UNKNOWN编排与资源关闭；替身只补充失败窗口 | [T-016](tasks.md#实施与测试任务)；TC-225 |
| `docs/validation/002-order-management/environment.md` | 新增（执行后） | 证据：版本/命令/退出码/浏览器/隔离服务/性能环境；无函数；本次不存在 | [T-030](tasks.md#实施与测试任务)；TC-226–TC-229 |
| `docs/validation/002-order-management/red-results.md` | 新增（执行后） | 证据：保留对应红灯/绿灯/缺口/度量原始样本/页面截图；本次不预造证据，无函数 | [T-017](tasks.md#实施与测试任务)、[T-030](tasks.md#实施与测试任务)；TC-226–TC-229 |
| `docs/validation/002-order-management/green-results.md` | 新增（执行后） | 证据：保留对应红灯/绿灯/缺口/度量原始样本/页面截图；本次不预造证据，无函数 | [T-017](tasks.md#实施与测试任务)、[T-030](tasks.md#实施与测试任务)；TC-226–TC-229 |
| `docs/validation/002-order-management/coverage-gaps.md` | 新增（执行后） | 证据：保留对应红灯/绿灯/缺口/度量原始样本/页面截图；本次不预造证据，无函数 | [T-017](tasks.md#实施与测试任务)、[T-030](tasks.md#实施与测试任务)；TC-226–TC-229 |
| `docs/validation/002-order-management/performance.json` | 新增（执行后） | 证据：保留对应红灯/绿灯/缺口/度量原始样本/页面截图；本次不预造证据，无函数 | [T-017](tasks.md#实施与测试任务)、[T-030](tasks.md#实施与测试任务)；TC-226–TC-229 |
| `docs/validation/002-order-management/orders-1280x720.png` | 新增（执行后） | 证据：保留对应红灯/绿灯/缺口/度量原始样本/页面截图；本次不预造证据，无函数 | [T-017](tasks.md#实施与测试任务)、[T-030](tasks.md#实施与测试任务)；TC-226–TC-229 |
| `frontend/src/app/router.tsx` | 复用 | 既有模块：createAppRouter：复用固定/订单/任务/404路由，无签名变更 | [T-021](tasks.md#实施与测试任务)、[T-023](tasks.md#实施与测试任务)；TC-212、TC-226 |
| `frontend/src/layouts/admin-layout.tsx` | 复用 | 既有模块：AdminLayout：复用路径派生标题与侧栏 | [T-021](tasks.md#实施与测试任务)、[T-023](tasks.md#实施与测试任务)；TC-212、TC-226 |
| `frontend/src/layouts/sidebar-menu.tsx` | 复用 | 既有模块：SidebarMenu：复用菜单导航 | [T-021](tasks.md#实施与测试任务)、[T-023](tasks.md#实施与测试任务)；TC-212、TC-226 |
| `frontend/src/shared/navigation/navigation.ts` | 复用 | 既有模块：NAVIGATION_ITEMS：复用导航唯一元数据 | [T-021](tasks.md#实施与测试任务)、[T-023](tasks.md#实施与测试任务)；TC-212、TC-226 |
| `frontend/src/app/route-error-fallback.tsx` | 复用 | 既有模块：RouteErrorFallback：复用渲染失败恢复 | [T-021](tasks.md#实施与测试任务)、[T-023](tasks.md#实施与测试任务)；TC-212、TC-226 |
| `frontend/src/pages/not-found-page.tsx` | 复用 | 既有模块：NotFoundPage：复用404 | [T-021](tasks.md#实施与测试任务)、[T-023](tasks.md#实施与测试任务)；TC-212、TC-226 |
| `frontend/src/shared/styles/global.css` | 复用 | 既有模块：无函数：复用基础布局与设计令牌；局部通过Ant Design组件布局 | [T-021](tasks.md#实施与测试任务)、[T-023](tasks.md#实施与测试任务)；TC-212、TC-226 |
| `backend/src/main/java/com/exportflow/exporttasks/application/DownloadExportFileService.java` | 修改占位；本期实现 | 应用：短事务读权威成功引用，流式下载前释放事务 | [T-034](tasks.md#实施与测试任务)；TC-234 |
| `backend/src/main/java/com/exportflow/exporttasks/port/ExportFileStorage.java` | 修改占位；本期实现 | 端口：块/工作区/不可变产物/就绪/下载，文件操作不得携带DB长事务 | [T-033](tasks.md#实施与测试任务)；TC-230、TC-234、TC-235、TC-236 |
| `backend/src/main/java/com/exportflow/exporttasks/port/ExportProgressCache.java` | 后续；本次保留占位 | 仅边界说明：Redis缓存参数和通知契约后实施；无本期函数，不被运行链导入 | 后续规格；本期不验收 |
| `backend/src/main/java/com/exportflow/exporttasks/infrastructure/cache/RedisExportProgressCache.java` | 后续；本次保留占位 | 仅边界说明：Redis7.4兼容与TTL/版本后实施；无本期函数，不被运行链导入 | 后续规格；本期不验收 |
| `backend/src/main/java/com/exportflow/exporttasks/infrastructure/file/LocalExportFileStorage.java` | 修改占位；本期实现 | 适配：安全根目录、Windows路径/reparse保护、force/原子移动、数据库产物对账 | [T-033](tasks.md#实施与测试任务)；TC-230、TC-234、TC-235、TC-236 |
| `frontend/src/features/export-tasks/hooks/use-export-tasks.ts` | 后续；本次保留占位 | 仅边界说明：完整任务列表及SSE生命周期另规格；无本期函数，不被运行链导入 | 后续规格；本期不验收 |
| `frontend/src/features/export-tasks/components/export-task-table.tsx` | 后续；本次保留占位 | 仅边界说明：完整任务表格另规格；无本期函数，不被运行链导入 | 后续规格；本期不验收 |
| `backend/src/main/java/com/exportflow/worker/infrastructure/persistence/ExecutionLeaseRecord.java` | 新增；架构决策已确认 | 记录类型：执行权数据库字段见模型；与领域ExecutionLease独立，无业务函数 | [T-029](tasks.md#实施与测试任务)；TC-224 |
| `backend/src/main/java/com/exportflow/exporttasks/domain/ExportFileModels.java` | 新增 | 类型：StorageRef/AttemptWorkspace/StagingWorkbook/ValidatedWorkbook/DownloadHandle/DownloadMetadata 设计见模型；机械字段访问无函数 | [T-033](tasks.md#实施与测试任务)；TC-234 |
| `backend/src/main/java/com/exportflow/worker/port/CheckpointRepository.java` | 新增；架构决策已确认 | 端口：持久数据块与游标/进度版本的一致提交 | [T-032](tasks.md#实施与测试任务)；TC-232、TC-235 |
| `backend/src/main/java/com/exportflow/worker/infrastructure/persistence/MyBatisCheckpointRepository.java` | 新增；架构决策已确认 | 适配：task→execution→checkpoint→artifact锁序，DB时间及版本CAS；不持事务读写文件 | [T-032](tasks.md#实施与测试任务)；TC-232、TC-235 |
| `backend/src/main/java/com/exportflow/worker/infrastructure/persistence/CheckpointMapper.java` | 新增；架构决策已确认 | SQL边界：断点和块记录独立类型输出 | [T-032](tasks.md#实施与测试任务)；TC-235 |
| `backend/src/main/java/com/exportflow/worker/infrastructure/persistence/CheckpointRecord.java` | 新增 | 记录：CheckpointRecord与嵌套BlockRecord定义，字段见模型；产物记录由独立ArtifactRecord.java定义；无业务函数 | [T-032](tasks.md#实施与测试任务)；TC-235 |
| `backend/src/main/java/com/exportflow/worker/application/ExportDataCollectionService.java` | 新增；架构决策已确认 | 应用：稳定分批读取、块持久化、断点提交及安全续写 | [T-032](tasks.md#实施与测试任务)；TC-230、TC-232、TC-235 |
| `backend/src/main/java/com/exportflow/worker/infrastructure/file/DataBlockCodec.java` | 新增 | 格式适配：UTF8 JSON Lines v1，结构头与原始订单值，不与POI耦合 | [T-032](tasks.md#实施与测试任务)；TC-235 |
| `backend/src/main/java/com/exportflow/worker/infrastructure/excel/ExcelRowProjector.java` | 新增 | 映射：按字段顺序与页面中文/定点/香港时间一致，不用double业务金额 | [T-033](tasks.md#实施与测试任务)；TC-230、TC-233 |
| `backend/src/main/java/com/exportflow/worker/infrastructure/excel/ExcelWorkbookAssembler.java` | 新增 | POI适配：SXSSF窗口/inline字符串/固定样式，流式块输入、受控临时文件 | [T-033](tasks.md#实施与测试任务)；TC-230、TC-231、TC-233、TC-236 |
| `backend/src/main/java/com/exportflow/worker/infrastructure/excel/ExcelFileValidator.java` | 新增 | 校验：OPCPackage/XSSFReader/SAX有界验证，无全量XSSFWorkbook | [T-033](tasks.md#实施与测试任务)；TC-230、TC-231、TC-233、TC-236 |
| `backend/src/main/java/com/exportflow/worker/domain/CheckpointModels.java` | 新增 | 类型：OrderBatch、BlockHeader/Ref/Digest/Reader、CheckpointSnapshot/WriteResult、TrustedPrefix/CompletedDataset，定义见data-model；无业务函数（Reader行为在codec列出） | [T-032](tasks.md#实施与测试任务)；TC-235 |
| `backend/src/main/java/com/exportflow/exporttasks/port/ArtifactRepository.java` | 新增；架构决策已确认 | 端口：可信READY/ADOPTED产物与清理互斥 | [T-033](tasks.md#实施与测试任务)；TC-234、TC-235、TC-236 |
| `backend/src/main/java/com/exportflow/exporttasks/infrastructure/persistence/MyBatisArtifactRepository.java` | 新增；架构决策已确认 | 适配：产物就绪/采用/清理身份持久化，与任务事务同边界 | [T-033](tasks.md#实施与测试任务)；TC-235、TC-236 |
| `backend/src/main/java/com/exportflow/exporttasks/infrastructure/persistence/ArtifactMapper.java` | 新增；架构决策已确认 | SQL边界：独立ArtifactRecord，不用文件存在推断成功 | [T-033](tasks.md#实施与测试任务)；TC-234、TC-235、TC-236 |
| `backend/src/main/java/com/exportflow/exporttasks/infrastructure/persistence/ArtifactRecord.java` | 新增 | 记录：产物/孤儿清理候选持久字段；无业务函数，和ArtifactProof独立 | [T-033](tasks.md#实施与测试任务)；TC-236 |
| `backend/src/main/java/com/exportflow/worker/application/OrphanCleanupService.java` | 新增；架构决策已确认 | 应用：独立有界候选扫描和数据库授予清理权；绝不清成功/断点引用 | [T-033](tasks.md#实施与测试任务)；TC-236 |
| `backend/src/main/resources/db/migration/V4__create_export_checkpoints_and_artifacts.sql` | 新增；架构决策已确认 | 配置／资源：checkpoint/dataBlocks/artifacts/cleanupCandidates表、唯一sequence/config/ref、结果FK及执行恢复字段；无函数 | [T-032](tasks.md#实施与测试任务)；TC-235、TC-236 |
| `backend/src/main/resources/mappers/CheckpointMapper.xml` | 新增；架构决策已确认 | SQL：find/findBlocks/findBlock/insertBlock/updateCheckpoint/startGeneration、固定锁序/版本；无自有函数 | [T-032](tasks.md#实施与测试任务)；TC-235 |
| `backend/src/main/resources/mappers/ArtifactMapper.xml` | 新增；架构决策已确认 | SQL：findReady/findAdopted/find/insertReady/lockArtifact/adopt/registerCandidate/claimCleanup/completeCleanup；无函数 | [T-033](tasks.md#实施与测试任务)；TC-236 |
| `backend/src/test/java/com/exportflow/worker/ExcelContentTest.java` | 新增（规格获批后测试阶段） | 测试设计：真实.xlsx可打开、选列及顺序、精确金额/时间/中文/原值/未知、公式文本、边界；只列场景，不预写可执行测试 | [T-018](tasks.md#实施与测试任务)；TC-230、TC-233 |
| `backend/src/test/java/com/exportflow/worker/ExcelResourceTest.java` | 新增（规格获批后测试阶段） | 测试设计：独立512MB堆真实110000数据、内存采样、SXSSF窗口与资源关闭；只列场景，不预写可执行测试 | [T-018](tasks.md#实施与测试任务)；TC-231 |
| `backend/src/test/java/com/exportflow/worker/ExportProgressMySqlTest.java` | 新增（规格获批后测试阶段） | 测试设计：块登记与实际进度、非终态99、成功100、明确失败保留高水位；只列场景，不预写可执行测试 | [T-018](tasks.md#实施与测试任务)；TC-232 |
| `backend/src/test/java/com/exportflow/worker/DownloadExportFileTest.java` | 新增（规格获批后测试阶段） | 测试设计：真实成功文件流、状态/缺失/头/路径/reparse/大文件无Blob；只列场景，不预写可执行测试 | [T-018](tasks.md#实施与测试任务)；TC-234 |
| `backend/src/test/java/com/exportflow/worker/CheckpointRecoveryTest.java` | 新增（规格获批后测试阶段） | 测试设计：真实DB/broker/磁盘kill、块与READY提交不明、续写/安全重建/旧代次/预算耗尽；只列场景，不预写可执行测试 | [T-019](tasks.md#实施与测试任务)；TC-235 |
| `backend/src/test/java/com/exportflow/worker/ArtifactCleanupRaceTest.java` | 新增（规格获批后测试阶段） | 测试设计：真实文件关闭/发布失败、成功不明、采用清理互斥、永久引用不误删；只列场景，不预写可执行测试 | [T-019](tasks.md#实施与测试任务)；TC-236 |
| `docs/validation/002-order-management/excel-performance.json` | 新增（实际执行后） | 证据：110000/200000行9列真实生成耗时、资源、原始样本与硬件；本次无结果，无函数 | [T-030](tasks.md#实施与测试任务)；TC-230、TC-231 |
| `docs/validation/002-order-management/excel-content.md` | 新增（实际执行后） | 证据：文件哈希、逐行核对、下载与恢复窗口；不提交客户信息或大文件，无函数 | [T-030](tasks.md#实施与测试任务)；TC-233、TC-234、TC-235、TC-236 |

## 逐函数签名、注释与调用

### frontend/src/app/app-providers.tsx

阶段：修改；[T-020](tasks.md#实施与测试任务)；TC-201、TC-211。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `AppProviders(props: {children: ReactNode}): ReactElement` | 统一中文主题与单例查询缓存。 | 复用稳定 QueryClient；嵌套现有 ConfigProvider | TanStack QueryClientProvider、ConfigProvider | 渲染错误交既有 RouteErrorFallback |

### frontend/src/app/query-client.ts

阶段：新增；[T-020](tasks.md#实施与测试任务)；TC-211。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `createQueryClient(): QueryClient（无参数）` | 创建可测试且不会自动重复提交的查询客户端。 | 配置读请求受控重试、禁用后台定时刷新和 mutation 重试 | TanStack QueryClient | 配置错误启动失败 |

### frontend/src/features/orders/pages/order-management-page.tsx

阶段：修改占位；[T-021](tasks.md#实施与测试任务)；TC-201、TC-202、TC-203、TC-205。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `OrderManagementPage(): ReactElement（无参数）` | 组合订单查询、选择和导出视图。 | 读取 Hooks→渲染筛选、操作、表格、分页、弹窗→显示错误 | useOrders、useOrderSelection、useOrderExport、OrderFilterForm、OrderTable、ExportConfigDialog | 请求错误在区域展示 |
| `handleReset(): void（无参数）` | 显式协调查询、选择和配置的重置。 | 可编辑态才执行；重置查询、CLEAR选择并丢弃未提交导出草稿；SUBMITTING/UNKNOWN禁用此动作 | useOrders.reset、useOrderSelection.dispatch、useOrderExport.resetDraft | 未知提交必须先核对，不能破坏原选择 |

### frontend/src/features/orders/components/order-filter-form.tsx

阶段：修改占位；[T-021](tasks.md#实施与测试任务)；TC-202、TC-204、TC-206。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `OrderFilterForm(props: OrderFilterFormProps): ReactElement` | 提供有标签、可键盘操作的筛选输入。 | 挂载编辑草稿→resolver 校验→提交调用 onQuery；重置调用 onReset | orderFilterSchema、React Hook Form、Ant Design | 字段错误关联控件，不提交无效输入 |

### frontend/src/features/orders/components/order-table.tsx

阶段：修改占位；[T-021](tasks.md#实施与测试任务)；TC-203、TC-207。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `OrderTable(props: OrderTableProps): ReactElement` | 展示服务端当前页并保留跨页 ID 选择。 | rowKey=id→受控选择→排序回调；不缓存整页对象 | Ant Design Table、props.onToggle、props.onSort | 未知枚举用模型回退；不触发隐式请求 |

### frontend/src/features/orders/components/export-config-dialog.tsx

阶段：修改占位；[T-022](tasks.md#实施与测试任务)；TC-208、TC-209。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `ExportConfigDialog(props: ExportConfigDialogProps): ReactElement` | 让用户确认范围、字段与提交状态。 | 初始化焦点→禁用最后一项取消→确认／同键核对→关闭恢复焦点 | Ant Design Modal、props.onFieldsChange/onConfirm/onRetry/onClose | 未知结果阶段禁止破坏冻结提交 |

### frontend/src/features/orders/hooks/use-orders.ts

阶段：修改占位；[T-021](tasks.md#实施与测试任务)；TC-201、TC-202、TC-211。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `useOrders(): UseOrdersResult（无参数）` | 协调已提交条件、分页和查询生命周期。 | 读取 URL 安全查询→TanStack useQuery→映射状态→暴露动作 | useOrderQueryState、queryOrders、toOrderViewModel | 取消不展示旧错误；普通请求错误可重试 |
| `submitFilter(values: OrderFilterFormValues): void` | 只提交通过验证的筛选。 | normalizeOrderFilter→page=1→setQuery；通知选择生命周期 | normalizeOrderFilter、setQuery | 验证错误保持旧结果 |
| `reset(): void（无参数）` | 恢复无筛选第一页和默认页大小。 | 仅重置 query 与 keyword 并请求；选择和导出草稿由页面显式协调 | setQuery | 无 |
| `changePage(page: number, pageSize: PageSize): void` | 同步页码与大小。 | 检查正整数；大小变化置 page=1；setQuery | setQuery | 非法值拒绝 |
| `changeSort(direction: SortDirection): void` | 切换 time/ID 同方向排序。 | 规范化方向→page=1→保留同筛选选择→setQuery | setQuery | 未知方向拒绝 |
| `refresh(): Promise<void>（无参数）` | 手动更新当前订单结果。 | 调用 query.refetch，保留当前条件／页码 | TanStack Query.refetch | 显示归一化查询错误 |

### frontend/src/features/orders/hooks/use-order-query-state.ts

阶段：新增；[T-021](tasks.md#实施与测试任务)；TC-206、TC-211。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `useOrderQueryState(): UseOrderQueryStateResult（无参数）` | 维护可恢复且不泄漏客户输入的查询状态。 | 初始化 parse→规范化 replace→监听后退→清 keyword 与选择 | parseOrderQuery、serializeOrderQuery、React Router useSearchParams | 非法 URL 显示恢复提示 |
| `setQuery(query: OrderQuery, mode: 'push'或'replace' = 'push'): void` | 提交安全 URL 与内存关键字。 | 拆 keyword→内存保存→其余参数序列化→导航 | serializeOrderQuery、setSearchParams | 无效 query 不提交 |

### frontend/src/features/orders/hooks/use-order-selection.ts

阶段：新增；[T-021](tasks.md#实施与测试任务)；TC-207。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `useOrderSelection(filterKey: string): UseOrderSelectionResult` | 按已提交筛选管理选择生命周期。 | useReducer→filterKey 改变 CLEAR→返回只读集合与 dispatch | reduceOrderSelection | 无 |

### frontend/src/features/orders/hooks/use-order-export.ts

阶段：新增；[T-022](tasks.md#实施与测试任务)；TC-208、TC-209、TC-210。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `useOrderExport(deps: OrderExportDependencies): UseOrderExportResult` | 协调配置、单次提交与未知结果找回。 | 内存保存 dialog/submission→绑定动作→卸载释放请求 | createSubmission、submitOrderExport、retryOrderExport | ApiRequestError 分类 |
| `open(scope: ExportScope): void` | 冻结导出来源范围。 | 校验来源已加载→新配置默认字段；失败草稿按契约保留 | 默认字段常量 | 空范围拒绝 |
| `setFields(fields: readonly ExportField[]): void` | 维护合法有序字段。 | 非提交态校验 1–9 唯一→更新草稿 | 字段白名单 | 至少一个字段 |
| `close(): void（无参数）` | 取消可编辑配置并恢复焦点。 | 仅可编辑态关闭；UNKNOWN 保留冻结意图 | 无 | 提交中／未知拒绝破坏性关闭 |
| `resetDraft(): void（无参数）` | 支持用户明确重置时清除未提交配置。 | 仅可编辑态恢复默认字段、关闭配置；不删除未知提交 | 无 | SUBMITTING/UNKNOWN拒绝 |
| `confirm(): Promise<void>（无参数）` | 固定键和请求后提交一次。 | createSubmission→SUBMITTING→submitOrderExport→成功导航或保留输入 | createSubmission、submitOrderExport | 未知结果不清状态 |
| `retryUnknown(): Promise<void>（无参数）` | 使用原键原内容找回结果。 | 要求 UNKNOWN→retryOrderExport；禁止换键 | retryOrderExport | 无冻结提交拒绝 |

### frontend/src/features/orders/application/order-export-use-case.ts

阶段：修改占位；[T-022](tasks.md#实施与测试任务)；TC-209、TC-210。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `createSubmission(scope: ExportScope, fields: readonly ExportField[], newKey: () => string): FrozenExportSubmission` | 创建深度不可变的单次提交意图。 | 校验范围／字段→复制快照→生成键→SUBMITTING | newKey | 校验失败返回安全错误 |
| `submitOrderExport(submission: FrozenExportSubmission, deps: OrderExportDependencies, signal?: AbortSignal): Promise<FrozenExportSubmission>` | 仅确认成功后清空选择并跳转。 | 调用 createTask→CONFIRMED→clearSelection→navigate；失败按 outcome 分类 | deps.createTask/clearSelection/navigate | UNKNOWN 与 REJECTED 均保留输入 |
| `retryOrderExport(submission: FrozenExportSubmission, deps: OrderExportDependencies, signal?: AbortSignal): Promise<FrozenExportSubmission>` | 恢复同一次提交，不能重复创建意图。 | 要求 UNKNOWN→复用 request/key→submitOrderExport | submitOrderExport | 不允许修改载荷或键 |

### frontend/src/features/orders/api/orders-api.ts

阶段：修改占位；[T-021](tasks.md#实施与测试任务)；TC-204、TC-211。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `queryOrders(query: OrderQuery, signal?: AbortSignal): Promise<PageResult<OrderDto>>` | 使用同源订单查询契约。 | 受控参数编码→GET→运行时响应 schema 校验 | requestJson、orderPageSchema | 网络、协议或服务端 ApiRequestError |

### frontend/src/features/orders/models/order-view-model.ts

阶段：修改占位；[T-021](tasks.md#实施与测试任务)；TC-204。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `toOrderViewModel(dto: OrderDto): OrderViewModel` | 集中页面展示与未知枚举回退。 | 映射 id→key→状态渠道中文→金额／时区格式化 | formatMoney、formatHongKongDateTime | 协议非法数据不静默展示 |

### frontend/src/features/orders/models/order-query.ts

阶段：新增；[T-021](tasks.md#实施与测试任务)；TC-204、TC-206。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `normalizeOrderFilter(values: OrderFilterFormValues): OrderFilter` | 将编辑值变为规范化查询条件。 | trim／空项删除→校验范围→UTC 转换→定点格式 | orderFilterSchema、toUtcInstant、normalizeMoney | 字段错误 |
| `parseOrderQuery(params: URLSearchParams): OrderQuery` | 安全恢复浏览器非敏感查询。 | 白名单解析→非法项默认→keyword 不读取→默认页／排序 | orderFilterSchema | 错误项恢复提示由 Hook 提供 |
| `serializeOrderQuery(query: OrderQuery): URLSearchParams` | 只序列化可公开的已提交条件。 | 排除 keyword→固定字段→写非默认分页／排序 | 无 | 非法值不序列化 |

### frontend/src/features/orders/models/order-selection.ts

阶段：新增；[T-021](tasks.md#实施与测试任务)；TC-207。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `reduceOrderSelection(state: OrderSelectionState, action: SelectionAction): OrderSelectionState` | 只更新指定页 ID，不丢失其他页选择。 | 复制集合→按 action 添加／移除／反选／清空 | Set API | 非法 ID 拒绝 |

### frontend/src/features/export-tasks/api/export-tasks-api.ts

阶段：修改占位；[T-022](tasks.md#实施与测试任务)、[T-034](tasks.md#实施与测试任务)；TC-210、TC-234。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `createExportTask(request: CreateExportRequest, idempotencyKey: string, signal?: AbortSignal): Promise<TaskSummary>` | 提交固定幂等意图。 | POST JSON+header→校验摘要→成功返回 | requestJson、taskSummarySchema | 所有不明创建错误归 UNKNOWN |
| `getExportTask(taskId: string, signal?: AbortSignal): Promise<TaskSummary>` | 读取 MySQL 权威单任务摘要。 | 编码 UUID 路径→GET→校验摘要 | requestJson、taskSummarySchema | 404／依赖／协议错误 |
| `getExportDownloadUrl(taskId: string): string` | 构造安全同源直接下载链接。 | UUID校验→固定/api/v1/export-tasks/{id}/file；不请求Blob | 无 | 非法UUID拒绝 |

### frontend/src/features/export-tasks/pages/task-management-page.tsx

阶段：修改；[T-023](tasks.md#实施与测试任务)、[T-034](tasks.md#实施与测试任务)；TC-212、TC-234。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `TaskManagementPage(): ReactElement（无参数）` | 承接创建成功后的真实受理回执。 | 读取taskId→一次权威查询→展示状态/实际进度/安全错误/成功元数据→手动刷新；成功渲染同源下载anchor，其余状态可见禁用理由 | getExportTask、TanStack useQuery、formatHongKongDateTime、getExportDownloadUrl | 非法 ID／404 在区域展示，不发重复请求 |

### frontend/src/shared/api/http-client.ts

阶段：修改占位；[T-020](tasks.md#实施与测试任务)；TC-211。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `requestJson<T>(path: string, options?: RequestOptions): Promise<T>` | 集中同源 JSON、超时、取消与安全错误。 | 限定 /api/v1 相对路径→合并取消超时→fetch→解析→finally 释放 | normalizeApiError、fetch、AbortController | 抛 ApiRequestError；创建取消不暗示回滚 |

### frontend/src/shared/api/api-error.ts

阶段：修改占位；[T-020](tasks.md#实施与测试任务)；TC-211。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `normalizeApiError(error: unknown, status?: number): ApiRequestError` | 区分明确拒绝与创建结果不明。 | 仅可信 outcome=REJECTED 明确拒绝→其余网络／超时／未知归 UNKNOWN | 无 | 不透出响应原文／堆栈 |

### frontend/src/shared/formatters/money.ts

阶段：修改占位；[T-020](tasks.md#实施与测试任务)；TC-204。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `normalizeMoney(input: string): MoneyDecimal` | 用字符串规范化非负定点金额。 | 校验整数位／小数位→去前导零→补两位 | 无 | 非法金额拒绝 |
| `formatMoney(amount: MoneyDecimal, currency: 'CNY' = 'CNY'): string` | 避免浮点转换并显示币种。 | 规范化→字符串分组→拼接币种 | normalizeMoney | 非法协议金额拒绝 |

### frontend/src/shared/formatters/date-time.ts

阶段：修改占位；[T-020](tasks.md#实施与测试任务)；TC-204。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `toUtcInstant(local: string): UtcInstant` | 将香港秒级表单值转换为 UTC。 | 严格验证日历日期→按固定 +08:00 构造→转 Z | 标准 Date API（不依赖系统时区） | 非法／不存在日期拒绝 |
| `formatHongKongDateTime(utc: UtcInstant): string` | 统一人类时间展示口径。 | 严格解析 Z→Intl 指定 Asia/Hong_Kong→yyyy-MM-dd HH:mm:ss | Intl.DateTimeFormat | 非法协议日期拒绝 |

### backend/src/main/java/com/exportflow/ExportFlowApplication.java

阶段：修改占位；[T-024](tasks.md#实施与测试任务)；TC-213。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `main(args: String[]): void` | 启动获批范围的后端服务。 | Spring 启动→配置校验→注册 API／独立扫描；消费者受开关约束 | SpringApplication.run、RuntimeConfiguration | 必需配置非法启动失败 |

### backend/src/main/java/com/exportflow/config/RuntimeConfiguration.java

阶段：修改占位；[T-024](tasks.md#实施与测试任务)；TC-213、TC-214、TC-225。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `validateRuntime(properties: RuntimeProperties, generator: ExportFileGenerator): void` | 在启用真实消费者前核验生成、恢复与存储能力。 | 校验隔离配置、已批准参数、存储可读写/同卷原子移动→真实generator→消费者和独立清理调度 | ExportFileGenerator.isAvailable | 非法配置／不可用消费者启动拒绝 |
| `dispatchScheduler(service: OutboxDispatchService, timeout: OutboxTimeoutService, policy: DispatchPolicy): DispatchScheduler` | 装配独立投递与到期扫描。 | 分离线程资源／配置周期；返回调度器 | DispatchScheduler | 不得让 MQ 阻塞到期扫描 |
| `jacksonConstraints(properties: RuntimeProperties): Jackson2ObjectMapperBuilderCustomizer` | 限制输入JSON长度并在事务前拒绝超大请求。 | 配置UTF8输入流StreamReadConstraints.maxDocumentLength及FAIL_ON_TRAILING_TOKENS，完整检查尾部后才进入Controller | Spring Boot Jackson customizer、Jackson JsonFactory | 实际超限异常由handleValidation映射413，合法200000 IDs可接受 |

### backend/src/main/java/com/exportflow/shared/api/ApiExceptionHandler.java

阶段：修改占位；[T-024](tasks.md#实施与测试任务)；TC-214。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `handleValidation(ex: Exception, traceId: String): ResponseEntity<ApiErrorResponse>` | 稳定映射事务前输入错误。 | JSON文档长度超限→413 REQUEST_TOO_LARGE/REJECTED；其余白名单字段错误→400 REJECTED | ApiErrorResponse、Jackson StreamConstraintsException | 不暴露异常原文；只在明确未进入业务事务时宣告REJECTED |
| `handleBusiness(ex: BusinessException, traceId: String): ResponseEntity<ApiErrorResponse>` | 按稳定代码映射明确业务拒绝。 | 区分409／422／404→安全响应 | BusinessException | 安全 message |
| `handleUnexpected(ex: Exception, traceId: String): ResponseEntity<ApiErrorResponse>` | 保守处理依赖与提交不明。 | 读取可信业务outcome；提交结果不明统一UNKNOWN；不能证明拒绝的意外错误保持UNKNOWN | 安全日志分类 | 500/503，禁止 SQL/堆栈外泄 |

### backend/src/main/java/com/exportflow/orders/api/OrderController.java

阶段：修改占位；[T-025](tasks.md#实施与测试任务)；TC-214、TC-215。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `getOrders(query: OrderQueryDto): PageResult<OrderResponse>` | 接收和校验订单查询协议。 | DTO 校验→toQuery→service.query→toResponse | OrderApiMapper.toQuery/toResponse、OrderQueryService.query | 400／503 |

### backend/src/main/java/com/exportflow/orders/api/OrderApiMapper.java

阶段：新增；[T-025](tasks.md#实施与测试任务)；TC-214。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `toQuery(dto: OrderQueryDto): OrderQuery` | 隔离 HTTP 类型与业务查询。 | 白名单→UTC／BigDecimal→领域校验 | OrderQuery.validate | 输入非法 |
| `toResponse(order: Order): OrderResponse` | 按协议输出安全订单字段。 | ID字符串→金额两位→UTC Z；保留未知输出枚举原值 | 无 | 映射异常为协议内部错误 |

### backend/src/main/java/com/exportflow/orders/application/OrderQueryService.java

阶段：修改占位；[T-025](tasks.md#实施与测试任务)；TC-215。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `query(query: OrderQuery): PageSlice<Order>` | 统一列表查询语义。 | validate→短只读事务 count 和 page→稳定结果 | OrderQuery.validate、OrderRepository.count/findPage | 依赖不可用，绝不回假数据 |

### backend/src/main/java/com/exportflow/orders/domain/OrderQuery.java

阶段：新增；[T-025](tasks.md#实施与测试任务)；TC-214、TC-215。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `validate(): void（无参数）` | 重复保护筛选和分页不变量。 | 检查枚举／边界／精度／页大小／默认排序 | 无 | BusinessException |

### backend/src/main/java/com/exportflow/orders/port/OrderRepository.java

阶段：修改占位；[T-025](tasks.md#实施与测试任务)、[T-032](tasks.md#实施与测试任务)；TC-215、TC-230、TC-235。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `count(filter: OrderFilter): long` | 计算真实筛选总数。 | 相同 AND／keyword OR 条件；闭区间 | 持久化参数化 count | 依赖错误 |
| `findPage(query: OrderQuery): List<Order>` | 读取稳定的当前页。 | 排序白名单→offset 上限→limit | 持久化参数化 select | 依赖错误 |
| `countExisting(orderIds: List<Long>): long` | 拒绝包含不存在 ID 的选择。 | 已去重 ID 分批计数，同事务累加 | 持久化 IN 分批查询 | 依赖错误 |
| `readBatch(taskId: UUID, scope: ExportScope, after: OrderCursor?, limit: int = 1000): OrderBatch` | 按任务冻结范围有界稳定读下一批。 | SELECTED连接task关联或FILTERED共享条件→time/id DESC keyset→最多limit行→末游标 | 参数化稳定批量查询 | limit需正数并符合定稿批量上限；DB故障UNKNOWN |

### backend/src/main/java/com/exportflow/orders/infrastructure/persistence/MyBatisOrderRepository.java

阶段：修改占位；[T-025](tasks.md#实施与测试任务)、[T-032](tasks.md#实施与测试任务)；TC-215、TC-230、TC-235。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `count(filter: OrderFilter): long` | 计算真实筛选总数。 | 相同 AND／keyword OR 条件；闭区间→数据库记录映射领域对象 | OrderMapper.count、OrderPersistenceMapper.toDomain | 依赖错误 |
| `findPage(query: OrderQuery): List<Order>` | 读取稳定的当前页。 | 排序白名单→offset 上限→limit→数据库记录映射领域对象 | OrderMapper.findPage、OrderPersistenceMapper.toDomain | 依赖错误 |
| `countExisting(orderIds: List<Long>): long` | 拒绝包含不存在 ID 的选择。 | 已去重 ID 分批计数，同事务累加→数据库记录映射领域对象 | OrderMapper.countExisting、OrderPersistenceMapper.toDomain | 依赖错误 |
| `readBatch(taskId: UUID, scope: ExportScope, after: OrderCursor?, limit: int = 1000): OrderBatch` | 按任务冻结范围有界稳定读下一批。 | SELECTED连接task关联或FILTERED共享条件→time/id DESC keyset→最多limit行→末游标 | OrderMapper.readBatch、OrderPersistenceMapper.toDomain | limit需正数并符合定稿批量上限；DB故障UNKNOWN |

### backend/src/main/java/com/exportflow/orders/infrastructure/persistence/OrderMapper.java

阶段：修改占位；[T-025](tasks.md#实施与测试任务)、[T-032](tasks.md#实施与测试任务)；TC-215、TC-230、TC-235。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `count(filter: OrderFilter): long` | 查询计数 SQL 入口。 | 绑定条件→执行 count | OrderMapper.xml count | SQL异常 |
| `findPage(query: OrderQuery): List<OrderRecord>` | 查询分页 SQL 入口。 | 绑定条件→白名单排序→select | OrderMapper.xml findPage | SQL异常 |
| `countExisting(orderIds: List<Long>): long` | 验证已选 ID 存在数量。 | 有界 IN→count | OrderMapper.xml countExisting | SQL异常 |
| `readBatch(taskId: UUID, scope: ExportScope, after: OrderCursor?, limit: int): List<OrderRecord>` | 批读SQL入口。 | keyset不使用offset；SELECTED JOIN关联表 | OrderMapper.xml readBatch | SQL错误 |

### backend/src/main/java/com/exportflow/orders/infrastructure/persistence/OrderPersistenceMapper.java

阶段：新增；[T-025](tasks.md#实施与测试任务)；TC-215。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `toDomain(record: OrderRecord): Order` | 转换订单持久值。 | BigDecimal／UTC／ID 校验→不可变领域模型 | 无 | 非法库数据不伪造默认 |

### backend/src/main/java/com/exportflow/exporttasks/api/ExportTaskController.java

阶段：修改占位；[T-026](tasks.md#实施与测试任务)；TC-216、TC-219。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `create(request: CreateTaskRequestDto, idempotencyKey: String): ResponseEntity<TaskSummaryResponse>` | 受理异步申请，不等 MQ 或 Excel。 | 校验DTO/header→应用服务→提交确认后201或重放200 | ExportTaskApiMapper.toCommand/toResponse、CreateExportTaskService.create | 400/409/422/503/500 |
| `get(taskId: UUID): TaskSummaryResponse` | 提供可核对的权威受理回执。 | UUID校验→queryService.get→映射 | ExportTaskQueryService.get、ExportTaskApiMapper.toResponse | 404／503 |
| `download(taskId: UUID): ResponseEntity<StreamingResponseBody>` | 以同源文件流提供成功产物。 | 调用prepare→生成安全响应头→流式写响应；不持DB事务 | DownloadExportFileService.prepare、DownloadHandle.writeTo | 409/404/410/503/安全500 |

### backend/src/main/java/com/exportflow/exporttasks/api/ExportTaskApiMapper.java

阶段：新增；[T-026](tasks.md#实施与测试任务)；TC-216。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `toCommand(dto: CreateTaskRequestDto, key: String): CreateExportCommand` | 转换请求并排除客户端状态。 | 校验判别联合→BigDecimal／Instant→命令 | ExportScope.validate、ExportField.validateFields | 结构／字段错误 |
| `toResponse(summary: TaskSummary): TaskSummaryResponse` | 输出用户需要的当前摘要。 | 隐藏范围敏感内容／租约／物理引用→协议格式 | 无 | 不泄漏内部信息 |

### backend/src/main/java/com/exportflow/exporttasks/application/CreateExportTaskService.java

阶段：修改占位；[T-026](tasks.md#实施与测试任务)；TC-216、TC-217、TC-218。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `create(command: CreateExportCommand): CreateTaskResult` | 协调幂等、事务与提交不明回读。 | normalize→findByKeyHash→同请求返回；新请求交事务→唯一冲突或提交异常新事务回读 | ExportRequestCanonicalizer.normalize、IdempotencyRepository.findByKeyHash、CreateExportTransaction.commit、reconcile | 不能证明提交则 UNKNOWN |
| `reconcile(submission: CanonicalSubmission): CreateTaskResult` | 仅基于已提交记录找回结果。 | 独立连接读取→摘要一致性→读取当前任务；读不到不武断判回滚 | IdempotencyRepository.findByKeyHash、ExportTaskRepository.findSummary | 不明／同键冲突 |

### backend/src/main/java/com/exportflow/exporttasks/application/CreateExportTransaction.java

阶段：新增；[T-026](tasks.md#实施与测试任务)；TC-217、TC-218。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `commit(submission: CanonicalSubmission): CreateTaskResult` | 原子保存任务、范围、幂等和 Outbox。 | TransactionTemplate.execute内重核幂等→verify range→DB时间→newTaskIdentity→写记录；execute返回后才返回确认结果 | ExportRangeService.verify、ExportTaskRepository.insert、IdempotencyRepository.insert、CommandOutboxRepository.insert、newTaskIdentity、CommandOutboxMapper.databaseNow、TransactionTemplate.execute | 唯一冲突整体回滚；提交异常交外层核对 |
| `newTaskIdentity(acceptedAt: Instant): TaskIdentity` | 建立稳定任务、事件及人类编号。 | 生成task UUID/event UUID→按香港日期与完整UUID组成taskNo→唯一约束最终裁决 | JDK UUID、显式Asia/Hong_Kong | 极少数编号冲突在明确回滚后有界重新分配，不更换用户键 |

### backend/src/main/java/com/exportflow/exporttasks/application/ExportRangeService.java

阶段：新增；[T-026](tasks.md#实施与测试任务)；TC-216。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `verify(scope: ExportScope): ExportRangeCheck` | 用权威数据拒绝空范围、非法选择及超限。 | validate→SELECTED去重存在性／FILTERED count→检查0与200000边界 | ExportScope.validate、OrderRepository.count/countExisting | 空／不存在／超限拒绝 |

### backend/src/main/java/com/exportflow/exporttasks/application/ExportRequestCanonicalizer.java

阶段：新增；[T-026](tasks.md#实施与测试任务)；TC-217。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `normalize(command: CreateExportCommand): CanonicalSubmission` | 为语义等价请求建立稳定摘要。 | 模型定义的逐项规则→固定顺序UTF8→SHA-256 request/key | ExportScope.validate、ExportField.validateFields、JDK MessageDigest | 不同有序字段不得合并 |

### backend/src/main/java/com/exportflow/exporttasks/application/ExportTaskQueryService.java

阶段：修改占位；[T-026](tasks.md#实施与测试任务)；TC-219。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `get(taskId: UUID): TaskSummary` | 查询可见任务摘要。 | repository.findSummary→不存在404 | ExportTaskRepository.findSummary | 404／依赖不可用 |

### backend/src/main/java/com/exportflow/exporttasks/domain/ExportTask.java

阶段：修改占位；[T-026](tasks.md#实施与测试任务)、[T-028](tasks.md#实施与测试任务)、[T-029](tasks.md#实施与测试任务)；TC-218、TC-222、TC-224。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `pending(taskId: UUID, taskNo: String, scope: ExportScope, fields: List<ExportField>, total: long, acceptedAt: Instant): ExportTask` | 建立可持久化的等待任务。 | 校验1..200000→状态PENDING／计数0→不可变对象 | ExportScope.validate、ExportField.validateFields | 不变量错误 |
| `validateProgress(processed: long): void` | 保护单调有界进度。 | 检查当前状态和已处理下限、total上限 | 无 | 非法进度拒绝 |
| `validateTerminal(target: TaskStatus, artifact: ArtifactProof?, failure: SafeFailure?): void` | 防止无文件成功和终态覆盖。 | 检查RUNNING及成功证明／明确失败数据；UNKNOWN不终止 | 无 | 非法终态拒绝 |

### backend/src/main/java/com/exportflow/exporttasks/domain/ExportScope.java

阶段：新增；[T-026](tasks.md#实施与测试任务)；TC-216。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `validate(): void（无参数）` | 保护互斥范围及条件约束。 | 按scopeType检查ID／filter，拒绝额外范围字段 | OrderQuery.validate | BusinessException |

### backend/src/main/java/com/exportflow/exporttasks/domain/ExportField.java

阶段：新增；[T-026](tasks.md#实施与测试任务)；TC-216。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `validateFields(fields: List<ExportField>): void` | 保证字段非空且有序唯一。 | 检查1..9、未知／重复拒绝，不重排 | 无 | INVALID_EXPORT_FIELDS |

### backend/src/main/java/com/exportflow/exporttasks/port/ExportTaskRepository.java

阶段：修改占位；[T-026](tasks.md#实施与测试任务)、[T-028](tasks.md#实施与测试任务)；TC-218、TC-219、TC-222。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `insert(task: ExportTask, scope: ExportScope, fields: List<ExportField>): void` | 创建任务及固定范围关联。 | 同一创建事务插入任务和SELECTED关联 | 持久化 task与关联表 | SQL／唯一约束异常 |
| `findSummary(taskId: UUID): Optional<TaskSummary>` | 读取当前已提交任务摘要。 | 只读查询和领域映射 | 持久化 select summary | 依赖错误 |
| `findSnapshot(taskId: UUID): Optional<TaskSnapshot>` | 取得执行所需权威范围与字段。 | 读任务及关联范围，不能从消息反推配置 | 持久化 task/selection 查询 | 依赖错误 |
| `lockTask(taskId: UUID): ExportTask` | 统一争抢锁序的第一步。 | 当前事务按PK FOR UPDATE；之后才能锁outbox | 持久化锁定读 | 不存在／锁超时 |
| `failDispatchTimeout(taskId: UUID, endedAt: Instant): boolean` | 到期扫描条件结束未开工任务。 | 在共享裁决事务中PENDING条件写FAILED、实际进度、结束时间 | 条件 UPDATE | 条件失效返回false，SQL错误抛出 |

### backend/src/main/java/com/exportflow/exporttasks/port/IdempotencyRepository.java

阶段：新增；[T-026](tasks.md#实施与测试任务)；TC-217。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `findByKeyHash(keyHash: byte[]): Optional<IdempotencyRecord>` | 找回已提交意图。 | 按唯一键读版本／摘要／taskId | 持久化 select | 依赖错误 |
| `insert(submission: CanonicalSubmission, taskId: UUID): void` | 用唯一约束裁决并发同键。 | 当前创建事务插入登记 | 持久化 insert | 唯一冲突交外层回滚后核对 |

### backend/src/main/java/com/exportflow/exporttasks/infrastructure/persistence/MyBatisExportTaskRepository.java

阶段：修改占位；[T-026](tasks.md#实施与测试任务)、[T-028](tasks.md#实施与测试任务)；TC-218、TC-219、TC-222。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `insert(task: ExportTask, scope: ExportScope, fields: List<ExportField>): void` | 创建任务及固定范围关联。 | 同一创建事务插入任务和SELECTED关联→记录映射 | ExportTaskMapper.insert、ExportTaskPersistenceMapper | SQL／唯一约束异常 |
| `findSummary(taskId: UUID): Optional<TaskSummary>` | 读取当前已提交任务摘要。 | 只读查询和领域映射→记录映射 | ExportTaskMapper.findSummary、ExportTaskPersistenceMapper | 依赖错误 |
| `findSnapshot(taskId: UUID): Optional<TaskSnapshot>` | 取得执行所需权威范围与字段。 | 读任务及关联范围，不能从消息反推配置→记录映射 | ExportTaskMapper.findSnapshot、ExportTaskPersistenceMapper | 依赖错误 |
| `lockTask(taskId: UUID): ExportTask` | 统一争抢锁序的第一步。 | 当前事务按PK FOR UPDATE；之后才能锁outbox→记录映射 | ExportTaskMapper.lockTask、ExportTaskPersistenceMapper | 不存在／锁超时 |
| `failDispatchTimeout(taskId: UUID, endedAt: Instant): boolean` | 到期扫描条件结束未开工任务。 | 在共享裁决事务中PENDING条件写FAILED、实际进度、结束时间→记录映射 | ExportTaskMapper.failDispatchTimeout、ExportTaskPersistenceMapper | 条件失效返回false，SQL错误抛出 |

### backend/src/main/java/com/exportflow/exporttasks/infrastructure/persistence/MyBatisIdempotencyRepository.java

阶段：新增；[T-026](tasks.md#实施与测试任务)；TC-217。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `findByKeyHash(keyHash: byte[]): Optional<IdempotencyRecord>` | 恢复持久意图。 | mapper读→独立领域记录 | ExportTaskMapper.findIdempotency | SQL异常 |
| `insert(submission: CanonicalSubmission, taskId: UUID): void` | 加入当前创建事务。 | 参数记录映射→mapper写 | ExportTaskMapper.insertIdempotency | 唯一冲突透传安全分类 |

### backend/src/main/java/com/exportflow/exporttasks/infrastructure/persistence/ExportTaskMapper.java

阶段：修改占位；[T-026](tasks.md#实施与测试任务)、[T-028](tasks.md#实施与测试任务)；TC-217、TC-218、TC-219、TC-222。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `insert(task: ExportTaskRecord): int` | 插入任务主表。 | 参数化insert | ExportTaskMapper.xml insert | SQL异常 |
| `insertSelected(taskId: UUID, orderIds: List<Long>): int` | 批量固定已选订单。 | 有界批次插入关联，仍在同事务 | ExportTaskMapper.xml insertSelected | SQL异常 |
| `findSummary(taskId: UUID): Optional<ExportTaskRecord>` | 读取权威摘要记录。 | select主表 | ExportTaskMapper.xml findSummary | SQL异常 |
| `findSnapshot(taskId: UUID): Optional<ExportTaskRecord>` | 读取任务固定配置。 | select主表范围JSON | ExportTaskMapper.xml findSnapshot | SQL异常 |
| `findSelected(taskId: UUID): List<Long>` | 恢复SELECTED范围。 | 关联表按orderId稳定读取 | ExportTaskMapper.xml findSelected | SQL异常 |
| `lockTask(taskId: UUID): ExportTaskRecord` | 取得共同裁决主锁。 | SELECT FOR UPDATE | ExportTaskMapper.xml lockTask | SQL异常 |
| `failDispatchTimeout(taskId: UUID, endedAt: Instant): int` | 只更新仍PENDING的到期任务。 | 条件UPDATE；其余谓词在共享锁内确认 | ExportTaskMapper.xml failDispatchTimeout | SQL异常 |
| `findIdempotency(keyHash: byte[]): Optional<IdempotencyDbRecord>` | 读取独立的幂等数据库记录。 | SELECT唯一键；由适配器映射为领域IdempotencyRecord | ExportTaskMapper.xml findIdempotency | SQL异常 |
| `insertIdempotency(submission: CanonicalSubmission, taskId: UUID): int` | 幂等原子写入。 | INSERT唯一键 | ExportTaskMapper.xml insertIdempotency | 唯一冲突 |

### backend/src/main/java/com/exportflow/exporttasks/infrastructure/persistence/ExportTaskPersistenceMapper.java

阶段：新增；[T-026](tasks.md#实施与测试任务)；TC-218。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `toRecord(task: ExportTask, scope: ExportScope, fields: List<ExportField>): ExportTaskRecord` | 保存固定且可回读的配置。 | 按版本序列化filter/fields→记录 | 获批JSON库 | 非法领域数据拒绝 |
| `toSummary(record: ExportTaskRecord): TaskSummary` | 只输出安全摘要。 | 转换UTC／定点／状态与实际百分比 | 无 | 坏记录不伪造成功 |
| `toSnapshot(record: ExportTaskRecord, orderIds: List<Long>): TaskSnapshot` | 恢复执行固定范围。 | 版本化反序列化→SELECTED/FILTERED校验 | ExportScope.validate、ExportField.validateFields | 不支持版本拒绝 |

### backend/src/main/java/com/exportflow/dispatch/application/OutboxDispatchService.java

阶段：修改占位；[T-027](tasks.md#实施与测试任务)；TC-220、TC-221。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `scanAvailable(): DispatchScanResult（无参数）` | 推动已提交且到期的原命令。 | 先检查容量→claimDue→逐条dispatchOne；坏记录隔离、连接故障停批 | CommandOutboxRepository.claimDue、dispatchOne | 不得回滚其他已成功记录 |
| `dispatchOne(lease: DispatchLease): DispatchOutcome` | 只在有效投递权下发布。 | 查权利和策略→publish→confirm+route→markSent；不明先回读 | ExportCommandPublisher.verifyTopology/publish、CommandOutboxRepository.isPublishAllowed/markSent/find、reconcile | 单条失败scheduleRetry；共享依赖故障安全停止 |
| `reconcile(lease: DispatchLease, evidence: PublishEvidence?): DispatchOutcome` | 发布或回写不明时先核对。 | 读已提交SENT/CLOSED；有可信证据且有效lease优先重试mark；否则保留 | CommandOutboxRepository.find/markSent/scheduleRetry | DB不可读UNKNOWN |

### backend/src/main/java/com/exportflow/dispatch/application/OutboxTimeoutService.java

阶段：新增；[T-028](tasks.md#实施与测试任务)；TC-222。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `scanExpired(): DeadlineScanResult（无参数）` | 自动关闭到期且尚未开工的原命令。 | 扫描候选→逐task事务expire→处理已开工无需补发诊断 | CommandOutboxRepository.findExpired/expireOrClose | 独立于MQ线程；DB不可用恢复优先扫描 |

### backend/src/main/java/com/exportflow/dispatch/application/DispatchScheduler.java

阶段：新增；[T-027](tasks.md#实施与测试任务)、[T-028](tasks.md#实施与测试任务)；TC-221、TC-222。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `start(): void（无参数）` | 恢复时先处理到期记录。 | timeout.scanExpired→启动独立周期；发布循环无重入 | OutboxTimeoutService.scanExpired、OutboxDispatchService.scanAvailable | 启动异常安全告警 |
| `stop(grace: Duration): void` | 停止新领取并有界收尾。 | 停新扫描→等待在途预算→无法确认的lease保留 | 调度Executor | 不提前伪造SENT／释放不明lease |

### backend/src/main/java/com/exportflow/dispatch/domain/CommandOutbox.java

阶段：修改占位；[T-027](tasks.md#实施与测试任务)；TC-221。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `canClaim(now: Instant): boolean` | 集中调度谓词。 | 检查READY/过期LEASED、deadline、nextAttempt、crashRetryAt | 无 | 无 |
| `backoffDelay(attemptNo: int, policy: DispatchPolicy, random: DoubleSupplier): Duration` | 计算可控的有上限指数退避与全抖动。 | 检查attemptNo为正→有界指数基数→注入随机分布→返回Duration；实际调度由DB当前时间加delay | JDK DoubleSupplier，不读取系统墙上时间 | 参数非法拒绝；领取时同时保存crashRetryAt |
| `accepts(token: UUID, now: Instant): boolean` | 拒绝旧凭据与过期回写。 | 匹配LEASED、token、leaseUntil、未关闭 | 无 | 无 |

### backend/src/main/java/com/exportflow/dispatch/port/CommandOutboxRepository.java

阶段：修改占位；[T-027](tasks.md#实施与测试任务)、[T-028](tasks.md#实施与测试任务)；TC-221、TC-222。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `insert(taskId: UUID, eventId: UUID, acceptedAt: Instant): void` | 同创建事务写原始待投递命令。 | deadline=DB受理+10分钟→READY→唯一task/type | OutboxPersistenceMapper.toRecord、CommandOutboxMapper.insert | SQL／唯一冲突 |
| `claimDue(owner: String, capacity: int, policy: DispatchPolicy): ClaimBatch` | 有界并发领取并持久预留尝试。 | 无锁候选→每条task/outbox锁内DB时间核对→新token/attempt/crashRetryAt→短事务提交 | ExportTaskRepository.lockTask、CommandOutboxMapper.findDue/lockByTask/databaseNow/reserveAttempt/insertAttempt、CommandOutbox.backoffDelay | 过期／竞争跳过；提交不明不发布 |
| `isPublishAllowed(lease: DispatchLease): boolean` | 发布前权威检查当前资格。 | 核对凭据／期限／task状态，不持锁发MQ | CommandOutboxMapper.find | 无法核对返回依赖错误，不发布 |
| `markSent(lease: DispatchLease, evidence: PublishEvidence): boolean` | 同裁决边界记录可靠发送。 | task→outbox锁→DB时间→证据/lease/deadline/终态→条件更新 | CommandOutboxMapper.markSent/finishAttempt、ExportTaskRepository.lockTask | 失权返回false，未知提交回读 |
| `scheduleRetry(lease: DispatchLease, safeCode: String, delay: Duration): boolean` | 持久保存单条失败和下次调度。 | 有效token且lease未过期→在事务内取DB时间加delay→记录尝试结果→READY/退避 | CommandOutboxMapper.databaseNow/scheduleRetry/finishAttempt | 不能覆盖SENT/CLOSED |
| `find(eventId: UUID): Optional<CommandOutbox>` | 回读已提交发送结果。 | 按event主键查询映射 | CommandOutboxMapper.find | DB错误 |
| `findExpired(limit: int): List<UUID>` | 给独立扫描提供到期task候选。 | 只读索引扫描，不受lease/退避排除 | CommandOutboxMapper.findExpired | DB错误 |
| `expireOrClose(taskId: UUID): DispatchOutcome` | 与认领及SENT串行裁决超时。 | task→outbox锁→取DB时间→到期PENDING未SENT原子失败+关闭；已开工/终态关闭无需补发；SENT跳过 | ExportTaskRepository.lockTask/failDispatchTimeout、CommandOutboxMapper.lockByTask/close | 事务不明保留并回读，不返回假成功 |

### backend/src/main/java/com/exportflow/dispatch/infrastructure/persistence/MyBatisCommandOutboxRepository.java

阶段：修改占位；[T-027](tasks.md#实施与测试任务)、[T-028](tasks.md#实施与测试任务)；TC-221、TC-222。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `insert(taskId: UUID, eventId: UUID, acceptedAt: Instant): void` | 同创建事务写原始待投递命令。 | deadline=DB受理+10分钟→READY→唯一task/type；加入创建事务，不开启独立提交 | OutboxPersistenceMapper.toRecord、CommandOutboxMapper.insert | SQL／唯一冲突 |
| `claimDue(owner: String, capacity: int, policy: DispatchPolicy): ClaimBatch` | 有界并发领取并持久预留尝试。 | 无锁候选→每条task/outbox锁内DB时间核对→新token/attempt/crashRetryAt→短事务提交；显式事务边界由此适配器实现 | ExportTaskRepository.lockTask、CommandOutboxMapper.findDue/lockByTask/databaseNow/reserveAttempt/insertAttempt、CommandOutbox.backoffDelay、JDK随机源注入（仅退避抖动） | 过期／竞争跳过；提交不明不发布 |
| `isPublishAllowed(lease: DispatchLease): boolean` | 发布前权威检查当前资格。 | 核对凭据／期限／task状态，不持锁发MQ；显式事务边界由此适配器实现 | CommandOutboxMapper.find、JDK随机源注入（仅退避抖动） | 无法核对返回依赖错误，不发布 |
| `markSent(lease: DispatchLease, evidence: PublishEvidence): boolean` | 同裁决边界记录可靠发送。 | task→outbox锁→DB时间→证据/lease/deadline/终态→条件更新；显式事务边界由此适配器实现 | CommandOutboxMapper.markSent/finishAttempt、ExportTaskRepository.lockTask、JDK随机源注入（仅退避抖动） | 失权返回false，未知提交回读 |
| `scheduleRetry(lease: DispatchLease, safeCode: String, delay: Duration): boolean` | 持久保存单条失败和下次调度。 | 有效token且lease未过期→在事务内取DB时间加delay→记录尝试结果→READY/退避；显式事务边界由此适配器实现 | CommandOutboxMapper.databaseNow/scheduleRetry/finishAttempt、JDK随机源注入（仅退避抖动） | 不能覆盖SENT/CLOSED |
| `find(eventId: UUID): Optional<CommandOutbox>` | 回读已提交发送结果。 | 按event主键查询映射；显式事务边界由此适配器实现 | CommandOutboxMapper.find、JDK随机源注入（仅退避抖动） | DB错误 |
| `findExpired(limit: int): List<UUID>` | 给独立扫描提供到期task候选。 | 只读索引扫描，不受lease/退避排除；显式事务边界由此适配器实现 | CommandOutboxMapper.findExpired、JDK随机源注入（仅退避抖动） | DB错误 |
| `expireOrClose(taskId: UUID): DispatchOutcome` | 与认领及SENT串行裁决超时。 | task→outbox锁→取DB时间→到期PENDING未SENT原子失败+关闭；已开工/终态关闭无需补发；SENT跳过；显式事务边界由此适配器实现 | ExportTaskRepository.lockTask/failDispatchTimeout、CommandOutboxMapper.lockByTask/close、JDK随机源注入（仅退避抖动） | 事务不明保留并回读，不返回假成功 |

### backend/src/main/java/com/exportflow/dispatch/infrastructure/persistence/CommandOutboxMapper.java

阶段：修改占位；[T-027](tasks.md#实施与测试任务)、[T-028](tasks.md#实施与测试任务)；TC-221、TC-222。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `insert(record: CommandOutboxRecord): int` | 插入创建命令。 | INSERT参数化 | CommandOutboxMapper.xml insert | SQL错误 |
| `findDue(limit: int): List<UUID>` | 稳定取得候选taskId。 | next_attempt_at、event_id排序无锁查询；候选不授权 | CommandOutboxMapper.xml findDue | SQL错误 |
| `lockByTask(taskId: UUID): CommandOutboxRecord` | 在task锁之后锁定原命令。 | SELECT FOR UPDATE；SKIP LOCKED选择须验证 | CommandOutboxMapper.xml lockByTask | SQL错误 |
| `databaseNow(): Instant` | 同一权威时间做期限裁决。 | 锁获得后读取UTC_TIMESTAMP(0) | CommandOutboxMapper.xml databaseNow | SQL错误 |
| `reserveAttempt(lease: DispatchLease, crashRetryAt: Instant): int` | 原子存领取与崩溃退避依据。 | 当前锁序下只更新outbox的lease/attempt/nextAttempt；调用方另调insertAttempt，同一事务 | CommandOutboxMapper.xml reserveAttempt/insertAttempt | SQL错误 |
| `markSent(lease: DispatchLease, now: Instant): int` | 按当前凭据标记发送。 | 只更新outbox；适配器另调finishAttempt同事务 | CommandOutboxMapper.xml markSent | SQL错误 |
| `scheduleRetry(lease: DispatchLease, safeCode: String, nextAt: Instant): int` | 保存失败退避与释放当前领取。 | 只更新outbox；适配器另调finishAttempt同事务 | CommandOutboxMapper.xml scheduleRetry | SQL错误 |
| `find(eventId: UUID): Optional<CommandOutboxRecord>` | 核对已提交权威状态。 | 按主键SELECT | CommandOutboxMapper.xml find | SQL错误 |
| `findExpired(limit: int): List<UUID>` | 扫描到期未发送候选。 | deadline索引，包含有效lease及晚退避记录 | CommandOutboxMapper.xml findExpired | SQL错误 |
| `close(eventId: UUID, reason: String, now: Instant): int` | 原子撤销投递权并保留原因。 | 当前锁内CLOSED、清lease；适配器另调finishAttempt同事务 | CommandOutboxMapper.xml close | SQL错误 |
| `insertAttempt(record: DispatchAttemptRecord): int` | 领取事务即保存尝试历史。 | INSERT(event,attempt,token,start,crashRetryAt)，与reserve同事务 | CommandOutboxMapper.xml insertAttempt | SQL错误 |
| `finishAttempt(eventId: UUID, attemptNo: int, token: UUID, outcome: String, safeCode: String?, now: Instant): int` | 保留单次发送或关闭的诊断。 | token条件更新，旧回调不得改新尝试 | CommandOutboxMapper.xml finishAttempt | SQL错误 |

### backend/src/main/java/com/exportflow/dispatch/infrastructure/persistence/OutboxPersistenceMapper.java

阶段：新增；[T-027](tasks.md#实施与测试任务)；TC-221。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `toDomain(record: CommandOutboxRecord): CommandOutbox` | 恢复持久调度依据。 | 转换状态／UTC／尝试／token，校验不变量 | 无 | 坏记录分类保留 |
| `toRecord(taskId: UUID, eventId: UUID, acceptedAt: Instant): CommandOutboxRecord` | 建立原命令记录。 | 设置版本／READY／deadline，不序列化客户数据 | 无 | 非法时间拒绝 |

### backend/src/main/java/com/exportflow/dispatch/port/ExportCommandPublisher.java

阶段：修改占位；[T-027](tasks.md#实施与测试任务)；TC-220。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `verifyTopology(): boolean（无参数）` | 核对正确目标及有效保留策略。 | 检查durable／绑定／TTL／溢出／有效policy | RabbitMQ管理／声明适配 | 无法证明返回false并告警 |
| `publish(envelope: ExportCommandEnvelope, lease: DispatchLease, budget: Duration): PublishResult` | 有界发布并汇总确认与路由。 | 序列化→persistent/mandatory→correlation→confirm/return→结果 | RabbitTemplate | 超时UNKNOWN，单条和连接故障分类 |

### backend/src/main/java/com/exportflow/dispatch/infrastructure/messaging/RabbitMqExportCommandPublisher.java

阶段：修改占位；[T-027](tasks.md#实施与测试任务)；TC-220。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `verifyTopology(): boolean（无参数）` | 形成正确目标与保留策略证明。 | 取有效policy与唯一绑定→核验全部约束 | 获批RabbitMQ管理HTTP适配 | 失败不能SENT |
| `publish(envelope: ExportCommandEnvelope, lease: DispatchLease, budget: Duration): PublishResult` | 发布持久命令并关联本次结果。 | 编码→RabbitTemplate correlatedConfirm+return→预算内归并 | ExportCommandCodec.encode、RabbitTemplate | 版本／序列化错误单条；连接错误共享 |

### backend/src/main/java/com/exportflow/dispatch/infrastructure/messaging/ExportCommandCodec.java

阶段：新增；[T-027](tasks.md#实施与测试任务)、[T-029](tasks.md#实施与测试任务)；TC-220、TC-223。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `encode(envelope: ExportCommandEnvelope): byte[]` | 生成不含业务敏感数据的载荷。 | 版本与字段校验→JSON UTF8 | 获批JSON库 | 单条序列化安全错误 |
| `decode(body: byte[]): ExportCommandEnvelope` | 拒绝畸形和未知版本命令。 | 大小上限→JSON→UUID/type/version校验 | 获批JSON库 | 未知／畸形停止消费槽/受控关通道并告警，保留原消息不ACK |

### backend/src/main/java/com/exportflow/worker/application/ExportCommandHandler.java

阶段：修改占位；架构决策已确认；[T-029](tasks.md#实施与测试任务)；TC-223、TC-224、TC-225。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `handle(command: ExportCommandEnvelope, delivery: DeliveryContext): DeliveryDecision` | 控制执行与消费确认先后。 | 权威终态/能力检查→首次认领或到期有界接管→提交确认→优先READY/断点核对→generate→终态提交→ACK；活跃lease重复不新执行 | ExportTaskRepository.findSnapshot、ExportFileGenerator.isAvailable/generate、TaskExecutionService.claim/complete/reconcile、ExecutionSupervisor.heartbeat | 未知／失权不ACK；活跃重复不生成 |
| `handleGenerationResult(result: GenerationResult, lease: ExecutionLease): DeliveryDecision` | 终态确认后才允许ACK。 | READY证明验证→complete；明确业务失败→FAILED；UNKNOWN保留命令并回读；旧执行权停止 | TaskExecutionService.complete/reconcile | 未知或未提交不ACK |

### backend/src/main/java/com/exportflow/worker/application/TaskExecutionService.java

阶段：新增；架构决策已确认；[T-029](tasks.md#实施与测试任务)；TC-224。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `claim(taskId: UUID, owner: String): ClaimResult` | 提交PENDING到RUNNING及执行权。 | 共同锁序→DB时间→首次认领或过期且退避到期接管→预算/fence/owner裁决→确认后返回；耗尽事务FAILED | TaskExecutionRepository.claim、ExportTaskRepository.lockTask | 提交不明返回UNKNOWN |
| `renew(lease: ExecutionLease): LeaseWriteResult` | 只给有效owner/fence续租。 | DB时间、当前身份、未到期、尝试上限检查→更新 | TaskExecutionRepository.renew | 失权或未知停止 |
| `report(lease: ExecutionLease, update: ProgressUpdate): LeaseWriteResult` | 持久化单调且版本正确的实际进度。 | 仅核对已同事务登记断点的计数与版本；不得推进未落盘行；无独立高于断点的进度写 | CheckpointRepository.find、TaskExecutionRepository.report | 旧版本拒绝；数据进度由CheckpointRepository.commitBlock原子写入 |
| `complete(lease: ExecutionLease, result: GenerationResult): LeaseWriteResult` | 在有效执行权下提交可信终态。 | 禁止UNKNOWN→有效owner/fence/lease→READY产物与引用/校验合规→任务成功/产物采用同事务；明确失败保留高水位 | ExportTask.validateTerminal、TaskExecutionRepository.complete、ExportFileStorage.validate | 不明先核对，不删除候选文件 |
| `reconcile(taskId: UUID, owner: String): ClaimResult` | 回读认领或终态提交结果。 | 新连接查权威任务／lease→已终态／当前权利／未知 | TaskExecutionRepository.find | DB不可读UNKNOWN |

### backend/src/main/java/com/exportflow/worker/port/TaskExecutionRepository.java

阶段：新增；架构决策已确认；[T-029](tasks.md#实施与测试任务)；TC-224。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `claim(taskId: UUID, owner: String, policy: ExecutionPolicy): ClaimResult` | 事务裁决并建立执行权。 | 共同锁序→首次PENDING或过期RUNNING→nextRecoveryAt与恢复预算→新owner/fence；耗尽原子失败；确认提交 | TaskExecutionMapper.markRunning/insertLease及任务/Outbox锁 | BUSY/TERMINAL/UNKNOWN |
| `renew(lease: ExecutionLease): LeaseWriteResult` | 拒绝过期和旧fence的心跳。 | 条件更新owner/fence/lease/尝试期限 | TaskExecutionMapper.renew | 失权或UNKNOWN |
| `report(lease: ExecutionLease, update: ProgressUpdate): LeaseWriteResult` | 限制单调版本进度写入。 | 读取已登记检查点后条件核对，禁止独立推进业务进度 | TaskExecutionMapper.advanceProgressVersion/updateProgress | 失权或UNKNOWN |
| `complete(lease: ExecutionLease, result: GenerationResult): LeaseWriteResult` | 提交唯一终态与结果引用。 | 当前执行权下任务+可信产物采用同事务→成功/明确失败，结果不明回读 | TaskExecutionMapper.completeTask/releaseLease | 失权或UNKNOWN |
| `find(taskId: UUID): ClaimResult` | 读取权威状态和当前执行身份。 | 只读task及lease映射 | TaskExecutionMapper.find | UNKNOWN |

### backend/src/main/java/com/exportflow/worker/infrastructure/persistence/MyBatisTaskExecutionRepository.java

阶段：新增；架构决策已确认；[T-029](tasks.md#实施与测试任务)；TC-224。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `claim(taskId: UUID, owner: String, policy: ExecutionPolicy): ClaimResult` | 事务裁决并建立执行权。 | 固定task→outbox→execution锁序；核对首次/过期lease、退避及恢复预算→条件接管或耗尽终止→确认提交 | TaskExecutionMapper.markRunning/insertLease/takeOver/failRecoveryExhausted、ExportTaskRepository.lockTask、CommandOutboxMapper.lockByTask/databaseNow | BUSY/TERMINAL/UNKNOWN |
| `renew(lease: ExecutionLease): LeaseWriteResult` | 拒绝过期和旧fence的心跳。 | 条件更新owner/fence/lease/尝试期限；固定task→outbox→execution锁序 | TaskExecutionMapper.renew、ExportTaskRepository.lockTask、CommandOutboxMapper.lockByTask/databaseNow | 失权或UNKNOWN |
| `report(lease: ExecutionLease, update: ProgressUpdate): LeaseWriteResult` | 限制单调版本进度写入。 | 读取checkpoint和任务实际进度，仅核对已提交版本，不独立UPDATE进度 | CheckpointMapper.find、ExportTaskRepository.findSnapshot | 失权或UNKNOWN |
| `complete(lease: ExecutionLease, result: GenerationResult): LeaseWriteResult` | 提交唯一终态与结果引用。 | 核对权利→合法终态及artifact条件→提交；固定task→outbox→execution锁序 | TaskExecutionMapper.completeTask/releaseLease、ArtifactMapper.lockArtifact/adopt、ExportTaskRepository.lockTask、CommandOutboxMapper.databaseNow | 失权或UNKNOWN |
| `find(taskId: UUID): ClaimResult` | 读取权威状态和当前执行身份。 | 只读task及lease映射，不申请执行权 | TaskExecutionMapper.find、ExportTaskRepository.findSnapshot | UNKNOWN |

### backend/src/main/java/com/exportflow/worker/infrastructure/persistence/TaskExecutionMapper.java

阶段：新增；架构决策已确认；[T-029](tasks.md#实施与测试任务)；TC-224。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `insertLease(record: ExecutionLeaseRecord): int` | 原子建立执行身份。 | 与markRunning同事务插入owner/fence/到期值 | TaskExecutionMapper.xml insertLease | SQL/唯一冲突 |
| `markRunning(taskId: UUID, expectedTaskVersion: long): int` | 在共同锁内条件开工。 | 只更新PENDING及任务版本 | TaskExecutionMapper.xml markRunning | SQL错误 |
| `renew(lease: ExecutionLease, newUntil: Instant): int` | 拒绝失权续租。 | DB条件owner/fence/lease有效 | TaskExecutionMapper.xml renew | SQL错误 |
| `advanceProgressVersion(lease: ExecutionLease, expectedVersion: long): int` | 串行化同一尝试进度。 | 条件lease与旧version→递增 | TaskExecutionMapper.xml advanceProgressVersion | SQL错误 |
| `updateProgress(taskId: UUID, processed: long, expectedTaskVersion: long): int` | 与执行版本同事务推进实际条数。 | RUNNING、单调、未超total、版本条件UPDATE | TaskExecutionMapper.xml updateProgress | SQL错误 |
| `completeTask(taskId: UUID, result: GenerationResult, expectedTaskVersion: long): int` | 提交合法终态及唯一产物引用。 | 共同事务内执行权已核对；条件RUNNING/version | TaskExecutionMapper.xml completeTask | SQL错误 |
| `releaseLease(lease: ExecutionLease): int` | 已安全完成后撤销当前执行权。 | 条件owner/fence，不抢删新身份 | TaskExecutionMapper.xml releaseLease | SQL错误 |
| `find(taskId: UUID): Optional<ExecutionLeaseRecord>` | 读取执行持久记录。 | 只读当前task_execution | TaskExecutionMapper.xml find | SQL错误 |
| `takeOver(taskId: UUID, newOwner: String, expectedFence: long, now: Instant, policy: ExecutionPolicy): int` | 原子扣恢复预算并发放新执行权。 | 过期lease+nextRecoveryAt到期→owner/fence++/恢复次数++/下一持久退避 | TaskExecutionMapper.xml takeOver | 未获权返回0，不扣预算 |
| `failRecoveryExhausted(taskId: UUID, expectedFence: long, now: Instant): int` | 在合法到期裁决下结束耗尽任务。 | 共同事务检查expired/预算/无终态→FAILED/结束时间/实际进度→撤销lease | TaskExecutionMapper.xml failRecoveryExhausted | 不能由旧owner无条件失败 |

### backend/src/main/java/com/exportflow/worker/application/ExecutionSupervisor.java

阶段：新增；架构决策已确认；[T-029](tasks.md#实施与测试任务)；TC-224。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `onDelivery(context: DeliveryContext, taskId: UUID): void` | 登记本地未认领交付。 | 记录受控本地计时，不写业务分配状态 | 本地有界注册表 | 容量满停止新消费 |
| `checkClaims(): void（无参数）` | 只隔离确实未认领的停滞通道。 | 达到认领阈值→MySQL核对PENDING→协调停止/关闭通道 | TaskExecutionService.reconcile、通道关闭回调 | 正常RUNNING不按短阈值关闭 |
| `heartbeat(lease: ExecutionLease): LeaseWriteResult` | 为当前执行者受控续租。 | 调用renew→失权/UNKNOWN停止新工作并核对 | TaskExecutionService.renew/reconcile | 不能复活旧owner |
| `stop(grace: Duration): void` | 先停止执行再协调通道和权利。 | 停新领取→有界等待→停止guard/心跳→未确认通道关闭 | ExecutionGuard、通道关闭回调 | 不自行释放仍运行线程的权利 |
| `checkAttempts(): void（无参数）` | 隔离超过单次预算或失权的执行。 | 监测本地受控单调时间→停止guard与IO→核对DB→停止续租并有界关闭通道；不新发Outbox | TaskExecutionService.reconcile、ExecutionGuard、通道关闭回调 | DB不可读停止但不伪造终态 |

### backend/src/main/java/com/exportflow/worker/infrastructure/messaging/RabbitMqExportCommandListener.java

阶段：修改占位；架构决策已确认；[T-029](tasks.md#实施与测试任务)；TC-223、TC-225。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `onMessage(body: byte[], channel: Channel, deliveryTag: long): void` | 只按应用裁决确认消息。 | decode→监督登记→handler.handle→ACK或关闭/保持；不自动确认异常 | ExportCommandCodec.decode、ExecutionSupervisor.onDelivery、ExportCommandHandler.handle、Channel.basicAck/close | 非法消息暂停受影响消费并告警 |
| `closeDeliveryChannel(channelId: String): void` | 受控归还未确认消息。 | 协调停止本地执行→定位通道→关闭 | RabbitMQ Channel.close | 关闭不等于业务失败 |

### backend/src/main/java/com/exportflow/worker/port/ExportFileGenerator.java

阶段：修改占位；[T-029](tasks.md#实施与测试任务)；TC-225。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `isAvailable(): boolean（无参数）` | 验证真实生成所需能力。 | 读取已验证依赖、持久目录与原子发布配置，不在此执行导出 | 启动能力检查状态 | 不可用阻止启用消费者 |
| `generate(context: GenerationContext, progressSink: ProgressSink, guard: ExecutionGuard): GenerationResult` | 生成符合任务配置的真实Excel。 | 优先已有READY对账→收集/恢复数据→流式装配→关闭验证→安全发布→就绪登记 | ExportFileStorage.findReady/validate、ExportDataCollectionService.collect、ExcelWorkbookAssembler.assemble、ExcelFileValidator.validate、ExportFileStorage.publish/registerReady | READY/KNOWN_FAILURE/UNKNOWN；不存在未实现分支 |

### backend/src/main/java/com/exportflow/worker/infrastructure/excel/PoiExportFileGenerator.java

阶段：修改占位；真实SXSSF生成；[T-029](tasks.md#实施与测试任务)；TC-225。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `isAvailable(): boolean（无参数）` | 验证真实生成所需能力。 | 读取已验证依赖、持久目录与原子发布配置，不在此执行导出 | 启动能力检查状态 | 不可用阻止启用消费者 |
| `generate(context: GenerationContext, progressSink: ProgressSink, guard: ExecutionGuard): GenerationResult` | 生成符合任务配置的真实Excel。 | 优先已有READY对账→收集/恢复数据→流式装配→关闭验证→安全发布→就绪登记 | ExportFileStorage.findReady/validate、ExportDataCollectionService.collect、ExcelWorkbookAssembler.assemble、ExcelFileValidator.validate、ExportFileStorage.publish/registerReady | READY/KNOWN_FAILURE/UNKNOWN；不存在未实现分支 |

### backend/src/main/java/com/exportflow/worker/port/ProgressSink.java

阶段：新增；[T-029](tasks.md#实施与测试任务)；TC-224。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `report(update: ProgressUpdate): LeaseWriteResult` | 通知已经真实持久化的数据进度。 | 核对已提交断点版本→通知本地观察器；不以该回调替代持久断点事务 | TaskExecutionService.report | STALE/UNKNOWN停止 |

### backend/src/main/java/com/exportflow/worker/port/ExecutionGuard.java

阶段：新增；[T-029](tasks.md#实施与测试任务)；TC-224。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `check(): boolean（无参数）` | 让长工作及时遵守停止与失权。 | 读取受控监督器状态；false不能再写批次 | ExecutionSupervisor | 不以本地状态授予DB权利 |

### scripts/seed-orders.ps1

阶段：新增；[T-025](tasks.md#实施与测试任务)；TC-213、TC-226。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `Invoke-SeedOrders(Count: int = 110000, Seed: int = 20261007, Schema: string（必填）, ConnectionConfig: string（必填外部路径）, Help: switch = false): int` | 协调显式且可重复的合成订单初始化。 | --help→schema白名单→正式Wrapper解析已锁JDBC/JSON classpath→Java21源文件模式调用SeedOrders.main；只传外部配置路径与数量/seed，不传密码 | Maven3.9.16、Java21、SeedOrders.main | 失败非零；不打印凭据，不触及示例schema；正式工具未就绪明确失败 |

### backend/tools/SeedOrders.java

阶段：新增（生产实施阶段）；[T-025](tasks.md#实施与测试任务)；TC-215。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `main(args: String[]): void` | 在独立工具进程初始化指定隔离schema。 | 解析Count/Seed/Schema/外部JSON配置路径→复核本机URL/catalog白名单→JDBC连接→seed→安全摘要/退出 | seed、JDK DriverManager、Boot锁定Jackson | 无密码CLI/日志；非法配置非零退出，不启动Web服务 |
| `seed(connection: Connection, count: int, seed: long): SeedOutcome` | 空库按固定算法有界初始化，已有数据仅核对。 | 短核对已有→无数据时同事务PreparedStatement每1000条executeBatch→提交；失败回滚，主键防并发重复 | verifyExistingDataset、generatedOrder、JDBC PreparedStatement | 不DROP/TRUNCATE/覆盖；断开提交不明下次先核对 |
| `verifyExistingDataset(connection: Connection, count: int, seed: long): Optional<SeedOutcome>` | 证明数量及全部合成字段与固定算法一致。 | 查count→空库empty；非空按id有界流式读→逐行对generatedOrder，算法版本1且完全一致返回VERIFIED | generatedOrder、JDBC fetchSize=1000 | 任一不一致拒绝，不仅凭数量认为相同seed |
| `generatedOrder(index: long, seed: long): OrderSeedRow` | 根据固定算法生成单条合成订单。 | index/seed派生确定性随机→固定基准UTC秒→合成九字段及ID，金额BigDecimal两位 | JDK SplittableRandom、BigDecimal、Instant | 纯函数，无真实客户资料、不读取机器当前时间 |

### scripts/check-order-performance.ps1

阶段：新增；[T-030](tasks.md#实施与测试任务)；TC-213、TC-226。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `Invoke-MeasureOrderPerformance(BaseUrl: Uri（本机必填）, Warmup: int = 30, Samples: int = 200, OutputPath: string（必填）, Help: switch = false): int` | 收集可复核查询与创建P95样本。 | 校验获批参考环境→固定场景预热→逐次测量并记原始样本→计算分位→保存环境摘要 | 本机/api/v1、测试数据夹具 | 场景失败记录并非零；创建测量不能污染共享资源 |

### backend/src/main/java/com/exportflow/exporttasks/application/DownloadExportFileService.java

阶段：修改占位；本期实现；[T-034](tasks.md#实施与测试任务)；TC-234。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `prepare(taskId: UUID): DownloadHandle` | 只提供权威成功产物。 | 查摘要/resultArtifact→非成功409→元数据核对→openDownload→安全文件名/响应头 | ExportTaskRepository.findSnapshot、ArtifactRepository.findAdopted、ExportFileStorage.openDownload | 404/409/410/503/安全500 |

### backend/src/main/java/com/exportflow/exporttasks/port/ExportFileStorage.java

阶段：修改占位；本期实现；[T-033](tasks.md#实施与测试任务)；TC-230、TC-234、TC-235、TC-236。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `prepareAttempt(context: GenerationContext): AttemptWorkspace` | 取得安全独立尝试工作区。 | 服务端task/fence/owner路径→normalize/root/reparse核验→创建受控目录 | 安全文件系统API | 不暴露物理路径 |
| `writeBlock(workspace: AttemptWorkspace, header: BlockHeader, rows: List<Order>): BlockRef` | 先落盘不可变数据块。 | DataBlockCodec写part→force/close→SHA/大小→同卷原子移动→返回引用；未登记前不是检查点 | DataBlockCodec.write、路径与原子移动API | 存储临时不可用UNKNOWN；磁盘满明确错误 |
| `readBlock(ref: BlockRef): BlockReader` | 有界读取可信数据块。 | 安全路径→核验checksum/header→流式reader，调用方负责close | DataBlockCodec.open | 不完整/损坏明确区分暂不可读 |
| `publish(workspace: AttemptWorkspace, staging: StagingWorkbook, proof: ValidatedWorkbook): ArtifactProof` | 校验关闭后原子发布不可变xlsx。 | 确认当前工作区与证明→唯一artifactId目标→ATOMIC_MOVE no overwrite→metadata | 安全文件系统API | 不支持原子移动拒绝能力；目标不可覆盖 |
| `validate(proof: ArtifactProof, snapshot: TaskSnapshot): boolean` | 核对产物与冻结配置一致且可读取。 | 逻辑引用/实际大小/checksum→流式xlsx验行数/字段/类型 | ExcelFileValidator.validate | 暂不可访问UNKNOWN，不推断损坏 |
| `findReady(taskId: UUID, configurationHash: String): Optional<ArtifactProof>` | 读取可采用的可信就绪证明。 | DB查READY/ADOPTED引用→不依据文件名猜测 | ArtifactRepository.findReady | DB不可读UNKNOWN |
| `registerReady(lease: ExecutionLease, proof: ArtifactProof): LeaseWriteResult` | 由当前执行权登记就绪事实。 | task/execution/artifact固定锁→有效身份→insertReady→确认提交；不明按artifactId回读 | ArtifactRepository.registerReady | 拒绝旧owner/fence |
| `openDownload(proof: ArtifactProof): DownloadHandle` | 取得成功引用文件流资源。 | safe resolve→文件属性/size→open→由响应边界关闭 | 文件InputStream | 缺失410/权限安全500 |

### backend/src/main/java/com/exportflow/exporttasks/infrastructure/file/LocalExportFileStorage.java

阶段：修改占位；本期实现；[T-033](tasks.md#实施与测试任务)；TC-230、TC-234、TC-235、TC-236。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `prepareAttempt(context: GenerationContext): AttemptWorkspace` | 取得安全独立尝试工作区。 | 服务端task/fence/owner路径→normalize/root/reparse核验→创建受控目录 | JDK Files/FileChannel；共享safeResolve | 不暴露物理路径 |
| `writeBlock(workspace: AttemptWorkspace, header: BlockHeader, rows: List<Order>): BlockRef` | 先落盘不可变数据块。 | DataBlockCodec写part→force/close→SHA/大小→同卷原子移动→返回引用；未登记前不是检查点 | DataBlockCodec.write、路径与原子移动API、safeResolve | 存储临时不可用UNKNOWN；磁盘满明确错误 |
| `readBlock(ref: BlockRef): BlockReader` | 有界读取可信数据块。 | 安全路径→核验checksum/header→流式reader，调用方负责close | DataBlockCodec.open、safeResolve | 不完整/损坏明确区分暂不可读 |
| `publish(workspace: AttemptWorkspace, staging: StagingWorkbook, proof: ValidatedWorkbook): ArtifactProof` | 校验关闭后原子发布不可变xlsx。 | 确认当前工作区与证明→唯一artifactId目标→ATOMIC_MOVE no overwrite→metadata | JDK Files/FileChannel；共享safeResolve | 不支持原子移动拒绝能力；目标不可覆盖 |
| `validate(proof: ArtifactProof, snapshot: TaskSnapshot): boolean` | 核对产物与冻结配置一致且可读取。 | 逻辑引用/实际大小/checksum→流式xlsx验行数/字段/类型 | ExcelFileValidator.validate、safeResolve | 暂不可访问UNKNOWN，不推断损坏 |
| `findReady(taskId: UUID, configurationHash: String): Optional<ArtifactProof>` | 读取可采用的可信就绪证明。 | DB查READY/ADOPTED引用→不依据文件名猜测 | ArtifactRepository.findReady、safeResolve | DB不可读UNKNOWN |
| `registerReady(lease: ExecutionLease, proof: ArtifactProof): LeaseWriteResult` | 由当前执行权登记就绪事实。 | task/execution/artifact固定锁→有效身份→insertReady→确认提交；不明按artifactId回读 | ArtifactRepository.registerReady、safeResolve | 拒绝旧owner/fence |
| `openDownload(proof: ArtifactProof): DownloadHandle` | 取得成功引用文件流资源。 | safe resolve→文件属性/size→open→由响应边界关闭 | 文件InputStream、safeResolve | 缺失410/权限安全500 |
| `safeResolve(reference: StorageRef): Path` | 拒绝逻辑引用逃逸或符号链接。 | 仅服务端身份字段→normalize→within root→逐段NOFOLLOW/reparse核验 | JDK Path/Files | 路径穿越安全错误 |
| `closeWorkspace(workspace: AttemptWorkspace): void` | 关闭当前尝试可丢弃资源。 | 仅关闭句柄和当前工作簿scratch；不删除块/READY/成功文件 | AutoCloseable | 资源关闭错误不覆盖已提交成功 |

### backend/src/main/java/com/exportflow/exporttasks/domain/ExportFileModels.java

阶段：新增；[T-033](tasks.md#实施与测试任务)；TC-234。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `DownloadHandle.writeTo(output: OutputStream): void` | 有界写出已打开文件并保证关闭。 | 64KiB读写→finally关闭InputStream；不关闭框架output | InputStream/OutputStream | 客户端断开只停止下载，不改任务终态 |
| `DownloadHandle.close(): void（无参数）` | 释放下载文件句柄。 | 幂等close输入流 | InputStream.close | 安全IO诊断 |

### backend/src/main/java/com/exportflow/worker/port/CheckpointRepository.java

阶段：新增；架构决策已确认；[T-032](tasks.md#实施与测试任务)；TC-232、TC-235。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `find(taskId: UUID): CheckpointSnapshot` | 读取权威当前数据代次与连续清单。 | 读generation/version/cursor/block refs/verified count/high water | 持久化检查点SQL | 无首批记录返回初始状态 |
| `commitBlock(lease: ExecutionLease, expected: CheckpointSnapshot, ref: BlockRef): CheckpointWriteResult` | 原子登记可续写数据与进度。 | task/execution/checkpoint锁→身份/数据代次/前序version/游标→块insert+checkpoint+task进度同事务 | CheckpointMapper.insertBlock/updateCheckpoint、TaskExecutionMapper.advanceProgressVersion/updateProgress | UNKNOWN回读blockId；旧版本拒绝 |
| `reconcileBlock(taskId: UUID, blockId: UUID): Optional<BlockRef>` | 核对登记提交结果。 | 新连接按block identity回读 | CheckpointMapper.findBlock | DB不可读UNKNOWN |
| `startGeneration(lease: ExecutionLease, expectedVersion: long, trustedPrefix: TrustedPrefix): CheckpointWriteResult` | 只按安全重建证据切换数据代次或回退可信前缀。 | 证明只读重做安全→新dataGeneration或合法prefix→checkpointVersion++→保留高水位与旧引用 | CheckpointMapper.startGeneration | 不能删除可能有效旧块，失权拒绝 |

### backend/src/main/java/com/exportflow/worker/infrastructure/persistence/MyBatisCheckpointRepository.java

阶段：新增；架构决策已确认；[T-032](tasks.md#实施与测试任务)；TC-232、TC-235。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `find(taskId: UUID): CheckpointSnapshot` | 读取权威当前数据代次与连续清单。 | 读generation/version/cursor/block refs/verified count/high water | 持久化检查点SQL、ExportTaskRepository.lockTask、CommandOutboxMapper.databaseNow | 无首批记录返回初始状态 |
| `commitBlock(lease: ExecutionLease, expected: CheckpointSnapshot, ref: BlockRef): CheckpointWriteResult` | 原子登记可续写数据与进度。 | task/execution/checkpoint锁→身份/数据代次/前序version/游标→块insert+checkpoint+task进度同事务 | CheckpointMapper.insertBlock/updateCheckpoint、TaskExecutionMapper.advanceProgressVersion/updateProgress、ExportTaskRepository.lockTask、CommandOutboxMapper.databaseNow | UNKNOWN回读blockId；旧版本拒绝 |
| `reconcileBlock(taskId: UUID, blockId: UUID): Optional<BlockRef>` | 核对登记提交结果。 | 新连接按block identity回读 | CheckpointMapper.findBlock、ExportTaskRepository.lockTask、CommandOutboxMapper.databaseNow | DB不可读UNKNOWN |
| `startGeneration(lease: ExecutionLease, expectedVersion: long, trustedPrefix: TrustedPrefix): CheckpointWriteResult` | 只按安全重建证据切换数据代次或回退可信前缀。 | 证明只读重做安全→新dataGeneration或合法prefix→checkpointVersion++→保留高水位与旧引用 | CheckpointMapper.startGeneration、ExportTaskRepository.lockTask、CommandOutboxMapper.databaseNow | 不能删除可能有效旧块，失权拒绝 |

### backend/src/main/java/com/exportflow/worker/infrastructure/persistence/CheckpointMapper.java

阶段：新增；架构决策已确认；[T-032](tasks.md#实施与测试任务)；TC-235。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `find(taskId: UUID): CheckpointRecord` | 读取检查点头。 | select当前代次/版本/游标 | CheckpointMapper.xml find | SQL错误 |
| `findBlocks(taskId: UUID, dataGeneration: long): List<BlockRecord>` | 按连续顺序返回有界块引用清单。 | 按sequence排序，最多200块上限由定稿1000行batch与200000业务上限确定 | CheckpointMapper.xml findBlocks | SQL错误；未来更小batch需复核清单内存 |
| `findBlock(taskId: UUID, blockId: UUID): Optional<BlockRecord>` | 对账已提交块。 | 按唯一identity select | CheckpointMapper.xml findBlock | SQL错误 |
| `insertBlock(record: BlockRecord): int` | 持久保存不可变引用。 | unique task/generation/sequence和blockId | CheckpointMapper.xml insertBlock | 唯一冲突回读核对 |
| `updateCheckpoint(lease: ExecutionLease, expected: CheckpointRecord, ref: BlockRecord): int` | CAS推进连续游标与版本。 | 检查前序→更新count/cursor/checkpointVersion | CheckpointMapper.xml updateCheckpoint | SQL错误 |
| `startGeneration(lease: ExecutionLease, expectedVersion: long, prefix: TrustedPrefix): int` | 切换合法代次并保护进度高水位。 | 条件version→generation/prefix→保留旧引用 | CheckpointMapper.xml startGeneration | SQL错误 |

### backend/src/main/java/com/exportflow/worker/application/ExportDataCollectionService.java

阶段：新增；架构决策已确认；[T-032](tasks.md#实施与测试任务)；TC-230、TC-232、TC-235。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `collect(context: GenerationContext, progressSink: ProgressSink, guard: ExecutionGuard): CompletedDataset` | 取得完整可信数据块清单。 | checkpoint→validatePrefix→必要安全startGeneration→guard→readBatch→writeBlock→commitBlock/reconcile→已提交report→行数确认 | CheckpointRepository.find/commitBlock/reconcileBlock/startGeneration、validatePrefix、OrderRepository.readBatch、ExportFileStorage.writeBlock、ProgressSink.report、ExecutionGuard.check | 失权停止；UNKNOWN不删引用 |
| `validatePrefix(snapshot: CheckpointSnapshot, config: String): TrustedPrefix` | 只采用可信连续持久数据。 | 逐块metadata/校验和/序号/游标/行数→可信前缀；暂不可访问UNKNOWN | ExportFileStorage.readBlock | 不把进度高水位当读取游标 |

### backend/src/main/java/com/exportflow/worker/infrastructure/file/DataBlockCodec.java

阶段：新增；[T-032](tasks.md#实施与测试任务)；TC-235。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `write(header: BlockHeader, rows: List<Order>, output: OutputStream): BlockDigest` | 有界编码可跨重启复用的源数据。 | 元数据首行→逐行UTF8编码/大小限制→SHA和计数 | 获批JSON库、JDK MessageDigest | 超限/非法值明确错误，不截断 |
| `open(ref: BlockRef, input: InputStream): BlockReader` | 读取并核验数据块版本。 | 流式header→config/generation/sequence/游标核验→reader | 获批JSON库 | 未知版本/损坏与IO不明区分 |
| `BlockReader.next(): Optional<Order>（无参数）` | 每次只解码一行订单。 | 有界行读取→schema→Order；EOF核对digest和行数 | Order领域模型 | 坏块明确错误 |
| `BlockReader.close(): void（无参数）` | 释放块流。 | 幂等关闭 | InputStream.close | 安全IO诊断 |

### backend/src/main/java/com/exportflow/worker/infrastructure/excel/ExcelRowProjector.java

阶段：新增；[T-033](tasks.md#实施与测试任务)；TC-230、TC-233。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `headers(fields: List<ExportField>): List<String>` | 输出有序中文表头。 | 白名单映射，不重排 | ExportField.validateFields | 非法字段拒绝 |
| `cells(order: Order, fields: List<ExportField>): List<String>` | 精确映射业务单元格文本。 | 状态渠道中文/未知→金额toPlainString两位→香港时间→文本ID与手机号 | BigDecimal、DateTimeFormatter指定Asia/Hong_Kong | 超长/非法控制字符错误，不转公式 |
| `fileName(snapshot: TaskSnapshot): String` | 按受理时间与任务号生成安全人类文件名。 | createdAt香港格式→taskNo→去CR/LF与文件名危险字符 | DateTimeFormatter | 不作为物理身份 |

### backend/src/main/java/com/exportflow/worker/infrastructure/excel/ExcelWorkbookAssembler.java

阶段：新增；[T-033](tasks.md#实施与测试任务)；TC-230、TC-231、TC-233、TC-236。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `assemble(context: GenerationContext, dataset: CompletedDataset, workspace: AttemptWorkspace, guard: ExecutionGuard): StagingWorkbook` | 将已完成行逐批写入临时文件，结束后生成待发布结果。 | headers→100行窗口/压缩scratch→逐块逐行guard/cells→每块末flushCompletedRows→完整数据一次write至.xlsx.part→close/dispose→force输出→计数 | ExportFileStorage.readBlock、ExcelRowProjector.headers/cells、flushCompletedRows、ExecutionGuard.check、SXSSFWorkbook | 失权/刷盘失败停止；临时结果不提供下载 |
| `flushCompletedRows(sheet: SXSSFSheet, guard: ExecutionGuard, retainedRows: int = 0): void` | 将本批已写完的行和缓冲内容刷入磁盘临时文件。 | guard核对→sheet.flushRows(retainedRows)→sheet.flushBufferedData→释放已完成行内存；每块末默认保留0行 | ExecutionGuard.check、POI SXSSFSheet.flushRows/flushBufferedData | IO失败或失权停止装配；不重复调用workbook.write追加ZIP |
| `closeWorkbook(workbook: SXSSFWorkbook, output: OutputStream): void` | 即使异常也关闭本次工作簿与scratch。 | 关闭stream/close/dispose分别处理并保留主错误；不删断点 | POI close/dispose | 清理故障不覆盖已提交结果 |

### backend/src/main/java/com/exportflow/worker/infrastructure/excel/ExcelFileValidator.java

阶段：新增；[T-033](tasks.md#实施与测试任务)；TC-230、TC-231、TC-233、TC-236。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `validate(staging: StagingWorkbook, snapshot: TaskSnapshot, guard: ExecutionGuard): ValidatedWorkbook` | 在发布前核对文件可打开及结构准确。 | ZIP/schema→sheet/表头/列序/行数/STRING类型→流式SHA/size→guard | ExcelRowProjector.headers、XSSFReader、ExecutionGuard.check | 损坏或不一致拒绝READY |
| `validateRows(reader: XSSFReader, fields: List<ExportField>, expectedRows: long, guard: ExecutionGuard): long` | 流式检查每行结构和文本表达。 | SAX解析→字段数/表头/类型/格式→EOF行数 | ExcelRowProjector.headers、SAX框架 | 不会为了验证把文件读成全量对象 |

### backend/src/main/java/com/exportflow/exporttasks/port/ArtifactRepository.java

阶段：新增；架构决策已确认；[T-033](tasks.md#实施与测试任务)；TC-234、TC-235、TC-236。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `findReady(taskId: UUID, configurationHash: String): Optional<ArtifactProof>` | 找回可信同配置产物。 | 只读READY/ADOPTED；排除cleanup | ArtifactMapper.findReady | DB故障UNKNOWN |
| `findAdopted(taskId: UUID): Optional<ArtifactProof>` | 读取成功所选唯一产物。 | 按task.resultArtifactId读ADOPTED | ArtifactMapper.findAdopted | 缺失权威引用告警 |
| `registerReady(lease: ExecutionLease, proof: ArtifactProof): LeaseWriteResult` | 受执行权保护登记就绪文件。 | task/execution/artifact锁→有效身份→insert→确认；未知按id回读 | ArtifactMapper.insertReady/find | 旧身份拒绝 |
| `claimCleanup(candidate: StorageCandidate, now: Instant): Optional<CleanupPermit>` | 在采用与引用裁决后授予清理资格。 | task/execution/checkpoint/artifact锁→无有效attempt/引用且过grace→标CLEANING并撤销采用资格 | ArtifactMapper.lockArtifact/claimCleanup | 不可证明安全不清理 |
| `completeCleanup(permit: CleanupPermit): void` | 记录实际删除结果。 | permit token条件更新→CLEANED；失败仍可恢复核对 | ArtifactMapper.completeCleanup | 不清成功文件 |

### backend/src/main/java/com/exportflow/exporttasks/infrastructure/persistence/MyBatisArtifactRepository.java

阶段：新增；架构决策已确认；[T-033](tasks.md#实施与测试任务)；TC-235、TC-236。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `findReady(taskId: UUID, configurationHash: String): Optional<ArtifactProof>` | 找回可信同配置产物。 | 只读READY/ADOPTED；排除cleanup | ArtifactMapper.findReady、ExportTaskRepository.lockTask、CommandOutboxMapper.databaseNow | DB故障UNKNOWN |
| `findAdopted(taskId: UUID): Optional<ArtifactProof>` | 读取成功所选唯一产物。 | 按task.resultArtifactId读ADOPTED | ArtifactMapper.findAdopted、ExportTaskRepository.lockTask、CommandOutboxMapper.databaseNow | 缺失权威引用告警 |
| `registerReady(lease: ExecutionLease, proof: ArtifactProof): LeaseWriteResult` | 受执行权保护登记就绪文件。 | task/execution/artifact锁→有效身份→insert→确认；未知按id回读 | ArtifactMapper.insertReady/find、ExportTaskRepository.lockTask、CommandOutboxMapper.databaseNow | 旧身份拒绝 |
| `claimCleanup(candidate: StorageCandidate, now: Instant): Optional<CleanupPermit>` | 在采用与引用裁决后授予清理资格。 | task/execution/checkpoint/artifact锁→无有效attempt/引用且过grace→标CLEANING并撤销采用资格 | ArtifactMapper.lockArtifact/claimCleanup、ExportTaskRepository.lockTask、CommandOutboxMapper.databaseNow | 不可证明安全不清理 |
| `completeCleanup(permit: CleanupPermit): void` | 记录实际删除结果。 | permit token条件更新→CLEANED；失败仍可恢复核对 | ArtifactMapper.completeCleanup、ExportTaskRepository.lockTask、CommandOutboxMapper.databaseNow | 不清成功文件 |

### backend/src/main/java/com/exportflow/exporttasks/infrastructure/persistence/ArtifactMapper.java

阶段：新增；架构决策已确认；[T-033](tasks.md#实施与测试任务)；TC-234、TC-235、TC-236。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `findReady(taskId: UUID, configurationHash: String): Optional<ArtifactRecord>` | 查可信可采用产物。 | select READY/ADOPTED同config | ArtifactMapper.xml findReady | SQL错误 |
| `findAdopted(taskId: UUID): Optional<ArtifactRecord>` | 读取成功引用。 | JOIN task.result_artifact_id | ArtifactMapper.xml findAdopted | SQL错误 |
| `find(artifactId: UUID): Optional<ArtifactRecord>` | 就绪提交结果对账。 | select PK | ArtifactMapper.xml find | SQL错误 |
| `insertReady(record: ArtifactRecord): int` | 登记校验完的不可变结果。 | 参数化insert unique artifactRef/id | ArtifactMapper.xml insertReady | 唯一冲突核对 |
| `lockArtifact(artifactId: UUID): ArtifactRecord` | 与采用/清理共用行锁。 | 既定task/execution/checkpoint后FOR UPDATE | ArtifactMapper.xml lockArtifact | SQL错误 |
| `adopt(taskId: UUID, artifactId: UUID, fence: long): int` | 标记结果被成功任务采用。 | READY→ADOPTED，同事务task结果引用 | ArtifactMapper.xml adopt | CLEANING不能采用 |
| `registerCandidate(candidate: StorageCandidate): int` | 为磁盘孤儿建立DB清理身份。 | insert幂等逻辑路径/attempt；无锁目录扫描只产生候选 | ArtifactMapper.xml registerCandidate | 不能直接授删除权 |
| `claimCleanup(candidate: StorageCandidate, token: UUID, now: Instant): int` | 标记可安全删除候选。 | 完整引用/身份核对后CLEANING | ArtifactMapper.xml claimCleanup | SQL错误 |
| `completeCleanup(permit: CleanupPermit): int` | 写回删除结果。 | token条件CLEANED | ArtifactMapper.xml completeCleanup | SQL错误 |

### backend/src/main/java/com/exportflow/worker/application/OrphanCleanupService.java

阶段：新增；架构决策已确认；[T-033](tasks.md#实施与测试任务)；TC-236。

| 名称、参数与返回值 | 中文注释草稿 | 简短实现流程 | 调用关系 | 结果／错误出口 |
| --- | --- | --- | --- | --- |
| `scanCandidates(limit: int = 20): int` | 收集过grace残留但不直接删除。 | 受控根内扫描→登记candidate→逐项claimCleanup→deleteWithPermit→complete | ArtifactMapper.registerCandidate、ArtifactRepository.claimCleanup/completeCleanup、deleteWithPermit | 依赖不可读跳过清理；有效引用永久保留 |
| `deleteWithPermit(permit: CleanupPermit): boolean` | 只删除经串行裁决授予的孤儿。 | 核验permit token/root/NOFOLLOW→单文件删除→返回结果 | JDK Files.deleteIfExists | 无递归盲删，删除失败留可核对状态 |

## 设计边界核对

- DTO、SQL记录、消息和领域分开建模；SQL Mapper只输出独立记录或标量，由基础设施映射。新数据块与产物记录的机械映射不夹带业务判断，复杂版本校验在单列函数执行。
- 相同方法在端口与实现中各列一项；就绪/断点/终态事务不能拆分。数据块登记同时推进持久进度，ProgressSink仅通知已提交事实。
- 真实POI/file适配本期实施；Redis/SSE和完整任务页面保留注释占位；用户暂停、显式恢复、跨主机存储另规格。
- 依赖准确版本、执行恢复子集和参数已定稿，见[版本表](dependency-versions.md)与[决策记录](decision-record.md)；具体代码生成仍受规格及测试阶段审批约束。框架内部函数不展开；脚本若需要额外自有拆分，先补清单。
