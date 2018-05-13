package xerus.monstercat

import xerus.ktutil.createDirs
import xerus.ktutil.exists
import xerus.ktutil.preferences.SettingsNode
import xerus.ktutil.preferences.multiSeparator
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

val defaultColumns = arrayOf("Genre", "Artist(s)", "Track", "Length").joinToString(multiSeparator)
val availableColumns = arrayOf("Catalog #", "Date", "Genre", "Subgenre(s)", "Artist(s)", "Track", "BPM", "Length", "Key").joinToString(multiSeparator)

val cachePath: Path
	get() = (Paths.get("/var/tmp").takeIf { it.exists() } ?: Paths.get(System.getProperty("java.io.tmpdir")))
			.resolve("monsterutilities").createDirs()

object Settings : SettingsNode("xerus/monsterutilities") {
	val ENABLECACHE = create("cacheEnabled", true)
	
	val STARTUPTAB = create("tabStartup", "Previous")
	val LASTTAB = create("tabLast")
	
	val LASTCATALOGCOLUMNS = create("catalogLastColumns", availableColumns)
	val VISIBLECATALOGCOLUMNS = create("catalogVisibleColumns", defaultColumns)
	val GENRECOLORS = create("genrecolors", 80)
	
	val SKIN = create("skin", "silver")
	
	val LASTVERSION = create("versionLast")
	val IGNOREVERSION = create("versionIgnore")
	val DELETE = create("versionDelete", File(""))
	val UNSTABLE = create("updateUnstable", false)
	
	val FILENAMEPATTERN = create("updatePattern", "MonsterUtilities-%version%.jar")
	
}
