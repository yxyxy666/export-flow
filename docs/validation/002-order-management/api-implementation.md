# 统一API实施与占位清理

日期：2026-10-11（Asia/Hong_Kong）；证据版本 `0.4-api-01`。依据[规格](../../../specs/002-order-management/spec.md)、[计划](../../../specs/002-order-management/plan.md)、[函数清单](../../../specs/002-order-management/function-design-reference.md)，授权见[审批记录](../../../specs/002-order-management/tasks.md#审批记录)。此记录补充历史[测试先行报告](red-results.md)，不覆盖原红灯和阻塞证据。

## 实施范围

- 业务API统一到 `frontend/src/shared/api/api.ts`，仅queryOrders/createExportTask；底层http-client/api-error处理超时、取消、监听释放和安全错误。创建不自动重试，不追加任务GET。
- 订单及任务创建类型、独立Zod schema、分页类型已实现；输入/响应拒绝非法Long ID、金额、日历时间、分页、额外字段、UUID及重复字段，未知输出枚举保留原值。Zod锁定4.6.5。
- 删除旧orders-api/export-tasks-api和未使用的任务表格/任务查询Hook占位，共四个文件。保留任务管理静态页及创建类型/schema，后续任务列表和SSE另规格。
- 页面仍未接入API，后端未实施；T-020/T-021/T-022仅部分完成，完整功能不能判交付。

## 测试先行及执行

原API套件已在生产实现前存在。首轮实际执行退出1，因统一API缺失不能加载，不计业务红灯；缺口沿用G-01。原页面55条预期失败及后端缺失入口证据保留在[历史报告](red-results.md)。

实现后原33条API用例通过。补充5条输入边界回归后先运行，实际出现2条失败：非法ID和NaN金额在Zod refine中触发裸BigInt异常，未形成安全协议错误；修正格式校验与数值比较顺序后，38条API用例全部通过。

| 检查 | 结果 | 限制 |
| --- | --- | --- |
| API、导航和MSW工具回归 | 退出0；3个套件44条通过（API38、导航4、工具2） | 网络为合成MSW/mock，不证明真实后端、事务或Excel |
| 冻结锁文件安装 | 退出0 | 本次仅新增锁定Zod4.6.5，不升级其余依赖 |
| 统一API及其依赖的独立严格类型检查 | 退出0 | 只证明本次API实现类型正确，不代替全项目门禁 |
| 文档链接及目标目录/逐文件职责核对 | 退出0；16份文档本地链接/锚点可解析，220项目标文件与职责一致 | 不作为运行或产品验收证据 |
| 前端完整类型检查 | 退出1；9处其他生产模块/导出缺失 | 查询模型、选择模型、导出用例及Hook尚未实现；未使用空壳消除错误 |
| 后端、完整页面、生产构建和性能 | 本轮未执行 | 保留原缺口，后续恢复后执行；不标通过 |

## 复现命令

仓库根执行，Node22.14.0、pnpm10.34.6、Vitest5.0.3；依赖沿用[环境记录](environment.md)。

```powershell
npx --yes pnpm@10.34.6 --dir frontend install --frozen-lockfile
npx --yes pnpm@10.34.6 --dir frontend test:run src/features/orders/__tests__/order-api.test.ts src/features/orders/__tests__/navigation.test.tsx src/test/runtime.test.ts
npx --yes pnpm@10.34.6 --dir frontend typecheck
npx --yes pnpm@10.34.6 --dir frontend exec tsc --ignoreConfig --noEmit --strict --noUnusedLocals --noUnusedParameters --verbatimModuleSyntax --skipLibCheck --target ES2022 --module ESNext --moduleResolution Bundler --lib ES2022,DOM,DOM.Iterable src/shared/api/api.ts
```

本轮报告另存本地 `artifacts/002-order-management/api-01/`，不覆盖0.4-test-03日志。G-01中API套件已解除阻塞，其他套件仍待实现；G-02/G-03未消除。开发授权不豁免这些真实边界测试及产品成功标准。
