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
		rider(providers.gradleProperty("riderPlatformVersion"))
		pluginModule(implementation(project(":core")))
	}
}

intellijPlatform {
	instrumentCode.set(false)
}