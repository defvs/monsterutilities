package xerus.monstercat

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.services.sheets.v4.SheetsScopes
import javafx.application.Platform
import javafx.concurrent.Task
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.layout.VBox
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import org.controlsfx.dialog.ExceptionDialog
import org.controlsfx.dialog.ProgressDialog
import xerus.ktutil.*
import xerus.ktutil.javafx.applySkin
import xerus.ktutil.javafx.fill
import xerus.ktutil.javafx.launch
import xerus.ktutil.javafx.onJFX
import xerus.ktutil.javafx.properties.listen
import xerus.ktutil.javafx.ui.App
import xerus.ktutil.javafx.ui.Changelog
import xerus.ktutil.javafx.ui.JFXMessageDisplay
import xerus.ktutil.ui.SimpleFrame
import xerus.monstercat.api.Player
import xerus.monstercat.downloader.TabDownloader
import xerus.monstercat.tabs.BaseTab
import xerus.monstercat.tabs.TabCatalog
import xerus.monstercat.tabs.TabGenres
import xerus.monstercat.tabs.TabSettings
import java.io.File
import java.net.URL
import java.util.*
import javax.swing.JTextArea
import kotlin.reflect.KClass

typealias logger = XerusLogger

private const val VERSION = "1.0.0"
private val isUnstable = VERSION.split('.').size > 3

val logDir
    get() = cachePath.resolve("logs").create().toFile()

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
            if (logs.size > 5) {
                logs.asSequence().sortedByDescending { it.name }.drop(2).filter {
                    val timestamp = it.nameWithoutExtension.substring(3).toIntOrNull() ?: return@filter true
                    timestamp + 100_000 < currentSeconds()
                }.also { logger.finer("Deleting ${it.count()} old logs") }.forEach { it.delete() }
            }
        }
    } catch (t: Throwable) {
        showErrorSafe(t, "Can't log to $logfile!")
    }
    // Only Java 8 works
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
        stage.icons.add(Image(getResource("favicon-glowing.png")?.toExternalForm()))
    }, {
        val scene = Scene(MonsterUtilities(), 800.0, 600.0)
        scene.applySkin(Settings.SKIN())
        scene
    })
}

fun showErrorSafe(error: Throwable, title: String = "Error") {
    launch {
        var i = 0
        while (i < 100) {
            try {
                monsterUtilities.tabs
                break
            } catch (t: Throwable) {
            }
            delay(400)
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

        val addTab = { tabClass: KClass<out BaseTab> ->
            try {
                val baseTab = tabClass.java.newInstance()
                logger.finer("New Tab: " + baseTab)
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
                onJFX {
                    // TODO intro dialog
                }
                Settings.LASTVERSION.put(VERSION)
            } else {
                launch {
                    logger.fine("New version detected! $VERSION from " + Settings.LASTVERSION())
                    val f = Settings.DELETE()
                    if (f.exists()) {
                        logger.config("Deleting older version $f...")
                        val time = currentSeconds()
                        var res = false
                        do {
                            res = f.delete()
                        } while (!res && time + 10 > currentSeconds())
                        if (res) {
                            Settings.DELETE.reset()
                            logger.config("Deleted $f!")
                        } else
                            logger.config("Couldn't delete older version $f")
                    }
                    Settings.LASTVERSION.put(VERSION)
                }
                showChangelog()
            }
        }

        children.add(Player.box)
        fill(tabPane)
        checkForUpdate()
    }

    inline fun <reified T : BaseTab> findTabs() = tabs.map { it as? T }.filterNotNull()

    fun checkForUpdate(userControlled: Boolean = false, unstable: Boolean = isUnstable) {
        // todo unstable builds
        launch {
            try {
                val latestVersion = URL("http://monsterutilities.bplaced.net/downloads/" + if (unstable) "unstable/latest.html" else "latest").openConnection().getInputStream().reader().readLines().firstOrNull()
                logger.fine("Latest version: $latestVersion")
                if (latestVersion == null || latestVersion.length > 20 || latestVersion == VERSION || (!userControlled && latestVersion == Settings.IGNOREVERSION())) {
                    if (userControlled)
                        showMessage("No update found!", "Updater", Alert.AlertType.INFORMATION)
                    return@launch
                }
                if (unstable)
                    update(latestVersion, true)
                else
                    onJFX {
                        val dialog = showAlert(Alert.AlertType.CONFIRMATION, "Updater", null, "New version $latestVersion available! Update now?", ButtonType.YES, ButtonType("Not now", ButtonBar.ButtonData.NO), ButtonType("Ignore this update", ButtonBar.ButtonData.CANCEL_CLOSE))
                        dialog.resultProperty().listen { type ->
                            if (type.buttonData == ButtonBar.ButtonData.YES) {
                                update(latestVersion)
                            } else if (type.buttonData == ButtonBar.ButtonData.CANCEL_CLOSE)
                                Settings.IGNOREVERSION.set(latestVersion)
                        }
                    }
            } catch (e: Exception) {
                if (userControlled)
                    showMessage("No connection possible!", "Updater", Alert.AlertType.INFORMATION)
            }
        }
    }

    private fun update(version: String, unstable: Boolean = false) {
        // todo patterns normal/unstable
        val file = File("MonsterUtilities $version.jar").absoluteFile
        logger.fine("Update initiated to $file")
        val worker = object : Task<Unit>() {
            override fun call() {
                val connection = URL("http://monsterutilities.bplaced.net/downloads/" + if (unstable) "unstable" else version).openConnection()
                val contentLength = connection.contentLengthLong
                val inputStream = connection.getInputStream()
                logger.fine("Update to $version started")
                inputStream.copyTo(file.outputStream(), true, true) {
                    updateProgress(it, contentLength)
                    isCancelled
                }
                if (isCancelled)
                    logger.config("Update cancelled, deleting $file: ${file.delete()}")
            }

            // restart
            override fun succeeded() {
                if (isUnstable == unstable)
                    Settings.DELETE.set(File(MonsterUtilities::class.java.protectionDomain.codeSource.location.toURI()))
                App.stage.close()
                logger.info("Exiting for update")
                Platform.exit()
                Settings.flush()
                Runtime.getRuntime().exec("java -jar \"$file\"")
            }
        }
        worker.launch()
        val progressDialog = ProgressDialog(worker)
        progressDialog.title = "Updater"
        progressDialog.headerText = "Downloading Update"
        progressDialog.contentText = "Downloading $file to ${file.absoluteFile.parent}"
        progressDialog.dialogPane.scene.window.setOnCloseRequest { worker.cancel() }
        progressDialog.initOwner(App.stage)
        progressDialog.show()
    }

    fun showChangelog() {
        val c = Changelog("Note: The Catalog and Genres Tab pull their data from the MCatalog Spreadsheet, thus issues may stem from their side.").apply {
            version(1, 0, "Release", "Brand new shiny favicon and player buttons - big thanks to NocFA!",
                    "Added tutorial", "Feedback can now be sent directly from the application!")
                    .change("New Downloader!", "Can download any combinations of Releases and Tracks", "Easy filtering", "Validates connect.sid while typing", "Two distinct filename patterns for Singles and Album tracks", "Greatly improved pattern syntax with higher flexibility")
                    .change("Settings reworked", "Multiple skins available, changeable on-the-fly", "Startup Tab can now also be the previously opened one")
                    .change("Catalog and Genre Tab now show Genre colors")
                    .change("Catalog improved", "More filtering options", "Smart column size")
                    .change("Player now has a Seekbar")

            /*
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
            */
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
