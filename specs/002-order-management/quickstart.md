# 本地演示与验证计划

**状态**：草稿0.3；技术决策已定，规格整体仍待审；本页是后续获批实施后的操作设计，不是当前后端可用说明。当前可运行内容仍见[根 README](../../README.md)。关联 [plan](plan.md)、[research](research.md)、[tasks](tasks.md)。

## 已有环境

2026-10-07 用户确认 MySQL、RabbitMQ、Redis 已通过 Docker 启动；只读 `docker ps` 核对：

| 服务 | 容器／镜像 | 状态与入口 |
| --- | --- | --- |
| MySQL | project-export-flow-mysql-1／mysql:8.4 | healthy；localhost:3306 |
| RabbitMQ | project-export-flow-rabbitmq-1／rabbitmq:3.13-management-alpine | healthy；localhost:5672，管理15672 |
| Redis | project-export-flow-redis-1／redis:7.4-alpine | healthy；localhost:6379 |

这是此前运行观察，不证明当前运行或连接凭据、隔离 schema/vhost、策略或业务测试已通过。本次没有连接数据库、初始化数据或修改容器。复用现有服务，不另行安装中间件或新建 Compose；Redis本期保持缓存职责但不接入真实进度链。本轮docker ps退出码0且desktop-linux context无运行容器记录，实际执行前先复核context及既有服务状态，未重新启动或修改服务。

## 两次门禁对应的操作范围

规格批准前只核对文档。规格批准后仅创建测试工具、测试与夹具，设计命令为：

```powershell
pnpm --dir frontend test:run
mvn -f tests/backend/pom.xml test
```

上述脚本／测试harness尚不存在，已定Maven3.9.16，系统Maven当前未找到，测试准备时在外部提供该版本；此阶段不得生成正式生产 Wrapper 规避门禁。正式工程尚无导致的导入／编译失败记为缺口，不当业务红灯。准确依赖、工具、隔离资源及用例完整性明确后，按[任务清单](tasks.md#测试进度与证据)保存真实结果，提交第二门禁。

## 实施获批后的启动顺序

1. 确认准确Java/Maven/依赖版本、MySQL连接配置、独立schema、独立RabbitMQ vhost/队列与有效策略。凭据走本地外部配置／环境变量，不写入文档、仓库、URL或日志；未确认隔离名不能用示例项目数据库。
2. 正式后端生成后以Maven Wrapper运行Flyway验证／迁移；V3执行权和V4断点/产物迁移的架构子集已接受，仍须生产实施门禁。空库和已有V1升级场景分别验证，只增量迁移，不手工删表回退。
3. 显式运行合成初始化脚本，传入获批schema与外部连接配置。参数包括Count=110000、固定Seed，重复运行只核对相同种子／数量，不覆盖不一致数据。200001边界数据用独立测试环境，不修改共享演示数据。
4. 后端API、dispatcher及真实消费者按获批角色启动；先检查POI/存储目录/原子发布能力与恢复架构参数，能力不足拒绝启用，不保留默认停消费作为交付。dispatcher、到期扫描、执行监督与孤儿候选清理使用独立有界调度。应用只监听本机，后端绑定127.0.0.1:8080，Vite绑定127.0.0.1:5173并代理/api至后端；文件根外部配置，默认LOCALAPPDATA/ExportFlow/data。端口与目录设计已定，不代表服务已启动。
5. 前端冻结依赖安装、启动，通过 `/orders` 查询、选择与创建；创建后跳转 `/export-tasks?taskId=...` 展示权威结果回执；手动刷新查看实际进度，成功后下载xlsx并核对。观察真实Outbox、MQ、数据块、产物登记及结果引用，文件存在不能单独证明成功。

正式工程和脚本实现后使用的设计命令：

```powershell
pnpm --dir frontend install --frozen-lockfile
pnpm --dir frontend dev
pnpm --dir frontend test:run
pnpm --dir frontend typecheck
pnpm --dir frontend build
```

```powershell
Set-Location backend
.\mvnw.cmd test
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run
```

这些命令必须等工具链与实际产物存在再运行；本次未执行，不能填成功退出码。正式脚本的 PowerShell 参数与帮助形式见[函数清单](function-design.md)，脚本应同时提供要求的 `--help` 入口映射。

## 手工演示与故障验证

- 首次查询、组合条件、时间／金额边界、20/50/100分页、稳定升降序、跨页全选／反选／清空；无效范围不得请求。
- 修改筛选草稿不改变当前结果；筛选导出以已提交全部范围为准；字段默认与最后一项限制、无选择／空结果原因可见。
- 201与同键200均打开真实回执；明确拒绝保留状态；断连/超时保留原键原请求并“重试确认”，不发生随机换键重试。
- 实际启用消费者，使用冻结范围稳定批读并生成真实xlsx；验证成功100、失败实际进度、文件名/大小/结束时间及直接下载。暂时关闭消费者作隔离测试时，SENT等待不得按十分钟失败。
- MQ停机／恢复、confirm后标记前中断、有效策略不合规、旧凭据迟到、总窗口到期与SENT/认领竞态仅在隔离资源执行；不能暂停或重启示例项目共享容器作随意故障注入。故障注入采用独立Testcontainers实例，不能停止共享示例容器。
- 1280×720视觉、键盘／焦点及ADR目标浏览器手工检查；截图与版本记录一起归档。性能使用获批参考环境独立测量全部块读取/落盘/装配/校验/发布耗时；110000/200000真实文件可打开，独立512MB堆验证无OOM。

证据计划在 `docs/validation/002-order-management/`，本次未创建运行报告或截图。本期规划可演示真实文件、异常恢复与下载；用户暂停/显式恢复、完整任务列表和SSE留后续，不能将手工回执当作持续通知。

## 本期真实Excel与恢复验证步骤（实施后）

创建不同字段/两种范围任务，核对原始订单到下载文件的数量、表头与字段顺序、中文枚举、未知回退、原值、两位金额和香港时间；订单号/手机号前导零及以公式字符开头客户值仍为文本。将样本文件hash与源版本记录在excel-content.md，不在仓库提交真实客户资料。

在隔离环境分别于块落盘前后/断点提交前后、组装、READY登记、成功提交/ACK之间终止Worker进程；用原命令重交付验证owner/fence/持久退避、可信断点续写、可信READY采用和唯一成功引用。模拟旧身份迟到与产物采用/清理竞争，成功与仍引用数据不得误删。磁盘临时不可读不能冒充损坏并立即重建。批准恢复预算耗尽时验证合法失败及安全摘要。不能用暂停共享容器作随意故障注入。

正式工具链可用后执行TC-230至TC-236；性能/资源用获批独立进程与本项目隔离数据，不在同一测试套件中用高并发偶然时间判达标。已定启动配置及物理目录规则见[Excel方案](excel-design.md)，所有上述操作本次未执行。
