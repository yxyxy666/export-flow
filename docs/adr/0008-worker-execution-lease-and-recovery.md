# ADR-0008 Worker 执行权、用户暂停、断点恢复与竞态隔离

- 状态：已接受执行权／异常恢复／断点与文件对账子集；用户暂停／显式恢复提议撤出当前MVP
- 日期：2026-10-03
- 修订：2026-10-07；负责人明确“解决下所有待决策项，按你的建议来”，接受本次订单规格所需架构；不构成规格或代码实施授权
- 关联：[产品](../product/order-export-prd.md)、[验收基线](../product/acceptance-criteria.md)、[ADR-0006](0006-broker-owned-pending-delivery.md)、[ADR-0007](0007-outbox-dispatch-retry.md)、[ADR-0009](0009-engineering-runtime-and-progress-notification.md)、[本次规格](../../specs/002-order-management/spec.md)

## 背景与接受边界

RabbitMQ保管已发送和未确认命令，MySQL裁决任务及执行权。真实Excel长任务需要在进程崩溃、心跳失败、重复消息与文件提交窗口下保持单一有效结果。接受MySQL持久租约、单调隔离代次、可信块断点、有界异常接管及唯一产物引用，不扩展任务创建Outbox为RUNNING扫描恢复机制。

原2026-10-04提议中的用户暂停、控制版本、暂停生效ACK与显式恢复Outbox不纳入当前MVP，既有四种产品状态保持。本文件后续使用的“恢复”指异常恢复；暂停方案须在未来明确要求时重新准备产品和功能规格，不能据本次批准生成暂停接口。跨主机共享存储和对象存储同样延期，同主机Worker共享受控持久命名空间。

## 决策

### 消息领取与执行权

Worker通过RabbitMQ竞争消费，收到后保持未确认，立即按task读取权威状态。PENDING首次认领与投递到期扫描共用task→outbox锁序，原子写RUNNING和执行lease，只有确认提交后生成。收到终态命令核对后ACK；RUNNING有效租约不能被抢占或仅凭状态ACK，监督器有界核对后协调通道；认领不明和DB不可读不生成、不ACK。

每个执行槽使用可独立关闭的通道。认领保护、执行lease、dispatcher投递lease和broker ACK timeout分别配置；不增加永久领取锁，不在生成期间持有DB行锁。消费监督器独立于生成线程，识别收到但未认领的停滞；正常RUNNING不能被短认领阈值关闭。

### 租约、续租与异常接管

owner是每次尝试唯一身份；fence单调递增，所有到期依据DB时间。续租只接受有效当前身份并受单次尝试期限限制；拒绝或提交不明立即停止新读写/业务登记，回读核对并协调关闭通道，不以本地失败假定锁已释放，迟到心跳不能复活过期身份。

原未确认命令在断连或进程退出后由broker重交付。只有lease过期且持久nextRecoveryAt到期时，短事务建立新owner/fence并扣异常接管预算；竞争失败不扣。禁止扫描RUNNING创建新Outbox。明确不可恢复错误或恢复预算耗尽，由合法裁决事务提交FAILED、结束时间及安全错误，保留实际进度，终态提交后ACK；依赖不可读不推断失败。

### 数据块、断点与安全重做

源合成订单初始化后只读，范围与字段冻结；稳定keyset分批读取，先持久化独立不可变数据块，再同事务登记引用、连续游标、行数和版本。写入检查owner/fence、有效lease、dataGeneration、前序版本与游标，同一owner乱序也拒绝。提交不明按唯一块身份核对，不重复累加。

恢复先核对可信连续前缀和READY结果，暂不可读不视为损坏；断点计数不是独立恢复位置。没有可信断点时，只有重复无业务副作用及尝试隔离/结果对账已证明，才建立新数据代次重建。无法证明重复安全时禁止自动重跑，由合法裁决明确失败。数据代次、fence和恢复预算分别表达不同事实。

最终SXSSF从可信完整块清单流式装配，已完成行逐批刷入磁盘临时sheet，结束后完整封装.xlsx.part；关闭、校验、force后原子改名为正式.xlsx。半成品xlsx和POI scratch不作跨进程续写协议，装配可在新attempt重做。显示进度保留高水位，恢复可信前缀单独记录，非成功最多99，真实成功才100，不用显示进度跳过数据。

### 就绪产物与清理

attempt文件路径隔离，关闭/验证后同卷原子发布不可变文件，持有效执行权登记READY证明。READY不等于成功；接管者重新校验后可采用，成功及唯一resultArtifactId同事务提交。成功提交不明先核对，不写FAILED或删除候选结果。旧fence不能登记新断点或覆盖新结果。

仍受断点、READY和成功记录引用的文件禁止按年龄或租约过期删除。采用/清理共用候选行锁，先核对无有效执行与所有保护引用再授予清理资格；物理扫描只登记候选。成功任务与结果永久保留。文件根、原子移动与Windows reparse拒绝见[Excel方案](../../specs/002-order-management/excel-design.md)。

### 停机与运行参数

SIGTERM先停新受理/消费，在有界宽限内完成或保留已提交断点，协调停止当前工作、心跳和未确认通道。不能先释放lease再让旧线程写入，也不能删恢复数据；无法确认旧线程停止时保留权利至到期，由fence隔离。

数值、目录、块格式、SQL谓词与函数设计在[已定稿决策](../../specs/002-order-management/decision-record.md)、[模型](../../specs/002-order-management/data-model.md)、[函数清单](../../specs/002-order-management/function-design.md)维护；异常消息及后续SSE由[ADR-0009](0009-engineering-runtime-and-progress-notification.md)负责，不在本ADR默认扩展ACK边界。

## 验证与回退

关联 [`AC-049`](../product/acceptance-criteria.md#ac-049)、[`AC-053`](../product/acceptance-criteria.md#ac-053)、[`AC-067`](../product/acceptance-criteria.md#ac-067)、[`AC-068`](../product/acceptance-criteria.md#ac-068)、[`AC-069`](../product/acceptance-criteria.md#ac-069)，具体测试映射见[tasks](../../specs/002-order-management/tasks.md)。必须验证认领/成功提交不明、kill、租约续租接管竞争、旧fence、块登记前后、断点损坏/暂不可读、安全重建、READY采用与清理互斥、ACK丢失及恢复耗尽，尚无执行证据。

回退停止新消费并保留租约、原命令、幂等、块清单和结果，禁止清队列、清表、重置fence或批量回退PENDING。架构批准后仍须分别批准规格和测试阶段，才可实施。

## 技术依据

- [RabbitMQ发布与消费确认](https://www.rabbitmq.com/docs/confirms)
- [RabbitMQ消费者](https://www.rabbitmq.com/docs/consumers)
- [MySQL8.4锁定读](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html)
- [Apache POI SXSSF](https://poi.apache.org/components/spreadsheet/how-to.html#sxssf)

上述资料提供机制依据，不是本项目端到端验证结果。[学习稿](../../DATA-FLOW.md#figure-4)同步接受边界，具体规格仍待独立审批。
