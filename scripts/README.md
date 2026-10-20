# 本地启动脚本

[Makefile](../Makefile)统一封装[run-project.ps1](run-project.ps1)。支持Windows PowerShell 5.1+、GNU Make；其他系统需另行适配。基础设施由[start-infrastructure.ps1](start-infrastructure.ps1)创建，应用在宿主机运行。

## 全新环境：两步启动

安装并启动Docker Desktop（Linux引擎及Compose v2+），安装GNU Make、Java21、Node22 LTS（至少22.12，含npm）。在项目根目录执行：

```powershell
make infra
make dev
```

PowerShell找不到make时，可使用仓库内的`./make infra`、`./make dev`或`.\make.cmd infra`、`.\make.cmd dev`。包装器优先查找本机用户目录下GNU Make4.4.1，否则使用PATH中的make.exe；没有安装时会明确报错。GNU Make可通过`winget install --id ezwinports.make --exact --source winget --scope user`安装。

`make infra`不依赖Java、Node或预先准备的数据库、账号、订单表、配置文件，也不要求先复制或编辑.env文件。它会：

1. 自动生成四个独立随机密码，写入被Git忽略的`.env.infrastructure.local`。
2. 自动生成应用`.env.local`，填入与新基础设施一致的连接信息。
3. 用本项目[Compose](../docker-compose.yml)创建MySQL8.4、Redis7.4、RabbitMQ3.13及持久数据卷。
4. MySQL空卷首次启动时执行[初始化脚本](../infrastructure/mysql/01-bootstrap.sh)，创建应用库`export_flow`、来源库`exportflow`及兼容示例契约的空`orders`表；创建独立运行、迁移账号及权限。
5. RabbitMQ创建`/export-flow` vhost和`export_flow_mq`账号，等待三个服务健康。

初始化完成后，`make dev`检查配置并并行启动前后端；后端Flyway创建任务、Outbox、断点等业务表及订单只读视图。新环境订单列表为空，不生成演示或性能数据，也不访问示例项目的数据库。随后可按实际需要准备业务订单。

## 命令

| 命令 | 行为 |
| --- | --- |
| make / make help | 显示帮助 |
| make infra | 创建、初始化或重新启动当前项目基础设施，等待健康 |
| make infra-status | 只读查看当前项目Compose服务 |
| make check | 检查应用工具与必需连接配置 |
| make dev | 仅并行启动前后端，不创建或初始化基础设施 |
| make backend | 打包后启动后端，启用消费者，最大堆512MiB |
| make frontend | 按锁文件安装依赖后启动Vite |
| make install | 只安装前端依赖 |
| make build | 构建前端dist和后端可执行jar |

前端默认[http://127.0.0.1:5173/orders](http://127.0.0.1:5173/orders)，后端默认[http://127.0.0.1:8080](http://127.0.0.1:8080)，健康检查为`/actuator/health`。修改`.env.local`中的`EXPORT_FLOW_BACKEND_PORT`后重新启动，后端与Vite代理共同使用该值。前端固定使用pnpm10.34.6，通过npm exec调用，不依赖全局pnpm版本；首次执行需要网络下载依赖及Maven Wrapper。

按Ctrl+C停止应用，基础设施继续运行。重复`make infra`保留本项目容器、卷、数据库及基础设施密码，应用连接字段按当前基础设施同步，不删除或重写订单。停止过的本项目服务会由Compose重新启动。

## 项目资源及配置

固定Compose项目名为`export-flow`；只管理该项目的三个服务，不按端口发现或复用其他项目的容器。其他服务占用所需端口时Compose返回失败，需要使用者处理端口冲突。初始化失败也保留容器和卷以供诊断。

新服务只向127.0.0.1发布默认端口3306、6379、5672和15672。MySQL、Redis、RabbitMQ均使用命名卷；RabbitMQ固定hostname保持持久卷节点身份。新MySQL的运行账号只有应用库DML及来源订单SELECT权限；迁移账号负责应用库DDL并只对来源持有SELECT/REFERENCES。已有外部服务、账号、订单和全局MQ策略不会被初始化脚本修改。

[.env.infrastructure.example](../.env.infrastructure.example)是自动生成模板。四个密码由命令生成64位小写十六进制值，不打印或提交；它们仅在新数据卷首次初始化时生效。端口可在首次运行前复制模板并调整`EXPORT_FLOW_*_PORT`，密码占位符应替换为同格式随机值；普通首次启动不需要此操作。已有卷不能通过编辑密码文件更改账号密码。

[.env.example](../.env.example)是应用配置模板。`.env.infrastructure.local`保留，不重置已有密码。`make infra`会同步应用`.env.local`中的数据库、迁移账号、MQ和Redis连接字段；有变化时先完整备份为`.env.local.backup-<时间>-<标识>`，保留应用端口、文件根、日志、实例配置和注释。连接未变化时不重写、不新增备份。备份同样被Git忽略，包含旧凭据，不要提交。应用.env.local每行KEY=value，允许整值引号，内容不执行；文件配置优先于进程环境。直接java命令不会自动读取.env.local。

导出文件及断点默认保存到项目根`export-data/`，已被Git忽略。可用`EXPORT_FLOW_FILE_ROOT`指定绝对路径；迁移目录需保留整个任务目录结构。日志另由`EXPORT_FLOW_LOG_FILE`配置。不得通过删卷、清库或删除导出文件修复启动问题。

## 无Make入口和验证

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/start-infrastructure.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/start-infrastructure.ps1 -Action status
# 基础设施初始化后，两个终端分别运行：
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run-project.ps1 -Action backend
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run-project.ps1 -Action frontend
```

基础设施健康等待默认180秒，可使用`-TimeoutSeconds 300`调整。所有脚本支持`--help`，失败返回非零码。

```powershell
# 初始化后仅解析Compose配置，不输出真实密码：
docker compose --env-file .env.infrastructure.local config --quiet
# 不操作Docker的初始化及编排回归：
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tests/scripts/start-infrastructure.test.ps1
```

启动和build不执行业务测试或性能专项；正式质量门禁仍按[构建规则](../docs/rules/build.md)执行。此次决策见[ADR-0010](../docs/adr/0010-local-infrastructure-bootstrap.md)，真实验证及限制见[验证记录](../docs/validation/local-infrastructure-bootstrap.md)。

## 进度实例配置

`EXPORT_FLOW_INSTANCE_ID`默认local-api，同环境多个API实例需配置不同且跨重启稳定的标识。`EXPORT_FLOW_PROGRESS_ENABLED`控制进度通知，`EXPORT_FLOW_REDIS_*`配置可降级缓存；缓存故障回源MySQL。初始化的MQ账号在本项目vhost内具备任务及进度交换机／队列操作和管理接口核验权限。任务功能说明见[004 quickstart](../specs/004-task-management/quickstart.md)。
