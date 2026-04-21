# Repository Guidelines

## Project Overview

`CodeGlancePro` is a multi-module IntelliJ Platform plugin project based on Gradle Kotlin DSL. Its goal is to provide an enhanced code minimap and scrollbar experience for IntelliJ IDEA-based IDEs.

The repository follows a structure where the **root module handles packaging and common entry points**, while **submodules handle platform-specific adaptations**:

* `src\main\kotlin` and `src\main\java`: Core logic of the root module, including minimap rendering, panel injection, scrollbar synchronization, listeners, actions, and Java Agent entry.
* `core\src\main\kotlin`: Shared capabilities such as configuration, UI components, utilities, internationalization, and color schemes.
* `rider\src\main\kotlin`: Rider-specific extensions.
* `clion\src\main\kotlin`: CLion-specific extensions.
* `src\main\resources\META-INF` and module-level `META-INF`: Plugin descriptors and extension declarations split by language/platform.

This repository already contains local debugging traces and uncommitted changes. Agents must assume:

* **Make minimal changes**
* **Avoid unintended side effects**
* **Do not reset the user’s workspace**

---

## Directory and Module Responsibilities

Understand responsibilities before modifying code:

* `src\main\kotlin\com\nasller\codeglance\render`: Core minimap rendering (e.g., `MainMinimap.kt`, `FastMainMinimap.kt`, `ScrollState.kt`, `MarkState.kt`)
* `src\main\kotlin\com\nasller\codeglance\panel`: Panels, scrollbars, and UI interaction (e.g., `GlancePanel.kt`, `panel\scroll\ScrollBar.kt`)
* `src\main\kotlin\com\nasller\codeglance\listener`: Editor, VCS, and scrollbar listeners
* `src\main\kotlin\com\nasller\codeglance\extensions`: Action providers and language highlight visitors
* `core\src\main\kotlin\com\nasller\codeglance\config`: Settings, services, configuration UI, enums
* `core\src\main\resources\messages`: i18n resources (update when adding visible text)
* `core\src\main\resources\colorSchemes`: Default color schemes
* `rider\src\main\resources\META-INF`, `clion\src\main\resources\META-INF`: Platform-specific extensions
* `src\test\kotlin`: Existing tests (e.g., `render\ScrollStateTest.kt`)

The following directories are considered generated or local artifacts and should not be modified:

* `build\`
* `out\`
* `idea-sandbox\`
* `rider-sandbox\`
* `clion-sandbox\`
* `.gradle\`
* `.kotlin\`
* `.intellijPlatform\`

---

## Development Environment and Constraints

* Assume Windows OS; use PowerShell 7 for commands
* Use backslashes `\` in paths and examples
* Java version: `21` (see `gradle.properties`)
* Gradle version: `9.4.1`
* Platform version: `2026.1`
* Root module uses `IU` and combines `core`, `rider`, `clion`

---

## Common Commands

Run in the repository root:

* `.\gradlew.bat build` → Build all modules and generate plugin artifacts
* `.\gradlew.bat runIde` → Launch IntelliJ IDEA sandbox
* `.\gradlew.bat runRider` → Launch Rider sandbox
* `.\gradlew.bat runClion` → Launch CLion sandbox
* `.\gradlew.bat test` → Run IntelliJ Platform tests

Constraints:

* Do **not** run Gradle test commands if the user explicitly forbids it

---

## Code Search and Modification Strategy

Follow this order:

1. Run `git status --short` to check for user changes
2. Perform minimal search before reading full files
3. Locate by responsibility directory (avoid blind cross-module edits)
4. For shared logic, decide between root module vs `core`
5. For language/platform changes, verify corresponding `META-INF\*.xml`

Recommended entry points:

* Rendering/scroll issues → `render`, `panel\scroll`
* Config/settings/color issues → `core\config`, `messages`
* Language highlighting → `extensions\visitor`, plus `rider`/`clion`
* Plugin assembly/compatibility → `build.gradle.kts`, `settings.gradle.kts`, `META-INF`
* Agent/lifecycle → `src\main\java\...\agent`

---

## Coding Conventions

* Kotlin-first; keep existing style
* Use tabs in `.kt` and `.kts`
* Package names lowercase (`com.nasller.codeglance`)
* Classes: `PascalCase`; methods/fields: `camelCase`; constants: `UPPER_SNAKE_CASE`
* Prefer IntelliJ Platform APIs; avoid new dependencies
* Comments should explain **why**, not obvious behavior
* Do not move platform-specific logic into `core` unless clearly shared
* For UI text, check `CodeGlanceBundle.properties`

---

## Change Impact Rules

Changes often require updates in multiple places:

* **Config changes**

  * `CodeGlanceConfig.kt`
  * `CodeGlanceConfigService.kt`
  * `CodeGlanceConfigurable.kt`
  * i18n + defaults

* **Scrollbar/minimap changes**

  * `ScrollBar.kt`
  * `ScrollState.kt`
  * `GlancePanel.kt`
  * `MainMinimap.kt`, `FastMainMinimap.kt`, `BaseMinimap.kt`

* **New language/platform**

  * Visitor classes
  * `META-INF\codeglancepro-*.xml`
  * Rider/CLion META-INF

* **Build/metadata**

  * `build.gradle.kts`
  * `gradle.properties`
  * `settings.gradle.kts`
  * README if needed

---

## Testing and Validation

* No need to generate or run tests

---

## Commit and PR Guidelines

* Keep commit messages short, imperative, ≤50 chars
* PR must include:

  * Summary
  * Affected modules
  * Validation method
  * Related issues
* Include screenshots for UI changes
* If tests were not run, explicitly state why

---

## Security and Local Environment Notes

Be cautious of local configuration:

* `build.gradle.kts` contains local `-javaagent` paths → do not modify unless requested
* Certificates, keys, passwords, `PUBLISH_TOKEN` → never commit
* Do not commit sandbox outputs, caches, or machine-specific paths
* Be careful with publishing/signing/EAP logic (affects IDEA, Rider, CLion)

---

## Agent Restrictions

Forbidden unless explicitly requested:

* Reverting or overwriting user changes
* Modifying generated directories
* Upgrading platform/Gradle or adding dependencies without reason
* Running full builds for minor validation
* Committing private paths, debug configs, or secrets

---

## Pre-Delivery Checklist

Before finishing:

* Changes are in the correct module
* No accidental modification of user changes
* Resources/config/META-INF updated if needed
* Validation scope clearly stated
* No generated files, local paths, or sensitive data included
