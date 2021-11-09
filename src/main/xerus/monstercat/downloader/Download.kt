package xerus.monstercat.downloader

import javafx.concurrent.Task
import mu.KotlinLogging
import xerus.ktutil.*
import xerus.monstercat.api.APIConnection
import xerus.monstercat.api.Covers
import xerus.monstercat.api.response.MusicItem
import xerus.monstercat.api.response.Release
import xerus.monstercat.api.response.Track
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Path

private inline val basePath
	get() = DOWNLOADDIR()

fun Track.toFileName(inAlbum: Boolean) =
	toString(if(inAlbum) ALBUMTRACKNAMEPATTERN() else TRACKNAMEPATTERN()).replaceIllegalFileChars()

fun Release.downloadFolder(): Path {
	fun transformDirectories(string: String) = string.split("/", "\\")
		.mapNotNull { toString(it).nullIfEmpty()?.replaceIllegalFileChars() }
		.joinToString("/")
	return basePath.resolve(when {
		isMulti() -> transformDirectories(DOWNLOADDIRALBUM()) // Album, Monstercat Collection
		isType(Release.Type.PODCAST) -> transformDirectories(DOWNLOADDIRPODCAST())
		isType(Release.Type.MIX) -> transformDirectories(DOWNLOADDIRMIXES())
		else -> transformDirectories(DOWNLOADDIRSINGLE())
	})
}

fun Release.isMulti() = isCollection && tracks.size >= EPSTOSINGLES()

fun String.addFormatSuffix() = "$this.${QUALITY().split('_')[0]}"

private val logger = KotlinLogging.logger { }

abstract class Download(val item: MusicItem): Task<Long>() {
	
	init {
		@Suppress("LEAKINGTHIS")
		updateTitle(item.toString())
	}
	
	fun getReleaseItem(): Release = when(item) {
		is Release -> item
        is Track -> item.release
        else -> throw IllegalArgumentException("Unknown item type: $item")
	}
	
	private var startTime: Long = 0
	protected var maxProgress: Double = 0.0
	override fun call(): Long {
		startTime = System.currentTimeMillis()
		download()
		return System.currentTimeMillis() - startTime
	}
	
	abstract fun download()
	
	protected fun downloadFile(inputStream: InputStream, path: Path, closeIn: Boolean = false, progress: (Long) -> Double) {
		val file = path.toFile()
		logger.trace("Downloading $file")
		updateMessage(file.name)
		
		val partFile = file.resolveSibling(file.name + ".part")
		inputStream.copyTo(FileOutputStream(partFile), closeIn, true) {
			updateProgress(progress(it), maxProgress)
			isCancelled
		}
		if(isCancelled) {
			logger.trace("Download of $partFile cancelled, deleting: " + partFile.delete())
		} else {
			file.delete()
			logger.trace("Renamed $partFile to $file: " + partFile.renameTo(file))
		}
	}
	
	override fun toString(): String = "Download(item=$item)"
	
}

class ReleaseDownload(private val release: Release, private var tracks: Collection<Track>): Download(release) {
	
	private var totalProgress = 0
	
	fun downloadTrack(releaseId: String, trackId: String, path: Path) {
		val connection = APIConnection("api", "release", releaseId, "track-download", trackId).addQuery("format", QUALITY())
		val httpResponse = connection.getResponse()
		val entity = httpResponse.entity
		val contentLength = entity.contentLength
		if(contentLength == 0L)
			throw EmptyResponseException(connection.uri.toString())
		if(!entity.contentType.value.let { it.startsWith("audio/") || it == "binary/octet-stream" || it == "application/octet-stream" })
			throw WrongResponseTypeException(connection.uri.toString(), entity.contentType.value)
		if(httpResponse.statusLine.statusCode != 200)
			throw WrongResponseCodeException(connection.uri.toString(), httpResponse.statusLine.toString())
		val length = contentLength.toDouble()
		downloadFile(entity.content, path, true) {
			totalProgress + it / length
		}
	}
	
	override fun download() {
		if(tracks.isEmpty())
			return
		val folder = release.downloadFolder()
		val partFolder = folder.resolveSibling(folder.fileName.toString() + ".part")
		val downloadFolder =
			if(folder.exists() && !partFolder.exists() || folder == basePath)
				folder
			else
				partFolder
		downloadFolder.createDirs()
		
		val downloadCover = DOWNLOADCOVERS() == DownloadCovers.ALL || DOWNLOADCOVERS() == DownloadCovers.COLLECTIONS && release.isMulti()
		val coverFraction = 0.2
		maxProgress = tracks.size + coverFraction * downloadCover.toInt()
		
		logger.debug("Downloading $release to $downloadFolder")
		tr@ for(track in tracks) {
			val filename = track.toFileName(release.isMulti()).addFormatSuffix()
			var trackPath = downloadFolder.resolve(filename)
			if(track.isAlbumMix)
				when(ALBUMMIXES()) {
					AlbumMixes.SEPARATE -> trackPath = basePath.resolve(DOWNLOADDIRMIXES()).createDirs().resolve(filename)
					AlbumMixes.EXCLUDE -> continue@tr
					else -> {
					}
				}
			downloadTrack(release.id, track.id, trackPath)
			if(isCancelled)
				break@tr
			totalProgress++
		}
		if(!isCancelled && downloadCover) {
			val entity = Covers.fetchCover(release, COVERARTSIZE())
			val coverName = release.toString(COVERPATTERN()).replaceIllegalFileChars() + "." + entity.contentType.value.substringAfter('/')
			updateMessage(coverName)
			val length = entity.contentLength.toDouble()
			downloadFile(entity.content, downloadFolder.resolve(coverName), true) {
				totalProgress + (coverFraction * it / length)
			}
		}
		
		if(!isCancelled && downloadFolder == partFolder) {
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

class EmptyResponseException(term: String): Exception("No file found for $term!")

class WrongResponseTypeException(term: String, mime: String): Exception("Error downloading $term: file type $mime unexpected!")

class WrongResponseCodeException(term: String, status: String): Exception("Error downloading $term: Server returned \"$status\"")
