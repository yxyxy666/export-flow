# 任务：任务列表、SSE进度、暂停与装配修复

**版本**：0.3　**日期**：2026-10-17　**状态**：规格0.3、测试阶段及缺口处理方案已批准；本期生产实施与适用验证已完成，排除项保持未验证。  
输入：[规格](spec.md)、[计划](plan.md)、[函数清单](function-design.md)、[模型](data-model.md)、[协议](contracts/http-sse.md)、[执行契约](contracts/execution-control.md)、[调查](research.md)、[暂停ADR](../../docs/adr/0010-task-pause-control-barrier.md)。

负责人已明确“仅做列表/SSE/暂停，增删改先预留”。范围确认不等于规格或测试阶段审批；不实施失败后用户手动重试/继续、名称、删除；新增同任务装配异常自动修复，见[修复契约](contracts/assembly-repair.md)。任务ID在本规格唯一；下列测试ID为本期编号；既有回归保留原编号，原测试阶段缺口及实施补证均见[缺口记录](../../docs/validation/004-task-management/coverage-gaps.md)。

## 审批记录

| 门禁 | 版本／范围 | 状态 | 批准人 | 日期 | 结论依据 |
| --- | --- | --- | --- | --- | --- |
| 产品范围确认 | 仅列表/SSE/暂停，增删改先预留 | 已确认范围 | 项目负责人 | 2026-10-14 | 本会话明确选择；不构成以下批准 |
| 项目级效率规则 | [规则草案0.1](../../docs/rules/testing-efficiency-proposal.md) | 未批准 | 待负责人 | — | 未收到明确审批 |
| 暂停架构接受 | [ADR-0010](../../docs/adr/0010-task-pause-control-barrier.md) | 已随当前规格接受 | 项目负责人 | 2026-10-17 | “可以按照当前规格开始了”；仅进入测试先行 |
| 自动修复需求确认 | 装配对账／自动局部重建／可信进度／黄色提示 | 已确认需求方向 | 项目负责人 | 2026-10-17 | 本会话明确要求加入规格，不构成开发或架构审批 |
| 修复架构接受 | [ADR-0011](../../docs/adr/0011-selective-block-repair-and-progress.md)及3轮预算 | 已随当前规格接受 | 项目负责人 | 2026-10-17 | “可以按照当前规格开始了”；保留第二次门禁 |
| 规格审批 | 004版本0.3全部核心/附属文档、新验收及本期排除清单 | 已批准 | 项目负责人 | 2026-10-17 | “可以按照当前规格开始了”；授权T-001至008及T-016，不授权生产实施 |
| 测试先行阶段审批 | 004版本0.3，65项基础用例、EX-001/002与6类缺口处理方案；T-010至018实施 | 已批准 | 项目负责人 | 2026-10-17 | 对上轮明确审批请求回复“继续”；缺口按方案先补断言再实施，不代表永久免验或已经验收 |

## 执行任务与依赖

本期统一测试阶段已获批；以下勾选对应实际工作，审批事实不替代功能验证。

