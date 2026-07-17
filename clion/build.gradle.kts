plugins {
	id("org.jetbrains.intellij.platform.module")
	alias(libs.plugins.kotlin)
}

repositories {
	mavenCentral()

	intellijPlatform {
		defaultRepositories()
	}
}

dependencies {
	intellijPlatform {
		clion(providers.gradleProperty("clionPlatformVersion"))
		pluginComposedModule(implementation(project(":core")))
		bundledPlugins("org.jetbrains.plugins.clion.radler")
		plugins(providers.gradleProperty("clionPlugins").map { it.split(',') })
	}
}