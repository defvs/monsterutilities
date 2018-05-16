package xerus.monstercat

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.services.sheets.v4.SheetsScopes
import javafx.application.Platform
import javafx.concurrent.Task
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.VBox
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import org.controlsfx.dialog.ExceptionDialog
import org.controlsfx.dialog.ProgressDialog
import xerus.ktutil.*
import xerus.ktutil.javafx.*
import xerus.ktutil.javafx.properties.listen
import xerus.ktutil.javafx.ui.*
import xerus.ktutil.ui.SimpleFrame
import xerus.monstercat.api.Player
import xerus.monstercat.downloader.TabDownloader
import xerus.monstercat.tabs.*
import java.io.File
import java.net.URL
import java.net.UnknownHostException
import java.util.*
import java.util.concurrent.TimeUnit
import javax.swing.JTextArea
import kotlin.reflect.KClass

typealias logger = XerusLogger

private val VERSION = logger::class.java.getResourceAsStream("/version").reader().run {
	readText().also { close() }
}
private val isUnstable = VERSION.indexOf('-') > -1

val logDir: File
	get() = cachePath.resolve("logs").createDirs().toFile()

lateinit var monsterUtilities: MonsterUtilities

fun main(args: Array<String>) {
	XerusLogger.parseArgs(*args, defaultLevel = "finer")
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
		SimpleFrame {
			add(JTextArea("Please install and use Java 8!\nThe current version is ${javaVersion()}").apply { isEditable = false })
		}
		return
	}
	logger.info("Version: $VERSION, Java version: ${javaVersion()}")
	logger.config("Initializing Google Sheets API Service")
	MCatalog.initService("MCatalog Reader", GoogleCredential().createScoped(listOf(SheetsScopes.SPREADSHEETS_READONLY)))
	App.launch("MonsterUtilities $VERSION", { stage ->
		stage.icons.addAll(arrayOf("icon64.png").map {
			getResource(it)?.let { Image(it.toExternalForm()) }
					?: null.apply { logger.warning("Resource $it not found") }
		})
	}, {
		val scene = Scene(MonsterUtilities(), 800.0, 600.0)
		scene.applySkin(Settings.SKIN())
		scene
	})
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

class MonsterUtilities : VBox(), JFXMessageDisplay {
	
	val tabs: MutableList<BaseTab>
	val tabPane: TabPane
	
	override val window = App.stage
	
	init {
		monsterUtilities = this
		logger.info("Starting MonsterUtilities")
		tabPane = TabPane()
		tabs = ArrayList()
		val startupTab = Settings.STARTUPTAB.get().takeUnless { it == "Previous" } ?: Settings.LASTTAB()
		logger.fine("Startup tab: $startupTab")
		
		fun addTab(tabClass: KClass<out BaseTab>) {
			try {
				val baseTab = tabClass.java.newInstance()
				logger.finer("New Tab: $baseTab")
				tabs.add(baseTab)
				val tab = Tab(baseTab.tabName, baseTab.asNode())
				tab.isClosable = false
				tabPane.tabs.add(tab)
				if (baseTab.tabName == startupTab)
					tabPane.selectionModel.select(tab)
			} catch (e: Exception) {
				monsterUtilities.showError(e, "Couldn't create ${tabClass.java.simpleName}!")
			}
		}
		addTab(TabCatalog::class)
		addTab(TabGenres::class)
		addTab(TabDownloader::class)
		addTab(TabSettings::class)
		if (VERSION != Settings.LASTVERSION.get()) {
			if (Settings.LASTVERSION().isEmpty()) {
				logger.info("First launch! Showing tutorial!")
				showIntro()
				Settings.LASTVERSION.put(VERSION)
			} else {
				launch {
					logger.fine("New version! Now running $VERSION, previously " + Settings.LASTVERSION())
					val f = Settings.DELETE()
					if (f.exists()) {
						logger.config("Deleting older version $f...")
						val time = currentSeconds()
						var res: Boolean
						do {
							res = f.delete()
						} while (!res && time + 10 > currentSeconds())
						if (res) {
							Settings.DELETE.reset()
							logger.config("Deleted $f!")
						} else
							logger.warning("Couldn't delete older version residing in $f")
					}
					Settings.LASTVERSION.put(VERSION)
				}
				showChangelog()
			}
		}
		
		children.add(Player.box)
		fill(tabPane)
		if (Settings.AUTOUPDATE())
			checkForUpdate()
	}
	
