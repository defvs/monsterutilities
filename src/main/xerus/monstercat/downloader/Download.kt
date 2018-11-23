package xerus.monstercat.downloader

import com.google.common.io.CountingInputStream
import javafx.concurrent.Task
import kotlinx.coroutines.runBlocking
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
import java.net.URI
import java.nio.file.Path

fun getCover(coverUrl: String, size: Int? = null) =
	HttpClientBuilder.create().build().execute(HttpGet(coverUrl + size?.let { "?image_width=$it" }.orEmpty())).entity.content

fun Release.folder(): Path = basePath.resolve(when {
	isMulti -> toString(DOWNLOADDIRALBUM()).replaceIllegalFileChars() // Album, Monstercat Collection
	type == "Podcast" -> DOWNLOADDIRPODCAST()
	type == "Mixes" -> DOWNLOADDIRMIXES()
	else -> DOWNLOADDIRSINGLE()
})

fun Release.path(): Path = if (isMulti) folder() else folder().resolve(ReleaseFile("${renderedArtists.nullIfEmpty()
	?: "Monstercat"} - 1 $title").toFileName().addFormatSuffix())

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
	private lateinit var inputStream: CountingInputStream
	
	protected fun <T : InputStream> createConnection(releaseId: String, streamConverter: (InputStream) -> T, vararg queries: String): T {
		connection = APIConnection("release", releaseId, "download").addQueries("method=download", "type=" + QUALITY(), *queries)
		val entity = connection!!.getResponse().entity
		length = entity.contentLength
		if (length == 0L)
			throw EmptyResponseException(releaseId)
		updateProgress(0, length)
		val input = streamConverter(entity.content)
		inputStream = CountingInputStream(input)
		return input
	}
	
	override fun failed() = abort()
	
	protected fun abort() {
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
		if (isCancelled) {
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
		if (tracks == null)
			tracks = release.tracks
		val toSingle = tracks!!.size < EPS_TO_SINGLES()
		val folder = if (toSingle) basePath else release.folder()
		val partFolder = folder.resolveSibling(folder.fileName.toString() + ".part")
		val downloadFolder =
			if (folder.exists())
				folder
			else
				partFolder.createDirs()
		logger.debug("Downloading $release to $downloadFolder")
		if (DOWNLOADCOVERS() == 2 || DOWNLOADCOVERS() == 1 && release.isMulti && !toSingle) {
			getCover(release.coverUrl, 1024).copyTo(downloadFolder.resolve(release.toString(COVERPATTERN()).replaceIllegalFileChars() + "." + release.coverUrl.split(".").last()).toFile().outputStream())
		}
		tr@ for (track in tracks!!) {
			createConnection(release.id, { it }, "track=" + track.id)
			downloadFile(downloadFolder.resolve(track.toFileName().addFormatSuffix()))
			abort()
			if (isCancelled)
				break@tr
		}
		if (!isCancelled && partFolder.exists()) {
			folder.toFile().deleteRecursively()
			partFolder.renameTo(folder)
		}
	}
	
}

class EmptyResponseException(term: String) : Exception("No file found for $term!")
