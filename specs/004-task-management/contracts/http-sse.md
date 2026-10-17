# HTTP与SSE契约草稿

**版本**：0.3　**状态**：规格0.3及测试阶段已于2026-10-17获负责人批准，当前生产实施；保留测试先行证据。
关联：[规格](../spec.md)、[计划](../plan.md)、[任务](../tasks.md)、[模型](../data-model.md)、[暂停协议](execution-control.md)。


规格0.3由负责人明确批准；测试阶段及六类缺口处理方案随后获批，生产实施已授权，见[审批记录](../tasks.md#审批记录)。

## HTTP端点

| 方法与路径 | 输入 | 输出 |
| --- | --- | --- |
| GET /api/v1/export-tasks | TaskQuery查询参数 | 200 TaskPage，no-store；MySQL权威分页与全环境活动信息 |
| GET /api/v1/export-tasks/events | 不接受分页/筛选；同源EventSource | 200 text/event-stream；no-cache,no-transform；关闭代理缓冲 |
| POST /api/v1/export-tasks/{taskId}/pause | operationId、expectedControlVersion；UUID及非负十进制字符串 | 200 PauseResult，提交后的PAUSING或PAUSED；同操作重放仍200 |
| GET /api/v1/export-tasks/{taskId}/file | 复用已有taskId路径 | 仅权威成功文件可下载，路径与响应头安全不变 |
| POST /api/v1/export-tasks | 既有创建，非本期新增界面 | 响应字段及留页行为不变，仅同事务增加通知 |

不新增详情、继续、修改、删除或失败重试接口。TaskPage/TaskListItem/PauseRequest/PauseResult逐字段见[模型](../data-model.md#类型与字段)，机器可读结构见[OpenAPI](openapi.yaml)；创建和下载引用原契约，不复制成新接口。

查询支持keyword/status/page/pageSize，固定createdAt/taskId倒序；非法页码、枚举、UUID、控制版本或未知字段400 VALIDATION_ERROR。不存在404 TASK_NOT_FOUND；状态409 TASK_ACTION_NOT_ALLOWED；控制版本409 TASK_CONTROL_CONFLICT；同operationId不同任务/载荷409 OPERATION_ID_REUSED。依赖不可读503 STATE_UNAVAILABLE且outcome=UNKNOWN。沿用code/message/traceId/outcome安全错误，不泄露内部路径。

pause回执先查同操作结果，再核对当前控制版本；操作已确认后任务后续发生变化不重新执行。200只返回APPLIED/REPLAY并附当前权威task；持久resultJson保留原受理结果供核对，重放响应另读当前摘要，不能把过期PAUSING覆盖已见PAUSED。状态/版本/操作冲突通过409安全错误体outcome=REJECTED表达，不返回200 CONFLICT。依赖不可读或提交不明503 outcome=UNKNOWN，不能凭找不到回执断言原操作未提交。连接中断保留原ID、目标及控制版本，同pause接口重发找回；不新增操作详情查询。

## SSE事件与衔接

| event | data | 行为 |
| --- | --- | --- |
| stream-ready | connectionId、activityVersion | 订阅已注册，前端补一次当前查询快照；期间事件缓冲再按版本合并 |
| task-updated | activityVersion、task:TaskListItem | 旧/重复版本丢弃；纯进度直接合并，成员变化合并一次当前页查询 |
| stream-idle | connectionId、activityVersion | 当前连接且未见更高活动版本才关闭；立即一次最终快照校准，再按结果决定是否连接 |
| 心跳注释 | 无业务进度 | 保留ADR的15秒，不验证时间间隔 |

事件id表达connectionId/activityVersion/taskId/version，ready/idle无task部分，不承诺回放；Last-Event-ID不能跳过快照。开流先本地注册与缓冲，再ready；所有连接发送串行，不持DB事务。前端先初始快照决定是否开流，ready后桥接一次快照避免订阅间隙。

HTTP在途收到更新事件时记录seenVersions与membershipDirty；响应按更高任务版本合并，dirty在在途查询结束后补一次权威查询。旧queryGeneration响应忽略。非成员变化事件不会形成循环查询，持续纯进度不追加HTTP；旧响应若含已被最新事件筛除的条目不得短暂复活。

## 按需连接与空闲裁决

页面可见、在线且全环境有PENDING/RUNNING/PAUSING时开一条通道；PAUSED不保持活跃。隐藏、离开和离线立即关闭。初次进入、可见恢复、联网恢复、手动刷新和本浏览器任务创建通知执行快照再决定连接。

异常断线先close原EventSource并解绑回调，再沿用1至30秒全抖动退避；每次重连先快照，使用连接generation丢弃旧回调，避免原生自动重连和自定义退避同时开流。正常idle不自动重连，不注册固定或低频轮询。所有计时策略保留设计，但本轮不执行间隔、宽限或退避测试。

空闲候选读activityVersion，宽限后在activity锁下核对版本未变且无全环境活跃任务，再将本连接转换为idle。更高活动版本或待发送变更取消idle。创建严格发生在idle裁决之后属于已有空闲限制，本浏览器通知可唤醒；其他设备不自动唤醒。

终态/暂停提交后也发送状态事件，不能只在生成中推百分比。当前筛选无活跃项但其他页有活跃时仍订阅；列表刷新保页且SSE更新不改变固定排序。

空闲裁决时通知dispatcher可能尚未广播最后的PAUSED/终态，不能以“本地队列没有事件”证明所有状态已送达。客户端接受idle后关闭旧连接并立即查询一次当前页权威快照，复用在途查询合并机制；最终快照失败保留旧值并提示未更新，不显示已同步。快照若显示更新活动则重新连接，否则保持空闲。此为idle事件的一次补偿，不是正常idle自动重连或周期轮询。创建发生在此最后快照之后仍沿用其他设备无法自动唤醒的限制；初次无活跃快照后的创建也适用同一限制。

SseEmitter使用显式无请求寿命上限的配置（timeout=0），关闭由完成、异常、页面生命周期及idle管理；代理需允许text/event-stream并关闭响应缓冲。只设计连接运行参数，不执行超时验证。坏JSON/无效版本/非法事件结构关闭当前连接、显示协议错误并允许手动刷新，不以无穷自动重试掩盖契约错误。

## 查询和展示边界

keyword trim后最多100个Unicode码点，空值按不筛选；按任务编号或展示文件名作字面子串匹配，再AND状态。SQL绑定参数，显式ESCAPE字符，转义该字符及%和_；排序规则沿用任务表utf8mb4_0900_as_ci（编号列原为utf8mb4_bin，搜索表达式显式使用表排序规则），不新增未经决定的大小写语义。展示文件名由已持久受理时间按香港时区和任务编号生成，成功时核对并采用真实元数据；非成功任务file_name可空也必须可显示／搜索同一名称，不冒充文件存在。不得逐行查询订单或读取原客户数据。

page取1..2147483647整数，pageSize只20/50/100；乘法用long校验，不接受小数、溢出、重复参数或未知查询字段。HTTP传输UUID与版本使用明确字符串；版本限制为Java非负long，fileSize与total保持精度，total协议也用非负十进制字符串（任务总数没有200000上限）。只有单任务计数最大200000可用number。UTC时间带Z，显示香港时间；非成功百分比最多99，成功100，按最近已提交可信计数计算；修复校验后的新版本允许进度下降，历史高水位不参与显示。

服务端只生成六态合法数据；前端接收状态字段使用string并派生已知状态集合，未知值显示安全“未知状态”且动作全部禁用，不映射为失败／已完成。其他结构或版本不合法仍归协议错误，不接受整条任意数据。TaskPage的schema约束与前端容错分别声明；状态查询枚举仍严格六态。

展示名称搜索表达式在SQL内使用受理UTC时间的固定香港偏移转换（CONVERT_TZ的+00:00→+08:00，无需部署MySQL命名时区表）及DATE_FORMAT，并拼接taskNo；返回名称由Java Asia/Hong_Kong格式器生成，使用相同创建时间精度。当前系统创建的任务均在香港固定+08:00时期；成功任务使用已登记file_name。参数化LIKE匹配的名称必须与响应一致，TC-401覆盖非成功名称与Unicode/%/_/转义字符。

## 装配修复摘要

同一TaskListItem增加executionPhase、repairState、repairEpoch、repairAttempt、repairReasonCode，所有字段必传，可空原因显式null；类型见[模型](../data-model.md#选择性重建模型)及[OpenAPI](openapi.yaml)。processedCount来源改为持久trusted_processed_count。修复不新增HTTP执行端点或任务状态，RUNNING仍可暂停。

新版本计数可小于旧版本，前端不得以max合并；进度／阶段／提示的变更均同事务版本化并通知，Redis仅按version比较。仅修复摘要变化不是筛选成员变化，不触发列表HTTP查询。SSE只发数据库最近提交，不每次读盘核验；权威读取不表示磁盘持续无损。

可恢复异常用repairReasonCode表达，errorCode/errorSummary留给最终失败；安全原因含DATA_BLOCK_MISSING、DATA_BLOCK_CORRUPT、DATA_BLOCK_RECORD_MISSING、WORKBOOK_CORRUPT。终态错误含DATA_REPAIR_UNRECOVERABLE、DATA_REPAIR_SOURCE_CHANGED、DATA_REPAIR_EXHAUSTED、DATA_REPAIR_IO_ERROR。不得把损坏原因原文或物理路径发给页面。提示映射、停止和成功清理见[修复契约](assembly-repair.md#进度与黄色提示)。
