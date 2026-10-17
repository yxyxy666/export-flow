# 逐文件职责与函数设计草稿

**版本**：0.3　**日期**：2026-10-17　**状态**：规格及测试阶段已批准，生产实施已授权；本清单已按实际代码同步。

关联[计划](plan.md)、[任务与审批](tasks.md)、[模型](data-model.md)、[HTTP/SSE](contracts/http-sse.md)、[暂停](contracts/execution-control.md)及[修复](contracts/assembly-repair.md)。以下保留设计目的和调用关系，签名采用实际代码；未生成任何预留CRUD、手动重试或继续接口。

## 逐文件职责

| 文件 | 职责与内容 |
| --- | --- |
| [frontend/vite.config.ts](../../frontend/vite.config.ts) | 开发代理从项目根配置读取并校验后端端口；只读取端口键，凭据不注入客户端 |
| [.env.example](../../.env.example) | 依赖或运行／测试配置；实例身份、Redis外部连接、构建门禁及测试选择；不改共享容器配置 |
| [backend/pom.xml](../../backend/pom.xml) | 依赖或运行／测试配置；实例身份、Redis外部连接、构建门禁及测试选择；不改共享容器配置 |
| [backend/src/main/java/com/exportflow/config/RuntimeConfiguration.java](../../backend/src/main/java/com/exportflow/config/RuntimeConfiguration.java) | 组装生成器和修复服务、声明独立durable fanout及稳定实例队列 |
| [backend/src/main/java/com/exportflow/config/RuntimeProperties.java](../../backend/src/main/java/com/exportflow/config/RuntimeProperties.java) | 外部实例、缓存与执行配置校验；凭据外注入 |
| [backend/src/main/java/com/exportflow/dispatch/infrastructure/persistence/CommandOutboxMapper.java](../../backend/src/main/java/com/exportflow/dispatch/infrastructure/persistence/CommandOutboxMapper.java) | 原命令SQL增加暂停状态保护，保留v1消息及原唯一键 |
| [backend/src/main/java/com/exportflow/dispatch/infrastructure/persistence/MyBatisCommandOutboxRepository.java](../../backend/src/main/java/com/exportflow/dispatch/infrastructure/persistence/MyBatisCommandOutboxRepository.java) | 原命令授权与封存；暂停不得被迟到确认或投递扫描复活 |
| [backend/src/main/java/com/exportflow/exporttasks/api/TaskManagementApiMapper.java](../../backend/src/main/java/com/exportflow/exporttasks/api/TaskManagementApiMapper.java) | 协议与领域转换；解析白名单参数、字符串long和安全错误 |
| [backend/src/main/java/com/exportflow/exporttasks/api/TaskManagementApiModels.java](../../backend/src/main/java/com/exportflow/exporttasks/api/TaskManagementApiModels.java) | 类型、record及枚举；携带控制版本、可信计数、修复阶段或协议字段；不执行I/O |
| [backend/src/main/java/com/exportflow/exporttasks/api/TaskManagementController.java](../../backend/src/main/java/com/exportflow/exporttasks/api/TaskManagementController.java) | HTTP列表和暂停入口；校验参数后调用服务，输出安全DTO |
| [backend/src/main/java/com/exportflow/exporttasks/application/CreateExportTransaction.java](../../backend/src/main/java/com/exportflow/exporttasks/application/CreateExportTransaction.java) | 初次受理与原命令／初始进度通知同事务，幂等重放不重复创建 |
| [backend/src/main/java/com/exportflow/exporttasks/application/TaskManagementService.java](../../backend/src/main/java/com/exportflow/exporttasks/application/TaskManagementService.java) | 查询与暂停用例；仓库异常分类为安全拒绝或结果不明 |
| [backend/src/main/java/com/exportflow/exporttasks/domain/ExportFileModels.java](../../backend/src/main/java/com/exportflow/exporttasks/domain/ExportFileModels.java) | 类型、record及枚举；携带控制版本、可信计数、修复阶段或协议字段；不执行I/O |
| [backend/src/main/java/com/exportflow/exporttasks/domain/ExportTask.java](../../backend/src/main/java/com/exportflow/exporttasks/domain/ExportTask.java) | 类型、record及枚举；携带控制版本、可信计数、修复阶段或协议字段；不执行I/O |
| [backend/src/main/java/com/exportflow/exporttasks/domain/ExportTaskModels.java](../../backend/src/main/java/com/exportflow/exporttasks/domain/ExportTaskModels.java) | 类型、record及枚举；携带控制版本、可信计数、修复阶段或协议字段；不执行I/O |
| [backend/src/main/java/com/exportflow/exporttasks/domain/TaskManagementModels.java](../../backend/src/main/java/com/exportflow/exporttasks/domain/TaskManagementModels.java) | 类型、record及枚举；携带控制版本、可信计数、修复阶段或协议字段；不执行I/O |
| [backend/src/main/java/com/exportflow/exporttasks/infrastructure/persistence/ExportTaskPersistenceMapper.java](../../backend/src/main/java/com/exportflow/exporttasks/infrastructure/persistence/ExportTaskPersistenceMapper.java) | 依赖或运行／测试配置；实例身份、Redis外部连接、构建门禁及测试选择；不改共享容器配置 |
| [backend/src/main/java/com/exportflow/exporttasks/infrastructure/persistence/ExportTaskRecord.java](../../backend/src/main/java/com/exportflow/exporttasks/infrastructure/persistence/ExportTaskRecord.java) | 类型、record及枚举；携带控制版本、可信计数、修复阶段或协议字段；不执行I/O |
| [backend/src/main/java/com/exportflow/exporttasks/infrastructure/persistence/MyBatisArtifactRepository.java](../../backend/src/main/java/com/exportflow/exporttasks/infrastructure/persistence/MyBatisArtifactRepository.java) | READY控制屏障与完整可信检查；引用保护下清理，物理I/O在事务外 |
| [backend/src/main/java/com/exportflow/exporttasks/infrastructure/persistence/MyBatisExportTaskRepository.java](../../backend/src/main/java/com/exportflow/exporttasks/infrastructure/persistence/MyBatisExportTaskRepository.java) | 依赖或运行／测试配置；实例身份、Redis外部连接、构建门禁及测试选择；不改共享容器配置 |
| [backend/src/main/java/com/exportflow/exporttasks/infrastructure/persistence/MyBatisTaskManagementRepository.java](../../backend/src/main/java/com/exportflow/exporttasks/infrastructure/persistence/MyBatisTaskManagementRepository.java) | READ COMMITTED单SELECT列表快照；activity串行暂停与幂等回执同事务提交 |
| [backend/src/main/java/com/exportflow/exporttasks/infrastructure/persistence/TaskManagementMapper.java](../../backend/src/main/java/com/exportflow/exporttasks/infrastructure/persistence/TaskManagementMapper.java) | 参数化分页投影及暂停操作记录；SQL不执行文件或网络I/O |
| [backend/src/main/java/com/exportflow/exporttasks/infrastructure/persistence/TaskManagementPersistenceMapper.java](../../backend/src/main/java/com/exportflow/exporttasks/infrastructure/persistence/TaskManagementPersistenceMapper.java) | 持久化记录转领域任务摘要，可信计数作为展示进度 |
| [backend/src/main/java/com/exportflow/exporttasks/port/TaskManagementRepository.java](../../backend/src/main/java/com/exportflow/exporttasks/port/TaskManagementRepository.java) | 依赖或运行／测试配置；实例身份、Redis外部连接、构建门禁及测试选择；不改共享容器配置 |
| [backend/src/main/java/com/exportflow/orders/infrastructure/persistence/MyBatisOrderRepository.java](../../backend/src/main/java/com/exportflow/orders/infrastructure/persistence/MyBatisOrderRepository.java) | 只读原订单；原scope与(prev,last]有界分段查询，稳定排序 |
| [backend/src/main/java/com/exportflow/orders/infrastructure/persistence/OrderMapper.java](../../backend/src/main/java/com/exportflow/orders/infrastructure/persistence/OrderMapper.java) | 只读订单分页及有界分段参数化SQL |
| [backend/src/main/java/com/exportflow/orders/port/OrderRepository.java](../../backend/src/main/java/com/exportflow/orders/port/OrderRepository.java) | 依赖或运行／测试配置；实例身份、Redis外部连接、构建门禁及测试选择；不改共享容器配置 |
| [backend/src/main/java/com/exportflow/progress/api/TaskEventController.java](../../backend/src/main/java/com/exportflow/progress/api/TaskEventController.java) | 同源SSE入口；订阅就绪后注册emitter并交给MVC |
| [backend/src/main/java/com/exportflow/progress/api/TaskStreamWebConfiguration.java](../../backend/src/main/java/com/exportflow/progress/api/TaskStreamWebConfiguration.java) | MVC异步Handler安装完成后激活真实发送，禁止把预初始化缓冲当作已发送 |
| [backend/src/main/java/com/exportflow/progress/application/ProgressConsumerReadiness.java](../../backend/src/main/java/com/exportflow/progress/application/ProgressConsumerReadiness.java) | 监听broker订阅确认；稳定ID冲突拒绝启动，未就绪返回503 |
| [backend/src/main/java/com/exportflow/progress/application/ProgressScheduler.java](../../backend/src/main/java/com/exportflow/progress/application/ProgressScheduler.java) | 独立通知投递、空闲裁决及心跳入口，不生成导出任务 |
| [backend/src/main/java/com/exportflow/progress/application/SseConnectionRegistry.java](../../backend/src/main/java/com/exportflow/progress/application/SseConnectionRegistry.java) | 每连接有界串行队列；ready先入队、实际send后完成future，错误或溢出关闭并释放 |
| [backend/src/main/java/com/exportflow/progress/application/TaskProgressService.java](../../backend/src/main/java/com/exportflow/progress/application/TaskProgressService.java) | 领取通知→可选缓存→可靠发布→CAS确认；消费时回源并等待本地实际发送 |
| [backend/src/main/java/com/exportflow/progress/domain/ProgressModels.java](../../backend/src/main/java/com/exportflow/progress/domain/ProgressModels.java) | 类型、record及枚举；携带控制版本、可信计数、修复阶段或协议字段；不执行I/O |
| [backend/src/main/java/com/exportflow/progress/infrastructure/cache/RedisProgressCache.java](../../backend/src/main/java/com/exportflow/progress/infrastructure/cache/RedisProgressCache.java) | Lua按十进制字符串比较long版本；隔离键与索引、容量及TTL运行策略 |
| [backend/src/main/java/com/exportflow/progress/infrastructure/messaging/RabbitProgressListener.java](../../backend/src/main/java/com/exportflow/progress/infrastructure/messaging/RabbitProgressListener.java) | 独立手动确认消费者；onNotice实际发送成功才ACK，否则保留未确认消息 |
| [backend/src/main/java/com/exportflow/progress/infrastructure/messaging/RabbitProgressTransport.java](../../backend/src/main/java/com/exportflow/progress/infrastructure/messaging/RabbitProgressTransport.java) | 只负责fanout发布、confirm与路由证据；不含消费监听 |
| [backend/src/main/java/com/exportflow/progress/infrastructure/persistence/MyBatisProgressRepository.java](../../backend/src/main/java/com/exportflow/progress/infrastructure/persistence/MyBatisProgressRepository.java) | MySQL活动行与进度Outbox实现；旧确认不消除更高pending版本 |
| [backend/src/main/java/com/exportflow/progress/infrastructure/persistence/ProgressMapper.java](../../backend/src/main/java/com/exportflow/progress/infrastructure/persistence/ProgressMapper.java) | 注解SQL维护活动版本、通知租约与CAS，不存在ProgressMapper.xml |
| [backend/src/main/java/com/exportflow/progress/port/ProgressCache.java](../../backend/src/main/java/com/exportflow/progress/port/ProgressCache.java) | 可选缓存端口；按最低版本读取，旧写拒绝 |
| [backend/src/main/java/com/exportflow/progress/port/ProgressPublisher.java](../../backend/src/main/java/com/exportflow/progress/port/ProgressPublisher.java) | 发布最小版本通知并返回broker可靠确认 |
| [backend/src/main/java/com/exportflow/progress/port/ProgressRepository.java](../../backend/src/main/java/com/exportflow/progress/port/ProgressRepository.java) | 业务事务内登记通知；领取和确认使用独立短事务 |
| [backend/src/main/java/com/exportflow/worker/application/AssemblyRepairService.java](../../backend/src/main/java/com/exportflow/worker/application/AssemblyRepairService.java) | 封存清单全段审计→可信计数→坏段有界重读及基线比对→原子替换；健康块保留 |
| [backend/src/main/java/com/exportflow/worker/application/ExecutionSupervisor.java](../../backend/src/main/java/com/exportflow/worker/application/ExecutionSupervisor.java) | guard结合权威控制；USER_PAUSE停止推进并保留delivery等待安全回执 |
| [backend/src/main/java/com/exportflow/worker/application/ExportCommandHandler.java](../../backend/src/main/java/com/exportflow/worker/application/ExportCommandHandler.java) | 原消息身份核对、认领与生成；优先处理PAUSE_STOPPED，只有持久封存后ACK |
| [backend/src/main/java/com/exportflow/worker/application/ExportDataCollectionService.java](../../backend/src/main/java/com/exportflow/worker/application/ExportDataCollectionService.java) | 按稳定游标逐块读取、关闭落盘后登记；旧完整块实体验证封存，不重复查询来源 |
| [backend/src/main/java/com/exportflow/worker/application/PauseReconciliationService.java](../../backend/src/main/java/com/exportflow/worker/application/PauseReconciliationService.java) | 安全停止证明与启动扫描；只确认暂停，不重新生成 |
| [backend/src/main/java/com/exportflow/worker/application/TaskExecutionService.java](../../backend/src/main/java/com/exportflow/worker/application/TaskExecutionService.java) | 执行仓库用例协调与未知结果分类 |
| [backend/src/main/java/com/exportflow/worker/domain/AssemblyRepairModels.java](../../backend/src/main/java/com/exportflow/worker/domain/AssemblyRepairModels.java) | 类型、record及枚举；携带控制版本、可信计数、修复阶段或协议字段；不执行I/O |
| [backend/src/main/java/com/exportflow/worker/domain/ExecutionModels.java](../../backend/src/main/java/com/exportflow/worker/domain/ExecutionModels.java) | 类型、record及枚举；携带控制版本、可信计数、修复阶段或协议字段；不执行I/O |
| [backend/src/main/java/com/exportflow/worker/infrastructure/excel/ExcelFileValidator.java](../../backend/src/main/java/com/exportflow/worker/infrastructure/excel/ExcelFileValidator.java) | 检查最终XLSX结构、行数、表头、文本类型、字段格式与文件摘要；不重新查询来源 |
| [backend/src/main/java/com/exportflow/worker/infrastructure/excel/ExcelWorkbookAssembler.java](../../backend/src/main/java/com/exportflow/worker/infrastructure/excel/ExcelWorkbookAssembler.java) | 顺序读取有效数据块并写SXSSF，EOF校验与资源关闭，工作簿整体重建 |
| [backend/src/main/java/com/exportflow/worker/infrastructure/excel/PoiExportFileGenerator.java](../../backend/src/main/java/com/exportflow/worker/infrastructure/excel/PoiExportFileGenerator.java) | 资源作用域内收集／审计／局部重建／新Workbook装配／最终校验／发布登记；暂停优先 |
| [backend/src/main/java/com/exportflow/worker/infrastructure/file/DataBlockCodec.java](../../backend/src/main/java/com/exportflow/worker/infrastructure/file/DataBlockCodec.java) | 块格式、SHA及规范内容基线编码；完整读至EOF后才能相信块内容 |
| [backend/src/main/java/com/exportflow/worker/infrastructure/persistence/AssemblyRepairMapper.java](../../backend/src/main/java/com/exportflow/worker/infrastructure/persistence/AssemblyRepairMapper.java) | 分段清单、选择、预算及轮次历史SQL；有序锁段，终态收口修复历史 |
| [backend/src/main/java/com/exportflow/worker/infrastructure/persistence/CheckpointMapper.java](../../backend/src/main/java/com/exportflow/worker/infrastructure/persistence/CheckpointMapper.java) | 断点及完整可信谓词；READY必须清单已封存、全段VERIFIED且计数与配置吻合 |
| [backend/src/main/java/com/exportflow/worker/infrastructure/persistence/ExecutionLeaseRecord.java](../../backend/src/main/java/com/exportflow/worker/infrastructure/persistence/ExecutionLeaseRecord.java) | 类型、record及枚举；携带控制版本、可信计数、修复阶段或协议字段；不执行I/O |
| [backend/src/main/java/com/exportflow/worker/infrastructure/persistence/MyBatisAssemblyRepairRepository.java](../../backend/src/main/java/com/exportflow/worker/infrastructure/persistence/MyBatisAssemblyRepairRepository.java) | 持久3轮预算及修复计划，提交可信进度与通知，同blockId找回避免重复计数 |
| [backend/src/main/java/com/exportflow/worker/infrastructure/persistence/MyBatisCheckpointRepository.java](../../backend/src/main/java/com/exportflow/worker/infrastructure/persistence/MyBatisCheckpointRepository.java) | 块与段基线原子提交；新代重置当前可信计数并通知，已封存不走旧重建 |
| [backend/src/main/java/com/exportflow/worker/infrastructure/persistence/MyBatisTaskExecutionRepository.java](../../backend/src/main/java/com/exportflow/worker/infrastructure/persistence/MyBatisTaskExecutionRepository.java) | 认领、控制、续租、安全暂停及终态事务；READY采用再次验证，完成修复历史 |
| [backend/src/main/java/com/exportflow/worker/infrastructure/persistence/TaskExecutionMapper.java](../../backend/src/main/java/com/exportflow/worker/infrastructure/persistence/TaskExecutionMapper.java) | 执行身份、控制版本、状态与进度SQL；旧执行不得越过暂停屏障 |
| [backend/src/main/java/com/exportflow/worker/port/AssemblyRepairRepository.java](../../backend/src/main/java/com/exportflow/worker/port/AssemblyRepairRepository.java) | 修复短事务端口，执行控制身份与清单／选择版本CAS |
| [backend/src/main/java/com/exportflow/worker/port/CheckpointRepository.java](../../backend/src/main/java/com/exportflow/worker/port/CheckpointRepository.java) | 依赖或运行／测试配置；实例身份、Redis外部连接、构建门禁及测试选择；不改共享容器配置 |
| [backend/src/main/java/com/exportflow/worker/port/ExecutionGuard.java](../../backend/src/main/java/com/exportflow/worker/port/ExecutionGuard.java) | 依赖或运行／测试配置；实例身份、Redis外部连接、构建门禁及测试选择；不改共享容器配置 |
| [backend/src/main/java/com/exportflow/worker/port/TaskExecutionRepository.java](../../backend/src/main/java/com/exportflow/worker/port/TaskExecutionRepository.java) | 依赖或运行／测试配置；实例身份、Redis外部连接、构建门禁及测试选择；不改共享容器配置 |
| [backend/src/main/resources/application.yml](../../backend/src/main/resources/application.yml) | 依赖或运行／测试配置；实例身份、Redis外部连接、构建门禁及测试选择；不改共享容器配置 |
| [backend/src/main/resources/db/migration/V5__task_list_pause_and_progress.sql](../../backend/src/main/resources/db/migration/V5__task_list_pause_and_progress.sql) | 追加迁移，六态／控制／通知或分段基线／可信计数；保留旧任务、命令与文件引用，不改历史迁移 |
| [backend/src/main/resources/db/migration/V6__assembly_repair_segments.sql](../../backend/src/main/resources/db/migration/V6__assembly_repair_segments.sql) | 追加迁移，六态／控制／通知或分段基线／可信计数；保留旧任务、命令与文件引用，不改历史迁移 |
| [backend/src/main/resources/mappers/AssemblyRepairMapper.xml](../../backend/src/main/resources/mappers/AssemblyRepairMapper.xml) | 分段清单、选择、预算及轮次历史SQL；有序锁段，终态收口修复历史 |
| [backend/src/main/resources/mappers/CheckpointMapper.xml](../../backend/src/main/resources/mappers/CheckpointMapper.xml) | 断点及完整可信谓词；READY必须清单已封存、全段VERIFIED且计数与配置吻合 |
| [backend/src/main/resources/mappers/CommandOutboxMapper.xml](../../backend/src/main/resources/mappers/CommandOutboxMapper.xml) | 原命令SQL增加暂停状态保护，保留v1消息及原唯一键 |
| [backend/src/main/resources/mappers/ExportTaskMapper.xml](../../backend/src/main/resources/mappers/ExportTaskMapper.xml) | 参数化SQL，配合对应Java Mapper完成查询、控制屏障或持久化；无独立函数 |
| [backend/src/main/resources/mappers/OrderMapper.xml](../../backend/src/main/resources/mappers/OrderMapper.xml) | 只读订单分页及有界分段参数化SQL |
| [backend/src/main/resources/mappers/TaskExecutionMapper.xml](../../backend/src/main/resources/mappers/TaskExecutionMapper.xml) | 执行身份、控制版本、状态与进度SQL；旧执行不得越过暂停屏障 |
| [backend/src/main/resources/mappers/TaskManagementMapper.xml](../../backend/src/main/resources/mappers/TaskManagementMapper.xml) | 参数化分页投影及暂停操作记录；SQL不执行文件或网络I/O |
| [backend/src/test/java/com/exportflow/exporttasks/TaskManagementContractTest.java](../../backend/src/test/java/com/exportflow/exporttasks/TaskManagementContractTest.java) | 测试／夹具；具体行为、红绿灯与对应标准见[任务映射](tasks.md#ac测试用例映射)及[证据](../../docs/validation/004-task-management/verification.md) |
| [backend/src/test/java/com/exportflow/exporttasks/TaskManagementMigrationTest.java](../../backend/src/test/java/com/exportflow/exporttasks/TaskManagementMigrationTest.java) | 测试／夹具；具体行为、红绿灯与对应标准见[任务映射](tasks.md#ac测试用例映射)及[证据](../../docs/validation/004-task-management/verification.md) |
| [backend/src/test/java/com/exportflow/exporttasks/TaskManagementUnitTest.java](../../backend/src/test/java/com/exportflow/exporttasks/TaskManagementUnitTest.java) | 测试／夹具；具体行为、红绿灯与对应标准见[任务映射](tasks.md#ac测试用例映射)及[证据](../../docs/validation/004-task-management/verification.md) |
| [backend/src/test/java/com/exportflow/progress/ProgressDeliveryUnitTest.java](../../backend/src/test/java/com/exportflow/progress/ProgressDeliveryUnitTest.java) | 测试／夹具；具体行为、红绿灯与对应标准见[任务映射](tasks.md#ac测试用例映射)及[证据](../../docs/validation/004-task-management/verification.md) |
| [backend/src/test/java/com/exportflow/progress/TaskProgressIntegrationTest.java](../../backend/src/test/java/com/exportflow/progress/TaskProgressIntegrationTest.java) | 测试／夹具；具体行为、红绿灯与对应标准见[任务映射](tasks.md#ac测试用例映射)及[证据](../../docs/validation/004-task-management/verification.md) |
| [backend/src/test/java/com/exportflow/progress/application/SseDeliveryUnitTest.java](../../backend/src/test/java/com/exportflow/progress/application/SseDeliveryUnitTest.java) | 测试／夹具；具体行为、红绿灯与对应标准见[任务映射](tasks.md#ac测试用例映射)及[证据](../../docs/validation/004-task-management/verification.md) |
| [backend/src/test/java/com/exportflow/worker/AssemblyRepairFileTest.java](../../backend/src/test/java/com/exportflow/worker/AssemblyRepairFileTest.java) | 测试／夹具；具体行为、红绿灯与对应标准见[任务映射](tasks.md#ac测试用例映射)及[证据](../../docs/validation/004-task-management/verification.md) |
| [backend/src/test/java/com/exportflow/worker/AssemblyRepairMySqlTest.java](../../backend/src/test/java/com/exportflow/worker/AssemblyRepairMySqlTest.java) | 测试／夹具；具体行为、红绿灯与对应标准见[任务映射](tasks.md#ac测试用例映射)及[证据](../../docs/validation/004-task-management/verification.md) |
| [backend/src/test/java/com/exportflow/worker/AssemblyRepairUnitTest.java](../../backend/src/test/java/com/exportflow/worker/AssemblyRepairUnitTest.java) | 测试／夹具；具体行为、红绿灯与对应标准见[任务映射](tasks.md#ac测试用例映射)及[证据](../../docs/validation/004-task-management/verification.md) |
| [backend/src/test/java/com/exportflow/worker/DataCollectionGuardUnitTest.java](../../backend/src/test/java/com/exportflow/worker/DataCollectionGuardUnitTest.java) | 测试／夹具；具体行为、红绿灯与对应标准见[任务映射](tasks.md#ac测试用例映射)及[证据](../../docs/validation/004-task-management/verification.md) |
| [backend/src/test/java/com/exportflow/worker/TaskPauseFileTest.java](../../backend/src/test/java/com/exportflow/worker/TaskPauseFileTest.java) | 测试／夹具；具体行为、红绿灯与对应标准见[任务映射](tasks.md#ac测试用例映射)及[证据](../../docs/validation/004-task-management/verification.md) |
| [backend/src/test/java/com/exportflow/worker/TaskPauseMySqlTest.java](../../backend/src/test/java/com/exportflow/worker/TaskPauseMySqlTest.java) | 测试／夹具；具体行为、红绿灯与对应标准见[任务映射](tasks.md#ac测试用例映射)及[证据](../../docs/validation/004-task-management/verification.md) |
| [frontend/src/features/export-tasks/__tests__/task-actions.test.tsx](../../frontend/src/features/export-tasks/__tests__/task-actions.test.tsx) | 测试／夹具；具体行为、红绿灯与对应标准见[任务映射](tasks.md#ac测试用例映射)及[证据](../../docs/validation/004-task-management/verification.md) |
| [frontend/src/features/export-tasks/__tests__/task-additional.test.tsx](../../frontend/src/features/export-tasks/__tests__/task-additional.test.tsx) | 测试／夹具；具体行为、红绿灯与对应标准见[任务映射](tasks.md#ac测试用例映射)及[证据](../../docs/validation/004-task-management/verification.md) |
| [frontend/src/features/export-tasks/__tests__/task-created.test.ts](../../frontend/src/features/export-tasks/__tests__/task-created.test.ts) | 测试／夹具；具体行为、红绿灯与对应标准见[任务映射](tasks.md#ac测试用例映射)及[证据](../../docs/validation/004-task-management/verification.md) |
| [frontend/src/features/export-tasks/__tests__/task-query.test.ts](../../frontend/src/features/export-tasks/__tests__/task-query.test.ts) | 测试／夹具；具体行为、红绿灯与对应标准见[任务映射](tasks.md#ac测试用例映射)及[证据](../../docs/validation/004-task-management/verification.md) |
| [frontend/src/features/export-tasks/__tests__/task-repair.test.tsx](../../frontend/src/features/export-tasks/__tests__/task-repair.test.tsx) | 测试／夹具；具体行为、红绿灯与对应标准见[任务映射](tasks.md#ac测试用例映射)及[证据](../../docs/validation/004-task-management/verification.md) |
| [frontend/src/features/export-tasks/__tests__/task-stream.test.tsx](../../frontend/src/features/export-tasks/__tests__/task-stream.test.tsx) | 测试／夹具；具体行为、红绿灯与对应标准见[任务映射](tasks.md#ac测试用例映射)及[证据](../../docs/validation/004-task-management/verification.md) |
| [frontend/src/features/export-tasks/__tests__/task-test-support.tsx](../../frontend/src/features/export-tasks/__tests__/task-test-support.tsx) | 测试／夹具；具体行为、红绿灯与对应标准见[任务映射](tasks.md#ac测试用例映射)及[证据](../../docs/validation/004-task-management/verification.md) |
| [frontend/src/features/export-tasks/components/task-filter.tsx](../../frontend/src/features/export-tasks/components/task-filter.tsx) | 任务页面／组件或订单创建通知接入；展示、交互与业务请求由下表函数负责 |
| [frontend/src/features/export-tasks/components/task-table.tsx](../../frontend/src/features/export-tasks/components/task-table.tsx) | 任务页面／组件或订单创建通知接入；展示、交互与业务请求由下表函数负责 |
| [frontend/src/features/export-tasks/hooks/use-task-actions.ts](../../frontend/src/features/export-tasks/hooks/use-task-actions.ts) | 任务列表、SSE生命周期或暂停操作状态，卸载时释放浏览器资源 |
| [frontend/src/features/export-tasks/hooks/use-task-list.ts](../../frontend/src/features/export-tasks/hooks/use-task-list.ts) | 任务列表、SSE生命周期或暂停操作状态，卸载时释放浏览器资源 |
| [frontend/src/features/export-tasks/hooks/use-task-stream.ts](../../frontend/src/features/export-tasks/hooks/use-task-stream.ts) | 任务列表、SSE生命周期或暂停操作状态，卸载时释放浏览器资源 |
| [frontend/src/features/export-tasks/models/export-task-schema.ts](../../frontend/src/features/export-tasks/models/export-task-schema.ts) | Zod协议边界：查询／暂停／六态摘要及字符串版本；未知状态安全展示 |
| [frontend/src/features/export-tasks/models/task-list-model.ts](../../frontend/src/features/export-tasks/models/task-list-model.ts) | 版本合并、成员变更判定与允许动作及中文不可用原因 |
| [frontend/src/features/export-tasks/models/task-query.ts](../../frontend/src/features/export-tasks/models/task-query.ts) | URL条件解析和序列化，不包含订单客户信息 |
| [frontend/src/features/export-tasks/pages/task-management-page.tsx](../../frontend/src/features/export-tasks/pages/task-management-page.tsx) | 任务页面／组件或订单创建通知接入；展示、交互与业务请求由下表函数负责 |
| [frontend/src/features/export-tasks/types/export-task.types.ts](../../frontend/src/features/export-tasks/types/export-task.types.ts) | 类型、record及枚举；携带控制版本、可信计数、修复阶段或协议字段；不执行I/O |
| [frontend/src/features/orders/__tests__/navigation.test.tsx](../../frontend/src/features/orders/__tests__/navigation.test.tsx) | 测试／夹具；具体行为、红绿灯与对应标准见[任务映射](tasks.md#ac测试用例映射)及[证据](../../docs/validation/004-task-management/verification.md) |
| [frontend/src/features/orders/__tests__/order-management.test.tsx](../../frontend/src/features/orders/__tests__/order-management.test.tsx) | 测试／夹具；具体行为、红绿灯与对应标准见[任务映射](tasks.md#ac测试用例映射)及[证据](../../docs/validation/004-task-management/verification.md) |
| [frontend/src/features/orders/pages/order-management-page.tsx](../../frontend/src/features/orders/pages/order-management-page.tsx) | 任务页面／组件或订单创建通知接入；展示、交互与业务请求由下表函数负责 |
| [frontend/src/shared/api/api-error.ts](../../frontend/src/shared/api/api-error.ts) | 依赖或运行／测试配置；实例身份、Redis外部连接、构建门禁及测试选择；不改共享容器配置 |
| [frontend/src/shared/api/api.ts](../../frontend/src/shared/api/api.ts) | 统一同源HTTP/SSE与下载路径，协议schema校验及错误归一 |
| [frontend/src/shared/events/task-created.ts](../../frontend/src/shared/events/task-created.ts) | 本标签事件与BroadcastChannel创建唤醒，卸载取消订阅 |
| [frontend/src/shared/styles/global.css](../../frontend/src/shared/styles/global.css) | 任务筛选布局、表格可滚动和信息按钮键盘焦点样式；无函数 |
| [frontend/vitest.config.ts](../../frontend/vitest.config.ts) | 依赖或运行／测试配置；实例身份、Redis外部连接、构建门禁及测试选择；不改共享容器配置 |
| [scripts/README.md](../../scripts/README.md) | 依赖或运行／测试配置；实例身份、Redis外部连接、构建门禁及测试选择；不改共享容器配置 |
| [scripts/run-project.ps1](../../scripts/run-project.ps1) | 依赖或运行／测试配置；实例身份、Redis外部连接、构建门禁及测试选择；不改共享容器配置 |
| [tests/backend/src/test/java/com/exportflow/testing/BoundaryFixture.java](../../tests/backend/src/test/java/com/exportflow/testing/BoundaryFixture.java) | 测试／夹具；具体行为、红绿灯与对应标准见[任务映射](tasks.md#ac测试用例映射)及[证据](../../docs/validation/004-task-management/verification.md) |
| [tests/backend/src/test/java/com/exportflow/testing/ProductionBackendFixture.java](../../tests/backend/src/test/java/com/exportflow/testing/ProductionBackendFixture.java) | 测试／夹具；具体行为、红绿灯与对应标准见[任务映射](tasks.md#ac测试用例映射)及[证据](../../docs/validation/004-task-management/verification.md) |
| [tests/backend/src/test/java/com/exportflow/testing/ProgressRedisFixture.java](../../tests/backend/src/test/java/com/exportflow/testing/ProgressRedisFixture.java) | 测试／夹具；具体行为、红绿灯与对应标准见[任务映射](tasks.md#ac测试用例映射)及[证据](../../docs/validation/004-task-management/verification.md) |
| [tests/backend/src/test/java/com/exportflow/testing/TaskManagementFixture.java](../../tests/backend/src/test/java/com/exportflow/testing/TaskManagementFixture.java) | 测试／夹具；具体行为、红绿灯与对应标准见[任务映射](tasks.md#ac测试用例映射)及[证据](../../docs/validation/004-task-management/verification.md) |

## 前端函数与调用关系

参数与返回类型采用源码类型；局部controller的结构在下方明确。组件内普通事件回调不构成外部协议。

| 函数 | 类型化签名 | 中文作用、流程与调用关系 |
| --- | --- | --- |
| TaskManagementPage | `(): ReactElement` | 组合useTaskList/useTaskStream/useTaskActions，显示加载／错误／空态、筛选、分页与暂停确认 |
| TaskFilter | `({query:TaskQuery,onQuery:(filter:{keyword:string,status?:TaskStatus})=>void,onReset:()=>void}):ReactElement` | 草稿提交才更新已提交条件；重置清空条件并回第一页 |
| TaskTable | `({rows:TaskListItem[],isBusy:boolean,onPause:(task:TaskListItem)=>void}):ReactElement` | 按getTaskActions呈现允许动作，成功下载使用exportTaskFileUrl；repairHint控制黄色图标 |
| repairHint | `(task:TaskListItem):string&#124;undefined` | 终态先移除提示，暂停状态优先，REBUILDING优先于再次CHECKING，直至成功采用 |
| parseTaskQuery | `(search:string):TaskQuery` | URLSearchParams解析合法keyword／status／page／pageSize，非法值归一 |
| writeTaskQuery | `(query:TaskQuery):string` | 稳定序列化已提交查询，构成查询缓存和响应隔离身份 |
| mergeTaskEvent | `(current:TaskListItem&#124;undefined,next:TaskListItem):TaskListItem` | BigInt比较version，保留新版本，即使其可信计数更低 |
| getTaskActions | `(task:TaskListItem):{statusLabel:string,canPause:boolean,canDownload:boolean,unavailableReason?:string}` | 以六态及allowedActions双重约束动作，未知状态不执行动作 |
| affectsTaskMembership | `(previous:TaskListItem&#124;undefined,next:TaskListItem,query:TaskQuery):boolean` | 判定新增或筛选成员变化；纯进度不发查询 |
| useTaskList | `():{query:TaskQuery,identity:string,snapshot?:TaskPage,isLoading:boolean,error:Error&#124;null,refresh:()=>Promise<TaskPage&#124;undefined>,accept:(task:TaskListItem)=>void,setQuery:(query:TaskQuery)=>void}` | 使用queryExportTasks；旧查询隔离，在途事件按版本合并，dirty补一次，越界回退最后页 |
| useTaskStream | `(options:{identity:string,hasActiveTasks:boolean,activityVersion:string,refresh:()=>Promise<TaskPage&#124;undefined>,accept:(task:TaskListItem)=>void}):{notice:string,refresh:()=>Promise<void>}` | openTaskEventStream单连接；ready补快照，idle核对connectionId与活动版本；最终快照失败保持关闭；联网／可见／创建通知唤醒 |
| useTaskActions | `(accept:(task:TaskListItem)=>void):{operation?:Operation,error:string,pause:(task:TaskListItem)=>Promise<void>,retry:()=>Promise<void>&#124;undefined}` | 冻结operationId和expectedControlVersion→pauseExportTask；UNKNOWN保留原请求，retry仅找回结果，不恢复执行 |
| queryExportTasks | `(query:TaskQuery,signal?:AbortSignal):Promise<TaskPage>` | 同源GET列表→schema校验；不轮询 |
| pauseExportTask | `(taskId:string,request:PauseRequest,signal?:AbortSignal):Promise<PauseResult>` | POST原operationId；协议校验和安全错误归一 |
| openTaskEventStream | `():EventSource` | 统一创建同源具名SSE入口，生命周期由useTaskStream拥有 |
| exportTaskFileUrl | `(taskId:string):string` | 编码taskId得到已有同源下载路径，不接收物理路径 |
| notifyTaskCreated | `(notice:TaskCreated):void` | 订单页已确认创建后本标签及跨标签唤醒；不追加执行请求 |
| subscribeTaskCreated | `(listener:(notice:TaskCreated)=>void):()=>void` | 订阅本标签及跨标签通知，返回释放函数 |

`Operation={taskId:string,request:PauseRequest,state:SUBMITTING/UNKNOWN,message?:string}`；其余字段见[任务类型](../../frontend/src/features/export-tasks/types/export-task.types.ts)。所有组件和hook返回值由TypeScript实际推断，不引入不存在的依赖注入接口。

## 后端类型化函数与目的

以下从实际公开函数和端口签名同步；每个文件的中文说明给出职责、流程与主要调用关系。构造函数只负责依赖注入。私有函数实现对应公开流程，不另引入跨模块契约。

### RuntimeConfiguration

组装生成器和修复服务、声明独立durable fanout及稳定实例队列

源码：[RuntimeConfiguration.java](../../backend/src/main/java/com/exportflow/config/RuntimeConfiguration.java)。

```java
public Object validateRuntime( RuntimeProperties properties, org.springframework.core.env.Environment environment, org.springframework.amqp.rabbit.connection.CachingConnectionFactory factory)
public Declarables exportTopology(RuntimeProperties properties)
public RabbitAdmin exportAdmin(ConnectionFactory factory)
public FanoutExchange progressExchange()
public Queue progressQueue(RuntimeProperties properties)
public Binding progressBinding(Queue progressQueue, FanoutExchange progressExchange)
ApplicationListener<ApplicationReadyEvent> initializeTopology(RabbitAdmin admin)
```

### RuntimeProperties

外部实例、缓存与执行配置校验；凭据外注入

源码：[RuntimeProperties.java](../../backend/src/main/java/com/exportflow/config/RuntimeProperties.java)。

```java
String getInstanceId()
void setInstanceId(String value)
String getProgressCachePrefix()
void setProgressCachePrefix(String value)
boolean isProgressEnabled()
void setProgressEnabled(boolean value)
Path getFileRoot()
void setFileRoot(Path value)
URI getManagementUrl()
void setManagementUrl(URI value)
boolean isConsumerEnabled()
void setConsumerEnabled(boolean value)
Duration managementTimeout()
void validate()
```

### CommandOutboxMapper

原命令SQL增加暂停状态保护，保留v1消息及原唯一键

源码：[CommandOutboxMapper.java](../../backend/src/main/java/com/exportflow/dispatch/infrastructure/persistence/CommandOutboxMapper.java)。

```java
boolean pausedSealed(String taskId)
Instant databaseNow()
int insert( String taskId, String eventId, Instant acceptedAt)
List<String> findDue(int limit)
CommandOutboxRecord lockByTask(String taskId)
Optional<CommandOutboxRecord> find(String eventId)
int reserveAttempt( String eventId, String owner, String token, int attempt, Instant leaseUntil, Instant nextAt)
int insertAttempt( String eventId, int attempt, String token, Instant now, Instant nextAt)
int markSent( String eventId, String token, Instant now)
int scheduleRetry( String eventId, String token, String safeCode, Instant nextAt)
int finishAttempt( String eventId, int attempt, String token, String outcome, String safeCode, Instant now)
List<String> findExpired(int limit)
int close( String eventId, String reason, Instant now)
```

### MyBatisCommandOutboxRepository

原命令授权与封存；暂停不得被迟到确认或投递扫描复活

源码：[MyBatisCommandOutboxRepository.java](../../backend/src/main/java/com/exportflow/dispatch/infrastructure/persistence/MyBatisCommandOutboxRepository.java)。

```java
public MyBatisCommandOutboxRepository( CommandOutboxMapper mapper, OutboxPersistenceMapper converter, ExportTaskRepository tasks, PlatformTransactionManager manager)
void insert(UUID taskId, UUID eventId, Instant acceptedAt)
ClaimBatch claimDue(String owner, int capacity, DispatchPolicy policy)
boolean isPublishAllowed(DispatchLease lease)
boolean markSent(DispatchLease lease, PublishEvidence evidence)
boolean scheduleRetry(DispatchLease lease, String safeCode, Duration delay)
Optional<CommandOutbox> find(UUID eventId)
List<UUID> findExpired(int limit)
DispatchOutcome expireOrClose(UUID taskId)
```

### TaskManagementApiMapper

协议与领域转换；解析白名单参数、字符串long和安全错误

源码：[TaskManagementApiMapper.java](../../backend/src/main/java/com/exportflow/exporttasks/api/TaskManagementApiMapper.java)。

```java
TaskQuery toQuery(MultiValueMap<String, String> input)
PauseRequest toPause(UUID taskId, PauseRequestDto dto)
TaskDto toDto(TaskListItem task)
TaskPageDto toDto(TaskPage page)
```

### TaskManagementController

HTTP列表和暂停入口；校验参数后调用服务，输出安全DTO

源码：[TaskManagementController.java](../../backend/src/main/java/com/exportflow/exporttasks/api/TaskManagementController.java)。

```java
public TaskManagementController( TaskManagementService service, TaskManagementApiMapper mapper, ObjectMapper json)
public ResponseEntity<TaskPageDto> query(@RequestParam MultiValueMap<String, String> values)
public ResponseEntity<PauseResultDto> pause(@PathVariable UUID taskId, HttpServletRequest request) throws java.io.IOException
```

### CreateExportTransaction

初次受理与原命令／初始进度通知同事务，幂等重放不重复创建

源码：[CreateExportTransaction.java](../../backend/src/main/java/com/exportflow/exporttasks/application/CreateExportTransaction.java)。

```java
public CreateExportTransaction( PlatformTransactionManager manager, ExportTaskRepository tasks, IdempotencyRepository keys, CommandOutboxRepository outbox, ExportRangeService ranges, CommandOutboxMapper clock, com.exportflow.progress.port.ProgressRepository progress)
CreateTaskResult commit(CanonicalSubmission submission)
TaskIdentity newTaskIdentity(Instant acceptedAt)
```

### TaskManagementService

查询与暂停用例；仓库异常分类为安全拒绝或结果不明

源码：[TaskManagementService.java](../../backend/src/main/java/com/exportflow/exporttasks/application/TaskManagementService.java)。

```java
public TaskManagementService(TaskManagementRepository tasks)
TaskPage query(TaskQuery query)
PauseResult pause(PauseRequest request)
```

### ExportFileModels

类型或既有端口的本期扩展；保持原调用边界，字段映射携带控制版本及可信进度。

源码：[ExportFileModels.java](../../backend/src/main/java/com/exportflow/exporttasks/domain/ExportFileModels.java)。

```java
public ExecutionStoppedException()
Optional<Order> next() throws IOException
void close() throws IOException
public DownloadHandle(DownloadMetadata metadata, InputStream input)
public DownloadMetadata metadata()
public InputStream input()
public void writeTo(OutputStream output) throws IOException
public void close() throws IOException
public InvalidExportDataException()
public InvalidExportDataException(String code)
public String code()
```

### ExportTask

类型或既有端口的本期扩展；保持原调用边界，字段映射携带控制版本及可信进度。

源码：[ExportTask.java](../../backend/src/main/java/com/exportflow/exporttasks/domain/ExportTask.java)。

```java
public ExportTask( UUID taskId, String taskNo, String scopeType, long totalCount, long processedCount, TaskStatus status, long version, Instant createdAt, Instant finishedAt, String errorCode, String errorSummary, String fileName, Long fileSize, UUID resultArtifactId)
ExportTask pending( UUID id, String number, ExportScope scope, List<ExportField> fields, long total, Instant acceptedAt)
void validateProgress(long processed)
```

### ExportTaskModels

类型或既有端口的本期扩展；保持原调用边界，字段映射携带控制版本及可信进度。

源码：[ExportTaskModels.java](../../backend/src/main/java/com/exportflow/exporttasks/domain/ExportTaskModels.java)。

```java
public byte[] requestHash()
public byte[] keyHash()
public String fileName()
public String configurationHash()
```

### TaskManagementModels

类型或既有端口的本期扩展；保持原调用边界，字段映射携带控制版本及可信进度。

源码：[TaskManagementModels.java](../../backend/src/main/java/com/exportflow/exporttasks/domain/TaskManagementModels.java)。

```java
public long offset()
public String pattern()
```

### ExportTaskPersistenceMapper

类型或既有端口的本期扩展；保持原调用边界，字段映射携带控制版本及可信进度。

源码：[ExportTaskPersistenceMapper.java](../../backend/src/main/java/com/exportflow/exporttasks/infrastructure/persistence/ExportTaskPersistenceMapper.java)。

```java
public ExportTaskPersistenceMapper(ObjectMapper json)
ExportTaskRecord toRecord(ExportTask task, ExportScope scope, List<ExportField> fields)
ExportTask toTask(ExportTaskRecord row)
TaskSummary toSummary(ExportTaskRecord row)
TaskSnapshot toSnapshot(ExportTaskRecord row, List<Long> orderIds)
```

### ExportTaskRecord

类型或既有端口的本期扩展；保持原调用边界，字段映射携带控制版本及可信进度。

源码：[ExportTaskRecord.java](../../backend/src/main/java/com/exportflow/exporttasks/infrastructure/persistence/ExportTaskRecord.java)。

```java
public byte[] requestHash()
```

### MyBatisArtifactRepository

READY控制屏障与完整可信检查；引用保护下清理，物理I/O在事务外

源码：[MyBatisArtifactRepository.java](../../backend/src/main/java/com/exportflow/exporttasks/infrastructure/persistence/MyBatisArtifactRepository.java)。

```java
public MyBatisArtifactRepository( ArtifactMapper artifacts, ExportTaskRepository tasks, TaskExecutionMapper executions, CheckpointMapper checkpoints, CommandOutboxMapper clock, ObjectMapper json, PlatformTransactionManager manager, AssemblyRepairMapper repairs)
Optional<ArtifactProof> findReady(UUID taskId, String hash)
Optional<ArtifactProof> findAdopted(UUID taskId)
LeaseWriteResult registerReady(ExecutionLease lease, ArtifactProof proof)
Optional<CleanupPermit> claimCleanup(StorageCandidate candidate, Instant ignoredHostTime)
void completeCleanup(CleanupPermit permit)
```

### MyBatisExportTaskRepository

类型或既有端口的本期扩展；保持原调用边界，字段映射携带控制版本及可信进度。

源码：[MyBatisExportTaskRepository.java](../../backend/src/main/java/com/exportflow/exporttasks/infrastructure/persistence/MyBatisExportTaskRepository.java)。

```java
public MyBatisExportTaskRepository( ExportTaskMapper mapper, ExportTaskPersistenceMapper converter, com.exportflow.progress.port.ProgressRepository progress)
void insert(ExportTask task, ExportScope scope, List<ExportField> fields)
Optional<TaskSummary> findSummary(UUID taskId)
Optional<TaskSnapshot> findSnapshot(UUID taskId)
ExportTask lockTask(UUID taskId)
boolean failDispatchTimeout(UUID taskId, Instant endedAt)
```

### MyBatisTaskManagementRepository

READ COMMITTED单SELECT列表快照；activity串行暂停与幂等回执同事务提交

源码：[MyBatisTaskManagementRepository.java](../../backend/src/main/java/com/exportflow/exporttasks/infrastructure/persistence/MyBatisTaskManagementRepository.java)。

```java
public MyBatisTaskManagementRepository( TaskManagementMapper mapper, TaskManagementPersistenceMapper converter, ExportTaskRepository tasks, CommandOutboxMapper commands, TaskExecutionMapper executions, ProgressRepository progress, ObjectMapper json, PlatformTransactionManager manager)
TaskPage findPage(TaskQuery query)
TaskListItem find(UUID id)
PauseResult pause(PauseRequest request)
```

### TaskManagementMapper

参数化分页投影及暂停操作记录；SQL不执行文件或网络I/O

源码：[TaskManagementMapper.java](../../backend/src/main/java/com/exportflow/exporttasks/infrastructure/persistence/TaskManagementMapper.java)。

```java
List<ListRow> findPage(TaskQuery query)
ListRow find(String taskId)
OperationRow operation(String id)
int insertOperation( String operation, String task, byte[] hash, long expected, String result)
int pause(String id, long expected)
int paused(String id)
int stopExecution(String id, long version)
List<String> pausing()
```

### TaskManagementPersistenceMapper

持久化记录转领域任务摘要，可信计数作为展示进度

源码：[TaskManagementPersistenceMapper.java](../../backend/src/main/java/com/exportflow/exporttasks/infrastructure/persistence/TaskManagementPersistenceMapper.java)。

```java
public TaskManagementPersistenceMapper(ObjectMapper json)
TaskListItem toItem(TaskManagementMapper.ListRow row)
```

### TaskManagementRepository

类型或既有端口的本期扩展；保持原调用边界，字段映射携带控制版本及可信进度。

源码：[TaskManagementRepository.java](../../backend/src/main/java/com/exportflow/exporttasks/port/TaskManagementRepository.java)。

```java
TaskPage findPage(TaskQuery query)
TaskListItem find(UUID taskId)
PauseResult pause(PauseRequest request)
```

### MyBatisOrderRepository

只读原订单；原scope与(prev,last]有界分段查询，稳定排序

源码：[MyBatisOrderRepository.java](../../backend/src/main/java/com/exportflow/orders/infrastructure/persistence/MyBatisOrderRepository.java)。

```java
public MyBatisOrderRepository(OrderMapper mapper, OrderPersistenceMapper converter)
long count(OrderFilter filter)
List<Order> findPage(OrderQuery query)
long countExisting(List<Long> ids)
OrderBatch readBatch(UUID taskId, ExportScope scope, OrderCursor after, int limit)
List<Order> readSegment( UUID taskId, ExportScope scope, OrderCursor previous, OrderCursor last, int limit)
long countMatching(OrderFilter filter, List<Long> ids)
```

### OrderMapper

只读订单分页及有界分段参数化SQL

源码：[OrderMapper.java](../../backend/src/main/java/com/exportflow/orders/infrastructure/persistence/OrderMapper.java)。

```java
List<OrderRecord> readSegment( String taskId, ExportScope scope, OrderCursor previous, OrderCursor last, int limit)
long count(OrderFilter filter)
List<OrderRecord> findPage(OrderQuery query)
long countExisting(List<Long> orderIds)
long countMatching(OrderFilter filter, List<Long> orderIds)
List<OrderRecord> readBatch( String taskId, ExportScope scope, OrderCursor after, int limit)
```

### OrderRepository

类型或既有端口的本期扩展；保持原调用边界，字段映射携带控制版本及可信进度。

源码：[OrderRepository.java](../../backend/src/main/java/com/exportflow/orders/port/OrderRepository.java)。

```java
long count(OrderFilter filter)
List<Order> findPage(OrderQuery query)
long countExisting(List<Long> orderIds)
long countMatching(OrderFilter filter, List<Long> orderIds)
OrderBatch readBatch(UUID taskId, ExportScope scope, OrderCursor after, int limit)
List<Order> readSegment( UUID taskId, ExportScope scope, OrderCursor previous, OrderCursor last, int limit)
```

### TaskEventController

同源SSE入口；订阅就绪后注册emitter并交给MVC

源码：[TaskEventController.java](../../backend/src/main/java/com/exportflow/progress/api/TaskEventController.java)。

```java
public TaskEventController( SseConnectionRegistry connections, com.exportflow.progress.application.ProgressConsumerReadiness readiness)
public SseEmitter events( jakarta.servlet.http.HttpServletRequest request, HttpServletResponse response)
```

### TaskStreamWebConfiguration

MVC异步Handler安装完成后激活真实发送，禁止把预初始化缓冲当作已发送

源码：[TaskStreamWebConfiguration.java](../../backend/src/main/java/com/exportflow/progress/api/TaskStreamWebConfiguration.java)。

```java
public TaskStreamWebConfiguration(SseConnectionRegistry connections)
public void addInterceptors(InterceptorRegistry registry)
public void afterConcurrentHandlingStarted( HttpServletRequest request, HttpServletResponse response, Object handler)
```

### ProgressConsumerReadiness

监听broker订阅确认；稳定ID冲突拒绝启动，未就绪返回503

源码：[ProgressConsumerReadiness.java](../../backend/src/main/java/com/exportflow/progress/application/ProgressConsumerReadiness.java)。

```java
public ProgressConsumerReadiness(RuntimeProperties properties)
public void consumed(ConsumeOkEvent event)
public void failed(ListenerContainerConsumerFailedEvent event)
public void verify()
boolean isReady()
```

### ProgressScheduler

独立通知投递、空闲裁决及心跳入口，不生成导出任务

源码：[ProgressScheduler.java](../../backend/src/main/java/com/exportflow/progress/application/ProgressScheduler.java)。

```java
public ProgressScheduler( TaskProgressService service, SseConnectionRegistry connections, RuntimeProperties properties)
public void dispatch()
public void checkIdle()
public void heartbeat()
```

### SseConnectionRegistry

每连接有界串行队列；ready先入队、实际send后完成future，错误或溢出关闭并释放

源码：[SseConnectionRegistry.java](../../backend/src/main/java/com/exportflow/progress/application/SseConnectionRegistry.java)。

```java
public SseConnectionRegistry( ProgressRepository progress, TaskManagementApiMapper mapper, ObjectMapper json)
void activate(SseEmitter emitter)
SseEmitter open()
CompletionStage<Boolean> broadcast(long activity, TaskListItem task)
void checkIdle()
void heartbeat()
void close(UUID id)
void closeAll()
CompletableFuture<Boolean> send(SseEmitter.SseEventBuilder event)
void close()
```

### TaskProgressService

领取通知→可选缓存→可靠发布→CAS确认；消费时回源并等待本地实际发送

源码：[TaskProgressService.java](../../backend/src/main/java/com/exportflow/progress/application/TaskProgressService.java)。

```java
public TaskProgressService( ProgressRepository progress, ProgressCache cache, ProgressPublisher publisher, TaskManagementRepository tasks, SseConnectionRegistry connections, RuntimeProperties properties)
DispatchSummary dispatchPending(int limit)
boolean onNotice(ProgressNotice notice)
```

### ProgressModels

类型或既有端口的本期扩展；保持原调用边界，字段映射携带控制版本及可信进度。

源码：[ProgressModels.java](../../backend/src/main/java/com/exportflow/progress/domain/ProgressModels.java)。

```java
public boolean reliable()
```

### RedisProgressCache

Lua按十进制字符串比较long版本；隔离键与索引、容量及TTL运行策略

源码：[RedisProgressCache.java](../../backend/src/main/java/com/exportflow/progress/infrastructure/cache/RedisProgressCache.java)。

```java
public RedisProgressCache( StringRedisTemplate redis, ObjectMapper json, TaskManagementApiMapper mapper, RuntimeProperties properties)
Optional<TaskListItem> get(UUID id, long minimumVersion)
void putIfNewer(TaskListItem task)
```

### RabbitProgressListener

独立手动确认消费者；onNotice实际发送成功才ACK，否则保留未确认消息

源码：[RabbitProgressListener.java](../../backend/src/main/java/com/exportflow/progress/infrastructure/messaging/RabbitProgressListener.java)。

```java
public RabbitProgressListener(TaskProgressService service, ObjectMapper json)
public void receive(Message message, Channel channel) throws java.io.IOException
```

### RabbitProgressTransport

只负责fanout发布、confirm与路由证据；不含消费监听

源码：[RabbitProgressTransport.java](../../backend/src/main/java/com/exportflow/progress/infrastructure/messaging/RabbitProgressTransport.java)。

```java
public RabbitProgressTransport(RabbitTemplate rabbit, ObjectMapper json)
PublishProof publish(ProgressNotice notice)
```

### MyBatisProgressRepository

MySQL活动行与进度Outbox实现；旧确认不消除更高pending版本

源码：[MyBatisProgressRepository.java](../../backend/src/main/java/com/exportflow/progress/infrastructure/persistence/MyBatisProgressRepository.java)。

```java
public MyBatisProgressRepository(ProgressMapper mapper, PlatformTransactionManager manager)
void lockActivity()
void recordChange(UUID id, long version)
List<NoticeLease> claimPending(String owner, int limit)
boolean markPublished(NoticeLease lease, PublishProof proof)
void release(NoticeLease lease)
IdleCandidate prepareIdle()
boolean confirmIdle(IdleCandidate candidate)
```

### ProgressMapper

注解SQL维护活动版本、通知租约与CAS，不存在ProgressMapper.xml

源码：[ProgressMapper.java](../../backend/src/main/java/com/exportflow/progress/infrastructure/persistence/ProgressMapper.java)。

```java
long lockActivity()
int advanceActivity()
long activity()
int record( String taskId, long version, long activity)
List<String> due(int limit)
NoticeRecord lock(String id)
int claim(String id, String owner, String token)
int published( String id, String token, String owner, long version)
int release(String id, String token)
IdleRecord idle()
```

### ProgressCache

可选缓存端口；按最低版本读取，旧写拒绝

源码：[ProgressCache.java](../../backend/src/main/java/com/exportflow/progress/port/ProgressCache.java)。

```java
Optional<TaskListItem> get(UUID taskId, long minimumVersion)
void putIfNewer(TaskListItem task)
```

### ProgressPublisher

发布最小版本通知并返回broker可靠确认

源码：[ProgressPublisher.java](../../backend/src/main/java/com/exportflow/progress/port/ProgressPublisher.java)。

```java
PublishProof publish(ProgressNotice notice)
```

### ProgressRepository

业务事务内登记通知；领取和确认使用独立短事务

源码：[ProgressRepository.java](../../backend/src/main/java/com/exportflow/progress/port/ProgressRepository.java)。

```java
void lockActivity()
void recordChange(UUID taskId, long version)
List<NoticeLease> claimPending(String owner, int limit)
boolean markPublished(NoticeLease lease, PublishProof proof)
void release(NoticeLease lease)
IdleCandidate prepareIdle()
boolean confirmIdle(IdleCandidate candidate)
```

### AssemblyRepairService

封存清单全段审计→可信计数→坏段有界重读及基线比对→原子替换；健康块保留

源码：[AssemblyRepairService.java](../../backend/src/main/java/com/exportflow/worker/application/AssemblyRepairService.java)。

```java
public AssemblyRepairService( ExportFileStorage storage, DataBlockCodec codec, AssemblyRepairRepository repairs, ExportDataCollectionService collection)
AssemblyAudit audit( GenerationContext context, RepairSnapshot snapshot, RepairIssue trigger, ExecutionGuard guard) throws IOException
void rebuild( GenerationContext context, AssemblyAudit audit, AttemptWorkspace workspace, ExecutionGuard guard) throws IOException
```

### ExecutionSupervisor

guard结合权威控制；USER_PAUSE停止推进并保留delivery等待安全回执

源码：[ExecutionSupervisor.java](../../backend/src/main/java/com/exportflow/worker/application/ExecutionSupervisor.java)。

```java
public ExecutionSupervisor( TaskExecutionService executions, RabbitListenerEndpointRegistry registry, RuntimeProperties properties, ExportFileGenerator generator)
public void start()
void onDelivery(DeliveryContext context, UUID taskId)
void attachChannel(String channelId, Runnable closer)
ExecutionGuard onExecution(DeliveryContext context, ExecutionLease lease)
public boolean check()
public StopReason stopReason()
void finish(String channelId)
boolean isAccepting()
LeaseWriteResult heartbeat(ExecutionLease lease)
void checkClaims()
void checkAttempts()
void stop(Duration grace)
public void close()
```

### ExportCommandHandler

原消息身份核对、认领与生成；优先处理PAUSE_STOPPED，只有持久封存后ACK

源码：[ExportCommandHandler.java](../../backend/src/main/java/com/exportflow/worker/application/ExportCommandHandler.java)。

```java
public ExportCommandHandler( ExportTaskRepository tasks, TaskExecutionService executions, ExportFileGenerator generator, ExecutionSupervisor supervisor, com.exportflow.dispatch.port.CommandOutboxRepository outbox)
DeliveryDecision handle(ExportCommandEnvelope command, DeliveryContext delivery)
DeliveryDecision handleGenerationResult(GenerationResult result, ExecutionLease lease)
```

### ExportDataCollectionService

按稳定游标逐块读取、关闭落盘后登记；旧完整块实体验证封存，不重复查询来源

源码：[ExportDataCollectionService.java](../../backend/src/main/java/com/exportflow/worker/application/ExportDataCollectionService.java)。

```java
public ExportDataCollectionService( CheckpointRepository checkpoints, OrderRepository orders, ExportFileStorage storage, AssemblyRepairRepository repairs, DataBlockCodec codec)
CompletedDataset collect(GenerationContext context, ExecutionGuard guard) throws IOException
VerifiedBlock rebuildSegment( GenerationContext context, SegmentRef segment, AttemptWorkspace workspace, ExecutionGuard guard) throws IOException
TrustedPrefix validatePrefix(CheckpointSnapshot snapshot, String config) throws IOException
```

### PauseReconciliationService

安全停止证明与启动扫描；只确认暂停，不重新生成

源码：[PauseReconciliationService.java](../../backend/src/main/java/com/exportflow/worker/application/PauseReconciliationService.java)。

```java
public PauseReconciliationService( TaskExecutionRepository executions, TaskManagementMapper tasks)
public void scanPausedRequests()
```

### TaskExecutionService

执行仓库用例协调与未知结果分类

源码：[TaskExecutionService.java](../../backend/src/main/java/com/exportflow/worker/application/TaskExecutionService.java)。

```java
public TaskExecutionService(TaskExecutionRepository executions)
ClaimResult claim(UUID taskId, String owner)
LeaseWriteResult renew(ExecutionLease lease)
LeaseWriteResult complete(ExecutionLease lease, GenerationResult result)
ClaimResult reconcile(UUID taskId, String owner)
ControlSnapshot findControl(UUID id)
PauseReconcileResult acknowledgePause(PauseProof proof)
```

### AssemblyRepairModels

类型或既有端口的本期扩展；保持原调用边界，字段映射携带控制版本及可信进度。

源码：[AssemblyRepairModels.java](../../backend/src/main/java/com/exportflow/worker/domain/AssemblyRepairModels.java)。

```java
public SegmentBaseline baseline()
String manifestDigest(List<SegmentBaseline> segments)
```

### ExecutionModels

类型或既有端口的本期扩展；保持原调用边界，字段映射携带控制版本及可信进度。

源码：[ExecutionModels.java](../../backend/src/main/java/com/exportflow/worker/domain/ExecutionModels.java)。

```java
public static ExecutionPolicy defaults()
public ExecutionLease( UUID taskId, String owner, long fence, Instant leaseUntil, long progressVersion)
public GenerationResult(GenerationKind kind, ArtifactProof artifact, SafeFailure error)
```

### ExcelFileValidator

检查最终XLSX结构、行数、表头、文本类型、字段格式与文件摘要；不重新查询来源

源码：[ExcelFileValidator.java](../../backend/src/main/java/com/exportflow/worker/infrastructure/excel/ExcelFileValidator.java)。

```java
public ExcelFileValidator(ExcelRowProjector projector)
ValidatedWorkbook validate( Path path, StorageRef ref, TaskSnapshot snapshot, ExecutionGuard guard) throws IOException
public void startElement(String uri, String local, String name, Attributes attributes) throws SAXException
public void characters(char[] chars, int start, int length)
public void endElement(String uri, String local, String name)
long businessRows()
```

### ExcelWorkbookAssembler

顺序读取有效数据块并写SXSSF，EOF校验与资源关闭，工作簿整体重建

源码：[ExcelWorkbookAssembler.java](../../backend/src/main/java/com/exportflow/worker/infrastructure/excel/ExcelWorkbookAssembler.java)。

```java
public ExcelWorkbookAssembler(ExportFileStorage storage, ExcelRowProjector projector)
StagingWorkbook assemble( GenerationContext context, CompletedDataset data, AttemptWorkspace workspace, ExecutionGuard guard) throws IOException
SXSSFWorkbook createWorkbook()
void flushCompletedRows(SXSSFSheet sheet, ExecutionGuard guard, int retainedRows) throws IOException
void closeWorkbook(SXSSFWorkbook workbook, OutputStream output) throws IOException
```

### PoiExportFileGenerator

资源作用域内收集／审计／局部重建／新Workbook装配／最终校验／发布登记；暂停优先

源码：[PoiExportFileGenerator.java](../../backend/src/main/java/com/exportflow/worker/infrastructure/excel/PoiExportFileGenerator.java)。

```java
public PoiExportFileGenerator( ExportFileStorage storage, ArtifactRepository artifacts, ExportDataCollectionService collection, ExcelWorkbookAssembler assembler, ExcelFileValidator validator, com.exportflow.worker.application.TaskExecutionService executions, AssemblyRepairRepository repairs, AssemblyRepairService repairService)
boolean isAvailable()
GenerationResult generate(GenerationContext context, ExecutionGuard guard)
```

### DataBlockCodec

块格式、SHA及规范内容基线编码；完整读至EOF后才能相信块内容

源码：[DataBlockCodec.java](../../backend/src/main/java/com/exportflow/worker/infrastructure/file/DataBlockCodec.java)。

```java
public DataBlockCodec(ObjectMapper json)
String checksumRows(List<Order> rows, int formatVersion)
VerifiedBlock verifyContent( BlockReader reader, BlockRef block, SegmentBaseline expected, ExecutionGuard guard) throws IOException
BlockDigest write(BlockHeader header, List<Order> rows, OutputStream output) throws IOException
BlockReader open(BlockRef ref, InputStream input) throws IOException
void readHeader() throws IOException
public Optional<Order> next() throws IOException
public void close() throws IOException
```

### AssemblyRepairMapper

分段清单、选择、预算及轮次历史SQL；有序锁段，终态收口修复历史

源码：[AssemblyRepairMapper.java](../../backend/src/main/java/com/exportflow/worker/infrastructure/persistence/AssemblyRepairMapper.java)。

```java
List<String> lockTaskRepairs(String id)
int finishRepairs(String id, String state)
SnapshotRow snapshot(String id)
int checking(String id)
String lockRepair(String id, long epoch)
List<SegmentRow> lockSegments(String id, long generation)
int insertSegment(SegmentRow row)
int seal( String id, int count, long rows, String digest)
int phase( String id, long rows, String phase, String state)
int begin(String id, long rows, String reason)
int insertRepair( String id, long epoch, int attempt, long checkpoint, long manifest, String reason)
int planSegment( String id, long epoch, int sequence, String block, long selection)
int auditSegment( String id, long generation, int sequence, long epoch, String state)
ReplacementRow lockReplacement( String id, long epoch, int sequence)
int selectBlock( String id, long generation, int sequence, long selection, String block, long epoch)
int replaced( String id, long epoch, int sequence, String block)
int addTrusted(String id, long rows)
int assembling(String id, long epoch)
```

### CheckpointMapper

断点及完整可信谓词；READY必须清单已封存、全段VERIFIED且计数与配置吻合

源码：[CheckpointMapper.java](../../backend/src/main/java/com/exportflow/worker/infrastructure/persistence/CheckpointMapper.java)。

```java
boolean fullyTrusted(String taskId, long generation)
CheckpointRecord find(String taskId)
CheckpointRecord lock(String taskId)
List<BlockRecord> findBlocks( String taskId, long generation)
Optional<BlockRecord> findBlock(String taskId, String blockId)
int initialize(String taskId, String hash)
int insertBlock(BlockRecord record)
int updateCheckpoint( String taskId, long count, Instant time, long id)
int startGeneration(String taskId)
```

### ExecutionLeaseRecord

类型或既有端口的本期扩展；保持原调用边界，字段映射携带控制版本及可信进度。

源码：[ExecutionLeaseRecord.java](../../backend/src/main/java/com/exportflow/worker/infrastructure/persistence/ExecutionLeaseRecord.java)。

```java
ExecutionLease toLease()
boolean accepts(ExecutionLease lease, Instant now)
```

### MyBatisAssemblyRepairRepository

持久3轮预算及修复计划，提交可信进度与通知，同blockId找回避免重复计数

源码：[MyBatisAssemblyRepairRepository.java](../../backend/src/main/java/com/exportflow/worker/infrastructure/persistence/MyBatisAssemblyRepairRepository.java)。

```java
public MyBatisAssemblyRepairRepository( AssemblyRepairMapper mapper, ExportTaskRepository tasks, TaskExecutionMapper executions, CheckpointMapper checkpoints, CommandOutboxMapper commands, ProgressRepository progress, ObjectMapper json, PlatformTransactionManager manager)
RepairSnapshot load(UUID id)
RepairWriteResult markChecking(ExecutionLease lease)
RepairWriteResult sealManifest( ExecutionLease lease, RepairSnapshot expected, List<SegmentBaseline> baselines)
RepairWriteResult beginRepair(ExecutionLease lease, AssemblyAudit audit)
RepairWriteResult commitReplacement( ExecutionLease lease, RepairPlan plan, SegmentRef segment, VerifiedBlock block)
Optional<VerifiedBlock> findReplacement(UUID id, long epoch, int sequence)
RepairWriteResult markAssembling(ExecutionLease lease, AssemblyAudit audit)
AssemblyRepairMapper.SegmentRow segmentRow( UUID id, long generation, SegmentBaseline baseline, String blockId)
```

### MyBatisCheckpointRepository

块与段基线原子提交；新代重置当前可信计数并通知，已封存不走旧重建

源码：[MyBatisCheckpointRepository.java](../../backend/src/main/java/com/exportflow/worker/infrastructure/persistence/MyBatisCheckpointRepository.java)。

```java
public MyBatisCheckpointRepository( CheckpointMapper checkpoints, TaskExecutionMapper executions, ExportTaskRepository tasks, CommandOutboxMapper clock, ObjectMapper json, PlatformTransactionManager manager, com.exportflow.progress.port.ProgressRepository progress, AssemblyRepairMapper segments, com.exportflow.exporttasks.port.ExportFileStorage storage, com.exportflow.worker.infrastructure.file.DataBlockCodec codec)
CheckpointSnapshot find(UUID taskId)
CheckpointWriteResult commitBlock( ExecutionLease lease, CheckpointSnapshot expected, BlockRef ref)
CheckpointWriteResult commitBlock( ExecutionLease lease, CheckpointSnapshot expected, com.exportflow.worker.domain.AssemblyRepairModels.VerifiedBlock verified)
Optional<BlockRef> reconcileBlock(UUID taskId, UUID blockId)
CheckpointWriteResult startGeneration( ExecutionLease lease, long expectedVersion, TrustedPrefix prefix)
```

### MyBatisTaskExecutionRepository

认领、控制、续租、安全暂停及终态事务；READY采用再次验证，完成修复历史

源码：[MyBatisTaskExecutionRepository.java](../../backend/src/main/java/com/exportflow/worker/infrastructure/persistence/MyBatisTaskExecutionRepository.java)。

```java
public MyBatisTaskExecutionRepository( ExportTaskRepository tasks, CommandOutboxMapper outbox, TaskExecutionMapper executions, ArtifactMapper artifacts, CheckpointMapper checkpoints, com.fasterxml.jackson.databind.ObjectMapper json, PlatformTransactionManager manager, com.exportflow.exporttasks.infrastructure.persistence.TaskManagementMapper controls, com.exportflow.progress.port.ProgressRepository progress, AssemblyRepairMapper repairs)
ClaimResult claim(UUID taskId, String owner, ExecutionPolicy policy)
LeaseWriteResult renew(ExecutionLease lease)
LeaseWriteResult complete(ExecutionLease lease, GenerationResult result)
ClaimResult find(UUID taskId)
ControlSnapshot findControl(UUID id)
PauseReconcileResult acknowledgePause(PauseProof proof)
PauseReconcileResult reconcilePause(UUID id)
```

### TaskExecutionMapper

执行身份、控制版本、状态与进度SQL；旧执行不得越过暂停屏障

源码：[TaskExecutionMapper.java](../../backend/src/main/java/com/exportflow/worker/infrastructure/persistence/TaskExecutionMapper.java)。

```java
com.exportflow.worker.domain.ExecutionModels.ControlSnapshot control(String taskId)
int setControl(String taskId, long version)
ExecutionLeaseRecord find(String taskId)
ExecutionLeaseRecord lock(String taskId)
int insertLease( String taskId, String owner, Instant now, Instant until, Instant deadline, Instant recoveryAt)
int takeOver( String taskId, String owner, Instant now, Instant until, Instant deadline, Instant recoveryAt)
int markRunning(String taskId)
int renew( String taskId, Instant now, Instant until, Instant recoveryAt)
int releaseLease(String taskId, Instant now)
int completeTask( String taskId, String status, Instant now, String artifactId, String fileName, Long size, String code, String message)
int advanceProgressVersion(String taskId)
int updateProgress(String taskId, long processed)
```

### AssemblyRepairRepository

修复短事务端口，执行控制身份与清单／选择版本CAS

源码：[AssemblyRepairRepository.java](../../backend/src/main/java/com/exportflow/worker/port/AssemblyRepairRepository.java)。

```java
RepairSnapshot load(UUID taskId)
RepairWriteResult markChecking(ExecutionLease lease)
RepairWriteResult sealManifest( ExecutionLease lease, RepairSnapshot expected, List<SegmentBaseline> baselines)
RepairWriteResult beginRepair(ExecutionLease lease, AssemblyAudit audit)
RepairWriteResult commitReplacement( ExecutionLease lease, RepairPlan plan, SegmentRef segment, VerifiedBlock block)
Optional<VerifiedBlock> findReplacement(UUID taskId, long epoch, int sequence)
RepairWriteResult markAssembling(ExecutionLease lease, AssemblyAudit audit)
```

### CheckpointRepository

类型或既有端口的本期扩展；保持原调用边界，字段映射携带控制版本及可信进度。

源码：[CheckpointRepository.java](../../backend/src/main/java/com/exportflow/worker/port/CheckpointRepository.java)。

```java
CheckpointWriteResult commitBlock( com.exportflow.worker.domain.ExecutionModels.ExecutionLease lease, CheckpointSnapshot expected, com.exportflow.worker.domain.AssemblyRepairModels.VerifiedBlock block)
CheckpointSnapshot find(UUID taskId)
CheckpointWriteResult commitBlock( ExecutionLease lease, CheckpointSnapshot expected, BlockRef ref)
Optional<BlockRef> reconcileBlock(UUID taskId, UUID blockId)
CheckpointWriteResult startGeneration( ExecutionLease lease, long expectedVersion, TrustedPrefix prefix)
```

### ExecutionGuard

类型或既有端口的本期扩展；保持原调用边界，字段映射携带控制版本及可信进度。

源码：[ExecutionGuard.java](../../backend/src/main/java/com/exportflow/worker/port/ExecutionGuard.java)。

```java
boolean check()
default com.exportflow.worker.domain.ExecutionModels.StopReason stopReason()
```

### TaskExecutionRepository

类型或既有端口的本期扩展；保持原调用边界，字段映射携带控制版本及可信进度。

源码：[TaskExecutionRepository.java](../../backend/src/main/java/com/exportflow/worker/port/TaskExecutionRepository.java)。

```java
ClaimResult claim(UUID taskId, String owner, ExecutionPolicy policy)
LeaseWriteResult renew(ExecutionLease lease)
LeaseWriteResult complete(ExecutionLease lease, GenerationResult result)
ClaimResult find(UUID taskId)
ControlSnapshot findControl(UUID taskId)
PauseReconcileResult acknowledgePause(PauseProof proof)
PauseReconcileResult reconcilePause(UUID taskId)
```

## 选择性修复增量设计

调用关系：PoiExportFileGenerator → collect／sealManifest → markChecking → AssemblyRepairService.audit；有异常时beginRepair先提交较低可信进度与通知，再rebuild，commitReplacement切换坏段选择；全段可信后markAssembling，新Workbook装配、EOF和候选文件校验，registerReady及complete分别核验fullyTrusted。成功采用同事务结束修复历史。

`AssemblyRepairService.rebuild(context:GenerationContext,audit:AssemblyAudit,workspace:AttemptWorkspace,guard:ExecutionGuard):void`只重读损坏段；`OrderRepository.readSegment(taskId:UUID,scope:ExportScope,previous:OrderCursor,last:OrderCursor,limit:int):List<Order>`返回原范围内订单，实际Java参数详见对应端口。健康数据块保留，SXSSF工作文件整体重建。替换提交响应丢失按同blockId找回，保守拒绝过期选择；不接受无登记孤儿文件。

## 测试先行实际夹具与职责

新增及受影响测试文件见逐文件表；TaskManagementFixture使用真实隔离schema／vhost与只读订单，ProgressRedisFixture限定UUID前缀并精确清理键及索引。BoundaryProbe只注入边界故障，不实现生产选择性修复或通知算法。测试ID、标准反向引用与红绿灯在[任务](tasks.md#测试进度与证据)和[验证记录](../../docs/validation/004-task-management/verification.md)维护。性能、容量及计时用例均不执行；已有历史红灯不覆盖。
