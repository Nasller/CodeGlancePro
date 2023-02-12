import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val env: MutableMap<String, String> = System.getenv()
val dir: String = projectDir.parentFile.absolutePath
fun properties(key: String) = project.findProperty(key)?.toString() ?: ""

plugins {
	// Java support
	id("java")
	// Kotlin support
	id("org.jetbrains.kotlin.jvm") version "1.8.0"
	// Gradle IntelliJ Plugin
	id("org.jetbrains.intellij") version "1.13.0"
}

group = properties("pluginGroup")
version = properties("pluginVersion") + if(env.getOrDefault("snapshots","") == "true") "-SNAPSHOT" else ""

kotlin {
	jvmToolchain {
		languageVersion.set(JavaLanguageVersion.of(properties("javaVersion")))
	}
}

intellij {
	pluginName.set(properties("pluginName"))
	version.set(properties("platformVersion"))
	type.set(properties("platformType"))
	sandboxDir.set("${rootProject.rootDir}/" + properties("sandboxDir"))
	downloadSources.set(true)
	// Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file.
	plugins.set(properties("platformPlugins").split(',').map(String::trim).filter(String::isNotEmpty))
	// languagePlugins=com.intellij.zh:222.202
	env["languagePlugins"]?.let { plugins.add(it) }
}

tasks{
    runIde {
        systemProperties["idea.is.internal"] = true

        // Path to IDE distribution that will be used to run the IDE with the plugin.
        // ideDir.set(File("path to IDE-dependency"))
    }

	buildSearchableOptions {
		enabled = env["buildSearchableOptions.enabled"] == "true"
		jvmArgs("-Dintellij.searchableOptions.i18n.enabled=true")
	}

	jarSearchableOptions {
		include { it.name.contains(rootProject.name) }
	}

	signPlugin {
		certificateChainFile.set(File(env.getOrDefault("CERTIFICATE_CHAIN", "$dir/pluginCert/chain.crt")))
		privateKeyFile.set(File(env.getOrDefault("PRIVATE_KEY", "$dir/pluginCert/private.pem")))
		password.set(File(env.getOrDefault("PRIVATE_KEY_PASSWORD", "$dir/pluginCert/password.txt")).readText(Charsets.UTF_8))
	}

	publishPlugin {
		token.set(env["PUBLISH_TOKEN"])
	}

	patchPluginXml {
		version.set(project.version.toString())
		sinceBuild.set(properties("pluginSinceBuild"))
		untilBuild.set(properties("pluginUntilBuild"))
	}

	wrapper {
		gradleVersion = properties("gradleVersion")
		distributionType = Wrapper.DistributionType.ALL
	}

	properties("javaVersion").let {
		withType<JavaCompile> {
			sourceCompatibility = it
			targetCompatibility = it
			options.encoding = "UTF-8"
		}
		withType<KotlinCompile> {
			kotlinOptions.jvmTarget = it
		}
	}
}