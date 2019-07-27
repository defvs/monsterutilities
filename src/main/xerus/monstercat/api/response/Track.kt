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
	var version: String = ""
	@Key
	var albums: List<Album> = emptyList()
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
		if(albumArtists.isNotEmpty() && titleClean.isNotEmpty())
			return this
		
		if(::release.isInitialized) {
			albumArtists = release.renderedArtists
			albumName = release.title
			albumId = release.catalogId
			trackNumber = albums.find { it.albumId == release.id }?.trackNumber ?: -1
		}
		
		artistsTitle = formatArtists(artistsTitle)
		artistsSplit = artistsTitle.splitArtists()
		splitTitle = "$artistsTitle $title $version".splitTitleTrimmed()
		
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
	
	override fun toString(template: String, vararg additionalFields: Pair<String, String>): String {
		init()
		return super.toString(template, *additionalFields)
	}
	
	override fun toString(): String = when {
		artistsTitle.isEmpty() -> "%2\$s"
		version.isEmpty() -> "%s - %s"
		else -> "%s - %s (%s)"
	}.format(artistsTitle, title, version)
	
	override fun equals(other: Any?): Boolean =
		this === other || (other is Track && id == other.id)
	
	override fun hashCode() = id.hashCode()
	
	fun debugString(): String =
		"Track(id='$id', artistsTitle='$artistsTitle', title='$title', albums=$albums, artists=$artists, bpm=$bpm, artistsSplit=$artistsSplit, titleClean='$titleClean', remix='$remix', feat='$feat', extra='$extra', splitTitle=$splitTitle, release=${if(::release.isInitialized) release else null}, albumArtists='$albumArtists', albumId='$albumId', albumName='$albumName', trackNumber=$trackNumber)"
}
