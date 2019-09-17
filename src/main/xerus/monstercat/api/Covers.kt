package xerus.monstercat.api

import javafx.scene.image.Image
import org.apache.http.HttpEntity
import org.apache.http.client.methods.HttpGet
import xerus.ktutil.replaceIllegalFileChars
import xerus.monstercat.cacheDir
import java.io.File
import java.io.InputStream
import java.net.URI

object Covers {

	private val coverCacheDir = cacheDir.resolve("cover-images").apply { mkdirs() }
	
	private fun coverCacheFile(coverUrl: String, size: Int): File {
		coverCacheDir.mkdirs()
		val newFile = coverCacheDir.resolve(coverUrl.substringAfterLast('/').replaceIllegalFileChars())
		return coverCacheDir.resolve("${newFile.nameWithoutExtension}x$size.${newFile.extension}")
	}
		
	/** Returns an Image of the cover in the requested size using caching.
	 * @param size the size of the Image that is returned - the image file will always be 64x64 */
	fun getThumbnailImage(coverUrl: String, size: Number = 64, invalidate: Boolean = false): Image =
		getCover(coverUrl, 64, invalidate).use { createImage(it, size) }
	
	/** Returns a larger Image of the cover in the requested size using caching.
	 * @param size the size of the Image that is returned - the image file will always be 1024x1024 */
	fun getCoverImage(coverUrl: String, size: Int = 1024, invalidate: Boolean = false): Image =
		getCover(coverUrl, 1024, invalidate).use { createImage(it, size) }
	
	fun createImage(content: InputStream, size: Number) =
		Image(content, size.toDouble(), size.toDouble(), false, false)
	
	/**
	 * Returns an [InputStream] of the downloaded image file.
	 * @param coverUrl the URL for fetching the cover
	 * @param size the dimensions of the cover file
	 * @param invalidate set to true to ignore already existing cache files
	 */
	fun getCover(coverUrl: String, size: Int, invalidate: Boolean = false): InputStream {
		val coverFile = coverCacheFile(coverUrl, size)
		if(!invalidate)
			try {
				return coverFile.inputStream()
			} catch(e: Exception) {
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
	
	fun getCachedCover(coverUrl: String, cachedSize: Int, imageSize: Int = cachedSize): Image? {
		val coverFile = coverCacheFile(coverUrl, cachedSize)
		return try {
			val imageStream = coverFile.inputStream()
			createImage(imageStream, imageSize)
		} catch(e: Exception) {
			null
		}
	}
	
	/** Fetches the given [coverUrl] with an [APIConnection] in the requested [size].
	 * @param coverUrl the base url to fetch the cover
	 * @param size the size of the cover to be fetched from the api, with all powers of 2 being available.
	 *             By default null, which results in the biggest size possible, usually between 2k and 8k. */
	fun fetchCover(coverUrl: String, size: Int? = null): HttpEntity =
		APIConnection.executeRequest(HttpGet(getCoverUrl(coverUrl, size))).entity
	
	/** Attaches a parameter to the [coverUrl] for the requested [size] */
	private fun getCoverUrl(coverUrl: String, size: Int? = null) =
		URI(coverUrl).toASCIIString() + size?.let { "?image_width=$it" }.orEmpty()
	
}