package xerus.monstercat.downloader

import xerus.ktutil.helpers.Masker
import xerus.ktutil.replaceIllegalFileChars
import xerus.monstercat.api.response.Artist
import xerus.monstercat.api.response.Track
import java.util.regex.Pattern

val delimiters = arrayOf(" & ", ", ", " and ", " x ")
val exceptions = arrayOf("Slips & Slurs", "Case & Point", "Gent & Jawns")
val artistMasker = Masker("artist", *exceptions)

val namePattern = Pattern.compile("([^-]+) - (.+) - (\\d+) (.+)")

class ReleaseFile(filename: String, val single: Boolean) : Track() {
	
	// These are visible JvmFields for reflection
	@JvmField
	internal val album: String
	@JvmField
	internal val track: Int
	
	init {
		val m = namePattern.matcher(filename)
		m.matches()
		// split up artists
		artistsTitle = m.group(1)
		var artist = artistMasker.mask(artistsTitle)
		artist = artist.replace(delimiters.joinToString("|").toRegex(), ";")
		artist = artistMasker.unmask(artist)
		// trim whitespaces
		artists = artist.split(";").map { Artist(it.trim()) }
		
		// get other stuff
		album = m.group(2)
		track = m.group(3).toInt()
		var title = m.group(4)
		val dot = title.lastIndexOf(".")
		if (dot > title.length - 6)
			title = title.substring(0, dot)
		this.title = title
	}
	
	override fun toFileName() =
			(if (single) toString(TRACKNAMEPATTERN()) else toString(ALBUMTRACKNAMEPATTERN())).replaceIllegalFileChars()
	
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
