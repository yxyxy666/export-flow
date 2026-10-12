# 订单管理测试与真实边界验证

本目录是规格0.4获批后的测试设施，关联[计划](../specs/002-order-management/plan.md)、[任务](../specs/002-order-management/tasks.md)与[当前证据](../docs/validation/002-order-management/green-results.md)。[测试harness POM](backend/pom.xml)仅编译测试、加载已存在的正式源文件及资源，不替代仓库正式[后端POM](../backend/pom.xml)，也不生成迁移或生产入口。正式入口已实施，夹具已删除测试准备阶段“入口缺失就跳过”的旧分支；后续入口缺失必须直接失败。

前端使用项目锁定pnpm10.34.6执行`npx --yes pnpm@10.34.6 --dir frontend test:run`。后端正式门禁使用`backend/mvnw.cmd -f backend/pom.xml verify`；harness保留测试准备阶段用途。按负责人2026-10-11要求全部复用project-export-flow，不再启动Testcontainers或Ryuk。真实边界使用正式Failsafe的完整临时路径配置，避免Windows默认短路径的NIO管道错误。环境与实际限制见[共享服务记录](../docs/validation/002-order-management/shared-services.md)。

`ProductionBackendFixture`加载已实施的真实后端，入口缺失直接失败。每个业务用例启动独立上下文，在共享服务内创建唯一任务schema/vhost/测试账号与文件目录，结束后回收本次资源，未ACK消息与故障不跨用例残留。订单直接只读复用exportflow.orders，不生成、不复制、不修改来源；规模不足明确记录数据前置条件缺口，不计功能失败。仅测试TCP桥承担断线故障；不停止共享broker或修改既有用户。`TestEnvironmentTest`通过只证明测试工具正常。测试会保留自己创建的临时文件目录供失败诊断，不递归清理生产文件根。当前结果见[来源复验](../docs/validation/002-order-management/provided-data.md)和[代码review](../docs/validation/002-order-management/code-review.md)。

测试专用JDBC包装器在实际提交之后注入异常；Worker子进程屏障在真实断点端口返回之后停住，便于强制结束进程。它们不实现业务状态机、SQL适配器或文件生成器，尚未执行的故障窗口仍在[缺口清单](../docs/validation/002-order-management/coverage-gaps.md)中。

## 场景补充与专项执行

本轮[34组场景及8组断言对照](../docs/validation/002-order-management/scenario-completion.md)指向具体测试。BoundaryProbe默认委托真实端口/组件，挂钩只用于困难窗口；Barrier子进程按真实调用前后和第N次边界供kill；WorkerSampledMain采样真实512MiB进程的堆。正式Wrapper以UnitTest后缀交给Surefire，其余真实边界交给Failsafe；运行命令见[quickstart](../specs/002-order-management/quickstart.md)。

性能默认不纳入普通单测；显式命令：

```powershell
powershell -NoProfile -File scripts/check-order-performance.ps1
```

正常调度、监督和消费者在性能用例中启用；API每场景30预热/200原样本，Excel110000/200000九列各5次串行并记录生成与等待时间、堆/scratch/文件。原始采样只在实际运行后写入artifacts；缺失入口的跳过不产出虚构指标。Chrome/Edge按[14项手工用例](../docs/validation/002-order-management/browser-checklist.md)逐步执行并记录真实版本/视口/截图及直接URL下载，当前未执行。

ExecutionSupervisionTest包含真实五分钟单调时间边界：正式入口可用后该用例会维持心跳并等待实际预算到期，不缩短产品参数、不用改DB期限冒充本地计时。普通后端全量回归届时会包含这段运行时间。
