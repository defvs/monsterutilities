package xerus.monstercat.api.response

import com.google.api.client.util.Key
import xerus.ktutil.to
import xerus.monstercat.api.splitArtists
import xerus.monstercat.api.splitTitle
import xerus.monstercat.api.splitTitleTrimmed
import java.util.Collections.emptyList

open class Track: MusicItem() {
	
	@Key("_id")
	override var id: String = ""
	@Key
	var artistsTitle: String = ""
	@Key
	override var title: String = ""
	@Key
	var albums: List<Album> = emptyList()
	@Key
	var albumNames: List<String> = emptyList()
	@Key
	var albumCatalogIds: List<String> = emptyList()
	@Key("artistRelationships")
	var artists: List<ArtistRel> = emptyList()
	@Key
	var bpm: Double? = null
	
	var artistsSplit: List<String> = emptyList()
	var titleClean: String = ""
	var remix: String = ""
	var feat: String = ""
	var extra: String = ""
	var splitTitle: List<String> = emptyList()
	
	lateinit var release: Release
	
	var albumArtists = ""
	var albumId = ""
	var albumName = ""
	var trackNumber = -1
	
	val streamHash: String?
		get() = albums.find { it.streamHash.isNotEmpty() }?.streamHash
	
	val isAlbumMix
		get() = title.contains("Album Mix")
	
	open fun init(): Track {
		if(titleClean.isNotEmpty())
			return this
		
		/*if(::release.isInitialized) { TODO : Remove this as it's useless, unless proven
			albumArtists = release.renderedArtists
			val index = albums.indexOfFirst { it.albumId == release.id }
			if(index > -1) {
				albumName = albumNames[index]
				albumId = albumCatalogIds[index]
				trackNumber = albums[index].trackNumber
			}
		}*/
		
		artistsTitle = formatArtists(artistsTitle)
		artistsSplit = artistsTitle.splitArtists()
		splitTitle = "$artistsTitle $title".splitTitleTrimmed()
		
		title.splitTitle().forEachIndexed { index, s ->
			when {
				index == 0 -> titleClean = s
				s.startsWith("feat", true) -> feat = s.split(' ', limit = 2)[1]
				s.endsWith("mix", true) || s == "Classical" -> remix = s
				s.isNotBlank() -> extra = s
			}
		}
		
		return this
	}
	
	override fun toString(pattern: String, vararg additionalFields: Pair<String, String>): String {
		init()
		return super.toString(pattern, *additionalFields)
	}
	
	override fun toString(): String = artistsTitle.isEmpty().to("%2\$s", "%s - %s").format(artistsTitle, title)
	
	override fun equals(other: Any?): Boolean =
		this === other || (other is Track && id == other.id)
	
	override fun hashCode() = id.hashCode()
	
}
