# 容器与本地启动规则

## 当前开发环境（已确认）

项目负责人于 2026-10-04 确认：当前项目开发阶段复用示例项目
`D:\project\ai-vibe-coding-example\project-export-flow` 已启动的 MySQL、RabbitMQ 和 Redis 容器；容器相关内容后续单独处理。

2026-10-04 通过示例项目的 `docker-compose.yml` 与 `docker ps` 核对，三个容器均处于运行且健康状态。本机开发连接入口如下：

| 服务 | 现有容器 | 镜像标签 | 本机连接入口 |
| --- | --- | --- | --- |
| MySQL | `project-export-flow-mysql-1` | `mysql:8.4` | `localhost:3306` |
| RabbitMQ | `project-export-flow-rabbitmq-1` | `rabbitmq:3.13-management-alpine` | `localhost:5672` |
| Redis | `project-export-flow-redis-1` | `redis:7.4-alpine` | `localhost:6379` |

RabbitMQ 管理界面为 `http://localhost:15672`。以上为本机进程连接入口，不能直接作为将来应用容器内的连接地址；运行状态与镜像标签也不替代实际连接、认证和功能验证。

复用现有服务不要求重新安装中间件。连接凭据从现有本地配置取得，不在项目文档中记录密码。当前只记录环境复用决定，不执行数据库迁移、清理或修改示例项目资源；隔离已定为schema export_flow、vhost /export-flow与Redis前缀export-flow:；队列、端口、文件根和性能隔离服务见[ADR-0009](../adr/0009-engineering-runtime-and-progress-notification.md)与[决策记录](../../specs/002-order-management/decision-record.md)。资源尚未创建，连接凭据与实际能力在获批准备后核对。

## 后续容器专项

当前应用在宿主机本地运行，127.0.0.1:8080提供API，Vite127.0.0.1:5173提供开发页面及/api代理；应用Compose与远程部署明确延期。本轮不新增或迁移Compose，也不更改现有容器；运行时核对依赖连接、MQ有效策略、存储原子发布能力与/actuator/health，暴露health/info而不暴露env等配置端点。文件根由外部配置，默认LOCALAPPDATA/ExportFlow/data，目录跨进程重启保留。

继续使用Redis7.4，不升级共享服务到8；测试同版本线。镜像在实际准备时记录patch和digest，验收批次固定digest。历史healthy不替代当前连接验证；2026-10-07本轮docker ps退出码0但desktop-linux context没有运行容器记录，需在执行前复核正确context和既有服务状态。
