package xerus.monstercat.api.response

import com.google.api.client.util.Key
import xerus.ktutil.replaceIllegalFileChars
import xerus.ktutil.to
import xerus.monstercat.api.splitTitle
import xerus.monstercat.downloader.TRACKNAMEPATTERN
import xerus.monstercat.downloader.splitArtists
import java.util.Collections.emptyList

open class Track : MusicItem() {
	
	@Key("_id")
	override var id: String = ""
	@Key
	var artistsTitle: String = ""
	@Key
	override var title: String = ""
	@Key
	var albums: List<Album> = emptyList()
	@Key("artistRelationships")
	var artists: List<Artist> = emptyList()
	@Key
	var bpm: Double? = null
	
	var titleClean: String = ""
	var remix: String = ""
	var feat: String = ""
	var extra: String = ""
	
	val alb: Album
		get() = albums.first()
	
	val streamHash: String?
		get() = albums.find { it.streamHash.isNotEmpty() }?.streamHash
	
	open fun init() {
		if (titleClean.isNotEmpty())
			return
		artistsTitle = formatArtists(artistsTitle)
		if(artists.isEmpty() && artistsTitle.isNotEmpty())
			artists = artistsTitle.splitArtists()
		artists = artists.distinct()
		val split = title.splitTitle()
		// TODO braces in title (e.g. "I (still) dream of You")
		titleClean = split[0]
		if (split.size > 1)
			split.subList(1, split.lastIndex).forEach {
				when {
					it.startsWith("feat", true) -> feat = it.split(' ', limit = 2)[1]
					it.endsWith("mix", true) || it == "Classical" -> remix = it
					it.isNotBlank() -> extra = it
				}
			}
	}
	
	val isAlbumMix
		get() = title.contains("Album Mix")
	
	override fun toString(pattern: String): String {
		init()
		return super.toString(pattern)
	}
	
	override fun toString(): String = artistsTitle.isEmpty().to("%2\$s", "%s - %s").format(artistsTitle, title)
	
	override fun equals(other: Any?): Boolean =
		this === other || (other is Track && id == other.id)
	
	override fun hashCode() = id.hashCode()
	
}
