# 本地启动脚本

根目录[Makefile](../Makefile)封装[run-project.ps1](run-project.ps1)。当前支持Windows PowerShell 5.1及以上和GNU Make；其他系统需另行适配。入口只启动宿主机前后端，不操作现有容器、数据库账号、源订单或共享MQ配置。脚本为UTF-8 BOM，兼容Windows PowerShell中文解析。

## 一次性准备

安装Java21、Node22 LTS（至少22.12）、GNU Make；npm随Node安装。前端始终通过npm exec使用锁定pnpm10.34.6，无需修改全局pnpm。首次执行需要网络下载依赖和Maven Wrapper。Windows可安装GNU Make：

```powershell
winget install --id ezwinports.make --exact --source winget --scope user
```

若安装后找不到make，重新打开终端并确认GNU Make的bin目录已加入PATH。

本机2026-10-12已安装GNU Make4.4.1至`$env:LOCALAPPDATA\Programs\GNU Make\4.4.1\bin`并登记用户PATH。已打开的PowerShell不会自动刷新环境；在该终端执行下面两行即可立即使用：

```powershell
$env:Path += ";$env:LOCALAPPDATA\Programs\GNU Make\4.4.1\bin"
make dev
```

根目录另提供[make.cmd](../make.cmd)作为Windows兼容入口，PowerShell中`./make dev`或`.\make.cmd dev`均可使用。入口优先使用本机已安装的GNU Make4.4.1，否则从PATH查找make.exe；无需刷新当前终端PATH。它只将参数转给GNU Make并保留退出码；Makefile仍是唯一命令定义，实际工具链脚本仍在scripts目录。

复用负责人已启动的project-export-flow服务，应用数据库export_flow、迁移/运行账号和RabbitMQ vhost /export-flow须已准备；不能把应用JDBC地址指向源数据库exportflow。订单直接读取exportflow.orders，迁移账号需应用库DDL及来源SELECT/REFERENCES权限，运行账号只读订单、读写任务表，MQ账号需当前vhost权限及管理接口核验权限，详见[quickstart](../specs/002-order-management/quickstart.md)。本脚本不自动创建这些资源。

本机2026-10-12已按负责人要求准备被Git忽略的.env.local，并在已有服务内创建应用库export_flow、专属运行/迁移账号和MQ vhost /export-flow及专属账号；运行账号对来源订单仅有SELECT权限，真实密码只存本地配置。当前直接执行`make dev`，不要用示例覆盖已准备的文件。

2026-10-17本机8080被Steam调试服务占用，`.env.local`现设`EXPORT_FLOW_BACKEND_PORT=8081`。后端和Vite代理共用该键，页面仍为5173，无需关闭Steam。示例及未配置默认值保留8080；修改端口后重新启动前后端。诊断和实际验证见[运行修复记录](../docs/validation/004-task-management/runtime-startup-fix.md)。

其他开发环境首次配置时，在项目根执行：

```powershell
if (-not (Test-Path .env.local)) { Copy-Item .env.example .env.local }
# 编辑 .env.local，将占位值改为自己的本机连接值。
make dev
```

[.env.example](../.env.example)不含真实凭据；.env.local被Git忽略。每行KEY=value，可用整值引号；不会执行配置内容，值内的#、=、$保留。文件配置优先于当前进程环境；也可只设置相同环境变量而不创建文件。不要在终端输出或提交实际密码。

本机`EXPORT_FLOW_FILE_ROOT`已配置为项目根目录的`export-data`绝对路径；项目迁移到其他位置时同步修改此项。示例保留可选的绝对路径覆盖，未配置时应用使用启动工作目录的`export-data`，标准入口固定工作目录为项目根。Excel保存在`export-data/tasks/<任务ID>/artifacts/<文件ID>.xlsx`，断点和临时运行文件也位于该根目录，全部由Git忽略。修改配置后由用户重启后端；现有文件需要保留任务目录结构，不能只移动Excel导致断点引用失效。日志目录仍使用原配置。

## 命令

| 命令 | 行为 |
| --- | --- |
| make / make help | 显示帮助 |
| make check | 检查工具与必需配置；实际依赖连接由后端启动核验 |
| make dev | 检查通过后并行启动前后端；Ctrl+C停止前台应用 |
| make backend | 打包当前后端源码后启动，启用真实消费者，最大堆512MiB |
| make frontend | 冻结锁文件安装前端依赖后启动Vite |
| make install | 只安装前端依赖 |
| make build | 构建前端dist与可执行后端jar |

启动与build不执行测试或性能专项；正式质量门禁仍使用[构建规则](../docs/rules/build.md)中的Wrapper verify及前端命令。停止应用保留已有任务、文件和三个共享容器，不提供按端口杀进程或清库操作。

前端地址[http://127.0.0.1:5173/orders](http://127.0.0.1:5173/orders)，API地址[http://127.0.0.1:8080](http://127.0.0.1:8080)，健康检查[http://127.0.0.1:8080/actuator/health](http://127.0.0.1:8080/actuator/health)。端口被占用时直接报错。

上行API为默认端口；本机当前实际API和健康检查使用`http://127.0.0.1:8081`及`http://127.0.0.1:8081/actuator/health`，由`EXPORT_FLOW_BACKEND_PORT`决定。

并行启动中任一服务因连接或端口失败时，查看错误并按Ctrl+C停止其余应用，再修正配置重新启动。

没有GNU Make时，在两个终端分别执行相同入口：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run-project.ps1 -Action backend
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run-project.ps1 -Action frontend
```

脚本也支持--help；任一命令失败返回非零码。实际核验见[启动入口记录](../docs/validation/002-order-management/startup-entry.md)。

## 任务进度实例配置

任务列表／SSE使用`EXPORT_FLOW_INSTANCE_ID`作为稳定实例身份，同环境每个API实例必须唯一；重复身份无法取得exclusive consumer时拒绝就绪。`EXPORT_FLOW_PROGRESS_ENABLED`控制进度通知，Redis连接由`.env.example`中的`EXPORT_FLOW_REDIS_*`配置，缓存故障时回源MySQL。脚本允许这些外部变量。应用MQ账号除原命令权限外，还需当前vhost内进度交换机`exportflow.progress.v1`与本实例队列`exportflow.progress.v1.<instanceId>`的声明、绑定、发布和消费权限；不授予其他vhost权限。任务功能和验证步骤见[004说明](../specs/004-task-management/quickstart.md)。
