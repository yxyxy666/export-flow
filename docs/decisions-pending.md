# 待确认决策

**更新日期**：2026-10-07。项目负责人已明确“解决下所有待决策项，按你的建议来”，原待决事项已按建议定稿或明确延期，当前无待选择的技术方案。决策批准不等于规格整体审批、测试阶段审批或验收通过；运行事实与验证缺口继续如实记录。

## 运行与交付

使用宿主机应用、本地构建门禁、前端dist和后端jar，复用既有中间件并隔离schema/vhost/键前缀。当前不新增Compose、托管CI或远程发布，多主机存储延期。已同步[运行规则](rules/container.md)、[构建规则](rules/build.md)、[ADR-0009](adr/0009-engineering-runtime-and-progress-notification.md)和[决策记录](../specs/002-order-management/decision-record.md)。

## 基础骨架规格评审

既有骨架实施授权保持不变，[验证缺口](validation/001-project-bootstrap/environment.md)仍未执行补证或获得验收结论。目标浏览器确定为Windows稳定Chrome/Edge；IAB版本和Network面板不足属于辅助工具限制，不再作为技术待选项。异常恢复和完整浏览器验证仍需真实证据，[T-008](../specs/001-project-bootstrap/tasks.md)不自动勾选。

## 订单管理规格评审

本次D-01至D-12全部有明确结论，见[逐项记录](../specs/002-order-management/decision-record.md#逐项结论)、[精确依赖版本](../specs/002-order-management/dependency-versions.md)。恢复架构已在[ADR-0008](adr/0008-worker-execution-lease-and-recovery.md)接受对应子集；相关产品条目已在[验收基线](product/acceptance-criteria.md)确认。核心及附属规格更新为草稿0.3，整体规格仍待单独审批，见[任务记录](../specs/002-order-management/tasks.md#审批记录)。

## SSE 生命周期参数

未来采用SseEmitter、1秒空闲宽限、15秒心跳、1至30秒重连退避、重连MySQL快照及版本事件ID；隐藏立即关流，不固定轮询，接受空闲后无法由其他设备新任务自动唤醒。可靠通知使用独立进度Outbox和每实例持久队列，见[ADR-0009](adr/0009-engineering-runtime-and-progress-notification.md#后续sse与redis方向)。本期不实施SSE。

## 后端实现选择

READ COMMITTED、固定锁序/SKIP LOCKED、经典持久队列/正确路由证明、幂等409、执行监督和文件策略均已定稿。Redis继续7.4，不升级8；后续缓存TTL10分钟、应用活跃键上限10000、版本比较及MySQL回源。默认任务排序沿用创建时间/ID倒序，活跃优先及依赖它的进度排序撤出，编号保留。配置值见[决策记录](../specs/002-order-management/decision-record.md#文件及执行参数)，不再重复维护。

<a id="worker执行架构候选"></a>
## Worker 执行架构

[ADR-0008](adr/0008-worker-execution-lease-and-recovery.md)已接受执行权、异常恢复、不可变块断点与产物对账子集；用户暂停/显式恢复和跨主机存储明确不纳入当前MVP。坏消息保留原命令、停止消费槽并告警，不ACK或自动死信，依据[ADR-0009](adr/0009-engineering-runtime-and-progress-notification.md#命令保留与异常消息)。

## 测试与验证选择

测试栈、静态检查、覆盖率报告而不设百分比阈值、目标浏览器、参考硬件和测量方法均已定稿，见[测试规则](rules/testing.md)、[版本表](../specs/002-order-management/dependency-versions.md)和[参考环境](../specs/002-order-management/decision-record.md#参考环境及事实核对)。

尚未完成的是连接/隔离资源、实际镜像digest、依赖安装兼容、文件系统能力、行为测试及性能测量，这些属于执行与证据；不能通过填写决策消除。本轮docker ps退出码0但当前desktop-linux context无运行容器记录，保留此前healthy历史证据，不宣称现在环境已就绪。

后续若出现新的设计歧义在此新增；已决方案在归属产品、ADR、规则和规格维护，不将阶段审批与未执行验证重新包装为待选技术。
