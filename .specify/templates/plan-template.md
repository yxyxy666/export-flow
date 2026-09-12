# 实施计划：[功能]

**分支**：`[###-feature-name]` | **日期**：[日期] | **规格**：[链接]

**输入**：来自 `/specs/[###-feature-name]/spec.md` 的功能规格

**说明**：模型依据本模板填写计划；模板定义了计划阶段的执行结构。

## 摘要

[从功能规格提取：核心需求 + 研究得出的技术方案]

## 技术背景

<!-- 将本节替换为本项目和本功能的实际技术细节。下列结构仅用于指导迭代，不是固定要求。 -->

**语言/版本**：[例如 Python 3.11、Swift 5.9、Rust 1.75 或待澄清]

**主要依赖**：[例如 FastAPI、UIKit、LLVM 或待澄清]

**存储**：[如适用，例如 PostgreSQL、CoreData、文件或不适用]

**测试**：[例如 pytest、XCTest、cargo test 或待澄清]

**目标平台**：[例如 Linux 服务器、iOS 15+、WASM 或待澄清]

**项目类型**：[例如 library/cli/web-service/mobile-app/compiler/desktop-app 或待澄清]

**性能目标**：[领域指标，例如 1000 req/s、每秒 1 万行、60 fps 或待澄清]

**约束**：[领域约束，例如 p95 <200ms、内存 <100MB、支持离线或待澄清]

**规模/范围**：[领域规模，例如 1 万用户、100 万行代码、50 个页面或待澄清]

## 宪法检查

*门禁：必须在阶段 0 研究前通过，并在阶段 1 设计后重新检查。*

[根据 constitution 文件确定门禁]

## 项目结构

### 文档（本功能）

```text
specs/[###-feature]/
├── plan.md              # 本文件
├── research.md          # 阶段 0 输出
├── data-model.md        # 阶段 1 输出
├── quickstart.md        # 阶段 1 输出
├── contracts/           # 阶段 1 输出
└── tasks.md             # 阶段 2 输出
```

### 源代码（仓库根目录）
<!-- 将下面的占位目录树替换为本功能的实际结构；删除未使用的选项，并补充真实路径。最终计划中不得保留 Option 标签。 -->

```text
# [未使用时删除] 选项 1：单项目（默认）
src/
├── models/
├── services/
├── cli/
└── lib/

tests/
├── contract/
├── integration/
└── unit/

# [未使用时删除] 选项 2：Web 应用（检测到 frontend + backend 时）
backend/
├── src/
│   ├── models/
│   ├── services/
│   └── api/
└── tests/

frontend/
├── src/
│   ├── components/
│   ├── pages/
│   └── services/
└── tests/

# [未使用时删除] 选项 3：移动端 + API（检测到 iOS/Android 时）
api/
└── [同上面的 backend 结构]

ios/ 或 android/
└── [平台特定结构：功能模块、UI 流程和平台测试]
```

**结构决策**：[记录选定的结构，并引用上面列出的真实目录]

## 复杂度追踪

> **仅当宪法检查存在需要说明的违规项时填写。**

| 违规项 | 必要原因 | 未采用更简单方案的原因 |
|---|---|---|
| [例如：第 4 个项目] | [当前需求] | [为什么 3 个项目不足] |
| [例如：Repository 模式] | [具体问题] | [为什么直接访问数据库不足] |
