# 0.4实施细节补充

日期：2026-10-11。此附属文件补充既有授权范围的内部拆分，不增加任务页、SSE、Redis或新产品行为。核心[spec](spec.md)、[plan](plan.md)、[tasks](tasks.md)及[函数附录](function-design-reference.md)继续承担原职责；实际执行进度见[证据](../../docs/validation/002-order-management/implementation-progress.md)。

## 内部目录增补

| 文件 | 职责与调用关系 |
| --- | --- |
| `backend/src/main/java/com/exportflow/config/RuntimeProperties.java` | 外部文件根、管理URL及消费角色，启动校验本机资源配置 |
| `backend/src/main/java/com/exportflow/config/RuntimeHealthMapper.java` | 仅返回未发送/已发送等待及超期执行计数，不查询客户值或载荷 |
| `backend/src/main/java/com/exportflow/config/RuntimeMonitor.java` | 独立5秒运行观察与10分钟、20条许可清理，告警30秒限频，状态变化立即记录 |
| `backend/src/main/java/com/exportflow/dispatch/infrastructure/messaging/RabbitManagementClient.java` | 共用管理HTTP凭据、2秒子预算和总deadline，不重定向；服务发布核验与运行观察 |
| `backend/src/main/java/com/exportflow/worker/infrastructure/file/ScratchFileStrategy.java` | 进程启动固定POI策略；线程作用域携带task/fence身份，崩溃scratch可经数据库许可清理 |
| `backend/src/test/java/com/exportflow/worker/WorkbookLifecycleTest.java` | 真实POI5.5正常关闭回归；不加载应用或中间件 |
| `backend/src/test/java/com/exportflow/worker/BlockResourceUnitTest.java` | 损坏块打开失败时保留主异常并抑制关闭异常 |
| `backend/src/test/java/com/exportflow/worker/DataCollectionGuardUnitTest.java` | 失权后禁止开始/继续前缀读取 |
| `backend/src/test/java/com/exportflow/worker/CheckpointInvariantTest.java` | 真实MySQL验证调用方断点计数不能覆盖权威前缀 |
| `backend/src/test/java/com/exportflow/worker/DeliveryIsolationUnitTest.java` | 屏障验证停止消费者先于关闭通道，以及失权后的晚登记通道回调 |
| `backend/src/test/java/com/exportflow/RuntimeMonitorUnitTest.java` | 告警限频、即时恢复、安全错误及不改业务事实 |
| `tests/backend/src/test/java/com/exportflow/testing/BrowserDemoMain.java` | 仅演示夹具；随机端口真实后端、隔离MySQL/MQ；调用独立脚本初始化110000行 |
| `backend/pom.xml` | 正式生产/测试依赖与工具门禁，Checkstyle锁10.26.1；jacocoArgLine独立于test.jvmArgs；Spotless纳入tools及生产测试 |
| `scripts/check-order-performance.ps1` | Wrapper串行执行真实性能，test.jvmArgs固定512MiB；UTF-8 BOM兼容Windows PowerShell5.1 |
| `docs/validation/002-order-management/code-review.md` | 开发后review发现、影响、红绿证据及剩余项 |

主模型仍按[原附录](function-design-reference.md)分层；新增record按领域/SQL记录/协议各自保存。删除无本期调用方的ExportTaskQueryService、ExportProgressCache、RedisExportProgressCache空占位；后续规格再定义。

## 类型化函数与辅助职责

