# 目录与职责核对

**日期**：2026-10-07  
**验证编号**：VC-003  
**追踪**：[`AC-058`](../../product/acceptance-criteria.md#ac-058)、[`SC-014`](../../product/acceptance-criteria.md#sc-014)；[逐文件计划](../../../specs/001-project-bootstrap/plan.md)、[任务](../../../specs/001-project-bootstrap/tasks.md)

本轮使用临时只读核对脚本解析 plan 的文件职责表并检查文件、占位内容和模块导入；不增加仓库测试框架或业务用例。

| 核对项 | 实际结果 |
| --- | --- |
| 文件清单 | 80 个计划职责表文件存在，缺少 0；另有本目录的验证证据 |
| 前端业务与通用占位 | 17 个文件全部仅中文注释 |
| Java 职责占位 | 33 个文件全部仅中文注释，无类、方法或注解 |
| 占位被运行模块导入 | 0 |
| layouts/features 反向依赖 app | 0 |
| shared 导航依赖 app/layouts/features | 0 |
| 后端配置 | POM 仅 XML 注释、YAML 仅职责注释；无 Maven Wrapper 执行脚本，无 SQL |
| 前端脚本与依赖 | 无 test 脚本，无 Vitest、Testing Library、MSW、Playwright 或 Cypress 工程依赖 |
| 实际静态函数 | 按 plan 实现 13 项函数/局部函数；入口及自有签名有类型和中文职责说明 |
| 文档 | 前后端 README、根 README、AGENTS 工程地图、规格与任务相互引用 |

未操作现有容器。没有接口、业务 DTO、数据库迁移、假服务或导出数据。
类型检查及生产构建检查静态实现的有效性；不执行后端构建，不将注释 POM 当作有效工程。
