# 贡献指南

欢迎提交 Issue 与 Pull Request！提交前请先阅读本指南。

## 开发与构建

- 环境：JDK 8 及以上 + 仓库内置 Gradle Wrapper，无需本机安装 Gradle。
- 构建：`./gradlew bootJar`；三平台发布包：`./gradlew buildJar`。
- 测试：`./gradlew test`；单个测试类：`./gradlew test --tests "com.grw.xiaobai.hybrid.llm.utils.EnumUtilTest"`。
- 启动开发实例：`java -jar build/libs/hybridLLM-v1.14_release_base.jar`，默认端口 8098。

## 项目分层

`controller → service（接口） + service/impl → manager（内存态状态/配置） → entity（POJO）`

- 系统无数据库：状态持久化于磁盘文件、运行态保存在 `manager/`。
- 前端为原生 JS + Bootstrap 5，静态资源直接放在 `src/main/resources/static/`，无 Node 构建链。

## 代码风格

- 遵循现有代码风格：类名 PascalCase、方法与变量 camelCase、常量与枚举值 UPPER_SNAKE_CASE。
- Lombok：简单 POJO 用 `@Data`，复杂对象用 `@Builder`，枚举用 `@Getter` + `@AllArgsConstructor`。
- 依赖注入优先使用 `@Resource`（javax）。
- 日志：`private static final Logger LOGGER = LoggerFactory.getLogger(ClassName.class)`，使用参数化日志，避免字符串拼接。
- 异常：业务错误抛 `BusinessLogicException`，参数校验错误抛 `InvalidParamException`（均继承 `BaseRuntimeException`）。
- 导入顺序：`java.*` → `javax.*` → `org.*` → `com.*`（本地优先，再次三方）。
- 避免魔法数字，优先使用枚举/常量；方法尽量控制在 50 行以内。

## 前端与多语言

- 界面文案通过 `src/main/resources/static/file/common/messages.js` 的 `t('key')` 取词，**任何新增 key 必须同时提供中英文**。
- 样式与现有类名风格保持一致，优先复用已有 CSS 变量（`--primary-color`、`--border-color` 等）。

## 提交与 Pull Request

- Commit message 采用约定式前缀：`feat:` / `fix:` / `refactor:` / `docs:` / `test:` / `build:`，描述用一句话英文（小写开头，不加句号）。
- 一个 PR 聚焦一个主题；涉及行为变更请在描述中说明动机与影响面。
- UI 变更请附截图；修复运行时 bug 请附复现步骤。
- PR 合入以仓库配置的默认分支为准。

## Issue 反馈

优先使用仓库提供的 Issue 模板（Bug / Feature）。报告 Bug 时请尽量附上：版本、操作系统、JDK 版本、复现步骤、服务端日志片段（`logs/` 目录）。

安全类问题请勿发公开 Issue，参见 [SECURITY.md](SECURITY.md)。
