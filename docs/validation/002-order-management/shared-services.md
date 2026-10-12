# 复用project-export-flow服务

> 2026-10-12最终状态：负责人确认本轮剩余验证无需执行，性能已取消；原“待验／待补／未验证”为历史事实，不再是本轮待办。范围和日期见[免验确认](../../../specs/002-order-management/tasks.md#本轮免验确认)，免验不标实测通过。

> 2026-10-12状态更新：下文316项等通过统计属于旧数据方案的历史证据；当前已删除订单造数，直接读取负责人容器中的110000条订单。当前复验及200000规模数据缺口以[来源记录](provided-data.md)为准，不沿用历史通过结论。

2026-10-11负责人明确要求直接复用已准备的project-export-flow。原独立容器方案由此替换；规则归属[容器规则](../../rules/container.md)、[测试规则](../../rules/testing.md)，规格设计见[计划](../../../specs/002-order-management/plan.md)、[任务](../../../specs/002-order-management/tasks.md)、[实施补充](../../../specs/002-order-management/implementation-notes.md#共享服务夹具)。

## 当前环境

| 容器 | 实际入口 | 配置事实 |
| --- | --- | --- |
| project-export-flow-mysql-1 | 127.0.0.1:3306 | MySQL8.4；内存和CPU限额均为0（未设置） |
| project-export-flow-rabbitmq-1 | 127.0.0.1:5672/15672 | RabbitMQ3.13；内存和CPU限额均为0；全局consumer_timeout=1800000毫秒 |
| project-export-flow-redis-1 | 127.0.0.1:6379 | Redis7.4；当前订单链路不使用 |

`docker ps -a`确认只剩以上3个服务且均健康。本次额外创建的6个容器已按原测试日志完整ID精确清理，未停止负责人服务。镜像ID：MySQL `sha256:3466ba4a4828aa8d46fb7c3bc16b67b781c98413cf4ea0fac6feaa6e881faa26`；RabbitMQ `sha256:606d8c0d6b3c18d1da9afc53bc7cdb2a8d5486df91b5a9830e9e07626c9ae281`；Redis `sha256:ff02b58f971e7d7d156a1267e283fcbbeee91773b6aa36c49dac28ecfe28eadf`。未输出或提交既有密码。

## 业务数据边界核对

负责人再次要求复用已提供的基础设施并删除无用内容。只读核对示例项目Compose、七份Flyway迁移及实际information_schema：既有业务schema为`exportflow`，包含`orders`、`export_jobs`、`export_job_attempts`、`outbox_events`及Flyway历史。当前应用按已批准规格使用`export_tasks`、`command_outbox`、执行权、断点和产物记录；订单列也与示例不同（例如`amount/channel/region`与示例`total_amount/sales_channel/shipping_province`）。提供服务不等于两个应用的业务表契约相同，直接移除本应用迁移或指向示例schema会导致SQL不匹配或Flyway历史冲突。

本项目没有Compose或服务初始化SQL，生产迁移不含CREATE DATABASE/CREATE USER；Redis本期无调用方，未保留对应实现和依赖。删除`backend/src/main/resources/db/migration/README.md`过时占位说明，迁移职责与执行入口集中在[后端README](../../../backend/README.md)及[quickstart](../../../specs/002-order-management/quickstart.md)。必要任务模型和迁移仍有实际调用；2026-10-12已删除造数工具及订单夹具，正式运行、演示及数据库测试直接读取来源订单。未修改示例表、已有订单、账号或服务配置，当前证据见[来源记录](provided-data.md)。

## 隔离与回收

`SharedServices`逐用例创建`export_flow_test_<32位hex>` schema与schema专属账号，RabbitMQ使用`/export-flow-test-<32位hex>` vhost和唯一测试账号。管理员连接只用于准备/回收、测试DDL与行锁观测，生产调用仍普通schema账号。策略命令只允许本次vhost、账号；禁用停止共享broker等操作。文件使用独立临时目录保留诊断。

AMQP使用本机测试TCP桥真实转发至既有broker；故障时关闭本测试socket，恢复后重新连接。测试名称已改为“连接丢失”，不宣称它证明broker进程重启后的落盘持久性。ACK超时由本项目队列单独设置10分钟，保留共享broker全局配置。

原性能方案的MySQL2CPU/2GiB、RabbitMQ1CPU/1GiB限制不再施加；运行记录保留资源差异。共享服务上的测量不能冒称与旧独立容器环境等价，其他负载和TCP桥开销均须注明。

## 实际证据

本地证据目录`artifacts/002-order-management/`（忽略文件）：

- `backend-shutdown-red.log`复现Spring通道停止时guard仍有效；`backend-shared-unit-green.log`停机顺序与严格隔离名称两项通过。
- `backend-shared-isolation-red-05.log`是严格动态名称被旧运行校验拒绝的行为红灯；前几次编译/测试配置错误保留但不作行为红灯。
- `backend-shared-environment-red-02.log`：4项真实环境检查通过，队列级ACK超时缺失1项预期失败；首次Surefire直接运行因Windows短临时路径的NIO管道报错，不作业务红灯。后续真实边界使用正式Failsafe配置的完整临时路径。
- `frontend-review-green-03.log`：153/153通过；最新typecheck/build通过，前端不依赖容器。
- `backend-shared-policy-green-02.log`：24/24通过，含9项策略、5项发布、5项运行能力、5项权限检查。早先Windows CLI JSON引号损坏及HTTP/2 EOF证据保留，现用管理API HTTP/1.1。
- `backend-shared-unit-green-02.log`：停机顺序、停用消费者不等待无回调、严格名称与初始化事务4/4通过；无回调等待红灯见`backend-inactive-shutdown-red.log`。
- `backend-dependency-evidence.log`：正式依赖树与有效POM生成成功，依赖树没有Testcontainers/Docker-Java/Ryuk。
- `backend-shared-batch-green.log`：27/27通过；批量JDBC写入优化、动态schema初始化、整批回滚、数据不覆盖、200000受理与超限/请求体拒绝均用真实服务复验。
- `backend-shared-verify-01.log`已通过：ExcelContent24项（含110000/200000九列逐行核对）、ExcelResource独立512MiB进程无OOM、ExcelBoundary13项、ExcelValidationBoundary13项、ExecutionSupervision7项（实际五分钟期限）与真实下载边界。长批次在恢复套件启动时测试JVM堆耗尽，退出1；不是完整通过证据。原6项策略安装错误已在24项专项中解除。
- 堆诊断在`shared-full-01-reports/`保留，失败栈发生于Spring重复装配后的异常日志包装，根因未由堆快照确定。现按类使用新的串行512MiB JVM，夹具结束清理Mockito inline状态；不提高堆上限。遗留的1个schema/专属账号、1个vhost/账号已按完整名称回收，未改用户服务。`backend-shared-verify-02.log`补验两项受影响策略套件和两个未完成恢复套件，并执行质量门禁。
- 旧`backend-verify-01.log`、`backend-worker-review-green.log`人为中断，保留已执行结果，不写整个批次通过。

## 剩余缺口

共享broker进程重启的持久性验证尚未执行；不能为覆盖该项停止用户服务。按类汇总后端316项回归及正式质量门禁已通过，JaCoCo HTML/XML已生成，详见[当前结果](green-results.md)。独立性能尚在测量，目标浏览器手工证据仍缺失。[浏览器限制](browser-checklist.md)保持，不能用HTTP或jsdom替代。
