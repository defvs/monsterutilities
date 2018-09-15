package xerus.monstercat.downloader

import com.google.common.io.CountingInputStream
import javafx.concurrent.Task
import mu.KotlinLogging
import xerus.ktutil.*
import xerus.monstercat.api.APIConnection
import xerus.monstercat.api.response.MusicItem
import xerus.monstercat.api.response.Release
import xerus.monstercat.api.response.Track
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Path
import java.util.zip.ZipInputStream

fun MusicItem.downloadTask() = when (this) {
	is Track -> TrackDownload(this)
	is Release -> ReleaseDownload(this)
	else -> throw NoWhenBranchMatchedException()
}

fun MusicItem.folder(): Path = basePath.resolve(when {
	this !is Release -> TRACKFOLDER() // Track
	isMulti -> toString(ALBUMFOLDER()).replaceIllegalFileChars() // Album, Monstercat Collection
	type == "Podcast" -> PODCASTFOLDER()
	type == "Mixes" -> MIXESFOLDER()
	else -> SINGLEFOLDER() // Single
})

fun Release.path(): Path = if (isMulti) folder() else folder().resolve(ReleaseFile("${renderedArtists.nullIfEmpty()
		?: "Monstercat"} - 1 $title").toFileName().addFormatSuffix())

fun Track.path(): Path = folder().createDirs().resolve(toFileName().addFormatSuffix())

private inline val basePath
	get() = DOWNLOADDIR()

private fun String.addFormatSuffix() = "$this.${QUALITY().split('_')[0]}"

private val logger = KotlinLogging.logger { }

abstract class Download(val item: MusicItem, val coverUrl: String) : Task<Unit>() {
	
	init {
		@Suppress("LEAKINGTHIS")
		updateTitle(item.toString())
	}
	
	override fun call() = download()
	
	abstract fun download()
	
	var length: Long = 0
		private set
	
	private lateinit var connection: APIConnection
	private lateinit var inputStream: CountingInputStream
	
	protected fun <T : InputStream> createConnection(releaseId: String, streamConverter: (InputStream) -> T, vararg queries: String): T {
		connection = APIConnection("release", releaseId, "download").addQueries("method=download", "type=" + QUALITY(), *queries)
		val entity = connection.getResponse().entity
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
		if (::connection.isInitialized)
			connection.abort()
	}
	
	private val buffer = ByteArray(1024)
	protected fun downloadFile(path: Path) {
		val file = path.toFile()
		logger.trace("Downloading $file")
		updateMessage(file.name)
		val partFile = file.resolveSibling(file.name + ".part")
		val output = FileOutputStream(partFile)
		try {
			var length = inputStream.read(buffer)
			while (length > 0) {
				output.write(buffer, 0, length)
				updateProgress(inputStream.count)
				if (isCancelled) {
					output.close()
					partFile.delete()
					return
				}
				length = inputStream.read(buffer)
			}
			if (!isCancelled) {
				file.delete()
				partFile.renameTo(file)
			}
		} catch (e: Exception) {
			logger.error("Error while downloading to $path", e)
		} finally {
			output.close()
		}
	}
	
	private fun updateProgress(progress: Long) = updateProgress(progress, length)
	
	override fun toString(): String = "Download(item=$item)"
	
}

class ReleaseDownload(private val release: Release) : Download(release, release.coverUrl) {
	
	override fun download() {
		val folder = release.folder()
		val part = folder.resolveSibling(folder.fileName.toString() + ".part")
		val path =
				if (!folder.exists())
					part.createDirs()
				else
					folder
		val zis = createConnection(release.id, { ZipInputStream(it) })
		// TODO EPS_TO_SINGLES
		zip@ do {
			val entry = zis.nextEntry ?: break
			if (entry.isDirectory)
				continue
			val name = entry.name.split("/").last()
			if (name == "cover.false" || (name.contains("cover.") && (DOWNLOADCOVERS() == 0 || DOWNLOADCOVERS() == 1 && !release.isMulti)))
				continue
			val target = when {
				name.contains("cover.") -> path.resolve(name)
				name.contains("Album Mix") ->
					when (ALBUMMIXES()) {
						"Separate" -> resolve(name, basePath.resolve("Mixes").createDirs())
						"Exclude" -> continue@zip
						else -> resolve(name, path)
					}
				else -> resolve(name, path)
			}
			downloadFile(target)
		} while (!isCancelled)
		if (!isCancelled && part.exists()) {
			folder.toFile().deleteRecursively()
			part.renameTo(folder)
		}
	}
	
	private fun resolve(name: String, path: Path = basePath): Path =
			path.resolve(ReleaseFile(name, release.title.takeIf { release.isMulti }).toFileName().addFormatSuffix())
	
}

class TrackDownload(private val track: Track) : Download(track, APIConnection("catalog", "release", track.alb.albumId).parseJSON(Release::class.java).coverUrl) {
	
	override fun download() {
		createConnection(track.alb.albumId, { it }, "track=" + track.id)
		downloadFile(track.path())
		abort()
	}
	
}

class EmptyResponseException(term: String) : Exception("No file found for $term!")
