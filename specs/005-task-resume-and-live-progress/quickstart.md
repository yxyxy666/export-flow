# 本地操作及验证

沿用[项目启动](../../README.md#项目启动)和[既有基础设施规则](../../docs/rules/container.md)。不启动中间件容器、不改来源订单、不清除export-data。

## 更新运行版本

当前已运行的后端不会因源码或验证jar变化自动更新。完成验证后，在用户原make终端Ctrl+C，再在根目录执行`./make dev`；启动迁移会追加V7继续回执表，原任务与块登记保留。不要另起后台后端与8081争抢端口。前端通过原5173访问`/export-tasks`。

PAUSED任务点击“继续”并确认；等待核验，文件不匹配时进度回退到已核验数量，黄色图标说明重建。健康块身份保持，重建后继续剩余数据，最终文件采用后100%并可下载。结果不明只点“找回结果”，不创建新操作。

## 功能验证

根目录PowerShell，Java21、Node22、pnpm10.34.6；Maven Wrapper验证使用独立`artifacts/005-task-resume-and-live-progress/backend-target`。实际执行范围和结果以[验证记录](../../docs/validation/005-task-resume-and-live-progress/verification.md)为准。

```powershell
$taskEvidenceRoot = Join-Path (Get-Location) 'artifacts/005-task-resume-and-live-progress'
./backend/mvnw.cmd -f backend/pom.xml "-Dexportflow.buildDirectory=$taskEvidenceRoot/backend-target" "-Dexportflow.evidenceRoot=$taskEvidenceRoot" '-Dit.test=TaskResumeTest,AutomaticTaskProgressTest' '-Dtest=TaskManagementUnitTest,AssemblyRepairUnitTest,ProgressDeliveryUnitTest' verify
pnpm --dir frontend test:run src/features/export-tasks/__tests__/task-resume.test.tsx
pnpm --dir frontend build
```

后台服务均由测试夹具关闭，隔离schema/vhost/账号/Redis键精确回收；文件诊断根由夹具创建后保留，不触碰共享export-data。上述命令不测性能、容量、超时、延时或租约到期，SSE屏障仅确保事件先后、有限等待仅防挂死。
