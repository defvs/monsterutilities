package xerus.monstercat.api

import javafx.scene.image.Image
import mu.KotlinLogging
import org.apache.http.HttpEntity
import org.apache.http.client.ClientProtocolException
import org.apache.http.client.methods.HttpGet
import xerus.ktutil.replaceIllegalFileChars
import xerus.monstercat.cacheDir
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.lang.IllegalArgumentException
import java.net.URI

object Covers {
	val logger = KotlinLogging.logger { }
	private val coverCacheDir = cacheDir.resolve("cover-images").apply { mkdirs() }
	
	private fun coverCacheFile(coverUrl: String, size: Int): File {
		coverCacheDir.mkdirs()
		val newFile = coverCacheDir.resolve(coverUrl.split('/').let{ it[it.lastIndex - 1] }.replaceIllegalFileChars())
		return coverCacheDir.resolve("${newFile.nameWithoutExtension}x$size")
	}
		
	/** Returns an Image of the cover in the requested size using caching.
	 * @param size the size of the Image that is returned - the image file will always be 64x64
	 * @throws IOException propagation of [getCover]'s exception */
	fun getThumbnailImage(coverUrl: String, size: Number = 64, invalidate: Boolean = false) =
			getCover(coverUrl, 64, invalidate).use { createImage(it, size) }
	
	/** Returns a larger Image of the cover in the requested size using caching.
	 * @param size the size of the Image that is returned - the image file will always be 1024x1024
	 * @throws IOException propagation of [getCover]'s exception */
	fun getCoverImage(coverUrl: String, size: Int = 1024, invalidate: Boolean = false): Image =
		getCover(coverUrl, 1024, invalidate).use { createImage(it, size) }
	
	private fun createImage(content: InputStream, size: Number) =
		Image(content, size.toDouble(), size.toDouble(), false, false)
	
	/**
	 * Returns an [InputStream] of the downloaded image file.
	 * @param coverUrl the URL for fetching the cover
	 * @param size the dimensions of the cover file
	 * @param invalidate set to true to ignore already existing cache files
	 * @throws IOException propagation of [fetchCover]'s exception if it fails (connectivity issues)
	 */
	fun getCover(coverUrl: String, size: Int, invalidate: Boolean = false): InputStream {
		val coverFile = coverCacheFile(coverUrl, size)
		if(!invalidate)
			try {
				return coverFile.inputStream()
			} catch (e: FileNotFoundException) {
				logger.warn("Cover file at ${coverFile.path} not found, invalidating.")
			} catch (e: SecurityException) {
				logger.warn("Cover file at ${coverFile.path} cannot be opened, invalidating.")
			}
		fetchCover(coverUrl, size).content.use { input ->
			val tempFile = File.createTempFile(coverFile.name, size.toString())
			tempFile.outputStream().use { out ->
				input.copyTo(out)
			}
			tempFile.renameTo(coverFile)
		}
		return coverFile.inputStream()
	}
	
	/** Fetches the given [coverUrl] with an [APIConnection] in the requested [size].
	 * @param coverUrl the base url to fetch the cover
	 * @param size the size of the cover to be fetched from the api, with all powers of 2 being available.
	 *             By default null, which results in the biggest size possible, usually between 2k and 8k.
	 * @throws IOException in case of timeout, connectivity issues...
	 */
	fun fetchCover(coverUrl: String, size: Int? = null): HttpEntity =
		APIConnection.executeRequest(HttpGet(getCoverUrl(coverUrl, size))).entity
	
	/** Attaches a parameter to the [coverUrl] for the requested [size] */
	private fun getCoverUrl(coverUrl: String, size: Int? = null) =
		URI(coverUrl).toASCIIString() + size?.let { "?image_width=$it" }.orEmpty()
	
}