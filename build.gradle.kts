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
	kotlin("jvm") version "1.2.70"
	application
	id("com.github.johnrengelman.shadow") version "2.0.4"
	id("com.github.ben-manes.versions") version "0.20.0"
}

// source directories
sourceSets {
	getByName("main") {
		java.srcDir("src/main")
		resources.srcDir("src/resources")
	}
	getByName("test").java.srcDir("src/test")
}


// configure kotlin
kotlin.experimental.coroutines = Coroutines.ENABLE
val kotlinVersion: String by extra {
	buildscript.configurations["classpath"].resolvedConfiguration.firstLevelModuleDependencies
			.find { it.moduleName == "org.jetbrains.kotlin.jvm.gradle.plugin" }!!.moduleVersion
}

application {
	applicationDefaultJvmArgs = listOf("-XX:+UseG1GC")
	mainClassName = "xerus.monstercat.MainKt"
}

repositories {
	jcenter()
	maven("https://jitpack.io")
	maven("http://maven.bluexin.be/repository/snapshots/")
}

dependencies {
	implementation(kotlin("reflect"))
	
	implementation("com.github.Xerus2000.util", "javafx", "-SNAPSHOT")
	implementation("org.controlsfx", "controlsfx", "8.40.14")
	
	implementation("ch.qos.logback", "logback-classic", "1.2.3")
	implementation("com.github.Xerus2000", "drpc4k", "-SNAPSHOT")
	implementation("org.apache.httpcomponents", "httpmime", "4.5.+")
	implementation("com.google.apis", "google-api-services-sheets", "v4-rev542-1.25.0")
	
	val junitVersion = "5.3.1"
	testCompile("org.junit.jupiter", "junit-jupiter-api", junitVersion)
	testRuntimeOnly("org.junit.jupiter", "junit-jupiter-engine", junitVersion)
}

val file
	get() = "MonsterUtilities-$version.jar"

val MAIN = "_Main"
tasks {
	
	getByName("runShadow").group = MAIN
	getByName("startShadowScripts").group = "distribution"
	
	"run"(JavaExec::class) {
		group = MAIN
		// Usage: gradle run -Dargs="--loglevel trace"
		args = System.getProperty("args", "--loglevel debug").split(" ")
	}
	
	"shadowJar"(ShadowJar::class) {
		baseName = "MonsterUtilities"
		classifier = ""
		destinationDir = file(".")
		doLast { file(file).setExecutable(true) }
	}
	
	create<Exec>("release") {
		dependsOn("jar")
		group = MAIN
		val path = file("../monsterutilities-extras/website/downloads/" + if (isUnstable) "unstable" else "latest")
		val pathLatest = path.resolveSibling("latest") // TODO temporary workaround until real release
		doFirst {
			path.writeText(version.toString())
			pathLatest.writeText(version.toString())
			exec { commandLine("git", "tag", version) }
		}
		val s = if (OperatingSystem.current().isWindows) "\\" else ""
		commandLine("lftp", "-c", """set ftp:ssl-allow true; set ssl:verify-certificate no;
			open -u ${properties["credentials.ftp"]} -e $s"
			cd /www/downloads; ${if (properties["noversion"] == null) "put $path; put $pathLatest;" else ""}
			cd ./files; put $file;
			quit$s" monsterutilities.bplaced.net""".filter { it != '\t' && it != '\n' })
	}
	
	withType<KotlinCompile> {
		kotlinOptions.jvmTarget = "1.8"
	}
	
	replace("jar", Delete::class).run {
		group = MAIN
		dependsOn("shadowJar")
		setDelete(file(".").listFiles { f -> f.name.run { startsWith("MonsterUtilities-") && endsWith("jar") && this != file } })
	}
	
	withType<Test> {
		useJUnitPlatform()
	}
	
}

println("Java version: ${JavaVersion.current()}")
println("Version: $version")
