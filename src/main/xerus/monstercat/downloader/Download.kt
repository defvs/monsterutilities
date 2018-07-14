package xerus.monstercat.downloader

import com.google.common.io.CountingInputStream
import javafx.concurrent.Task
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

fun Release.path(): Path = if(isMulti) folder() else folder().resolve(ReleaseFile("${renderedArtists.nullIfEmpty() ?: "Monstercat"} - 1 $title").toFileName().addFormatSuffix())

fun Track.path(): Path = folder().createDirs().resolve(toFileName().addFormatSuffix())

private inline val basePath
	get() = DOWNLOADDIR()

private fun String.addFormatSuffix() = "$this.${QUALITY().split('_')[0]}"

abstract class Download(val item: MusicItem, val coverUrl: String) : Task<Unit>() {
	
	init {
		@Suppress("LEAKINGTHIS")
		updateTitle(item.toString())
	}
	
	override fun call() = download()
	
	abstract fun download()
	
	var length: Long = 0
		private set
	
	private lateinit var con: APIConnection
	private lateinit var cis: CountingInputStream
	
	protected fun <T : InputStream> createConnection(releaseId: String, streamConverter: (InputStream) -> T, vararg queries: String): T {
		con = APIConnection("release", releaseId, "download").addQueries("method=download", "type=" + QUALITY(), *queries)
		val entity = con.getResponse().entity
		length = entity.contentLength
		if (length == 0L)
			throw EmptyResponseException(releaseId)
		updateProgress(0, length)
		val input = streamConverter(entity.content)
		cis = CountingInputStream(input)
		return input
	}
	
	override fun failed() = abort()
	
	protected fun abort() = con.abort()
	
	private val buffer = ByteArray(1024)
	protected fun downloadFile(path: Path) {
		val file = path.toFile()
		logger.finest("Downloading $file")
		updateMessage(file.name)
		val partFile = file.resolveSibling(file.name + ".part")
		val output = FileOutputStream(partFile)
		try {
			var length = cis.read(buffer)
			while (length > 0) {
				output.write(buffer, 0, length)
				updateProgress(cis.count)
				if (isCancelled) {
					output.close()
					partFile.delete()
					return
				}
				length = cis.read(buffer)
			}
			if(!isCancelled) {
				file.delete()
				partFile.renameTo(file)
			}
		} catch (e: Exception) {
			logger.throwing("Download", "downloadFile", e)
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
		val path = folder.resolveSibling(folder.fileName.toString() + ".part").createDirs()
		val zis = createConnection(release.id, { ZipInputStream(it) })
		zip@ do {
			val entry = zis.nextEntry ?: break
			if (entry.isDirectory)
				continue
			val name = entry.name.split("/").last()
			if (name == "cover.false" || !release.isMulti && name.contains("cover."))
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
		if(!isCancelled) {
			folder.toFile().deleteRecursively()
			path.renameTo(folder)
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
