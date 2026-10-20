# 待确认决策

**更新日期**：2026-10-17。项目负责人已明确“解决下所有待决策项，按你的建议来”，原待决事项已按建议定稿或明确延期；新增日期组件解析问题已完成技术核验，见对应章节。2026-10-14新增测试执行效率规则评审及任务页面规格边界，见末尾。决策批准不等于规格整体审批、测试阶段审批或验收通过；运行事实与验证缺口继续如实记录。

## 运行与交付

使用宿主机应用、本地构建门禁、前端dist和后端jar，隔离schema/vhost/键前缀。2026-10-20负责人明确：新使用者先执行make infra从零创建本项目基础设施和应用连接，再执行make dev启动应用；不按端口复用其他项目容器。已按[ADR-0010](adr/0010-local-infrastructure-bootstrap.md)同步[运行规则](rules/container.md)与[构建规则](rules/build.md)，替代先前“不新增Compose”的限制。托管CI、远程发布和多主机存储仍延期。

## 基础骨架规格评审

既有骨架实施授权保持不变，[验证缺口](validation/001-project-bootstrap/environment.md)仍未执行补证或获得验收结论。目标浏览器确定为Windows稳定Chrome/Edge；IAB版本和Network面板不足属于辅助工具限制，不再作为技术待选项。异常恢复和完整浏览器验证仍需真实证据，[T-008](../specs/001-project-bootstrap/tasks.md)不自动勾选。

## 订单管理规格评审

本次D-01至D-12全部有明确结论，见[逐项记录](../specs/002-order-management/decision-record.md#逐项结论)、[精确依赖版本](../specs/002-order-management/dependency-versions.md)。恢复架构已在[ADR-0008](adr/0008-worker-execution-lease-and-recovery.md)接受对应子集；相关产品条目已在[验收基线](product/acceptance-criteria.md)确认。核心及附属规格已更新为0.4，2026-10-11负责人明确要求进入规格实施阶段，规格已批准；同日负责人明确授权开始开发，统一API和旧占位清理已落实，其余生产/验证任务未完成，见[任务记录](../specs/002-order-management/tasks.md#审批记录)。

## SSE 生命周期参数

未来采用SseEmitter、1秒空闲宽限、15秒心跳、1至30秒重连退避、重连MySQL快照及版本事件ID；隐藏立即关流，不固定轮询，接受空闲后无法由其他设备新任务自动唤醒。可靠通知使用独立进度Outbox和每实例持久队列，见[ADR-0009](adr/0009-engineering-runtime-and-progress-notification.md#后续sse与redis方向)。002范围未实施SSE；后续004的规格及测试阶段已获批，生产实施授权见004任务记录。

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

## 测试执行效率规则评审

待负责人审批（2026-10-14）。负责人要求暂时不考虑性能、不重复运行性能测试，超时与延时测试直接跳过，基础设施复用已经启动的服务。[规则草案 0.1](rules/testing-efficiency-proposal.md)已列出项目总原则、宪法、工程规则、模板及归属文档的具体拟定修改；目前仍是评审稿，未将草案标记为生效；004实际执行按其独立批准范围记录。规则获批后按归属同步，不替代任务页面的两次开发审批。

<a id="任务页面规格边界"></a>
## 任务页面已决范围

负责人于2026-10-14明确“仅做列表/SSE/暂停，增删改先预留”，2026-10-17增加同任务装配对账、选择性重建、可信进度重算及黄色提示，并以“可以按照当前规格开始了”批准[004规格0.3](../specs/004-task-management/spec.md)及其附属文档。[暂停ADR](adr/0010-task-pause-control-barrier.md)、[修复ADR](adr/0011-selective-block-repair-and-progress.md)、3轮持久预算、本地SSE发送队列上限256与本期排除范围均随规格接受，不再列为技术待选项。

状态与设计归产品／验收、ADR和功能规格维护。本期只复用负责人现有基础设施；增删改、失败后用户手动重试仍预留。2026-10-17用户继续已明确纳入[005增量](../specs/005-task-resume-and-live-progress/spec.md)，并明确预授权规格→补测试→实施无需再次等待；见[授权依据](../specs/005-task-resume-and-live-progress/tasks.md#审批记录)。项目级效率规则草案仍独立待审批。

已完成测试先行、获批生产实施及适用验证，历史未触达分支和六类功能缺口已补证，见[证据](validation/004-task-management/verification.md#最终功能与质量结果)与[覆盖缺口](validation/004-task-management/coverage-gaps.md)。负责人已批准测试阶段与缺口处理方案；性能及计时排除保持未验证，已决不等于免验或负责人产品验收通过。

## 004审批状态更新（2026-10-17）

负责人明确“可以按照当前规格开始了”，004规格0.3、关联暂停与修复ADR及本期测试排除范围已获批准，前述对应待评审记录关闭，进入测试先行。生产实施随后获明确授权；项目级效率规则草案仍独立未批准；实际测试证据见[任务](../specs/004-task-management/tasks.md#2026-10-17测试阶段启动)。

## 统一删除规格待确认事项（2026-10-18）

`006-task-deletion`已按“一个删除按钮、一个接口、服务端按状态分流”完成实现。用户已授权按规格直接测试、自检和开发；以下产品治理事项仍待后续明确，不阻塞当前实现：

- `PAUSED`任务是否与运行中任务一样允许删除；当前草案按需要撤销继续能力处理，但未视为已批准。
- 任务业务根记录及其业务子记录必须硬删除；为迟到消息保留的独立技术墓碑、操作回执和清理审计记录的最小身份保留期限仍待确认。
- 是否需要操作员/用户审计字段；当前项目没有认证边界，草案只保存操作ID和时间。
- 必须同步调整“成功任务与文件永久保留”和“失败任务记录保留”的产品口径；相关行为变化已在[验收基线](product/acceptance-criteria.md)记录，保留期限变更需补充产品决策。

文档草案见[006规格](../specs/006-task-deletion/spec.md)、[计划](../specs/006-task-deletion/plan.md)和[任务清单](../specs/006-task-deletion/tasks.md)。
