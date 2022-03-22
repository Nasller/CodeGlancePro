CodeGlance Pro (Merge CodeGlance And CodeGlance3)
==========

## This is an unofficial fork of the excellent CodeGlance plugin, which unfortunately no longer seems to be maintained.

#### Main differences compared to CodeGlance
- Works correctly with virtual space enabled
- Minimap can be used as a scrollbar

-------------

Latest build: https://github.com/mgziminsky/Minimap-for-Jetbrains/releases

Jetbrains IDE plugin that displays a zoomed out overview, or minimap, similar to the one found in Sublime into the editor pane. The minimap allows for quick scrolling letting you jump straight to sections of code.

 - Works with both light and dark themes using your customized colors for syntax highlighting.
 - Color rendering using IntelliJ's tokenizer
 - Scrollable!
 - Embedded into editor window

![Dracula](https://raw.github.com/mgziminsky/Minimap-for-Jetbrains/master/pub/example.png)


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
The result will be saved as build/distributions/MiniMap-{version}.zip


Running from source in IntelliJ
===================
Import the gradle project and run the `runIde` task.


Show/Hide or Enable/Disable Minimap
===================
* **Ctrl-Shift-G** to toggle minimap.
* Settings > Other Settings > CodeGlance
