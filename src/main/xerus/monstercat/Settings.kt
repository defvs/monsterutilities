package xerus.monstercat

import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.scene.control.Alert
import javafx.scene.control.ButtonBar
import xerus.ktutil.containsAny
import xerus.monstercat.logger
import xerus.ktutil.createDirs
import xerus.ktutil.exists
import xerus.ktutil.javafx.applySkin
import xerus.ktutil.javafx.properties.listen
import xerus.ktutil.preferences.SettingsNode
import xerus.ktutil.preferences.multiSeparator
import xerus.monstercat.tabs.FetchTab
import xerus.monstercat.tabs.availableColumns
import xerus.monstercat.tabs.defaultColumns
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

val cacheDir: File
	get() = (File("/var/tmp").takeIf { it.exists() } ?: File(System.getProperty("java.io.tmpdir")))
			.resolve("monsterutilities").apply { mkdirs() }

object Settings : SettingsNode("xerus/monsterutilities") {
	
	val PLAYERVOLUME = create("playerVolume", 0.4)
	val PLAYERSCROLLSENSITIVITY = create("playerSeekbarScrollSensitivity", 6.0)
	val PLAYERSEEKBARHEIGHT = create("playerSeekbarHeight", 8.0)
	val ENABLEEQUALIZER = create("equalizerEnabled", false)
	
	val ENABLECACHE = create("cacheEnabled", true)
	
	val STARTUPTAB = create("tabStartup", "Previous")
	val LASTTAB = create("tabLast")
	
	val LASTCATALOGCOLUMNS = create("catalogLastColumns", availableColumns)
	val VISIBLECATALOGCOLUMNS = create("catalogVisibleColumns", defaultColumns)
	val GENRECOLORS = create("genrecolors", 80)
	
	val SKIN = create("skin", "black")
	
	val LASTVERSION = create("versionLast")
	val IGNOREVERSION = create("versionIgnore")
	val DELETE = create("versionDelete", File(""))
	
	val AUTOUPDATE = create("updateAutomatic", true)
	val UNSTABLE = create("updateUnstable", false)
	
	val FILENAMEPATTERN = create("updatePattern", "MonsterUtilities-%version%.jar")
	
	init {
		Settings.ENABLECACHE.listen { selected ->
			logger.fine("Cache " + (if (selected) "en" else "dis") + "abled")
			if (selected)
				FetchTab.writeCache()
		}
		
		Settings.UNSTABLE.addListener(object : ChangeListener<Boolean> {
			override fun changed(o: ObservableValue<out Boolean>, old: Boolean, new: Boolean) {
				if (new) {
					val alert = monsterUtilities.showAlert(Alert.AlertType.CONFIRMATION, title = "Are you sure?",
							content = "Unstable builds contain the latest features and fixes, but may also introduce unexpected bugs, regressions and incompatible changes. Use at your own risk!\n" +
									"The unstable version can be used alongside the stable one and will forcibly update itself whenever possible.")
					alert.resultProperty().addListener { _ ->
						if (alert.result.buttonData == ButtonBar.ButtonData.YES) {
							monsterUtilities.checkForUpdate(true, true)
						} else {
							Settings.UNSTABLE.removeListener(this)
							Settings.UNSTABLE.set(false)
							Settings.UNSTABLE.addListener(this)
						}
					}
				}
			}
		})
		
		Settings.SKIN.listen { monsterUtilities.scene.applySkin(it) }
	}
	
}
