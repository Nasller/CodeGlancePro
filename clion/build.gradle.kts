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
		bundledPlugins("com.intellij.cidr.lang")
		pluginModule(implementation(project(":core")))
	}
}

intellijPlatform {
	instrumentCode.set(false)
}