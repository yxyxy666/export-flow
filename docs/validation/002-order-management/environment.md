# 测试先行运行环境

本页保留0.4-test-03的历史工具及隔离环境事实，以下独立容器启动方式已停用，不是当前操作指引；当前开发和测试全部复用负责人提供的服务，见[共享环境](shared-services.md)。同日开发授权及Zod/API实施结果另见[0.4-api-01](api-implementation.md)，最新门禁结果见[当前汇总](green-results.md)。

日期：2026-10-11（Asia/Hong_Kong）。范围：订单管理规格0.4的测试准备与补充边界用例，证据版本`0.4-test-03`。关联[审批及进度](../../../specs/002-order-management/tasks.md#审批记录)、[测试结果](red-results.md)、[覆盖缺口](coverage-gaps.md)。本记录不表示产品验收或生产实施获批。

## 实际工具

| 项目 | 实际观察 |
| --- | --- |
| Node.js／Java | Node22.14.0；Temurin21.0.12.1+1，Windows |
| pnpm | 系统命令实际为11.19.0；本轮全部安装和测试明确使用`npx --yes pnpm@10.34.6`，未升级项目版本或系统工具 |
| Maven | 外部测试工具3.9.16；官方发行包SHA-512比对通过，工具位于`LOCALAPPDATA/ExportFlow/tools`；未生成正式后端Wrapper |
| 前端 | 锁定Vitest5.0.3、Testing Library16.3.3、MSW3.0.2及jsdom26.1.0；冻结安装退出码0；未安装业务阶段依赖 |
| 后端 | 测试harness使用Boot3.5.16 BOM、JUnit／AssertJ／Testcontainers及POI5.5.1；正式`backend/pom.xml`仍为占位 |
| Docker | Docker Desktop29.7.2，desktop-linux；本轮启动引擎后观察到三个示例容器healthy；未修改共享数据库、队列、策略或凭据 |

## 真实隔离检查

测试通过Testcontainers新建随机端口的MySQL和RabbitMQ容器，不连接示例项目服务；MySQL使用`export_flow_test`数据库，RabbitMQ使用`/export-flow`。测试用户名与口令仅为测试容器的合成配置。镜像按本机实际RepoDigest固定，同一批次不追随可变标签：

- MySQL：`mysql@sha256:3466ba4a4828aa8d46fb7c3bc16b67b781c98413cf4ea0fac6feaa6e881faa26`。
- RabbitMQ：`rabbitmq@sha256:606d8c0d6b3c18d1da9afc53bc7cdb2a8d5486df91b5a9830e9e07626c9ae281`。

四项实际检查通过：MySQL8.4系列连接、UTF8字符及UTC会话、READ COMMITTED与真实`SKIP LOCKED`；RabbitMQ持久发布确认和未确认消息重交付；测试临时目录原子移动及关闭后读取；Java真实回环HTTP请求。工具检查容器在try-with-resources中关闭；业务夹具按用例创建资源并在AfterEach中依次关闭，本轮结束后按Testcontainers标签检查无残留容器。它们只证明测试工具和基础能力，不证明生产SQL、拓扑验证或业务一致性。

## 已处理的工具问题

1. Docker Hub无法拉取`testcontainers/ryuk:0.12.0`。保留失败诊断后中止该轮测试；本机重跑仅在当前PowerShell进程设置`TESTCONTAINERS_RYUK_DISABLED=true`，并保留显式容器关闭。没有修改Docker全局配置，也没有跳过数据库或消息检查。网络恢复后可去掉此环境变量使用默认Ryuk。
2. 本机Java默认`java.io.tmpdir`为Windows短路径，创建NIO pipe出现`Invalid argument: connect`。切换SelectorProvider未解决；使用测试harness的`target`完整路径作为`jdk.net.unixdomain.tmpdir`后，真实HTTP检查通过。该设置只在测试POM中，子进程测试继承它，不修改生产配置或系统临时目录。
3. Vitest的可选浏览器mock依赖声明MSW2 peer，安装MSW3会给出peer警告。本期使用`msw/node`，真实GET/POST拦截的两个工具测试通过；没有声称未使用的浏览器mock模式兼容。MSW3监听选项使用实际类型声明的`onUnhandledFrame`。

实际命令、退出码和报告位置见[测试结果](red-results.md)。测试容器已配置MySQL2CPU/2GiB、RabbitMQ1CPU/1GiB及consumer_timeout600000；真实工具容器启动检查通过。性能程序已按参考环境编写并显式启用试运行，但入口缺失，硬件采样、五次文件/API性能度量及目标浏览器实际验证未执行，不将本记录作为成功标准的达标证据。
