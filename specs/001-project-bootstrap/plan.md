# 实施计划：项目初始化与基础骨架

**分支建议**：`codex/001-project-bootstrap`（尚未创建） | **日期**：2026-10-04 | **规格**：[spec.md](spec.md)  
**状态**：待审批；本文件不代表实施授权。

## 摘要

建立 `frontend/` 和 `backend/` 两个工程边界。前端提供可运行的静态应用，后端只创建目录及职责占位文件。
页面内容只渲染中文名称，导航与业务逻辑分离；后续订单、任务、投递及 Worker 实现填入已分配的模块。

本期以文件清单固定职责，不固定尚未讨论的业务方法、协议字段、SQL、任务租约或消息参数。
业务文件无需定义空函数、假类或 `NotImplemented` 运行分支；“文件存在”与“功能实现”分别核对。

## 技术背景

| 项目 | 已确认依据与本期边界 |
| --- | --- |
| 前端 | React、TypeScript、Vite、Ant Design、React Router；Node.js 22 LTS、pnpm 10，见 [ADR-0002](../../docs/adr/0002-frontend-stack-and-test-scope.md) |
| 前端依赖 | 本期仅安装静态应用需要的 `react`、`react-dom`、`antd`、`react-router-dom`、TypeScript、Vite、React Vite 插件及类型包；具体兼容稳定版本实施时记录并锁定 |
| 后续前端依赖 | TanStack Query、React Hook Form、Zod 选型保留；本期没有服务端状态或表单，不初始化它们；测试依赖本期不安装 |
| 后端 | Java 21、Spring Boot 3、Maven Wrapper、MyBatis、Flyway 与中间件选型保留，本期不锁定或安装后端依赖，不生成 Wrapper，不启动服务 |
| 存储与集成 | 本期无数据库、消息、缓存、文件产物、API 或 SSE 连接 |
| 目标平台 | 简体中文；1280×720；支持范围沿用 [ADR-0002](../../docs/adr/0002-frontend-stack-and-test-scope.md) |
| 验证 | 无自动化测试；目录核对、前端类型检查、构建、开发与预览页面手工查看，见 [tasks.md](tasks.md) |
| 性能目标 | 无业务性能或导出指标；本期使用 [`SC-014`](../../docs/product/acceptance-criteria.md#sc-014) |

## 宪法检查

| 检查项 | 结论与依据 |
| --- | --- |
| 风险 | 中风险；静态页面及工程结构，不涉及数据和并发行为 |
| 产品和架构 | 路由沿用 [ADR-0002](../../docs/adr/0002-frontend-stack-and-test-scope.md)，职责遵循[前端](../../docs/rules/frontend.md)与[后端](../../docs/rules/backend.md)规则；名称同步产品基线 |
| 目录及函数草稿 | 本文设计完整；尚未实施，不代表审批 |
| 三份规格及附属文档审批 | 未批准；见 [tasks.md](tasks.md#审批记录) |
| 本轮测试要求 | 用户明确指定不涉及测试；见 [spec.md](spec.md#本轮用户明确指令与审批边界)，仅对本轮例外 |
| 测试阶段门禁与二次测试审批 | 本轮不适用；没有测试阶段，不填写为通过 |
| 实施门禁 | 用户明确批准当前三份核心规格及附属产物后，才可创建静态骨架；不授权业务实现 |

## 项目结构

### 文档（本轮已准备）

```text
specs/001-project-bootstrap/
├── spec.md                                                                   # 功能范围、需求与审批边界
├── plan.md                                                                   # 目录结构、逐文件职责与静态函数设计
├── tasks.md                                                                  # 实施任务、审批记录与验证进度
├── research.md                                                               # 架构依据、方案比较与候选决策
└── quickstart.md                                                             # 实施后的前端运行与静态核对步骤
```

没有业务数据模型或接口边界，因此不生成数据模型或 API 契约文件。目录、文件和静态函数设计集中在本文。

### 工程（审批后创建）

下面结构图在每个文件后标注主要用途；本期实现或职责占位的范围由后面的逐文件职责表说明。
前端实际实现文件需要完成静态应用运行所需的最小内容；前端业务及通用占位文件、后端占位文件只写中文职责注释或说明，不写可执行内容、不导入运行链。已有文档只修改相关索引。
`pnpm-lock.yaml` 是安装生成文件，不能手写或伪造。该树不包含测试目录或测试文件。

```text
.
├── .gitignore                                                                # 排除依赖、构建、日志、个人配置与将来临时文件，保留锁文件与职责占位文件
├── AGENTS.md                                                                 # 实施后把工程地图细化为 frontend/backend；原则不改
├── README.md                                                                 # 入口、已实现边界、规格链接、前端命令；明确后端不可运行
├── docs/validation/001-project-bootstrap/      # 实施后验证时生成，不提前伪造
│   ├── environment.md                                                        # 实际 Node、pnpm、依赖及浏览器版本、视口与运行模式；未验证浏览器的缺口
│   ├── commands.log                                                          # 实际冻结安装、类型检查与构建命令、退出码和必要诊断
│   ├── structure.md                                                          # 目录和逐文件职责核对结果、占位内容与导入边界
│   ├── navigation.md                                                         # 开发与预览的路由、菜单、标题、键盘及错误入口检查记录
│   ├── network.md                                                            # 无业务请求与无后端依赖的实际查看记录，区分开发工具连接
│   ├── orders.png                                                            # 1280×720 的订单文字占位页截图
│   ├── export-tasks.png                                                      # 1280×720 的任务文字占位页截图
│   └── not-found.png                                                         # 轻量 404 与返回入口截图
├── frontend/
│   ├── README.md                                                             # 模块职责、静态运行、依赖版本记录、占位边界和后续步骤
│   ├── package.json                                                          # 私有包；dev、typecheck、build、preview 脚本与必要依赖；无 test 脚本
│   ├── pnpm-lock.yaml                                                        # 初始化安装生成，固定实际依赖；后续使用冻结模式安装
│   ├── index.html                                                            # 简体中文 HTML、root 容器、入口模块与默认文档标题
│   ├── vite.config.ts                                                        # React 插件、开发与预览本机地址及端口；不配置 API 代理
│   ├── tsconfig.json                                                         # 引用 app 和 node 配置
│   ├── tsconfig.app.json                                                     # 严格模式、JSX、DOM、模块检测与无输出类型检查；包含 src，占位不导入
│   ├── tsconfig.node.json                                                    # Vite 配置的类型检查和项目引用所需选项
│   └── src/
│       ├── main.tsx                                                          # 找到 DOM 根节点并挂载应用
│       ├── vite-env.d.ts                                                     # Vite 客户端类型声明
│       ├── app/
│       │   ├── App.tsx                                                       # 组合应用级 Provider 与路由入口
│       │   ├── app-providers.tsx                                             # Ant Design 中文 locale、基础令牌；不创建 QueryClient 或后台连接
│       │   ├── router.tsx                                                    # 根重定向、两个业务路由、404 和路由错误出口；路由实例只创建一次
│       │   └── route-error-fallback.tsx                                      # 路由异常时显示安全提示及返回入口，不显示异常原文
│       ├── layouts/
│       │   ├── admin-layout.tsx                                              # 组合侧栏与主内容 Outlet；从路径派生选中状态和浏览器标题
│       │   └── sidebar-menu.tsx                                              # 用 Ant Design Menu 渲染两个导航项并响应合法选项
│       ├── pages/
│       │   └── not-found-page.tsx                                            # 轻量 404、返回订单管理链接；不是业务页
│       ├── features/
│       │   ├── orders/
│       │   │   ├── pages/order-management-page.tsx                           # 只展示订单管理文字
│       │   │   ├── api/orders-api.ts                                         # 订单接口的请求序列化、取消和 DTO 接收；接口契约后续确定
│       │   │   ├── types/order.types.ts                                      # 订单传输类型及枚举；与页面模型分开
│       │   │   ├── models/order-view-model.ts                                # DTO 到订单展示模型映射
│       │   │   ├── hooks/use-orders.ts                                       # 订单服务端状态、已提交查询及请求生命周期
│       │   │   ├── application/order-export-use-case.ts                      # 导出提交、结果不明处理和成功导航编排
│       │   │   └── components/
│       │   │       ├── order-filter-form.tsx                                 # 编辑中筛选表单与交互校验
│       │   │       ├── order-table.tsx                                       # 订单展示与跨页选择交互
│       │   │       └── export-config-dialog.tsx                              # 导出字段选择与提交确认
│       │   └── export-tasks/
│       │       ├── pages/task-management-page.tsx                            # 只展示任务管理文字
│       │       ├── api/export-tasks-api.ts                                   # 任务接口和同源下载地址边界
│       │       ├── types/export-task.types.ts                                # 任务传输类型与枚举
│       │       ├── hooks/use-export-tasks.ts                                 # 任务查询与未来按需实时连接生命周期
│       │       └── components/export-task-table.tsx                          # 任务列表、进度、动作的展示
│       └── shared/
│           ├── navigation/navigation.ts                                      # 两个业务路径、菜单名称及路由匹配；供路由、布局与页面共用，不依赖 app、layouts 或 features
│           ├── components/static-business-page.tsx                           # 两个页面共同使用的语义化文字标题
│           ├── api/http-client.ts                                            # 集中 HTTP 请求、取消、超时与协议边界；不在页面拼协议 URL
│           ├── api/api-error.ts                                              # 稳定错误类型及安全错误归一化
│           ├── types/page-result.ts                                          # 明确分页契约后的通用分页 DTO，不先猜字段
│           ├── formatters/date-time.ts                                       # UTC 协议时间与产品时区展示的转换
│           ├── formatters/money.ts                                           # 十进制定点金额的展示与校验，避免浮点业务计算
│           └── styles/global.css                                             # 根节点高度、布局间距、侧栏宽度、主区域与焦点基础样式
└── backend/
    ├── README.md                                                             # 逐层说明、包名前缀、占位边界；明确本期无可执行服务或 Maven 构建
    ├── pom.xml                                                               # 将来 Maven 工程、依赖和构建定义；本期只有 XML 注释，不能用于 Maven 命令
    ├── .mvn/wrapper/README.md                                                # 将来 Wrapper 生成位置与职责；后续生成真正 mvnw/mvnw.cmd，不创建假脚本
    └── src/main/
        ├── resources/
        │   ├── application.yml                                               # 将来环境变量和运行配置映射；本期只有 YAML 注释，无账号或连接地址
        │   └── db/migration/README.md                                        # 将来获批 Flyway 迁移位置；本期无建表或数据初始化
        └── java/com/exportflow/
            ├── ExportFlowApplication.java                                    # 将来 Spring Boot 启动入口
            ├── config/RuntimeConfiguration.java                              # 外层组合、配置注入和适配器装配
            ├── shared/api/ApiErrorResponse.java                              # 安全 HTTP 错误协议对象，字段后续确定
            ├── shared/api/ApiExceptionHandler.java                           # 传输边界错误映射，不泄露内部异常
            ├── orders/
            │   ├── api/OrderController.java                                  # 订单 HTTP 传输和结构校验，不直接访问数据库
            │   ├── application/OrderQueryService.java                        # 订单查询用例编排
            │   ├── domain/Order.java                                         # 订单领域模型与不变量，不共用 DTO 或数据库记录
            │   ├── port/OrderRepository.java                                 # 应用所依赖的订单读取端口
            │   └── infrastructure/persistence/
            │       ├── OrderMapper.java                                      # MyBatis 数据记录与 SQL 映射
            │       └── MyBatisOrderRepository.java                           # 实现订单读取端口，转换记录与领域模型
            ├── exporttasks/
            │   ├── api/ExportTaskController.java                             # 任务受理、查询和文件下载的 HTTP 边界
            │   ├── application/
            │   │   ├── CreateExportTaskService.java                          # 创建校验、幂等与同事务保存任务及 Outbox
            │   │   ├── ExportTaskQueryService.java                           # 查询 MySQL 权威任务信息
            │   │   └── DownloadExportFileService.java                        # 下载资格、元数据与安全文件访问编排
            │   ├── domain/ExportTask.java                                    # 任务领域状态及不变量，不引入未获批暂停或恢复状态
            │   ├── port/
            │   │   ├── ExportTaskRepository.java                             # 任务持久化与权威状态读取端口
            │   │   ├── ExportFileStorage.java                                # 安全文件读写与产物访问端口
            │   │   └── ExportProgressCache.java                              # 可重建的最新进度缓存端口，不代替 MySQL
            │   └── infrastructure/
            │       ├── persistence/ExportTaskMapper.java                     # 任务持久记录和 MyBatis 映射
            │       ├── persistence/MyBatisExportTaskRepository.java          # 任务持久化端口适配
            │       ├── file/LocalExportFileStorage.java                      # 本地文件端口适配与安全路径校验
            │       └── cache/RedisExportProgressCache.java                   # Redis 进度缓存适配
            ├── dispatch/
            │   ├── application/OutboxDispatchService.java                    # 发布领取、交接、退避与超时收口用例；具体协议后续批准
            │   ├── domain/CommandOutbox.java                                 # 待投递命令及投递状态的领域事实
            │   ├── port/CommandOutboxRepository.java                         # Outbox 事务读写与条件裁决端口
            │   ├── port/ExportCommandPublisher.java                          # 命令发布及可靠确认结果端口
            │   └── infrastructure/
            │       ├── persistence/CommandOutboxMapper.java                  # Outbox 持久记录与 SQL 映射
            │       ├── persistence/MyBatisCommandOutboxRepository.java       # Outbox 持久端口适配
            │       └── messaging/RabbitMqExportCommandPublisher.java         # RabbitMQ 发布确认与正确路由检查适配
            └── worker/
                ├── application/ExportCommandHandler.java                     # 消费后的任务核对、执行权判断和生成编排；执行恢复协议后续评审
                ├── port/ExportFileGenerator.java                             # 流式文件生成能力端口
                └── infrastructure/
                    ├── messaging/RabbitMqExportCommandListener.java          # 消费入口、消息解析与确认边界适配
                    └── excel/PoiExportFileGenerator.java                     # Apache POI SXSSF 流式生成适配
```

### 文件状态与职责

以下路径以仓库根目录为基准。`frontend/src/` 和 `backend/src/main/java/com/exportflow/` 内的短路径分别注明。
下表中的前端业务及通用占位文件、后端占位文件无本期函数、类、字段、类型或协议定义，只放中文职责、阶段边界及对应规格位置。
未来需要拆分 DTO、持久记录、领域值对象等文件时，在各功能规格中补充；本轮不冒充已经完成详细业务设计。

#### 根目录与前端配置

| 文件 | 本期内容与用途 | 函数 | 任务 |
| --- | --- | --- | --- |
| `.gitignore` | 排除依赖、构建、日志、个人配置与将来临时文件，保留锁文件与职责占位文件 | 无函数 | T-002 |
| `AGENTS.md` | 实施后把工程地图细化为 frontend/backend；原则不改 | 无函数 | T-006 |
| `README.md` | 入口、已实现边界、规格链接、前端命令；明确后端不可运行 | 无函数 | T-006 |
| `frontend/README.md` | 模块职责、静态运行、依赖版本记录、占位边界和后续步骤 | 无函数 | T-002、T-006 |
| `frontend/package.json` | 私有包；dev、typecheck、build、preview 脚本与必要依赖；无 test 脚本 | 无函数 | T-002 |
| `frontend/pnpm-lock.yaml` | 初始化安装生成，固定实际依赖；后续使用冻结模式安装 | 无函数 | T-002 |
| `frontend/index.html` | 简体中文 HTML、root 容器、入口模块与默认文档标题 | 无函数 | T-002 |
| `frontend/vite.config.ts` | React 插件、开发与预览本机地址及端口；不配置 API 代理 | 无自有函数，导出配置 | T-002 |
| `frontend/tsconfig.json` | 引用 app 和 node 配置 | 无函数 | T-002 |
| `frontend/tsconfig.app.json` | 严格模式、JSX、DOM、模块检测与无输出类型检查；包含 src，占位不导入 | 无函数 | T-002 |
| `frontend/tsconfig.node.json` | Vite 配置的类型检查和项目引用所需选项 | 无函数 | T-002 |
| `frontend/src/vite-env.d.ts` | Vite 客户端类型声明 | 无函数 | T-002 |

以下验证证据在实施后才生成，不是测试代码或当前已存在的结果，均无函数：

| 文件（相对 `docs/validation/001-project-bootstrap/`） | 内容 | 任务 |
| --- | --- | --- |
| `environment.md` | 实际 Node、pnpm、依赖及浏览器版本、视口与运行模式；未验证浏览器的缺口 | T-007 |
| `commands.log` | 实际冻结安装、类型检查与构建命令、退出码和必要诊断 | T-007 |
| `structure.md` | 目录和逐文件职责核对结果、占位内容与导入边界 | T-007 |
| `navigation.md` | 开发与预览的路由、菜单、标题、键盘及错误入口检查记录 | T-007 |
| `network.md` | 无业务请求与无后端依赖的实际查看记录，区分开发工具连接 | T-007 |
| `orders.png` | 1280×720 的订单文字占位页截图 | T-007 |
| `export-tasks.png` | 1280×720 的任务文字占位页截图 | T-007 |
| `not-found.png` | 轻量 404 与返回入口截图 | T-007 |

#### 前端实际实现（相对 `frontend/src/`）

| 文件 | 职责与边界 | 函数 | 任务 |
| --- | --- | --- | --- |
| `main.tsx` | 找到 DOM 根节点并挂载应用 | `mountApplication` | T-002 |
| `app/App.tsx` | 组合应用级 Provider 与路由入口 | `App` | T-003 |
| `app/app-providers.tsx` | Ant Design 中文 locale、基础令牌；不创建 QueryClient 或后台连接 | `AppProviders` | T-003 |
| `app/router.tsx` | 根重定向、两个业务路由、404 和路由错误出口；路由实例只创建一次 | `createAppRouter` | T-003 |
| `app/route-error-fallback.tsx` | 路由异常时显示安全提示及返回入口，不显示异常原文 | `RouteErrorFallback` | T-003 |
| `layouts/admin-layout.tsx` | 组合侧栏与主内容 Outlet；从路径派生选中状态和浏览器标题 | `AdminLayout` | T-003 |
| `layouts/sidebar-menu.tsx` | 用 Ant Design Menu 渲染两个导航项并响应合法选项 | `SidebarMenu`、局部 `handleMenuSelect` | T-003 |
| `features/orders/pages/order-management-page.tsx` | 只展示订单管理文字 | `OrderManagementPage` | T-003 |
| `features/export-tasks/pages/task-management-page.tsx` | 只展示任务管理文字 | `TaskManagementPage` | T-003 |
| `shared/components/static-business-page.tsx` | 两个页面共同使用的语义化文字标题 | `StaticBusinessPage` | T-003 |
| `pages/not-found-page.tsx` | 轻量 404、返回订单管理链接；不是业务页 | `NotFoundPage` | T-003 |
| `shared/navigation/navigation.ts` | 两个业务路径、菜单名称及路由匹配，作为导航元数据唯一来源；供路由、布局与页面共用，不依赖 app、layouts 或 features | `resolveNavigation`；静态配置与类型 | T-003 |
| `shared/styles/global.css` | 根节点高度、布局间距、侧栏宽度、主区域与焦点基础样式 | 无函数 | T-003 |

#### 前端业务及通用占位（相对 `frontend/src/`）

| 文件 | 未来职责（本期只写说明，无函数） | 任务 |
| --- | --- | --- |
| `features/orders/api/orders-api.ts` | 订单接口的请求序列化、取消和 DTO 接收；接口契约后续确定 | T-004 |
| `features/orders/types/order.types.ts` | 订单传输类型及枚举；与页面模型分开 | T-004 |
| `features/orders/models/order-view-model.ts` | DTO 到订单展示模型映射 | T-004 |
| `features/orders/hooks/use-orders.ts` | 订单服务端状态、已提交查询及请求生命周期 | T-004 |
| `features/orders/application/order-export-use-case.ts` | 导出提交、结果不明处理和成功导航编排 | T-004 |
| `features/orders/components/order-filter-form.tsx` | 编辑中筛选表单与交互校验 | T-004 |
| `features/orders/components/order-table.tsx` | 订单展示与跨页选择交互 | T-004 |
| `features/orders/components/export-config-dialog.tsx` | 导出字段选择与提交确认 | T-004 |
| `features/export-tasks/api/export-tasks-api.ts` | 任务接口和同源下载地址边界 | T-004 |
| `features/export-tasks/types/export-task.types.ts` | 任务传输类型与枚举 | T-004 |
| `features/export-tasks/hooks/use-export-tasks.ts` | 任务查询与未来按需实时连接生命周期 | T-004 |
| `features/export-tasks/components/export-task-table.tsx` | 任务列表、进度、动作的展示 | T-004 |
| `shared/api/http-client.ts` | 集中 HTTP 请求、取消、超时与协议边界；不在页面拼协议 URL | T-004 |
| `shared/api/api-error.ts` | 稳定错误类型及安全错误归一化 | T-004 |
| `shared/types/page-result.ts` | 明确分页契约后的通用分页 DTO，不先猜字段 | T-004 |
| `shared/formatters/date-time.ts` | UTC 协议时间与产品时区展示的转换 | T-004 |
| `shared/formatters/money.ts` | 十进制定点金额的展示与校验，避免浮点业务计算 | T-004 |

#### 后端配置与业务占位

| 文件 | 未来职责（除 README 外，本期只写职责注释） | 任务 |
| --- | --- | --- |
| `backend/README.md` | 逐层说明、包名前缀、占位边界；明确本期无可执行服务或 Maven 构建 | T-005 |
| `backend/pom.xml` | 将来 Maven 工程、依赖和构建定义；本期只有 XML 注释，不能用于 Maven 命令 | T-005 |
| `backend/.mvn/wrapper/README.md` | 将来 Wrapper 生成位置与职责；后续生成真正 mvnw/mvnw.cmd，不创建假脚本 | T-005 |
| `backend/src/main/resources/application.yml` | 将来环境变量和运行配置映射；本期只有 YAML 注释，无账号或连接地址 | T-005 |
| `backend/src/main/resources/db/migration/README.md` | 将来获批 Flyway 迁移位置；本期无建表或数据初始化 | T-005 |

以下 Java 路径相对 `backend/src/main/java/com/exportflow/`；全部仅中文注释，不声明类、方法或注解。

| 文件 | 未来职责 | 任务 |
| --- | --- | --- |
| `ExportFlowApplication.java` | 将来 Spring Boot 启动入口 | T-005 |
| `config/RuntimeConfiguration.java` | 外层组合、配置注入和适配器装配 | T-005 |
| `shared/api/ApiErrorResponse.java` | 安全 HTTP 错误协议对象，字段后续确定 | T-005 |
| `shared/api/ApiExceptionHandler.java` | 传输边界错误映射，不泄露内部异常 | T-005 |
| `orders/api/OrderController.java` | 订单 HTTP 传输和结构校验，不直接访问数据库 | T-005 |
| `orders/application/OrderQueryService.java` | 订单查询用例编排 | T-005 |
| `orders/domain/Order.java` | 订单领域模型与不变量，不共用 DTO 或数据库记录 | T-005 |
| `orders/port/OrderRepository.java` | 应用所依赖的订单读取端口 | T-005 |
| `orders/infrastructure/persistence/OrderMapper.java` | MyBatis 数据记录与 SQL 映射 | T-005 |
| `orders/infrastructure/persistence/MyBatisOrderRepository.java` | 实现订单读取端口，转换记录与领域模型 | T-005 |
| `exporttasks/api/ExportTaskController.java` | 任务受理、查询和文件下载的 HTTP 边界 | T-005 |
| `exporttasks/application/CreateExportTaskService.java` | 创建校验、幂等与同事务保存任务及 Outbox | T-005 |
| `exporttasks/application/ExportTaskQueryService.java` | 查询 MySQL 权威任务信息 | T-005 |
| `exporttasks/application/DownloadExportFileService.java` | 下载资格、元数据与安全文件访问编排 | T-005 |
| `exporttasks/domain/ExportTask.java` | 任务领域状态及不变量，不引入未获批暂停或恢复状态 | T-005 |
| `exporttasks/port/ExportTaskRepository.java` | 任务持久化与权威状态读取端口 | T-005 |
| `exporttasks/port/ExportFileStorage.java` | 安全文件读写与产物访问端口 | T-005 |
| `exporttasks/port/ExportProgressCache.java` | 可重建的最新进度缓存端口，不代替 MySQL | T-005 |
| `exporttasks/infrastructure/persistence/ExportTaskMapper.java` | 任务持久记录和 MyBatis 映射 | T-005 |
| `exporttasks/infrastructure/persistence/MyBatisExportTaskRepository.java` | 任务持久化端口适配 | T-005 |
| `exporttasks/infrastructure/file/LocalExportFileStorage.java` | 本地文件端口适配与安全路径校验 | T-005 |
| `exporttasks/infrastructure/cache/RedisExportProgressCache.java` | Redis 进度缓存适配 | T-005 |
| `dispatch/application/OutboxDispatchService.java` | 发布领取、交接、退避与超时收口用例；具体协议后续批准 | T-005 |
| `dispatch/domain/CommandOutbox.java` | 待投递命令及投递状态的领域事实 | T-005 |
| `dispatch/port/CommandOutboxRepository.java` | Outbox 事务读写与条件裁决端口 | T-005 |
| `dispatch/port/ExportCommandPublisher.java` | 命令发布及可靠确认结果端口 | T-005 |
| `dispatch/infrastructure/persistence/CommandOutboxMapper.java` | Outbox 持久记录与 SQL 映射 | T-005 |
| `dispatch/infrastructure/persistence/MyBatisCommandOutboxRepository.java` | Outbox 持久端口适配 | T-005 |
| `dispatch/infrastructure/messaging/RabbitMqExportCommandPublisher.java` | RabbitMQ 发布确认与正确路由检查适配 | T-005 |
| `worker/application/ExportCommandHandler.java` | 消费后的任务核对、执行权判断和生成编排；执行恢复协议后续评审 | T-005 |
| `worker/port/ExportFileGenerator.java` | 流式文件生成能力端口 | T-005 |
| `worker/infrastructure/messaging/RabbitMqExportCommandListener.java` | 消费入口、消息解析与确认边界适配 | T-005 |
| `worker/infrastructure/excel/PoiExportFileGenerator.java` | Apache POI SXSSF 流式生成适配 | T-005 |

## 本期函数与类型设计草稿

仅以下静态函数在本期实现；框架内部方法不展开。业务和后端占位文件均无本期函数，
不是尚未补齐的函数清单，也不能借文件名提前实现业务。后续规格必须补充签名、参数和调用关系。

### 自有类型与静态配置

| 类型或常量 | 文件 | 定义草稿 |
| --- | --- | --- |
| `BusinessPath` | `shared/navigation/navigation.ts` | 联合类型 `/orders`、`/export-tasks` |
| `NavigationItem` | 同上 | `path: BusinessPath`、`label: string`、`title: string`；本期 label/title 相同 |
| `NAVIGATION_ITEMS` | 同上 | 只读数组，两个条目；path 与标签唯一，不接受任意外部 URL |
| `AppProvidersProps` | `app/app-providers.tsx` | 必填 `children: ReactNode` |
| `SidebarMenuProps` | `layouts/sidebar-menu.tsx` | 可选 `selectedPath: BusinessPath`；必填 `onNavigate: (path: BusinessPath) => void` |
| `StaticBusinessPageProps` | `shared/components/static-business-page.tsx` | 必填 `title: string`，由共享导航元数据提供 |
| React 与根节点类型 | 依赖库 | `ReactElement`、`ReactNode` 来自 React，`Root` 来自 react-dom/client，不重复定义 |

### 逐函数签名与中文注释草稿

| 文件／函数 | 参数与返回值 | 中文注释：作用与目的 | 简短流程及调用关系 | 任务／验证 |
| --- | --- | --- | --- | --- |
| `main.tsx`：`mountApplication` | 必填 `container: HTMLElement`；返回 `Root` | 将静态应用挂载到指定 DOM 根节点，使运行入口与业务页面分离。 | 入口先确认 root 存在 → 调用 `createRoot(container)` → 渲染 `App` → 返回根实例；不发请求。 | T-002／VC-005 |
| `app/App.tsx`：`App` | 无参数；返回 `ReactElement` | 组合中文界面配置和唯一的应用路由入口。 | 渲染 `AppProviders` 包裹 `RouterProvider`；使用模块初始化时创建的路由实例，不在每次渲染重建。 | T-003／VC-001 |
| `app/app-providers.tsx`：`AppProviders` | `props: AppProvidersProps`；返回 `ReactElement` | 为静态页面统一设置 Ant Design 简体中文和基础主题。 | 配置 `ConfigProvider` 中文 locale 与基础令牌 → 渲染 children；无 QueryClient、请求或副作用。 | T-003／VC-001 |
| `app/router.tsx`：`createAppRouter` | 无参数；返回 `ReturnType<typeof createBrowserRouter>` | 集中定义应用路由，让菜单与页面沿用既定地址。 | 配置 `/` 的 `Navigate` 替换跳转、`AdminLayout` 子路由、两个页面及 `NotFoundPage`；子路由配置 `RouteErrorFallback` 以保留父布局，布局自身失败也使用安全恢复出口；调用 `createBrowserRouter`。 | T-003／VC-001、VC-002 |
| `shared/navigation/navigation.ts`：`resolveNavigation` | 必填 `pathname: string`；返回 `NavigationItem \| undefined` | 从当前路径派生导航项，避免菜单与标题分别维护。 | 去除末尾多余斜杠后按固定 path 查找 `NAVIGATION_ITEMS`；未知路径返回 undefined；不修改 URL、无外部调用。 | T-003／VC-001、VC-002 |
| `layouts/admin-layout.tsx`：`AdminLayout` | 无参数；返回 `ReactElement` | 提供统一侧栏和内容容器，使页面只关注自身内容。 | `useLocation` → `resolveNavigation` → 派生选中键与文档标题；`useEffect` 更新 `document.title`；`useNavigate` 提供导航回调；组合 Layout、`SidebarMenu` 与 `Outlet`。 | T-003／VC-001、VC-002 |
| `layouts/sidebar-menu.tsx`：`SidebarMenu` | `props: SidebarMenuProps`；返回 `ReactElement` | 渲染业务菜单并只允许导航至登记过的路径。 | 根据 `NAVIGATION_ITEMS` 配置 Menu，selectedPath 决定 selectedKeys；点击调用局部 `handleMenuSelect`。 | T-003／VC-001 |
| 同上：局部 `handleMenuSelect` | `event: { key: string }`；返回 `void` | 校验菜单键后通知父布局导航，防止任意键进入路由。 | 在固定导航数组中找到 key 对应项 → 有效才调用 `props.onNavigate(item.path)`；否则忽略。 | T-003／VC-001 |
| `shared/components/static-business-page.tsx`：`StaticBusinessPage` | `props: StaticBusinessPageProps`；返回 `ReactElement` | 复用两个页面的最小语义化标题，保持内容一致。 | 渲染包含 title 的 h1 或 Ant Design 标题；无额外数据、操作或函数调用。 | T-003／VC-001 |
| `features/orders/pages/order-management-page.tsx`：`OrderManagementPage` | 无参数；返回 `ReactElement` | 提供订单管理的路由占位内容，业务功能以后填充。 | 从 `shared/navigation/navigation.ts` 的订单导航项取得 title → 调用 `StaticBusinessPage`；不导入 app 或订单业务占位文件。 | T-003／VC-001、VC-004 |
| `features/export-tasks/pages/task-management-page.tsx`：`TaskManagementPage` | 无参数；返回 `ReactElement` | 提供任务管理的路由占位内容，任务功能以后填充。 | 从 `shared/navigation/navigation.ts` 的任务导航项取得 title → 调用 `StaticBusinessPage`；不导入 app 或任务业务占位文件。 | T-003／VC-001、VC-004 |
| `pages/not-found-page.tsx`：`NotFoundPage` | 无参数；返回 `ReactElement` | 未知地址显示轻量结果和明确返回入口。 | 渲染 Ant Design Result 404 与 `/orders` 的 Link；不创建独立 403/500 页面。 | T-003／VC-002 |
| `app/route-error-fallback.tsx`：`RouteErrorFallback` | 无参数；返回 `ReactElement` | 在路由渲染异常时给出安全的恢复入口，不泄露异常细节。 | 在现有容器中显示简短错误提示与 `/orders` 的普通同源链接，重新加载恢复；不输出异常原文。 | T-003／VC-002 |

导航用到的数组查找和 React 生命周期回调仅执行表中已描述的静态查找或标题同步，不承载额外业务函数。
前端和后端预留通用模块已经列入职责表，但因为本期没有调用方，不预先确定其函数或实现；
本期实际复用能力只有导航配置、导航匹配及 `StaticBusinessPage`。

### 调用链与依赖方向

```text
index.html
  → main.mountApplication
    → App
      → AppProviders（Ant Design 中文配置）
      → RouterProvider（唯一静态路由实例）
        → AdminLayout
          → shared/navigation.resolveNavigation → 选中菜单与文档标题
          → SidebarMenu（读取共享导航元数据）→ handleMenuSelect → onNavigate → Router
          → Outlet → OrderManagementPage 或 TaskManagementPage
            → shared/navigation.NAVIGATION_ITEMS → 标题 → StaticBusinessPage
          → 未知路由：NotFoundPage
        → 渲染异常：RouteErrorFallback
```

前端依赖：app 组合布局与页面；路由、布局、菜单与业务页面统一读取 `shared/navigation/navigation.ts` 的类型和导航元数据，页面使用共享标题组件。
shared 导航模块只定义静态配置、类型与路径匹配，不导入 app、layouts 或 features；layouts 和 features 不反向依赖 app，app 保持应用组合职责。
占位的业务 API、Hook 与用例均不被导入。
页面不手动拼装 API URL，路由地址来自导航元数据。无后端调用链。

未来后端依赖方向为：传输层 → 应用层 → 领域与端口；基础设施实现端口，外层配置负责装配。
域模型不依赖 Spring、MyBatis、Redis、RabbitMQ 或 POI。任务创建与投递分模块，但同事务边界由未来创建规格确定，
不能为了按目录分层拆开必须原子提交的记录。投递原则仍以 [ADR-0007](../../docs/adr/0007-outbox-dispatch-retry.md) 为准；
[ADR-0008](../../docs/adr/0008-worker-execution-lease-and-recovery.md) 为候选，不在本期获得实施授权。

## 配置、运行与验证方案

- 前端开发与预览仅监听 `127.0.0.1`，建议开发 5173、预览 4173；端口冲突直接报告，运行记录写实际配置。
- 开发脚本使用 Vite；`typecheck` 对两个 TypeScript 项目执行无输出检查，`build` 先检查类型再构建；`preview` 查看构建结果。
- 不复制示例项目代码、配置或凭据，不新增 Compose，不启动、停止或修改现有容器。
- 不创建假后端接口、空返回 JSON 或可下载演示文件；不对外宣称后端可运行。
- 占位 `.ts`、`.tsx`、`.java` 只含中文注释；占位 YAML/XML 使用相应注释语法；不把占位 POM 当成有效 Maven 工程。
- 常规工具链规则尚未制定，本计划仅提出该静态前端的最小命令，不制定全项目 CI、覆盖率、打包或发布策略。
- 本期不创建测试目录、测试配置、测试脚本或测试依赖。验收通过目录核对、类型检查、构建与页面查看；步骤见 [quickstart.md](quickstart.md)。

## 复杂度与后续扩展

| 项目 | 处理方式 |
| --- | --- |
| 本轮测试先行例外 | 用户本次明确要求；以静态验证代替，仅限定本轮，不修改通用治理文件 |
| 两个工程根目录 | 降低前后端配置耦合，沿用计划模板结构；获批后同步工程地图，不提前改变已有入口职责 |
| 业务文件预留较多 | 为满足用户确定文件及职责的要求，只放注释、不增加实际依赖或运行分支 |
| 后端暂不可运行 | 本轮结构准备的明确边界；后续初始化规格补齐 POM、Wrapper、启动入口、依赖与测试，不以空 POM 通过构建 |
| 没有新架构 ADR | 沿用已接受技术栈与依赖方向；没有不可逆或跨模块业务协议变化，包名与目录提案随规格审批 |

扩大范围时必须更新上游需求、验收、计划及任务并重新审批；后续只读页面、接口与后台导出均须恢复测试先行。
