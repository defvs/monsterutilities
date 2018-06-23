package xerus.monstercat.downloader

import com.google.common.io.CountingInputStream
import javafx.concurrent.Task
import xerus.ktutil.createDirs
import xerus.ktutil.replaceIllegalFileChars
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

fun Release.path() = basePath.resolve(when {
	isMulti -> toString(ALBUMFOLDER()).replaceIllegalFileChars() // Album, Monstercat Collection
	type == "Podcast" -> PODCASTFOLDER()
	type == "Mixes" -> MIXESFOLDER()
	else -> SINGLEFOLDER() // Single
})

fun Track.path() = basePath.resolve(SINGLEFOLDER()).createDirs().resolve(addFormat(toFileName()))

private inline val basePath
	get() = DOWNLOADDIR()

private fun addFormat(fileName: String) = "$fileName.${QUALITY().split('_')[0]}"

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
		val output = FileOutputStream(file)
		try {
			while (true) {
				val length = cis.read(buffer)
				if (length < 1) return
				output.write(buffer, 0, length)
				updateProgress(cis.count)
				if (isCancelled) {
					output.close()
					file.delete()
					return
				}
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
		val path = release.path().createDirs()
		logger.finer("Downloading $release to $path")
		
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
						"Separate" -> resolve(name, basePath.resolve("Album Mixes").createDirs())
						"Exclude" -> continue@zip
						else -> resolve(name, path)
					}
				else -> resolve(name, path)
			}
			downloadFile(target)
		} while (!isCancelled)
	}
	
	private fun resolve(name: String, path: Path = basePath): Path =
			path.resolve(addFormat(ReleaseFile(name, release.title.takeIf { release.isMulti }).toFileName()))
	
}

class TrackDownload(private val track: Track) : Download(track, APIConnection("catalog", "release", track.alb.albumId).parseJSON(Release::class.java).coverUrl) {
	
	override fun download() {
		createConnection(track.alb.albumId, { it }, "track=" + track.id)
		downloadFile(track.path())
		abort()
	}
	
}

class EmptyResponseException(term: String) : Exception("No file found for $term!")
