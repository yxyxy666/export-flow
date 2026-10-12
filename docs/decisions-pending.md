# 待确认决策

**更新日期**：2026-10-12。项目负责人已明确“解决下所有待决策项，按你的建议来”，原待决事项已按建议定稿或明确延期；新增日期组件解析问题已完成技术核验，见末节。当前无待选择的技术方案。决策批准不等于规格整体审批、测试阶段审批或验收通过；运行事实与验证缺口继续如实记录。

## 运行与交付

使用宿主机应用、本地构建门禁、前端dist和后端jar，复用既有中间件并隔离schema/vhost/键前缀。当前不新增Compose、托管CI或远程发布，多主机存储延期。已同步[运行规则](rules/container.md)、[构建规则](rules/build.md)、[ADR-0009](adr/0009-engineering-runtime-and-progress-notification.md)和[决策记录](../specs/002-order-management/decision-record.md)。

## 基础骨架规格评审

既有骨架实施授权保持不变，[验证缺口](validation/001-project-bootstrap/environment.md)仍未执行补证或获得验收结论。目标浏览器确定为Windows稳定Chrome/Edge；IAB版本和Network面板不足属于辅助工具限制，不再作为技术待选项。异常恢复和完整浏览器验证仍需真实证据，[T-008](../specs/001-project-bootstrap/tasks.md)不自动勾选。

## 订单管理规格评审

本次D-01至D-12全部有明确结论，见[逐项记录](../specs/002-order-management/decision-record.md#逐项结论)、[精确依赖版本](../specs/002-order-management/dependency-versions.md)。恢复架构已在[ADR-0008](adr/0008-worker-execution-lease-and-recovery.md)接受对应子集；相关产品条目已在[验收基线](product/acceptance-criteria.md)确认。核心及附属规格已更新为0.4，2026-10-11负责人明确要求进入规格实施阶段，规格已批准；同日负责人明确授权开始开发，统一API和旧占位清理已落实，其余生产/验证任务未完成，见[任务记录](../specs/002-order-management/tasks.md#审批记录)。

## SSE 生命周期参数

未来采用SseEmitter、1秒空闲宽限、15秒心跳、1至30秒重连退避、重连MySQL快照及版本事件ID；隐藏立即关流，不固定轮询，接受空闲后无法由其他设备新任务自动唤醒。可靠通知使用独立进度Outbox和每实例持久队列，见[ADR-0009](adr/0009-engineering-runtime-and-progress-notification.md#后续sse与redis方向)。本期不实施SSE。

## 后端实现选择

READ COMMITTED、固定锁序/SKIP LOCKED、经典持久队列/正确路由证明、幂等409、执行监督和文件策略均已定稿。Redis继续7.4，不升级8；后续缓存TTL10分钟、应用活跃键上限10000、版本比较及MySQL回源。默认任务排序沿用创建时间/ID倒序，活跃优先及依赖它的进度排序撤出，编号保留。配置值见[决策记录](../specs/002-order-management/decision-record.md#文件及执行参数)，不再重复维护。

<a id="worker执行架构候选"></a>
## Worker 执行架构

[ADR-0008](adr/0008-worker-execution-lease-and-recovery.md)已接受执行权、异常恢复、不可变块断点与产物对账子集；用户暂停/显式恢复和跨主机存储明确不纳入当前MVP。坏消息保留原命令、停止消费槽并告警，不ACK或自动死信，依据[ADR-0009](adr/0009-engineering-runtime-and-progress-notification.md#命令保留与异常消息)。

## 测试与验证选择

测试栈、静态检查、覆盖率报告而不设百分比阈值、目标浏览器、参考硬件和测量方法均已定稿，见[测试规则](rules/testing.md)、[版本表](../specs/002-order-management/dependency-versions.md)和[参考环境](../specs/002-order-management/decision-record.md#参考环境及事实核对)。

2026-10-11测试准备已完成锁定测试依赖安装、镜像digest固定和隔离MySQL/RabbitMQ/原子文件/回环HTTP工具检查；Docker已启动，共享示例容器healthy，未修改其数据或配置。尚未完成的是生产实现、真实业务边界与完整覆盖、浏览器及性能测量，见[环境](validation/002-order-management/environment.md)、[测试结果](validation/002-order-management/red-results.md)和[覆盖缺口](validation/002-order-management/coverage-gaps.md)。这些属于执行与证据，不能通过填写决策消除。

后续若出现新的设计歧义在此新增；已决方案在归属产品、ADR、规则和规格维护，不将阶段审批与未执行验证重新包装为待选技术。

2026-10-12新增选择问题已由负责人明确：全选后各页显示选中，可逐条取消且支持全部反选；时间使用Ant Design选择器。产品与规格已同步至[增量条目](product/acceptance-criteria.md#全选筛选结果增量草稿)和[新规格](../specs/003-select-all-filtered/spec.md)，不再作为待确认问题。负责人随后回复“开始吧”明确批准003规格0.1，测试先行已执行，见[阶段证据](validation/003-select-all-filtered/verification.md)；负责人随后以“继续开发”批准测试阶段及覆盖缺口，授权003生产实施，旧历史授权不代替本次审批。


## 日期控件内部文本解析核验

已关闭（2026-10-12）。测试先行review发现默认文本解析可能依赖本机Dayjs时区，负责人以“继续开发”批准该阶段及覆盖缺口。按[日期控件标准](product/acceptance-criteria.md#ac-080)和[计划](../specs/003-select-all-filtered/plan.md)使用AntD公开DatePicker.generatePicker适配：内部文本解析及日历运算均以UTC承载香港墙上时间组成，最终接口转换仍使用原toUtcInstant；未新增rc-picker依赖。

真实业务组件已在UTC通过日期、清空、历史和重置测试；America/New_York夏令时跳时对应的两项同值输入及UTC请求断言也全部通过，见[最终记录](validation/003-select-all-filtered/verification.md#最终功能回归与质量门禁)。保留本节锚点供历史覆盖缺口追溯，不再列为待核验事项。
