# Repository Guidelines

## 项目概览
`CodeGlancePro` 是一个基于 Gradle Kotlin DSL 的多模块 IntelliJ Platform 插件项目，目标是为 IntelliJ IDEA 系列 IDE 提供增强版代码缩略图与滚动条体验。仓库采用“根模块负责打包与公共入口，子模块负责平台适配”的结构：

- `src\main\kotlin` 与 `src\main\java`：根模块主逻辑，包含 minimap 渲染、面板注入、滚动条联动、监听器、动作以及 Java Agent 入口。
- `core\src\main\kotlin`：公共配置、UI 组件、工具类、国际化与颜色方案等共享能力。
- `rider\src\main\kotlin`：Rider 平台特有扩展。
- `clion\src\main\kotlin`：CLion 平台特有扩展。
- `src\main\resources\META-INF` 及各模块 `META-INF`：插件描述文件与按语言/平台拆分的扩展声明。

这个仓库已经存在用户本地调试痕迹和未提交改动，代理在操作时必须默认“最小改动、避免误伤、不要重置用户工作区”。

## 目录与模块职责
建议先按下面的职责理解代码，再决定落点：

- `src\main\kotlin\com\nasller\codeglance\render`：缩略图渲染核心，例如 `MainMinimap.kt`、`FastMainMinimap.kt`、`ScrollState.kt`、`MarkState.kt`。
- `src\main\kotlin\com\nasller\codeglance\panel`：面板、滚动条与交互 UI，例如 `GlancePanel.kt`、`panel\scroll\ScrollBar.kt`。
- `src\main\kotlin\com\nasller\codeglance\listener`：编辑器、VCS、滚动条等监听逻辑。
- `src\main\kotlin\com\nasller\codeglance\extensions`：动作提供器与语言高亮访问器。
- `core\src\main\kotlin\com\nasller\codeglance\config`：设置项、配置服务、设置页与枚举。
- `core\src\main\resources\messages`：国际化资源，新增可见文案时优先同步这里。
- `core\src\main\resources\colorSchemes`：颜色方案默认值。
- `rider\src\main\resources\META-INF`、`clion\src\main\resources\META-INF`：平台特有扩展声明。
- `src\test\kotlin`：当前已有少量测试，至少已存在 `render\ScrollStateTest.kt`，后续测试可优先贴近对应模块放置。

以下目录视为生成物或本地环境产物，默认不作为修改目标，也不作为评审重点：

