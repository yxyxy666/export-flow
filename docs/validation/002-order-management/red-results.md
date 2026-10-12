# 测试先行执行结果

本页保留0.4-test-03原始结果。随后负责人已明确授权开始开发，统一API实施后的结果另见[0.4-api-01](api-implementation.md)；下文审批及未实施描述均为当轮历史状态。

日期：2026-10-11（Asia/Hong_Kong）。规格0.4已批准，本轮按负责人要求补充原审计的34组场景和8组断言，证据版本`0.4-test-03`。关联[任务及审批](../../../specs/002-order-management/tasks.md)、[环境](environment.md)、[补充对照](scenario-completion.md)、[覆盖缺口](coverage-gaps.md)、[机器摘要](test-summary.json)。本轮仅修改测试、测试夹具和追踪文档。

## 实际结果

| 检查 | 退出码／实际结果 | 判定 |
| --- | --- | --- |
| 前端完整测试 | 1；61个已收集用例中55个失败、6个通过；另4个套件导入阻塞 | 页面仍是静态占位，无业务请求/数据/控件，55个为预期页面行为失败；导入阻塞不算业务红灯 |
| 前端类型检查 | 1；12处生产模块／导出缺失 | 无新增测试源码类型诊断，不使用生产空壳掩盖阻塞 |
| 后端编译及默认完整测试 | 0；304个记录中4个工具检查通过、300个跳过，0失败/错误 | 298个因正式入口缺失；性能开关默认关闭产生另外2个套件级跳过记录，不能说304个业务通过 |
| 单独启用性能程序、512MB JVM | 0；API1个、Excel两个数据量，共3个场景全部因入口缺失跳过 | 开关/参数化收集可用，未运行采样、未生成性能报告，退出0不表示指标通过 |
| 生产构建、实际Excel与浏览器 | 未执行 | 无生产实现，无绿灯/实际Excel/截图/性能达标结论 |

前端6个通过为4个既有导航/菜单/404回归与2个MSW Node网络工具检查。四个阻塞套件仍为`order-query.test.ts`、`order-selection.test.ts`、`order-export-state.test.tsx`、`order-api.test.ts`。本轮新增页面边界用例已被收集，失败主要停在真实页面尚无合成订单或业务控件。

后端JUnit逐条通过assumption记录`com.exportflow.ExportFlowApplication`缺失，未使用内存后端或测试业务算法替代生产。所有业务/工具测试及测试辅助源已通过编译；这不证明Spring端口代理、真实MySQL锁/约束、RabbitTemplate回调和POI/文件故障窗口装配已经运行正确，见G-03。

四项真实环境检查再次通过：独立MySQL UTF8/UTC/事务及SKIP LOCKED、RabbitMQ持久confirm与未ACK重交付、本机文件原子移动/关闭读取、Java回环HTTP。资源由各用例独立创建并显式关闭，未修改共享服务数据。

## 复现命令

仓库根目录执行：

```powershell
npx --yes pnpm@10.34.6 --dir frontend test:run
npx --yes pnpm@10.34.6 --dir frontend typecheck

# 本机无法取得Ryuk镜像时仅本轮设置；网络恢复可省略。
$env:TESTCONTAINERS_RYUK_DISABLED='true'
$taskMaven=Join-Path $env:LOCALAPPDATA 'ExportFlow\tools\apache-maven-3.9.16\bin\mvn.cmd'
& $taskMaven -B -f tests/backend/pom.xml test

# 显式启用性能：使用独立报告后缀，避免覆写默认轮结果。
& $taskMaven -B -f tests/backend/pom.xml test '-Dexportflow.performance=true' '-Dtest=ApiPerformanceTest,ExcelPerformanceTest' '-Dsurefire.reportNameSuffix=performance-enabled' '-DargLine=-Xms512m -Xmx512m'
```

性能程序按[参考环境](../../../specs/002-order-management/decision-record.md#参考环境及事实核对)使用固定容器限额、110000固定seed的API样本及110000/200000九列文件；正常dispatcher/监督/consumer启用。独立512MB Worker另有实际进程堆采样。Chrome/Edge手工步骤见[检查表](browser-checklist.md)，尚未执行。

原始日志位于本地`artifacts/002-order-management/`：`test-03-backend.log`、`test-03-frontend.log`、`test-03-typecheck.log`、`test-03-performance-enabled.log`。前端JUnit/JSON在`frontend/test-results/`，后端JUnit在`tests/backend/target/surefire-reports/`；后缀performance-enabled单独统计，不与默认轮累加。大产物/日志不提交，小型摘要保存计数、原因和哈希，可按命令复现。

## 门禁与历史

原审计34组场景及8组断言均已有源码或具体手工用例对应；本轮任务的场景补充已完成。实际生产边界不能运行，装配与覆盖缺口尚待评审，测试阶段门禁不自行判通过，生产审批状态不变。完整性说明见[补充对照](scenario-completion.md#完成口径与后续执行)。

补充前0.4-test-02为前端36个（30失败、6通过）和4个套件阻塞，后端83个（4环境通过、79入口缺失跳过）。原缺失项保留在[历史审计](coverage-audit.md)，最新状态以本报告和补充对照为准。
