package xerus.monstercat

import javafx.application.Platform
import javafx.concurrent.Task
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.util.Duration
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.controlsfx.dialog.ExceptionDialog
import xerus.ktutil.byteCountString
import xerus.ktutil.copyTo
import xerus.ktutil.currentSeconds
import xerus.ktutil.javafx.checkFx
import xerus.ktutil.javafx.controlsfx.progressDialog
import xerus.ktutil.javafx.controlsfx.stage
import xerus.ktutil.javafx.createStage
import xerus.ktutil.javafx.fill
import xerus.ktutil.javafx.launch
import xerus.ktutil.javafx.onFx
import xerus.ktutil.javafx.properties.listen
import xerus.ktutil.javafx.ui.App
import xerus.ktutil.javafx.ui.Changelog
import xerus.ktutil.javafx.ui.JFXMessageDisplay
import xerus.ktutil.javafx.ui.SimpleTransition
import xerus.ktutil.javafx.ui.stage
import xerus.ktutil.to
import xerus.monstercat.api.DiscordRPC
import xerus.monstercat.api.Player
import xerus.monstercat.downloader.TabDownloader
import xerus.monstercat.tabs.BaseTab
import xerus.monstercat.tabs.TabCatalog
import xerus.monstercat.tabs.TabGenres
import xerus.monstercat.tabs.TabSettings
import xerus.monstercat.tabs.TabSound
import java.io.File
import java.net.URL
import java.net.UnknownHostException
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

class MonsterUtilities(checkForUpdate: Boolean): JFXMessageDisplay {
	private val logger = KotlinLogging.logger { }
	
	val container = VBox()
	
	val root = StackPane(Region().apply { opacity = Settings.BACKRGOUNDCOVEROPACITY() }, container).also { pane ->
		Settings.BACKRGOUNDCOVEROPACITY.listen {
			pane.children.first().opacity = it
		}
		Player.backgroundCover.listen { newBg ->
			val oldRegion = pane.children.first()
			val newRegion = Region().apply {
				opacity = 0.0
				background = newBg
			}
			pane.children.add(0, newRegion)
			val opacity = Settings.BACKRGOUNDCOVEROPACITY()
			SimpleTransition(pane, Duration.seconds(1.0), {
				newRegion.opacity = opacity * it
				oldRegion.opacity = opacity * (1 - it)
			}, true, {
				children.remove(oldRegion)
			})
		}
	}
	
	val tabs: MutableList<BaseTab>
	val tabPane: TabPane
	
	override val window = App.stage
	
	init {
		monsterUtilities = this
		logger.info("Starting MonsterUtilities")
		tabPane = TabPane()
		tabs = ArrayList()
		val startupTab = Settings.STARTUPTAB.get().takeUnless { it == "Previous" } ?: Settings.LASTTAB()
		logger.debug("Startup tab: $startupTab")
		
		fun addTab(tabClass: KClass<out BaseTab>) {
			try {
				val baseTab = tabClass.java.getDeclaredConstructor().newInstance()
				logger.debug("New Tab: $baseTab")
				tabs.add(baseTab)
				val tab = Tab(baseTab.tabName, baseTab.asNode())
				tab.isClosable = false
				tabPane.tabs.add(tab)
				if(baseTab.tabName == startupTab)
					tabPane.selectionModel.select(tab)
			} catch(e: Exception) {
				monsterUtilities.showError(e, "Couldn't create ${tabClass.java.simpleName}!")
			}
		}
		addTab(TabCatalog::class)
		addTab(TabGenres::class)
		addTab(TabDownloader::class)
		addTab(TabSound::class)
		addTab(TabSettings::class)
		if(VERSION != Settings.LASTVERSION.get()) {
			if(Settings.LASTVERSION().isEmpty()) {
				logger.info("First launch! Showing tutorial!")
				showIntro()
				Settings.LASTVERSION.put(VERSION)
			} else {
				GlobalScope.launch {
					logger.info("New version! Now running $VERSION, previously " + Settings.LASTVERSION())
					val f = Settings.DELETE()
					if(f.exists()) {
						logger.info("Deleting older version $f...")
						val time = currentSeconds()
						var res: Boolean
						do {
							res = f.delete()
						} while(!res && time + 10 > currentSeconds())
						if(res) {
							Settings.DELETE.clear()
							logger.info("Deleted $f!")
						} else
							logger.warn("Couldn't delete older version residing in $f")
					}
					Settings.LASTVERSION.put(VERSION)
				}
				showChangelog()
			}
		}
		
		container.children.add(Player.container)
		container.fill(tabPane)
		if(checkForUpdate)
			checkForUpdate()
		DiscordRPC.connect()
	}
	
