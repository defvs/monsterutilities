package xerus.monstercat.downloader

import xerus.ktutil.preferences.SettingsNode
import java.nio.file.Paths

object DownloaderSettings : SettingsNode("xerus/monsterutilities/downloader")

val DOWNLOADDIR = DownloaderSettings.create("directory", Paths.get("Monstercat"))
val DOWNLOADDIRSINGLE = DownloaderSettings.create("directorySingle")
val DOWNLOADDIRALBUM = DownloaderSettings.create("directoryAlbum", "{%renderedArtists% - }%title%")
val DOWNLOADDIRPODCAST = DownloaderSettings.create("directoryPodcasts", "Podcast")
val DOWNLOADDIRMIXES = DownloaderSettings.create("directoryMixes", "Mixes")

val ALBUMMIXES = DownloaderSettings.create("albummixes", "Include")
val TRACKNAMEPATTERN = DownloaderSettings.create("namepatternTrack", trackPatterns[0])
val ALBUMTRACKNAMEPATTERN = DownloaderSettings.create("namepatternAlbumtrack", albumTrackPatterns[0])

val EPS_TO_SINGLES = DownloaderSettings.create("epsAsSingles", 0)
val DOWNLOADCOVERS = DownloaderSettings.create("downloadCovers", 1)

val QUALITY = DownloaderSettings.create("quality")
val CONNECTSID = DownloaderSettings.create("connect.sid")


val DOWNLOADTHREADS = DownloaderSettings.create("threads", Runtime.getRuntime().availableProcessors().minus(1).coerceIn(1, 3))

val LASTDOWNLOADTIME = DownloaderSettings.create("lastdownloadtime", 0)