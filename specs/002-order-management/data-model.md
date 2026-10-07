# 数据模型与类型设计

**状态**：草稿；以下是设计，不是已生成迁移或代码。关联 [plan](plan.md)、[函数设计](function-design.md)、[HTTP](contracts/http-api.md)、[消息](contracts/export-command.md)、[任务](tasks.md)。本文件不复制验收定义。

## 通用规则

ID 在 JSON 中为字符串，Java 内部订单 ID 为正 Long（协议十进制字符串），任务及事件 UUID 为字符串；不可把 64 位 ID 转成 JavaScript Number。时间使用 UTC 秒级 Instant／ISO `Z`，DB DATETIME(0) 且会话 UTC。金额类型 MoneyDecimal 为非负、整数最多 16 位、固定两位十进制字符串；Java BigDecimal，SQL DECIMAL(18,2)。所有可选项省略，不混用 null、空串与空数组。页面页码从 1 开始，PageSize 为 20、50、100。

## 前后端语义模型

| 类型 | 字段与约束 |
| --- | --- |
| OrderStatus / SalesChannel | 使用 PRD §8 枚举；输入未知值拒绝；输出未知值展示“未知” |
| SortDirection | `ASC`、`DESC`；默认 DESC；time 与 ID 同方向 |
| OrderFilter | keyword?: string≤100；orderNo?: string≤64；status?: OrderStatus；channel?: SalesChannel；createdFrom?/createdTo?: UtcInstant；minAmount?/maxAmount?: MoneyDecimal；两侧范围有序 |
| OrderQuery | filter: OrderFilter；page: positive integer（默认 1，最多 1000000）；pageSize: PageSize（默认 20）；sortDirection: SortDirection（默认 DESC） |
| Order / OrderDto | id、orderNo、customerName、customerPhone、status、channel、amount、currency=`CNY`、region、createdAt；DTO 和领域分别定义，不能共用同一类 |
| OrderViewModel | key: OrderId；其余展示字段为中文／格式化字符串，原始 ID 保留；金额不经浮点计算 |
| PageResult<T> | items: T[]、page、pageSize、total: nonnegative integer（Java Long，JSON 在安全整数范围内，超过拒绝而不截断） |
| ExportField | 有序允许值 orderNo、customerName、customerPhone、status、channel、amount、currency、region、createdAt；1–9 项且不重复；默认列表按页面业务列顺序 |
| ExportScope | SELECTED: orderIds: OrderId[1..200000]，忽略排序去重后校验每个 ID 存在；FILTERED: filter: OrderFilter，不含分页／排序／前端计数；两种范围互斥 |
| CreateExportRequest | scope: ExportScope；fields: ExportField[]；额外字段拒绝；服务端不能接受 total/status/taskId |
| FrozenExportSubmission | request: 深度不可变 CreateExportRequest；idempotencyKey；localState=`SUBMITTING`/`UNKNOWN`/`CONFIRMED`/`REJECTED`；仅页面内存；结果不明不能换键 |
| ExportTask / TaskSummary | taskId、taskNo、scopeType、totalCount、fields、status、processedCount、progressPercent、createdAt、finishedAt?、errorCode?、errorSummary?、fileName?、fileSize?；PENDING 为 0，FAILED 保留实际进度；回执不展示内部路径／键／lease |
| ApiError | code、message、traceId、fieldErrors?: {field,message}[]、details?: {currentCount,limit}、outcome?: `REJECTED`/`UNKNOWN`；前端创建失败只依据可信 outcome 分类 |

## 前端自有状态与参数类型

