import com.github.breadmoirai.githubreleaseplugin.GithubReleaseTask
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.install4j.gradle.Install4jTask
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.IOException
import java.util.*

val isUnstable = properties["release"] == null
var commitNumber: String = ""
try {
	commitNumber = Scanner(Runtime.getRuntime().exec("git rev-list --count HEAD").inputStream).next()
	version = "dev" + commitNumber +
		"-" + Scanner(Runtime.getRuntime().exec("git rev-parse --short HEAD").inputStream).next()
} catch(e: IOException) {
	println("Encountered an exception while determining the version - $e\nThe most likely cause is that git is not installed!")
	version = "self-compiled"
}
file("src/resources/version").writeText(version as String)

plugins {
	kotlin("jvm") version "1.3.71"
	application
	id("com.github.johnrengelman.shadow") version "5.1.0"
	id("com.github.breadmoirai.github-release") version "2.2.9"
	id("com.github.ben-manes.versions") version "0.21.0"
	id("se.patrikerdes.use-latest-versions") version "0.2.13"
	id("com.install4j.gradle") version "8.0"
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
}

dependencies {
	implementation(kotlin("reflect"))
	
	implementation("com.github.Xerus2000.util", "javafx", "2f67fc2")
	implementation("org.controlsfx", "controlsfx", "8.40.+")
	
	implementation("ch.qos.logback", "logback-classic", "1.2.+")
	implementation("io.github.microutils", "kotlin-logging", "1.6.+")
	
	implementation("be.bluexin", "drpc4k", "0.9")
	implementation("org.apache.httpcomponents", "httpmime", "4.5.+")
	implementation("com.google.apis", "google-api-services-sheets", "v4-rev20190508-1.30.1")
	
	implementation("com.beust", "klaxon", "5.2")
	
	val junitVersion = "5.5.0"
	testImplementation("org.junit.jupiter", "junit-jupiter-api", junitVersion)
	testRuntimeOnly("org.junit.jupiter", "junit-jupiter-engine", junitVersion)
	testImplementation("io.kotlintest", "kotlintest-runner-junit5", "3.3.3")
	implementation(kotlin("stdlib-jdk8"))
}

val jarFile
	get() = "$name-$version.jar"

githubRelease {
	tagName(version.toString())
	body(project.properties["m"]?.toString() ?: "")
	releaseName("Dev $commitNumber" + project.properties["n"]?.let { " - $it" }.orEmpty())
	
	prerelease(isUnstable)
	releaseAssets(jarFile)
	owner("Xerus2000")
	
	token(project.properties["github.token"]?.toString())
}

install4j {
	(properties["install4j.installDir"] as String?)?.let { installDir = file(it) }
	(properties["install4j.license"] as String?)?.let { license = it }
}

val MAIN = "_main"
tasks {
	
	arrayOf(run.get(), runShadow.get()).forEach {
		it.group = MAIN
		it.args = System.getProperty("args", "--loglevel trace").split(" ")
	}
	
	val shadowJar by getting(ShadowJar::class) {
		group = MAIN
		archiveClassifier.set("")
		destinationDirectory.set(file("."))
		doFirst {
			destinationDirectory.get().asFile.listFiles()!!.forEach {
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
	}
	
	val websiteRelease by creating(Exec::class) {
		dependsOn(shadowJar)
		
		val path = file("../monsterutilities-extras/website/downloads/" + if(isUnstable) "unstable" else "latest")
		val pathLatest = path.resolveSibling("latest") // TODO temporary workaround until real release
		doFirst {
			path.writeText(version.toString())
			pathLatest.writeText(version.toString())
			println("Uploading release to server...")
		}
		
		val s = if(OperatingSystem.current().isWindows) "\\" else ""
		commandLine("lftp", "-c", """set ftp:ssl-allow true; set ssl:verify-certificate no; set cmd:fail-exit true;
			open -u ${project.properties["credentials.ftp"]} -e $s"
			cd /www/downloads/files && put $jarFile;
			cd /www/downloads && ${if(project.properties["noversion"] == null) "put $path; put $pathLatest;" else ""}
			quit$s" monsterutilities.bplaced.net""".filter { it != '\t' && it != '\n' })
	}
	
	val release by creating {
		dependsOn(websiteRelease, githubRelease)
		group = MAIN
	}
	
	val buildInstaller by creating(Install4jTask::class) {
		dependsOn(shadowJar)
		group = MAIN
		
		projectFile = file("MonsterUtilities.install4j")
		destination = "build/install4j/"
		this.release = version.toString()
		
		lateinit var jarTemp: File
		doFirst {
			jarTemp = file(jarFile).copyTo(file("build/install4j/$jarFile"), true)
		}
		doLast {
			if(!jarTemp.delete())
				println("Couldn't delete temporary install4j file '${jarTemp.path}'")
		}
	}
	
	withType<KotlinCompile> {
		kotlinOptions.jvmTarget = "1.8"
	}
	
	test {
		useJUnitPlatform()
	}
	
}

println("Java version: ${System.getProperty("java.version")}")
println("Version: $version")
val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
	jvmTarget = "1.8"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
	jvmTarget = "1.8"
}