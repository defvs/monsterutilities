package xerus.monstercat

import xerus.ktutil.create
import xerus.ktutil.preferences.SettingsNode
import xerus.ktutil.preferences.multiSeparator
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

val defaultColumns = arrayOf("Genre", "Artist(s)", "Track", "Length").joinToString(multiSeparator)
val availableColumns = arrayOf("Catalog #", "Date", "Genre", "Subgenre(s)", "Artist(s)", "Track", "BPM", "Length", "Key").joinToString(multiSeparator)

object Settings : SettingsNode("xerus/monstercat") {
	val ENABLECACHE = create("enableCache", true)
	
	val STARTUPTAB = create("startuptab", "Previous")
	val LASTTAB = create("lasttab")
	
	val LASTCATALOGCOLUMNS = create("catalogLastColumns", availableColumns)
	val VISIBLECATALOGCOLUMNS = create("catalogVisibleColumns", defaultColumns)
	val GENRECOLORS = create("genrecolors", 80)
	
	val SKIN = create("skin", "silver")
	
	val cachePath: Path
		get() = Paths.get(System.getProperty("java.io.tmpdir")).resolve("monsterutilities").create()

	val LASTVERSION = create("versionLast")
	val IGNOREVERSION = create("versionIgnore")
	val DELETEVERSION = create("versionDelete", File(""))
	val UNSTABLE = create("updateUnstable", false)

	val FILENAMEPATTERN = create("updatePattern", "MonsterUtilities {version}")
	val FILENAMEPATTERNUNSTABLE = create("updatePatternUnstable", "MonsterUtilities unstable {version}")
	
}
