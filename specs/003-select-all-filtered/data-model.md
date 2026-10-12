# 选择状态与任务范围设计

0.1已批准，随[规格](spec.md)、[计划](plan.md)和[任务](tasks.md)完成测试先行并获第二次批准，本文记录本次实施状态设计。交互依据见[产品基线](../../docs/product/acceptance-criteria.md#全选筛选结果增量草稿)。

## 前端状态

OrderSelectionState为mode: INCLUDE或EXCLUDE、ids: ReadonlySet<OrderId>、notice?: string；notice用于展示非法ID或容量拒绝原因，拒绝操作保持原mode/ids，下一次合法操作清除notice。INCLUDE的ids是已选，EXCLUDE的ids是已取消。Hook的filterKey继续包括已提交filter（含内存keyword）及historyRevision，不包括分页/排序。全选等价EXCLUDE空集合，清空等价INCLUDE空集合，全部反选只翻转mode，保持例外集合；每页行选中在INCLUDE时判断ids.has，在EXCLUDE时判断非ids.has。

SelectionAction保留TOGGLE、SELECT_PAGE、INVERT_PAGE、CLEAR，表头整批取消用DESELECT_PAGE，新增SELECT_ALL、INVERT_ALL。TOGGLE按模式增删例外；SELECT_PAGE在INCLUDE合并页ID，在EXCLUDE移除页排除；DESELECT_PAGE在INCLUDE移除页ID，在EXCLUDE加入页排除；与其他页动作相同，超容量时整批拒绝。INVERT_PAGE只对页ID翻转例外。双全局反选恢复原状态，不读取其他分页。总数由当前有效pageResult.total传入Hook而非复制订单对象；显示count为INCLUDE的ids.size或EXCLUDE的max(0,total-ids.size)，列表数量仅参考，受理时后端重新计算真正匹配的排除交集。

不同筛选/POP/CLEAR/重置回到INCLUDE空集合；同筛选翻页/排序不清选择；加载或失败保留意图但禁用范围操作。全部取消后count为0，导出禁用；clear按模式或例外非空可用。例外ID集合最多200000项，超过时操作不生效并展示原因，不能静默裁剪。只有用户取消的例外或显式选择ID保存在内存，不构造全部匹配ID。

## 请求及冻结

INCLUDE生成原SELECTED.orderIds；EXCLUDE生成FILTERED.filter与可选excludedOrderIds，空排除省略字段。filter取已提交查询，不含草稿/分页/排序；数组在确认时与filter一起深度冻结，UNKNOWN重试原请求/原键。字段类型和ID格式见[新增契约](contracts/export-scope.md)。

## 后端及持久化

ExportScope增加excludedOrderIds: List<Long>，内部统一为不可变空/非空集合；保持旧三参数构造器兼容原调用。SELECTED禁止排除和filter，FILTERED禁止orderIds；仅允许正Long字符串转入，去重排序和集合长度上限。非空排除ID必须仍存在于只读来源；不匹配当前filter但存在的排除ID不影响计数。

复用现有export_task_orders(task_id,order_id)及主键/FK，按任务scope_type解释关联：SELECTED表示包含ID，FILTERED表示排除ID。旧FILTERED没有关联，默认空排除；filter_json仍为旧OrderFilter结构，不写JSON新信封，不增加列/表或重写迁移。任务/关联/幂等/Outbox仍由创建事务统一提交，失败整笔回滚。Mapper入口改为中性insertScopeOrderIds/findScopeOrderIds，按1000项写关联并参数化查询；readBatch对FILTERED用NOT EXISTS排除关联，对SELECTED仍用EXISTS包含。

受理前计数根据同一filter和排除列表计算；存在性/匹配排除计数按1000项分批，避免单SQL海量占位符。真实数量为筛选总数减匹配排除数量，超上限/空范围沿用错误；来源变更在受理与执行间仍由既有数量/断点一致性检查处理，不承诺MVCC长快照。

## 幂等与恢复身份

无排除请求继续canonical version1，并保持旧三字段scope JSON属性顺序及null字段字节编码，不能因新record字段自动序列化而改变旧摘要。非空排除采用version2，包含去重排序后的排除ID；同键改集合冲突。TaskSnapshot.configurationHash在无排除时保留configuration-v1完全相同编码，非空排除用configuration-v2并纳入排除集合。旧断点/READY配置身份不变，新范围不能复用不同排除集合的块。

不改变文件、执行权、lease/fence或消息协议：消息仍只带taskId，恢复从数据库读回包含排除的范围。发布前后端需同一批版本，旧后端会拒绝新字段；含排除任务受理后不能只回退旧Worker消费此类任务，应停止消费并保留任务，部署本版修正后恢复。无数据清理或来源改写。

## 日期表单

DatePicker使用Dayjs值及showTime，RHF继续存createdFromLocal/createdToLocal香港墙上时间字符串或undefined。桥接用customParseFormat及utc插件严格解析YYYY-MM-DD HH:mm:ss，UTC模式只用于承载所见年月日时分秒，避免浏览器本地夏令时跳时使香港合法时间被拒绝或改写；它不代表接口时间戳。控件格式化组件值后交给既有toUtcInstant，禁止直接对Dayjs调用toISOString作为业务转换。只做编辑不提交查询；单侧条件、清空、URL恢复、重置与冻结沿用现有模型。AntD公开DatePicker.generatePicker的Dayjs UTC适配同时接管文本解析与日历运算，内部locale.parse使用严格UTC解析；默认本机解析不会进入本表单。直接声明已有锁文件中的dayjs1.11.23依赖；不引入新的日期库。
