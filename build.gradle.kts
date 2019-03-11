import com.github.breadmoirai.GithubReleaseTask
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.*

buildscript {
	dependencies {
		classpath("com.squareup.okhttp3:okhttp:3.12.1")
		classpath("com.j256.simplemagic:simplemagic:1.14")
		classpath("org.zeroturnaround:zt-exec:1.10")
	}
}

val isUnstable = properties["release"] == null
val commitNumber = Scanner(Runtime.getRuntime().exec("git rev-list --count HEAD").inputStream).next()
version = "dev" + commitNumber +
	"-" + Scanner(Runtime.getRuntime().exec("git rev-parse --short HEAD").inputStream).next()
file("src/resources/version").writeText(version as String)

plugins {
	kotlin("jvm") version "1.3.21"
	application
	id("com.github.johnrengelman.shadow") version "5.0.0"
	id("com.github.ben-manes.versions") version "0.21.0"
	id("com.github.breadmoirai.github-release") version "2.2.4"
}

// source directories
sourceSets {
	main {
		java.srcDir("src/main")
		resources.srcDir("src/resources")
	}
	test {
		java.srcDir("src/test")
	}
}

application {
	applicationDefaultJvmArgs = listOf("-XX:+UseG1GC")
	mainClassName = "xerus.monstercat.MainKt"
}

repositories {
	jcenter()
	maven("https://jitpack.io")
	maven("https://oss.jfrog.org/simple/libs-snapshot")
}

dependencies {
	implementation(kotlin("reflect"))
	
	implementation("com.github.Xerus2000.util", "javafx", "6bc6adce50de31606732f9a14342690a96308901")
	implementation("org.controlsfx", "controlsfx", "8.40.+")
	
	implementation("ch.qos.logback", "logback-classic", "1.2.+")
	implementation("io.github.microutils", "kotlin-logging", "1.6.+")
	
	implementation("com.github.Bluexin", "drpc4k", "16b0c60")
	implementation("org.apache.httpcomponents", "httpmime", "4.5.+")
	implementation("com.google.apis", "google-api-services-sheets", "v4-rev20190109-1.28.0")
	
	val junitVersion = "5.4.0"
	testCompile("org.junit.jupiter", "junit-jupiter-api", junitVersion)
	testRuntimeOnly("org.junit.jupiter", "junit-jupiter-engine", junitVersion)
}

val jarFile
	get() = "$name-$version.jar"

val MAIN = "_main"
tasks {
	
	arrayOf(run.get(), runShadow.get()).forEach {
		it.group = MAIN
		it.args = System.getProperty("args", "--loglevel debug").split(" ")
	}
	
	val shadowJar by getting(ShadowJar::class) {
		group = MAIN
		archiveClassifier.set("")
		destinationDirectory.set(file("."))
		doFirst {
			destinationDirectory.get().asFile.listFiles().forEach {
				if(it.name.endsWith("jar"))
					it.delete()
			}
		}
		doLast {
			outputs.files.singleFile.setExecutable(true)
		}
	}
	
	val githubRelease by getting(GithubReleaseTask::class) {
		dependsOn(shadowJar)
		
		setTagName(version.toString())
		setBody(project.properties["m"]?.toString())
		setReleaseName("Dev $commitNumber" + project.properties["n"]?.let { " - $it" }.orEmpty())
		
		setPrerelease(isUnstable)
		setReleaseAssets(jarFile)
		setToken(project.properties["github.token"]?.toString())
		setOwner("Xerus2000")
	}
	
	val release by creating(Exec::class) {
		dependsOn(shadowJar, githubRelease)
		group = MAIN
		
		val path = file("../monsterutilities-extras/website/downloads/" + if(isUnstable) "unstable" else "latest")
		val pathLatest = path.resolveSibling("latest") // TODO temporary workaround until real release
		doFirst {
			path.writeText(version.toString())
			pathLatest.writeText(version.toString())
			println("Uploading release to server...")
		}
		
		val s = if(OperatingSystem.current().isWindows) "\\" else ""
		commandLine("lftp", "-c", """set ftp:ssl-allow true; set ssl:verify-certificate no;
			open -u ${project.properties["credentials.ftp"]} -e $s"
			cd /www/downloads/files; put $jarFile;
			cd /www/downloads; ${if(project.properties["noversion"] == null) "put $path; put $pathLatest;" else ""}
			quit$s" monsterutilities.bplaced.net""".filter { it != '\t' && it != '\n' })
	}
	
	withType<KotlinCompile> {
		kotlinOptions.jvmTarget = "1.8"
	}
	
	test {
		useJUnitPlatform()
	}
	
}

println("Java version: ${JavaVersion.current()}")
println("Version: $version")
