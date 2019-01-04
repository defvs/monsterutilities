package xerus.monstercat.downloader

import xerus.ktutil.helpers.ParserException
import xerus.ktutil.helpers.StringMasker
import xerus.ktutil.replaceIllegalFileChars
import xerus.monstercat.api.response.Artist
import xerus.monstercat.api.response.Track
import java.util.regex.Pattern

val delimiters = arrayOf(" & ", ", ", " and ", " x ")
// todo fetch from https://connect.monstercat.com/api/catalog/artist
val artistExceptions = arrayOf("Slips & Slurs", "Case & Point", "Gent & Jawns", "A.M.C & Turno")
val artistMasker = StringMasker("artist", *artistExceptions)

/** artists - tracknumber title */
val namePattern: Pattern = Pattern.compile("([^-]+) - (.+ - )?(\\d+) (.+)")

/** Splits up artists & trim whitespaces */
fun String.splitArtists() =
	artistMasker.unmask(artistMasker.mask(this).replace(delimiters.joinToString("|").toRegex(), ";")).split(";").map { Artist(it.trim()) }

class ReleaseFile(filename: String, @JvmField val album: String? = null) : Track() {
	
	@JvmField
	internal val track: Int
	
	init {
		val m = namePattern.matcher(filename)
		if (m.matches()) {
			artists = m.group(1).splitArtists()
			
			// get other stuff
			track = m.group(3).toInt()
			var title = m.group(4)
			val dot = title.lastIndexOf(".")
			if (dot > title.length - 6 && dot > 0)
				title = title.substring(0, dot)
			this.title = title
		} else {
			throw ParserException("Failed to parse '$filename'")
		}
	}
	
	/*
	private fun parse(filename: String) {
		val split = filename.split(" - ".toRegex()).toTypedArray()
		// split up artists
		artistsTitle = split[0]
		var artist = artistMasker.mask(artistsTitle)
		artist = artist.replace(delimiters.joinToString("|").toRegex(), ";")
		artist = artistMasker.unmask(artist)
		// trim whitespaces
		artists = artist.split(";").map { it.trim() }.toTypedArray()

		// get other stuff
		album = split.copyOfRange(1, split.size - 1).joinToString(" - ")
		val p = StringTools.splitoffFirst(Tools.getLast(split), " ")
		track = p.left
		title = p.right
		val ind = title.lastIndexOf(".")
		if (ind > title.length - 6)
			title = title.substring(0, ind)
	}
	*/
	
}
