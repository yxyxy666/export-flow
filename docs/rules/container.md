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

复用现有服务不要求重新安装中间件。连接凭据从现有本地配置取得，不在项目文档中记录密码。当前只记录环境复用决定，不执行数据库迁移、清理或修改示例项目资源；当前项目的数据库、RabbitMQ vhost/队列与 Redis 键空间隔离方式须在使用前明确，见[待确认决策](../decisions-pending.md#运行与交付)。

## 后续容器专项

当前项目自身的容器配置、应用启动方式、拓扑、健康检查、资源配置和持久化方案后续单独制定。本轮不新增或迁移 Compose 配置，也不更改现有容器。

现有 Redis 7.4 是本阶段复用环境的实际版本；文档中的 Redis 8 候选环境与当前容器不同，兼容性验证及后续版本统一见[待确认决策](../decisions-pending.md#后端实现选择)。
