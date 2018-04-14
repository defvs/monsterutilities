import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.Coroutines
import java.io.ByteArrayOutputStream
import java.util.Scanner

version = "1.0.0" + "-" + Scanner(Runtime.getRuntime().exec(arrayOf("git", "rev-parse", "--short", "HEAD")).inputStream).next()

plugins {
	kotlin("jvm") version "1.2.31"
	application
	id("com.github.johnrengelman.shadow") version "2.0.1"
}

// source directories
java.sourceSets {
	getByName("main") {
		java.srcDir("src/main")
		resources.srcDir("src/resources")
	}
	getByName("test") {
		java.srcDir("src/test")
	}
}


// configure kotlin
kotlin.experimental.coroutines = Coroutines.ENABLE
val kotlinVersion: String by extra {
	buildscript.configurations["classpath"].resolvedConfiguration.firstLevelModuleDependencies
			.find { it.moduleName == "org.jetbrains.kotlin.jvm.gradle.plugin" }!!.moduleVersion
}

application {
	mainClassName = "xerus.monstercat.MonsterUtilitiesKt"
}

repositories {
	jcenter()
}

dependencies {
	compile("xerus.util", "javafx")
	
	compile(kotlin("stdlib-jdk8"))
	//compile("org.jetbrains.kotlinx", "kotlinx-coroutines-core", "0.+")
	
	compile("org.controlsfx", "controlsfx", "8.40.+")
	
	compile("org.apache.httpcomponents", "httpmime", "4.5.4")
	compile("com.google.apis", "google-api-services-sheets", "v4-rev496-1.+")
	
	testCompile("com.google.api-client", "google-api-client-java6", "1.23.0")
	testCompile("com.google.oauth-client", "google-oauth-client-jetty", "1.11.0-beta")
	
}

application {
	applicationDefaultJvmArgs = listOf("-XX:+UseG1GC")
}

val file
	get() = "MonsterUtilities-$version.jar"

val MAIN = "_Main"
tasks {
	
	getByName("runShadow").group = MAIN
	getByName("startShadowScripts").group = "distribution"
	
	"run"(JavaExec::class) {
		group = MAIN
		// gradle run -Dexec.args="FINE save"
		args = System.getProperty("exec.args", "").split(" ")
	}
	
	"shadowJar"(ShadowJar::class) {
		baseName = "MonsterUtilities"
		classifier = null
		destinationDir = file(".")
		doLast {
			println("Created $file")
		}
	}
	
	val release by creating(Exec::class) {
		group = MAIN
		dependsOn("shadowJar")
		commandLine("lftp", "-c", "set ftp:ssl-allow true ; set ssl:verify-certificate no; open -u ${properties["credentials.ftp"]} -e \"cd /; mput $file; quit\" monsterutilities.bplaced.net")
	}
	
	val version by creating(Copy::class) {
		from("src/main/xerus/monstercat/MonsterUtilities.kt")
		into("$buildDir/copied")
		filter { line -> if (line.contains("val VERSION")) "private const val VERSION = \"$version\"" else line }
	}
	
	"compileKotlin"(KotlinCompile::class).dependsOn(version)
	
	withType<KotlinCompile> {
		kotlinOptions.jvmTarget = "1.8"
	}
	
	tasks.replace("jar").apply {
		group = MAIN
		dependsOn("shadowJar")
	}
	
}

println("Java version: ${JavaVersion.current()}")
println("Version: $version")
