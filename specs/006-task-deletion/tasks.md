# 任务：任务统一删除

**输入**：[`spec.md`](spec.md)、[`plan.md`](plan.md)、[`data-model.md`](data-model.md)、[`contracts/http.md`](contracts/http.md)、[`research.md`](research.md)

**状态**：已按用户授权完成测试、自检和实现；真实 MySQL/RabbitMQ/Redis 集成证据待环境恢复。

## 审批记录

| 门禁 | 范围与文档版本 | 状态 | 批准人 | 日期 | 结论与依据 |
| --- | --- | --- | --- | --- | --- |
| 实施授权 | `006-task-deletion`全部核心规格和附属契约 | 已授权 | 用户 | 2026-10-18 | 用户明确要求按规格开始、测试完成后自检并直接开发 |
| 测试先行阶段 | 前端删除测试、后端删除单元/契约测试 | 已执行 | 模型自检 | 2026-10-18 | 新增行为测试先红后绿；真实依赖阻塞单独记录 |

## 执行任务

### 用户故事1：统一删除入口和契约

- [ ] **T-601**：在产品基线、OpenAPI和数据模型中锁定统一删除接口、操作回执、墓碑和错误码；关联[`AC-096`](../../docs/product/acceptance-criteria.md#ac-096)、[`AC-100`](../../docs/product/acceptance-criteria.md#ac-100)。
- [ ] **T-602**：定义前后端删除状态、模式、回执和`DELETE`动作类型；关联[`AC-096`](../../docs/product/acceptance-criteria.md#ac-096)、[`AC-101`](../../docs/product/acceptance-criteria.md#ac-101)。
- [ ] **T-603**：实现统一HTTP入口和前端单按钮确认、提交、UNKNOWN找回、错误展示；关联[`AC-096`](../../docs/product/acceptance-criteria.md#ac-096)、[`AC-100`](../../docs/product/acceptance-criteria.md#ac-100)。
- [ ] **T-604**：实现应用层统一删除用例和稳定错误分类；关联[`AC-096`](../../docs/product/acceptance-criteria.md#ac-096)。

### 用户故事2：运行中安全停止

- [ ] **T-605**：编写V8迁移、删除操作表、清理作业表和任务墓碑字段；关联[`AC-097`](../../docs/product/acceptance-criteria.md#ac-097)、[`AC-098`](../../docs/product/acceptance-criteria.md#ac-098)。
- [ ] **T-606**：实现运行中状态分流、控制版本递增、原投递权关闭和停止等待作业；关联[`AC-097`](../../docs/product/acceptance-criteria.md#ac-097)。
- [ ] **T-607**：把删除屏障纳入Worker认领、心跳、进度、块登记、产物登记和终态提交；实现安全停止证明；关联[`AC-097`](../../docs/product/acceptance-criteria.md#ac-097)、[`AC-098`](../../docs/product/acceptance-criteria.md#ac-098)。
- [ ] **T-608**：实现迟到命令、旧fence和删除墓碑的消息确认边界；关联[`AC-098`](../../docs/product/acceptance-criteria.md#ac-098)。

### 用户故事3：终态清理和实时收口

- [ ] **T-609**：实现终态任务分流、数据库子记录清理和任务活动版本更新；关联[`AC-099`](../../docs/product/acceptance-criteria.md#ac-099)、[`AC-101`](../../docs/product/acceptance-criteria.md#ac-101)。
- [ ] **T-610**：实现任务根记录和业务子记录硬删除、任务引用保护、文件清理候选、清理租约、失败退避和独立技术墓碑完成；关联[`AC-099`](../../docs/product/acceptance-criteria.md#ac-099)、[`AC-102`](../../docs/product/acceptance-criteria.md#ac-102)、[`AC-103`](../../docs/product/acceptance-criteria.md#ac-103)。
- [ ] **T-611**：实现清理完成后的列表过滤、下载/暂停/继续/重试拒绝和SSE活动版本收口；关联[`AC-101`](../../docs/product/acceptance-criteria.md#ac-101)。
- [ ] **T-612**：记录清理失败恢复、数据库/消息/文件依赖不可用时的安全状态和验证证据；关联[`AC-102`](../../docs/product/acceptance-criteria.md#ac-102)。

## 测试进度与证据

**阶段状态**：已执行；可用环境验证通过，真实数据库集成因依赖不可用而阻塞。

| 测试任务/用例 | AC/SC链接 | 目标文件 | 编写进度 | 执行进度与日期 | 实际结果与原因 | 命令/环境/证据 | 实现后结果 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| T-601 / TC-601 | [`AC-096`](../../docs/product/acceptance-criteria.md#ac-096) | `backend/src/test/.../TaskDeletionContractTest.java` | 已编写 | 已执行 2026-10-18 | MySQL 契约测试因 SharedServices 依赖启动失败而阻塞 | `backend/.\mvnw.cmd -q test`；单元回归通过，真实依赖日志见 Surefire | 待环境恢复 |
| T-601 / TC-602 | [`AC-100`](../../docs/product/acceptance-criteria.md#ac-100) | `backend/src/test/.../TaskDeletionUnitTest.java` | 已编写 | 已执行 2026-10-18 | 请求身份和版本边界通过 | 同上 | 通过 |
| T-605 / TC-603 | [`AC-097`](../../docs/product/acceptance-criteria.md#ac-097) | 迁移/契约测试 | 已编写 | 阻塞 | MySQL 不可用，迁移和约束无真实证据 | SharedServices.require() 依赖资源启动失败 | 待环境恢复 |
| T-606 / TC-604 | [`AC-096`](../../docs/product/acceptance-criteria.md#ac-096) | `TaskDeletionUnitTest.java` | 已编写 | 已执行 2026-10-18 | 统一仓库入口和稳定错误边界通过 | `backend/.\mvnw.cmd -q test`，20 tests passed | 通过 |
| T-607 / TC-605 | [`AC-097`](../../docs/product/acceptance-criteria.md#ac-097) | 运行中竞态测试 | 设计完成 | 阻塞 | 需要真实 MySQL/RabbitMQ/Redis | 环境恢复后运行认领、续租、提交竞态 | 待环境恢复 |
| T-608 / TC-606 | [`AC-098`](../../docs/product/acceptance-criteria.md#ac-098) | 迟到消息边界 | 设计完成 | 阻塞 | 需要真实 RabbitMQ 和墓碑数据 | 环境恢复后运行旧消息 ACK/fence 拒绝 | 待环境恢复 |
| T-609 / TC-607 | [`AC-099`](../../docs/product/acceptance-criteria.md#ac-099) | 数据清理契约 | 已编写 | 阻塞 | 需要真实 MySQL 外键 | 环境恢复后验证所有业务子表硬删除 | 待环境恢复 |
| T-610 / TC-608 | [`AC-099`](../../docs/product/acceptance-criteria.md#ac-099)、[`AC-102`](../../docs/product/acceptance-criteria.md#ac-102)、[`AC-103`](../../docs/product/acceptance-criteria.md#ac-103) | 清理作业实现 | 已实现 | 阻塞 | 文件/数据库依赖不可用 | 单元代码检查已覆盖重试分支；真实文件根待验证 | 待环境恢复 |
| T-603 / TC-609 | [`AC-096`](../../docs/product/acceptance-criteria.md#ac-096) | `frontend/src/features/export-tasks/__tests__/task-deletion.test.ts` | 已编写 | 已执行 2026-10-18 | 单一删除动作通过 | `frontend/.\node_modules\.bin\vitest.cmd run src/features/export-tasks/__tests__/task-deletion.test.ts` | 通过 |
| T-603 / TC-610 | [`AC-100`](../../docs/product/acceptance-criteria.md#ac-100) | 前端结果不明找回 | 已编写 | 已执行 2026-10-18 | 既有结果不明模式回归通过 | `frontend/test-results/results.json`：260/260 passed | 通过 |
| T-611 / TC-611 | [`AC-101`](../../docs/product/acceptance-criteria.md#ac-101) | 列表和SSE收口 | 已编写 | 已执行 2026-10-18 | 前端全量回归通过 | `frontend/test-results/results.json`：41 suites、260 tests passed | 通过 |
| T-612 / TC-612 | [`SC-019`](../../docs/product/acceptance-criteria.md#sc-019) | `docs/validation/006-task-deletion/verification.md` | 待补真实证据 | 阻塞 | 完整闭环需真实依赖 | 恢复 MySQL/RabbitMQ/Redis 后补执行命令和结果 | 待环境恢复 |

## AC/SC到任务和测试映射

| 标准 | 实现任务 | 测试/验证 |
| --- | --- | --- |
| [`AC-096`](../../docs/product/acceptance-criteria.md#ac-096) | T-601、T-602、T-603、T-604 | TC-601、TC-609 |
| [`AC-097`](../../docs/product/acceptance-criteria.md#ac-097) | T-605、T-606、T-607 | TC-603、TC-605 |
| [`AC-098`](../../docs/product/acceptance-criteria.md#ac-098) | T-606、T-607、T-608 | TC-606 |
| [`AC-099`](../../docs/product/acceptance-criteria.md#ac-099) | T-609、T-610 | TC-607、TC-608 |
| [`AC-100`](../../docs/product/acceptance-criteria.md#ac-100) | T-601、T-603 | TC-602、TC-610 |
| [`AC-101`](../../docs/product/acceptance-criteria.md#ac-101) | T-602、T-603、T-609、T-611 | TC-611 |
| [`AC-102`](../../docs/product/acceptance-criteria.md#ac-102) | T-610、T-612 | TC-608、TC-612 |
| [`AC-103`](../../docs/product/acceptance-criteria.md#ac-103) | T-610 | TC-608 |
| [`SC-019`](../../docs/product/acceptance-criteria.md#sc-019) | T-612 | TC-612及验证记录 |

## 覆盖缺口与阶段完成检查

| 标准/用例 | 缺口及影响 | 替代验证与恢复条件 | 负责人 | 审批结论与日期 |
| --- | --- | --- | --- | --- |
| 运行中删除停止窗口 | 当前真实 Worker 依赖不可用，无法执行竞态 | 代码已在删除屏障、fence、续租和提交入口增加拒绝；恢复依赖后执行真实竞态 | 模型 | 待环境恢复 |
| 物理文件清理失败重试 | 当前真实文件根/依赖未启动 | 代码保留 FILE_CLEANUP/RETRYABLE_FAILURE 和指数退避；恢复后注入失败验证 | 模型 | 待环境恢复 |
| 独立技术墓碑保留 | 保留期限仍在待确认清单 | 当前仅保存任务身份、操作身份、删除时间和状态，不保存客户数据或路径 | 模型 | 需产品决策 |

- [ ] 测试环境正常，真实MySQL/RabbitMQ/Redis/文件根和运行器已就绪（当前依赖启动失败）。
- [x] 所有AC均有实现任务和测试/验证映射。
- [x] 测试代码已编写并运行，预期失败、已有行为通过和环境阻塞分别记录。
- [x] 覆盖缺口已说明影响、替代验证、恢复条件。
- [x] 用户已明确授权直接按规格测试、自检和开发。
- [x] 可用测试阶段已完成并进入实现；真实依赖证据待恢复后补齐。

## 规格评审前检查

- [ ] 负责人确认`PAUSED`是否允许删除。
- [ ] 负责人确认独立技术墓碑、操作回执和清理审计记录的保留期限及字段范围。
- [ ] 负责人确认本增量已修改“成功任务永久保留”及失败任务重试历史口径。
- [ ] 负责人批准`202 Accepted + operationId`统一回执契约。
