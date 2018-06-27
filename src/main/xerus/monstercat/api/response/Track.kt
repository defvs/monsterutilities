package xerus.monstercat.api.response

import com.google.api.client.util.Key
import xerus.ktutil.helpers.Parsable
import xerus.ktutil.replaceIllegalFileChars
import xerus.ktutil.to
import xerus.monstercat.downloader.TRACKNAMEPATTERN
import java.util.Collections.emptyList

/** JvmFields are used for Reflection, which is needed for the formatted [toString] method */
open class Track(
		@Key("_id") override var
		id: String = "",
		@Key @JvmField var
		title: String = "",
		@Key @JvmField var
		artistsTitle: String = "",
		@Key var
		albums: List<Album> = emptyList(),
		@Key @JvmField var
		artists: List<Artist> = emptyList(),
		@JvmField var
		remix: String = "",
		@JvmField var
		feat: String = "") : MusicItem, Parsable {
	
	val alb: Album
		get() = albums.first()
	
	val streamHash: String?
		get() = albums.find { it.streamHash.isNotEmpty() }?.streamHash
	
	@JvmField
	var titleRaw: String = ""
	
	open fun toFileName() =
			toString(TRACKNAMEPATTERN()).replaceIllegalFileChars()
	
	open fun init() {
		artistsTitle = if (artistsTitle == "Various Artists" || artistsTitle == "Various" || artistsTitle == "Monstercat" && title.contains("Monstercat")) "" else artistsTitle.trim()
		if (titleRaw.isNotEmpty())
			return
		val split = title.split('(', ')', '[', ']').map { it.trim() }
		titleRaw = split[0]
		if (split.size > 1)
			split.subList(1, split.lastIndex).forEach {
				when {
					it.startsWith("feat", true) -> feat = it.split(' ', limit = 2)[1]
					it.endsWith("mix", true) -> remix = it
				}
			}
	}
	
	override fun toString(pattern: String): String {
		init()
		return super.toString(pattern)
	}
	
	override fun toString(): String =
			artistsTitle.isEmpty().to("%2\$s", "%s - %s").format(artistsTitle, title)
	
}
