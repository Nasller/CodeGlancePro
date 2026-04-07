# Repository Guidelines

## Project Structure & Module Organization
`CodeGlancePro` is a multi-module IntelliJ Platform plugin built with Gradle Kotlin DSL. The root module contains shared plugin packaging and launch tasks, with main sources under `src\main\kotlin`, `src\main\java`, and `src\main\resources`. Feature-specific code is split into `core\`, `rider\`, and `clion\`, each with its own `build.gradle.kts` and `src\main\...` tree. Treat `build\`, `out\`, `idea-sandbox\`, `rider-sandbox\`, and `clion-sandbox\` as generated artifacts, not review targets.

## Build, Test, and Development Commands
Use PowerShell 7 and the Gradle wrapper from the repository root.

- `.\gradlew.bat build` builds all modules and produces plugin artifacts.
- `.\gradlew.bat runIde` launches the default IntelliJ sandbox from `idea-sandbox\`.
- `.\gradlew.bat runRider` runs the Rider-targeted sandbox.
- `.\gradlew.bat runClion` runs the CLion-targeted sandbox.
- `.\gradlew.bat test` executes the configured IntelliJ Platform test task.

Java 21 is required; project defaults are defined in `gradle.properties`.

## Coding Style & Naming Conventions
Follow the existing Kotlin-first style with tabs for indentation in `.kts` and Kotlin sources. Keep package names lowercase (for example `com.nasller.codeglance`), classes in `PascalCase`, methods and properties in `camelCase`, and constants in `UPPER_SNAKE_CASE`. Prefer small, focused classes in the relevant module instead of cross-module shortcuts. Reuse IntelliJ Platform APIs before adding new dependencies.

## Testing Guidelines
The build is wired to `TestFrameworkType.Platform`, but dedicated `src\test` source sets are currently minimal. Add tests close to the module they cover, using `src\test\kotlin` or `src\test\java`. Name test files `*Test` and align test class names with the production type, such as `GlancePanelTest`. Run `.\gradlew.bat test` before opening a PR; for UI-sensitive fixes, also verify behavior with `runIde`.

## Commit & Pull Request Guidelines
Recent history favors short, imperative subjects such as `fix package`, `change settings`, and version bumps like `2026.1`. Keep commit titles under 50 characters and scoped to one change. PRs should include a concise summary, affected modules, manual verification steps, linked issues, and screenshots or recordings for UI-visible changes.

## Security & Configuration Tips
Do not commit local agent paths, certificates, publish tokens, or sandbox output. Review `build.gradle.kts` before changing JVM args or signing settings, and keep machine-specific paths out of shared commits.
