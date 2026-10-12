# 复用负责人提供的订单数据

2026-10-12，负责人明确要求核验并删除数据库造数内容，全部订单数据使用已提供容器。该要求替代原规格的合成订单初始化方案，沿用既有开发授权；不修改负责人原始订单、表结构、Flyway历史或服务配置。

## 来源与适配

只读核验`project-export-flow-mysql-1`内`exportflow.orders`已有110000行、ID范围1至110000；状态为PENDING/PAID，渠道为APP/STORE/PARTNER/WEB，时间范围2026-01-01至2026-01-02，币种CNY。没有200000或200001行的数据，不自行补齐。

本应用的订单持久化适配使用本项目schema内`orders`视图直接读取`exportflow.orders`，不复制数据。映射：`total_amount→amount`、`sales_channel→channel`、`shipping_province→region`、`COALESCE(order_status,status)→status`；其余字段保持原值，时间按UTC连接会话读取。SQL SECURITY INVOKER使用调用者权限；应用和测试账号对来源只获SELECT权限，迁移账号另获REFERENCES以建立所选订单外键，不获写入原始订单的权限。业务任务、Outbox、执行权和产物表仍由本应用迁移管理。

删除SeedOrders.java、造数脚本及说明、orders.sql合成夹具和初始化工具专属测试；删除两个POM的工具源目录注册。演示、查询、创建、Excel及性能均读上述来源。测试按实际需要以只读视图限定既有ID范围，不插入、更新或删除订单；故障数据通过端口故障注入验证，不修改来源。原造数结果只作为历史记录，不能作为本次复用数据的通过证据。

## 验证与限制

新增ProvidedOrdersTest先验证旧实现不能读取来源，然后验证视图类型、来源行数、字段映射及写权限拒绝；来源行数与校验和在回归前后核对。既有查询测试改为使用真实来源值，导出内容逐行与同一来源核对。200000行受理和200000/200001行数据量性能缺口明确记录，禁止通过自己造数补齐或降低验收阈值。参数、类型和恶意输入测试可使用测试输入值，但不持久化为订单。

本项目私有任务schema/vhost仍用于隔离新增任务及故障测试，不创建容器；不共享示例应用的任务表。迁移V1尚未交付生产，替换为复用视图迁移；此前测试schema均已回收。已有正式应用schema若包含旧V1历史须单独评审兼容迁移，不能直接repair或清表。

相关设计：[计划](plan.md)、[业务数据模型](data-model.md)、[决策](decision-record.md)、[任务及审批](tasks.md)、[实际共享环境](../../docs/validation/002-order-management/shared-services.md)。
