# 本次决策定稿记录

**日期／版本**：2026-10-07／草稿0.3。**决策状态**：项目负责人要求“解决下所有待决策项，按你的建议来”，据此接受建议并定稿；规格整体审批与测试阶段审批仍未给出。关联[spec](spec.md)、[plan](plan.md)、[tasks](tasks.md)、[research](research.md)、[版本表](dependency-versions.md)、[ADR-0008](../../docs/adr/0008-worker-execution-lease-and-recovery.md)、[ADR-0009](../../docs/adr/0009-engineering-runtime-and-progress-notification.md)。

## 逐项结论

| 事项 | 已确定方案 | 归属／后续验证 |
| --- | --- | --- |
| D-01 范围 | 真实查询、受理、投递、消费、Excel、单任务回执与下载 | [产品阶段](../../docs/product/order-export-prd.md#612-订单管理分阶段规格待评审)；完整任务页和SSE仍延期 |
| D-02 执行与恢复 | MySQL owner/fence/lease、条件心跳、原未确认命令有界接管、块断点、READY对账与采用/清理互斥；无可信断点仅在安全重复已证明时重建 | [ADR-0008](../../docs/adr/0008-worker-execution-lease-and-recovery.md)接受此子集；用户暂停/显式恢复与跨主机存储不纳入当前MVP |
| D-03 幂等 | SHA-256、规范化版本1、ID去重排序、保留字段顺序；异载荷409 IDEMPOTENCY_KEY_REUSED | [HTTP](contracts/http-api.md)；TC-217/218 |
| D-04 事务 | READ COMMITTED；锁序task→outbox→execution→checkpoint→artifact/candidate→attempt，省略只能向后；SKIP LOCKED逐task裁决 | [模型](data-model.md)；真实MySQL并发验证 |
| D-05 MQ | /export-flow；export-flow.commands；export-flow.export.requested.v1；export.requested.v1；durable direct/classic、persistent、mandatory、相关confirm；消费者并发1/预取1 | [消息契约](contracts/export-command.md)；无TTL/expiry/drop-head/DLX/次数上限，本期无长度上限 |
| D-06 投递参数 | 扫描1秒、批20、并发2、单条总预算5秒、投递lease15秒、退避1–30秒全抖动；到期扫描5秒、受理窗口10分钟不变 | [消息契约](contracts/export-command.md)；领取min(批量,空闲槽)，截止不延期 |
| D-07 隔离与运行 | schema export_flow、vhost /export-flow、Redis前缀export-flow:；宿主机API127.0.0.1:8080、Vite127.0.0.1:5173；外部文件根LOCALAPPDATA/ExportFlow/data | [运行规则](../../docs/rules/container.md)；不新建Compose，凭据外部注入，本次不创建资源 |
| D-08 测试 | JUnit/AssertJ/Boot Test/MockMvc/Testcontainers；Vitest/Testing Library/MSW；真实隔离MySQL/MQ/文件 | [ADR-0009](../../docs/adr/0009-engineering-runtime-and-progress-notification.md)；测试harness不冒充生产集成 |
| D-09 版本 | Java21；Spring Boot3.5.16、MyBatis starter3.0.5、POI5.5.1、Maven3.9.16；其余精确坐标见版本表 | [dependency-versions](dependency-versions.md)；版本存在及元数据已调查，尚未安装/运行兼容验证 |
| D-10 查询数据 | UTC秒/DATETIME(0)、DECIMAL(18,2)、keyword literal LIKE和as_ci、订单号bin精确比较；初始化后只读，Java21源文件模式运行独立JDBC PreparedStatement工具，不依赖未选择的MySQL CLI/ODBC；排序切换保留同筛选选择 | [模型](data-model.md)、[HTTP](contracts/http-api.md)；稳定keyset按created_at/id倒序 |
| D-11 客户端请求 | 查询/创建均10秒；创建不自动重试或换键；UNKNOWN冻结原载荷原键；键16–128安全ASCII，客户端UUID | [HTTP](contracts/http-api.md)；同次重试确认 |
| D-12 参考测量 | 当前Windows本机参考配置；API预热30次/200样本/单客户端；真实Excel每规模5次串行测量，全部5次须达标；独立512MB JVM | 下方参考环境与[tasks](tasks.md#sc验证映射)；完整块读取/落盘/装配/校验/发布计时 |

## 文件及执行参数

采用[Excel方案](excel-design.md#本期具体建议参数)全部建议值：源批1000、SXSSF窗口100、inline字符串、压缩scratch、64KiB缓冲、块行1MiB/块16MiB预算、认领5秒、lease30秒/heartbeat5秒、单次尝试5分钟、最多3次成功异常接管、恢复退避1–30秒、broker ACK timeout10分钟、停机宽限15秒、孤儿grace24小时/清理批20。清理每10分钟运行一次，与业务监督分开调度。

HTTP请求体限制16MiB，命令体限制16KiB；200000个合法Long ID和全部九字段必须在限制内，测试同时验证字节上限和业务数量上限。MySQL连接池最大10、最小空闲2、连接获取5秒，锁等待5秒；事务异常仍先核对提交结果。后台健康检查5秒，坏消息停止消费槽；依赖故障恢复1–30秒退避，不以重试次数删命令。

启动及每次投递核验唯一绑定和有效策略，管理请求超时2秒，计入5秒投递总预算。未SENT记录超过60秒、SENT/PENDING超过60秒、unacked超过单次5分钟预算、消费者为零且队列非空、MQ队列超过1000或broker磁盘/内存告警时输出安全结构化告警；同类告警最多每30秒一次，关键状态变化立即记录。容量告警交由操作者受控停止新受理并核对依赖；已提交任务、幂等查询和十分钟窗口保持。告警不复制消息或自动失败SENT任务，MySQL不可读不能伪造已回滚结论。

Outbox SENT/CLOSED及投递历史保留，不自动删除；日志本地滚动保留7天，不记客户输入或原载荷。任务、成功结果和受保护断点永久保留；孤儿只按DB资格删除。

## 参考环境及事实核对

- 本机：Windows 11专业版10.0.26200；AMD Ryzen 7 9700X，8核16逻辑处理器；物理内存33462767616字节；D盘NTFS，总139691290624字节，观察可用118772805632字节。记录采样值，后续运行报告重采并保留差异；不宣称已测性能。
- 已安装Temurin21.0.12.1+1、Node22.14.0；系统Maven未找到，使用定稿Maven3.9.16，在获准测试准备后提供外部测试工具，正式Wrapper在生产实施阶段生成。
- 性能隔离服务选MySQL8.4（2CPU/2GiB）、RabbitMQ3.13（1CPU/1GiB）、Redis7.4（1CPU/256MiB，当前链路不接入），同一宿主机本地连接；JVM固定-Xms512m/-Xmx512m，导出并发1。只用合成数据，测量期间不运行其他压力测试，包含正常dispatcher/监督开销；不限制JVM可用CPU数。隔离容器创建与digest记录属于获批后的环境准备，不修改共享示例容器。
- 数据固定Seed=20261007；查询/创建用110000基线，文件用110000及200000行九列，200001拒绝场景单独夹具。API延迟从客户端发送到完整响应接收，200样本nearest-rank P95；文件计时从获得执行权后首次生成动作到校验发布并提交成功，单独记录排队等待。
- 浏览器目标确定为Windows稳定Chrome与Edge，1280×720及键盘/焦点；运行时记录真实版本。IAB不作为目标认证环境，骨架Network面板/异常注入缺口仍保留，不能批准为通过。
- 之前docker ps有三容器healthy证据；本轮同命令退出码0但无记录，context为desktop-linux。只能说明该context当前未观察到运行容器；连接、目录原子移动/reparse拒绝和实际策略验证仍未执行，不伪造兼容成功。这些是运行待验证项，已经选定技术方案，无需再次选择。

## 已确定延期范围

本期不新增完整任务列表、失败重试页面、SSE/Redis接入、用户暂停/显式恢复、跨主机存储、托管CI、远程发布或应用Compose。SSE/缓存/可靠通知的后续方向见[ADR-0009](../../docs/adr/0009-engineering-runtime-and-progress-notification.md)，任务默认排序沿用产品现行值；活跃优先及依赖它的进度排序撤出且编号不复用。暂停方案在ADR中保留为历史提议，不作为本期已接受能力。延期是范围决定，不再以待选技术阻塞当前规格。

## 阶段结论

负责人追加的“已完成部分写入临时文件，全部完成后改为正式结果”已纳入[Excel方案](excel-design.md#sxssf内容与资源)：逐块刷出SXSSF行及缓冲，最终一次封装.xlsx.part，关闭/校验/force后原子改名；未完成或失败的临时内容不作为下载结果。新增刷盘函数及用例映射随本次草稿修订，不产生代码。

技术、产品候选和参数决策已确认；仅文档更新。规格整体审批与测试阶段审批仍独立待审，相关测试、故障注入、兼容和性能结果均未产生。本次批准决策不会把任何未执行测试、骨架覆盖缺口或产品标准标记为通过。
