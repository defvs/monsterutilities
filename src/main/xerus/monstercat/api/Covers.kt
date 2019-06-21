package xerus.monstercat.api

import javafx.scene.image.Image
import org.apache.http.HttpEntity
import org.apache.http.client.methods.HttpGet
import xerus.ktutil.replaceIllegalFileChars
import xerus.monstercat.cacheDir
import java.io.InputStream

object Covers {
	
	private val coverCacheDir = cacheDir.resolve("cover-images").apply { mkdirs() }
	
	/** Returns an Image of the cover in the requested size using caching.
	 * @param size the size of the Image - the underlying image data will always be 64x64, thus this is the default. */
	fun getCoverImage(coverUrl: String, size: Int = 64): Image =
		getCover(coverUrl).use { Image(it, size.toDouble(), size.toDouble(), false, false) }
	
	/** Returns an InputStream to the cover in size 64x64, using caching. */
	fun getCover(coverUrl: String): InputStream {
		val file = coverCacheDir.resolve(coverUrl.substringAfterLast('/').replaceIllegalFileChars())
		if(!file.exists()) {
			fetchCover(coverUrl, 64).content.use { input ->
				file.outputStream().use { out ->
					input.copyTo(out)
				}
			}
		}
		return file.inputStream()
	}
	
	fun getLargeCoverImage(coverUrl: String, size: Int = 1024): Image =
			getLargeCover(coverUrl, size).use { Image(it, size.toDouble(), size.toDouble(), false, false) }
	
	fun getLargeCover(coverUrl: String, size: Int): InputStream =
			fetchCover(coverUrl, size).content
	
	/** Fetches the given [coverUrl] with an [APIConnection] in the requested [size].
	 * @param coverUrl the base url to fetch the cover
	 * @param size the size of the cover to be fetched from the api, with all powers of 2 being available.
	 *             By default null, which results in the biggest size possible, usually between 2k and 8k. */
	fun fetchCover(coverUrl: String, size: Int? = null): HttpEntity =
		APIConnection.execute(HttpGet(getCoverUrl(coverUrl, size))).entity
	
	/** Attaches a parameter to the [coverUrl] for the requested [size] */
	private fun getCoverUrl(coverUrl: String, size: Int? = null) =
		coverUrl + size?.let { "?image_width=$it" }.orEmpty()
	
}