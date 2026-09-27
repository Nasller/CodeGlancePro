# CodeGlance Pro

[English](README.md) | [简体中文](README_CN.md)

[![Version](https://img.shields.io/jetbrains/plugin/v/18824-codeglance-pro.svg)](https://plugins.jetbrains.com/plugin/18824-codeglance-pro)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/18824-codeglance-pro.svg)](https://plugins.jetbrains.com/plugin/18824-codeglance-pro)

CodeGlance Pro 为 JetBrains IDE 编辑器增加交互式代码缩略图。它可以替代或辅助原始编辑器滚动条，渲染语法颜色和 IDE 高亮，显示 VCS/错误标记，支持悬停预览代码，并且可以分别配置主编辑器、Diff 查看器、控制台输出、预览编辑器和未分类编辑器的行为。

## 与 CodeGlance 的主要差异

- 可以隐藏原始滚动条和错误条，让缩略图成为主要导航区域。
- 可以右键点击缩略图或编辑器滚动条，快速调整常用配置，无需打开完整设置页。
- 支持渲染 markup 高亮、错误条高亮、VCS 行高亮、光标行高亮、选区以及语言配色方案。
- 支持在缩略图悬停位置附近预览代码。
- 支持在缩略图中直接渲染 MARK、注释、书签标记。
- 可以按编辑器类型分别配置缩略图宽度，并支持在分屏模式下自动计算宽度。
- 可以独立支持主编辑器、Diff 编辑器、预览编辑器、控制台编辑器和未分类编辑器。

## 使用方式

- 使用 **Ctrl-Shift-G** 切换当前编辑器的缩略图显示状态。
- 通过 **Settings | Other Settings | CodeGlance Pro** 打开完整设置页。
- 右键点击缩略图或右侧编辑器 gutter，打开快速配置菜单。
- 拖动缩略图中的视窗区域，可以滚动编辑器。
- 点击缩略图空白区域，可以跳转到对应代码位置。按住 **Shift** 点击时，会扩展当前选区。
- 拖动缩略图的宽度调整区域，可以改变宽度。当宽度锁定、启用“悬停滚动条显示缩略图”或编辑器处于分屏模式时，宽度拖拽会被禁用。
- 启用代码预览后，鼠标悬停在缩略图上可以显示代码预览弹窗。

## 设置说明

### 通用设置

| 设置项 | 默认值 | 功能说明 | 使用建议 |
|---|---:|---|---|
| Pixels Per Line | `4` | 控制一个编辑器可视行在缩略图中占用多少像素。值越小，可显示的代码越多；值越大，缩略图结构越清晰。 | 超大文件可使用 `1` 或 `2`；希望结构更清楚时使用 `3` 或 `4`。 |
| Editor Size | `Proportional` | 控制缩略图高度计算方式。`Proportional` 使用配置的每行像素比例。`Fit` 会压缩文档，让长文件尽可能适配当前编辑器可见高度。 | 想看完整文件概览时使用 `Fit`；希望行间距稳定时使用 `Proportional`。 |
| Render Style | `Clean` | 控制字符栅格化方式。`Clean` 使用更简单的权重，外观更干净且通常更快。`Accurate` 使用字符上半部/下半部权重，形状更接近文本纹理。 | 日常建议使用 `Clean`；想要更密集、更像文本的效果时可尝试 `Accurate`。 |
| Alignment | `Right` | 控制缩略图显示在编辑器右侧还是左侧。 | 如果 IDE 布局或阅读习惯更适合左侧导航，可以选择 `Left`。 |
| Click Type | `Code Position` | 控制缩略图点击如何映射到编辑器。`Code Position` 按缩略图渲染内容定位。`Mouse Position` 按鼠标在编辑器内容中的相对位置定位。 | 折叠、软换行较多时建议保留 `Code Position`；想要更像滚动条的比例跳转时使用 `Mouse Position`。 |
| Jump to position on | `Mouse Down` | 控制点击何时触发跳转：`None`、`Mouse Down` 或 `Mouse Up`。 | 只想拖动不想点击跳转时用 `None`；想先预览再跳转时可用 `Mouse Up`。 |
| Move Only | `false` | 只滚动到目标位置，不移动光标。 | 希望浏览代码时保留当前光标位置，可以启用。 |
| Min lines count | `0` | 显示或渲染缩略图的最小总行数阈值。 | 只想在较大文件中显示缩略图时，可以调高。 |
| Max lines count | `20000` | 显示或渲染缩略图的最大总行数阈值。 | 如果超大文件渲染成本过高，可以调低。 |
| Out Range Empty | `true` | 文件行数超出范围时，保留缩略图区域但渲染为空。关闭后，超出范围的编辑器会被视为禁用缩略图。 | 想保持布局稳定时启用；想完全隐藏超出范围的缩略图时关闭。 |
| Editor Kind | `MAIN_EDITOR`, `PREVIEW`, `DIFF` | 选择哪些 JetBrains 编辑器类型启用缩略图。 | 需要时可添加 `CONSOLE` 或 `UNTYPED`；不希望显示的位置可以移除对应类型。 |
| Use Empty Minimap | `CONSOLE` | 对选中的编辑器类型使用空白缩略图渲染器。它仍保留滚动、视窗、标记和覆盖层，但不绘制完整代码文本。 | 适合控制台输出，或语法渲染噪声较多、成本较高的视图。 |
| Widths | Main `110`, Diff `50`, Untyped `50`, Console `50`, Preview `50` | 按编辑器类型分别设置缩略图宽度。 | 可以在设置页调整，也可以直接拖动缩略图宽度调整区域。 |
| Lock | `false` | 禁止通过拖拽调整缩略图宽度。 | 宽度配置稳定后可以启用，避免误操作。 |
| Disable language extension name | `ipynb` | 逗号分隔的文件扩展名列表，匹配后不会注入缩略图。 | 示例：`ipynb,log,csv`。扩展名不需要写点号。 |
| Delay show minimap on scrollbar hover | `0 ms` | 启用“悬停滚动条显示缩略图”时，控制缩略图出现前的延迟。 | 如果鼠标经过编辑器边缘时容易误触，可以适当增大。 |

### 视窗设置

视窗是缩略图上表示当前编辑器可见区域的矩形。

| 设置项 | 默认值 | 功能说明 |
|---|---:|---|
| Viewport Color | `A0A0A0` | 视窗矩形填充色。 |
| Viewport Border Color | `00FF00` | 视窗矩形边框色。 |
| Viewport Border Thickness | `0` | 边框厚度，范围 `0` 到 `4`；`0` 表示不显示边框。 |

当当前编辑器主题下视窗对比度不足时，可以调整这些选项。

### 标记设置

标记功能可以把重要注释、region、方法注解和书签以可读标签的形式渲染在缩略图中。

| 设置项 | 默认值 | 功能说明 | 使用建议 |
|---|---:|---|---|
| Enable Markers render | `true` | 启用注释、region、注解标记渲染。 | 如果标记文字让缩略图过于拥挤，可以关闭。 |
| Enable Bookmarks Marker render | `true` | 将书签描述显示为缩略图标记。 | 给 IDE 书签添加描述后，可把缩略图当作可视索引使用。 |
| Markers regex | `\bMARK:(?: -)?(?=\s|$)|#?region\b` | 使用正则表达式识别注释标记。 | 可以按项目约定改成 `TODO:`、`SECTION:` 或其他自定义标签。 |
| Markers font scale | `3.0` | 控制标记标签字体大小。 | 需要更清晰时调大；标记重叠太多时调小。 |
| Markers method names | 空 | 高亮 Java、Kotlin、Scala、Dart、C#、C 和 C++ 中配置的方法或函数声明名称。 | 每行填写一个名称；填写 `*` 可高亮所有支持的声明。使用语言 PSI 识别，不会匹配调用和任意同名标识符。 |
| Markers method annotation | `androidx.compose.runtime.Composable` | 换行分隔的方法注解名称，被识别的方法声明会显示标记。 | 每行填写一个注解；填写 `*` 可高亮所有带注解的方法声明。建议使用全限定名，也支持简单名称。 |

### 选项设置

| 设置项 | 默认值 | 功能说明 | 使用建议 |
|---|---:|---|---|
| Disabled by default | `false` | 编辑器默认禁用缩略图。 | 如果只想在少数文件中手动开启 CodeGlance Pro，可以启用。 |
| Show quick hide button | `true` | 在单个主编辑器中显示快速显示/隐藏操作。 | 可通过快速菜单调整；启用悬停显示模式时该按钮不可用。 |
| Hide Original ScrollBar And ErrorStripes | `false` | 隐藏 IDE 原始垂直滚动条和错误条，由 CodeGlance Pro 提供可见导航区域。 | 想让编辑器边缘更简洁，并依赖缩略图覆盖层时启用。 |
| Show Minimap on scrollbar hover | `false` | 默认隐藏缩略图，鼠标悬停到原始滚动条区域时再显示。 | 可在快速菜单启用，适合更紧凑的编辑器布局。该功能要求右侧对齐，并且默认禁用插件时不可用。 |
| Enable HiDPI Scale | `true` | 按显示器缩放比例渲染缩略图图像，同时保持布局宽度使用逻辑像素。 | HiDPI 显示器建议保持启用；只有需要旧的 1x 渲染行为时才关闭。 |
| VCS Highlight | `true` | 在缩略图区域显示 VCS 行标记，例如新增、修改、删除区域。 | 查看本地变更或代码审查时很有用。 |
| Filter Markup Highlight | `true` | 显示 filtered editor markup，包括调试、检查等 IDE 提供的高亮。 | 如果诊断覆盖层太多，可以关闭。 |
| Markup Highlight | `true` | 显示 editor markup，例如搜索结果、光标下标识符等。 | 如果临时编辑器高亮影响观察代码结构，可以关闭。 |
| ErrorStripes full line highlight | `true` | 在合适情况下将错误条高亮扩展为缩略图整行高亮。 | 可在快速菜单切换，用于增强或弱化诊断提示。 |
| Another full line highlight | `false` | 将其他 markup 高亮扩展为缩略图整行高亮。 | 只有偏好强烈整行色带时建议启用。 |
| Syntax Highlight | `true` | 渲染缩略图文本时使用编辑器配色方案。出于性能考虑，超大文件会跳过部分语法覆盖逻辑。 | 想要更简单的单色缩略图或排查渲染成本时可以关闭。 |
| Open TwoSides Diff | `true` | 在两侧 Diff 查看器的两个编辑器中都添加缩略图。 | 如果 Diff 视图横向空间不足，可以关闭。 |
| Open ThreeSides Diff | `true` | 在三侧 Diff 查看器中添加缩略图。 | 如果三侧 Diff 过于拥挤，可以关闭。 |
| Open ThreeSides DiffMiddle | `false` | 在三侧 Diff 的中间编辑器中也添加缩略图。 | 需要导航 base/中间面板时启用。 |
| Experimental: Use FastMinimap For Main Editor | `true` | 对非控制台、非未分类编辑器使用增量快速渲染器。 | 日常建议保持启用；需要和经典渲染器对比行为时可关闭。 |
| Show code lens on minimap hover | `true` | 鼠标悬停缩略图时，显示附近代码及相关 highlighter 信息的预览弹窗。 | 可在快速菜单切换。把鼠标移动到缩略图内容上并短暂停留即可触发。 |
| Mouse wheel move code lens | `false` | 代码预览弹窗打开时，允许使用鼠标滚轮移动预览目标位置。 | 想不滚动编辑器就检查附近代码时可以启用。 |
| Automatically calculate width in splitter mode | `true` | 主编辑器分屏且空间有限时，自动减小缩略图宽度。 | 分屏编辑时建议保持启用；偏好固定配置宽度时可以关闭。 |

## 快速配置菜单

右键点击缩略图或编辑器 gutter，可以访问常用配置：

- 仅在鼠标悬停原始滚动条时显示缩略图。
- 将错误条高亮显示为整行。
- 将其他 markup 高亮显示为整行。
- 在分屏模式下自动计算宽度。
- 显示或隐藏单文件快速隐藏按钮。
- 高亮可用时，配置 IDE 高亮级别和 next-error 行为。
- 启用或禁用代码预览，以及鼠标滚轮移动预览目标。

## EAP 版本

如果你正在使用任意 IDEA 系列 IDE 的 EAP 版本，可以添加 EAP 插件仓库：`https://plugins.jetbrains.com/plugins/eap/18824` 或 `https://plugins.jetbrains.com/plugins/eap/list`。

更多细节请参考 JetBrains 文档：https://www.jetbrains.com/help/idea/managing-plugins.html#repos