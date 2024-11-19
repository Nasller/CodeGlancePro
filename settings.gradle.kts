@file:Suppress("UnstableApiUsage")

rootProject.name = "CodeGlancePro"
include(":core")
include(":rider")

dependencyResolutionManagement {
	repositories {
		maven("https://mirrors.tencent.com/nexus/repository/maven-public/")
		mavenLocal()
		mavenCentral()
	}
}