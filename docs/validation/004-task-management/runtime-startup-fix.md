# 本机任务列表404与SSE启动修复

**日期**：2026-10-17　**性质**：低风险本地连接配置修复，不新增业务行为或重新运行全量测试。关联[规格](../../../specs/004-task-management/spec.md)、[任务记录](../../../specs/004-task-management/tasks.md)与[启动说明](../../../scripts/README.md)。

## 原因与修正

负责人截图中的5173列表GET返回404。实际复现：8080健康检查和列表、5173代理列表均404且响应体为空；监听进程为Steam的steamwebhelper，启动日志明确报告后端8080端口被占用，未启动成功。此前交付未切换运行服务，不能将集成测试通过等同于用户本机已运行新版。

保留Steam进程，项目增加`EXPORT_FLOW_BACKEND_PORT`配置：后端application.yml读取该键，启动脚本校验并显示实际端口，Vite读取同一项目根配置作为代理目标；Vite只加载端口键，不向浏览器注入数据库或MQ凭据。默认8080不变，本机被忽略的.env.local改为8081。现有Vite重新加载配置后，5173代理指向8081。

首次8081启动暴露第二项配置缺口：原应用账号仅允许原命令资源，进度交换机声明被RabbitMQ拒绝。已保留其原权限，并仅在`/export-flow`为本项目账号补充`exportflow.progress.v1`及`exportflow.progress.v1.local-api`的配置／写／读权限；未授予其他vhost权限，不改其他账号、策略或容器。权限前后值保存在本地artifacts的runtime-mq-permissions-before.log与runtime-mq-permissions-after.log，无密码。应用重新启动后已正常消费进度队列。

## 实际结果与证据

| 检查 | 结果 |
| --- | --- |
| 标准入口后端启动 | 8081成功运行；真实消费者启用；未再出现权限拒绝 |
| 8081健康检查 | HTTP200，UP |
| 5173代理列表GET | HTTP200；不再访问Steam |
| 5173代理SSE | HTTP200，text/event-stream，收到stream-ready后主动关闭验证连接 |
| 前端生产构建 | 退出码0，含TypeScript检查 |
| 启动脚本 | 语法解析通过，Action check退出码0 |

结果见[脱敏烟测](../../../artifacts/004-task-management/runtime-port-smoke.json)、[前端构建](../../../artifacts/004-task-management/frontend-port-fix-build.log)、[后端启动](../../../artifacts/004-task-management/backend-runtime-startup.log)。首次SSE烟测误匹配事件名`ready`，被防挂取消，不计功能失败或计时测试；按既有契约`stream-ready`修正后通过。没有断言连接耗时、心跳间隔或重试时长，没有再跑全量功能或性能测试。

本次实际运行jar为backend/target下的当前构建，SHA-256为`5340e0636402772f1ad220a19438e3216a59132ed7bddc03f6bec9b966a6240e`，与前次独立交付目录jar区分保留；最新本机产物摘要见[清单](../../../artifacts/004-task-management/runtime-delivery-sha256.json)。此次启动连接本机应用库并加载兼容迁移；没有手工修改任务状态、删除文件或改写来源订单。烟测后曾保留前后端运行；后续进程交接见下节，基础设施继续复用原三个容器。

## 验证进程交接修正

负责人随后自行执行make dev，8081再次冲突。此次占用者为本轮验证遗留的Java进程1680，父进程为本轮后台启动器28516，而非Steam或用户新启动的后端。保留后台验证服务导致了这次冲突。

核对父子关系和命令行后，只向该独立控制台发送Ctrl+C。日志确认消费槽停止、Tomcat graceful shutdown complete；两个验证进程均退出，8081已释放。未终止用户新启动的5173前端进程31524，也未停止容器或删除数据。本次不修改业务代码、不重复运行测试。用户当前make仍等待前端退出，可在该终端Ctrl+C后重新执行`./make dev`，由用户终端统一管理前后端；不再留下后台后端占用端口。
