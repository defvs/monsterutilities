package xerus.monstercat.api

import javafx.scene.image.Image
import mu.KotlinLogging
import org.apache.http.HttpEntity
import org.apache.http.client.methods.HttpGet
import xerus.ktutil.replaceIllegalFileChars
import xerus.monstercat.api.response.Release
import xerus.monstercat.cacheDir
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

object Covers {
	val logger = KotlinLogging.logger { }
	private val coverCacheDir = cacheDir.resolve("cover-images").apply { mkdirs() }
	
	private fun coverCacheFile(release: Release, size: Int): File {
		coverCacheDir.mkdirs()
		val newFile = coverCacheDir.resolve(release.catalogId.replaceIllegalFileChars())
		return coverCacheDir.resolve("${newFile.nameWithoutExtension}x$size")
	}
		
	/** Returns an Image of the cover in the requested size using caching.
	 * @param size the size of the Image that is returned - the image file will always be 64x64
	 * @throws IOException propagation of [getCover]'s exception */
	fun getThumbnailImage(release: Release, size: Number = 64, invalidate: Boolean = false) =
			getCover(release, 64, invalidate).use { createImage(it, size) }
	
	/** Returns a larger Image of the cover in the requested size using caching.
	 * @param size the size of the Image that is returned - the image file will always be 1024x1024
	 * @throws IOException propagation of [getCover]'s exception */
	fun getCoverImage(release: Release, size: Int = 1024, invalidate: Boolean = false): Image =
		getCover(release, 1024, invalidate).use { createImage(it, size) }
	
	private fun createImage(content: InputStream, size: Number) =
		Image(content, size.toDouble(), size.toDouble(), false, false)
	
	/**
	 * Returns an [InputStream] of the downloaded image file.
	 * @param release the release to get the cover for
	 * @param size the dimensions of the cover file
	 * @param invalidate set to true to ignore already existing cache files
	 * @throws IOException propagation of [fetchCover]'s exception if it fails (connectivity issues)
	 */
	fun getCover(release: Release, size: Int, invalidate: Boolean = false): InputStream {
		val coverFile = coverCacheFile(release, size)
		if(!invalidate)
			try {
				return coverFile.inputStream()
			} catch (e: FileNotFoundException) {
				logger.warn("Cover file at ${coverFile.path} not found, invalidating.")
			} catch (e: SecurityException) {
				logger.warn("Cover file at ${coverFile.path} cannot be opened, invalidating.")
			}
		fetchCover(release, size).content.use { input ->
			val tempFile = File.createTempFile(coverFile.name, size.toString())
			tempFile.outputStream().use { out ->
				input.copyTo(out)
			}
			tempFile.renameTo(coverFile)
		}
		return coverFile.inputStream()
	}
	
	/** Fetches the given [release] cover art with an [APIConnection] in the requested [size].
	 * @param release the release to get the cover for
	 * @param size the size of the cover to be fetched from the api, with all powers of 2 being available.
	 * @throws IOException in case of timeout, connectivity issues...
	 */
	fun fetchCover(release: Release, size: Int = 1024): HttpEntity =
		APIConnection.executeRequest(HttpGet(getCoverUrl(release, size))).entity
	
	/** Gets the cover for a given [release] at [size] */
	private fun getCoverUrl(release: Release, size: Int = 1024) = release.getCoverUrl(size)
	
}