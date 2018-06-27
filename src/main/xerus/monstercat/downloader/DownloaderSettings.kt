package xerus.monstercat.downloader

import xerus.monstercat.logger
import xerus.ktutil.preferences.SettingsNode
import java.nio.file.Paths

object DownloaderSettings : SettingsNode("xerus/monsterutilities/downloader")

val DOWNLOADDIR = DownloaderSettings.create("directory", Paths.get("Monstercat"))
val SINGLEFOLDER = DownloaderSettings.create("directorySingles")
val ALBUMFOLDER = DownloaderSettings.create("directoryAlbums", "{%renderedArtists% - }%title%")
val PODCASTFOLDER = DownloaderSettings.create("directoryPodcasts", "Podcast")
val MIXESFOLDER = DownloaderSettings.create("directoryMixes", "Mixes")

val ALBUMMIXES = DownloaderSettings.create("albummixes", "Include")
val TRACKNAMEPATTERN = DownloaderSettings.create("namepatternTrack", trackPatterns[0])
val ALBUMTRACKNAMEPATTERN = DownloaderSettings.create("namepatternAlbumtrack", albumTrackPatterns[0])

//val HIDEDOWNLOADED = DownloaderSettings.create("hidedownloaded", false)

val QUALITY = DownloaderSettings.create("quality")
val CONNECTSID = DownloaderSettings.create("connect.sid")

val DOWNLOADTHREADS = DownloaderSettings.create("threads", Runtime.getRuntime().availableProcessors().minus(1).coerceIn(1, 3))

