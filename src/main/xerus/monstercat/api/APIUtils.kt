package xerus.monstercat.api

import mu.KotlinLogging
import xerus.ktutil.to
import xerus.ktutil.toInt
import xerus.monstercat.api.response.Track
import xerus.monstercat.api.response.declaredKeys
import java.net.URLEncoder
import java.util.regex.Pattern

fun String.splitTitle() = split('(', ')', '[', ']').map { it.trim() }

object APIUtils {
	private val logger = KotlinLogging.logger { }
	
	/** Finds the best matching Track for the given [title] and [artists] */
	suspend fun find(title: String, artists: String): Track? {
		val splitTitle = title.split(" ")
		return Cache.getTracks().maxBy { track ->
			splitTitle.map { track.title.contains(it).toInt() }
			+(track.titleClean == title).toInt()
			+(track.artistsTitle == artists).to(10, 0)
			+track.artists.map { artists.contains(it.name).to(3, 0) }.average()
		}
	}
	
}