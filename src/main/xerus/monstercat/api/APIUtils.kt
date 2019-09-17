package xerus.monstercat.api

import mu.KotlinLogging
import xerus.ktutil.helpers.StringMasker
import xerus.ktutil.nullIfEmpty
import xerus.ktutil.toInt
import xerus.monstercat.api.response.Release
import xerus.monstercat.api.response.Settings
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

val meaninglessTitleContents = arrayOf("", "feat.", "Remix")

fun String.splitTitleTrimmed() =
	split(' ', ',', '[', ']', '(', ')', '&').filterNot { it in meaninglessTitleContents }


object APIUtils {
	private val logger = KotlinLogging.logger { }
	
	/** Finds the best matching Track for the given [title] and [artists] */
	suspend fun find(title: String, artists: String): Track? {
		val titleSplit = "$artists $title".splitTitleTrimmed()
		val loggingThreshold = titleSplit.size / 2
		val tracks = Cache.getAllTracks()
		var bestTrack = tracks.maxBy { track ->
			val splitTitleTrimmed = track.init().splitTitle
			titleSplit.sumBy { splitTitleTrimmed.contains(it).toInt() }
				.also {
					if(it > loggingThreshold)
						logger.trace { "Rated $track with $it for \"$artists - $title\" - $splitTitleTrimmed $titleSplit" }
				}
		}
		bestTrack = tracks.filter { it.id == bestTrack?.id }
				.minBy { xerus.monstercat.Settings.PLAYERARTPRIORITY.get().priorities.map { it.displayName }.indexOf(it.release.type)}
		
		return bestTrack
	}
	
}