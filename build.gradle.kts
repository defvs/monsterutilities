import org.gradle.internal.os.OperatingSystem;
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.Coroutines
import java.io.ByteArrayOutputStream
import java.nio.file.*
import java.util.Scanner

val isUnstable = properties["release"] == null
version = "dev" + Scanner(Runtime.getRuntime().exec("git rev-list --count HEAD").inputStream).next() +
		"-" + Scanner(Runtime.getRuntime().exec("git rev-parse --short HEAD").inputStream).next()
file("src/resources/version").writeText(version as String)

plugins {
	kotlin("jvm") version "1.2.41"
	application
	id("com.github.johnrengelman.shadow") version "2.0.4"
	id("com.github.ben-manes.versions") version "0.17.0"
}

// source directories
java.sourceSets {
	getByName("main") {
		java.srcDir("src/main")
		resources.srcDir("src/resources")
	}
}


// configure kotlin
kotlin.experimental.coroutines = Coroutines.ENABLE
val kotlinVersion: String by extra {
	buildscript.configurations["classpath"].resolvedConfiguration.firstLevelModuleDependencies
			.find { it.moduleName == "org.jetbrains.kotlin.jvm.gradle.plugin" }!!.moduleVersion
}

application {
	applicationDefaultJvmArgs = listOf("-XX:+UseG1GC")
	mainClassName = "xerus.monstercat.MonsterUtilitiesKt"
}

repositories {
	jcenter()
	maven(url = "http://maven.bluexin.be/repository/snapshots/")
}

dependencies {
	compile("xerus.util", "javafx")
	compile(kotlin("stdlib-jdk8"))
	
	compile("org.controlsfx", "controlsfx", "8.40.+")
	
	compile("be.bluexin:drpc4k:0.6-SNAPSHOT")
	
	compile("org.apache.httpcomponents", "httpmime", "4.5.4")
	compile("com.google.apis", "google-api-services-sheets", "v4-rev518-1.23.0")
}

val file
	get() = "$name-$version.jar"

val MAIN = "_Main"
tasks {
	
	getByName("runShadow").group = MAIN
	getByName("startShadowScripts").group = "distribution"
	
	"run"(JavaExec::class) {
		group = MAIN
		// Usage: gradle run -Dexec.args="FINE save"
		args = System.getProperty("exec.args", "").split(" ")
	}
	
	"shadowJar"(ShadowJar::class) {
		baseName = "MonsterUtilities"
		classifier = ""
		destinationDir = file(".")
		doLast {
			file(file).setExecutable(true)
		}
	}
	
	val release by creating(Exec::class) {
		group = MAIN
		val path = "../monsterutilities-extras/website/downloads/" + if (isUnstable) "unstable" else "latest"
		file(path).writeText(version.toString())
		// TODO temporary workaround until real release
		val pathLatest = "../monsterutilities-extras/website/downloads/latest"
		file(pathLatest).writeText(version.toString())
		
		val s = if (OperatingSystem.current().isWindows) "\\" else ""
		commandLine("lftp", "-c", """set ftp:ssl-allow true; set ssl:verify-certificate no; 
			open -u ${properties["credentials.ftp"]} -e $s"
			cd /www/downloads; ${if (properties["noversion"] == null) "put $path; put $pathLatest;" else ""}
			cd ./files; ${if (properties["noversion"] == null) "mrm ${rootProject.name}-*-*.jar;" else ""} put $file;
			quit$s" monsterutilities.bplaced.net""".filter { it != '\t' && it != '\n' })
	}
	
	withType<KotlinCompile> {
		kotlinOptions.jvmTarget = "1.8"
	}
	
	tasks.replace("jar", Delete::class.java).apply {
		group = MAIN
		dependsOn("shadowJar")
		setDelete(file(".").listFiles { f -> f.name.run { startsWith("${rootProject.name}-") && endsWith("jar") && this != file } })
	}
	
}

println("Java version: ${JavaVersion.current()}")
println("Version: $version")