	inline fun <reified T: BaseTab> tabsByClass() = tabs.mapNotNull { it as? T }
	
	private fun String.devVersion() = if(startsWith("dev")) split('v', '-')[1].toInt() else null
	
	fun checkForUpdate(userControlled: Boolean = false, unstable: Boolean = isUnstable) {
		GlobalScope.launch {
			try {
				val latestVersion = URL("http://monsterutilities.bplaced.net/downloads/" + if(unstable) "unstable" else "latest").openConnection().getInputStream().reader().readLines().firstOrNull()
				logger.info("Latest version: $latestVersion")
				if(latestVersion == null || latestVersion.length > 50 || latestVersion == VERSION || (!userControlled && latestVersion == Settings.IGNOREVERSION()) || latestVersion.devVersion()?.let { VERSION.devVersion()!! < it } == true) {
					if(userControlled)
						showMessage("No update found!", "Updater", Alert.AlertType.INFORMATION)
					return@launch
				}
				if(unstable)
					update(latestVersion, true)
				else
					onFx {
						val dialog = showAlert(Alert.AlertType.CONFIRMATION, "Updater", null, "New version $latestVersion available! Update now?", ButtonType.YES, ButtonType("Not now", ButtonBar.ButtonData.NO), ButtonType("Ignore this update", ButtonBar.ButtonData.CANCEL_CLOSE))
						dialog.stage.icons.setAll(Image("img/updater.png"))
						dialog.resultProperty().listen { type ->
							if(type.buttonData == ButtonBar.ButtonData.YES) {
								update(latestVersion)
							} else if(type.buttonData == ButtonBar.ButtonData.CANCEL_CLOSE)
								Settings.IGNOREVERSION.set(latestVersion)
						}
					}
			} catch(e: UnknownHostException) {
				if(userControlled)
					showMessage("No connection possible!", "Updater", Alert.AlertType.INFORMATION)
			} catch(e: Throwable) {
				showError(e, "Update failed!")
			}
		}
	}
	
	private fun update(version: String, unstable: Boolean = false) {
		val newFile = File(Settings.FILENAMEPATTERN().replace("%version%", version, true)).absoluteFile
		logger.info("Update initiated to $newFile")
		val worker = object: Task<Unit>() {
			init {
				updateTitle("Downloading Update")
				updateMessage("Downloading ${newFile.name} to ${newFile.absoluteFile.parent}")
			}
			
			override fun call() {
				val connection = URL("http://monsterutilities.bplaced.net/downloads?download&version=" + if(unstable) "unstable" else version).openConnection()
				val contentLength = connection.contentLengthLong
				logger.debug("Update to $version started, size ${contentLength.byteCountString()}")
				connection.getInputStream().copyTo(newFile.outputStream(), true, true) {
					updateProgress(it, contentLength)
					isCancelled
				}
				if(isCancelled)
					logger.info("Update cancelled, deleting $newFile: ${newFile.delete().to("Success", "FAILED")}")
			}
			
			override fun succeeded() {
				if(isUnstable == unstable && jarLocation.toString().endsWith(".jar")) {
					val jar = File(jarLocation.toURI())
					logger.info("Scheduling '$jar' for delete")
					Settings.DELETE.set(jar)
				}
				logger.warn("Exiting for update to $version!")
				Settings.flush()
				
				newFile.setExecutable(true)
				val cmd = arrayOf("${System.getProperty("java.home")}/bin/java", "-jar", newFile.toString())
				logger.info("Executing '${cmd.joinToString(" ")}'")
				val p = Runtime.getRuntime().exec(cmd)
				val exited = p.waitFor(3, TimeUnit.SECONDS)
				
				if(!exited) {
					Platform.exit()
					logger.warn("Exiting $VERSION!")
				} else {
					showAlert(Alert.AlertType.WARNING, "Error while updating", content = "The downloaded jar was not started successfully!")
				}
			}
		}
		worker.launch()
		checkFx {
			worker.progressDialog().run {
				title = "Updater"
				stage.icons.setAll(Image("img/updater.png"))
				show()
				graphic = ImageView(Image("img/updater.png"))
			}
		}
	}
	
