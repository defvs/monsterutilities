package xerus.monstercat.api

import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.HBox
import org.apache.http.HttpEntity
import org.apache.http.client.methods.HttpGet
import xerus.ktutil.javafx.ui.App
import xerus.ktutil.replaceIllegalFileChars
import xerus.monstercat.cacheDir
import java.io.InputStream

fun main() {
	App.launch {
		Scene(HBox(ImageView(Image(Covers.getCover("https://assets.monstercat.com/releases/covers/Memtrix%20-%20Blind%20In%20Light%20(Art).png")))))
	}
}

object Covers {
	
	private val coverCacheDir = cacheDir.resolve("cover-images").apply { mkdirs() }
	
	fun getCoverImage(coverUrl: String, size: Int = 64): Image =
		getCover(coverUrl).use { Image(it, size.toDouble(), size.toDouble(), false, false) }
	
	/** Returns an InputStream to the cover in size 64x64, using caching */
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
	
	fun fetchCover(coverUrl: String, size: Int? = null): HttpEntity =
		APIConnection.execute(HttpGet(getCoverUrl(coverUrl, size))).entity
	
	private fun getCoverUrl(coverUrl: String, size: Int? = null) =
		coverUrl + size?.let { "?image_width=$it" }.orEmpty()
	
}