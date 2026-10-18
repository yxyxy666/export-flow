# 006 任务统一删除验证记录

## 已完成验证

- 前端删除用例：`frontend/.\node_modules\.bin\vitest.cmd run src/features/export-tasks/__tests__/task-deletion.test.ts`，1 个测试文件、1 个测试通过。
- 前端全量回归：`frontend/test-results/results.json`，41 个测试文件、260 个测试通过。
- 前端 TypeScript：`frontend/.\node_modules\.bin\tsc.cmd --noEmit`，通过。
- 前端生产构建：`frontend/.\node_modules\.bin\vite.cmd build`，通过；保留既有 chunk 体积提示。
- 后端编译：`backend/.\mvnw.cmd -q -DskipTests compile`，通过。
- 后端回归：`backend/.\mvnw.cmd -q test`，20 个测试通过。
- 差异检查：`git diff --check`，通过。

## 当前阻塞

真实 MySQL、RabbitMQ、Redis 集成测试未能启动，`SharedServices.require()` 报依赖资源启动失败。因此删除与 Worker 认领/提交竞态、外键子表清理、迟到消息墓碑、文件清理失败重试尚无真实依赖证据。环境恢复后运行 `TaskDeletionContractTest` 及新增的竞态、清理测试，并把结果追加到本文件。

## 实现检查点

- 运行中任务请求删除后只建立 `STOPPING` 屏障，关闭未执行投递、递增控制版本并失效旧 fence。
- Worker 认领、续租、终态提交和控制检查均拒绝删除中的任务；迟到命令在删除屏障下安全确认。
- 终态任务硬删除任务根记录及业务子表，保留与任务表解耦的最小技术墓碑。
- 删除作业通过 `STOP_WAIT`、`FILE_CLEANUP`、`RETRYABLE_FAILURE` 推进，文件失败按退避重试。
