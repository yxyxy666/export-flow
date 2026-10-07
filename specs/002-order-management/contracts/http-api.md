# HTTP 与前端状态契约（草稿 v1）

关联 [OpenAPI](openapi.yaml)、[模型](../data-model.md)、[函数](../function-design.md)、[任务](../tasks.md)。接口参数已定稿，契约随整体规格审批后实施；不得把学习稿接口视为已实现。所有普通 JSON 响应 `Cache-Control: no-store`，错误安全化，不在访问日志记录查询内容。

## 订单查询

`GET /api/v1/orders`：query 为 keyword、orderNo、status、channel、createdFrom、createdTo、minAmount、maxAmount、page、pageSize、sortDirection。省略项遵循模型默认，未知参数、重复单值参数、未知枚举、金额精度错误、非 UTC `Z` 时间、页码非法返回 400 `VALIDATION_ERROR`；值必须参数化。时间允许单边，精确到秒闭区间。订单号 trim 后二进制精确匹配；keyword 对三字段做 literal 子串 OR，其余条件 AND，不把 SQL 通配符当业务通配符；keyword 不影响订单号精确匹配语义。

返回 200 `PageResult<OrderDto>`。列表 time DESC/id DESC 为默认；可选 ASC 同时切换两列。空页正常返回 items=[] 和权威 total。count与分页读在短只读事务中完成，初始化后只读前提下无需长快照事务。后端每页最多 100，不能用本接口加载整个导出范围。

## 创建

