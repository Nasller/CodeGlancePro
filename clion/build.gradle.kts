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
		pluginModule(implementation(project(":core")))
		bundledPlugins("com.intellij.cidr.lang")
	}
}

intellijPlatform {
	instrumentCode.set(false)
}