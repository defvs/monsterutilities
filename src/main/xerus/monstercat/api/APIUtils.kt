package xerus.monstercat.api

import mu.KotlinLogging
import xerus.ktutil.helpers.StringMasker
import xerus.ktutil.nullIfEmpty
import xerus.ktutil.splitTitleTrimmed
import xerus.ktutil.toInt
import xerus.monstercat.api.response.Release
import xerus.monstercat.api.response.Track

val artistDelimiters = arrayOf(" & ", ", ", " and ", " x ", " feat. ")
// TODO fetch from https://connect.monstercat.com/api/catalog/artist
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
	
	suspend fun findRelease(title: String, artist: String) = Cache.getReleases().maxBy { release ->
		title.sumBy { release.title.contains(it).toInt() } + title.sumBy { release.artistsTitle.contains(it).toInt() }
	}
	
	suspend fun findRelease(catalogId: String) = Cache.getReleases().maxBy { release ->
		catalogId.sumBy { release.catalogId.contains(it).toInt() }
	}
	
}