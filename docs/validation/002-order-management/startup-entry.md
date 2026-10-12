# Make启动入口核验

日期：2026-10-12。负责人要求新增Make启动脚本，按低风险现有工具链封装执行；本记录不重开已取消性能或[本轮免验](../../../specs/002-order-management/tasks.md#本轮免验确认)。入口及使用见[脚本说明](../../../scripts/README.md)，逐文件/函数职责见[实施补充](../../../specs/002-order-management/implementation-notes.md#本地启动入口)。

## 环境与前置

Windows PowerShell5.1、Java21、Node22.14.0；npm通过本机nvm提供npm.exe。入口固定使用pnpm10.34.6，未改变全局pnpm。初次核验时系统PATH没有GNU Make；核验使用artifacts/tools/make-4.4.1/package/tools/install/bin/make.exe（GNU Make4.4.1，Chocolatey make4.4.1 portable包），winget/SourceForge下载未成功。负责人随后报告make命令不存在，已将核验通过的完整portable目录及许可证安装至LOCALAPPDATA/Programs/GNU Make/4.4.1，安装文件摘要与核验来源一致，并只向用户PATH追加bin目录，保留既有PATH。刷新核验进程环境后，Get-Command make、make --version和make help通过；已经打开的用户终端须按[脚本说明](../../../scripts/README.md)刷新PATH。

首次只读核对共享服务：MySQL/RabbitMQ/Redis容器均healthy，当时应用库export_flow不存在，RabbitMQ仅有默认vhost /。首次核验没有EXPORT_FLOW或Flyway连接环境变量，也未创建真实.env.local，未实际启动后端。入口不承担创建资源；后续负责人要求准备本机配置，资源已补齐并成功启动，见本页下方记录。

## 执行结果

| 实际命令/检查 | 结果 |
| --- | --- |
| powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run-project.ps1 --help | 退出码0，帮助可在无配置时使用，中文正常 |
| 同一入口 -Action check；portable make dev | 缺必需连接键时退出码1；Make在check阶段失败，没有构建/启动前后端 |
| PowerShell -Action build | 退出码0；冻结安装pnpm10.34.6、前端typecheck/Vite构建、后端Wrapper package成功；后端测试跳过，未运行性能 |
| portable make help；portable make -n dev | 退出码0；递归并行两个独立前台动作，目标命令正确 |
| 隔离配置烟测startup-smoke.ps1 | 7项通过：缺配置、后端提前失败、模板占位、有效配置、未知键、错误格式、整值引号及特殊字符原样保留；错误输出未回显模拟密码 |
| portable make dev，RUN替换为本轮等待进程stub | 两个PowerShell及两个Node子进程并行运行；Ctrl+C后Make非零退出，4个已记录PID均不存在，没有残留；未连接业务服务 |
| review | 数组参数改为显式传递；兼容npm.exe/npm.cmd；递归Make路径加引号；PowerShell脚本UTF-8 BOM；并行中单服务失败需Ctrl+C停止其余服务，文档已注明 |

本轮构建jar的SHA256为FBA53C072CE7E59BDE7BBC9DDBD58D12CA0554EFE3B09A3719CA26CA78A7400C。前端原有大chunk提示保留，构建成功；不将本轮跳过测试的启动构建冒充正式质量门禁。配置和进程stub仅在被Git忽略的artifacts中，无真实凭据、不操作容器、不造数据。

最终文档检查退出码0：225个文件、2238处本地链接/锚点可解析，44项本期验收映射完整；git diff --check退出码0。递归Make路径加引号后的并行/Ctrl+C再次核对通过，无子进程残留。安装后的含空格路径另以make dev及回显RUN核对，check/backend/frontend均执行，退出码0。

## 本机配置准备

2026-10-12负责人要求“帮我把配置文件准备好，并准备一个配置示例”。沿用已确认的应用schema/vhost和已有服务，将此前缺失资源补齐；不重开规格、业务实现或已免验专项。生成根.env.local的本机真实配置及随机专属密码，git check-ignore确认忽略，git ls-files确认未跟踪；[.env.example](../../../.env.example)保持同一组配置键，只保留密码占位，说明URI不追加vhost路径、文件根和日志默认值。

MySQL现有容器内新建export_flow应用库及export_flow_app/export_flow_migrator账号。运行账号仅获应用库SELECT/INSERT/UPDATE/DELETE及exportflow.orders的SELECT；迁移账号仅获应用库DDL/DML和来源SELECT/REFERENCES，无全局权限，不写源订单。现有RabbitMQ内新建/export-flow及export_flow_mq账号，monitoring标签满足节点/拓扑核验，AMQP权限仅授予本期应用交换机/队列名。未修改已有账号、全局策略或容器；不新增可提交的基础设施初始化脚本。

实际make dev暴露两项启动配置问题并修正：本机JDK在用户临时目录初始化NIO pipe失败，启动入口仅给本次JVM指定已验证可用的backend/target完整socket临时路径，不改系统TEMP；Spring RabbitMQ地址配置附加vhost路径触发隔离检查失败，配置URI改为只含协议/账号/地址，vhost由已有application.yml固定为/export-flow。保留故障证据于本机应用日志，两个修正均经实际重启验证。

最终启动核验：前后端均由make dev启动；/actuator/health为UP，/orders返回HTTP200，经Vite代理/api/v1/orders?page=1&pageSize=20取得total=110000及20行。Flyway版本1–4均成功；应用任务数0；MQ专属队列1个消费者、0条消息；原始订单仍为110000行。只做配置与启动烟测，未创建导出任务、生成订单或运行性能测试，也不替代既有浏览器手工免验。核验结束Ctrl+C停止前后端，配置、应用表及共享容器保留，用户可自行执行make dev。

配置与示例9项键对齐，示例未包含本机真实密码；make check通过，停止后5173/8080没有监听，三个共享容器仍healthy。更新后文档检查225个文件、2242处链接/锚点及44项验收映射通过，git diff --check退出码0。

## Windows相对路径兼容入口

负责人再次执行./make dev遇到命令不存在，新增根[make.cmd](../../../make.cmd)，只转发至用户目录已安装GNU Make或PATH中的make.exe，固定项目工作目录，不重复定义目标。当前进程未额外刷新PATH时，PowerShell实际执行./make help、./make check和./make -n dev均退出码0；未知目标返回Make退出码2，未吞掉失败。没有重新运行业务或性能测试；当前前后端保持停止，用户可直接使用./make dev。

## 项目内导出运行目录

2026-10-13负责人要求将生成文件改到当前项目根目录，明确旧文件已删除、无需复制，由其自行重启后端；本次不新建规格，只修正既有配置与规格。已创建D:/project/ai-vibe-coding/export-flow/export-data，并将本机.env.local中的EXPORT_FLOW_FILE_ROOT改为该绝对路径。application.yml的未配置默认值改为user.dir/export-data，标准Make/PowerShell入口固定项目根工作目录；示例说明默认值及可选的绝对路径覆盖。日志位置保持原值。

整个export-data目录由根.gitignore排除，包含Excel、断点和临时工作区；完成文件仍按tasks/<任务ID>/artifacts/<文件ID>.xlsx分层。未复制旧文件、未启动或重启后端、未修改数据库或容器、未运行业务或性能专项。

实际核验：目录存在；.env.local路径与项目export-data一致；./make check退出0；git check-ignore export-data/probe.xlsx确认忽略，git diff --check退出0。此次验证仅证明配置和文档就绪，不声称运行中的后端已重新加载配置；由用户自行重启后生效。

14份相关文档的本地链接与锚点核验通过；默认配置、本机绝对路径及Git忽略一致，export-data没有已跟踪文件。核验过程未启动后端或迁移旧文件，详细结果见[目录配置检查](../../../artifacts/export-data-location-check.json)。
