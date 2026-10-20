# 容器与本地启动规则

## 全新环境入口

2026-10-20负责人明确要求：别人拿到当前项目、没有中间件时，通过`make infra`生成基础设施，再执行`make dev`启动应用；本次免规格。决策见[ADR-0010](../adr/0010-local-infrastructure-bootstrap.md)，步骤见[脚本说明](../../scripts/README.md)。该流程替代仅复用示例容器的旧约束。

`make infra`使用[本项目Compose](../../docker-compose.yml)创建MySQL8.4、Redis7.4、RabbitMQ3.13，自动生成本地随机凭据与应用连接配置，初始化空数据卷资源并等待健康。`make dev`只启动宿主机应用，不执行基础设施创建。基础设施入口仅管理固定Compose项目export-flow，不按端口发现或复用其他项目的容器；端口冲突必须失败，不接管外部服务。

重复执行保留本项目容器、数据卷及本地配置，启动停止的本项目服务。Docker不可用、Compose失败、服务不健康或等待超时必须非零退出；不通过停止共享服务、强制重建、清库或删卷恢复。

| 服务 | 镜像标签 | 默认宿主机入口 |
| --- | --- | --- |
| MySQL | mysql:8.4 | 127.0.0.1:3306 |
| RabbitMQ | rabbitmq:3.13-management-alpine | 127.0.0.1:5672，管理界面127.0.0.1:15672 |
| Redis | redis:7.4-alpine | 127.0.0.1:6379 |

新服务只发布回环接口，分别使用命名卷。RabbitMQ固定hostname保持持久化节点身份。端口可在基础设施配置中调整，自动生成的应用连接必须一致。镜像标签不替代实际版本、digest、认证或功能验证。

## 初始化和数据边界

MySQL空卷首次启动执行[infrastructure/mysql/01-bootstrap.sh](../../infrastructure/mysql/01-bootstrap.sh)，创建export_flow应用库、exportflow来源库、兼容示例契约的空orders表、独立运行／迁移账号和权限。RabbitMQ创建/export-flow vhost和对应账号。初始订单列表为空，不要求用户手工创建数据库、账号、订单来源表或填写密码后才能启动。

任务、Outbox、断点等业务结构及只读订单视图仍由Flyway管理。运行账号只读来源订单；迁移账号对来源仅SELECT/REFERENCES。初始化脚本只运行于新卷，不改变外部或历史环境的订单、账号、schema、Flyway历史或全局MQ策略。不恢复订单造数或性能数据工具。

.env.infrastructure.local由模板自动生成并保留，密码和本地文件不提交Git；.env.local自动填入匹配的应用连接值。已有应用配置只同步基础设施连接字段，修改前完整备份，保留应用端口、文件根及其他设置；一致时不重写。备份也不得提交。密码只在空卷首次初始化生效，编辑文件不等于修改已有账号密码。不得记录实际密码、完整渲染Compose或inspect原文。

## 运行、测试与发布

应用仍运行在宿主机，默认127.0.0.1:8080，Vite默认127.0.0.1:5173；EXPORT_FLOW_BACKEND_PORT共同配置API与代理。仅暴露health/info，不暴露env等端点。远程发布及应用容器化仍延期。

初始化专项验证可以创建本项目的空卷和服务来证明从零可用，不能接管或停止外部基础设施。普通业务测试继续使用已准备服务及每用例独立schema/vhost/账号；测试夹具不自行创建中间件容器，不修改业务来源。历史共享服务记录保留为历史，当前专项状态见[迁移验证](../validation/local-infrastructure-bootstrap.md)。

导出文件、断点和临时工作区默认位于项目根export-data，可配置绝对路径，跨进程重启保留且被Git忽略；日志独立配置。启停或回退不删除任务、成功Excel、断点或未确认命令。
