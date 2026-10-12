# 功能规格：全量选择、全部反选与日期时间控件

**标识／版本／日期**：003-select-all-filtered／0.1已批准／2026-10-12；未创建Git分支。  
**状态**：负责人回复“开始吧”批准0.1进入测试先行；负责人于测试阶段审批请求后回复“继续开发”，批准阶段及覆盖缺口；2026-10-13：功能实施、代码review、功能回归及质量门禁已完成；性能/规模和原浏览器免验保持，免验不记实测通过。  
**输入**：负责人要求“全选当前条件下的所有数据”，明确“切到每个页面看到的都是选中的，支持用户自己逐条取消，也支持全部反选”，并要求“时间应该用antd的时间选择器”。

## 规格交付与阶段边界

本次新增选择行为、可选请求字段和任务关联范围语义，按[风险规则](../../.specify/memory/constitution.md#风险分级)定为中风险：不改物理数据库结构、来源订单或既有公共接口语义。目录及完整函数草稿见[plan.md](plan.md)，选择/持久化设计见[data-model.md](data-model.md)，新增请求字段见[契约](contracts/export-scope.md)，追踪与两次审批见[tasks.md](tasks.md)。核心规格批准后才能生成测试；测试门禁及负责人第二次审批通过后实施，既有订单0.4授权不代替本次门禁。

范围：增加全选筛选结果和全部反选，保留各页逐行/表头/当前页工具；把例外集合传入既有创建接口并贯穿计数、幂等、持久化、执行和恢复；开始/结束时间改为两个Ant Design DatePicker（showTime，秒级），保留单侧条件能力。现有导出筛选结果入口仍表示整个条件范围，导出已选遵循当前选择意图。

非目标：加载全量ID、新订单列表接口、造数据、新容器、修改源订单、修改V1–V4迁移、新物理表、任务页/SSE、性能专项。实际订单直接复用已有容器，测试只隔离本次任务资源。

## 用户故事

### US1 全量选择和任意反选（P1）

运营人员提交筛选后全选全部匹配订单，翻页逐条取消或恢复，使用全局反选快速选择补集；通过状态、Hook与真实页面组件测试独立验证。验收关联：[全量范围](../../docs/product/acceptance-criteria.md#ac-073)、[生命周期](../../docs/product/acceptance-criteria.md#ac-074)、[边界提示](../../docs/product/acceptance-criteria.md#ac-076)、[逐条操作](../../docs/product/acceptance-criteria.md#ac-077)、[全部反选](../../docs/product/acceptance-criteria.md#ac-078)，原当前页工具回归关联[既有选择工具](../../docs/product/acceptance-criteria.md#ac-033)。

### US2 按实际选择生成文件（P1）

用户通过导出已选提交含排除项的条件范围，确认、拒绝和结果不明时选择状态一致，文件不会重新包含取消项。通过请求契约、真实MySQL和小范围实际Excel/恢复测试独立验证。验收关联：[全量导出](../../docs/product/acceptance-criteria.md#ac-075)、[持久化与兼容](../../docs/product/acceptance-criteria.md#ac-079)、[原筛选导出](../../docs/product/acceptance-criteria.md#ac-008)、[上限](../../docs/product/acceptance-criteria.md#ac-019)、[拒绝](../../docs/product/acceptance-criteria.md#ac-030)、[UNKNOWN](../../docs/product/acceptance-criteria.md#ac-051)、[成功留页](../../docs/product/acceptance-criteria.md#ac-072)。

### US3 用日期时间控件筛选（P1）

用户通过组件选择秒级起止时间、单侧选择或清空，再提交查询；通过真实Ant Design控件与UTC转换测试验证。验收关联：[日期时间控件](../../docs/product/acceptance-criteria.md#ac-080)，回归关联[重置](../../docs/product/acceptance-criteria.md#ac-003)、[范围校验](../../docs/product/acceptance-criteria.md#ac-005)和[香港时区与时间边界](../../docs/product/acceptance-criteria.md#ac-022)。

## 功能需求与范围设计

- FR-001：新增跨页范围按钮与全局补集操作；选择以INCLUDE或EXCLUDE加例外ID表达，范围身份仅由已提交筛选及历史版本确定，追踪[全量范围](../../docs/product/acceptance-criteria.md#ac-073)及[全部反选](../../docs/product/acceptance-criteria.md#ac-078)。
- FR-002：行、表头和当前页工具在两种模式下都可操作，筛选变化/清空/重置正确清理，追踪[逐条操作](../../docs/product/acceptance-criteria.md#ac-077)及[生命周期](../../docs/product/acceptance-criteria.md#ac-074)。
- FR-003：FILTERED增加excludedOrderIds可选字段，空排除规范化为旧请求，非空范围在任务关联表持久化；计数与分批读取使用相同排除语义，追踪[持久化与兼容](../../docs/product/acceptance-criteria.md#ac-079)。
- FR-004：全局范围意图、例外集合、字段及幂等键在confirm时冻结，错误不丢取消项，追踪[全量导出](../../docs/product/acceptance-criteria.md#ac-075)及[边界提示](../../docs/product/acceptance-criteria.md#ac-076)。
- FR-005：日期控件桥接RHF原有香港本地字符串，复用既有UTC校验；不依赖浏览器时区做时间戳转换，追踪[日期时间控件](../../docs/product/acceptance-criteria.md#ac-080)。

边界：未提交草稿、同条件刷新、不同筛选、POP、旧响应、0条、超过上限、全部排除、重复/非法/过长ID、未知字段、旧请求摘要兼容、旧任务恢复、跨时区、单侧时间、清空和倒置范围。服务端重新计算当前实际集合，列表显示是参考数量，外部来源变化继续按已有源变化失败处理。

## 验收与成功映射

完整定义仅在[唯一基线](../../docs/product/acceptance-criteria.md#全选筛选结果增量草稿)维护；各项实施/测试双向映射及用例TC-301至TC-316见[tasks.md](tasks.md#ac实现任务映射)。新增TC-301至TC-316已编写并执行；[验证记录](../../docs/validation/003-select-all-filtered/verification.md)保留预期红灯、旧行为基线及审批时未到达断言，并另列实施后的实际通过证据。负责人已批准测试阶段及覆盖缺口，生产功能已实施；旧TC-207至TC-211等不充当新增红灯。质量目标关联[增量质量门禁](../../docs/product/acceptance-criteria.md#sc-015)，最终状态以任务及实际门禁记录为准，不增加性能测量。

## 已确认前提

上述交互方向由负责人在本会话明确；技术方案0.1已获规格批准，测试先行已执行；生产实现仍待第二次批准。queryOrders/createExportTask继续集中在[统一API](../../frontend/src/shared/api/api.ts)，不新建功能目录API。React/Ant Design、Java/MyBatis及恢复架构沿用[既有决策](../002-order-management/plan.md)。本次方案复用export_task_orders关联表：SELECTED关联表示包含，FILTERED关联表示排除；物理结构与来源不变，旧FILTERED任务无关联即无排除。

没有仍需产品澄清的选择问题。日期组件内部解析采用公开适配接口并已在UTC/NY复验通过，[技术核验事项已关闭](../../docs/decisions-pending.md#日期控件内部文本解析核验)。DatePicker选型为两个独立日期时间控件，保留既有单侧条件。既有免验及取消性能保持，不将免验当实测达标。
