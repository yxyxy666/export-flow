# 构建、测试与打包规则

CI 使用 GitHub Actions。前端使用 `pnpm` 的 frozen lockfile 模式，后端使用 Maven Wrapper；构建产物和测试报告使用 GitHub Actions Artifacts 保存。本期仅提供本地 Docker Compose 运行方式，不建设远程部署流水线或外部制品仓库。

- 依赖必须锁版本并提交锁文件；构建不得依赖开发者本机隐式状态。
- CI 顺序：格式化检查 → 静态分析 → 单元测试 → MySQL/RabbitMQ/Redis 集成测试 → 契约测试 → 构建/打包 → 产物检查。
- 后端集成测试使用隔离的 MySQL 8.4 LTS、RabbitMQ 和 Redis 8，验证迁移、outbox、RabbitMQ 发布/消费与恢复；进度部分在 [`ADR-0006`](../adr/0006-backend-stack-and-progress-cache.md) 获批后验证 Redis 缓存回源及 SSE 快照回补，不再同时要求旧 Stream 游标语义。不得依赖开发者本机服务。
- 产物必须可追溯到提交、版本和构建参数；禁止把密钥打入产物。
- 发布脚本默认可重复执行；破坏性发布需要显式确认和回滚说明。
