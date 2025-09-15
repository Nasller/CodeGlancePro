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
		create(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))
	}
}