- OrderFilterFormValues：OrderFilter 的业务输入，其中 createdFromLocal/createdToLocal 为 `yyyy-MM-dd HH:mm:ss` 字符串，金额原始字符串；提交验证后转 UTC。OrderFilterFormProps：values、isLoading、onQuery(values):void、onReset():void。
- OrderTableProps：rows: OrderViewModel[]、isLoading、selectedIds: ReadonlySet<OrderId>、sortDirection、onToggle(id,isSelected):void、onSort(direction):void。
- OrderSelectionState：ids: ReadonlySet<OrderId>；SelectionAction 为 TOGGLE(id,isSelected)、SELECT_PAGE(ids)、INVERT_PAGE(ids)、CLEAR；不会存整个订单数据。
- ExportDialogState：isOpen、scope?: ExportScope、fields: ExportField[]；ExportConfigDialogProps：state、isSubmitting、isUnknown、onFieldsChange(fields):void、onConfirm():void、onClose():void、onRetry():void。
- OrdersState：query: OrderQuery、pageResult?: PageResult<OrderDto>、loadState=`loading`/`success`/`empty`/`error`、error?: ApiError；UseOrdersResult 增加 submitFilter(values)、reset()、changePage(page,pageSize)、changeSort(direction)、refresh()，返回 Promise<void> 或 void 依函数设计；不复制 TanStack Query 的服务端缓存。
- UseOrderSelectionResult：ids、count、dispatch(action):void；UseOrderExportResult：dialog、submission?、open(scope):void、setFields(fields):void、close():void、confirm():Promise<void>、retryUnknown():Promise<void>。
- OrderExportDependencies：createTask(request,key,signal?):Promise<TaskSummary>、navigate(path):void、clearSelection():void、newKey():string；用于可控测试，不访问缓存或存储。
- UseOrderExportResult 还提供 resetDraft():void，只有可编辑态允许显式重置；OrderManagementPage.handleReset 协调 query、selection、dialog 的重置，不让 useOrders 暗中操作其他Hook状态。SUBMITTING/UNKNOWN时冻结筛选、选择、排序、分页与导出字段的交互，先确认当前意图结果；刷新／离开不冒充取消后台任务。
- RequestOptions：method=`GET`/`POST`、body?:unknown、headers?:Record<string,string>、signal?:AbortSignal、timeoutMs?:number（默认10000）。UseOrderQueryStateResult：query、setQuery(query,mode=`push`):void；keyword 独立内存。ApiRequestError：error:ApiError、status?:number、kind=`http`/`network`/`timeout`/`aborted`/`protocol`。
- 组件 props 仅声明所需回调；回调命名统一由函数清单列出，简单渲染匿名回调不新增隐蔽业务逻辑。

## 持久化关系（设计逻辑表）

| 表／模型 | 主要字段、索引与生命周期 |
| --- | --- |
| orders / OrderRecord | id BIGINT PK；order_no UNIQUE（二进制精确比较）；姓名／手机号 VARCHAR；status、channel VARCHAR；amount DECIMAL(18,2)；currency CHAR(3)；region；created_at DATETIME(0)；(created_at,id)、(status,created_at,id)、(channel,created_at,id) 设计索引；keyword 前置通配可能全扫，须性能实测，不假定索引能解决 |
| export_tasks / ExportTaskRecord | task_id CHAR(36) PK、task_no UNIQUE、scope_type、filter_json?、fields_json（保序）、total_count、processed_count（持久高水位）、status、version、created_at、finished_at?、error_code?、error_summary?、file_name?、file_size?、result_artifact_id?；(created_at,task_id) 索引；成功与所选产物同事务，任务和文件永久保留 |
| export_task_orders | (task_id,order_id) 联合 PK，SELECTED 的去重固定 ID 集合；FK 到任务与订单；FILTERED 不插入该表；关联字段在任务持久模型内说明，不创建独立业务实体 |
| export_idempotency / IdempotencyRecord | key_hash BINARY(32) PK，canonical_version、request_hash、task_id UNIQUE FK；不存可用于日志泄漏的原键，数据库唯一约束裁决；本期无过期删除 |
| command_outbox / CommandOutboxRecord | event_id PK、task_id FK、type=`EXPORT_REQUESTED`、schema_version=1、status=`READY`/`LEASED`/`SENT`/`CLOSED`、accepted_at、dispatch_deadline、next_attempt_at、attempt_no、lease_owner?、lease_token?、lease_until?、sent_at?、closed_at?、close_reason?、last_error_code?；UNIQUE(task_id,type)；到期／调度索引；不删除未发送或关闭记录 |
| dispatch_attempts / DispatchAttemptRecord | (event_id,attempt_no) PK、lease_token、started_at、crash_retry_at、finished_at?、outcome?、safe_error?；领取事务即插入，崩溃也有历史；保留期未决，先不自动清理 |
| task_execution / ExecutionLeaseRecord | task_id PK FK、owner_id、fence BIGINT、lease_until、heartbeat_at、progress_version、attempt_started_at、attempt_deadline、recovery_count、next_recovery_at；获得接管权才扣预算，执行权和异常恢复子集已接受，实施仍受两次门禁 |

