# 实施进度与阶段证据

> 2026-10-12最终状态：负责人确认本轮剩余验证无需执行，性能已取消；原“待验／待补／未验证”为历史事实，不再是本轮待办。范围和日期见[免验确认](../../../specs/002-order-management/tasks.md#本轮免验确认)，免验不标实测通过。

> 2026-10-12状态更新：下文316项等通过统计属于旧数据方案的历史证据；当前已删除订单造数，直接读取负责人容器中的110000条订单。当前复验及200000规模数据缺口以[来源记录](provided-data.md)为准，不沿用历史通过结论。

日期：2026-10-11。归属[规格任务](../../../specs/002-order-management/tasks.md#测试进度与证据)，沿用已记录的两次审批和开发授权。前端153项与类型／构建、后端按类汇总316项回归及正式质量门禁已通过；[逐套件结果](green-results.md)有实际报告来源。[统一代码review](code-review.md)修复已取得回归，独立性能和手工证据继续，整个规格尚未验收。以下保留原执行批次，当前状态以本段和文末为准。

## 前端

订单查询、URL恢复、取消、选择、字段配置、固定幂等提交及创建反馈已实现。业务API只在[api.ts](../../../frontend/src/shared/api/api.ts)，删除两个功能API转发/占位及无用任务表格、查询Hook。任务管理仍为本期要求的文字占位。

- `npx --yes pnpm@10.34.6 --dir frontend test:run`：`artifacts/002-order-management/frontend-implementation-09.log`，9个文件、150个测试通过，退出0。
- `npx --yes pnpm@10.34.6 --dir frontend typecheck`：`frontend-typecheck-02.log`，退出0。
- `npx --yes pnpm@10.34.6 --dir frontend build`：`frontend-build-01.log`，退出0；单入口体积提示待统一review评估。

取消测试观察真实fetch收到的AbortSignal，不用MSW从复用socket重建的handler signal判定浏览器是否取消；仍验证迟到响应不覆盖页面。TanStack Query直接使用框架signal。复杂AntD/jsdom页面套件串行运行，避免多worker争用CPU造成15秒交互超时。对话框焦点ref稳定，不随字段变化抢焦点。

关联[页面质量](../../product/acceptance-criteria.md#sc-009)的自动化部分已通过；[视觉](../../product/acceptance-criteria.md#sc-008)尚待目标浏览器证据。

## 后端

正式Spring Boot/MyBatis/Flyway、订单查询/创建幂等事务、Outbox/RabbitMQ投递、执行权、批读/块断点、SXSSF、产物登记、下载和数据库许可清理已写入生产目录。当前继续补运行治理并恢复全部真实用例，不把“源码已写”当验收完成。

| 执行批次 | 实际结果 | 证据与后续 |
| --- | --- | --- |
| 创建事务诊断 | MyBatis数组绑定名错误先复现后修复；直接真实提交通过 | `backend-create-diagnostic.log`红灯；`backend-create-diagnostic-02.log`绿灯 |
| 创建输入 | 四个输入/关联/并发场景通过；创建回执未开放详情GET返回500的问题已修，待专项复验 | `backend-create-02.log` |
| 投递 | 首轮6条中5条通过，TTL有效策略需直接查询policy已修 | `backend-dispatch-01.log` |
| 执行与策略 | 5条通过：TTL拒绝、确认消息、执行租约和接管预算 | `backend-execution-01.log` |
| 工作簿关闭 | POI5.5关闭自带dispose，重复清理误判失败；新增回归先失败，调整首次dispose检查及全部资源关闭后通过 | `backend-workbook-red.log`；`backend-file-02.log` |
| 文件小范围复验 | 11条中9条通过，2条装配错误：日期类型假设及触发器DDL权限。真实批读/文件事务隔离、生成结果三分类及晚ACK通过 | `backend-file-02.log`；装配已修 |
| 文件扩大复验 | 70条中68通过、2条报错：锁监测账号权限及压缩sheet尚未结束的EOF；已分别修复并专项通过 | `backend-files-03.log`；锁竞态绿灯见`backend-runtime-green-01.log`；批次刷盘绿灯见`backend-flush-runtime-green-02.log` |
| 健康端点 | 原404已复现；增加Actuator后健康端点和隐藏配置端点用例通过 | `backend-health-red.log`；`backend-event-red-health-green.log` |
| 命令身份 | 跨任务eventId错误命令先复现误ACK；增加原Outbox身份核对后通过 | `backend-event-red-health-green.log`；`backend-runtime-green-01.log` |
| Hikari/刷盘/内容 | 4条通过：真正应用连接池READ COMMITTED、SYNC_FLUSH的批次可解码数据及最终文件内容 | `backend-flush-runtime-green-02.log` |
| 脚本实际入口 | Windows PowerShell5.1编码错误先复现后修复；Help、非法参数非零和Wrapper真实初始化110000条通过 | 两脚本Help红绿日志；`seed-script-invalid.log`、`seed-script-real-01.log` |
| 独立运行演示 | 真实MySQL/RabbitMQ、正常调度/消费者，110000条查询与22009行九列创建/生成/HTTP200下载；全部行数/列数及SHA核对 | `browser-demo-03.log`、`demo-http.json`、`demo-workbook-check.json`；这是后端/脚本证据，不是浏览器证据 |
| Review专项 | 7条通过：恢复前缀失权、初始化关闭异常、告警限频/恢复及创建事务4场景 | `backend-review-green-01.log`；红灯分别见`backend-prefix-guard-red.log`与`backend-seed-close-red.log` |
| 静态质量 | Spotless、Checkstyle10.26.1和SpotBugs通过，高优先级BugInstance=0；原9.3语法内部错误保留 | `backend-quality-01.log`、`backend-quality-02.log`；仍须最终源码/正式verify复核 |
| 完整Wrapper回归 | 已按负责人要求人为中断，不能标整批通过；已发现管理用户名、相关confirm预算、trigger权限三处测试装配错误，均已修复并专项通过 | `backend-verify-01.log`保留原结果；`backend-management-denied-green.log`、`backend-correlation-green-02.log`、`backend-review-green-01.log` |
| 连续断点/资源Review | 4项通过：锁定前缀计数、块读取主异常、失权guard和真实生成边界 | `backend-review-red-02.log`；`backend-review-green-02.log` |
| 消费通道Review | 先停消费后归还坏消息、失权后晚登记回调两项红绿通过；真实停槽4场景复验通过，其他执行窗口继续 | `backend-delivery-isolation-red.log`、`backend-delivery-isolation-green.log`、`backend-worker-review-green.log` |
| 创建参与者故障 | 关联/幂等trigger仅由隔离容器root装配；实际业务仍普通账号；两项回归通过 | `backend-participant-trigger-green.log`；旧权限失败保留在完整回归 |
| 缺失成功文件 | 完整回归发现410错误码不符定稿；修为EXPORT_FILE_MISSING并安全告警，待最新代码复验 | `backend-verify-01.log`；完整成功事实和产物不变 |
| 前端Review门禁 | 旧结果选择回归已通过；完整回归旧复杂测试受15秒CPU预算影响，已拆分独立行为并保留断言；最新153/153、类型/构建通过 | `frontend-selection-red.log`、`frontend-review-green-01.log`、`frontend-review-green-02.log`、`frontend-review-green-03.log`；typecheck/build日志 |
| 文档/源码互链 | 218文件2162链接及规格44条验收映射无缺失；不代替行为与SC通过 | `document-review-check.json`，后续文档修改须复核 |

日志均为本地忽略产物，包含合成测试环境。命令及隔离服务版本沿用[环境记录](environment.md)；测试harness执行`mvn -f tests/backend/pom.xml -Dtest=<范围> test`，正式工程专项使用`mvn -f backend/pom.xml -Dit.test=<范围> test-compile failsafe:integration-test failsafe:verify`。历史扩大批次采用独立容器；负责人要求复用服务后已中断相关批次、清理额外容器。当前按[共享服务方案](shared-services.md)串行回归；性能另行串行执行，不使用回归耗时判定性能。

## 未完成验证

独立API与五次Excel性能、共享broker进程重启持久性、目标浏览器手工用例仍待完成。真实110000/200000九列内容、独立512MiB无OOM、kill恢复和停止窗口、正式质量门禁及JaCoCo HTML/XML已取得证据，见[汇总](green-results.md)。Windows UI工具因无法可靠识别当前浏览器URL中止，未继续UI操作，不将Chrome/Edge检查标通过。新增运行计数/告警/清理周期及设计补充见[实施说明](../../../specs/002-order-management/implementation-notes.md)，不覆盖历史红灯。

### 共享服务修订批次

前端153/153通过。严格schema/vhost名称、Spring停机guard与停用消费者无回调等待回归已取得红灯并修复；`backend-shared-unit-green-02.log`4/4通过。`backend-shared-environment-red-02.log`4环境通过、1队列ACK参数预期失败；参数修复后真实策略／发布／能力／权限24项及查询／创建／环境27项均通过。SeedOrders动态schema原拒绝已在真实MySQL复现3项失败，修订白名单后真实复验通过。前几次编译/Windows临时路径故障记录保留，不当行为红灯。

`backend-shared-verify-01.log`长批次在恢复套件装配阶段测试JVM OOM，不能标整批通过；按类新建512MiB fork后补验恢复／策略，旧同步通道关闭断言按既有异步语义修正，Worker15在`backend-shared-verify-04.log`全部通过。最终按类汇总316条通过、0失败／错误，2条默认关闭性能套件记录；单独性能批次仍运行，不把skip算通过。质量门禁、JaCoCo和报告路径详见[当前汇总](green-results.md)。

负责人再次要求复用基础设施并删除无用内容：只读核对示例业务表契约，清除过时迁移占位说明和重复设计清单，修正quickstart旧容器启动说明。项目无Compose／服务初始化定义，Redis本期未引入；必要业务迁移与显式测试／演示工具保留，启动不造数，见[核对结果](shared-services.md#业务数据边界核对)。

### 2026-10-12既有来源及Review收尾

上述316条及合成订单为历史。造数工具、脚本、SQL订单夹具及专属测试已删除，前端业务API统一于shared/api/api.ts且无功能目录转发；数据库订单查询、受理、Excel、演示及性能全部只读exportflow.orders。当前功能回归304条记录中300通过、0失败、0错误，2条来源规模不足及2条默认关闭性能另记；04受影响51项集成／8项单元及正式质量门禁通过，见[当前功能结果](provided-results.md)。

Review修复Outbox候选饥饿与等待告警误计数，两条真实MySQL回归先红后绿。修复后110000行9列五次完整性能测量全部成功（10.055–12.625秒，最大堆512MiB，无OOM），200000行来源不足只记录，详见[来源证据](provided-data.md)。大SELECTED创建性能仍须根据独立API样本判定，浏览器、broker进程重启及来源无同时间戳边界仍列为未验证；不宣称全规格验收完成。

负责人随后明确取消性能测试；已停止当前API测量，不补测其余场景，临时schema／vhost／测试账号精确回收，三个用户服务健康且来源聚合不变。开发及代码review已完成，本轮收尾；性能取消不计功能失败，也不记性能通过。最终边界见[收尾记录](provided-data.md#本轮收尾与性能取消)。