- [x] T-001 [AC/SC：不适用——测试准备] 已核对规格/ADR审批，复用已有三容器及隔离夹具；连接与实际测试证据见[验证记录](../../docs/validation/004-task-management/verification.md)。
- [x] T-002 [US1] [AC-012](../../docs/product/acceptance-criteria.md#ac-012)、[AC-026](../../docs/product/acceptance-criteria.md#ac-026)、[AC-027](../../docs/product/acceptance-criteria.md#ac-027)、[AC-028](../../docs/product/acceptance-criteria.md#ac-028)、[AC-031](../../docs/product/acceptance-criteria.md#ac-031)、[AC-038](../../docs/product/acceptance-criteria.md#ac-038)、[AC-081](../../docs/product/acceptance-criteria.md#ac-081)、[AC-084](../../docs/product/acceptance-criteria.md#ac-084)、[AC-086](../../docs/product/acceptance-criteria.md#ac-086) 编写列表查询/显示、非法输入、URL与分页测试TC-401。
- [x] T-003 [US2] [AC-012](../../docs/product/acceptance-criteria.md#ac-012)、[AC-028](../../docs/product/acceptance-criteria.md#ac-028)、[AC-031](../../docs/product/acceptance-criteria.md#ac-031)、[AC-039](../../docs/product/acceptance-criteria.md#ac-039)、[AC-040](../../docs/product/acceptance-criteria.md#ac-040)、[AC-042](../../docs/product/acceptance-criteria.md#ac-042)、[AC-044](../../docs/product/acceptance-criteria.md#ac-044)、[AC-072](../../docs/product/acceptance-criteria.md#ac-072)、[AC-082](../../docs/product/acceptance-criteria.md#ac-082)、[AC-084](../../docs/product/acceptance-criteria.md#ac-084) 编写TC-402/403，覆盖SSE版本衔接、生命周期与创建通知，不写计时用例。
- [x] T-004 [US3] [AC-049](../../docs/product/acceptance-criteria.md#ac-049)、[AC-083](../../docs/product/acceptance-criteria.md#ac-083)、[AC-084](../../docs/product/acceptance-criteria.md#ac-084)、[AC-086](../../docs/product/acceptance-criteria.md#ac-086)、[AC-087](../../docs/product/acceptance-criteria.md#ac-087) 编写TC-404/407，暂停屏障、竞态、独立控制版本和同操作找回。
- [x] T-005 [US3] [AC-018](../../docs/product/acceptance-criteria.md#ac-018)、[AC-049](../../docs/product/acceptance-criteria.md#ac-049)、[AC-067](../../docs/product/acceptance-criteria.md#ac-067)、[AC-068](../../docs/product/acceptance-criteria.md#ac-068)、[AC-069](../../docs/product/acceptance-criteria.md#ac-069)、[AC-083](../../docs/product/acceptance-criteria.md#ac-083) 编写TC-405，真实MySQL/文件guard与保护引用、已暂停重启不生成、正常小文件回归。
- [x] T-006 [US2] [AC-039](../../docs/product/acceptance-criteria.md#ac-039)、[AC-042](../../docs/product/acceptance-criteria.md#ac-042)、[AC-043](../../docs/product/acceptance-criteria.md#ac-043)、[AC-050](../../docs/product/acceptance-criteria.md#ac-050)、[AC-081](../../docs/product/acceptance-criteria.md#ac-081)、[AC-082](../../docs/product/acceptance-criteria.md#ac-082)、[AC-085](../../docs/product/acceptance-criteria.md#ac-085) 编写TC-406/411，真实通知、回源、回环SSE和屏障控制的空闲竞态。
- [x] T-007 [US4] [AC-013](../../docs/product/acceptance-criteria.md#ac-013)、[AC-014](../../docs/product/acceptance-criteria.md#ac-014)、[AC-015](../../docs/product/acceptance-criteria.md#ac-015)、[AC-084](../../docs/product/acceptance-criteria.md#ac-084) 编写TC-408及选定旧回归，复用下载且不展示预留动作。
- [x] T-016 [US5] [AC-088](../../docs/product/acceptance-criteria.md#ac-088)、[AC-089](../../docs/product/acceptance-criteria.md#ac-089)、[AC-090](../../docs/product/acceptance-criteria.md#ac-090)、[AC-091](../../docs/product/acceptance-criteria.md#ac-091) 编写TC-414至417及受影响迁移／通知回归，覆盖装配全对账、选择性重建、预算与并发、可信进度回退和黄色提示；基础用例已编写并执行，完整变体缺口见[清单](../../docs/validation/004-task-management/coverage-gaps.md)。
- [x] T-008 [AC/SC：不适用——测试阶段材料] 已运行新增测试基础集及选定基线，汇总行为红灯、基线绿灯与接口／迁移缺口；见[实际报告](../../docs/validation/004-task-management/verification.md)，不宣称所有计划变体均已覆盖。
- [x] T-009 [AC/SC：不适用——阶段审批] 提交T-001至008及T-016材料及免验范围，等负责人明确批准测试阶段后再实施。
- [x] T-010 [US1/4] [AC-012](../../docs/product/acceptance-criteria.md#ac-012)、[AC-013](../../docs/product/acceptance-criteria.md#ac-013)、[AC-014](../../docs/product/acceptance-criteria.md#ac-014)、[AC-015](../../docs/product/acceptance-criteria.md#ac-015)、[AC-026](../../docs/product/acceptance-criteria.md#ac-026)、[AC-027](../../docs/product/acceptance-criteria.md#ac-027)、[AC-028](../../docs/product/acceptance-criteria.md#ac-028)、[AC-031](../../docs/product/acceptance-criteria.md#ac-031)、[AC-038](../../docs/product/acceptance-criteria.md#ac-038)、[AC-081](../../docs/product/acceptance-criteria.md#ac-081)、[AC-084](../../docs/product/acceptance-criteria.md#ac-084)、[AC-086](../../docs/product/acceptance-criteria.md#ac-086) 实施列表接口、快照、前端筛选/分页/元数据和已有下载链接。
- [x] T-011 [US2] [AC-012](../../docs/product/acceptance-criteria.md#ac-012)、[AC-028](../../docs/product/acceptance-criteria.md#ac-028)、[AC-031](../../docs/product/acceptance-criteria.md#ac-031)、[AC-039](../../docs/product/acceptance-criteria.md#ac-039)、[AC-040](../../docs/product/acceptance-criteria.md#ac-040)、[AC-042](../../docs/product/acceptance-criteria.md#ac-042)、[AC-043](../../docs/product/acceptance-criteria.md#ac-043)、[AC-044](../../docs/product/acceptance-criteria.md#ac-044)、[AC-050](../../docs/product/acceptance-criteria.md#ac-050)、[AC-072](../../docs/product/acceptance-criteria.md#ac-072)、[AC-081](../../docs/product/acceptance-criteria.md#ac-081)、[AC-082](../../docs/product/acceptance-criteria.md#ac-082)、[AC-084](../../docs/product/acceptance-criteria.md#ac-084)、[AC-085](../../docs/product/acceptance-criteria.md#ac-085) 实施统一版本/可靠通知/缓存、SSE与前端单例及订单创建通知。
- [x] T-012 [US3] [AC-014](../../docs/product/acceptance-criteria.md#ac-014)、[AC-018](../../docs/product/acceptance-criteria.md#ac-018)、[AC-049](../../docs/product/acceptance-criteria.md#ac-049)、[AC-083](../../docs/product/acceptance-criteria.md#ac-083)、[AC-084](../../docs/product/acceptance-criteria.md#ac-084)、[AC-086](../../docs/product/acceptance-criteria.md#ac-086)、[AC-087](../../docs/product/acceptance-criteria.md#ac-087) 实施暂停接口、状态约束、controlVersion、操作回执、原命令封存与停止对账。
- [x] T-013 [US3] [AC-018](../../docs/product/acceptance-criteria.md#ac-018)、[AC-049](../../docs/product/acceptance-criteria.md#ac-049)、[AC-067](../../docs/product/acceptance-criteria.md#ac-067)、[AC-068](../../docs/product/acceptance-criteria.md#ac-068)、[AC-069](../../docs/product/acceptance-criteria.md#ac-069)、[AC-083](../../docs/product/acceptance-criteria.md#ac-083) 修改所有执行/块/READY/终态登记边界及暂停結果分类，保证guard关闭与引用保护。
- [x] T-017 [US5] [AC-088](../../docs/product/acceptance-criteria.md#ac-088)、[AC-089](../../docs/product/acceptance-criteria.md#ac-089)、[AC-090](../../docs/product/acceptance-criteria.md#ac-090)、[AC-091](../../docs/product/acceptance-criteria.md#ac-091) 在第二次门禁明确获批后实施V6／分段清单／规范摘要／有界重读／选择CAS／原生成器修复循环／可信计数与可靠通知，逐文件见函数设计增量。
- [x] T-018 [US5] [AC-090](../../docs/product/acceptance-criteria.md#ac-090)、[AC-091](../../docs/product/acceptance-criteria.md#ac-091) 在第二次门禁明确获批后实施新版本低计数合并、修复摘要schema和黄色Tooltip／键盘提示，复用现有表格。
- [x] T-014 [SC-005](../../docs/product/acceptance-criteria.md#sc-005)、[SC-012](../../docs/product/acceptance-criteria.md#sc-012)、[SC-016](../../docs/product/acceptance-criteria.md#sc-016)、[SC-017](../../docs/product/acceptance-criteria.md#sc-017) 运行一次适用功能回归与质量门禁，保留红灯历史并追加绿灯，免验用例精确过滤；前端116项、后端141项去重通过，修正只重跑受影响范围，见[最终结果](../../docs/validation/004-task-management/verification.md#最终功能与质量结果)。
- [x] T-015 [SC-011](../../docs/product/acceptance-criteria.md#sc-011) [AC/SC：文档追踪部分不适用] 已完成1280×720页面/键盘检查、实际证据、文档引用、迁移/回退说明和映射更新，见[页面证据](../../docs/validation/004-task-management/verification.md#浏览器与可访问性)。

依赖：T-001先于T-002至007及T-016；T-008汇总全部测试材料，T-016与T-002至007一起先行，T-008包含全部新增材料；T-009明确批准才允许T-010至013及T-017/018。实施中基础迁移/控制屏障与通知须同批对齐，不部署能写新状态但不识别新控制版本的Worker。T-017的数据摘要与T-018消费字段须同批兼容；T-014/015在全部实施后，不为计数重复执行已通过范围。

## AC—实现任务映射

| 唯一标准 | 实现任务 | 本次负责范围 |
| --- | --- | --- |
| [AC-012](../../docs/product/acceptance-criteria.md#ac-012) | T-010/011 | 列表进度与SSE合并 |
| [AC-013](../../docs/product/acceptance-criteria.md#ac-013) | T-010 | 复用成功下载 |
| [AC-014](../../docs/product/acceptance-criteria.md#ac-014) | T-010/012 | 六态中非成功下载禁用 |
| [AC-015](../../docs/product/acceptance-criteria.md#ac-015) | T-010 | 成功元数据 |
| [AC-018](../../docs/product/acceptance-criteria.md#ac-018) | T-012/013 | 暂停不删除任务或文件 |
| [AC-026](../../docs/product/acceptance-criteria.md#ac-026) | T-010 | 既有状态筛选；新增状态另由扩展标准 |
| [AC-027](../../docs/product/acceptance-criteria.md#ac-027) | T-010 | 稳定分页/页大小 |
| [AC-028](../../docs/product/acceptance-criteria.md#ac-028) | T-010/011 | 固定倒序与进度不重排 |
| [AC-031](../../docs/product/acceptance-criteria.md#ac-031) | T-010/011 | 刷新保页 |
| [AC-038](../../docs/product/acceptance-criteria.md#ac-038) | T-010 | 任务编号/文件名AND状态 |
| [AC-039](../../docs/product/acceptance-criteria.md#ac-039) | T-011 | 原活跃/终态通道；暂停扩展另映射 |
| [AC-040](../../docs/product/acceptance-criteria.md#ac-040) | T-011 | 唯一通道及可见性 |
| [AC-042](../../docs/product/acceptance-criteria.md#ac-042) | T-011 | 显式idle/当前连接，宽限计时EX-002 |
| [AC-043](../../docs/product/acceptance-criteria.md#ac-043) | T-011 | 活动序列竞态，宽限计时EX-002 |
| [AC-044](../../docs/product/acceptance-criteria.md#ac-044) | T-011 | 进入/可见/联网/创建/手动刷新，退避计时EX-002 |
| [AC-049](../../docs/product/acceptance-criteria.md#ac-049) | T-012/013 | 原命令注册/重复消息/持久暂停确认，无等待时长验证 |
| [AC-050](../../docs/product/acceptance-criteria.md#ac-050) | T-011 | 原创建事务提交与通知回滚边界 |
| [AC-067](../../docs/product/acceptance-criteria.md#ac-067) | T-013 | 小范围未暂停任务真实文件及旧生成链路回归 |
| [AC-068](../../docs/product/acceptance-criteria.md#ac-068) | T-013 | 受影响非计时身份/旧fence与控制校验；到期接管EX-002 |
| [AC-069](../../docs/product/acceptance-criteria.md#ac-069) | T-013 | 暂停仍保护断点/READY/成功引用 |
| [AC-072](../../docs/product/acceptance-criteria.md#ac-072) | T-011 | 创建确认仅广播，原留页/no状态查询 |
| [AC-081](../../docs/product/acceptance-criteria.md#ac-081) | T-010/011 | MySQL一致快照和全环境活动 |
| [AC-082](../../docs/product/acceptance-criteria.md#ac-082) | T-011 | ready桥接、版本/成员与query代次 |
| [AC-083](../../docs/product/acceptance-criteria.md#ac-083) | T-012/013 | 暂停屏障、停止回执与认领/完成竞态 |
| [AC-084](../../docs/product/acceptance-criteria.md#ac-084) | T-010/011/012 | 六态展示、筛选、活跃和动作 |
| [AC-085](../../docs/product/acceptance-criteria.md#ac-085) | T-011 | Outbox CAS、多实例通知、回源/断线补偿 |
| [AC-086](../../docs/product/acceptance-criteria.md#ac-086) | T-010/012 | 输入、安全错误、URL/越界页 |
| [AC-087](../../docs/product/acceptance-criteria.md#ac-087) | T-012 | 独立控制版本及同操作UNKNOWN找回 |
| [AC-088](../../docs/product/acceptance-criteria.md#ac-088) | T-017 | 装配入口／读取／候选校验及安全分类 |
| [AC-089](../../docs/product/acceptance-criteria.md#ac-089) | T-017 | 同任务预算／独立分段基线／选择性替换／暂停保护 |
| [AC-090](../../docs/product/acceptance-criteria.md#ac-090) | T-017/018 | 可信量、版本、缓存及SSE新版本低计数 |
| [AC-091](../../docs/product/acceptance-criteria.md#ac-091) | T-018 | 持久修复提示与黄色图标生命周期 |

## AC—测试用例映射

| 唯一标准 | 计划用例 | 测试任务 | 红灯／缺口状态 |
| --- | --- | --- | --- |
| [AC-012](../../docs/product/acceptance-criteria.md#ac-012) | TC-401/402 | T-002/003 | 已实施；红绿灯与补证见[记录](../../docs/validation/004-task-management/verification.md)，计时／性能排除仍未验证 |
| [AC-013](../../docs/product/acceptance-criteria.md#ac-013) | TC-408 | T-007 | 已实施；红绿灯与补证见[记录](../../docs/validation/004-task-management/verification.md)，计时／性能排除仍未验证 |
| [AC-014](../../docs/product/acceptance-criteria.md#ac-014) | TC-408 | T-007 | 已实施；红绿灯与补证见[记录](../../docs/validation/004-task-management/verification.md)，计时／性能排除仍未验证 |
| [AC-015](../../docs/product/acceptance-criteria.md#ac-015) | TC-408 | T-007 | 已实施；红绿灯与补证见[记录](../../docs/validation/004-task-management/verification.md)，计时／性能排除仍未验证 |
| [AC-018](../../docs/product/acceptance-criteria.md#ac-018) | TC-405 | T-005 | 已实施；红绿灯与补证见[记录](../../docs/validation/004-task-management/verification.md)，计时／性能排除仍未验证 |
| [AC-026](../../docs/product/acceptance-criteria.md#ac-026) | TC-401 | T-002 | 已实施；红绿灯与补证见[记录](../../docs/validation/004-task-management/verification.md)，计时／性能排除仍未验证 |
| [AC-027](../../docs/product/acceptance-criteria.md#ac-027) | TC-401 | T-002 | 已实施；红绿灯与补证见[记录](../../docs/validation/004-task-management/verification.md)，计时／性能排除仍未验证 |
| [AC-028](../../docs/product/acceptance-criteria.md#ac-028) | TC-401/402 | T-002/003 | 已实施；红绿灯与补证见[记录](../../docs/validation/004-task-management/verification.md)，计时／性能排除仍未验证 |
| [AC-031](../../docs/product/acceptance-criteria.md#ac-031) | TC-401/402 | T-002/003 | 已实施；红绿灯与补证见[记录](../../docs/validation/004-task-management/verification.md)，计时／性能排除仍未验证 |
| [AC-038](../../docs/product/acceptance-criteria.md#ac-038) | TC-401 | T-002 | 已实施；红绿灯与补证见[记录](../../docs/validation/004-task-management/verification.md)，计时／性能排除仍未验证 |
| [AC-039](../../docs/product/acceptance-criteria.md#ac-039) | TC-402/411 | T-003/006 | 已实施；红绿灯与补证见[记录](../../docs/validation/004-task-management/verification.md)，计时／性能排除仍未验证 |
| [AC-040](../../docs/product/acceptance-criteria.md#ac-040) | TC-403 | T-003 | 已实施；红绿灯与补证见[记录](../../docs/validation/004-task-management/verification.md)，计时／性能排除仍未验证 |
| [AC-042](../../docs/product/acceptance-criteria.md#ac-042) | TC-403/411 | T-003/006 | 已实施；红绿灯与补证见[记录](../../docs/validation/004-task-management/verification.md)，计时／性能排除仍未验证 |
| [AC-043](../../docs/product/acceptance-criteria.md#ac-043) | TC-406/411 | T-006 | 已实施；红绿灯与补证见[记录](../../docs/validation/004-task-management/verification.md)，计时／性能排除仍未验证 |
| [AC-044](../../docs/product/acceptance-criteria.md#ac-044) | TC-403 | T-003 | 已实施；红绿灯与补证见[记录](../../docs/validation/004-task-management/verification.md)，计时／性能排除仍未验证 |
| [AC-049](../../docs/product/acceptance-criteria.md#ac-049) | TC-404/405 | T-004/005 | 已实施；红绿灯与补证见[记录](../../docs/validation/004-task-management/verification.md)，计时／性能排除仍未验证 |
| [AC-050](../../docs/product/acceptance-criteria.md#ac-050) | TC-406 | T-006 | 已实施；红绿灯与补证见[记录](../../docs/validation/004-task-management/verification.md)，计时／性能排除仍未验证 |
| [AC-067](../../docs/product/acceptance-criteria.md#ac-067) | TC-405 | T-005 | 已实施；红绿灯与补证见[记录](../../docs/validation/004-task-management/verification.md)，计时／性能排除仍未验证 |
| [AC-068](../../docs/product/acceptance-criteria.md#ac-068) | TC-405 | T-005 | 已实施；红绿灯与补证见[记录](../../docs/validation/004-task-management/verification.md)，计时／性能排除仍未验证 |
| [AC-069](../../docs/product/acceptance-criteria.md#ac-069) | TC-405 | T-005 | 已实施；红绿灯与补证见[记录](../../docs/validation/004-task-management/verification.md)，计时／性能排除仍未验证 |
| [AC-072](../../docs/product/acceptance-criteria.md#ac-072) | TC-403 | T-003 | 已实施；红绿灯与补证见[记录](../../docs/validation/004-task-management/verification.md)，计时／性能排除仍未验证 |
| [AC-081](../../docs/product/acceptance-criteria.md#ac-081) | TC-401/406/409 | T-002/006 | 已实施；红绿灯与补证见[记录](../../docs/validation/004-task-management/verification.md)，计时／性能排除仍未验证 |
| [AC-082](../../docs/product/acceptance-criteria.md#ac-082) | TC-402/403/411 | T-003/006 | 已实施；红绿灯与补证见[记录](../../docs/validation/004-task-management/verification.md)，计时／性能排除仍未验证 |
| [AC-083](../../docs/product/acceptance-criteria.md#ac-083) | TC-404/405 | T-004/005 | 已实施；红绿灯与补证见[记录](../../docs/validation/004-task-management/verification.md)，计时／性能排除仍未验证 |
| [AC-084](../../docs/product/acceptance-criteria.md#ac-084) | TC-401/403/404/408 | T-002/003/004/007 | 已实施；红绿灯与补证见[记录](../../docs/validation/004-task-management/verification.md)，计时／性能排除仍未验证 |
| [AC-085](../../docs/product/acceptance-criteria.md#ac-085) | TC-406/411 | T-006 | 已实施；红绿灯与补证见[记录](../../docs/validation/004-task-management/verification.md)，计时／性能排除仍未验证 |
| [AC-086](../../docs/product/acceptance-criteria.md#ac-086) | TC-401/407 | T-002/004 | 已实施；红绿灯与补证见[记录](../../docs/validation/004-task-management/verification.md)，计时／性能排除仍未验证 |
| [AC-087](../../docs/product/acceptance-criteria.md#ac-087) | TC-404/407 | T-004 | 已实施；红绿灯与补证见[记录](../../docs/validation/004-task-management/verification.md)，计时／性能排除仍未验证 |
| [AC-088](../../docs/product/acceptance-criteria.md#ac-088) | TC-409/414/415 | T-016 | 已实施；红绿灯与补证见[记录](../../docs/validation/004-task-management/verification.md)，计时／性能排除仍未验证 |
| [AC-089](../../docs/product/acceptance-criteria.md#ac-089) | TC-415 | T-016 | 已实施；红绿灯与补证见[记录](../../docs/validation/004-task-management/verification.md)，计时／性能排除仍未验证 |
| [AC-090](../../docs/product/acceptance-criteria.md#ac-090) | TC-416 | T-016 | 已实施；红绿灯与补证见[记录](../../docs/validation/004-task-management/verification.md)，计时／性能排除仍未验证 |
| [AC-091](../../docs/product/acceptance-criteria.md#ac-091) | TC-417 | T-016 | 已实施；红绿灯与补证见[记录](../../docs/validation/004-task-management/verification.md)，计时／性能排除仍未验证 |

表中既有复合标准仅负责本次受影响断言，不把原阶段证据改写成004证据。原新增七项及本次四项标准完整映射；不把控制设计完成记作测试到达。


## SC—验证映射

| 唯一标准 | 验证用例／任务 | 环境 | 证据计划与状态 |
| --- | --- | --- | --- |
| [SC-005](../../docs/product/acceptance-criteria.md#sc-005) | TC-401/402/404/408/411；T-014 | 本地前后端+真实小范围任务/SSE | 成功／失败实际进度与错误已由真实接口、文件及前端用例验证；见验证记录 |
| [SC-011](../../docs/product/acceptance-criteria.md#sc-011) | TC-412；T-015 | Windows内置Chromium浏览器、1280×720、键盘 | 真实页面+合成响应视觉检查已通过；截图、黄色图标和键盘focus见验证记录，不新增E2E |
| [SC-012](../../docs/product/acceptance-criteria.md#sc-012) | TC-413；T-014 | Node22/pnpm锁文件/Java21、过滤后verify、已有真实隔离依赖 | 冻结安装、前端适用回归及含类型检查构建、后端适用回归及verify质量门禁通过；JaCoCo已生成且历史版本提示限制覆盖统计；[证据](../../docs/validation/004-task-management/verification.md#最终功能与质量结果)，排除清单不计通过 |
| [SC-016](../../docs/product/acceptance-criteria.md#sc-016) | TC-404/405/407；T-014 | 真实MySQL、原Rabbit命令与已有小范围数据/持久文件 | 控制屏障/ACK/断点引用/重启不生成；实施后安全停止、故障找回和竞态见验证记录，无计时断言 |
| [SC-001](../../docs/product/acceptance-criteria.md#sc-001)、[SC-002](../../docs/product/acceptance-criteria.md#sc-002)、[SC-003](../../docs/product/acceptance-criteria.md#sc-003)、[SC-004](../../docs/product/acceptance-criteria.md#sc-004) | EX-001，仅保留免验映射 | 本轮不准备性能/容量环境 | 负责人要求暂不考虑性能；未验证，不计达标；文件正常可打开的受影响功能另由TC-405验证 |
| [SC-017](../../docs/product/acceptance-criteria.md#sc-017) | TC-414至417；T-014/015/017/018 | 已有真实MySQL/Rabbit/Redis、隔离文件、只读订单及前端组件/键盘 | 局部替换身份、最终内容／顺序、重算进度和提示；实施后真实文件／事务、最终9字段内容及提示见[补证](../../docs/validation/004-task-management/coverage-gaps.md)，不含性能或计时 |

## 测试进度与证据

**测试阶段**：已于2026-10-17获负责人批准，含六类缺口处理方案；生产实施已授权。以下“实际结果”保留测试先行历史，“实施后”及验证记录承接绿灯与补证。原阶段环境及编译正常，入口和真实文件有红灯。已编写并执行65项基础用例（前端28、后端37），2项基线通过、50项首个目标行为断言失败、13项接口／迁移缺口；重复执行及过滤统计详见[实际证据](../../docs/validation/004-task-management/verification.md)。不将深层断言未到达或尚未编写的变体算作已覆盖。

| 用例／任务 | 标准反向链接 | 已创建的测试文件 | 编写进度 | 执行进度／日期 | 实际结果 | 命令／环境／证据计划 | 实施后 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| TC-401／T-002 | [AC-012](../../docs/product/acceptance-criteria.md#ac-012)、[AC-026](../../docs/product/acceptance-criteria.md#ac-026)、[AC-027](../../docs/product/acceptance-criteria.md#ac-027)、[AC-028](../../docs/product/acceptance-criteria.md#ac-028)、[AC-031](../../docs/product/acceptance-criteria.md#ac-031)、[AC-038](../../docs/product/acceptance-criteria.md#ac-038)、[AC-081](../../docs/product/acceptance-criteria.md#ac-081)、[AC-084](../../docs/product/acceptance-criteria.md#ac-084)、[AC-086](../../docs/product/acceptance-criteria.md#ac-086) | [task-query.test.ts](../../frontend/src/features/export-tasks/__tests__/task-query.test.ts)、[TaskManagementContractTest.java](../../backend/src/test/java/com/exportflow/exporttasks/TaskManagementContractTest.java) | 已编写并补充边界；处理见缺口记录 | 已执行／2026-10-17 | 列表GET 405／页面占位红灯；查询深层断言待补证 | unit/component/真实MySQL查询；匹配/过滤/排序/分页/单语句一致快照/展示文件名搜索/未知状态/全局活跃/URL/页越界；[执行证据](../../docs/validation/004-task-management/verification.md)、[缺口](../../docs/validation/004-task-management/coverage-gaps.md) | 已实施并运行；最终汇总见[证据](../../docs/validation/004-task-management/verification.md) |
| TC-402／T-003 | [AC-012](../../docs/product/acceptance-criteria.md#ac-012)、[AC-028](../../docs/product/acceptance-criteria.md#ac-028)、[AC-031](../../docs/product/acceptance-criteria.md#ac-031)、[AC-039](../../docs/product/acceptance-criteria.md#ac-039)、[AC-082](../../docs/product/acceptance-criteria.md#ac-082) | [task-stream.test.tsx](../../frontend/src/features/export-tasks/__tests__/task-stream.test.tsx) | 已编写并补充边界；处理见缺口记录 | 已执行／2026-10-17 | 页面占位红灯；版本／成员合并深层断言未到达 | component；高低版本、重复事件、旧HTTP合并、pure-progress无额外查询、成员变化合并；[执行证据](../../docs/validation/004-task-management/verification.md)、[缺口](../../docs/validation/004-task-management/coverage-gaps.md) | 已实施并运行；最终汇总见[证据](../../docs/validation/004-task-management/verification.md) |
| TC-403／T-003 | [AC-040](../../docs/product/acceptance-criteria.md#ac-040)、[AC-042](../../docs/product/acceptance-criteria.md#ac-042)、[AC-044](../../docs/product/acceptance-criteria.md#ac-044)、[AC-072](../../docs/product/acceptance-criteria.md#ac-072)、[AC-082](../../docs/product/acceptance-criteria.md#ac-082)、[AC-084](../../docs/product/acceptance-criteria.md#ac-084) | [task-stream.test.tsx](../../frontend/src/features/export-tasks/__tests__/task-stream.test.tsx) | 已编写并补充边界；处理见缺口记录 | 已执行／2026-10-17 | 页面占位／未发送列表GET；生命周期与创建通知未到达 | component；当前连接idle及最终快照/关闭原EventSource后单连接重连/可见/离线/ready/本浏览器创建通知；无计时断言；[执行证据](../../docs/validation/004-task-management/verification.md)、[缺口](../../docs/validation/004-task-management/coverage-gaps.md) | 已实施并运行；最终汇总见[证据](../../docs/validation/004-task-management/verification.md) |
| TC-404／T-004 | [AC-049](../../docs/product/acceptance-criteria.md#ac-049)、[AC-083](../../docs/product/acceptance-criteria.md#ac-083)、[AC-084](../../docs/product/acceptance-criteria.md#ac-084)、[AC-087](../../docs/product/acceptance-criteria.md#ac-087) | [task-actions.test.tsx](../../frontend/src/features/export-tasks/__tests__/task-actions.test.tsx)、[TaskPauseMySqlTest.java](../../backend/src/test/java/com/exportflow/worker/TaskPauseMySqlTest.java) | 已编写并补充边界；处理见缺口记录 | 已执行／2026-10-17 | 暂停404／页面占位红灯；暂停证明与竞争深层待补证 | component/真实MySQL；PENDING/RUNNING暂停、控制版本、重复ID、完成/认领竞态、回执绕过guard/监督器保留delivery与暂停ACK；[执行证据](../../docs/validation/004-task-management/verification.md)、[缺口](../../docs/validation/004-task-management/coverage-gaps.md) | 已实施并运行；最终汇总见[证据](../../docs/validation/004-task-management/verification.md) |
| TC-405／T-005 | [AC-018](../../docs/product/acceptance-criteria.md#ac-018)、[AC-049](../../docs/product/acceptance-criteria.md#ac-049)、[AC-067](../../docs/product/acceptance-criteria.md#ac-067)、[AC-068](../../docs/product/acceptance-criteria.md#ac-068)、[AC-069](../../docs/product/acceptance-criteria.md#ac-069)、[AC-083](../../docs/product/acceptance-criteria.md#ac-083) | [TaskPauseMySqlTest.java](../../backend/src/test/java/com/exportflow/worker/TaskPauseMySqlTest.java)、[TaskPauseFileTest.java](../../backend/src/test/java/com/exportflow/worker/TaskPauseFileTest.java) | 已编写并补充边界；处理见缺口记录 | 已执行／2026-10-17 | 暂停404；基线装配通过；引用／重启／停止确认待补证 | 真实MySQL/小范围文件；旧身份拒绝、停止关闭、READY返回后暂停竞态、启动对账入口/状态限定、引用保护、已暂停不重启生成，正常文件回归；[执行证据](../../docs/validation/004-task-management/verification.md)、[缺口](../../docs/validation/004-task-management/coverage-gaps.md) | 已实施并运行；最终汇总见[证据](../../docs/validation/004-task-management/verification.md) |
| TC-406／T-006 | [AC-043](../../docs/product/acceptance-criteria.md#ac-043)、[AC-050](../../docs/product/acceptance-criteria.md#ac-050)、[AC-081](../../docs/product/acceptance-criteria.md#ac-081)、[AC-085](../../docs/product/acceptance-criteria.md#ac-085) | [TaskProgressIntegrationTest.java](../../backend/src/test/java/com/exportflow/progress/TaskProgressIntegrationTest.java) | 已编写并补充边界；处理见缺口记录 | 已执行／2026-10-17 | 真实Redis夹具通过；列表405；通知接口或表缺失 | 真实MySQL/Rabbit/Redis；同事务通知回滚、CAS并发更新、Flyway空库/V4→V5/原命令与断点保留、稳定实例队列/同ID拒绝、实际发送后ACK/失败保留、有界发送溢出关闭、存量通知初始化、旧缓存与故障回源；[执行证据](../../docs/validation/004-task-management/verification.md)、[缺口](../../docs/validation/004-task-management/coverage-gaps.md) | 已实施并运行；最终汇总见[证据](../../docs/validation/004-task-management/verification.md) |
| TC-407／T-004 | [AC-086](../../docs/product/acceptance-criteria.md#ac-086)、[AC-087](../../docs/product/acceptance-criteria.md#ac-087) | [task-actions.test.tsx](../../frontend/src/features/export-tasks/__tests__/task-actions.test.tsx)、[TaskManagementContractTest.java](../../backend/src/test/java/com/exportflow/exporttasks/TaskManagementContractTest.java)、[TaskManagementUnitTest.java](../../backend/src/test/java/com/exportflow/exporttasks/TaskManagementUnitTest.java) | 已编写并补充边界；处理见缺口记录 | 已执行／2026-10-17 | HTTP405/404／页面占位；查询mapper不存在 | unit/contract/真实事务；非法输入、控制冲突、未知提交找回、同ID不同请求拒绝；[执行证据](../../docs/validation/004-task-management/verification.md)、[缺口](../../docs/validation/004-task-management/coverage-gaps.md) | 已实施并运行；最终汇总见[证据](../../docs/validation/004-task-management/verification.md) |
| TC-408／T-007 | [AC-013](../../docs/product/acceptance-criteria.md#ac-013)、[AC-014](../../docs/product/acceptance-criteria.md#ac-014)、[AC-015](../../docs/product/acceptance-criteria.md#ac-015)、[AC-084](../../docs/product/acceptance-criteria.md#ac-084) | [task-actions.test.tsx](../../frontend/src/features/export-tasks/__tests__/task-actions.test.tsx)、[TaskManagementContractTest.java](../../backend/src/test/java/com/exportflow/exporttasks/TaskManagementContractTest.java) | 已编写并补充边界；处理见缺口记录 | 已执行／2026-10-17 | 页面占位／列表405；下载与元数据深层未到达 | component/HTTP；仅成功可下载、元数据/原因、暂停不下载半成品、预留动作不出现；[执行证据](../../docs/validation/004-task-management/verification.md)、[缺口](../../docs/validation/004-task-management/coverage-gaps.md) | 已实施并运行；最终汇总见[证据](../../docs/validation/004-task-management/verification.md) |
| TC-411／T-006 | [AC-039](../../docs/product/acceptance-criteria.md#ac-039)、[AC-042](../../docs/product/acceptance-criteria.md#ac-042)、[AC-043](../../docs/product/acceptance-criteria.md#ac-043)、[AC-082](../../docs/product/acceptance-criteria.md#ac-082)、[AC-085](../../docs/product/acceptance-criteria.md#ac-085) | [TaskProgressIntegrationTest.java](../../backend/src/test/java/com/exportflow/progress/TaskProgressIntegrationTest.java) | 已编写并补充边界；处理见缺口记录 | 已执行／2026-10-17 | 真实SSE端点404；活动序列端口缺失 | 真实回环SSE；具名事件/ready桥接/实际终态/暂停通知、断线资源清理、受控idle竞争/终态通知未到时最终快照补偿，无间隔测试；[执行证据](../../docs/validation/004-task-management/verification.md)、[缺口](../../docs/validation/004-task-management/coverage-gaps.md) | 已实施并运行；最终汇总见[证据](../../docs/validation/004-task-management/verification.md) |
| TC-414／T-016 | [AC-088](../../docs/product/acceptance-criteria.md#ac-088) | [AssemblyRepairUnitTest.java](../../backend/src/test/java/com/exportflow/worker/AssemblyRepairUnitTest.java)、[AssemblyRepairMySqlTest.java](../../backend/src/test/java/com/exportflow/worker/AssemblyRepairMySqlTest.java)、[AssemblyRepairFileTest.java](../../backend/src/test/java/com/exportflow/worker/AssemblyRepairFileTest.java) | 已编写并补充边界；处理见缺口记录 | 已执行／2026-10-17 | 装配缺块／损坏红灯；摘要方法与修复接口缺失 | 装配入口／EOF／最终文件；删除、截断、checksum与登记不符、清单空洞；V6空库／V4及V5升级、旧块保留与选择查询、存量实际校验补齐；[执行证据](../../docs/validation/004-task-management/verification.md)、[缺口](../../docs/validation/004-task-management/coverage-gaps.md) | 已实施并运行；最终汇总见[证据](../../docs/validation/004-task-management/verification.md) |
| TC-415／T-016 | [AC-088](../../docs/product/acceptance-criteria.md#ac-088)、[AC-089](../../docs/product/acceptance-criteria.md#ac-089) | [AssemblyRepairUnitTest.java](../../backend/src/test/java/com/exportflow/worker/AssemblyRepairUnitTest.java)、[AssemblyRepairMySqlTest.java](../../backend/src/test/java/com/exportflow/worker/AssemblyRepairMySqlTest.java)、[AssemblyRepairFileTest.java](../../backend/src/test/java/com/exportflow/worker/AssemblyRepairFileTest.java) | 已编写并补充边界；处理见缺口记录 | 已执行／2026-10-17 | 现有全量新代／未自动修复／耗尽错误码红灯；接口缺口 | 坏块后健康块保持ID；缺块登记但有清单重建；双证据缺失失败；源摘要变化失败；仅候选损坏重装配；3轮耗尽及重启保留；UNKNOWN找回／选择CAS不重复计数／旧fence与暂停拒绝；最终Excel行集合内容顺序与清理引用；[执行证据](../../docs/validation/004-task-management/verification.md)、[缺口](../../docs/validation/004-task-management/coverage-gaps.md) | 已实施并运行；最终汇总见[证据](../../docs/validation/004-task-management/verification.md) |
| TC-416／T-016 | [AC-090](../../docs/product/acceptance-criteria.md#ac-090) | [AssemblyRepairMySqlTest.java](../../backend/src/test/java/com/exportflow/worker/AssemblyRepairMySqlTest.java)、[task-repair.test.tsx](../../frontend/src/features/export-tasks/__tests__/task-repair.test.tsx)、[TaskProgressIntegrationTest.java](../../backend/src/test/java/com/exportflow/progress/TaskProgressIntegrationTest.java) | 已编写并补充边界；处理见缺口记录 | 已执行／2026-10-17 | 修复事务接口缺失／页面占位；低计数后续断言未到达 | 对账先提交低计数再开始重建；段幂等累计；HTTP/SSE/旧缓存与旧响应；高version低percent；阶段不改变筛选不加查询；100%仅成功；[执行证据](../../docs/validation/004-task-management/verification.md)、[缺口](../../docs/validation/004-task-management/coverage-gaps.md) | 已实施并运行；最终汇总见[证据](../../docs/validation/004-task-management/verification.md) |
| TC-417／T-016 | [AC-091](../../docs/product/acceptance-criteria.md#ac-091) | [task-repair.test.tsx](../../frontend/src/features/export-tasks/__tests__/task-repair.test.tsx) | 已编写并补充边界；处理见缺口记录 | 已执行／2026-10-17 | 页面占位红灯；图标颜色／位置仍须真实浏览器验证 | 黄色InfoCircle在进度条后；hover/focus与aria名称；CHECKING/REBUILDING/ASSEMBLING/暂停/成功/失败；刷新与重连恢复，无时长断言；[执行证据](../../docs/validation/004-task-management/verification.md)、[缺口](../../docs/validation/004-task-management/coverage-gaps.md) | 已实施并运行；最终汇总见[证据](../../docs/validation/004-task-management/verification.md) |

TC-412视觉及TC-413质量验证已执行，结果归同一验证文档；具体命令见[quickstart](quickstart.md#计划验证命令)。测试名称/注释已关联上表标准，六类历史功能缺口已按[补证记录](../../docs/validation/004-task-management/coverage-gaps.md#实施补证状态)补齐，性能及计时部分仍免验；不能仅据关联判断覆盖完整。既有回归继续使用原编号。后续补写测试必须引用对应唯一标准，不重新分配旧回归编号。

## 验证排除与覆盖缺口

负责人于2026-10-14明确暂不考虑性能、超时延时不用测试直接跳过。以下是本功能执行排除依据；全项目规则草案仍独立待批，本次规格已批准。不新增对应测试代码，不把未验证部分当通过，不降低正常功能断言。

| 记录 | 标准／范围 | 原因与影响 | 替代验证／恢复条件 | 状态 | 责任人／记录日期 | 排除或缺口审批 |
| --- | --- | --- | --- | --- | --- | --- |
| EX-001 | 上表性能/资源标准，P95/生成耗时/预热/重复采样/压力/容量专项 | 暂不考虑性能；不证实性能或资源目标 | 不以普通功能耗时替代；负责人明确恢复后再设计环境与执行 | 本轮按负责人要求不安排；未验证 | 项目负责人／2026-10-14 | 本会话明确指令已确认本轮免验；不代表项目级规则生效或功能规格批准 |
| EX-002 | [AC-042](../../docs/product/acceptance-criteria.md#ac-042)、[AC-043](../../docs/product/acceptance-criteria.md#ac-043)、[AC-044](../../docs/product/acceptance-criteria.md#ac-044)、[AC-068](../../docs/product/acceptance-criteria.md#ac-068)、[AC-083](../../docs/product/acceptance-criteria.md#ac-083)的空闲宽限、重连退避、lease到期、暂停到期对账；心跳/TTL/请求或投递超时 | 超时/延时/阈值用例跳过，包括虚拟时钟与缩短阈值；保留运行设计 | 显式事件、事务屏障、控制身份与停止回执仍验证；到期分支不伪装补证；负责人明确恢复后执行 | 未验证；负责人已要求本轮跳过；规格已批准 | 项目负责人／2026-10-14 | 本会话明确指令已确认本轮免验；不代表项目级规则生效或功能规格批准 |
| GAP-001 | 新接口/控制类型尚不存在 | 规格批准后测试可能导入/编译阻塞，不能冒充目标行为红灯 | 在测试阶段记录实际阻塞与可运行的协议/持久边界测试；向负责人评审后再实施，不提前写生产空壳 | 历史13项接口缺口已解除并运行，见[证据](../../docs/validation/004-task-management/verification.md) | 执行本任务的开发者／2026-10-14 | 负责人已批准处理方案；实际补证见coverage-gaps记录，未视为永久免验 |
| GAP-002 | [SC-011](../../docs/product/acceptance-criteria.md#sc-011) | 测试先行时页面未实现；当前已实施并完成视觉验证 | 实施后1280×720/键盘截图补证 | 已完成1280×720及键盘截图 | 执行本任务的开发者／2026-10-14 | 负责人已批准处理方案；实际补证见coverage-gaps记录，未视为永久免验 |

其他新发现的非免验缺口须记录负责人、影响、替代方式和恢复条件，不能凭效率要求跳过并发或数据正确性。

## 阶段门禁

- [x] 本规格、附属产物及两个ADR于2026-10-17得到负责人明确批准；本轮免验依据已记录。
- [x] 真实隔离测试环境正常，不以基础设施故障冒充行为失败。
- [x] 本期非免验用例及六类功能补证已执行通过，标准映射和代码反向引用完整；范围限制见验证记录。
- [x] 可执行新增入口及文件行为有红灯，基线结果与接口／工具缺口已分开记录；不代表深层分支全部达到。
- [x] 已列明[实际证据](../../docs/validation/004-task-management/verification.md)及[6类缺口](../../docs/validation/004-task-management/coverage-gaps.md)，第二次评审已批准处理方案，实际补证继续记录。
- [x] 负责人已明确批准测试阶段及缺口处理方案，范围/日期/版本见上表。
- [x] 实施后适用回归和质量门禁通过，排除项保持未验证，实际证据与产物摘要已归档。

## 规格拟定阶段文档核对（历史）

只读核对治理、规则、模板、现有任务页/创建/下载/执行/断点/Outbox与已接受ADR，并按负责人收窄范围移除本期CRUD、失败后用户手动重试和继续；0.3按新增需求加入同任务自动装配修复。仅生成规格文档与候选验收/ADR，不运行测试、构建、中间件初始化或生产迁移。

版本0.1的机械核对仅证明当时链接与表格可解析，未识别本轮发现的行为设计缺口。版本0.2的实质审查见[review](review.md)，0.3增量同见review；规格阶段机械审计见[核对报告](../../artifacts/004-task-management/document-review-check.json)；不将文档检查记为业务测试、环境验证或阶段批准。

## 0.3新增覆盖与证据边界

T-016已创建并执行基础测试集；缺少接口与迁移及未触达的深层断言单列记录，不将文档修改或缺类记为行为红灯。源变化用既有只读订单重读结果与故意不一致的测试任务基线验证，不修改来源订单；文件破坏只针对测试隔离任务，不操作共享真实任务。块表缺记录与分段清单缺失分别覆盖，数据库不可读单列UNKNOWN，不用H2或内存选择表替代真实事务。

复用既有EX-001/002：自动修复只按确定轮次数和事务屏障验证，不测试性能、超时、延时、租约到期或轮次耗时，不用虚拟时钟补测。两个ADR与0.3规格现已批准；测试阶段与缺口处理方案已批准，生产实施已授权。

## 2026-10-17测试阶段启动

负责人批准当前规格0.3，含两个关联架构决策及本期排除范围；当前进度以本表及[执行证据](../../docs/validation/004-task-management/verification.md)为准；审批和验收结论分别维护。项目级效率规则草案独立未批准，不借本次审批修改治理。基础设施使用负责人现有三容器；只创建测试专属schema/vhost/缓存前缀，不创建容器或修改来源订单。

## 测试阶段提交范围（历史）

本次提交65项基础用例、红灯及基线结果、EX-001/002和[6类实际缺口](../../docs/validation/004-task-management/coverage-gaps.md)。提交当时T-002至007及T-016未全勾选：已有可运行基础集，但部分计划变体尚未写或深层未到达。用例完整性不判通过，缺口及实施范围须负责人明确评审；提交时未批准T-009前，不执行T-010至018的生产任务；后续授权见下节。获准后仍先补相应缺失断言，再实现该行为，不能把批准解释为缺口已验证或永久免验。

本次测试阶段机械核对见[核对报告](../../artifacts/004-task-management/test-stage-document-check.json)，其范围仅为链接、表格、32项验收映射与代码引用、OpenAPI引用和生产变更边界，不代替深层覆盖与业务验收。

## 2026-10-17生产实施授权

负责人在阅读测试阶段结果及审批请求后回复“继续”，明确批准本阶段和缺口处理方案，授权004规格0.3范围生产实施。保留上文红灯与缺口历史；非免验缺失断言仍先补后实现，真实边界及最终绿灯未完成前不宣称验收。项目级效率规则草案未因本次实施授权自动生效。

## 实施补证进度

2026-10-17已完成列表、SSE、暂停、安全停止及选择性修复实现。新增缓存故障回源／实际发送后ACK／队列溢出／重复实例、暂停提交后异常、资源未关闭拒绝证明、替换提交不明、跨重启预算、存量块封存及READY完整可信校验，均保留先行断言及真实结果。迁移与最终回归汇总、额外补证及质量门禁以[当前证据](../../docs/validation/004-task-management/verification.md)为准。项目级效率规则草案保持待批；免验不能记作通过。

## 实施收口（2026-10-17）

后续负责人报告页面404，已修复本机端口冲突及项目MQ进度权限，后端8081、前端5173代理列表与SSE均验证通过并保持运行；见[运行记录](../../docs/validation/004-task-management/runtime-startup-fix.md)。以下未切换运行服务的描述为初次交付状态。

前端116项及后端141项（14单元、127集成）去重通过，构建与本地质量门禁通过，T-014/015完成。六类功能缺口已补证，性能及计时排除仍未验证；JaCoCo历史数据版本提示及浏览器合成视觉的边界见[最终记录](../../docs/validation/004-task-management/verification.md#最终功能与质量结果)。现有8080应用未重启，用户运行库未迁移，未创建Git提交；本节记录交付事实，不代替负责人产品验收。
