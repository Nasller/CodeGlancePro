import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

val env: MutableMap<String, String> = System.getenv()
val dir: String = projectDir.parentFile.absolutePath
fun properties(key: String) = providers.gradleProperty(key)

plugins {
	id("java")
	alias(libs.plugins.kotlin)
	alias(libs.plugins.gradleIntelliJPlugin)
}

group = properties("pluginGroup").get()
version = properties("pluginVersion").get() + if(env.getOrDefault("snapshots","") == "true") "-SNAPSHOT"
else if(env.getOrDefault("PUBLISH_CHANNEL","") == "EAP") "-SNAPSHOT-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm")) else ""

dependencies {
	implementation("net.bytebuddy:byte-buddy-agent:1.14.18")
	intellijPlatform {
		create(properties("platformType"), properties("platformVersion"))

		// Plugin Dependencies. Uses `platformBundledPlugins` property from the gradle.properties file for bundled IntelliJ Platform plugins.
		bundledPlugins(properties("platformBundledPlugins").map { it.split(',') })
		// Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file for plugin from JetBrains Marketplace.
		plugins(properties("platformPlugins").map { it.split(',') })

		instrumentationTools()
		zipSigner()
	}
}

kotlin {
	jvmToolchain(properties("javaVersion").get().toInt())
}

repositories {
	mavenCentral()
	// IntelliJ Platform Gradle Plugin Repositories Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html
	intellijPlatform {
		defaultRepositories()
	}
}

intellijPlatform {
	pluginConfiguration {
		name = properties("pluginName").get()
		version = project.version.toString()

		ideaVersion {
			sinceBuild = properties("pluginSinceBuild")
			untilBuild = properties("pluginUntilBuild")
		}
	}
	sandboxContainer = layout.projectDirectory.dir(properties("sandboxDir").get())

	signing {
		certificateChainFile.set(File(env.getOrDefault("CERTIFICATE_CHAIN", "$dir/pluginCert/chain.crt")))
		privateKeyFile.set(File(env.getOrDefault("PRIVATE_KEY", "$dir/pluginCert/private.pem")))
		password.set(File(env.getOrDefault("PRIVATE_KEY_PASSWORD", "$dir/pluginCert/password.txt")).readText(Charsets.UTF_8))
	}

	publishing {
		token.set(env["PUBLISH_TOKEN"])
		channels.set(listOf(env["PUBLISH_CHANNEL"] ?: "default"))
	}
}

//intellijPlatformTesting {
//	runIde {
//		register("runIntelliJ") {
//			type = IntelliJPlatformType.IntellijIdeaUltimate
//			version = properties("platformVersion")
//			prepareSandboxTask {
//				defaultDestinationDirectory = project.layout.projectDirectory.dir(properties("sandboxDir"))
//			}
//		}
//	}
//}

tasks{
    runIde {
        systemProperties["idea.is.internal"] = true

        // Path to IDE distribution that will be used to run the IDE with the plugin.
        // ideDir.set(File("path to IDE-dependency"))
    }

	composedJar {
		manifest {
			attributes["Built-By"] = "Nasller"
			attributes["Premain-Class"] = "com.nasller.codeglance.agent.Main"
			attributes["Agent-Class"] = "com.nasller.codeglance.agent.Main"
			attributes["Can-Redefine-Classes"] = true
			attributes["Can-Retransform-Classes"] = true
		}
	}

	wrapper {
		gradleVersion = properties("gradleVersion").get()
		distributionType = Wrapper.DistributionType.ALL
	}
}