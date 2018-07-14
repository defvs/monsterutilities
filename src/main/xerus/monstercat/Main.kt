package xerus.monstercat

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.services.sheets.v4.SheetsScopes
import javafx.scene.Scene
import javafx.scene.image.Image
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import xerus.ktutil.*
import xerus.ktutil.javafx.applySkin
import xerus.ktutil.javafx.ui.App
import xerus.ktutil.ui.SimpleFrame
import java.io.File
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.swing.JTextArea

typealias logger = XerusLogger

val VERSION = getResource("version")!!.readText()
val isUnstable = VERSION.contains('-')

val logDir: File
	get() = cacheDir.resolve("logs").apply { mkdirs() }

lateinit var monsterUtilities: MonsterUtilities

val globalThreadPool: ExecutorService = Executors.newCachedThreadPool()

val location: URL = MonsterUtilities::class.java.protectionDomain.codeSource.location
var checkUpdate = Settings.AUTOUPDATE() && location.toString().endsWith(".jar")

fun main(args: Array<String>) {
	XerusLogger.parseArgs(*args, defaultLevel = "finer")
	if (args.contains("--no-update"))
		checkUpdate = false
	Thread.setDefaultUncaughtExceptionHandler { thread, ex ->
		logger.warning("Uncaught exception in $thread: ${ex.getStackTraceString()}")
	}
	val logfile = logDir.resolve("log${currentSeconds()}.txt")
	try {
		XerusLogger.logToFile(logfile)
		logger.config("Logging to $logfile")
		launch {
			val logs = logDir.listFiles()
			if (logs.size > 10) {
				logs.asSequence().sortedByDescending { it.name }.drop(5).filter {
					val timestamp = it.nameWithoutExtension.substring(3).toIntOrNull() ?: return@filter true
					timestamp + 200_000 < currentSeconds()
				}.also {
					val count = it.count()
					if (count > 0)
						logger.finer("Deleting $count old logs")
				}.forEach { it.delete() }
			}
		}
	} catch (t: Throwable) {
		showErrorSafe(t, "Can't log to $logfile!")
	}
	if (!javaVersion().startsWith("1.8")) {
		SimpleFrame { add(JTextArea("Please install and use Java 8!\nThe current version is ${javaVersion()}").apply { isEditable = false }) }
		return
	}
	logger.info("Version: $VERSION, Java version: ${javaVersion()}")
	logger.config("Initializing Google Sheets API Service")
	Sheets.initService("MonsterUtilities", GoogleCredential().createScoped(listOf(SheetsScopes.SPREADSHEETS_READONLY)))
	App.launch("MonsterUtilities $VERSION", { stage ->
		stage.icons.addAll(arrayOf("img/icon64.png").map {
			getResource(it)?.let { Image(it.toExternalForm()) }
					?: null.apply { logger.warning("Resource $it not found") }
		})
	}, {
		val scene = Scene(MonsterUtilities(), 800.0, 600.0)
		scene.applySkin(Settings.SKIN())
		scene
	})
	globalThreadPool.shutdown()
	logger.info("Main completed!")
}

fun showErrorSafe(error: Throwable, title: String = "Error") {
	launch {
		var i = 0
		while (i < 100 && !::monsterUtilities.isInitialized) {
			delay(200)
			i++
		}
		monsterUtilities.showError(error, title)
	}
}
