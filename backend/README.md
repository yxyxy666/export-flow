# 后端职责结构

基础骨架历史见[原计划](../specs/001-project-bootstrap/plan.md)。2026-10-11[订单规格0.4](../specs/002-order-management/spec.md)获开发授权，现已建立Java21、Spring Boot3.5、Maven3.9 Wrapper、MyBatis与Flyway正式工程，查询、创建、Outbox、Worker、真实SXSSF和下载链路正在完整验证。

当前状态与实际命令见[实施进度](../docs/validation/002-order-management/implementation-progress.md)和[任务清单](../specs/002-order-management/tasks.md)。2026-10-12已按负责人要求删除订单造数并复用既有来源，当前门禁和数据不足记录见[来源复验](../docs/validation/002-order-management/provided-data.md)；此前316项属于[历史方案证据](../docs/validation/002-order-management/green-results.md)。性能测试已按负责人要求取消，开发及review本轮收尾；共享broker进程重启及目标浏览器手工已由负责人确认本轮无需验证。

2026-10-12[003增量](../specs/003-select-all-filtered/spec.md)增加FILTERED可选排除，复用export_task_orders、原幂等/配置版本与Worker恢复，不新增迁移或来源写入；证据见[增量验证](../docs/validation/003-select-all-filtered/verification.md)。

## 职责与依赖方向

日常启动在仓库根执行`make backend`，同时启动前后端使用`make dev`。入口读取.env.local、打包当前源码并启用消费者；配置和已有服务前置见[脚本说明](../scripts/README.md)。启动入口不重复运行正式质量门禁或性能专项。

导出运行目录为项目根的`export-data`，完成文件位于`export-data/tasks/<任务ID>/artifacts/<文件ID>.xlsx`。整个目录被Git忽略；本机.env.local提供绝对路径，未配置EXPORT_FLOW_FILE_ROOT时使用user.dir/export-data，标准入口固定项目根目录。修改后由用户自行重启后端。

- `orders/`：订单传输、查询用例、领域、读取端口及持久化适配。
- `exporttasks/`：任务受理、下载编排、权威状态、文件与产物端口；任务列表/缓存延期，无空占位。
- `dispatch/`：命令 Outbox、持久端口和可靠消息发布适配。
- `worker/`：消息消费边界、执行编排与流式 Excel 生成适配。
- `shared/api/`：安全错误协议与传输错误映射。
- `config/`：外部配置与本机隔离校验、运行观察与独立清理调度。
- `resources/db/migration/`：V1至V4版本化迁移，空库/上一受支持版本按真实MySQL验证。

按“传输 → 应用 → 领域与端口”依赖，基础设施实现端口，配置负责装配。
创建任务及 Outbox 的原子提交不能因为目录分层而拆成两个事务。
目录存在不表示 Worker 暂停、恢复或其他候选协议已获批准。

技术栈依据[后端规则](../docs/rules/backend.md)及已接受ADR。生产凭据用环境变量或仓库外配置注入，必须使用本机独立schema/vhost；字段清单、运行和回退见[quickstart](../specs/002-order-management/quickstart.md)。

正式门禁使用backend Wrapper verify；按当前免验范围过滤性能和大规模文件用例，准确命令见[003验证记录](../docs/validation/003-select-all-filtered/verification.md)。不要直接无筛选verify恢复已取消的测试；构建成功后运行`java -jar backend/target/export-flow-0.2.0-SNAPSHOT.jar`。演示角色需显式启用`--export-flow.consumer-enabled=true`；消费默认关闭用于独立API/测试角色。生成能力不可用时禁止启用消费者。订单只读[负责人已有数据](../specs/002-order-management/provided-data.md)；V1仅建引用视图，造数脚本及工具已删除，不改写原始订单。
