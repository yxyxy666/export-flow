# 暂停执行与可靠通知契约

**版本**：0.3　**状态**：规格0.3及测试阶段已于2026-10-17获负责人批准，当前生产实施；保留测试先行证据。
关联：[规格](../spec.md)、[计划](../plan.md)、[任务](../tasks.md)、[模型](../data-model.md)、[候选ADR](../../../docs/adr/0010-task-pause-control-barrier.md)。


规格0.3由负责人明确批准；测试阶段及六类缺口处理方案随后获批，生产实施已授权，见[审批记录](../tasks.md#审批记录)。

## 暂停屏障

1. 按固定锁序检查PauseRequest。先核对operationId和请求摘要，已提交同操作直接找回；不同载荷409。新操作按expectedControlVersion与当前状态校验，不要求匹配不断变化的进度version。
2. PENDING尚未认领：写PAUSED，递增控制／任务／活动版本，封存原Outbox为CLOSED/USER_PAUSED并撤销投递lease，登记操作回执与进度通知，同事务提交。
3. RUNNING：写PAUSING，递增控制版本并登记回执/通知。从提交起claim/renew/commitBlock/startGeneration/registerReady/adopt/complete必须同时校验status、owner/fence和controlVersion，不允许新的业务登记。
4. Worker在读批次、落块、装配与发布边界感知权威控制。用户暂停后关闭资源，保留已登记数据，产出PAUSE_STOPPED及PauseProof，不把IOException转为业务失败；不可读控制状态则停止推进并返回UNKNOWN。
   续租因用户暂停被拒绝时先记录USER_PAUSE并停止guard推进，保留当前delivery等待生成线程关闭与停止回执，不因该拒绝立即ACK或重交付。失权或数据库不可读仍按原监督协议协调停止／关闭，重交付遇到PAUSING不得开工。
5. 安全停止事务要求PAUSING、proof.requestedControlVersion与当前请求一致、owner/fence及可信checkpoint一致。提交PAUSED、pauseAckVersion、fence推进、执行权释放、原命令封存与通知。旧执行lease的控制版本已失效，但它作为停止回执身份仍可被校验，不能用于新业务写入。
6. 原delivery只有核对权威PAUSED及该命令已持久封存后才ACK；暂停提交不明、DB不可读或PAUSING未确认均HOLD。暂停ACK是独立的持久责任转移，不声称文件完成，原命令不删除。
7. Worker崩溃后的PAUSING对账只在DB租约／fence裁决证明没有有效登记者时完成PAUSED；不重新执行、不转失败。到期计时路径只设计不测试。PAUSED在本期永久保持，用户继续需另行规格。

暂停先提交则旧控制身份不能完成；完成先提交则pause409并展示权威终态。未登记隔离文件可遗留但不能当断点／产物；清理不能删除被已暂停任务断点、READY或成功记录引用的文件。

## 认领、投递及旧消息

原schemaVersion=1、EXPORT_REQUESTED与task/type唯一键保持。发布授权、markSent、重试投递及到期扫描增加状态／暂停封存谓词；迟到confirm不能重开CLOSED/USER_PAUSED。只在PENDING且原命令允许时认领；PAUSING不生成且不ACK，PAUSED核对暂停证明及注册命令后ACK。

畸形消息、未知版本或未登记eventId仍按原坏消息规则保留消息并停止消费槽；不因“可能是暂停”而忽略。RUNNING的异常接管保留既有行为，PAUSED与PAUSING不得走生成器恢复。暂停不重置现有恢复预算、不清空可信数据。

## 通知与缓存

可观察业务变更同事务推进task.version、activityVersion并upsert progress_outbox；原事务先取得activity锁，网络操作不得放入事务。

独立dispatcher领取通知，读取权威最新摘要，尝试Redis版本写入，再发布durable fanout最小ProgressNotice。可靠confirm后按lease与发布版本CAS推进publishedVersion，保留并发新pendingVersion；失败保留通知，不发布执行命令。

各稳定API实例使用独立durable queue、手动ACK、重启复用同队列。通知版本能被缓存满足时读取缓存，否则回源MySQL；终态与空闲裁决始终权威读。广播使用本地串行发送器，等待本轮选定连接发送结果后再决定ACK，不能仅入内存队列即ACK。发送失败关闭该连接并保留本次通知，后续重交付重复事件可按版本丢弃；DB不可读也保留通知。无连接时可在权威核对后确认。

Redis前缀export-flow:progress:，TTL10分钟、活跃缓存键上限10000沿用既有ADR。Lua原子版本比较和容量裁决，已满跳过新缓存并回源；不调整共享全局maxmemory。TTL/性能专项本轮免验，旧缓存拒绝与故障回源仍验证。

订单创建成功仅广播taskId/taskNo至本浏览器；订单页仍留页，不追加任务状态查询或SSE。正常空闲关闭后的其他设备新任务不自动唤醒，沿用已有限制。

## Worker停止回执的实际入口

现有handler在generate返回后先执行guard.check，false便HOLD。新流程必须先处理PAUSE_STOPPED，停止确认不要求旧lease仍有业务写权限；否则用户暂停恰好使guard失效，回执永远到不了确认事务。

generate返回的GenerationResult新增resourcesClosed标记：只有本次读流、写流、Workbook及装配资源全部成功关闭才true；关闭失败为false，不能提供PauseProof，只能UNKNOWN/HOLD。返回READY或KNOWN_FAILURE后、complete前若guard首次观察到USER_PAUSE，handler使用resourcesClosed=true这一事实和最新权威checkpoint构造停止证明，再走同一停止入口，不能丢掉这个窄竞态，也不能把READY重新提交为成功。不得从单纯guard=false或任意IOException推导“资源已关闭”。生成器的停止异常分类和finally关闭必须共同满足此约束；兼容旧三参数构造默认false，不自动生成停止证据。

监督器将推进能力与delivery/channel有效性分开：USER_PAUSE停guard但保留delivery；heartbeat和checkAttempts不再重复对所有非APPLIED结果调用close。checkClaims遇到PAUSING不进入生成，交给暂停对账；PAUSED须核对原eventId与持久封存，不能只看状态ACK。LOST_AUTHORITY、UNKNOWN、通道断开和停机仍沿用原协调停止，不产生成功停止证明。finish只在处理器结束并完成ACK或明确通道处置后移除delivery。

## 停止确认及启动对账

安全回执在activity→task→原command→execution→checkpoint顺序下，核对同一任务、暂停请求controlVersion、原owner/fence与最新持久checkpointVersion。停止确认不要求旧lease尚未到期；身份和fence仍一致即可接受资源关闭证明。已由另一对账事务完成PAUSED时，只读核对pauseAckVersion/原命令封存返回既有结果，不重复推进fence。若fence或checkpoint不符，重新权威读取，不能替换成伪造证明。

PauseReconciliationService由既有ExecutionSupervisor.start在启动消费之前调用一次，之后在原监督周期中调用；不增加未经确认的调度时长。候选扫描只读查PAUSING的taskId，逐任务按固定锁序重新裁决，不先锁execution。无执行记录可直接封存；有记录则只有原lease_until按数据库时间已到期，或收到同owner/fence的可靠停止回执才可推进fence并封存。不能用“控制版本已失效”证明旧执行线程已经退出，也不能用record.accepts=false替代lease到期判断；PAUSING本来就禁止业务登记。

崩溃到期对账建立的是持久隔离、无有效登记者的停止边界，不声称进程内任意阻塞I/O已物理结束。旧线程最多遗留未登记隔离文件，不能登记断点、READY或终态；原引用仍保护。到期分支按负责人指令免验，启动调用、扫描状态限制及停止回执正常路径仍按非计时功能验证。

## 通知拓扑及资源边界

拟用独立durable fanout `exportflow.progress.v1`、每实例durable classic queue `exportflow.progress.v1.<instanceId>`，消息persistent、mandatory及相关confirm，绑定在开放SSE前就绪。队列无TTL/expiry/自动删除/drop-head，不修改原命令拓扑或共享策略。instanceId稳定、限定安全字符且每活跃API实例唯一；同ID重复启动须检测并拒绝，避免竞争消费漏广播。拟通过该实例专属队列的exclusive consumer检测重复，不使用会删除队列的exclusive queue。缺少绑定或拓扑不可用时不宣称通知发布成功。

SSE每连接采用有界串行发送队列；超过配置上限或发送失败时关闭连接、移除监听、保留未成功广播通知，客户端重连快照恢复。上限256个待发送事件已随0.3规格接受，不是性能目标；功能测试注入小容量验证溢出处置，不做压力、吞吐或容量测量。心跳与ready/idle占用同一串行通道，不允许无界堆积。

通知Outbox只有SUCCEEDED/FAILED且pendingVersion=publishedVersion、无未完成lease时才可清理；PAUSED保留已发布水位记录，避免把非终态当可清理终态。V5在同批停受理/停消费的迁移中为存量任务建立pendingVersion=task.version、publishedVersion=-1及当前活动版本，不遗漏部署前存在的活跃任务。Redis/SSE网络操作始终在业务事务外。

投递expireOrClose遇PAUSING必须直接STALE保留原命令，不能走原“非PENDING就AUTHORITY_NO_LONGER_NEEDS_COMMAND封存”分支；PAUSED只能核对USER_PAUSED封存，不替换原因。PENDING直接暂停的ACK证据为同事务暂停操作回执、权威PAUSED及注册原命令CLOSED/USER_PAUSED；无需伪造task_execution记录。有执行记录的暂停还要求pauseAckVersion匹配当前controlVersion及停止fence已经推进。

## 装配修复与暂停交界

[修复契约](assembly-repair.md)中的清单封存、检测结果发布、替换块登记、段选择CAS、进度与READY登记全部复用本契约的控制身份及锁序，并在checkpoint后、artifact前锁repair和按序segment。任一暂停屏障已建立则禁止上述新业务写入；安全资源关闭后仍优先提交PAUSE_STOPPED，不能因修复循环吞掉暂停。

修复只在原任务、原命令与当前RUNNING执行权内运行，无新schemaVersion、runGeneration或补投执行消息。检查中、重建中、重新装配中都支持原暂停；PAUSED保持计划与已登记选择但不自动重建。原消息仅在成功／明确失败终态或持久暂停责任边界后按原协议ACK；可恢复损坏不能提前ACK，数据库UNKNOWN保持HOLD。
