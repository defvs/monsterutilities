package xerus.monstercat.api

import mu.KotlinLogging
import xerus.ktutil.helpers.StringMasker
import xerus.ktutil.nullIfEmpty
import xerus.ktutil.to
import xerus.ktutil.toInt
import xerus.monstercat.api.response.Track

val artistDelimiters = arrayOf(" & ", ", ", " and ", " x ", " feat. ")
// todo fetch from https://connect.monstercat.com/api/catalog/artist
val artistExceptions = arrayOf("Slips & Slurs", "Case & Point", "Gent & Jawns", "A.M.C & Turno")
val artistMasker = StringMasker("artist", *artistExceptions)

/** Splits up artists & trim whitespaces */
fun String.splitArtists() =
	artistMasker.unmask(artistMasker.mask(this).replace(artistDelimiters.joinToString("|").toRegex(), ";")).split(";")

fun String.splitTitle(): List<String> {
	val matches = Regex("(.*?)(?: \\((.*?)\\))?(?: \\[(.*?)])?(?: \\((.*?)\\))?").matchEntire(this)!!
	return matches.groupValues.subList(1, matches.groupValues.size).mapNotNull { it.trim().nullIfEmpty() }
}

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