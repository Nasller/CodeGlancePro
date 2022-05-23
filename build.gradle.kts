import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

fun properties(key: String) = project.findProperty(key).toString()

plugins {
    // Java support
    id("java")
    // Kotlin support
    id("org.jetbrains.kotlin.jvm") version "1.6.20"
    // Gradle IntelliJ Plugin
    id("org.jetbrains.intellij") version "1.5.2"
}

group = properties("pluginGroup")
version = properties("pluginVersion")

dependencies {
    testCompileOnly("org.testng:testng:6.8.5")
}

kotlin {
    jvmToolchain {
        (this as JavaToolchainSpec).languageVersion.set(JavaLanguageVersion.of(11))
    }
}

intellij {
    pluginName.set(properties("pluginName"))
    version.set(properties("platformVersion"))
    type.set(properties("platformType"))
//    sandboxDir.set("${rootProject.rootDir}/idea-sandbox")
    downloadSources.set(true)
//    sandboxDir.set("${rootProject.rootDir}/rider-sandbox")
//    sandboxDir.set("${rootProject.rootDir}/clion-sandbox")
//    sandboxDir.set("${rootProject.rootDir}/py-sandbox")
//    downloadSources.set(false)
    // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file.
    plugins.set(properties("platformPlugins").split(',').map(String::trim).filter(String::isNotEmpty))
}

tasks{
    runIde {
        systemProperties["idea.is.internal"] = true

        // Path to IDE distribution that will be used to run the IDE with the plugin.
        // ideDir.set(File("path to IDE-dependency"))
    }

    wrapper {
        gradleVersion = properties("gradleVersion")
        distributionType = Wrapper.DistributionType.ALL
    }

    patchPluginXml {
        version.set(properties("pluginVersion"))
        sinceBuild.set(properties("pluginSinceBuild"))
        untilBuild.set(properties("pluginUntilBuild"))
    }

    properties("javaVersion").let {
        withType<JavaCompile> {
            sourceCompatibility = it
            targetCompatibility = it
            options.encoding = "UTF-8"
        }
        withType<KotlinCompile> {
            kotlinOptions.jvmTarget = it
        }
    }

    buildSearchableOptions{
        enabled=false
    }
}