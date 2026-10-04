# 任务：项目初始化与基础骨架

**输入**：[spec.md](spec.md)、[plan.md](plan.md)、[research.md](research.md)、[quickstart.md](quickstart.md)  
**状态**：规格草稿待项目负责人明确批准；没有生产文件、测试代码或实际验证结果。  
**组织方式**：本轮用户明确排除测试实施；审批规格后按“前端工程 → 静态导航 → 职责占位 → 手工与构建验证”执行。后续业务恢复常规测试门禁。

## 审批记录

| 门禁或指令 | 范围与版本 | 状态 | 确认人 | 日期 | 结论与依据 |
| --- | --- | --- | --- | --- | --- |
| 本轮测试范围指令 | 仅项目初始化、文字占位页、目录及职责文件 | 用户已明确排除测试实施 | 项目负责人 | 2026-10-04 | 本次请求明确“本规格不涉及到测试的部分”；不豁免后续业务 |
| 规格审批 | 三份核心规格及 research、quickstart；本次文档草稿 | 未批准 | 待项目负责人 | — | 生成功能规格的请求不等于批准 |
| 测试先行阶段审批 | 本轮无测试阶段 | 不适用 | — | — | 不能记录为测试通过；采用下面的替代验证 |
| 扩大范围 | 业务页面、Java 服务启动、接口、中间件、测试等 | 未授权 | 待项目负责人 | — | 新范围需另行规格与对应审批 |

批准记录须保存实际消息、评审记录或提交标识。本轮规格批准只允许搭建此静态骨架。

## 测试状态与范围例外

| 项目 | 编写进度 | 执行进度 | 结果与依据 |
| --- | --- | --- | --- |
| 前端自动化测试及测试环境 | 本轮不安排 | 本轮不安排 | 用户明确排除；不安装 Vitest、Testing Library、MSW，不生成测试文件 |
| 后端测试及服务启动 | 本轮不安排 | 本轮不安排 | 只有职责占位文件，无接口与可运行服务 |
| 测试阶段门禁 | 不适用 | 不适用 | 不是未执行却通过；不生成红灯或绿灯证据 |
| 静态交付验证 | 已设计步骤 | 未执行 | 所有实际结果待规格获批、骨架实施后产生 |

本表用于透明记录用户限定的范围，不引入测试实施任务。无需先完成不存在的测试阶段再请求审批。
替代验证不能证明订单查询、任务创建、事务、消息、生成、下载或进度正确，后续不得复用作业务测试证据。

## AC—实现任务映射

