# 任务、映射与证据

关联：[需求](spec.md)、[设计](plan.md)、[契约](contracts.md)、[ADR](../../docs/adr/0012-user-resume-and-live-progress.md)。

## 实施任务

- [x] T-01 先补真实边界与前端回归，执行并记录红灯/覆盖缺口。
- [x] T-02 实现继续控制事务、输入保护、幂等回执、原命令重开与新执行身份。
- [x] T-03 实现未封存恢复核验、选择性重建、可信进度重算和最终采用。
- [x] T-04 实现继续确认/找回/空闲通道唤醒、启用自动SSE调度。
- [x] T-05 受影响回归、质量门禁、文档追踪及证据收尾；范围外日期控件失败单独保留，未宣称全量前端门禁通过。

## 验收与测试映射

| 标准 | 实现 | 测试 | 文件 |
| --- | --- | --- | --- |
| [AC-092](../../docs/product/acceptance-criteria.md#ac-092) | T-02 | TC-501、TC-502 | TaskResumeTest |
| [AC-093](../../docs/product/acceptance-criteria.md#ac-093) | T-03 | TC-503、TC-504 | TaskResumeTest |
| [AC-094](../../docs/product/acceptance-criteria.md#ac-094) | T-04 | TC-505 | AutomaticTaskProgressTest |
| [AC-095](../../docs/product/acceptance-criteria.md#ac-095) | T-04 | TC-506 | task-resume.test.tsx |
| [AC-090](../../docs/product/acceptance-criteria.md#ac-090)、[AC-091](../../docs/product/acceptance-criteria.md#ac-091) | T-03、T-04 | TC-503、TC-505、TC-506；复用004修复测试 | 真实文件/HTTP/SSE、前端 |
| [SC-018](../../docs/product/acceptance-criteria.md#sc-018) | T-05 | TC-501至506及受影响回归 | 既有MySQL/RabbitMQ/Redis隔离、小批只读订单、临时文件；[实际证据](../../docs/validation/005-task-resume-and-live-progress/verification.md) |

## 审批记录

2026-10-17：负责人在本轮明确要求“规格列出后直接执行补测试，测试完成后直接开始实施开发，不需要我审批，直接记录审批通过”。规格0.1及上述范围：已获本次明确预授权；测试先行阶段后进入实施：同次明确预授权，不再等待。此记录说明授权依据，不代表负责人已经阅读尚未生成的测试证据，也不替代真实门禁检查。全局审批制度不修改。

## 测试进度与证据

| 范围 | 编写 | 执行 | 实际结果与证据 |
| --- | --- | --- | --- |
| TC-501/502 继续控制、错误、幂等与旧身份 | 已编写 | 已执行 | TaskResumeTest相关用例通过；红灯、回滚、并发、原命令及新旧身份见[证据](../../docs/validation/005-task-resume-and-live-progress/verification.md) |
| TC-503/504 部分/完整数据恢复、缺证据、源改变与文件内容 | 已编写/复用 | 已执行 | TaskResumeTest共11例最终通过；既有文件、修复及暂停回归通过，缺断点不新增来源读取；健康后段保留、最终行集合及顺序有断言 |
| TC-505 真实自动发布至100% | 已编写 | 已执行 | AutomaticTaskProgressTest通过；生产自动投递/消费/通知，真实SSE观察33%到成功100%，缓存、采用记录及下载核对，无手动dispatch |
| TC-506 前端继续与实时合并 | 已编写 | 已执行 | 新增3例通过；任务页受影响48例最终通过，额外UNKNOWN离页1例通过；类型检查与构建通过 |

门禁实绩：后端70个去重适用用例最终通过，最终定向verify成功，Spotless、Checkstyle、SpotBugs及JaCoCo报告完成；前端冻结安装、类型检查、构建及上述受影响用例完成。全量前端批次因包含免验的旧时间用例而停止；其中另发现未修改的订单日期控件用例失败，定向仍失败，未纳入本次修复，未宣称全量前端通过。性能、超时、延时、TTL和租约到期按用户要求免验，不能记为通过。浏览器端到端沿用项目非门禁范围；真实HTTP SSE与组件测试覆盖协议边界。未重跑用户110000行任务，不能把自动小范围闭环等同于复现该大任务的全部99%停滞原因。详细命令、批次失败、定向修正及限制见[验证记录](../../docs/validation/005-task-resume-and-live-progress/verification.md)。

### 测试先行结果（2026-10-17）

已编写TC-501至506。首轮真实后端9例均预期失败：继续入口404／未开放RESUME、生产缺Spring调度；前端3例因RESUME尚不支持失败。补充边界3例中2例继续404，回滚用例的SQL触发器因隔离账号无SUPER权限不能创建，属于工具故障，不冒充业务红灯；已改为真实事务内进度端口写入后抛错，不修改共享MySQL权限。初次全局pnpm指向Node24/pnpm11，属于工具错误；已改用本机Node22.14.0及缓存固定pnpm10.34.6并取得上述真实前端红灯。

证据：`artifacts/005-task-resume-and-live-progress/backend-red.log`、`backend-extra-red.log`、`frontend-red.log`。生产真实隔离MySQL/MQ与只读来源已正常；新增功能失败明确；输入/并发/未知/文件/前端用例已编写。延续004已有文件源变化、耗尽、READY损坏及缓存降级用例，本次纳入受影响回归。调整后的回滚用例已在实施验证通过，负责人本次预授权涵盖所列范围；测试阶段进入实施授权已批准（依据上节明确指示），不声称用户已阅读本节。T-02至T-04已实施，最终结果见上表及验证记录。

### 完成范围（2026-10-17）

新增标准均映射到已执行用例，未映射项为零。两次授权均来自本轮负责人明确指示；代码和证据完成不代表负责人已经完成产品验收。未改全局审批规则，未修改独立待审批的测试效率提案，未提交Git。验证使用独立构建目录，用户8081/5173服务及现有容器保持运行；新版本需按[操作说明](quickstart.md)在原终端重启后生效。