`POST /api/v1/export-tasks`，必填 `Idempotency-Key`，JSON `CreateExportRequest`。SELECTED 与 FILTERED 为判别联合，不允许混合、客户端 total 或任务状态；fields 保持顺序。前端标准选择按页面九列顺序序列化，API 有序字段支持调用者明确选择，后端不擅自重排。字段默认／最少一项及本期文件边界见[已确认基线](../../../docs/product/acceptance-criteria.md#订单管理分阶段交付候选)。

| HTTP | code／结果 | 前后端处理 |
| --- | --- | --- |
| 201 | 首次 `TaskSummary`，outcome 不需要出现在成功体 | 已确认创建事务提交；Location 指向摘要端点；HTTP 不等待 MQ |
| 200 | 同键同请求重放 `TaskSummary` | 当前摘要可能已 FAILED，不强行回 PENDING；不新增 Outbox |
| 400 | VALIDATION_ERROR、EMPTY_EXPORT_SCOPE、INVALID_EXPORT_FIELDS | 明确拒绝，outcome=REJECTED，无任务 |
| 409 | IDEMPOTENCY_KEY_REUSED | 同键不同摘要，明确拒绝；不能返回旧任务掩盖冲突 |
| 413 | REQUEST_TOO_LARGE | UTF8 JSON文档超过16MiB，反序列化阶段明确拒绝，outcome=REJECTED |
| 422 | EXPORT_LIMIT_EXCEEDED、ORDER_SELECTION_INVALID | details 含 currentCount/limit；不存在选择拒绝整笔；不静默截断 |
| 503 | CREATE_OUTCOME_UNKNOWN | 无法确定事务提交，outcome=UNKNOWN；原键原请求核对 |
| 503 | DEPENDENCY_UNAVAILABLE | 仅明确未提交／已回滚才 outcome=REJECTED；否则 UNKNOWN |
| 500 | INTERNAL_ERROR | 不能证明回滚则 UNKNOWN；安全文案与 traceId，无原始异常 |

事务唯一约束冲突后必须结束／回滚当前事务，再以新事务回读已提交幂等登记。提交异常时先新连接回读同键摘要；核对已提交才返回成功，确认同键冲突才拒绝；DB 不可读／未能判定返回 UNKNOWN，不把异常名称当回滚证明。即使 503 的 UNKNOWN 响应丢失，前端仍以原键重试。新建任务号采用 `EXP{香港日期}-{UUID去横线大写}`，完整UUID作为唯一性设计，DB unique再裁决；人类文件名按PRD及本期Excel方案固定。

## 权威回执

`GET /api/v1/export-tasks/{taskId}`，返回 200 `TaskSummary`，不存在 404 `TASK_NOT_FOUND`。本期提供单任务摘要及文件下载；不实现完整任务列表、失败重试或SSE。任务管理路由 `/export-tasks?taskId={id}` 展示回执、实际进度/错误、成功文件元数据、下载、单次手动刷新及返回订单管理；直接访问无 taskId 时仍可展示页面标题与阶段说明。创建成功后跳转此路由，保持统一菜单名称。

## URL、编辑表单和请求竞争

- 前端 URL 仅存 orderNo、status、channel、时间、金额、page、pageSize、sortDirection。keyword 可包含客户资料，**不能写入 URL、localStorage 或日志**；已提交 keyword 仅内存。URL 中 keyword 参数主动移除，不发后端，显示安全的“关键字需重新输入”提示。刷新与前进后退只恢复非敏感条件，关键字清空，选择及未提交配置清空；该取舍见待确认决策。
- 非法 URL 的非敏感项恢复默认并 replace 规范化 URL，显示可理解提示；API 仍独立严格拒绝非法参数。浏览器后退触发当前请求取消并更新 form 默认值，不保留不对应结果的选择。
- 首次加载及历史 POP 导航按URL重建并清内存关键字；页面自身的push/replace动作保留本次内存keyword，不能在每次分页写URL后立即清掉它。SUBMITTING/UNKNOWN时冻结筛选、分页、排序、选择和字段修改，防止找回旧意图时错误清空另一组新选择；显式重置由页面协调各Hook，不引入跨Hook隐蔽写入。
- TanStack Query key 包含已提交 filter（含内存 keyword）、页码、页大小和排序；HTTP 传 AbortSignal，切换条件不把旧结果当新范围可导出。普通后台刷新可保留旧结果，但旧请求结束不能覆盖新 key。创建没有自动随机换键重试。
- 进入、查询、翻页、排序、重置、手动刷新各发需要的一次请求；不注册定时轮询。筛选提交验证失败不更新 URL、不清选择、不请求后端。

## 冻结提交状态机

`EDITING -> SUBMITTING -> CONFIRMED | REJECTED | UNKNOWN`。confirm 前固定 scope、fields 和 key；SUBMITTING/UNKNOWN 时禁止更改这份提交、取消其结果归属或发第二份新键请求。UNKNOWN 可点击“重试确认”走同一 payload/key；可离开页面，但要明确“结果尚未确认，重新进入后可在后续完整任务功能中核对”，本期不声称跨刷新自动找回。离开不能撤销已受理任务。

明确拒绝保留字段和选择，返回编辑态，纠正后视作新意图使用新键。确认成功清选择、失效相应任务快照缓存并跳转；返回错误不能清选择。空筛选结果、未选择、加载中、失效结果必须在按钮旁给出文字原因，不能只靠 hover。新打开的对话框默认九列；明确失败重开同一未完成配置保留字段；明确取消后下次全新配置恢复默认。

无服务端幂等任务取消接口，关闭请求不是撤销事务。前端请求取消或无响应一律按 UNKNOWN 保留本次意图，不能把 AbortError 直接展示为“创建失败”。

## 同源文件下载

`GET /api/v1/export-tasks/{taskId}/file`返回成功xlsx文件流，MIME为`application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`，Content-Length来自实际文件，Content-Disposition包含安全ASCII fallback及UTF-8 filename*。任务ID仅用于查MySQL已提交的成功产物引用，不允许客户端提供文件名或物理路径。不存在任务404 TASK_NOT_FOUND；非成功409 TASK_NOT_DOWNLOADABLE；成功引用文件缺失410 EXPORT_FILE_MISSING并告警；DB不可读503；权限或其他确定IO错误安全500。流响应开始后客户端断开只释放文件句柄，不回滚任务或返回伪造JSON。下载流不持有数据库事务/执行租约。

前端仅成功回执渲染同源anchor，其他状态禁用并显示原因；不fetch Blob，不在JS内存保存文件，浏览器下载错误受直接链接限制，以手工真实浏览器验证。文件与任务永久保留，主动删除与重新生成成功文件不在本期。详细身份与安全边界见[Excel方案](../excel-design.md#文件身份就绪登记及下载)。

## 已定协议参数

查询/创建客户端超时均10000ms；HTTPUTF8 JSON请求体最大16MiB，200000个合法Long ID必须在上限内；超字节预算在进入创建事务前返回413 REQUEST_TOO_LARGE、outcome=REJECTED，不留下任务。命令16KiB上限由消息契约负责。参数由[决策记录](../decision-record.md)定稿，幂等摘要/URL恢复方案已确认；实际契约仍随整体规格审批后才实现。
