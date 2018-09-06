package xerus.monstercat.api

import xerus.ktutil.to
import xerus.ktutil.toInt
import xerus.monstercat.api.response.Track
import xerus.monstercat.api.response.declaredKeys
import xerus.monstercat.logger
import java.net.URLEncoder
import java.util.regex.Pattern

object API {
	
	/** Finds the best match for the given [title] and [artists] */
	fun find(title: String, artists: String): Track? {
		val connection = APIConnection("catalog", "track").addQuery("fields", *Track::class.declaredKeys.toTypedArray())
		URLEncoder.encode(title, "UTF-8")
				.split(Pattern.compile("%.."))
				.filter { it.isNotBlank() }
				.forEach { connection.addQuery("fuzzy", "title," + it.trim()) }
		val results = connection.getTracks()
		logger.finest("Found $results for $connection")
		return results?.maxBy { track ->
			track.init()
			track.artists.map { artists.contains(it.name).to(3, 0) }.average() +
					(track.titleRaw == title).toInt() + (track.artistsTitle == artists).to(10, 0)
		}
	}
	
}