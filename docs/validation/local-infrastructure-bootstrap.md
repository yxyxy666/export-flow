# 本地基础设施从零初始化验证

- 日期：2026-10-20（Asia/Hong_Kong）。
- 范围：负责人明确要求全新环境先make infra生成基础设施，再make dev启动应用，本次无需规格。
- 关联：[ADR-0010](../adr/0010-local-infrastructure-bootstrap.md)、[运行规则](../rules/container.md)、[启动说明](../../scripts/README.md)、[回归测试](../../tests/scripts/start-infrastructure.test.ps1)。

## 修正范围及失败证据

此前初稿的“优先发现并复用其他项目容器”和“make dev先创建基础设施”不符合本次要求，已删除。先改编排测试，旧实现执行时在全局Docker ps处明确失败：`不得扫描或复用其他项目的容器。`；此为项目隔离行为红灯，随后才改为本项目Compose创建及零配置初始化。

旧版15项跨项目复用测试与Docker未启动限制属于已替换初稿，不再作为当前通过证据。当前实现不以历史示例容器、110000条订单或预置应用账号为前提。

## 当前回归及真实空环境结果

环境：Windows、Windows PowerShell5.1、GNU Make4.4.1、Docker Compose v5.5.1、Docker Engine29.7.2；本轮启动Docker Desktop Linux引擎完成真实验证。

实际镜像digest：MySQL为`sha256:3466ba4a4828aa8d46fb7c3bc16b67b781c98413cf4ea0fac6feaa6e881faa26`，Redis为`sha256:ff02b58f971e7d7d156a1267e283fcbbeee91773b6aa36c49dac28ecfe28eadf`，RabbitMQ为`sha256:606d8c0d6b3c18d1da9afc53bc7cdb2a8d5486df91b5a9830e9e07626c9ae281`。

| 验证 | 实际结果 |
| --- | --- |
| `powershell.exe -NoProfile -ExecutionPolicy Bypass -File tests/scripts/start-infrastructure.test.ps1` | 退出0，11项通过；含完整服务创建、项目隔离、Docker/Compose失败、不健康/超时、零配置生成、凭据匹配、重复保留、旧连接同步及备份、用户非连接设置保留、端口匹配及dev职责 |
| 全新临时目录执行`make.cmd infra` | 退出0；无.env.local或.env.infrastructure.local，自动生成随机凭据、连接配置，创建三个新容器、三个新数据卷及网络，等待全部健康 |
| 再次执行`make.cmd infra` | 退出0；容器ID保持不变，配置和凭据保留 |
| MySQL结构与账号 | 新来源orders行数0；两个独立应用/迁移账号存在；运行账号能读来源，DELETE WHERE 1=0被权限拒绝 |
| 新配置执行`make.cmd check` | 退出0，自动生成配置通过应用入口校验 |
| 现有可执行jar使用新基础设施启动 | 后端健康UP，Flyway成功迁移8个版本，export_flow.orders为VIEW |
| 订单查询`GET /api/v1/orders?page=1&pageSize=20` | HTTP200，items为空、total=0，符合全新环境空订单表 |
| Compose `config --quiet` | 退出0，未输出含真实密码的完整配置 |
| `make.cmd -n dev` | 退出0，仅check及并行backend/frontend，没有infra操作 |
| `make.cmd help` | 退出0，明确先infra、后dev |
| `git diff --check` | 退出0，无空白错误 |

真实专项使用被Git忽略的`artifacts/local-infrastructure-bootstrap/smoke-58f157c1e8494225989f53faca9d9172/`，保留无密码摘要summary.json与本地后端日志；测试模板仅将端口调整为MySQL13306、Redis16379、AMQP15673、管理25672，后端使用18080，避免干扰本机其他服务。除端口外使用当前Compose、初始化脚本、Make入口及配置模板。

启动前已核对export-flow项目不存在任何容器或卷。验证结束后按精确进程命令及Compose工作目录标签核对归属，停止本次专用后端，回收本次新建容器、卷与网络。没有接管示例服务，没有修改项目根已有.env.local、历史订单或账号。

## 范围限制

上述最初空环境后端冒烟使用已构建jar。随后已在项目根完整执行make dev，见下方启动修复记录。未执行前端浏览器检查、依赖历史110000行来源的业务全量回归、性能测试或跨Docker重启持久性验证，不将它们记为通过。后续业务数据仍按实际场景准备。

初始化脚本只在本项目MySQL空数据卷首次启动执行；重复命令保留数据及密码，修改密码配置不会自动改已有账号。旧应用连接字段按当前基础设施同步并备份，非连接设置保留。

## 旧环境启动失败修复（2026-10-20）

项目根新容器健康，但保留了2026-10-17的旧.env.local。只比较一致性而不打印密码，发现运行、迁移、MQ三组密码均与新基础设施不符。完整make dev复现MySQL1045错误：`Access denied for user 'export_flow_migrator'`。同时本项目旧前端进程占用5173，重复启动前端报端口占用。8080归Steam占用，因此保留本项目既有8081配置。

先补旧密码同步回归，旧实现明确失败“旧连接凭据必须同步为本项目新基础设施凭据”，再修正初始化入口：备份完整旧文件，仅同步数据库、Flyway、MQ、Redis连接字段，保留端口、文件根和注释。一致时不重写或重复备份；11项回归全部通过。

在项目根执行make infra完成连接修复，旧文件备份为被Git忽略的.env.local.backup-*；没有重置容器账号或删除数据。按完整命令行确认并停止本项目失败启动遗留的Vite进程后，再完整执行make dev：前端依赖安装及后端package通过，8081健康接口UP，5173/orders返回200，经5173代理的订单接口返回空列表和total=0。当前新环境未装载订单，空列表符合预期。

实际失败日志及无密码结果摘要位于本地`artifacts/local-infrastructure-bootstrap/startup-fix/`。开发进程此次保留运行，三个基础设施容器保持健康；端到端验证只覆盖HTTP入口与代理，不代替浏览器交互验收。
