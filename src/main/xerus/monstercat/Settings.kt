package xerus.monstercat

import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.scene.control.Alert
import javafx.scene.control.ButtonBar
import mu.KotlinLogging
import xerus.ktutil.javafx.applyTheme
import xerus.ktutil.javafx.Themes
import xerus.ktutil.javafx.properties.listen
import xerus.ktutil.preferences.SettingsNode
import xerus.monstercat.tabs.FetchTab
import xerus.monstercat.tabs.TabSettings
import xerus.monstercat.tabs.availableColumns
import xerus.monstercat.tabs.defaultColumns
import java.io.File

object Settings : SettingsNode("xerus/monsterutilities") {
	private val logger = KotlinLogging.logger { }
	
	val PLAYERVOLUME = create("playerVolume", 0.4)
	val PLAYERSCROLLSENSITIVITY = create("playerSeekbarScrollSensitivity", 6.0)
	val PLAYERSEEKBARHEIGHT = create("playerSeekbarHeight", 8.0)
	val ENABLEEQUALIZER = create("equalizerEnabled", false)
	
	val PLAYERARTPRIORITY = create("coverartPriorityList", TabSettings.PriorityList.SGL_ALB_COL.priorities) {
		it.removeSurrounding("[","]").split(", ")
	}
	
	val ENABLECACHE = create("cacheEnabled", true)
	
	val STARTUPTAB = create("tabStartup", "Previous")
	val LASTTAB = create("tabLast")
	
	val LASTCATALOGCOLUMNS = create("catalogLastColumns", availableColumns)
	val VISIBLECATALOGCOLUMNS = create("catalogVisibleColumns", defaultColumns)
	val GENRECOLORINTENSITY = create("genrecolors", 80)
	
	val THEME = create("theme", Themes.BLACK)
	
	val LASTVERSION = create("versionLast")
	val IGNOREVERSION = create("versionIgnore")
	val DELETE = create("versionDelete", File(""))
	
	val AUTOUPDATE = create("updateAutomatic", true)
	val UNSTABLE = create("updateUnstable", false)
	
	val FILENAMEPATTERN = create("updatePattern", "MonsterUtilities-%version%.jar")
	
	init {
		ENABLECACHE.listen { selected ->
			logger.debug("Cache " + (if (selected) "en" else "dis") + "abled")
			if (selected)
				FetchTab.writeCache()
		}
		
		UNSTABLE.addListener(object : ChangeListener<Boolean> {
			override fun changed(o: ObservableValue<out Boolean>, old: Boolean, new: Boolean) {
				if (new) {
					val alert = monsterUtilities.showAlert(Alert.AlertType.CONFIRMATION, title = "Are you sure?",
						content = "Unstable builds contain the latest features and fixes, but may also introduce unexpected bugs, regressions and incompatible changes. Use at your own risk!\n" +
							"The unstable version can be used alongside the stable one and will forcibly update itself whenever possible.")
					alert.resultProperty().listen {
						if (alert.result.buttonData == ButtonBar.ButtonData.YES) {
							monsterUtilities.checkForUpdate(true, true)
						} else {
							UNSTABLE.removeListener(this)
							UNSTABLE.set(false)
							UNSTABLE.addListener(this)
						}
					}
				}
			}
		})
		
		THEME.listen { monsterUtilities.scene.applyTheme(it) }
	}
	
}
