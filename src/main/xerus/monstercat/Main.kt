package xerus.monstercat

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.services.sheets.v4.SheetsScopes
import javafx.scene.Scene
import javafx.scene.image.Image
import kotlinx.coroutines.experimental.*
import mu.KotlinLogging
import xerus.ktutil.SystemUtils
import xerus.ktutil.getResource
import xerus.ktutil.javafx.applySkin
import xerus.ktutil.javafx.ui.App
import xerus.ktutil.ui.SimpleFrame
import java.io.File
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JTextArea

val VERSION = getResource("version")!!.readText()
val isUnstable = VERSION.contains('-')

val cacheDir: File
	get() = (File("/var/tmp").takeIf { it.exists() } ?: File(System.getProperty("java.io.tmpdir")))
			.resolve("monsterutilities").apply { mkdirs() }

lateinit var monsterUtilities: MonsterUtilities

val globalThreadPool: ExecutorService = Executors.newCachedThreadPool(object : ThreadFactory {
	private val poolNumber = AtomicInteger(1)
	override fun newThread(r: Runnable) =
			Thread(Thread.currentThread().threadGroup, r, "global-" + poolNumber.getAndIncrement())
})
val globalDispatcher = globalThreadPool.asCoroutineDispatcher()

val jarLocation: URL = MonsterUtilities::class.java.protectionDomain.codeSource.location

fun main(args: Array<String>) {
	initLogging(args)
	val logger = KotlinLogging.logger {}
	
	if (!SystemUtils.javaVersion.startsWith("1.8")) {
		SimpleFrame { add(JTextArea("Please install and use Java 8!\nThe current version is ${SystemUtils.javaVersion}").apply { isEditable = false }) }
		return
	}
	logger.info("Version: $VERSION, Java version: ${SystemUtils.javaVersion}")
	
	logger.info("Initializing Google Sheets API Service")
	Sheets.initService("MonsterUtilities", GoogleCredential().createScoped(listOf(SheetsScopes.SPREADSHEETS_READONLY)))
	
	val checkUpdate = !args.contains("--no-update") && Settings.AUTOUPDATE() && jarLocation.toString().endsWith(".jar")
	App.launch("MonsterUtilities $VERSION", { stage ->
		stage.icons.addAll(arrayOf("img/icon64.png").map {
			getResource(it)?.let { Image(it.toExternalForm()) }
					?: null.apply { logger.warn("Resource $it not found!") }
		})
	}, {
		val scene = Scene(MonsterUtilities(checkUpdate), 800.0, 700.0)
		scene.applySkin(Settings.SKIN())
		scene
	})
	globalThreadPool.shutdown()
	logger.info("Main has shut down!")
}

fun showErrorSafe(error: Throwable, title: String = "Error") {
	GlobalScope.launch {
		var i = 0
		while (i < 100 && !::monsterUtilities.isInitialized) {
			delay(200)
			i++
		}
		monsterUtilities.showError(error, title)
	}
}