| 函数 | 中文职责、流程与调用 |
| --- | --- |
| `RuntimeProperties.validate(): void` | 核验绝对文件根、管理URL合法且本机，不含userinfo/query/fragment；非法安全拒绝 |
| `RuntimeConfiguration.validateRuntime(properties: RuntimeProperties, environment: Environment, factory: CachingConnectionFactory): Object` | 配置Bean初始化阶段复核本机MySQL及schema白名单、隔离vhost；只输出安全错误，不连接示例资源 |
| `RuntimeHealthMapper.counts(): Counts` | DB时间只读聚合；Counts含waitingCount/sentCount/overdueExecutionCount，类型均long |
| `RuntimeMonitor.start(): void / sample(): void / stop(): void` | 启动独立观察与清理；sample分别读取DB和broker，UNKNOWN只告警，不改事实；stop停止新周期 |
| `RuntimeMonitor.alert(code: String, active: boolean, count: long): void` | 单调时钟限频；首次/状态改变即时记录，同类活跃告警最多30秒一次；仅稳定code与计数 |
| `RabbitManagementClient.get(path: String, deadline: long): JsonNode` | 使用剩余总预算与2秒管理预算的较小值；固定Basic来源、禁止重定向；失败安全异常 |
| `RabbitManagementClient.snapshot(): BrokerSnapshot` | 队列与nodes实际信息；BrokerSnapshot含messages/unacknowledged(long)、consumers(int)、alarm(boolean)，不伪造缺失值 |
| `RabbitManagementClient.close(): void` | 关闭共用HTTP客户端；不将客户端资源关停放在每次发布中 |
| `ScratchFileStrategy.use(workspace: AttemptWorkspace): Scope` | 设置本线程作用域，Scope.close恢复原上下文；不逐任务修改全局POI策略 |
| `ScratchFileStrategy.createTempFile(prefix: String, suffix: String): File` | 有执行上下文时服务端UUID创建` scratch/{process}/{task}/{fence-owner}/{uuid}.tmp`；无上下文沿POI默认策略供生命周期测试 |
| `TaskSnapshot.configurationHash(): String / fileName(): String` | 固定长度前缀摘要范围/有序字段；人类文件名仅用权威受理时间、任务编号。projector转交，不重复业务命名规则 |
| `DownloadHandle.input(): InputStream / close(): void` | 允许框架/资源探针观察原输入；close幂等，writeTo自动关闭输入而不关闭框架输出 |
| `ExportDataCollectionService.validatePrefix(snapshot: CheckpointSnapshot, config: String, guard: ExecutionGuard): TrustedPrefix` | private辅助；入口、逐块和逐行检查guard，再校验连续元数据及计数；公开两参数函数保持兼容并委托本方法 |
| `DataBlockCodec.open(ref: BlockRef, input: InputStream): BlockReader` | 读取头失败时关闭输入，关闭异常追加到原异常，不改变损坏/IO分类 |
| `ExecutionSupervisor.attachChannel(channelId: String, closer: Runnable): void` | 与失权同步登记回调；已经停止时在锁外立即关闭；新执行计时先初始化再发布lease |
| `RabbitMqExportCommandListener.isolate(id: String): void` | private辅助；告警、独立线程停受影响容器、finally关闭该交付；无容器时直接关闭，不ACK/复制/删消息 |
| `OrderTable(props: OrderTableProps): ReactElement` | isSelectionDisabled:boolean禁用行及表头勾选；isDisabled:boolean冻结排序和选择；页面全选/反选使用同一有效结果判断 |

其他内部帮助函数只拆解原附录流程。消费者增加eventId/type/version与原Outbox对应核对；命令identity不匹配按既定坏消息隔离策略处理。READY登记/成功采用同时核对任务配置、断点代次、行数、字节、SHA、完整逻辑引用和权威人类文件名。

## 文件与清理约束落实

POI5.5的close自带dispose，首次dispose结果先核验，再尝试close；主异常保留，关闭异常追加，不把已经删除后的false判成生成失败。SAX因guard停止包装的IOException保持UNKNOWN分类。

BLOCK_PART、SCRATCH仅用于崩溃残留的服务端逻辑引用。SCRATCH的owner保存`{processUUID}_{executionOwner}`，resolve只拆合法UUID/owner，删除仍需DB许可；对应fence活跃时保护。BLOCK和ARTIFACT引用形式稳定，不含无关attempt。CLEANING在DB许可已裁决后阻止晚登记，清理中断时以同token重做单文件删除/完成登记。扫描轮换位置，避免永久引用的前20个文件让后续孤儿永远不可见。

