# 订单规格0.4代码Review

> 2026-10-12最终状态：负责人确认本轮剩余验证无需执行，性能已取消；原“待验／待补／未验证”为历史事实，不再是本轮待办。范围和日期见[免验确认](../../../specs/002-order-management/tasks.md#本轮免验确认)，免验不标实测通过。

> 2026-10-12状态更新：下文316项等通过统计属于旧数据方案的历史证据；当前已删除订单造数，直接读取负责人容器中的110000条订单。当前复验及200000规模数据缺口以[来源记录](provided-data.md)为准，不沿用历史通过结论。

本轮开发与review收尾：来源、API集中、无用文件清理及已复现缺陷修复已完成，当前功能300项通过及正式质量门禁通过。负责人已取消性能测试，当前测量已停且专属资源已回收；大SELECTED未取得完整P95，不据耗时现象宣称性能达标或最终失败，后续未继续测量。未验证边界与中止说明见[最终来源记录](provided-data.md#本轮收尾与性能取消)。

日期：2026-10-11。依据负责人明确要求“开发完成要review一遍代码，看看有什么需要调整的”。[生产链路](../../../specs/002-order-management/plan.md)齐备并验证真实创建/生成/下载后开始统一检查；完整回归与性能继续进行，本记录不是最终通过结论。归属[任务](../../../specs/002-order-management/tasks.md)，实现细节见[补充设计](../../../specs/002-order-management/implementation-notes.md)。

## 检查范围

前端统一API、协议校验、查询/URL/敏感输入、选择与幂等提交；后端HTTP/事务受理、Outbox确认/期限、执行权与停机、连续断点、SXSSF关闭/刷盘、产物/下载及清理；运行配置、脚本、构建质量与文档追踪。不增加任务详情、SSE或Redis。

## 已发现与处理

| 项目 | 影响与调整 | 回归证据及状态 |
| --- | --- | --- |
| 恢复前缀失权检查 | 原collect校验所有前缀后才检查guard，失权后仍能打开后续块；增加入口、逐块与逐行检查 | `backend-prefix-guard-red.log`先复现；`backend-review-green-01.log`回归通过；对应[执行隔离](../../product/acceptance-criteria.md#ac-068) |
| 初始化异常抑制 | 原finally可能用关闭异常覆盖插入异常；回滚未确认时关闭连接且禁止切回自动提交，关闭异常追加到主异常 | `backend-seed-close-red.log`、`backend-seed-close-green.log`；对应[独立输入保护](../../product/acceptance-criteria.md#ac-061) |
| PowerShell中文编码 | Windows PowerShell5.1无法解析无BOM中文文件；脚本改UTF-8 BOM | 两脚本Help红绿日志、`seed-script-invalid.log`与`seed-script-real-01.log`，实际初始化110000行；不改变业务规则 |
| 检查器运行兼容 | Checkstyle9.3处理Java记录语法内部报错；显式锁定10.26.1并保留google_checks.xml | `backend-quality-01.log`失败；`backend-quality-02.log`格式/Checkstyle/SpotBugs通过，高优先级BugInstance=0；最终构建仍须再验证 |
| JaCoCo参数 | 独立堆参数不可覆盖覆盖率agent；POM独立jacocoArgLine和test.jvmArgs，性能脚本使用后者 | `backend-shared-verify-04.log`门禁通过，HTML/XML报告已生成，见[汇总](green-results.md) |
| 连续断点不变量 | expected计数/游标/配置必须对应数据库锁定前缀；块逻辑引用必须对应代次和身份 | `backend-review-red-02.log`先复现错误APPLIED；`backend-review-green-02.log`真实MySQL通过；对应[断点恢复](../../product/acceptance-criteria.md#ac-069) |
| 块读取主异常 | 损坏块打开失败时关闭异常追加为suppressed，保留主异常 | 同批红绿证据；对应[断点恢复](../../product/acceptance-criteria.md#ac-069) |
| 旧查询选择 | 新范围请求期间保留旧行，但不能勾选、全选或反选；有效结果恢复后重新启用，分页/排序仍可切换 | `frontend-selection-red.log`复现；完整前端153/153通过，新旧交互共同验证；对应[选择生命周期](../../product/acceptance-criteria.md#ac-020)、[选择工具](../../product/acceptance-criteria.md#ac-033) |
| 坏消息隔离顺序 | 原先立即abort后异步停消费者可能触发恢复领取；现在独立线程先停止消费，再关闭该通道 | `backend-delivery-isolation-red.log`两项红灯，`backend-delivery-isolation-green.log`两项通过；`backend-shared-verify-04.log`真实Worker15通过；对应[执行隔离](../../product/acceptance-criteria.md#ac-068) |
| 监督通道登记竞态 | 失权与通道回调附加并发时，晚附加回调仍关闭通道；计时字段初始化后再发布lease，避免读取未初始化期限 | 同批两项红绿证据；ExecutionSupervision7与Worker15真实窗口复验通过 |
| 缺失文件错误码 | 实际410返回GONE，但定稿契约及回归要求MISSING；恢复EXPORT_FILE_MISSING并记录仅taskId的安全告警，保持成功事实 | `backend-verify-01.log`真实HTTP先复现；共享服务下DownloadBoundary与DownloadExportFile完整通过；对应[安全下载](../../product/acceptance-criteria.md#ac-018) |

已移除发布适配器未使用的ObjectMapper依赖和局部连接工厂变量；生产/工具源码补充可点击规格及真实测试链接，完整映射仍由任务负责。前端最新typecheck/build通过：`frontend-review-typecheck.log`、`frontend-review-build.log`；153项测试的最终全量已通过。单JS约1259.56kB（gzip399.26kB），Vite体积警告保留；本期主要依赖在订单首屏使用，未添加图表/编辑器等非首屏重库，也未调高警告阈值掩盖提示；目标浏览器首屏实际体验仍在手工缺口内。

完整回归还暴露三处测试装配问题：管理权限用例从未带userinfo的AMQP URL提取用户名；相关confirm探针给包含真实管理HTTP的流程仅50ms预算；故障trigger使用无SUPER权限的普通隔离账号。分别改为实际应用用户名、定稿5秒总预算和管理员连接仅建/删本次schema内trigger，保留真实生产调用及原断言。管理权限与相关confirm专项已通过，创建事务4项专项已通过。原失败日志保留在`backend-verify-01.log`。

## 待完成

自动化回归与正式质量门禁的结果见[当前汇总](green-results.md)，串行性能仍在独立测量；共享broker进程重启持久性没有证据。浏览器检查被Windows Computer Use工具中止，原因是无法可靠识别当前URL以执行策略；未继续UI输入，Chrome/Edge手工结果不标通过。详细缺口见[浏览器检查表](browser-checklist.md)。

## 共享服务后的追加review

停机guard在SmartLifecycle之前失效，并对重复close保持幂等；停用消费者不等待不存在的停止回调，新增回归先复现3秒无效等待再修复。共享schema名称扩展使用严格32位小写hex白名单，SeedOrders及脚本同步。队列级ACK超时避免更改共享broker全局设置；4项环境工具通过，缺少队列参数先取得真实红灯后修复。真实策略24项、查询／创建／环境27项、Worker15项和正式门禁已通过；详细证据见[共享服务记录](shared-services.md)。

基础设施重复定义核对及无用占位删除见[业务数据边界](shared-services.md#业务数据边界核对)。项目不维护现有服务部署；必要业务表、协议校验schema及显式演示／测试数据仍有调用，不按文件名中的data/schema误删。

## 2026-10-12来源修订Review

造数Java／PowerShell、SQL订单夹具及专属工具测试均已删除；测试harness旧编译输出通过Maven clean清理，历史失败诊断保留于artifacts。当前订单V1只建SQL SECURITY INVOKER视图，应用无原表写权限；V2仅为本项目任务关系建立原订单外键。演示、数据库查询、文件准确性和性能均读取原来源，生产没有订单INSERT／UPDATE／DELETE。权限拒绝探针不修改原值且不命中删除行，任务故障及文件故障注入只操作本次测试资源。

专项发现两处DATETIME测试强转Timestamp错误，改为LocalDateTime明确UTC转换。大文件逐行核对改为只读ResultSet流式遍历，避免把110000条订单再完整载入测试堆。规模选择ID从来源查询，不生成假订单ID；不足200000只记录。当前证据及来源不变核对以[来源复验](provided-data.md)为准。

全量发现幂等测试仍依赖旧ALPHA零金额记录，已改为实际来源的订单号与金额，并保留原同键重放、规范化及唯一命令断言。日志安全检查改为核对来源的实际姓名／手机号，防止旧造数字面量使断言失效；断言不输出原值。未被任何测试读取的export-requests.json及两个POM对应的资源复制项也已删除；契约文档仍保留为设计基线。

独立性能发现真实调度缺陷：已开工任务的旧LEASED命令占用候选LIMIT，锁内拒绝后新PENDING任务得不到投递名额。新增候选饥饿及告警误计数两条真实MySQL回归，先失败再修复；候选在LIMIT前关联任务并筛PENDING，锁内状态／期限／token复核保持有效，等待告警同样只计需投递任务。04批次51项受影响集成与8项单元通过正式质量门禁，当前功能汇总300通过；对应[队列交付](../../product/acceptance-criteria.md#ac-049)、[后端闭环](../../product/acceptance-criteria.md#sc-010)。

修复后110000行9列五次独立Excel测量均成功，10.055–12.625秒，512MiB堆无OOM。性能探针每次结束清除真实委托mock的调用参数引用，防止测试持有旧大批数据；不改生产算法、不强制GC、不放宽阈值。200000行只记录来源不足，原候选饥饿失败保留为真实缺陷证据。
