# 统一删除 HTTP 契约（草稿）

## 请求

`DELETE /api/v1/export-tasks/{taskId}`

请求体：

```json
{
  "operationId": "UUID",
  "expectedVersion": "十进制字符串"
}
```

接口要求严格 JSON：拒绝未知字段、重复字段、尾随内容、超过边界的请求体和非法UUID。`operationId`由前端一次确认生成，找回结果时必须复用；`expectedVersion`来自当前任务摘要，仅用于并发裁决，不代替服务端读取权威状态。

## 成功响应

首次受理统一返回`202 Accepted`，即使终态任务可以很快完成，也不让客户端把数据库提交和物理文件删除混为一个同步事务：

```json
{
  "outcome": "ACCEPTED",
  "operationId": "UUID",
  "taskId": "UUID",
  "mode": "ACTIVE_STOP | TERMINAL_CLEANUP",
  "state": "STOPPING | CLEANUP_PENDING",
  "task": {
    "taskId": "UUID",
    "deletionState": "REQUESTED | STOPPING | CLEANUP_PENDING | DELETED",
    "version": "十进制字符串"
  }
}
```

重复同一操作返回`200 OK`和`outcome=REPLAY`，携带同一操作的当前权威状态。清理完成后可通过任务列表刷新确认任务不可见；不把物理删除完成时延作为接口成功条件。

## 错误

| HTTP | 错误码 | 语义 |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | 请求结构、UUID或版本非法 |
| 404 | `TASK_NOT_FOUND` | 不存在或已完成墓碑且不是同一操作找回 |
| 409 | `TASK_VERSION_CONFLICT` | 列表版本已过期，要求刷新后重试 |
| 409 | `OPERATION_ID_REUSED` | 同一操作ID携带不同任务或请求摘要 |
| 409 | `TASK_DELETE_NOT_ALLOWED` | 状态或删除生命周期不允许该请求 |
| 503 | `STATE_UNAVAILABLE` | 数据库、消息或权威状态暂不可用，不能推断删除结果 |

错误响应沿用项目统一错误结构；不返回文件路径、Worker身份、SQL错误和客户载荷。

## 服务端分流

服务端在同一短事务内锁定任务活动元数据和任务行，先检查已有删除操作，再读取当前`status`与`deletion_state`：

1. `PENDING/RUNNING/PAUSING/PAUSED`且`deletion_state=NONE`：写入`ACTIVE_STOP`删除操作、递增控制/摘要版本、设置删除状态、关闭可关闭的原投递权，创建停止等待作业。
2. `SUCCEEDED/FAILED`且`deletion_state=NONE`：写入`TERMINAL_CLEANUP`删除操作、设置清理待处理状态，创建清理作业。
3. 已存在同操作ID：按请求摘要返回回放。
4. 已存在其他删除操作：返回当前处理中回执或稳定冲突，不创建第二个作业。
5. 任务根记录已删除但独立技术墓碑仍为`ACTIVE`：只有同操作ID可以回放；其他操作返回`TASK_NOT_FOUND`。

运行中路径不得在用户请求事务内等待Worker、RabbitMQ或文件删除；终态路径也必须通过清理作业完成物理文件清理。
