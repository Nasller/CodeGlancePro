# CodeGlance Pro

[English](README.md) | [简体中文](README_CN.md)

[![Version](https://img.shields.io/jetbrains/plugin/v/18824-codeglance-pro.svg)](https://plugins.jetbrains.com/plugin/18824-codeglance-pro)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/18824-codeglance-pro.svg)](https://plugins.jetbrains.com/plugin/18824-codeglance-pro)

CodeGlance Pro adds an interactive code minimap to JetBrains IDE editors. It can replace or complement the original editor scrollbar, render syntax colors and IDE highlights, show VCS/error markers, preview code on hover, and provide separate behavior for main editors, diff viewers, console output, preview editors, and untyped editors.

## Main differences compared to CodeGlance

- Hide the original scrollbar and error stripe when you want the minimap to become the primary navigation area.
- Right-click the minimap or editor scrollbar to adjust common options without opening the full settings page.
- Render markup highlights, error stripe highlights, VCS line highlights, caret line highlights, selections, and language color scheme information.
- Preview code around the hovered minimap position.
- Render MARK/comment/bookmark markers directly in the minimap.
- Configure minimap width per editor kind and optionally calculate width automatically in splitter mode.
- Support main editors, diff editors, preview editors, console editors, and untyped editors independently.

## Usage

- Toggle the minimap for the current editor with **Ctrl-Shift-G**.
- Open the full settings page from **Settings | Other Settings | CodeGlance Pro**.
- Right-click the minimap or the right editor gutter to open the quick configuration menu.
- Drag the minimap viewport to scroll the editor.
- Click the minimap background to jump to the corresponding code position. Hold **Shift** while jumping to extend the current selection.
- Drag the minimap resize gutter to change width. Width resizing is disabled when width is locked, when the minimap is shown only on scrollbar hover, or when the editor is in splitter mode.
- Hover the minimap to show a code preview popup when code preview is enabled.

## Settings

### General

| Setting | Default | What it does | How to use it |
|---|---:|---|---|
| Pixels Per Line | `4` | Controls how many minimap pixels represent one editor visual line. Smaller values show more code; larger values make the minimap easier to read. | Use `1` or `2` for very large files, `3` or `4` for clearer structure. |
| Editor Size | `Proportional` | Chooses how the minimap height is calculated. `Proportional` uses the configured pixels-per-line scale. `Fit` compresses the document so long files can fit into the visible editor height. | Use `Fit` when you prefer an overview of the whole file; use `Proportional` when you want stable line spacing. |
| Render Style | `Clean` | Controls how each character is rasterized. `Clean` uses simpler weights for a cleaner, faster look. `Accurate` uses character-specific top/bottom weights for a more text-like shape. | Use `Clean` for performance and readability; try `Accurate` if you want a denser text texture. |
| Alignment | `Right` | Places the minimap on the right or left side of the editor. | Use `Left` if your IDE layout or reading flow works better with navigation on the left. |
| Click Type | `Code Position` | Defines how a minimap click maps to the editor. `Code Position` maps by rendered minimap content. `Mouse Position` maps by the mouse's relative position in the editor content. | Keep `Code Position` for folded/soft-wrapped code; use `Mouse Position` when you want scrollbar-like proportional jumping. |
| Jump to position on | `Mouse Down` | Decides when a minimap click jumps: `None`, `Mouse Down`, or `Mouse Up`. | Use `None` if you only want dragging. Use `Mouse Up` if you often preview before committing a jump. |
| Move Only | `false` | Scrolls to the target position without moving the caret. | Enable it when you want navigation to preserve the current caret position. |
| Min lines count | `0` | Lower line-count threshold for showing or rendering the minimap. | Raise it if you only want the minimap for larger files. |
| Max lines count | `20000` | Upper line-count threshold for showing or rendering the minimap. | Lower it if extremely large files are too expensive to render. |
| Out Range Empty | `true` | When a file is outside the line-count range, keeps the minimap area but renders it empty. When disabled, the minimap is treated as disabled for that editor. | Enable it to keep layout stable; disable it to fully hide out-of-range minimaps. |
| Editor Kind | `MAIN_EDITOR`, `PREVIEW`, `DIFF` | Selects which JetBrains editor kinds receive a minimap. | Add `CONSOLE` or `UNTYPED` if you want minimaps there; remove kinds where the minimap is distracting. |
| Use Empty Minimap | `CONSOLE` | Uses an empty minimap renderer for selected editor kinds. It still provides scrolling, viewport, marks, and overlays without drawing full code text. | Useful for console output or views where syntax rendering is noisy or expensive. |
| Widths | Main `110`, Diff `50`, Untyped `50`, Console `50`, Preview `50` | Sets the minimap width per editor kind. | Adjust from the settings page or by dragging the minimap resize gutter. |
| Lock | `false` | Prevents drag resizing the minimap width. | Enable it after choosing widths you want to keep stable. |
| Disable language extension name | `ipynb` | Comma-separated file extensions where CodeGlance Pro should not inject a minimap. | Example: `ipynb,log,csv`. Use file extensions without dots. |
| Delay show minimap on scrollbar hover | `0 ms` | Delay before the minimap appears when hover-to-show mode is enabled. | Increase it to avoid accidental popups while moving across the editor. |

### Viewport

The viewport is the rectangle drawn on top of the minimap to represent the currently visible editor area.

| Setting | Default | What it does |
|---|---:|---|
| Viewport Color | `A0A0A0` | Fill color of the viewport rectangle. |
| Viewport Border Color | `00FF00` | Border color of the viewport rectangle. |
| Viewport Border Thickness | `0` | Border thickness from `0` to `4`; `0` disables the border. |

Use these options when you want stronger contrast against your current editor theme.

### Markers

Markers render important comments, regions, method annotations, and bookmarks as readable labels inside the minimap.

| Setting | Default | What it does | How to use it |
|---|---:|---|---|
| Enable Markers render | `true` | Enables comment/region/annotation marker rendering. | Disable it if marker text makes the minimap too busy. |
| Enable Bookmarks Marker render | `true` | Shows bookmark descriptions as minimap markers. | Add IDE bookmarks with descriptions, then use the minimap as a visual index. |
| Markers regex | `\bMARK:(?: -)?(?=\s|$)|#?region\b` | Detects comment markers by regular expression. | Customize it for conventions such as `TODO:`, `SECTION:`, or project-specific tags. |
| Markers font scale | `3.0` | Controls marker label size. | Increase for readability; decrease if labels overlap too much. |
| Markers method names | empty | Highlights configured function and method declaration names in Java, Kotlin, Scala, Dart, C#, C, and C++. | Add one name per line; use `*` to mark all supported declarations. Language PSI is used so calls and arbitrary identifiers are not matched. |
| Markers method annotation | `androidx.compose.runtime.Composable` | Newline-separated annotation names whose recognized method declarations should be marked. | Add one annotation per line; use `*` to mark all recognized annotated methods. Fully qualified names are preferred, and simple names are also accepted. |

### Options

| Setting | Default | What it does | How to use it |
|---|---:|---|---|
| Disabled by default | `false` | Starts editors with the minimap disabled. | Use this if you only want to enable CodeGlance Pro manually for selected files. |
| Show quick hide button | `true` | Shows the quick show/hide action for single main editors. | Available from the quick menu; disabled when hover-to-show mode is active. |
| Hide Original ScrollBar And ErrorStripes | `false` | Hides the IDE's original vertical scrollbar and error stripe, and lets CodeGlance Pro provide the visible navigation surface. | Enable it if you want a cleaner editor edge and rely on minimap overlays. |
| Show Minimap on scrollbar hover | `false` | Keeps the minimap hidden until you hover the original scrollbar area. | Enable it from the quick menu for a compact editor. This requires right alignment and is disabled when the plugin is disabled by default. |
| Enable HiDPI Scale | `true` | Renders minimap images using the display scale while keeping layout width in logical pixels. | Keep enabled on HiDPI displays; disable only if you need legacy 1x rendering behavior. |
| VCS Highlight | `true` | Shows VCS line markers such as changed/added/deleted regions in the minimap area. | Useful when reviewing local changes. |
| Filter Markup Highlight | `true` | Shows filtered editor markup, including debugging, inspections, and similar IDE-provided highlights. | Disable it if diagnostic overlays are too noisy. |
| Markup Highlight | `true` | Shows editor markup such as search results and identifiers under caret. | Disable it if transient editor highlights distract from code structure. |
| ErrorStripes full line highlight | `true` | Expands error stripe highlights to a full minimap line when appropriate. | Toggle from the quick menu when you want diagnostics to stand out more or less. |
| Another full line highlight | `false` | Expands other markup highlights to full minimap lines. | Enable only if you prefer strong full-width highlight bands. |
| Syntax Highlight | `true` | Uses the editor color scheme while rendering minimap text. For performance, syntax overlay logic is skipped for very large files. | Disable it for a simpler monochrome minimap or when chasing rendering cost. |
| Open TwoSides Diff | `true` | Adds minimaps to both editors in two-side diff viewers. | Disable if diff minimaps take too much horizontal space. |
| Open ThreeSides Diff | `true` | Adds minimaps to three-side diff viewers. | Disable if three-side diff views become too dense. |
| Open ThreeSides DiffMiddle | `false` | Adds a minimap to the middle editor in three-side diff viewers. | Enable when you need navigation in the base/middle pane too. |
| Experimental: Use FastMinimap For Main Editor | `true` | Uses the incremental fast renderer for non-console/non-untyped editor kinds. | Keep enabled for normal use; disable when comparing behavior with the classic renderer. |
| Show code lens on minimap hover | `true` | Shows a preview popup with nearby code and related highlighter information when hovering the minimap. | Toggle from the quick menu. Move the mouse over minimap content and pause briefly. |
| Mouse wheel move code lens | `false` | Lets the mouse wheel move the code preview popup target while the preview is open. | Enable if you inspect nearby code from the minimap without moving the editor. |
| Automatically calculate width in splitter mode | `true` | Reduces minimap width automatically when the main editor is split and space is limited. | Keep enabled for split editors. Disable if you prefer fixed configured width. |

## Quick configuration menu

Right-click the minimap or editor gutter to access frequently changed options:

- Show minimap only on scrollbar hover.
- Show error stripe highlights as full lines.
- Show other markup highlights as full lines.
- Automatically calculate width in splitter mode.
- Show or hide the single-file quick hide button.
- Configure IDE highlighting level and next-error behavior when highlighting is available.
- Enable or disable code preview and mouse-wheel preview movement.

## EAP Version
In case you are using an EAP version of any IDEA flavor,
just add the EAP channel: `https://plugins.jetbrains.com/plugins/eap/18824` or `https://plugins.jetbrains.com/plugins/eap/list`.
See JetBrains documentation for more details: https://www.jetbrains.com/help/idea/managing-plugins.html#repos