	fun showIntro() {
		onFx {
			val text = Label("""
					MonsterUtilities enables you to access the Monstercat library with ease! Here a quick feature overview:
					- The Catalog Tab serves you information about every track, freshly fetched from the MCatalog
					- The Genres Tab provides you an overview of genres, also from the MCatalog
					- The Downloader enables you to batch-download songs from the Monstercat library providing you have a valid Gold subscription
					Double-clicking on a song name anywhere plays it if possible
					The Catalog, Genres and Releases are conveniently cached for offline use in $cacheDir
					Look out for Tooltips when you are stuck!""".trimIndent())
			text.isWrapText = true
			App.stage.createStage("Welcome to MonsterUtilities!", text).apply {
				sizeToScene()
			}.show()
		}
	}
	
	fun showChangelog() {
		val c = Changelog().apply {
			version("dev139", "Improved fetching, caching & processing",
				"The Release fetching now works with the new pagination of the Monstercat API",
				"Added a little cover art in the Player",
				"Fixed naming patterns in Downloader",
				"Improved cache structure",
				"Squashed many small bugs")
			
			version("dev116", "Bugfixes & Downloader aftercare",
				"Updated & Expanded connect.sid instructions",
				"Fixed a bug where the Player always played \"Halo Nova - The Force\"")
				.change("Downloader", "Added cover icons for Releases in Downloader", "Fixed Track naming issues")
			
			version("dev107", "Downloader Rework",
				"Fixed many, many minor issues")
				.change("Downloader Rework",
					"Downloader now focuses on Releases first and incorporates Tracks subtly",
					"\"Smart Select\" & \"Exclude already downloaded songs\" now work properly",
					"Adjusted track naming patterns to enable more customization, fix parsing for more obscure titles",
					"Non-downloadable songs are now properly highlighted")
				.change("Improved cache", "Now saved as json", "Versioning ensures integrity")
			
			version("dev59", "Improved Downloading and Logging")
				.change("Improved Downloader, fixed Windows part files not being renamed")
				.change("Reworked logging to be more transparent")
			
			version("dev43", "Safer downloading")
				.change("Downloader now creates part-files while downloading so your files are safe from crashes")
				.change("Backend has been updated to cope with changes in the Monstercat API")
			
			version("dev30", "Rework",
				"Brand new shiny icons - big thanks to NocFA!", "Added intro dialog", "Automatic self-update",
				"Send feedback directly from the application!", "Every Slider is now scrollable with the mouse wheel")
				.change("New Downloader!",
					"Can download any combinations of Releases and Tracks", "Easy filtering",
					"Validates connect.sid while typing", "Two distinct filename patterns for Singles and Album tracks",
					"Greatly improved pattern syntax with higher flexibility")
				.change("Settings reworked",
					"Multiple skins available, changeable on-the-fly", "Startup Tab can now also be the previously opened one")
				.change("Catalog and Genre Tab show Genre colors")
				.change("Catalog improved",
					"More filtering options", "Smart column size")
				.change("Player now has a slick Seekbar inspired by the website",
					"It can also be controlled via scrolling (suggested by AddiVF)")
				.change("Added an Audio Equalizer")
				.change("Added Discord Rich Presence")
			
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
		onFx { c.show(App.stage) }
	}
	
	override fun showError(error: Throwable, title: String) {
		logger.error("$title: $error")
		onFx {
			val dialog = ExceptionDialog(error)
			dialog.initOwner(App.stage)
			dialog.headerText = title
			dialog.show()
		}
	}
	
}