- `build\`
- `out\`
- `idea-sandbox\`
- `rider-sandbox\`
- `clion-sandbox\`
- `.gradle\`
- `.kotlin\`
- `.intellijPlatform\`

## 开发环境与基础约束
- 操作系统按 Windows 处理，命令优先使用 PowerShell 7。
- 文件路径和命令示例统一使用反斜杠 `\`。
- Java 版本以 `gradle.properties` 中的 `javaVersion = 21` 为准。
- Gradle Wrapper 版本以 `gradle.properties` 中的 `gradleVersion = 9.4.1` 为准。
- 平台版本当前为 `2026.1`，根模块使用 `IU`，同时组合 `core`、`rider`、`clion` 子模块。
- 仓库文档、注释、说明性输出优先使用中文；代码标识符保持现有英文命名风格。

## 常用命令
在仓库根目录执行：

- `.\gradlew.bat build`：构建所有模块并生成插件产物。
- `.\gradlew.bat runIde`：启动默认 IntelliJ IDEA sandbox。
- `.\gradlew.bat runRider`：启动 Rider sandbox。
- `.\gradlew.bat runClion`：启动 CLion sandbox。
- `.\gradlew.bat test`：执行 IntelliJ Platform 测试任务。

执行命令约束：

- 用户明确禁止时，不得运行任何 Gradle 测试命令。

## 代码搜索与修改策略
代理在这个仓库中工作时，优先遵循以下顺序：

1. 先看 `git status --short`，确认工作区是否已有用户改动。
2. 先做最小范围检索，再决定是否读取完整文件。
3. 优先按职责目录定位，避免跨模块盲改。
4. 修改共享行为时，先判断应该落在根模块还是 `core`，不要把平台特化逻辑塞进共享层。
5. 修改语言或平台支持时，同时检查对应 `META-INF\*.xml` 扩展声明是否需要同步。

推荐的排查入口：

- 缩略图渲染或滚动同步问题：优先看 `src\main\kotlin\com\nasller\codeglance\render` 与 `src\main\kotlin\com\nasller\codeglance\panel\scroll`。
- 配置项、设置页、颜色方案问题：优先看 `core\src\main\kotlin\com\nasller\codeglance\config` 和 `core\src\main\resources\messages`。
- 语言标记、高亮访问器问题：优先看 `src\main\kotlin\com\nasller\codeglance\extensions\visitor` 以及 `rider\`、`clion\` 对应 visitor。
- 插件装配或平台兼容性问题：优先看根目录 `build.gradle.kts`、`settings.gradle.kts`、各模块 `build.gradle.kts` 和 `META-INF` 描述文件。
- Agent 注入或生命周期相关问题：优先看 `src\main\java\com\nasller\codeglance\agent`。

## 编码规范
- Kotlin 为主，保持现有风格，`.kt` 与 `.kts` 文件继续使用制表符缩进。
- 包名保持全小写，例如 `com.nasller.codeglance`。
- 类名使用 `PascalCase`，方法和属性使用 `camelCase`，常量使用 `UPPER_SNAKE_CASE`。
- 优先复用 IntelliJ Platform API，不要轻易引入新依赖。
- 新增复杂逻辑时允许少量中文注释，但注释必须解释“为什么”，不要解释显而易见的“做了什么”。
- 除非确认是共享能力，否则不要把 Rider 或 CLion 特有逻辑上提到 `core`。
- 涉及可见文本时，优先检查是否应写入 `messages\CodeGlanceBundle.properties` 或 `CodeGlanceBundle_zh.properties`。

## 变更联动规则
以下类型的修改通常不是单点变更，代理必须检查联动面：

- 新增或调整配置项：
  - 检查 `CodeGlanceConfig.kt`
  - 检查 `CodeGlanceConfigService.kt`
  - 检查 `CodeGlanceConfigurable.kt`
  - 检查是否需要补充国际化文案或默认值

- 修改滚动条、缩略图渲染或显示状态：
  - 检查 `ScrollBar.kt`
  - 检查 `ScrollState.kt`
  - 检查 `GlancePanel.kt`
  - 检查 `MainMinimap.kt`、`FastMainMinimap.kt`、`BaseMinimap.kt`

- 新增语言或平台扩展：
  - 检查对应 visitor 类
  - 检查 `src\main\resources\META-INF\codeglancepro-*.xml`
  - 检查 `rider\src\main\resources\META-INF` 或 `clion\src\main\resources\META-INF`

- 修改插件元数据或构建逻辑：
  - 检查根目录 `build.gradle.kts`
  - 检查 `gradle.properties`
  - 检查 `settings.gradle.kts`
  - 必要时同步 README 中的对外说明

## 测试与验证原则
虽然项目已接入 `TestFrameworkType.Platform`，但测试基建仍偏轻量，验证策略应按改动类型区分：

- 文档改动：只做内容复查，不运行 Gradle。
- 纯资源或文案改动：优先做文件级复查，必要时说明未执行运行时验证。
- 逻辑改动：优先补充或更新临近模块测试，例如放在 `src\test\kotlin` 或对应模块 `src\test\kotlin`。
- UI 或 IDE 交互改动：如果用户允许，优先使用对应 sandbox 做人工验证，而不是只依赖单元测试。

除非用户明确要求或任务本身需要，不要为了“走流程”执行重量级 Gradle 任务。答复中应明确写出“已执行”与“未执行”的验证项，避免模糊表述。

## 提交与 PR 规范
- 提交信息保持简短、祈使式、聚焦单一变更，长度尽量控制在 50 个字符以内。
- PR 描述至少包含：变更摘要、影响模块、验证方式、关联问题。
- 涉及 UI 变化时，优先附截图或录屏。
- 如果这次没有运行测试，要在 PR 或交付说明中明确写明原因。

## 安全与本地环境注意事项
这个仓库存在明显的本地环境配置，修改时必须谨慎：

- 根目录 `build.gradle.kts` 中存在本地 `-javaagent` 路径，视为开发机配置，除非用户明确要求，不要改动、更不要泛化提交。
- 签名证书、私钥、密码文件与 `PUBLISH_TOKEN` 都属于敏感信息，禁止写入仓库。
- 不要提交本地 sandbox 输出、缓存目录、IDE 配置目录或任何机器特定路径。
- 修改发布、签名、渠道、EAP 相关逻辑前，必须先确认影响范围，因为这些配置会同时影响 IDEA、Rider、CLion 三条运行链路。

## 代理协作禁区
以下行为默认禁止，除非用户明确要求：

- 回滚、覆盖或清理用户已有未提交改动。
- 修改 `build\`、`out\`、sandbox 目录中的生成结果。
- 在没有充分理由的情况下升级平台版本、Gradle 版本或新增依赖。
- 为了验证一个小改动而执行全量重构或全量构建。
- 把机器私有路径、调试参数、证书文件、发布令牌带入提交。

## 交付前检查清单
在结束一次改动前，至少自检以下内容：

- 改动是否落在正确模块。
- 是否误碰用户已有修改文件。
- 是否同步了必要的资源、配置或 `META-INF` 描述文件。
- 是否明确说明了验证范围，以及哪些测试没有执行。
- 是否把生成物、本地路径或敏感配置带进了改动。