package xerus.monstercat

import javafx.scene.Scene
import javafx.scene.image.Image
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import xerus.ktutil.SystemUtils
import xerus.ktutil.getResource
import xerus.ktutil.javafx.onFx
import xerus.ktutil.javafx.ui.App
import xerus.ktutil.ui.SimpleFrame
import java.io.File
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JTextArea

val currentVersion = getResource("version")!!.readText()
val isUnstable = currentVersion.contains('-')

val dataDir: File
	get() = SystemUtils.cacheDir.resolve("monsterutilities").apply { mkdirs() }

val cacheDir: File
	get() = dataDir.resolve("cache").apply { mkdirs() }

lateinit var monsterUtilities: MonsterUtilities

val globalThreadPool: ExecutorService = Executors.newCachedThreadPool(object: ThreadFactory {
	private val poolNumber = AtomicInteger(1)
	override fun newThread(r: Runnable) =
		Thread(Thread.currentThread().threadGroup, r, "mu-worker-" + poolNumber.getAndIncrement())
})
val globalDispatcher = globalThreadPool.asCoroutineDispatcher()

val codeSource: URL = MonsterUtilities::class.java.protectionDomain.codeSource.location

fun main(args: Array<String>) {
	initLogging(args)
	val logger = KotlinLogging.logger {}
	logger.debug("Commandline arguments: ${args.joinToString(", ", "[", "]")}")
	logger.debug("Running from $codeSource")
	
	if(!SystemUtils.javaVersion.startsWith("1.8")) {
		SimpleFrame { add(JTextArea("Please install and use Java 8!\nThe current version is ${SystemUtils.javaVersion}").apply { isEditable = false }) }
		return
	}
	logger.info("Version: $currentVersion, Java version: ${SystemUtils.javaVersion}")
	
	logger.info("Initializing Google Sheets API Service")
	Sheets.initService("MonsterUtilities")
	
	val checkUpdate = !args.contains("--no-update") && Settings.AUTOUPDATE() && codeSource.toString().endsWith(".jar")
	App.launch("MonsterUtilities $currentVersion", Settings.THEME(), { stage ->
		stage.icons.addAll(arrayOf("img/icon64.png").map {
			getResource(it)?.let { Image(it.toExternalForm()) }
				?: null.apply { logger.warn("Resource $it not found!") }
		})
	}, {
		Scene(MonsterUtilities(checkUpdate).root, 800.0, 700.0)
	})
	globalThreadPool.shutdown()
	logger.info("Main has shut down!")
}

fun showErrorSafe(error: Throwable, title: String = "Error") = doWhenReady { showError(error, title) }

fun doWhenReady(action: MonsterUtilities.() -> Unit) {
	GlobalScope.launch {
		var i = 0
		while(i < 100 && !::monsterUtilities.isInitialized) {
			delay(200)
			i++
		}
		onFx {
			action(monsterUtilities)
		}
	}
}

