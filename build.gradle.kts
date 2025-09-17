import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
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
	implementation("net.bytebuddy:byte-buddy:1.15.10")
	implementation("net.bytebuddy:byte-buddy-agent:1.15.10")
	intellijPlatform {
		create(properties("platformType"), properties("platformVersion"))
		pluginComposedModule(implementation(project(":core")))
		pluginComposedModule(runtimeOnly(project(":rider")))
		pluginComposedModule(runtimeOnly(project(":clion")))

		// Plugin Dependencies. Uses `platformBundledPlugins` property from the gradle.properties file for bundled IntelliJ Platform plugins.
		bundledPlugins(properties("platformBundledPlugins").map { it.split(',') })
		// Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file for plugin from JetBrains Marketplace.
		plugins(properties("platformPlugins").map { it.split(',') })
        // Module Dependencies. Uses `platformBundledModules` property from the gradle.properties file for bundled IntelliJ Platform modules.
        bundledModules(properties("platformBundledModules").map { it.split(',') })

		zipSigner()
        testFramework(TestFrameworkType.Platform)
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

intellijPlatformTesting {
	runIde {
		register("runRider") {
			type = IntelliJPlatformType.Rider
			version = properties("riderPlatformVersion")
			sandboxDirectory = project.layout.projectDirectory.dir(properties("riderSandboxDir").get())
			plugins {
				plugins(properties("defaultPlugins").map { it.split(',') })
			}
			task {
				systemProperties["idea.is.internal"] = true
				jvmArgs(
					"-XX:+AllowEnhancedClassRedefinition",
				)
			}
		}
		register("runClion") {
			type = IntelliJPlatformType.CLion
			version = properties("clionPlatformVersion")
			sandboxDirectory = project.layout.projectDirectory.dir(properties("clionSandboxDir").get())
			plugins {
				plugins(properties("defaultPlugins").map { it.split(',') })
			}
			task {
				systemProperties["idea.is.internal"] = true
				jvmArgs(
					"-XX:+AllowEnhancedClassRedefinition",
				)
			}
		}
	}
}

tasks{
    runIde {
        systemProperties["idea.is.internal"] = true
	    systemProperties["idea.kotlin.plugin.use.k2"] = true
	    jvmArgs(
		    "-XX:+AllowEnhancedClassRedefinition",
	    )
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