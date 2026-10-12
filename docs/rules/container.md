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

复用现有服务不要求重新安装中间件。连接凭据从现有本地配置取得，不在项目文档中记录密码。2026-10-11负责人再次明确“直接用我给你准备的project-export-flow”；开发与测试均复用以上三个服务。测试只创建、迁移和回收每用例专属schema/vhost/测试账号，禁止新建测试容器、停止共享服务、修改业务schema或已有用户/全局策略；隔离已定为schema export_flow、vhost /export-flow与Redis前缀export-flow:；队列、端口、文件根和性能隔离服务见[ADR-0009](../adr/0009-engineering-runtime-and-progress-notification.md)与[决策记录](../../specs/002-order-management/decision-record.md)。当前实际状态及环境差异见[共享服务验证](../validation/002-order-management/shared-services.md)。

## 基础设施与业务数据边界

本项目不重复维护现有MySQL、RabbitMQ、Redis的Compose、服务初始化、共享账号或全局配置。应用连接凭据由外部注入；应用的业务表结构、协议和持久化映射仍由产品规格及已接受ADR管理。Flyway迁移只操作获准的应用schema，不承担创建数据库服务、schema或用户的职责。订单直接复用负责人已提供的数据，不维护造数工具、复制订单或改写原始来源；来源适配见[规格](../../specs/002-order-management/provided-data.md)。测试夹具的任务数据及schema/vhost只用于隔离实际数据库和消息断言，并在结束后回收自己的资源。

## 后续容器专项

当前应用在宿主机本地运行，127.0.0.1:8080提供API，Vite127.0.0.1:5173提供开发页面及/api代理；应用Compose与远程部署明确延期。本轮不新增或迁移Compose，也不更改现有容器；运行时核对依赖连接、MQ有效策略、存储原子发布能力与/actuator/health，暴露health/info而不暴露env等配置端点。文件根由外部配置，2026-10-13负责人指定本机使用项目根目录export-data，由.env.local提供绝对路径并通过Git忽略；未提供外部配置时应用默认使用user.dir/export-data，标准启动入口固定工作目录为项目根。目录跨进程重启保留，日志位置独立配置，依据见[运行决策](../adr/0009-engineering-runtime-and-progress-notification.md)。

继续使用Redis7.4，不升级共享服务到8；测试同版本线。镜像在实际准备时记录patch和digest，验收批次固定digest。历史healthy不替代当前连接验证；2026-10-11已重新确认三个服务均健康，后续执行仍复核实际状态。
