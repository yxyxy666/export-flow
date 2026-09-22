# 待确认决策

## SSE 生命周期参数

原 SSE 契约草案已随功能规格撤回；当前链路学习说明见 [`DATA-FLOW.md`](../DATA-FLOW.md)，具体契约待重新制定。

- `stream-idle` 发送前的空闲宽限期尚未确定。**建议**：初始值为可配置的 1 秒，使用可控时钟验证；创建/重试导致活动版本变化时取消本次空闲候选并重新计时。
- 异常断线的退避、心跳、SSE 事件 ID 和 REST 回补窗口尚未确定。**建议**：使用受控退避和心跳；若 Redis 只做最新进度缓存，就没有可依赖的 Stream 游标和事件保留窗口，断线时从 MySQL 执行一次快照回补。
- 页面隐藏时是立即关闭 SSE，还是保留短暂宽限后关闭，尚未确定。**建议**：本地学习项目在隐藏时立即关闭，恢复可见时执行一次快照查询，有活跃任务再连接。
- 项目已决定不使用任何固定或低频轮询。SSE 正常关闭后，其他设备或服务端入口新建的任务无法自动唤醒当前页面。**建议**：本期接受该限制，只在进入/恢复页面、恢复联网和手动刷新时执行一次快照查询；若未来要求跨设备即时发现，则必须重新评审永久通知通道或 Web Push。
- 本期没有认证，规格和契约草案因此暂按“当前单一环境内全部未完成任务”编写，该范围语义仍待审批。**建议**：批准此单一环境语义；未来引入认证时通过新 ADR 和契约版本增加用户/租户隔离，不在当前代码中伪造用户边界。

## 后端实现选择

- [`AC-028`](product/acceptance-criteria.md#ac-028) 是当前已确认的默认排序；[`AC-036`](product/acceptance-criteria.md#ac-036) 是待审批的活跃优先方案，二者不能同时实施。**待决策**：是否将默认排序改为活跃优先；若批准，明确由候选标准取代现行标准，保留原编号和定义并同步 PRD、前端规则和测试映射。评审前保持创建时间倒序。

- 用户参考图指定 MyBatis 动态 SQL 与 Flyway Schema 演进，已写入待评审后端规则；此前的 Spring JDBC 建议不再作为候选实施基线。MyBatis/Flyway 的 ADR 评审、MySQL 事务隔离级别、版本和 outbox 并发领取 SQL 尚未确定，须在真实 MySQL 8.4 LTS 上验证。
- Redis 8 的镜像、持久化和资源参数尚未确定。待评审的 [`ADR-0006`](adr/0006-backend-stack-and-progress-cache.md) 拟只保留进度缓存；批准前不要把旧进度 Stream 的持久化与裁剪参数套用到缓存。
- RabbitMQ 的镜像版本、持久化、exchange/queue/routing key、发布确认与正确路由确认、手动消费确认、预取、并发、重投、死信和队列故障后的在途任务对账尚未确定。**建议**：均配置化，并在真实 broker 上验证发布前后及消费确认前后的崩溃窗口。
- outbox dispatcher、MySQL worker 租约和崩溃恢复参数尚未确定。**建议**：dispatcher 使用有界批次和退避；worker 使用有界租约、心跳和尝试次数，超限后写诊断死信并把任务置为失败；具体数值在实现前审批。
- 用户参考图指定 Redis 进度缓存、MySQL 事实源与 SSE 广播；待评审的 [`ADR-0006`](adr/0006-backend-stack-and-progress-cache.md) 拟取代 [`ADR-0004`](adr/0004-redis-streams-and-backend-processing.md) 的进度 Stream。Redis 缓存版本、有效期、容量、重建规则，以及跨实例 SSE 通知、在线漏通知补偿和断线快照回补仍待批准。普通订单/任务列表不默认缓存。
- Spring SSE 技术路径尚未确定。**建议**：保持现有同步 Spring MVC 栈并使用 `SseEmitter`，当前规模没有引入 WebFlux 双栈的收益。
- 相同 `Idempotency-Key` 携带不同载荷时的 HTTP 状态和错误码尚未确定。**建议**：返回 `409 Conflict` 和稳定错误码 `IDEMPOTENCY_KEY_REUSED`，不得返回原任务掩盖调用错误。
- SIGTERM 时正在生成的任务如何结束尚未确定。**建议**：停止领取 RabbitMQ 新任务消息，在有界宽限内完成当前文件；超时中断时删除临时文件，并由租约恢复策略处理任务。

## 测试与验证选择

- 后端测试工具基线尚未由 ADR 确认。**建议**：JUnit Jupiter + AssertJ + Spring Boot Test + MockMvc + MySQL 8.4 LTS/RabbitMQ/Redis 8 Testcontainers；只有选择 WebFlux 时才改用 WebTestClient。
- 静态检查、格式化和覆盖率阈值尚未确定。**建议**：先采用 Spotless、Checkstyle/SpotBugs 和 JaCoCo 收集基线，再单独审批阈值，避免用未经验证的数字阻塞初始化。
- [`SC-001`](product/acceptance-criteria.md#sc-001) 至 [`SC-004`](product/acceptance-criteria.md#sc-004) 所需的本地参考硬件、容器资源、预热和样本数尚未定义。**建议**：固定一套 Docker Compose 资源配置、JVM `-Xmx512m`、至少 5 次预热和 20 次测量，并把环境摘要随报告归档。

已确认的前端技术栈、测试范围、应用架构、本地运行和下载方式见：

- [`ADR-0002 前端技术栈与测试范围`](adr/0002-frontend-stack-and-test-scope.md)
- [`ADR-0003 应用技术栈与本地运行架构`](adr/0003-application-stack-and-local-runtime.md)

后续出现新的技术、产品或工程歧义时，先在此记录，不得在实现中隐式决定；确认后按事项性质更新产品需求、ADR 或对应规则，并从本文件移除。
