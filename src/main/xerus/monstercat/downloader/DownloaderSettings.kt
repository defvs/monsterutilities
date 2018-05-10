package xerus.monstercat.downloader

import xerus.ktutil.preferences.SettingsNode
import java.nio.file.Paths

object DownloaderSettings : SettingsNode("xerus/monsterutilities/downloader")

val DOWNLOADDIR = DownloaderSettings.create("directory", Paths.get("Monstercat"))
val SINGLEFOLDER = DownloaderSettings.create("directorySingles")
val ALBUMFOLDER = DownloaderSettings.create("directoryAlbums", "{%renderedArtists% - }%title%")

val ALBUMMIXES = DownloaderSettings.create("albummixes", "Include")
val TRACKNAMEPATTERN = DownloaderSettings.create("namepatternTrack", trackPatterns[0])
val ALBUMTRACKNAMEPATTERN = DownloaderSettings.create("namepatternAlbumtrack", albumTrackPatterns[0])

//val HIDEDOWNLOADED = DownloaderSettings.create("hidedownloaded", false)

val QUALITY = DownloaderSettings.create("quality")
val CONNECTSID = DownloaderSettings.create("connect.sid")

val DOWNLOADTHREADS = DownloaderSettings.create("threads", 3)

