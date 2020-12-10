package xerus.monstercat.downloader

import xerus.ktutil.helpers.Named
import xerus.ktutil.preferences.SettingsNode
import xerus.monstercat.api.response.Release
import java.nio.file.Paths

object DownloaderSettings : SettingsNode("xerus/monsterutilities/downloader")

val SONGSORTING = DownloaderSettings.create("songViewReleaseSorting", ReleaseSorting.DATE)

enum class ReleaseSorting(val selector: (Release) -> String) : Named {
	DATE(Release::releaseDate),
	TITLE(Release::title),
	ARTIST(Release::artistsTitle),
	AMOUNT_OF_TRACKS({ it.tracks.size.toString().padStart(2, '0') });
	
	override val displayName = name.replace('_', ' ').toLowerCase().capitalize()
}

val DOWNLOADDIR = DownloaderSettings.create("directory", Paths.get("Monstercat"))
val DOWNLOADDIRSINGLE = DownloaderSettings.create("directorySingle")
val DOWNLOADDIRALBUM = DownloaderSettings.create("directoryAlbum", "{%artistsTitle% - }%title%")
val DOWNLOADDIRPODCAST = DownloaderSettings.create("directoryPodcasts", "Podcast")
val DOWNLOADDIRMIXES = DownloaderSettings.create("directoryMixes", "Mixes")

val TRACKNAMEPATTERN = DownloaderSettings.create("namepatternTrack", trackPatterns[0])
val ALBUMTRACKNAMEPATTERN = DownloaderSettings.create("namepatternAlbumtrack", trackPatterns[0])

val EPSTOSINGLES = DownloaderSettings.create("epsAsSingles", 0)
val DOWNLOADCOVERS = DownloaderSettings.create("coverDownload", DownloadCovers.COLLECTIONS)

enum class DownloadCovers(override val displayName: String) : Named {
	NONE("Nothing"),
	COLLECTIONS("Collections"),
	ALL("Collections & Singles")
}

val ALBUMMIXES = DownloaderSettings.create("albummixes", AlbumMixes.KEEP)

enum class AlbumMixes(override val displayName: String) : Named {
	KEEP("Keep"),
	SEPARATE("Separate"),
	EXCLUDE("Exclude")
}


val COVERARTSIZE = DownloaderSettings.create("coverArtSize", 1024)
val COVERPATTERN = DownloaderSettings.create("coverPattern", "{%artistsTitle% - }%title%")

val QUALITY = DownloaderSettings.create("quality")
val CONNECTSID = DownloaderSettings.create("cid")

val DOWNLOADTHREADS = DownloaderSettings.create("threads", Runtime.getRuntime().availableProcessors().minus(1).coerceIn(2, 4))

val LASTDOWNLOADTIME = DownloaderSettings.create("lastdownloadtime", 0)
