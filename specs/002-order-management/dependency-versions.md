# 依赖精确版本与调查记录

**日期**：2026-10-07定稿，2026-10-11实施复核。**状态**：测试与开发均已获授权，前端锁定依赖和正式后端POM/Wrapper已落实；前端门禁和后端质量专项已执行，完整回归/性能仍在进行。调查时未安装的历史事实保留在[环境](../../docs/validation/002-order-management/environment.md)，当前以[实施进度](../../docs/validation/002-order-management/implementation-progress.md)为准。关联[决策](decision-record.md)、[计划](plan.md)、[研究](research.md)、[ADR-0009](../../docs/adr/0009-engineering-runtime-and-progress-notification.md)。

## 前端

现有React19.3.0、Ant Design6.6.5、Router7.18.4、Vite8.3.3、TypeScript7.0.2和现有类型/plugin版本均沿用锁文件；Node22.14.0、pnpm10.34.6。新增顶层依赖使用下列精确版本，不写^或~；传递依赖由pnpm-lock.yaml锁定。

| 包 | 定稿版本 | 元数据依据／兼容条件 |
| --- | --- | --- |
| @tanstack/react-query | 5.104.1 | [npm](https://registry.npmjs.org/@tanstack%2freact-query/5.104.1)，React18/19 |
| react-hook-form | 7.89.0 | [npm](https://registry.npmjs.org/react-hook-form/7.89.0)，React19、Node>=18 |
| zod | 4.6.5 | [npm](https://registry.npmjs.org/zod/4.6.5) |
| @hookform/resolvers | 5.9.1 | [npm](https://registry.npmjs.org/@hookform%2fresolvers/5.9.1)，RHF>=7.55、Zod4 |
| vitest | 5.0.3 | [npm](https://registry.npmjs.org/vitest/5.0.3)，Vite8、Node>=22.12（22分支） |
| @testing-library/react | 16.3.3 | [npm](https://registry.npmjs.org/@testing-library%2freact/16.3.3)，React19、DOM10 |
| @testing-library/dom | 10.4.2 | [npm](https://registry.npmjs.org/@testing-library%2fdom/10.4.2)，显式满足测试库peer |
| @testing-library/user-event | 14.6.7 | [npm](https://registry.npmjs.org/@testing-library%2fuser-event/14.6.7) |
| @testing-library/jest-dom | 7.0.1 | [npm](https://registry.npmjs.org/@testing-library%2fjest-dom/7.0.1)，Node>=22、DOM10 |
| msw | 3.0.2 | [npm](https://registry.npmjs.org/msw/3.0.2)，Node>=22.12、TS>=5.9 |
| jsdom | 26.1.0 | [npm](https://registry.npmjs.org/jsdom/26.1.0)，Node>=18；未选30.1.2，因为它要求22.22.2而本机为22.14.0 |

上述库采用MIT许可；msw仍使用网络层测试替身，不能替代真实后端测试。未选可选canvas、GraphQL或浏览器E2E插件。测试阶段只增加测试所需依赖；业务库在第二次批准后的生产实施阶段安装。无现存测试要迁移，但实施前须按锁定版本API编写用例。

组件/集成测试使用msw/node的setupServer拦截网络请求，按现有测试文件清单准备，不额外生成浏览器service worker或生产替代后端。

## 后端与工具

工程坐标com.exportflow:export-flow:0.2.0-SNAPSHOT，Java21；使用Spring Boot parent/BOM3.5.16，Maven3.9.16，与[正式POM](../../backend/pom.xml)及[模块启动说明](../../backend/README.md)一致。Boot管理组件不自行覆盖单个传递版本；本次直接读取了[Boot BOM](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/3.5.16/spring-boot-dependencies-3.5.16.pom)，以下值来自该POM或独立坐标。

| 坐标／组件 | 定稿版本 | 来源与说明 |
| --- | --- | --- |
| org.springframework.boot:spring-boot-starter-web/test/amqp/actuator | 3.5.16 | [Boot元数据](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/maven-metadata.xml)；保持已接受Boot3主版本 |
| Spring Framework / Spring AMQP | 6.2.19 / 3.2.12 | Boot BOM管理，不独立升级 |
| org.mybatis.spring.boot:mybatis-spring-boot-starter | 3.0.5 | [元数据](https://repo.maven.apache.org/maven2/org/mybatis/spring/boot/mybatis-spring-boot-starter/maven-metadata.xml)，使用其传递MyBatis版本 |
| org.flywaydb:flyway-core / flyway-mysql | 11.7.2 | Boot BOM；MySQL专用模块必须同时引用 |
| com.mysql:mysql-connector-j | 9.7.0 | Boot BOM；连接目标仍MySQL8.4，不代表服务器升级 |
| Jackson BOM | 2.21.4 | Boot管理JSON；无第二JSON框架 |
| org.apache.poi:poi-ooxml | 5.5.1 | [元数据](https://repo.maven.apache.org/maven2/org/apache/poi/poi-ooxml/maven-metadata.xml)，SXSSF与事件读取，不额外显式引入重复POI版本 |
| JUnit Jupiter / AssertJ | 5.12.2 / 3.27.7 | Boot测试依赖管理 |
| Testcontainers（历史测试准备） | 原1.21.4 | 2026-10-11按负责人要求复用既有服务，正式及harness POM已删除三个未使用直接依赖；Boot BOM保留的版本管理不表示运行时使用 |
| Maven Surefire / Failsafe | 3.5.6 / 3.5.6 | Boot管理；单元test，集成verify |
| org.codehaus.mojo:build-helper-maven-plugin | 3.6.1 | 正式工程汇入tests/backend测试辅助源；harness用于测试准备，未注册造数工具源 |
| com.diffplug.spotless:spotless-maven-plugin | 3.10.3 | [元数据](https://repo.maven.apache.org/maven2/com/diffplug/spotless/spotless-maven-plugin/maven-metadata.xml)，格式器显式锁定下行版本 |
| com.google.googlejavaformat:google-java-format | 1.28.0 | [POM](https://repo.maven.apache.org/maven2/com/google/googlejavaformat/google-java-format/1.28.0/google-java-format-1.28.0.pom)，适配Java21的固定维护版本，运行兼容在获批工具验证时检查 |
| org.apache.maven.plugins:maven-checkstyle-plugin | 3.6.0 | [元数据](https://repo.maven.apache.org/maven2/org/apache/maven/plugins/maven-checkstyle-plugin/maven-metadata.xml)，内置google_checks.xml |
| com.puppycrawl.tools:checkstyle | 10.26.1 | 显式插件依赖，原默认9.3处理本期Java记录语法内部报错；升级后相同google_checks.xml通过，见[review](../../docs/validation/002-order-management/code-review.md) |
| com.github.spotbugs:spotbugs-maven-plugin | 4.10.4.1 | [元数据](https://repo.maven.apache.org/maven2/com/github/spotbugs/spotbugs-maven-plugin/maven-metadata.xml)，高置信高优先级检查 |
| org.jacoco:jacoco-maven-plugin | 0.8.15 | [元数据](https://repo.maven.apache.org/maven2/org/jacoco/jacoco-maven-plugin/maven-metadata.xml)，报告必需，无百分比阈值 |
| Maven | 3.9.16 | [元数据](https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/maven-metadata.xml)，选择3.9维护分支，不选3.10主线 |
| org.apache.maven.plugins:maven-wrapper-plugin | 3.3.4 | [元数据](https://repo.maven.apache.org/maven2/org/apache/maven/plugins/maven-wrapper-plugin/maven-metadata.xml)，only-script模式生成正式Wrapper，Maven发行包固定3.9.16并记录官方校验摘要 |

主框架/POI/MyBatis/Flyway/Maven采用Apache-2.0，JUnit EPL-2.0、AssertJ Apache-2.0、Testcontainers MIT、JaCoCo EPL-2.0、SpotBugs LGPL-2.1；Connector/J采用GPL-2.0及Universal FOSS Exception。有效POM、插件传递树与许可证摘要在获批安装后记录，源码中的版本不依赖远程latest。此次元数据存在性和peer范围核对不等于全部依赖图、构建、启动或服务器兼容测试已经通过。

另读取[POI5.5.1源码包](https://repo.maven.apache.org/maven2/org/apache/poi/poi-ooxml/5.5.1/poi-ooxml-5.5.1-sources.jar)，核对SXSSFSheet.flushRows(int)与flushBufferedData()公开API；本期逐块刷行及缓冲采用这两个方法，最终workbook.write只完整封装一次。未运行实际文件验证。

## 中间件

复用mysql:8.4、rabbitmq:3.13-management-alpine、redis:7.4-alpine，不升级共享容器。2026-10-11测试准备已解析并固定实际MySQL/RabbitMQ镜像digest，创建独立测试容器完成工具检查，见[环境记录](../../docs/validation/002-order-management/environment.md)。标签并非不可变身份；本期未使用Redis测试容器，不能把共享容器healthy当业务证据。