任务与 Outbox、幂等记录、SELECTED 关联同事务持久化。使用FK RESTRICT，禁止级联清理永久记录。SQL CHECK 保护计数 0≤processed≤total≤200000，终态字段一致性；JSON 形状仍由应用校验。代码只读订单，初始化使用独立入口，运行用户只SELECT订单及读写任务相关表，禁止DDL/DROP/TRUNCATE；独立migrator做迁移、seed入口写合成订单，凭据外部提供，实际授权待执行验证。

## 规范化与范围冻结

规范化版本 1：文本 trim，空值删除；keyword 统一小写（Locale.ROOT，匹配亦不区分大小写）；orderNo 保留大小写；时间转 UTC 秒；金额去无义前导零后固定两位；枚举使用正式值；SELECTED ID 去重按数值升序；fields 顺序保留；固定属性序列生成 UTF-8 后 SHA-256。FILTERED 不含 page/pageSize/sortDirection。所有规则须契约测试验证；变更摘要规则须新版本，不能用新摘要误判旧键。

创建时用相同 OrderFilter 查询规则在事务内计数；SELECTED 去重数量与存在数量必须一致，否则整笔拒绝。FILTERED 保存条件即可固定集合的前提为初始化后订单只读；一旦允许订单写入必须另评审真正数据快照。字段映射本期由ExcelRowProjector实现，顺序、表头、原值、定点金额及香港时间遵循[Excel方案](excel-design.md)。

## 状态与数据库裁决

| 状态／转换 | 条件与持久动作 |
| --- | --- |
| 创建 → PENDING | 同事务写任务／幂等／范围／Outbox；accepted_at 取 DB 时间，deadline=accepted_at+10 分钟；确认提交后响应 |
| READY／过期 LEASED → LEASED | task→outbox 固定锁序；在 DB 当前时间未超过截止、到 next_attempt_at、旧 lease 过期且 crashRetryAt 到期时领取；预留尝试、保存 token 和退避 |
| LEASED → SENT | 发布 confirm + 正确目标路由 + 合规策略证明；凭据有效。若仍 PENDING 且未 SENT，达到 deadline 不准标记；已 RUNNING／终态不能被错误回退 |
| LEASED → READY | 同有效凭据写安全错误、next_attempt_at，释放 lease；失败回写不改变 SENT/CLOSED，不清空历史 |
| PENDING → FAILED，Outbox → CLOSED | 未 SENT、未 CLOSED 且到 deadline；同事务写错误、结束时间、撤销 lease；扫描不受退避／租约阻止 |
| 已开工／终态且原 Outbox 未 SENT | 停止补发；仅有可靠发布证明才 SENT，否则 CLOSED 原因 AUTHORITY_NO_LONGER_NEEDS_COMMAND；不伪造 MQ 确认 |
| PENDING → RUNNING（拟实现） | 与超时／SENT 使用同一 task→outbox 锁序；未 SENT 时 deadline 内才可认领；原子写 ExecutionLease；事务提交明确后才调用生成器 |
| RUNNING → RUNNING（拟实现） | owner/fence/有效 lease/预期 progressVersion 条件下单调进度和续租；失权或提交不明停止，不回写旧身份 |
| RUNNING → SUCCEEDED／FAILED（拟实现） | 当前执行权下校验READY产物、成功与唯一引用同事务；明确失败保留实际进度。到期恢复预算耗尽可在合法接管裁决事务失败；UNKNOWN不写终态 |

