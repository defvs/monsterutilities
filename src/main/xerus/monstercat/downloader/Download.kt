package xerus.monstercat.downloader

import com.google.common.io.CountingInputStream
import javafx.concurrent.Task
import mu.KotlinLogging
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import xerus.ktutil.*
import xerus.monstercat.api.APIConnection
import xerus.monstercat.api.response.MusicItem
import xerus.monstercat.api.response.Release
import xerus.monstercat.api.response.Track
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Path

fun getCover(coverUrl: String, size: Int? = null) =
	HttpClientBuilder.create().build().execute(HttpGet(
		coverUrl.replace("[", "%5B").replace("]", "%5D") + size?.let { "?image_width=$it" }.orEmpty())).entity.content

fun Release.folder(): Path = basePath.resolve(when {
	isMulti -> toString(DOWNLOADDIRALBUM()).replaceIllegalFileChars() // Album, Monstercat Collection
	type == "Podcast" -> DOWNLOADDIRPODCAST()
	type == "Mixes" -> DOWNLOADDIRMIXES()
	else -> DOWNLOADDIRSINGLE()
})

fun Release.path(): Path = if(isMulti) folder() else folder().resolve(
	ReleaseFile("${renderedArtists.nullIfEmpty() ?: "Monstercat"} - 1 $title").toFileName().addFormatSuffix())

private inline val basePath
	get() = DOWNLOADDIR()

private fun String.addFormatSuffix() = "$this.${QUALITY().split('_')[0]}"

private val logger = KotlinLogging.logger { }

abstract class Download(val item: MusicItem, val coverUrl: String) : Task<Unit>() {
	
	init {
		@Suppress("LEAKINGTHIS")
		updateTitle(item.toString())
	}
	
	override fun call() {
		startTime = System.currentTimeMillis()
		download()
	}
	
	abstract fun download()
	
	var startTime: Long = 0
		private set
	
	var length: Long = 0
		private set
	
	private var connection: APIConnection? = null
	@Suppress("UnstableApiUsage")
	private lateinit var inputStream: CountingInputStream
	
	protected fun <T : InputStream> createConnection(releaseId: String, streamConverter: (InputStream) -> T, vararg queries: String): T {
		connection = APIConnection("release", releaseId, "download").addQueries("method=download", "type=" + QUALITY(), *queries)
		val entity = connection!!.getResponse().entity
		length = entity.contentLength
		if(length == 0L)
			throw EmptyResponseException(releaseId)
		updateProgress(0, length)
		val input = streamConverter(entity.content)
		inputStream = CountingInputStream(input)
		return input
	}
	
	override fun failed() = closeConnection()
	
	protected fun closeConnection() {
		connection?.abort()
	}
	
	protected fun downloadFile(path: Path) {
		val file = path.toFile()
		logger.trace("Downloading $file")
		updateMessage(file.name)
		
		val partFile = file.resolveSibling(file.name + ".part")
		inputStream.copyTo(FileOutputStream(partFile), closeOut = true) {
			updateProgress(inputStream.count)
			isCancelled
		}
		if(isCancelled) {
			logger.trace("Download of $partFile cancelled, deleting: " + partFile.delete())
		} else {
			file.delete()
			logger.trace("Renamed $partFile to $file: " + partFile.renameTo(file))
		}
	}
	
	private fun updateProgress(progress: Long) = updateProgress(progress, length)
	
	override fun toString(): String = "Download(item=$item)"
	
}

class ReleaseDownload(private val release: Release, private var tracks: Collection<Track>?) : Download(release, release.coverUrl) {
	
	override fun download() {
		if(tracks == null)
			tracks = release.tracks
		val toSingle = tracks!!.size < EPS_TO_SINGLES()
		val folder = if(toSingle) basePath else release.folder()
		val partFolder = folder.resolveSibling(folder.fileName.toString() + ".part")
		val downloadFolder =
			if(folder.exists() || folder == basePath)
				folder
			else
				partFolder
		downloadFolder.createDirs()
		
		logger.debug("Downloading $release to $downloadFolder")
		var downloadedTracks = 0
		tr@ for(track in tracks!!) {
			var filename = downloadFolder.resolve(track.toFileName().addFormatSuffix())
			if(track.title.contains("Album Mix"))
				when(ALBUMMIXES()) {
					AlbumMixes.SEPARATE -> filename = basePath.resolve("Mixes").createDirs().resolve(track.toFileName().addFormatSuffix())
					AlbumMixes.EXCLUDE -> continue@tr
					else -> {
					}
				}
			createConnection(release.id, { it }, "track=" + track.id)
			downloadFile(filename)
			closeConnection()
			if(isCancelled)
				break@tr
			downloadedTracks++
		}
		if(downloadedTracks > 0 && (DOWNLOADCOVERS() == DownloadCovers.ALL || DOWNLOADCOVERS() == DownloadCovers.COLLECTIONS && release.isMulti && !toSingle)) {
			updateMessage("Cover")
			downloadFolder.resolve(release.toString(COVERPATTERN()).replaceIllegalFileChars() + "." + release.coverUrl.substringAfterLast('.'))
				.toFile().outputStream().use { out ->
					getCover(release.coverUrl, COVERARTSIZE()).use { it.copyTo(out) }
				}
			
		}
		
		if(!isCancelled && partFolder.exists()) {
			// this Release has not been downloaded to the root, thus delete the original subfolder if it exists and move the part-folder to it
			folder.toFile().deleteRecursively()
			try {
				partFolder.renameTo(folder)
			} catch(e: Exception) {
				logger.debug("Couldn't rename $partFolder to $folder as Paths: $e\nUsing File: " + partFolder.toFile().renameTo(folder.toFile()))
			}
		}
	}
	
}

class EmptyResponseException(term: String) : Exception("No file found for $term!")
