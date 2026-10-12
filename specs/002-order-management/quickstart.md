# 本地启动与验证

日期：2026-10-11。规格0.4和开发授权见[审批记录](tasks.md#审批记录)。订单前端和正式后端已实施；当前完整回归、性能、浏览器及最终review状态见[实施进度](../../docs/validation/002-order-management/implementation-progress.md)，不能用入口已存在代替验收通过。

## 前置条件

Windows PowerShell、Java21、Node22 LTS（至少22.12）、pnpm10.34.6，以及本机MySQL8.4/RabbitMQ3.13。精确版本见[版本表](dependency-versions.md)，隔离与运行参数见[决策记录](decision-record.md)。复用现有服务，不新增Compose；本期不连接Redis。

生产运行仅使用本机独立schema `export_flow`及RabbitMQ vhost `/export-flow`。迁移账号有DDL权限，运行账号仅能SELECT orders及读写任务/Outbox/执行/断点/产物表；来源表仅授予SELECT，迁移账号另需REFERENCES。连接凭据来自仓库外配置或环境变量，不写入仓库或日志。当前本机及配置示例按2026-10-13负责人要求使用项目根目录export-data，若设置EXPORT_FLOW_FILE_ROOT覆盖值，须使用受控绝对路径，不能含reparse/junction；该运行目录被Git忽略，启动会验证写入和同卷原子移动能力。未配置时应用默认使用user.dir/export-data，标准入口固定项目根目录；修改配置后由用户自行重启后端。

## 正式工程

### 日常启动

2026-10-12已按负责人要求准备当前机器的.env.local和应用专属资源，现在可直接`make dev`；其他环境按下文配置，不覆盖已有真实文件。本机连接和启动核验见[启动入口记录](../../docs/validation/002-order-management/startup-entry.md#本机配置准备)。

在仓库根复制[配置模板](../../.env.example)为被Git忽略的.env.local，填写运行账号、迁移账号和MQ连接；应用库export_flow及vhost /export-flow须已准备。安装GNU Make后执行`make dev`，前台同时启动前后端，Ctrl+C停止应用；可在两个终端分别执行`make backend`和`make frontend`。`make build`仅构建dist/jar，不运行测试或性能专项。详细命令及Windows前置见[脚本说明](../../scripts/README.md)，实际结果见[启动入口记录](../../docs/validation/002-order-management/startup-entry.md)。已有容器、来源订单和此前免验结论保持原职责。

### 正式质量门禁与直接命令

在仓库根执行；第一次Wrapper需联网取得固定Maven3.9.16分发包并校验SHA256：

```powershell
pnpm --dir frontend install --frozen-lockfile
pnpm --dir frontend test:run
pnpm --dir frontend typecheck
pnpm --dir frontend build
backend/mvnw.cmd -f backend/pom.xml verify
```

Wrapper verify运行单元、真实隔离集成、Spotless、Checkstyle、SpotBugs与JaCoCo。Docker Desktop须可用，全部复用负责人提供的三个服务；测试仅在共享服务内使用自己的隔离资源，见[当前环境](../../docs/validation/002-order-management/shared-services.md)。仅性能开关关闭的专项默认不运行，正式性能命令见下节。编译或环境阻塞不算业务红灯。

直接使用java命令时，配置以下环境变量的真实本机值后启动后端：`EXPORT_FLOW_JDBC_URL`、`EXPORT_FLOW_DB_USER`、`EXPORT_FLOW_DB_PASSWORD`、`EXPORT_FLOW_AMQP_URL`；可选`EXPORT_FLOW_MANAGEMENT_URL`、`EXPORT_FLOW_FILE_ROOT`、`EXPORT_FLOW_LOG_FILE`。Flyway迁移账号通过`SPRING_FLYWAY_USER`、`SPRING_FLYWAY_PASSWORD`或外部`spring.flyway.user/password`配置，运行账号不提升DDL权限。只有Make/PowerShell启动入口读取.env.local，直接java不自动读取。日志本地滚动保留7天，只暴露health/info。

```powershell
java -jar backend/target/export-flow-0.2.0-SNAPSHOT.jar --export-flow.consumer-enabled=true
pnpm --dir frontend dev
```

后端127.0.0.1:8080，Vite127.0.0.1:5173，开发代理/api/v1到后端；端口冲突直接失败。消费者默认关闭以支持独立API角色，完整演示须显式开启。dispatcher、到期裁决、执行监督、运行观察与孤儿许可清理均为独立周期；生成期间不占长事务。

订单直接读取负责人提供容器内的`exportflow.orders`，通过本项目`orders`视图适配已有列；没有订单建表、造数脚本或自动初始化。运行前确认来源存在及只读权限，详见[来源与字段](provided-data.md)。当前来源110000行，200000/200001行场景数据不足按负责人要求记录缺口，不生成新订单。性能脚本支持`--help`且保留UTF-8 BOM。

## 验证与证据

页面手工步骤见[浏览器检查表](../../docs/validation/002-order-management/browser-checklist.md)：1280×720、Chrome/Edge真实版本、键盘焦点、筛选/分页/选择、导出字段与同键找回。201/200成功均留在订单页，不追加详情GET/SSE/轮询；任务页仍为文字占位。后端成功结果通过直接下载URL验证，不增设前端详情页。

完整文件、故障/kill、租约与旧fence、READY采用、成功提交不明、断点/清理互斥由自动化集成验证；不能停止共享服务作故障注入，不能以文件存在代替权威成功引用。

所有压力测试结束后独立串行执行：

```powershell
powershell.exe -NoProfile -File scripts/check-order-performance.ps1
```

固定512MiB JVM、复用project-export-flow的独立schema/vhost/账号、API30次预热/200样本nearest-rank P95、来源足够时110000/200000行九列各5次完整生成。当前仅有110000条，200000规模记录数据缺口并跳过，不算功能失败、不造数。证据固定保存到仓库artifacts/002-order-management，包含原始延迟、状态、耗时、堆与scratch观察；完整准确性另逐行核对。不把并行回归耗时当产品性能证据。

2026-10-12负责人明确取消性能测试，当前不执行上述专项，也不补测剩余API场景；既有Excel测量保留为历史实际证据。API独立测量中止，未收齐样本／未取得完整P95报告，不将人为中止判为功能失败或性能通过，见[来源记录](../../docs/validation/002-order-management/provided-data.md)。

## 回退

停止新受理/消费，停机宽限15秒；不释放仍可能写入的持久执行权，未确认命令留RabbitMQ，后续经lease/fence裁决。保留任务、幂等、Outbox、断点、READY和成功文件，不清表/清队列/重置fence。孤儿只凭数据库许可删除。

测试前确认已有三个服务健康，不再由Testcontainers启动服务。测试需要本机Docker CLI及MySQL容器管理员访问；管理员凭据仅在内存中读取，普通业务仍使用专属schema账号。故障测试只关闭专属AMQP TCP桥连接，不停止共享broker；进程重启持久性缺口及性能资源差异见[共享服务验证](../../docs/validation/002-order-management/shared-services.md)。

正式Failsafe按测试类使用新的串行512MiB JVM（forkCount=1/reuseForks=false），harness同样不跨类复用fork；避免数百次Spring/Mockito装配累积到同一进程。每用例结束关闭实际服务连接并清理inline mock状态，生产导出资源限制保持512MiB。