| 验收链接 | 实现任务 | 本期责任 |
| --- | --- | --- |
| [`AC-029`](../../docs/product/acceptance-criteria.md#ac-029) | T-003 | 404 与返回入口 |
| [`AC-041`](../../docs/product/acceptance-criteria.md#ac-041) | T-003 | 路由、选中菜单与标题 |
| [`AC-057`](../../docs/product/acceptance-criteria.md#ac-057) | T-003 | 菜单名称和两个文字页面 |
| [`AC-058`](../../docs/product/acceptance-criteria.md#ac-058) | T-002、T-003、T-004、T-005、T-006 | 逐文件结构、职责及阶段说明 |
| [`AC-059`](../../docs/product/acceptance-criteria.md#ac-059) | T-002、T-003、T-004 | 无后端依赖和业务请求 |

## AC—替代验证映射

因本轮明确排除测试代码，使用手工与结构核对编号 `VC-xxx`，仅在此规格内标识验证，不分配全项目自动化测试编号。
本轮不生成可执行测试文件，原模板的测试映射以该替代验证表替代；负责人为实施者，评审人为项目负责人。

| 验收链接 | 验证编号 | 验证任务 | 方法与覆盖缺口 | 负责人／审批状态 |
| --- | --- | --- | --- | --- |
| [`AC-029`](../../docs/product/acceptance-criteria.md#ac-029) | VC-002 | T-007 | 未知地址、返回入口手工检查；无自动化路由回归 | 实施者／规格待审批 |
| [`AC-041`](../../docs/product/acceptance-criteria.md#ac-041) | VC-001、VC-002 | T-007 | 点击、直接访问、刷新、前进后退与标题核对；无自动化覆盖 | 实施者／规格待审批 |
| [`AC-057`](../../docs/product/acceptance-criteria.md#ac-057) | VC-001 | T-007 | 查看两个页面和无业务控件；无组件测试 | 实施者／规格待审批 |
| [`AC-058`](../../docs/product/acceptance-criteria.md#ac-058) | VC-003 | T-007 | 逐文件核对及占位内容检查；未验证任何后台行为 | 实施者／规格待审批 |
| [`AC-059`](../../docs/product/acceptance-criteria.md#ac-059) | VC-004 | T-007 | 前端离线于后端运行，并检查 Network；静态模块资源请求不算业务请求 | 实施者／规格待审批 |

未来对应功能规格负责补齐自动化验证；不在本轮创建未启动功能的测试产物。

## SC—验证映射

| 成功标准链接 | 验证编号 | 任务 | 环境 | 证据计划 |
| --- | --- | --- | --- | --- |
| [`SC-014`](../../docs/product/acceptance-criteria.md#sc-014) | VC-001、VC-003、VC-004、VC-005 | T-007 | 本地 Node.js 22 LTS、pnpm 10、真实锁文件、实际浏览器版本、1280×720；开发与构建预览 | `docs/validation/001-project-bootstrap/` 中的环境记录、命令日志、页面截图与核对报告；目前尚未生成 |

不用构建成功替代业务性能、数据准确性或后台可靠性指标。本轮不映射与订单和导出实现有关的其他成功标准。

## 验证进度与证据

| 编号 | 目标与反向追踪 | 执行状态 | 命令或手工操作计划 | 证据计划 | 实际结果 |
| --- | --- | --- | --- | --- | --- |
| VC-001 | [`AC-041`](../../docs/product/acceptance-criteria.md#ac-041)、[`AC-057`](../../docs/product/acceptance-criteria.md#ac-057)、[`SC-014`](../../docs/product/acceptance-criteria.md#sc-014) | 未执行 | 菜单切换、键盘导航、路由直接访问、刷新、后退前进、查看标题与视口 | `navigation.md`、`orders.png`、`export-tasks.png` | 无 |
| VC-002 | [`AC-029`](../../docs/product/acceptance-criteria.md#ac-029)、[`AC-041`](../../docs/product/acceptance-criteria.md#ac-041) | 未执行 | `/` 重定向、未知地址、404 返回、恢复入口和未选中菜单核对 | `navigation.md`、`not-found.png` | 无 |
| VC-003 | [`AC-058`](../../docs/product/acceptance-criteria.md#ac-058)、[`SC-014`](../../docs/product/acceptance-criteria.md#sc-014) | 未执行 | 对照 plan 的目录与全部文件表；检查占位文件不含实现且未被导入 | `structure.md` | 无 |
| VC-004 | [`AC-059`](../../docs/product/acceptance-criteria.md#ac-059)、[`SC-014`](../../docs/product/acceptance-criteria.md#sc-014) | 未执行 | 无后端地址配置运行；查看浏览器 Network 中无 API、SSE、轮询；不操作现有容器 | `network.md` | 无 |
| VC-005 | [`SC-014`](../../docs/product/acceptance-criteria.md#sc-014) | 未执行 | 冻结安装、typecheck、build、开发与 preview；具体命令见 quickstart | `environment.md`、`commands.log` | 无 |

报告只使用静态应用信息，不记录密码或个人路径。浏览器可用性有缺口时据实报告，不能声称所有浏览器已验证。

## 阶段 1：规格评审

- [ ] T-001 [准备，非产品行为] 项目负责人评审本规格、计划、任务及附属文档，确认根级目录、后端占位方式和最小前端依赖范围；记录批准范围与版本。开始生产实施前必须完成。

**检查点**：本轮不编写测试的要求已明确；三份核心规格审批仍未发生。现在停留在此检查点，仅可继续修订文档。

## 阶段 2：用户故事 1——静态前端（P1）

- [ ] T-002 [结构与启动] 获批后按 plan 创建 `.gitignore`、前端 package、Vite/TypeScript 配置、HTML 与 main，安装最小前端依赖并生成锁文件；关联 [`AC-058`](../../docs/product/acceptance-criteria.md#ac-058)、[`AC-059`](../../docs/product/acceptance-criteria.md#ac-059)。不生成测试配置或后台配置。
- [ ] T-003 [导航与文字页面] 创建 plan 中全部实际前端模块；完成静态 Provider、导航、布局、两个占位页、轻量 404、路由错误恢复和基础样式。关联 [`AC-029`](../../docs/product/acceptance-criteria.md#ac-029)、[`AC-041`](../../docs/product/acceptance-criteria.md#ac-041)、[`AC-057`](../../docs/product/acceptance-criteria.md#ac-057)、[`AC-058`](../../docs/product/acceptance-criteria.md#ac-058)、[`AC-059`](../../docs/product/acceptance-criteria.md#ac-059)。

本阶段按照用户限定不执行测试先行，不创建测试代码；最终静态验证仍须执行。

## 阶段 3：用户故事 2——职责结构（P1）

- [ ] T-004 [前端职责预留] 创建 plan 中“前端业务及通用占位”职责表列出的全部文件，仅含中文职责和本轮边界；不声明或导入业务函数、DTO、Hook、表单、API client。关联 [`AC-058`](../../docs/product/acceptance-criteria.md#ac-058)、[`AC-059`](../../docs/product/acceptance-criteria.md#ac-059)。
- [ ] T-005 [后端职责预留] 创建 plan 的全部后端目录和职责占位文件，包括注释 POM、配置、Java 文件和迁移位置说明；不生成 main、Bean、Controller、SQL、Wrapper 执行脚本或任何可运行服务。关联 [`AC-058`](../../docs/product/acceptance-criteria.md#ac-058)。
- [ ] T-006 [运行与地图] 完成 `frontend/README.md`、`backend/README.md`，同步根 README 与 AGENTS 工程地图；明确前端可运行、后端只是结构预留，维护职责和实际目录一致。关联 [`AC-058`](../../docs/product/acceptance-criteria.md#ac-058)。

## 阶段 4：静态交付验证与收尾

- [ ] T-007 [全部本期验收与成功标准] 执行 VC-001 至 VC-005，归档环境、命令退出码、结构记录与截图；逐项更新进度和 AC/SC 映射结果。不得为了报告完成而伪造截图、安装日志或后端构建成功。
- [ ] T-008 [追踪收尾，非新增产品行为] 核对所有文件及静态函数与 plan 一致、无漏项；确认没有业务实现或测试代码、中间件未被修改；同步获批结论和待确认清单。所有验收完成或缺口明确经项目负责人评审后再标记交付。

## 依赖与执行顺序

1. T-001 获批前只写文档；本轮不提前创建工程。
2. T-001 → T-002 → T-003；T-004 与 T-005 同样受 T-001 约束，不提前生成空生产文件。
3. T-006 在实际结构创建后更新，避免文档宣称不存在的工程已初始化。
4. T-002 至 T-006 完成后执行 T-007，再执行 T-008。
5. 若引入任何业务交互或后端启动，立即拆为后续规格并恢复测试先行，而不是沿用此例外。
