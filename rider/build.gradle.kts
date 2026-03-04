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
		pluginComposedModule(implementation(project(":core")))
		javaCompiler("243.21565.192")
	}
}