	inline fun <reified T : BaseTab> tabsByClass() = tabs.mapNotNull { it as? T }
	
	fun checkForUpdate(userControlled: Boolean = false, unstable: Boolean = isUnstable) {
		launch {
			try {
				val latestVersion = URL("http://monsterutilities.bplaced.net/downloads/" + if (unstable) "unstable" else "latest").openConnection().getInputStream().reader().readLines().firstOrNull()
				logger.fine("Latest version: $latestVersion")
				if (latestVersion == null || latestVersion.length > 50 || latestVersion == VERSION || (!userControlled && latestVersion == Settings.IGNOREVERSION())) {
					if (userControlled)
						showMessage("No update found!", "Updater", Alert.AlertType.INFORMATION)
					return@launch
				}
				if (unstable)
					update(latestVersion, true)
				else
					onJFX {
						val dialog = showAlert(Alert.AlertType.CONFIRMATION, "Updater", null, "New version $latestVersion available! Update now?", ButtonType.YES, ButtonType("Not now", ButtonBar.ButtonData.NO), ButtonType("Ignore this update", ButtonBar.ButtonData.CANCEL_CLOSE))
						dialog.stage.icons.setAll(Image("updater.png"))
						dialog.graphic = ImageView(Image("updater.png"))
						dialog.resultProperty().listen { type ->
							if (type.buttonData == ButtonBar.ButtonData.YES) {
								update(latestVersion)
							} else if (type.buttonData == ButtonBar.ButtonData.CANCEL_CLOSE)
								Settings.IGNOREVERSION.set(latestVersion)
						}
					}
			} catch (e: UnknownHostException) {
				if (userControlled)
					showMessage("No connection possible!", "Updater", Alert.AlertType.INFORMATION)
			} catch (e: Throwable) {
				showError(e, "Update failed!")
			}
		}
	}
	
	private fun update(version: String, unstable: Boolean = false) {
		val newFile = File(Settings.FILENAMEPATTERN().replace("%version%", version, true)).absoluteFile
		logger.fine("Update initiated to $newFile")
		val worker = object : Task<Unit>() {
			override fun call() {
				val connection = URL("http://monsterutilities.bplaced.net/downloads?download&version=" + if (unstable) "unstable" else version).openConnection()
				val contentLength = connection.contentLengthLong
				logger.fine("Update to $version started, size ${contentLength.byteCountString()}")
				connection.getInputStream().copyTo(newFile.outputStream(), true, true) {
					updateProgress(it, contentLength)
					isCancelled
				}
				if (isCancelled)
					logger.config("Update cancelled, deleting $newFile: ${newFile.delete().to("Success", "FAILED")}")
			}
			
			override fun succeeded() {
				if (isUnstable == unstable) {
					val jar = File(MonsterUtilities::class.java.protectionDomain.codeSource.location.toURI())
					logger.info("Scheduling $jar for delete")
					Settings.DELETE.set(jar)
				}
				logger.warning("Exiting for update to $version!")
				Settings.flush()
				newFile.setExecutable(true)
				val cmd = arrayOf("${System.getProperty("java.home")}/bin/java", "-jar", newFile.toString())
				logger.info("Executing '${cmd.joinToString(" ")}'")
				val p = Runtime.getRuntime().exec(cmd)
				val exited = p.waitFor(3, TimeUnit.SECONDS)
				logger.info("Dumping streams of $p")
				p.inputStream.dump()
				p.errorStream.dump()
				if (!exited) {
					Platform.exit()
					App.stage.close()
					logger.warning("Exiting!")
				}
			}
		}
		worker.launch()
		checkJFX {
			ProgressDialog(worker).run {
				title = "Updater"
				headerText = "Downloading Update"
				contentText = "Downloading ${newFile.name} to ${newFile.absoluteFile.parent}"
				dialogPane.scene.window.setOnCloseRequest { worker.cancel() }
				initOwner(App.stage)
				show()
			}
		}
	}
	
