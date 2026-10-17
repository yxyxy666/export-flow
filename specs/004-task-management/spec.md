# 功能规格：任务列表、SSE进度、暂停与装配修复

**版本**：0.3　**日期**：2026-10-17　**状态**：规格0.3及测试阶段已于2026-10-17获负责人批准，当前生产实施；保留测试先行证据。
**风险**：高风险；暂停与Worker、认领、完成和文件登记存在并发一致性边界。  
**范围依据**：负责人明确选择“仅做列表/SSE/暂停，增删改先预留”。

审批依据：负责人先以“可以按照当前规格开始了”批准规格0.3，随后对测试材料及六类缺口处理方案回复“继续”，批准测试阶段并授权本期生产实施。范围与日期见[审批记录](tasks.md#审批记录)；项目级效率规则草案仍独立待批。


关联：[计划](plan.md)、[任务](tasks.md)、[模型](data-model.md)、[HTTP/SSE契约](contracts/http-sse.md)、[OpenAPI](contracts/openapi.yaml)、[暂停执行契约](contracts/execution-control.md)、[调查](research.md)、[审查修订](review.md)、[已接受ADR](../../docs/adr/0010-task-pause-control-barrier.md)。产品行为定义只维护在[唯一验收基线](../../docs/product/acceptance-criteria.md)。

## 本期范围

升级既有任务占位页，交付任务查询、关键字／状态筛选、分页、刷新、实际进度与元数据展示、真实SSE、等待中／生成中任务暂停。已完成文件的下载按钮复用已有接口，复用文件生成链路并补充装配校验与自动修复。页面保持现有任务路由，不新增详情页。

新增、修改、删除、失败后用户手动重试及暂停后的用户显式继续仅预留，不设计本期可调用端点、生产函数、迁移或测试。不展示看似可用的预留动作；“已暂停”任务保留原编号、范围、进度和可信断点，本期不会自动继续。

暂停的新状态及执行协议已随规格0.3批准进入测试先行；旧ADR接受的异常恢复不授权用户继续，也不会把已暂停任务当异常恢复对象。上一轮[测试效率规则](../../docs/rules/testing-efficiency-proposal.md)仍待审批，本轮按负责人直接要求不安排性能、超时或延时验证；测试阶段与缺口处理方案已批准，生产实施已授权。

## 用户故事

### US1 查看与筛选任务（P2）

列表展示任务编号、文件名、导出范围、六种状态、已处理／总条数、实际百分比、创建／结束时间、成功文件大小和安全错误。表单支持任务编号或文件名关键字与状态组合，提交查询才生效，重置清空条件。默认稳定倒序分页，页大小20/50/100；查询、重置、改页大小回第一页，进度更新和手动刷新保页。成员变化导致页越界时查询最后有效页，空集合回第一页。

已提交查询可从URL恢复；任务关键字不包含订单客户数据。初次加载、空结果、刷新失败、断线分别显示；保留最后快照时注明数据尚未更新。未知状态不冒充合法状态，禁用相关操作并给出安全提示。

验收范围：[筛选](../../docs/product/acceptance-criteria.md#ac-026)、[分页](../../docs/product/acceptance-criteria.md#ac-027)、[排序](../../docs/product/acceptance-criteria.md#ac-028)、[保页](../../docs/product/acceptance-criteria.md#ac-031)、[搜索](../../docs/product/acceptance-criteria.md#ac-038)、[快照](../../docs/product/acceptance-criteria.md#ac-081)、[输入保护](../../docs/product/acceptance-criteria.md#ac-086)。

### US2 持续读取进度与全部状态（P1）

初始列表读取MySQL；页面可见且全环境存在活跃任务时建立唯一SSE，活跃判断不受当前筛选／分页限制。真实通道覆盖生成、完成、失败、暂停中、已暂停。任务版本单调合并，重复或旧事件不覆盖新状态；装配校验后新版本可携带下降的可信进度，不用历史最大值覆盖。建立订阅后桥接一次快照，补齐首次HTTP与开流间隙。

纯进度事件更新当前行，不重新排序或查询；可能改变筛选成员／总数的事件合并为一次当前页权威查询。接受idle后查一次最终快照，补齐尚未广播的最后状态；无活动则保持空闲。连接失败保留最后结果并提示；离开、隐藏或离线释放连接，恢复联网／可见先查快照。保留既有按需空闲和退避策略，但不安排时间阈值测试、不周期轮询。

订单创建仍留在订单页；成功后仅发送本浏览器无敏感任务通知，任务页可据此唤醒；订单页不追加状态请求／SSE。其他设备在正常空闲关闭后新建任务不会自动唤醒本页，沿用已接受限制。

验收范围：[进度](../../docs/product/acceptance-criteria.md#ac-012)、[活跃通道](../../docs/product/acceptance-criteria.md#ac-039)、[单例](../../docs/product/acceptance-criteria.md#ac-040)、[空闲](../../docs/product/acceptance-criteria.md#ac-042)、[创建竞态](../../docs/product/acceptance-criteria.md#ac-043)、[唤醒](../../docs/product/acceptance-criteria.md#ac-044)、[版本衔接](../../docs/product/acceptance-criteria.md#ac-082)、[扩展状态](../../docs/product/acceptance-criteria.md#ac-084)、[可靠通知](../../docs/product/acceptance-criteria.md#ac-085)。新状态扩展由新标准管理，既有编号原意不改写。

### US3 安全暂停任务（P1）

PENDING与RUNNING提供暂停确认；等待任务未被认领可直接暂停，已执行任务先显示“暂停中”。暂停接口成功仅代表已受理，直到收到权威PAUSED才显示“已暂停”。禁止乐观宣称生成线程已停止。

暂停先提交时建立控制屏障，旧执行者不得登记新块、就绪文件或终态；完成先提交时暂停返回状态冲突，刷新展示权威结果。暂停不算失败／完成，不设结束时间，不开放半成品下载。保留已提交断点、最新已提交可信进度与受保护文件；未登记文件不伪装成进度。

同操作ID重复请求找回已提交结果；连接中断／提交结果不明保留冻结操作，阻止该行互斥提交，按同ID找回。本期不提供继续，不自动重跑暂停任务。

验收范围：[暂停](../../docs/product/acceptance-criteria.md#ac-083)、[状态与动作](../../docs/product/acceptance-criteria.md#ac-084)、[结果不明](../../docs/product/acceptance-criteria.md#ac-087)、[文件保护](../../docs/product/acceptance-criteria.md#ac-069)。

### US4 查看成功产物（P2）

仅成功任务显示可用下载；复用已有同源文件接口，文件名／大小／结束时间取权威摘要。暂停中、已暂停及其他非成功状态不可下载并说明原因。不增加失败重试按钮或任务详情接口。

验收范围：[下载](../../docs/product/acceptance-criteria.md#ac-013)、[禁用](../../docs/product/acceptance-criteria.md#ac-014)、[元数据](../../docs/product/acceptance-criteria.md#ac-015)、[永久保留](../../docs/product/acceptance-criteria.md#ac-018)。旧失败重试标准保留，属于后续阶段，不取消定义。

### US5 装配异常自动修复（P1，2026-10-17新增）

负责人新增要求：执行结束装配时校验文件与登记，异常报错后自动重试；对账完先更新进度，匹配部分复用、不匹配部分重建，并在进度条后显示黄色重建提示。此项加入本期；失败后的用户手动重试及暂停后的继续仍预留。

可复用对象是有登记且实际校验通过的数据块；POI工作文件和Excel半成品重新装配。数据库记录缺失须有独立可信分段依据，否则明确失败，不能采信孤儿文件；数据库不可读归UNKNOWN。进度只体现最近数据库提交，不逐次SSE核验文件。修复仍受暂停屏障约束，轮次有界，不能无限运行。

完整边界与异常矩阵见[装配修复契约](contracts/assembly-repair.md)，验收引用[装配对账](../../docs/product/acceptance-criteria.md#ac-088)、[选择性修复](../../docs/product/acceptance-criteria.md#ac-089)、[进度修正](../../docs/product/acceptance-criteria.md#ac-090)、[黄色提示](../../docs/product/acceptance-criteria.md#ac-091)。

显示高水位与连续前缀的变化由[修复ADR](../../docs/adr/0011-selective-block-repair-and-progress.md)单独评审；规格及ADR已获批准，测试阶段与实施授权见任务审批记录。

## 功能需求

- FR-001：MySQL为状态、下载和空闲裁决事实源；Redis只缓存版本化摘要，故障可回源。
- FR-002：所有可观察写入，包括创建、认领、块提交、终态、暂停与停止确认，必须同事务登记版本化进度通知意图。
- FR-003：按接受的独立通知Outbox、fanout和每实例持久队列通知，不把进度Outbox用于执行命令补投。
- FR-004：新增PAUSING/PAUSED、控制版本及暂停幂等回执，不扩展用户继续命令协议；保留旧创建响应和原消息schemaVersion=1。
- FR-005：既有原命令保留；只有权威暂停已确认且执行责任已持久封存，才允许确认对应消费；依赖不可读不确认、不推定失败。
- FR-006：复用已启动MySQL/RabbitMQ/Redis、现有只读订单及文件根，不新建中间件、不造数、不操作共享全局配置。
- FR-008：装配入口、逐块读取与最终产物校验异常触发同任务内有界自动修复；只重建不匹配的数据块，校验后事务发布可信进度及修复状态。
- FR-009：进度条后黄色信息图标由持久重建状态驱动，hover及键盘focus提示“正在重建损坏文件”；暂停、成功与失败同步切换提示。
- FR-007：前后端遵循既有分层、统一API及中文维护规则；本轮只编写文档，所有审批真实记录在[任务](tasks.md)。

## 非目标

增删改、失败后用户手动重试、继续／恢复按钮、取消、批量暂停、任务详情、任务物理删除、已冻结范围修改、活跃优先／进度排序、前端轮询、认证、跨主机存储、性能及计时专项、浏览器E2E门禁。

## 成功标准

引用[可见性](../../docs/product/acceptance-criteria.md#sc-005)、[视觉](../../docs/product/acceptance-criteria.md#sc-011)、[质量门禁](../../docs/product/acceptance-criteria.md#sc-012)与[暂停安全闭环](../../docs/product/acceptance-criteria.md#sc-016)。证据、环境和用例在[tasks.md](tasks.md)映射，并引用[选择性修复闭环](../../docs/product/acceptance-criteria.md#sc-017)，不新增性能标准。

## 前提与待确认事项

已确认：用户收窄范围；技术栈、只读来源和基础设施复用；[通知架构](../../docs/adr/0009-engineering-runtime-and-progress-notification.md)已接受。

已接受：[暂停ADR](../../docs/adr/0010-task-pause-control-barrier.md)的控制屏障、暂停消费确认与崩溃对账；本规格的测试排除范围及两次审批均已记录。后续增删改和继续的语义不纳入本期评审结论，见[待确认事项](../../docs/decisions-pending.md#任务页面规格边界)。

