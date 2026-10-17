# 继续契约及数据生命周期

## HTTP与状态

机器可读输入输出见[OpenAPI](contracts/openapi.yaml)。

`POST /api/v1/export-tasks/{taskId}/resume`，JSON仅含`operationId: UUID字符串`、`expectedControlVersion: 非负long十进制字符串`；上限4096字节，未知／重复／溢出字段返回400。返回`{outcome: APPLIED|REPLAY, task: TaskDto}`，与列表共用摘要，`Cache-Control: no-store`。不存在404；状态／版本／操作复用冲突409；数据库结果不明503、outcome UNKNOWN。仅已提交PAUSED可转PENDING。

锁序activity→task→command→execution→checkpoint；继续事务重开原命令、写回执、更新控制与摘要版本及进度Outbox。暂停回执保持历史兼容，新增继续回执；跨动作检查操作标识。清旧lease token使迟到投递confirm失效；原eventId/schema/type不变，attemptNo不清零。重新认领已有execution更新owner/fence/控制版本，不重复插入，不清零recovery_count。暂停及终态封存语义的本次扩展由[ADR](../../docs/adr/0012-user-resume-and-live-progress.md)定义。

## 文件及恢复

worker收集服务按源区间创建不可变数据块，位于既有file-root的任务/代目录；块登记与分段独立内容基线及断点事务提交后才计入进度。用户暂停保护引用继续保留。继续不扫描目录提升进度；只读登记指向的块并读至EOF验证。多余未登记文件不计可信量，沿用既有垃圾清理策略，不主动删除受保护文件。

未封存恢复以断点verifiedPrefixCount及cursor为边界，分段连续链、行数和末游标须吻合，不能以task.totalCount要求部分收集已完成；封存恢复还要求全量清单digest。缺文件或缺块登记但独立区间和checksum存在时只重读该段，内容吻合才CAS替换选中块。两种记录皆缺失且已有进度时DATA_REPAIR_UNRECOVERABLE。数据库不可读保留不明结果，访问拒绝明确I/O错误，不伪装损坏。

健康行数事务提交为可信进度；重建轮次、替换选择版本和摘要版本持久保存。修复中再次暂停保留计划；继续不重置3轮预算。部分修复完继续收集尾部，完整修复完重新装配。SXSSF工作文件由新工作簿创建、关闭后清理；它没有可恢复登记，不在旧工作文件上续写。最终候选需全文件校验、READY登记、采用事务后SUCCEEDED/100%。

## SSE及缓存

保持既有stream-ready/task-updated/stream-idle结构，allowedActions新增RESUME。启用Spring调度，自动处理进度Outbox、心跳、空闲与暂停对账，不改变现成调度周期。MySQL为权威，Redis按版本缓存，MQ通知taskId/version；新版本可降低进度，旧版本不可覆盖。测试必须从真实生产入口经过自动发布、MQ及真实HTTP SSE，不能手动dispatch代替证据。
