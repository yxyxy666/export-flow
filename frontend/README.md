# 前端

订单管理已接入真实查询、筛选、分页、选择和导出创建。任务管理保持文字占位，导航和404沿用[基础骨架](../specs/001-project-bootstrap/spec.md)。后端未启动时，订单页展示可恢复的请求错误。

2026-10-11负责人已授权[订单规格0.4](../specs/002-order-management/spec.md)开发。业务API统一在shared/api/api.ts，功能目录无API实现或转发；153项测试、类型检查和生产构建通过，review修复已回归，目标浏览器视觉手工已由负责人确认本轮无需验证，见[实施进度](../docs/validation/002-order-management/implementation-progress.md)和[任务清单](../specs/002-order-management/tasks.md)。

## 运行

推荐在仓库根执行`make frontend`，同时启动前后端使用`make dev`；配置与工具安装见[脚本说明](../scripts/README.md)。入口自动使用固定版本pnpm，无需改全局安装。

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
- `src/features/`：订单页面、输入和展示组件、查询/选择/导出Hook、用例、协议类型与schema；任务页仅文字占位。未使用的任务表格和查询Hook已删除。
- `src/shared/navigation/`：导航类型、元数据及路径匹配的唯一来源。
- `src/shared/components/`、`src/shared/styles/`：共同标题及基础布局样式。
- `src/shared/api/api.ts`：唯一业务接口文件，queryOrders查询订单，createExportTask提交任务；页面和Hook后续统一引用。
- `src/shared/api/http-client.ts`、`api-error.ts`：十秒超时、取消、资源释放及安全错误归一化；创建结果不明时不自动换键重试，不追加任务详情请求。
- `src/shared/types/`：分页协议；`formatters/`：已实现定点金额、UTC解析及香港时区展示。

订单页面通过统一API发起查询和创建，使用真实表单与选择；创建成功留在订单页。任务页仍为文字占位；任务状态、下载界面、SSE和轮询不在本期范围。Vitest/MSW验证协议与交互，完整类型检查和生产构建已通过；实际浏览器验证见[检查表](../docs/validation/002-order-management/browser-checklist.md)。基础骨架原来的测试例外不适用于订单规格。

## 全量选择与日期筛选

订单页支持全选当前已提交条件的全部分页、逐行取消及全部反选；全选不请求其他分页或完整ID。导出已选使用筛选与排除范围，原“导出筛选结果”仍导出完整条件。不同筛选/重置/历史导航清空选择。下单时间使用两个Ant Design日期时间选择器，保留单侧/秒级/清空，按香港墙上时间提交UTC。统一API位置保持不变。范围、兼容和实际验证见[003规格](../specs/003-select-all-filtered/spec.md)及[验证记录](../docs/validation/003-select-all-filtered/verification.md)。

## 锁定版本与验证

| 依赖 | 版本 |
| --- | --- |
| React / React DOM | 19.3.0 |
| Ant Design / Dayjs | 6.6.5 / 1.11.23 |
| React Router DOM | 7.18.4 |
| Vite / React 插件 | 8.3.3 / 6.1.2 |
| TypeScript | 7.0.2 |
| Node 类型 / React 类型 / React DOM 类型 | 22.20.5 / 19.3.0 / 19.3.0 |

版本来自实施时 npm 稳定标签，真实安装生成 [pnpm-lock.yaml](pnpm-lock.yaml)。依赖许可为 MIT；TypeScript 为 Apache-2.0。
Vite 和其 React 插件要求 Node 22.12 或更新的兼容版本，本轮实际 Node 为 22.14.0。
冻结安装、类型检查及构建退出码与浏览器覆盖缺口见[验证环境](../docs/validation/001-project-bootstrap/environment.md)。
构建提示入口包大于 500 KB，当前约 608 KB、gzip 199 KB；后续按实际页面能力评审拆分，不提高警告阈值掩盖提示。
