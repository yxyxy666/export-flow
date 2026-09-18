# 后端规则

后端使用 Java 21、Spring Boot 3、PostgreSQL 和 Apache POI `SXSSF`。异步任务由数据库表持久化并由应用内定时 worker 领取执行；本期不引入 Redis 或独立消息队列。生成文件写入本地挂载目录。完整决策见 [`ADR-0003`](../adr/0003-application-stack-and-local-runtime.md)。

## 分层

Transport/API 层负责协议校验和鉴权；Application 层编排用例；Domain 层承载业务不变量；Infrastructure 层负责数据库、队列和外部服务。依赖只能由外向内。

本期为本地学习项目，不启用认证授权；上述鉴权职责仅在未来明确引入认证时适用。接口时间统一使用 UTC ISO 8601 字符串（带 `Z`）；需要生成人类可读的页面、Excel 或文件名时间时统一转换为 `Asia/Hong_Kong`，与 [`frontend.md`](frontend.md) 保持一致。

## 接口与可靠性

输入必须白名单校验，输出使用稳定契约；错误统一包含可检索 code，不返回敏感实现细节。写操作定义幂等键、事务边界、超时、重试和幂等行为。

接口使用 JSON REST 和 `/api/v1` 路径前缀，以 OpenAPI 作为机器可读契约。下载接口 `GET /api/v1/export-tasks/{taskId}/file` 直接返回文件流，设置正确的 `Content-Type`、长度（可得时）和安全的 `Content-Disposition`，不返回签名 URL。

## 数据

Schema 变更必须有迁移、回滚/前滚策略和兼容窗口；日志默认脱敏，密钥只来自受控配置，不进入仓库。

本期合成订单初始化后只读。订单号使用精确匹配；时间查询精确到秒且包含起止边界，金额范围包含上下限。首版币种仅 `CNY`，金额使用保留两位小数的十进制定点值，不使用二进制浮点承担业务金额计算。

订单列表按下单时间倒序、再按订单唯一 ID 倒序；导出任务按创建时间倒序、再按任务唯一 ID 倒序。导出任务查询支持全部、`PENDING`、`RUNNING`、`SUCCEEDED`、`FAILED` 状态和 20、50、100 的服务端分页。
