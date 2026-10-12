# 排除范围增量契约

0.1规格及测试阶段均已批准进入实施，关联[规格](../spec.md)、[计划](../plan.md)、[状态模型](../data-model.md)与[任务](../tasks.md)。本次接口增量已实施；基础协议仍遵循[原契约](../../002-order-management/contracts/http-api.md)，运行中的旧进程须重启后才使用新源码。

## 创建请求

POST /api/v1/export-tasks仍用原Idempotency-Key和fields。SELECTED不增加字段；FILTERED的scope增加可选excludedOrderIds: string[]，条目为符合原orderId规则的正Long十进制字符串，最多200000项，允许空数组并规范化为缺省；拒绝null、数字项、非数组、无效/越界ID和额外未知字段。重复ID按集合语义去重，但原输入长度先校验，不用去重绕过长度边界。

示例为协议示意，不是实际业务数据或可提交凭据：

```json
{
  "scope": {
    "type": "FILTERED",
    "filter": { "status": "PAID" },
    "excludedOrderIds": ["1", "2"]
  },
  "fields": ["orderNo", "amount"]
}
```

成功响应仍为原创建摘要，scopeType=FILTERED，totalCount为服务端已提交排除后的数量，不返回额外详情接口。排除ID不存在时ORDER_SELECTION_INVALID；空范围EMPTY_EXPORT_SCOPE；真实数量超上限EXPORT_LIMIT_EXCEEDED；结构/长度错误沿用400校验错误，原幂等冲突继续409。接口错误不得打印完整ID集合或客户关键字。

## 兼容与统一API

缺省/空排除复用旧canonical版本与摘要；非空集合使用新版本并排序去重。同键同集合不同顺序可找回，同键改变集合冲突。原QUERY、SELECTED、FILTERED和历史任务需回归。新增OpenAPI属性、TS schema和后端白名单在各自实施阶段同步；业务调用仍只通过frontend/src/shared/api/api.ts的createExportTask，无功能目录API实现或转发。

全量导出计数、关联写入、任务快照恢复和Worker读取必须遵循同一范围，不接受客户端选中计数。原Outbox消息及文件URL契约不变，验证映射见[任务清单](../tasks.md)。
