# ADR-0003 应用技术栈与本地运行架构

- 状态：已接受；原任务领取方式由 [`ADR-0004`](0004-redis-streams-and-backend-processing.md) 取代，RabbitMQ 替代方案见待评审的 [`ADR-0005`](0005-rabbitmq-task-queue.md)
- 日期：2026-09-18

## 背景

项目需要实现订单查询、数据库持久化的异步导出任务、最多 200,000 行的 Excel 流式生成和本地文件下载。项目定位为学习项目，应保留完整异步流程，同时避免引入非必要的缓存、消息中间件、对象存储和远程部署设施。

## 决策

### 接口与前端协作

- API 使用 JSON REST，统一前缀 `/api/v1`，使用 OpenAPI 维护机器可读契约。
- 文件下载使用同源直接链接 `GET /api/v1/export-tasks/{taskId}/file`，由服务端返回文件流和 `Content-Disposition`，不使用 Blob 或签名 URL。
- 前端开发服务器使用 Vite `/api` 代理访问后端，保证页面始终使用同源相对下载地址。

### 后端与数据

- 后端使用 Java 21 和 Spring Boot 3，使用 Maven Wrapper 构建。
- 数据库使用 MySQL 8.4 LTS，本地和容器环境不使用 H2 等替代数据库。
- 异步任务使用 MySQL 任务表持久化。原 Redis Streams 决策见 [`ADR-0004`](0004-redis-streams-and-backend-processing.md)；RabbitMQ 命令与 Redis 进度缓存的目标方案分别见待评审的 [`ADR-0005`](0005-rabbitmq-task-queue.md)、[`ADR-0006`](0006-backend-stack-and-progress-cache.md)。
- Excel 使用 Apache POI `SXSSF` 流式生成，文件写入本地挂载目录；文件元数据和任务状态保存在 MySQL。
- 合成订单通过可重复执行的初始化脚本生成，初始化后只读。

### 本地运行与交付

- 本地运行使用 Docker Compose，包含前端、后端、MySQL 和 Redis；待 RabbitMQ 方案获批后增加 RabbitMQ。默认对外端口为前端 `5173`、后端 `8080`、MySQL `3306`；端口冲突时通过环境变量覆盖。
- 导出文件使用 Docker 命名卷持久化。
- CI 使用 GitHub Actions，执行格式化检查、静态分析、单元测试、集成测试、构建和产物检查。
- 前端使用 pnpm frozen lockfile，后端使用 Maven Wrapper；测试报告和构建产物保存为 GitHub Actions Artifacts。
- 本期不建设远程部署流水线或外部制品仓库。

## 后果

MySQL 任务表继续提供持久状态；原进度流见 [`ADR-0004`](0004-redis-streams-and-backend-processing.md)，RabbitMQ 和缓存目标方案仍待 [`ADR-0005`](0005-rabbitmq-task-queue.md)、[`ADR-0006`](0006-backend-stack-and-progress-cache.md) 评审。

本地文件存储简化下载流程，但文件依赖挂载卷，不具备多节点共享能力。直接链接下载避免大文件进入前端 JavaScript 内存；由于前端不读取下载响应体，下载失败只依赖浏览器 HTTP 行为和服务端日志定位。

本决策对应 [`docs/rules/frontend.md`](../rules/frontend.md)、[`docs/rules/backend.md`](../rules/backend.md)、[`docs/rules/build.md`](../rules/build.md) 和 [`docs/rules/container.md`](../rules/container.md)。
