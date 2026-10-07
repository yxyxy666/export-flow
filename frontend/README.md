# 前端静态骨架

本期可独立运行，只提供左侧“订单管理”“任务管理”菜单、两个同名文字页、404 与安全错误恢复入口。
设计与授权范围见[规格](../specs/001-project-bootstrap/spec.md)、[逐文件计划](../specs/001-project-bootstrap/plan.md)和[任务记录](../specs/001-project-bootstrap/tasks.md)。

## 运行

使用 Node.js 22 LTS（至少 22.12）和 pnpm 10.34.6。在仓库根执行：

```powershell
pnpm --dir frontend install --frozen-lockfile
pnpm --dir frontend dev
pnpm --dir frontend typecheck
pnpm --dir frontend build
pnpm --dir frontend preview
```

开发地址为 `http://127.0.0.1:5173`，预览为 `http://127.0.0.1:4173`，端口冲突直接失败。
若本机 pnpm 不是 10，可把上面每条 `pnpm` 替换为 `npm exec --yes --package=pnpm@10.34.6 -- pnpm`；该方式不更改全局版本。
只使用开发或预览服务器的 SPA 回退，不包含生产部署配置。

## 模块职责

- `src/app/`：中文 Provider、唯一路由实例与应用组合。
- `src/layouts/`：侧栏与内容容器，从路径派生选中项和浏览器标题。
- `src/features/`：两个实际文字页面；API、类型、Hook、组件及用例文件暂时只含职责注释。
- `src/shared/navigation/`：导航类型、元数据及路径匹配的唯一来源。
- `src/shared/components/`、`src/shared/styles/`：共同标题及基础布局样式。
- `src/shared/api/`、`types/`、`formatters/`：后续功能的注释占位，不被运行链导入。

当前没有业务请求、后端地址、表单、查询、导出、下载、SSE、轮询或测试框架。
后续功能按独立规格恢复常规测试先行，不沿用本轮例外。

## 锁定版本与验证

| 依赖 | 版本 |
| --- | --- |
| React / React DOM | 19.3.0 |
| Ant Design | 6.6.5 |
| React Router DOM | 7.18.4 |
| Vite / React 插件 | 8.3.3 / 6.1.2 |
| TypeScript | 7.0.2 |
| Node 类型 / React 类型 / React DOM 类型 | 22.20.5 / 19.3.0 / 19.3.0 |

版本来自实施时 npm 稳定标签，真实安装生成 [pnpm-lock.yaml](pnpm-lock.yaml)。依赖许可为 MIT；TypeScript 为 Apache-2.0。
Vite 和其 React 插件要求 Node 22.12 或更新的兼容版本，本轮实际 Node 为 22.14.0。
冻结安装、类型检查及构建退出码与浏览器覆盖缺口见[验证环境](../docs/validation/001-project-bootstrap/environment.md)。
构建提示入口包大于 500 KB，当前约 608 KB、gzip 199 KB；后续按实际页面能力评审拆分，不提高警告阈值掩盖提示。
