CodeGlance Pro (Merge CodeGlance And CodeGlance3)
==========

## This is an unofficial fork of the excellent CodeGlance plugin, which unfortunately no longer seems to be maintained.

#### Main differences compared to CodeGlance
- Fix Some Bug
- Two MiniMap Support
- Config Change Right
- Git Line Support

Building using Gradle
====================
```
git clone https://github.com/mgziminsky/Minimap-for-Jetbrains.git Minimap
cd MiniMap
# run the tests
./gradlew test

# build the plugin and install it in the sandbox then start idea
./gradlew runIde

# build a release
./gradlew buildPlugin

```
Running from source in IntelliJ
===================
Import the gradle project and run the `runIde` task.


Show/Hide or Enable/Disable Minimap
===================
* **Ctrl-Shift-G** to toggle glance.
* Settings > Other Settings > CodeGlance Pro