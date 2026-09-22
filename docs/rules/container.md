# 容器与本地启动规则

目标本地运行使用 Docker Compose，包含前端、Spring Boot 后端、MySQL 8.4 LTS、RabbitMQ 和 Redis 8；导出文件与 MySQL 数据使用命名卷，RabbitMQ 按批准的持久化策略配置。RabbitMQ 命令队列见待评审的 [`ADR-0005`](../adr/0005-rabbitmq-task-queue.md)，Redis 进度缓存见待评审的 [`ADR-0006`](../adr/0006-backend-stack-and-progress-cache.md)。原 Stream 方案不得与缓存方案混合实施。本期不运行其他消息队列或远程对象存储。

- 镜像使用多阶段构建、固定基础镜像版本、非 root 用户和最小运行时依赖。
- 容器配置通过环境变量/挂载注入，密钥不得写入 Dockerfile 或镜像层。
- MySQL、RabbitMQ 和 Redis 必须先通过健康检查，后端才进入就绪状态；前端依赖后端健康。MySQL 健康检查使用专用健康检查账号执行 `mysqladmin ping` 或等价只读检查；RabbitMQ 检查 broker 就绪及队列拓扑；Redis 检查不得只验证端口打开，应至少执行 `PING`。
- 应用优雅处理 SIGTERM；后端停止领取 RabbitMQ 新任务消息，未提交终态的消息不得提前确认；日志输出 stdout/stderr。
- RabbitMQ 和 Redis 镜像版本与 digest 均在初始化时锁定，持久化与资源限制须满足各自恢复目标；Redis 仅做可重建进度缓存时不配置 Stream，缓存丢失从 MySQL 回源。具体缓存 TTL 和 broker 恢复策略待 ADR 评审。
- 本地启动命令、端口、依赖服务和数据初始化必须记录在项目 README（初始化后）及此规则引用中。