运行与告警参数不变，来源[决策记录](decision-record.md#文件及执行参数)。日志本地滚动保留7天；HTTP仅暴露health/info，不暴露env/configprops。Wrapper增加经Maven Central SHA512复核的分发包SHA256。工程工具变化仍须通过[构建门禁](../../docs/rules/build.md)，当前不宣称已通过。

## 共享服务夹具

| 文件/函数 | 参数、返回与职责 |
| --- | --- |
| `SharedServices.mysql(): Mysql；rabbit(): Rabbit` | 无参；只构造资源句柄，不启动容器 |
| `Mysql.start(): void throws Exception；close(): void throws Exception` | 无参；内存读取管理员凭据，创建唯一schema及schema专属用户；close只删除自己创建的schema/用户 |
| `Mysql.getAdminConnection(): Connection throws SQLException` | 无参；测试DDL/行锁观察用管理员连接，业务保持普通schema账号 |
| `Mysql.createRestrictedUser(restrictedPassword: String): void throws SQLException` | 创建唯一权限边界测试账号并记录创建成功；只回收确认属于本次的资源，各项回收失败不阻断其他清理 |
| `Rabbit.start(): void throws Exception；close(): void throws Exception` | 无参；唯一vhost与临时管理测试用户、权限和TCP桥；回收仅本次资源 |
| `Rabbit.execInContainer(args: String...): Result throws Exception` | 仅允许本次vhost策略/测试账号标签及只读超时查询；拒绝其他操作。策略通过管理API HTTP/1.1提交JSON，避免Windows CLI引号与broker HTTP/2兼容问题 |
| `Rabbit.suspendConnections(): void；resumeConnections(): void` | 无参；关闭测试TCP桥所有socket/重新接受转发，不停止broker |
| `TcpBridge.connect(local: Socket): void；pump(from: Socket,to: Socket): void` | 真实AMQP转发；虚拟线程、同步管理socket，故障关闭本测试连接 |
| `ExecutionSupervisor.close(): void` | ContextClosedEvent在SmartLifecycle停止前失效guard，PreDestroy兜底；stop幂等 |
| `ProductionBackendFixture.closeProduction(): void throws Exception` | 回收实际上下文、HTTP、专属vhost/schema/账号后清理Mockito inline状态；正式及harness按类串行使用新的512MiB进程 |
| `RuntimeIsolationUnitTest`、`ShutdownOrderUnitTest` | 严格资源命名拒绝边界与真实Spring停机顺序回归；对应[运行隔离](../../docs/product/acceptance-criteria.md#ac-061)、[执行隔离](../../docs/product/acceptance-criteria.md#ac-068) |


## 既有订单来源调整

2026-10-12按负责人要求删除独立造数工具及订单SQL夹具，迁移仅创建直接读取exportflow.orders的视图与必要任务表。ProvidedOrdersTest检查来源映射与写权限拒绝；查询和文件准确性使用原始数据，详细职责、权限及数据不足口径见[来源设计](provided-data.md)与[复验记录](../../docs/validation/002-order-management/provided-data.md)。

2026-10-12负责人明确取消性能测试，停止当前JVM及后续专项，不补测其余API场景。原性能定义保留，未取得完整指标不标达标，实际中止和专属资源回收见[来源记录](../../docs/validation/002-order-management/provided-data.md)。

## 性能批次候选调度修正

真实来源五次串行测量发现已RUNNING任务的未SENT旧Outbox会占满无锁候选LIMIT，使新的PENDING任务无法领取。候选SQL在LIMIT之前联合任务表筛选PENDING，锁内仍按task→outbox顺序重新核对权威状态／期限／token，不增加发布授权。RuntimeHealthMapper.counts只将仍PENDING且等待命令超过60秒的记录计入OUTBOX_WAITING。两个真实MySQL回归分别验证旧候选不阻塞新命令和等待告警计数；原失败性能批次保留，见[来源记录](../../docs/validation/002-order-management/provided-data.md)。

## 本地启动入口

负责人使用`./make dev`遇到入口不存在，补根[make.cmd](../../make.cmd)兼容其命令；无业务变化的低风险启动封装。它无自定义函数，只查找已安装GNU Make、固定项目工作目录、透传参数和退出码；实际命令仍由Makefile及scripts/run-project.ps1维护。

2026-10-12负责人要求新增Make启动脚本；按低风险现有工具链封装实施，不扩展业务或重新开启性能验证。根[Makefile](../../Makefile)调度前台PowerShell命令；[配置模板](../../.env.example)声明外部连接键，不含真实凭据；[run-project.ps1](../../scripts/run-project.ps1)处理白名单配置、前置检查、固定版本依赖、打包和进程启动；[脚本说明](../../scripts/README.md)记录Windows安装、命令、停止及资源前置。

| 函数 | 参数、返回及职责 |
| --- | --- |
| Import-LocalConfiguration | 无参，无返回；读取.env.local允许的键，剥离整值引号后写入当前进程环境，不执行文本；入口在动作前调用 |
| Assert-Command | Name: string → void；核对工具可找到，否则抛出错误 |
| Assert-BackendConfiguration | 无参，无返回；核对6项必需连接键非空且非占位，缺失仅报告键名；check/backend调用 |
| Find-Npm | 无参 → string；取得npm.exe或npm.cmd的实际路径，兼容本机nvm及标准Node安装 |
| Invoke-CheckedCommand | Command: string, Arguments: string[] → void；原样传递参数，等待前台进程，非零退出抛错；构建/启动共用 |
| Invoke-Frontend | Arguments: string[] → void；核对Node/npm，通过npm exec固定pnpm10.34.6，再执行frontend命令 |
| Build-Backend | 无参，无返回；核对Java，Wrapper跳过测试打包当前源码；build/backend调用，正式verify门禁独立保留 |

review修正数组参数显式绑定、兼容npm.exe，并为递归Make路径加引号。配置解析错误不回显配置值；缺少配置时先失败，不构建/启动后端。Make并行时任一服务失败，其余前台进程由Ctrl+C停止，说明中记录此行为。核验及资源限制见[启动入口记录](../../docs/validation/002-order-management/startup-entry.md)。

本机实际配置已按负责人要求准备；.env.local存外部真实凭据并被忽略，.env.example仅保留示例。backend动作额外给本次JVM的jdk.net.unixdomain.tmpdir指定已验证的backend/target完整路径，避免本机用户临时目录NIO pipe连接失败。AMQP连接URI不附加vhost路径，后端application.yml负责固定/export-flow；不新增配置键或业务代码。应用资源权限和启动烟测见[本机配置准备](../../docs/validation/002-order-management/startup-entry.md#本机配置准备)。

## 项目内导出运行目录

2026-10-13负责人要求将生成文件改到当前项目根目录，明确旧文件已删除、不用复制，由其自行重启后端，不新建规格但须修正原有规格。本次为低风险运行配置调整，不新增函数或业务测试。`.env.local`将EXPORT_FLOW_FILE_ROOT设为项目export-data绝对路径；application.yml的默认值改为user.dir/export-data，标准启动入口固定项目工作目录；[配置示例](../../.env.example)说明默认目录及可选的绝对路径覆盖；[Git忽略规则](../../.gitignore)排除整个运行目录。原规格、计划、任务、数据模型、Excel方案、决策记录、quickstart、README、运行规则及ADR已同步。目录已创建；未迁移旧文件、未启动或重启后端。实际配置核验见[记录](../../docs/validation/002-order-management/startup-entry.md#项目内导出运行目录)。
