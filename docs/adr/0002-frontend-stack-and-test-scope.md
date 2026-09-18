# ADR-0002 前端技术栈与测试范围

- 状态：已接受
- 日期：2026-09-18

## 背景

订单管理和导出任务页面需要统一的后台 UI、类型检查、组件交互测试和可控的接口模拟。项目定位为本地学习项目，项目负责人明确不建设端到端测试。

## 决策

- 前端使用 React + TypeScript + Vite。
- Node.js 最低使用 22 LTS，包管理器使用 pnpm 10；依赖安装使用锁文件冻结模式。
- 通用 UI 使用 Ant Design；业务 UI 通过组合 Ant Design 组件实现，仅在没有合适基础组件时补充与其规范一致的自定义组件。
- 路由使用 React Router，服务端状态使用 TanStack Query，表单使用 React Hook Form + Zod。
- 前端测试使用 Vitest + Testing Library + MSW，覆盖单元、组件、前端集成和接口契约边界。
- 本期不引入 Playwright、Cypress 或其他端到端测试框架，也不设置端到端测试 CI 门禁。
- React、TypeScript、Vite、Ant Design 和测试依赖的具体稳定版本在项目初始化时锁定并提交锁文件。
- 页面 URL 为 `/orders` 和 `/export-tasks`，`/` 重定向至 `/orders`；已提交筛选和分页保存在 URL query，选择状态仅保存在内存。
- 界面仅提供简体中文，按最小视口 1280×720 验证；支持最新两个稳定版本的 Chrome、Edge、Firefox 及当前稳定 Safari。

## 后果

Ant Design 与 React 的依赖关系被明确，不再保留“组件库已确认但框架未确认”的矛盾。关键验收行为需要在单元、组件、前端集成或契约测试中覆盖；真实浏览器下载等无法在这些层级自动化的行为必须按 [`docs/rules/testing.md`](../rules/testing.md) 记录覆盖缺口和替代验证方式。

本决策对应 [`docs/rules/frontend.md`](../rules/frontend.md) 和 [`docs/rules/testing.md`](../rules/testing.md)。
