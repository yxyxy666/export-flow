# Worker 编排与真实 Excel 契约

**版本／状态**：草稿0.3。生成、异常恢复及下载纳入本期；执行权/断点/就绪对账子集已在[ADR-0008](../../../docs/adr/0008-worker-execution-lease-and-recovery.md)接受，参数与异常消息见[决策记录](../decision-record.md)；规格整体仍待单独批准。关联[函数](../function-design.md)、[模型](../data-model.md)、[Excel方案](../excel-design.md)、[消息](export-command.md)、[tasks](../tasks.md)。

## 实际执行链

监听器校验最小命令→权威任务/终态→能力检查→MySQL首次认领或过期且退避到期的有界接管→提交确认→优先可信READY对账→校验连续断点→稳定批读和块落盘→断点/进度同事务提交→SXSSF逐批刷出已完成行与缓冲到临时sheet文件→完整封装.xlsx.part→关闭校验→原子改名正式.xlsx→就绪登记→成功及唯一产物引用同事务→权威终态确认后ACK。

所有源数据及文件工作在事务外，短事务只裁决权利和登记引用。没有可用生成/存储能力时阻止消费启用，不把能力检查故障当业务已失败。批准范围内的演示角色启用消费者，本期不保留空函数或默认关闭作为交付状态。配置开关只用于部署角色、受控停机与测试，不能替代交付验证。

## 生成端口

`isAvailable(): boolean` 根据已验证依赖、持久目录和原子发布能力；`generate(context: GenerationContext, progressSink: ProgressSink, guard: ExecutionGuard): GenerationResult` 真实返回READY、KNOWN_FAILURE或UNKNOWN。

READY必须包含真实校验后的ArtifactProof且就绪登记已确认；字段顺序、原值、中文展示、金额和香港时间见Excel方案。KNOWN_FAILURE只有明确不可恢复分类；依赖不可读、提交结果不明、失权均不能随意失败或ACK。不存在NOT_IMPLEMENTED分支。ProgressSink只核对已提交断点并通知本地观察器，不能报告尚未落盘的内存条数。

## 执行权与异常恢复

owner/fence/lease、attemptDeadline及持久恢复退避使用数据库时间。心跳续租条件检查身份/到期/尝试期限；失败或不明停止新批次/登记/结果提交后核对。旧身份不能复活、写新断点或覆盖新结果。收到RUNNING重复命令时先检查有效lease，不能同时执行或只看RUNNING就ACK。

连接关闭或进程退出后，原未确认命令由broker重交付；过期且nextRecoveryAt到期时条件接管，新fence并事务扣恢复次数。达到批准预算或明确不可恢复时由合法裁决事务失败，终态确认后ACK。禁止扫描RUNNING创建新的恢复Outbox；源只读及尝试文件隔离才证明可安全重做。断点续写与是否授权接管分别判断，不默认重读全部数据。

认领监测只处理交付后尚未认领且DB仍PENDING的停滞，不以短阈值关闭正常RUNNING。单次attempt期限与broker ACK timeout分别配置，监督器独立于生成，停止先停新消息、保存可信断点、停止当前工作，再协调心跳/通道；未确认线程停止不得提前释放有效权利。具体已定数值和故障场景见Excel方案。

## 断点、产物与确认

块包含版本/配置/代次/序号/游标/行数及SHA，先持久化不可变内容再同事务登记引用/游标/计数/版本。登记结果不明按blockId回读，旧owner或同owner乱序版本均拒绝。组装中断保留数据块，新attempt重新装配，不重新打开半成品xlsx追加。

artifact READY与成功不同；接管者重新校验READY后可采用，新成功与task结果引用同事务。文件存在无DB证明为残留，不能下载。就绪/成功提交不明先回读，不删除候选结果或写失败。真实业务终态已提交，或者MySQL权威证明消息无需执行后，才允许ACK；DB不可读不确认。成功文件及仍被断点引用的块永久保护，清理必须DB裁决且与采用互斥。

## 本期之外

用户暂停/显式恢复及相应控制版本、跨主机存储、SSE/Redis通知、完整任务页不在本契约。测试替身只能补充难触发窗口，正式文件内容、性能、堆资源与恢复验证必须真实MySQL/RabbitMQ/文件，不因函数签名完善而视为审批或验收通过。
