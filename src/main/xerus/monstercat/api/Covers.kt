package xerus.monstercat.api

import javafx.scene.image.Image
import org.apache.http.HttpEntity
import org.apache.http.client.methods.HttpGet
import xerus.ktutil.replaceIllegalFileChars
import xerus.monstercat.cacheDir
import java.io.InputStream
import java.net.URI

object Covers {
	
	private val coverCacheDir = cacheDir.resolve("cover-images").apply { mkdirs() }
	
	/** Returns an Image of the cover in the requested size using caching.
	 * @param size the size of the Image - the underlying image data will always be 64x64, thus this is the default. */
	fun getCoverImage(coverUrl: String, size: Number = 64, invalidate: Boolean = false): Image =
		getCover(coverUrl, invalidate).use { createImage(it, size) }
	
	private fun createImage(content: InputStream, size: Number) =
		Image(content, size.toDouble(), size.toDouble(), false, false)
	
	/** Returns an InputStream to the cover in size 64x64, using caching. */
	fun getCover(coverUrl: String, invalidate: Boolean = false): InputStream {
		val file = coverCacheFile(coverUrl)
		if(!file.exists() || invalidate) {
			fetchCover(coverUrl, 64).content.use { input ->
				file.outputStream().use { out ->
					input.copyTo(out)
				}
			}
		}
		return file.inputStream()
	}
	
	private fun coverCacheFile(coverUrl: String) =
		coverCacheDir.apply { mkdirs() }.resolve(coverUrl.substringAfterLast('/').replaceIllegalFileChars())
	
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