	fun showIntro() {
		onJFX {
			val text = Label("""
					MonsterUtilities enables you to access the Monstercat library with ease! Here a quick feature overview:
					- The Catalog Tab serves you information about every track, freshly fetched from the MCatalog
					- The Genres Tab provides you an overview of genres, also from the MCatalog
					- The Downloader enables you to batch-download songs from the Monstercat library providing you have Gold
					Clicking on a song name anywhere plays it if available
					The Catalog, Genres and Releases are conveniently cached for offline use in $cachePath
					Look out for Tooltips when you are stuck!""".trimIndent())
			text.isWrapText = true
			App.stage.createStage("Welcome to MonsterUtilities!", text).apply {
				sizeToScene()
			}.show()
		}
	}
	
	fun showChangelog() {
		val c = Changelog().apply {
			version("dev", "pre-Release", "Brand new shiny favicon and player buttons - big thanks to NocFA!",
					"Added intro dialog", "Send Feedback directly from the application!")
					.change("New Downloader!", "Can download any combinations of Releases and Tracks", "Easy filtering", "Validates connect.sid while typing", "Two distinct filename patterns for Singles and Album tracks", "Greatly improved pattern syntax with higher flexibility")
					.change("Settings reworked", "Multiple skins available, changeable on-the-fly", "Startup Tab can now also be the previously opened one")
					.change("Catalog and Genre Tab now show Genre colors")
					.change("Catalog improved", "More filtering options", "Smart column size")
					.change("Player now has a slick seekbar inspired by the website")
			
			version(0, 3, "UI Rework started", "Genres are now presented as a tree",
					"Music playing is better integrated", "Fixed some mistakes in the Downloader")
					.change("Catalog rework", "New, extremely flexible SearchVíew added", "Visible catalog columns can now be changed on-the-fly")
					.patch("Music player can now also play EPs", "Added more functionality to the Music player",
							"Improved the SearchView used in the Catalog", "Added a silver skin")
					.patch("Fixed a small bug in the Downloader", "Added the possibility to log to a file",
							"Prevented silent crashes")
					.patch("Fixed an issue that prevented the Player from playing older Remixes with featured Artists",
							"Fixed an issue with the cache preventing immediate downloading")
			
			version(0, 2, "Interactive Catalog",
					"Added direct song streaming by double-clicking a Song in the Catalog")
			
			version(0, 1, "Downloader overhaul", "Added more downloading options & prettified them", "Tweaked many Settings",
					"Catalog tab is now more flexible", "Implemented dismissable infobar (Only used in the Catalog yet)")
					.patch("Added Downloader continuator", "Improved the readability of the current Downloader-status and added an \"Estimated time left\"",
							"Cleared up some categorising Errors, but some will stay due to mislabelings on Monstercats side", "Endless bugfixes")
					.patch("Multiple Settings changed again, so another soft reset happened", "Fixed some formatting issues",
							"Added capability to split off Album Mixes when downloading", "Fixed various bugs and added some logging")
					.patch("Added Release caching for faster Release fetching",
							"Improved Downloader view & cancellation (now with 90% less spasticity!)", "Fixed various bugs")
			
			version(0, 0, "Offline caching", "Added Changelog", "Fixed offline Catalog caching",
					"Fixed Genre Tab", "Fixed some small downloading Errors", "Improved Error handling")
					.patch("Added more downloading options & prettified them", "Tweaked many Settings",
							"Catalog tab is now more flexible", "Implemented dismissable infobar (Only used in the Catalog yet)")
			
		}
		onJFX { c.show(App.stage) }
	}
	
	override fun showError(error: Throwable, title: String) {
		logger.severe("$title: $error")
		onJFX {
			val dialog = ExceptionDialog(error)
			dialog.initOwner(App.stage)
			dialog.headerText = title
			dialog.show()
		}
	}
	
}
