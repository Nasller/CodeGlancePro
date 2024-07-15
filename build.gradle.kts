
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

val env: MutableMap<String, String> = System.getenv()
val dir: String = projectDir.parentFile.absolutePath
fun properties(key: String) = project.findProperty(key)?.toString() ?: ""

plugins {
	id("java")
	alias(libs.plugins.kotlin)
	alias(libs.plugins.gradleIntelliJPlugin)
}

group = properties("pluginGroup")
version = properties("pluginVersion") + if(env.getOrDefault("snapshots","") == "true") "-SNAPSHOT"
else if(env.getOrDefault("PUBLISH_CHANNEL","") == "EAP") "-SNAPSHOT-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm")) else ""

dependencies {
	implementation("net.bytebuddy:byte-buddy-agent:1.14.18")
}

kotlin {
	jvmToolchain(properties("javaVersion").toInt())
}

repositories {
	mavenCentral()
}

intellij {
	pluginName.set(properties("pluginName"))
	version.set(properties("platformVersion"))
	type.set(properties("platformType"))
	sandboxDir.set("${rootProject.rootDir}/" + properties("sandboxDir"))
	downloadSources.set(true)
	// Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file.
	plugins.set(properties("platformPlugins").split(',').map(String::trim).filter(String::isNotEmpty))
}

tasks{
    runIde {
        systemProperties["idea.is.internal"] = true

        // Path to IDE distribution that will be used to run the IDE with the plugin.
        // ideDir.set(File("path to IDE-dependency"))
    }

	instrumentedJar {
		manifest{
			attributes["Built-By"] = "Nasller"
			attributes["Premain-Class"] = "com.nasller.codeglance.agent.Main"
			attributes["Agent-Class"] = "com.nasller.codeglance.agent.Main"
			attributes["Can-Redefine-Classes"] = true
			attributes["Can-Retransform-Classes"] = true
		}
	}

	signPlugin {
		certificateChainFile.set(File(env.getOrDefault("CERTIFICATE_CHAIN", "$dir/pluginCert/chain.crt")))
		privateKeyFile.set(File(env.getOrDefault("PRIVATE_KEY", "$dir/pluginCert/private.pem")))
		password.set(File(env.getOrDefault("PRIVATE_KEY_PASSWORD", "$dir/pluginCert/password.txt")).readText(Charsets.UTF_8))
	}

	publishPlugin {
		token.set(env["PUBLISH_TOKEN"])
		channels.set(listOf(env["PUBLISH_CHANNEL"] ?: "default"))
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