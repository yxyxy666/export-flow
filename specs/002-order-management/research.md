# 研究与决策依据

**日期／状态**：2026-10-07；草稿0.3。负责人明确“解决下所有待决策项，按你的建议来”，D-01至D-12及其余待决方向已接受或明确延期，见[决策记录](decision-record.md)。未安装依赖、未改中间件、未做业务运行实验；规格及测试阶段仍待独立批准。关联[spec](spec.md)、[plan](plan.md)、[tasks](tasks.md)、[版本表](dependency-versions.md)。

## 工程现状

前端已锁定React19.3.0、Ant Design6.6.5、Router7.18.4、Vite8.3.3、TypeScript7.0.2；Query/RHF/Zod和测试工具尚未安装。后端pom及Java文件仍仅注释，没有正式入口、Wrapper、SQL或测试，业务方法属于首次实现。准备规格前工作区干净，本轮已有文档草稿，不据此声称初始干净。

学习稿帮助展开处理顺序；以实际骨架和正式产品/ADR为准。[ADR-0008](../../docs/adr/0008-worker-execution-lease-and-recovery.md)原为提议，本次接受执行/异常恢复/断点/产物子集，用户暂停等延期；[ADR-0009](../../docs/adr/0009-engineering-runtime-and-progress-notification.md)归属工程和后续通知方向。决定归属已同步，没有从规格静默覆盖上游。

## 选择理由

- MySQL租约与fence能够由短事务裁决，避免长时间行锁与永久标志；RabbitMQ只保管原未确认交付，避免扫描RUNNING重复创建命令。
- 不可变JSONL块与DB连续清单保存可恢复事实，SXSSF只负责最后装配；半成品xlsx和POI scratch不能当跨进程断点。
- 同主机持久目录简化学习项目，原子发布、READY登记、唯一结果引用及清理互斥保持可核对窗口；下载沿用同源文件流。
- 经典持久队列符合当前单节点开发环境；禁TTL/expiry/drop-head和自动死信/重投上限，异常消息停止消费槽保留原命令，接受人工修复取舍。
- 默认排序保留现有产品基线；活跃优先及其依赖排序撤出，完整任务页/SSE/暂停不扩入当前规格。
- SSE未来采用独立进度Outbox和每实例持久队列，缓存不当通知日志；空闲后跨设备不自动唤醒的限制明确接受，避免固定前端轮询。
- 本地命令门禁与真实依赖测试优先，远程CI/发布/Compose延期；覆盖率先生成基线，无未经验证的百分比门槛。

## 依赖与环境调查

2026-10-07直接读取Maven Central各组件metadata及Boot3.5.16 BOM，确定Boot3.5维护分支、MyBatis starter3.0.5、POI5.5.1、Maven3.9.16及BOM管理测试组件；读取npm registry的包版本、发布时间、engines和peerDependencies，选择兼容React19/Vite8/Node22.14的组合。jsdom30要求更高Node补丁，故选26.1.0。核对POI5.5.1源码公开刷行/缓冲API，确定每块末落盘，再一次封装并原子转换；初始化工具采用已有Java21及锁定JDBC的独立源文件入口，不引入额外客户端工具。精确坐标和来源见[dependency-versions](dependency-versions.md)。这是元数据调查，不是安装/完整依赖图或运行兼容验证。

只读CIM/Get-Volume/java-version核对了本机CPU/内存/NTFS/Windows/Temurin；系统Maven未找到。参考硬件、服务资源、采样与串行测量已定，细节见[参考环境](decision-record.md#参考环境及事实核对)，未生成性能结果。

此前docker ps有三容器healthy证据，本轮同命令退出码0但desktop-linux context无记录；用户此前说明Docker服务已启动仍保留。本次没有尝试启动/删除资源或读取凭据，实际环境在获批执行前复核context/隔离/连接。

## 后续验证与回退

本期查询/受理/投递/执行/真实Excel/恢复/下载完整设计见[Excel方案](excel-design.md)及[函数清单](function-design.md)。数据块、装配、校验、发布全流程必须计入文件指标，使用真实MySQL/MQ/文件验证kill、未知提交、旧身份与清理窗口，不以替身验证资源或文件准确性。

Windows同卷ATOMIC_MOVE、reparse拒绝和POI资源关闭须真实验证；force不宣称宿主断电级目录元数据保证。回退停新受理/消费、协调执行停止，保留任务/幂等/原命令/块/就绪成功引用，不删受保护结果或重置fence。

本期无剩余技术待选项；规格整体和测试阶段审批、依赖安装、环境能力与所有业务测试仍未完成。文档验证检查链接、唯一编号、函数/文件清单、AC/SC映射和阶段记录，不替代产品验收。
