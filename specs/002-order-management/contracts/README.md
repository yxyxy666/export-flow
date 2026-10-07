# 契约目录

**状态**：随 [spec](../spec.md)、[plan](../plan.md)、[tasks](../tasks.md) 评审，规格整体未批准；技术/产品决策已确认，见[决策记录](../decision-record.md)。

- [HTTP 与前端状态](http-api.md)：订单查询、创建、摘要查询、错误与冻结提交。
- [任务命令](export-command.md)：RabbitMQ 载荷、投递确认、重复与未知消息。
- [Worker 与真实 Excel](worker-boundary.md)：应用编排、执行权、异常恢复、进度与确认边界；详细设计见[Excel方案](../excel-design.md)。
- [OpenAPI](openapi.yaml)：本期草稿机器可读 HTTP 设计；业务约束和结果不明语义同时遵循 HTTP 文档。

类型的唯一设计清单在 [data-model.md](../data-model.md)；验收编号及定义仅在[验收基线](../../../docs/product/acceptance-criteria.md)。本版本包含同源下载；SSE、失败任务重试和用户暂停／恢复不属于本契约版本。
