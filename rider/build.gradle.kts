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
	compileOnly(project(":core"))
	intellijPlatform {
		rider(providers.gradleProperty("riderPlatformVersion"))
		instrumentationTools()
	}
}