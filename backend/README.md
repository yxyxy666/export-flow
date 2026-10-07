# 后端职责结构

本期仅按[基础骨架计划](../specs/001-project-bootstrap/plan.md)预留文件和中文职责注释。
Java 文件没有 package、类、方法、注解或可执行入口；`pom.xml` 仅含 XML 注释，不是有效 Maven 工程。
没有 Maven Wrapper、后端依赖、接口、迁移、连接配置或可运行服务；不执行 Maven 或 Java 启动。
当前包目录前缀为 `com.exportflow`，后续工程坐标和依赖版本由对应规格确认。

## 职责与依赖方向

- `orders/`：订单传输、查询用例、领域、读取端口及持久化适配。
- `exporttasks/`：任务受理、查询、下载编排、权威状态及文件、缓存端口。
- `dispatch/`：命令 Outbox、持久端口和可靠消息发布适配。
- `worker/`：消息消费边界、执行编排与流式 Excel 生成适配。
- `shared/api/`：安全错误协议与传输错误映射。
- `config/`：未来运行配置注入与外层装配。
- `resources/db/migration/`：未来获批的 Flyway 迁移；本期无 SQL。

未来按“传输 → 应用 → 领域与端口”依赖，基础设施实现端口，配置负责装配。
创建任务及 Outbox 的原子提交不能因为目录分层而拆成两个事务。
目录存在不表示 Worker 暂停、恢复或其他候选协议已获批准。

未来技术栈依据[后端规则](../docs/rules/backend.md)及其中已接受 ADR。
实施状态与本轮验证见[任务记录](../specs/001-project-bootstrap/tasks.md)和[结构核对](../docs/validation/001-project-bootstrap/structure.md)。
