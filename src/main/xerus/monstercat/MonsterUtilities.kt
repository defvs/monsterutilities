package xerus.monstercat

import com.beust.klaxon.JsonArray
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import com.google.api.client.json.Json
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import javafx.application.Platform
import javafx.concurrent.Task
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.input.MouseEvent
import javafx.scene.layout.VBox
import javafx.util.Duration
import javafx.stage.Screen
import javafx.stage.StageStyle
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.controlsfx.dialog.ExceptionDialog
import xerus.ktutil.byteCountString
import xerus.ktutil.copyTo
import xerus.ktutil.currentSeconds
import xerus.ktutil.javafx.*
import xerus.ktutil.javafx.controlsfx.progressDialog
import xerus.ktutil.javafx.controlsfx.stage
import xerus.ktutil.javafx.properties.listen
import xerus.ktutil.javafx.ui.App
import xerus.ktutil.javafx.ui.Changelog
import xerus.ktutil.javafx.ui.JFXMessageDisplay
import xerus.ktutil.javafx.ui.SimpleTransition
import xerus.ktutil.javafx.ui.stage
import xerus.ktutil.to
import xerus.monstercat.api.Cache
import xerus.monstercat.api.Covers
import xerus.monstercat.api.DiscordRPC
import xerus.monstercat.api.Player
import xerus.monstercat.downloader.TabDownloader
import xerus.monstercat.tabs.*
import java.awt.Desktop
import java.io.File
import java.io.InputStream
import java.net.URI
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
		listOf(
			TabCatalog::class,
			TabGenres::class,
			TabDownloader::class,
			TabSound::class,
			TabPlaylist::class,
			TabSettings::class
		).forEach { addTab(it) }
		if(currentVersion != Settings.LASTVERSION.get()) {
			if(Settings.LASTVERSION().isEmpty()) {
				logger.info("First launch! Showing tutorial!")
				showIntro()
				Settings.LASTVERSION.put(currentVersion)
			} else {
				GlobalScope.launch {
					logger.info("New version! Now running $currentVersion, previously " + Settings.LASTVERSION())
					Settings.LASTVERSION.put(currentVersion)
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
	
	private fun String.devVersion() = takeIf { it.startsWith("dev") }?.split('v', '-')?.getOrNull(1)?.toIntOrNull()
	
	suspend fun fetchJson(url: String) = Parser.default().parse(URL(url).openConnection().getInputStream())
	
	fun checkForUpdate(userControlled: Boolean = false, unstable: Boolean = isUnstable) {
		GlobalScope.launch {
			try {
				val jsonObject = fetchJson("https://api.github.com/repos/Xerus2000/monsterutilities/releases/latest") as? JsonObject
				val latestVersion = jsonObject?.string("tag_name")
				val versionName = jsonObject?.string("name")
				val githubReleaseUrl = jsonObject?.string("html_url") ?: "https://github.com/xerus2000/monsterutilities/releases"
				if(latestVersion == null || latestVersion.length > 50 || latestVersion == currentVersion || (!userControlled && latestVersion == Settings.IGNOREVERSION()) || latestVersion.devVersion()?.let { currentVersion.devVersion()!! > it } == true) {
					if(userControlled)
						showMessage("No update found!", "Updater", Alert.AlertType.INFORMATION)
					return@launch
				}
				logger.info("Latest release: $latestVersion / $versionName")
				onFx {
					val dialog = showAlert(Alert.AlertType.CONFIRMATION, "Updater", "New version $latestVersion available", "\n$versionName\nUpdate now?", ButtonType.YES, ButtonType("Not now", ButtonBar.ButtonData.NO), ButtonType("Ignore this update", ButtonBar.ButtonData.CANCEL_CLOSE))
					dialog.stage.icons.setAll(Image("img/updater.png"))
					dialog.resultProperty().listen { type ->
						when(type.buttonData) {
							ButtonBar.ButtonData.YES -> {
								if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
									Desktop.getDesktop().browse(URI(githubReleaseUrl))
								else showAlert(Alert.AlertType.INFORMATION, "Updater", "Could not open Browser", "Please browse to the release manually:\n$githubReleaseUrl", ButtonType.OK)
							}
							ButtonBar.ButtonData.CANCEL_CLOSE -> Settings.IGNOREVERSION.set(latestVersion)
							else -> {}
						}
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
		GlobalScope.launch {
			val releasesArray = fetchJson("https://api.github.com/repos/xerus2000/monsterutilities/releases") as JsonArray<*>
			val c = Changelog()
			releasesArray.forEach {
				if (it is JsonObject) {
					c.version(it.string("tag_name") ?: "", it.string("name") ?: "", it.string("body") ?: "")
				}
			}
			onFx { c.show(App.stage) }
		}
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
	
	/** Shows a new window with an ImageView of the requested [coverUrl]
	 * @param coverUrl URL of the cover to download and show
	 * @param title Title of the window, only useful when decorated
	 * @param size Height and width in pixel of the window and image
	 * @param isDecorated True if the window has borders and title bar with close controls
	 * @param isDraggable True if the window can be dragged by the mouse
	 * @param closeOnFocusLost Should we close the window if we're out of focus ?
	 * @param isResizable Allow resizing the window. The image will follow.
	 */
	fun viewCover(coverUrl: String, size: Double? = null, title: String = "Cover Art", isDecorated: Boolean = false, isDraggable: Boolean = true, closeOnFocusLost: Boolean = true, isResizable: Boolean = false){
		val windowSize: Double = size ?: minOf(Screen.getPrimary().visualBounds.width, Screen.getPrimary().visualBounds.height) / 2
		
		val pane = StackPane()
		val largeImage = ImageView()
		pane.add(Label("Cover loading..."))
		pane.add(largeImage)
		
		App.stage.createStage(title, pane).apply {
			height = windowSize
			width = windowSize
			this.isResizable = isResizable
			
			widthProperty().addListener { _, _, newValue ->
				largeImage.fitHeight = newValue as Double
				largeImage.fitWidth = newValue
			}
			
			initStyle(if (isDecorated) StageStyle.DECORATED else StageStyle.UNDECORATED)
			
			if (isDraggable) {
				var xOffset = 0.0
				var yOffset = 0.0
				pane.onMousePressed = EventHandler<MouseEvent> { event ->
					xOffset = event.sceneX
					yOffset = event.sceneY
				}
				pane.onMouseDragged = EventHandler<MouseEvent> { event ->
					this.x = event.screenX - xOffset
					this.y = event.screenY - yOffset
				}
			}
			
			if (closeOnFocusLost) {
				focusedProperty().addListener { _, _, newFocus ->
					if (!newFocus) close()
				}
			}
			show()
		}
		
		GlobalScope.launch {
			largeImage.image = Covers.getCoverImage(coverUrl, windowSize.toInt())
		}
	}
	
}
