# 构建、测试与打包规则

## 已确定交付方式

依据[ADR-0009](../adr/0009-engineering-runtime-and-progress-notification.md)，当前采用本地自动化门禁，不新增托管CI、Compose或远程发布。交付为frontend/dist静态资源与backend/target可执行jar；依赖版本见[订单版本表](../../specs/002-order-management/dependency-versions.md)，更新版本遵守锁文件和有效POM记录。规则确认不授权初始化尚未获批的工程。

## 阶段与命令

规格批准后只生成测试工具/夹具/测试，前端运行pnpm --dir frontend test:run，后端测试准备运行mvn -f tests/backend/pom.xml test。正式工程缺失导致的编译阻塞按[测试规则](testing.md)登记，不算预期业务失败。

第二次门禁批准并完成实现后，门禁顺序为冻结依赖安装、前端test:run/typecheck/build、后端Wrapper verify。verify覆盖Surefire单元和Failsafe真实隔离集成、Spotless、Checkstyle、SpotBugs与JaCoCo报告。准确工作目录/命令在功能quickstart记录，命令设计不代表当前脚本存在。

Spotless检查格式、Checkstyle使用内置google_checks.xml；SpotBugs高置信高优先级问题为零。JaCoCo生成HTML/XML但不设置百分比阈值，以可观察AC覆盖和失败证据为门禁。不能通过跳过集成、降低断言或以编译错误代替红灯交付。

## 产物与证据

本地artifacts使用功能标识/执行时间命名，保存构建日志、JUnit XML、覆盖率、依赖树、版本与Git状态、产物SHA-256摘要；本地保留最近3次构建产物，失败诊断按功能记录。artifacts和大文件不提交版本控制，小型合成/脱敏摘要存docs/validation，引用实际命令和退出码。

本期不设计远程部署作业。产物删除只影响可重新构建的构建输出，不能删除运行目录中的成功Excel、任务记录、断点或原命令。性能指标独立按获批参考方案测量，不把构建机器的偶发耗时当性能验收。
