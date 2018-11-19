package xerus.monstercat.api.response

import com.google.api.client.util.Key
import xerus.ktutil.replaceIllegalFileChars
import xerus.ktutil.to
import xerus.monstercat.downloader.TRACKNAMEPATTERN
import java.util.Collections.emptyList

open class Track(
		@Key("_id") override var
		id: String = "",
		@Key override var
		title: String = "",
		@Key var
		artistsTitle: String = "",
		@Key var
		albums: List<Album> = emptyList(),
		@Key("artistRelationships") var
		artists: List<Artist> = emptyList(),
		var	remix: String = "",
		var	feat: String = "",
		var extra: String = "") : MusicItem() {
	
	@Key
	var bpm = 0
	@Key
	var duration = 0.0
	
	var titleClean: String = ""
	
	val alb: Album
		get() = albums.first()
	
	val streamHash: String?
		get() = albums.find { it.streamHash.isNotEmpty() }?.streamHash
	
	open fun toFileName() =
		toString(TRACKNAMEPATTERN()).replaceIllegalFileChars()
	
	open fun init() {
		if (titleClean.isNotEmpty())
			return
		artistsTitle = formatArtists(artistsTitle)
		val split = title.split('(', ')', '[', ']').map { it.trim() }
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
	
	override fun toString(pattern: String): String {
		init()
		return super.toString(pattern)
	}
	
	override fun toString(): String = artistsTitle.isEmpty().to("%2\$s", "%s - %s").format(artistsTitle, title)
	
	override fun equals(other: Any?): Boolean =
		this === other || (other is Track && id == other.id)
	
	override fun hashCode() = id.hashCode()
	
}