SENT仍PENDING不自动失败、不补投；任务终态不可回退，数据库不可读不ACK。本次实现真实生成，恢复方案已接受，启用消费者前须完成两次开发门禁及能力验证。原未确认命令重交付后，过期且持久退避到期的RUNNING可原子取得新owner/fence；无RUNNING扫描补投。SQL依据锁内DB时间与全部谓词裁决，已定READ COMMITTED/SKIP LOCKED须真实验证。

## 后端函数使用的自有类型

- PageRequest(page:int,pageSize:int)、PageSlice<T>(items:List<T>,page:int,pageSize:int,total:long)、TaskId(UUID)、EventId(UUID)、OwnerId(String)、LeaseToken(UUID) 为值类型；机械构造／访问器不展开。
- TaskIdentity(taskId:UUID,eventId:UUID,taskNo:String) 为本次创建的稳定身份；DispatchAttemptRecord、ExecutionLeaseRecord 分别独立映射相应表，不与领域类型共用。PageResult/OrderResponse 等传输类型在前后端各自定义，record 构造／访问器可机械生成。
- IdempotencyDbRecord 是 ExportTaskRecord 文件内的独立持久记录，字段与 export_idempotency 对应；基础设施显式转为 IdempotencyRecord，不把数据库记录直接返回领域端口。SQL mapper 输出为数据库记录或标量，端口输出为领域值。
- OrderQueryDto、OrderResponse、CreateTaskRequestDto、TaskSummaryResponse 为传输模型；OrderRecord、ExportTaskRecord、CommandOutboxRecord、DispatchAttemptRecord、ExecutionLeaseRecord 为基础设施记录；映射函数单列，禁止共用。
- CreateExportCommand(scope:ExportScope,fields:List<ExportField>,key:String)、CanonicalSubmission(version:int,requestHash:byte[],keyHash:byte[],scope:ExportScope,fields:List<ExportField>)、CreateTaskResult(task:TaskSummary,isReplay:boolean)；返回前必须确认已提交。
- ExportRangeCheck(total:long,scope:ExportScope)、TaskSnapshot(task:ExportTask,scope:ExportScope,fields:List<ExportField>)、IdempotencyMatch=`ABSENT`/`SAME`/`CONFLICT`。
- DispatchPolicy(scanInterval:Duration,batchSize:int,maxConcurrency:int,publishBudget:Duration,leaseDuration:Duration,backoffBase:Duration,backoffCap:Duration)、DispatchLease(eventId,taskId,owner,token,attemptNo,leaseUntil,deadline)、PublishEvidence(confirmAck:boolean,correctRoute:boolean,policyVerified:boolean)、PublishResult(kind=`CONFIRMED`/`FAILED`/`UNKNOWN`,evidence?,safeCode?)。
- DispatchOutcome=`SENT`/`RETRY_SCHEDULED`/`STALE`/`CLOSED`/`UNKNOWN`；ClaimBatch(leases:List<DispatchLease>)；DispatchScanResult(claimed,sent,retried,closed)；DeadlineScanResult(scanned,failed,closed)。
- ExportCommandEnvelope(eventId,taskId,type,schemaVersion,traceId)；DeliveryContext(channelId:String,deliveryTag:long,receivedAt:Instant) 仅本地进程上下文，不持久伪造 broker 状态；DeliveryDecision=`ACK`/`HOLD`/`CLOSE_CHANNEL`，由基础设施监听器执行。
- ExecutionPolicy(leaseDuration:Duration,heartbeatInterval:Duration,claimTimeout:Duration,maxAttemptDuration:Duration,maxRecoveryTakeovers:int,recoveryBackoffBase:Duration,recoveryBackoffCap:Duration,shutdownGrace:Duration) 为已定参数，具体值见[Excel方案](excel-design.md#本期具体建议参数)；ExecutionLease(taskId,owner,fence,leaseUntil,progressVersion)、ClaimResult(kind=`ACQUIRED`/`TERMINAL`/`BUSY`/`UNKNOWN`,lease?,snapshot?)、LeaseWriteResult=`APPLIED`/`STALE`/`UNKNOWN`。
- GenerationContext(snapshot:TaskSnapshot,lease:ExecutionLease)、GenerationResult(kind=`READY`/`KNOWN_FAILURE`/`UNKNOWN`,artifact?:ArtifactProof,error?:SafeFailure)、ArtifactProof(artifactId:UUID,taskId:UUID,dataGeneration:long,configurationHash:String,artifactRef:StorageRef,fileName:String,size:long,rowCount:long,checksum:String)、SafeFailure(code,message)、ProgressUpdate(processed:long,expectedVersion:long)。本期真实产物校验后才有证明，无未实现分支。
- ProgressSink.report(update:ProgressUpdate):LeaseWriteResult、ExecutionGuard.check():boolean为生成回调。块登记同事务推进真实进度，ProgressSink只核对已提交事实并本地通知；Redis/SSE不在本期，不能把回调等同前端连续更新。

## 调度时间与适配调用补充

退避纯函数返回Duration，Outbox重试与Worker恢复退避都由DB当前时间计算持久期限。mapper每方法对应明确statement；CheckpointRepository.commitBlock同事务调用insertBlock/updateCheckpoint/advanceProgressVersion/updateProgress，不能在尚未保存数据时独立report推进进度。终态同事务completeTask/adopt/releaseLease，find只读不隐式申请执行锁。

## 本期新增断点、产物及清理模型

| 表／模型 | 字段／约束 |
| --- | --- |
| export_checkpoints / CheckpointRecord | task_id PK、data_generation、checkpoint_version、verified_prefix_count、cursor_created_at?、cursor_order_id?、processed_high_water、configuration_hash、format_version；检查点是连续块清单，计数不代表独立可恢复位置 |
| export_data_blocks / BlockRecord | block_id PK、task_id、data_generation、sequence、previous_cursor_json?、last_cursor_json、row_count、byte_size、checksum、storage_ref、format_version、configuration_hash、created_by_owner/fence；unique(task,generation,sequence)、unique(storage_ref)；引用跨重启保留 |
| export_artifacts / ArtifactRecord | artifact_id PK、task_id、data_generation、configuration_hash、storage_ref UNIQUE、row_count、byte_size、checksum、file_name、state=READY/ADOPTED/CLEANING/CLEANED、created_by_owner/fence、ready_at、adopted_by_fence?、cleanup_token?；result_artifact_id FK，成功结果唯一 |
| export_cleanup_candidates | candidate_id PK、逻辑引用唯一、task/attempt身份、kind=BLOCK/STAGING/SCRATCH/ARTIFACT、observed_at、state、cleanup_token?；仅候选扫描不授权删除，claimCleanup串行核对所有引用 |

固定锁序为task→outbox（需要命令裁决时）→execution→checkpoint→artifact/candidate→attempt；任一省略锁只能向后，不反向加锁。文件读取与校验在锁外完成，锁内重核身份/版本/配置及候选资格。READY采用和清理互斥，CLEANING不可采用；成功引用及所有仍可恢复块禁止清理。

| 自有类型 | 字段／行为约束 |
| --- | --- |
| OrderCursor / OrderBatch | cursor(createdAt:Instant,id:Long)；batch(rows:List<Order>,lastCursor:OrderCursor?,isEnd:boolean)，空批isEnd=true |
| BlockHeader / BlockRef / BlockDigest | header(version:int,configHash:String,generation:long,sequence:int,previousCursor?,lastCursor,rowCount:int)；ref(blockId:UUID,header,storageRef:StorageRef,byteSize:long,checksum:String)；digest(bytes:long,rows:int,checksum:String) |
| BlockReader | AutoCloseable流式next():Optional<Order>；有界行编码；最终关闭与格式验证不能被跳过 |
| CheckpointSnapshot | taskId、generation、version、cursor?、verifiedPrefixCount、processedHighWater、configurationHash、blocks:List<BlockRef> |
| TrustedPrefix / CompletedDataset | prefix(generation,version,blocks,cursor?,verifiedCount,isSafeToRedo:boolean)；dataset(taskId,generation,configHash,blocks,totalRows)，只完整可信数据可装配 |
| CheckpointWriteResult | kind=APPLIED/STALE/UNKNOWN，snapshot?；未知提交按blockId核对 |
| StorageRef / AttemptWorkspace | ref(kind,taskId,identity:UUID,generation?,attemptFence?,owner?)全部由服务端生成；workspace(taskId,owner,fence,namespace:String)为逻辑命名空间，不输出物理Path |
| StagingWorkbook / ValidatedWorkbook | staging(ref,artifactId,rows,fileName)；validated(ref,size,checksum,rowCount,configHash)，证明来自流式校验 |
| DownloadMetadata / DownloadHandle | metadata(fileName,mimeType,length)；handle(metadata,input:InputStream)负责有界writeTo与close；禁止控制器持有DB事务直到下载完 |
| StorageCandidate / CleanupPermit | candidate(id,ref,taskId,owner?,fence?,observedAt)；permit(candidateId,token,ref)为DB核对后授予身份，不包含用户请求路径 |

文件物理目录和POI资源策略见[Excel方案](excel-design.md#文件身份就绪登记及下载)。数据库持久记录与领域值单独定义，机械字段转换在适配器内；BlockRef和ArtifactProof不直接充当SQL返回类型。新增schema为设计，不在本次生成迁移。

## 定稿运行配置的类型与归属

下列字段由既有计划中的RuntimeProperties定义，框架配置在application.yml/application-local.yml；不额外生成业务类。值的唯一清单见[决策记录](decision-record.md#文件及执行参数)；本文限定类型和责任。已定义DispatchPolicy/ExecutionPolicy保持独立嵌套值类型。

| 配置字段 | 类型 | 内容／约束 |
| --- | --- | --- |
| apiBindAddress / apiPort / frontendProxyTarget | String / int / URI | 本机地址、端口及代理目标，禁止启动时开放外网 |
| datasourceUrl / datasourceCredentialRef / brokerCredentialRef | String / String / String | 连接及外部凭据引用，不输出密码；schema/vhost白名单 |
| fileRoot / scratchRoot | Path / Path | 外部受控目录、同卷与reparse检查，不能来自HTTP路径 |
| sourceBatchSize / sxssfRowWindow / streamBufferBytes | int / int / int | 正数及有界内存配置，SXSSF每块末刷行/缓冲 |
| maxBlockLineBytes / maxBlockBytes / maxHttpBodyBytes / maxCommandBytes | long / long / long / int | 行、块、请求与命令字节预算，超限明确失败/拒绝 |
| compressScratch / useSharedStrings / consumerEnabled | boolean / boolean / boolean | true / false / 演示角色true，启动须能力验证 |
| consumerConcurrency / consumerPrefetch / poolMaximum / poolMinimumIdle | int / int / int / int | 有界执行容量与连接池；执行槽独立通道 |
| connectionAcquireTimeout / lockWaitTimeout / managementTimeout / healthCheckInterval | Duration / Duration / Duration / Duration | 连接、锁、管理请求和监督预算，各自独立计时 |
| orphanGrace / cleanupInterval / cleanupBatch | Duration / Duration / int | 清理仅DB授权候选，不按年龄删除引用文件 |
| brokerConsumerAckTimeout / alertRateLimit / logRetention | Duration / Duration / Duration | broker配置核对、告警限频与日志保留；不删除原命令 |

frontendProxyTarget供Vite配置设计使用，实际后端不依赖前端地址推进任务；Maven/框架工具版本由[依赖版本表](dependency-versions.md)管理，无配置代码在本次生成。

初始化工具SeedOrders.java在同一文件定义嵌套记录SeedOutcome(kind=CREATED/VERIFIED,count:int,seed:long,algorithmVersion:int=1)与OrderSeedRow(id:long,orderNo:String,customerName:String,phone:String,status:String,channel:String,amount:BigDecimal,currency:String,region:String,createdAt:Instant)。已有数据按固定算法逐行核对，不新增seed元数据表、不以同数量假定一致；工具权限仅限获准的export_flow或测试schema，应用启动不自动调用。
