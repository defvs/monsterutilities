package xerus.monstercat.api.response

import com.google.api.client.util.Key
import xerus.ktutil.splitTitleTrimmed
import xerus.monstercat.api.splitArtists
import xerus.monstercat.api.splitTitle
import java.util.Collections.emptyList

open class Track: MusicItem() {
	
	@Key("Id") override var id: String = ""
	@Key("ArtistsTitle") var artistsTitle: String = ""
	@Key("Title") override var title: String = ""
	@Key("Version") var version: String = ""
	@Key("Artists") var artists: List<ArtistRel> = emptyList()
	@Key("BPM") var bpm: Double? = null
	@Key("Streamable") var streamable: Boolean = false
	@Key("TrackNumber") var trackNumber = -1
	@Key("CreatorFriendly") var creatorFriendly: Boolean = false
	
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
	
	val isAlbumMix
		get() = title.contains("Album Mix")
	
	val titleFull
		get() = when {
			version.isEmpty() -> "%s"
			else -> "%s (%s)"
		}.format(title, version)
	
	open fun init(): Track {
		if(albumArtists.isNotEmpty() && titleClean.isNotEmpty())
			return this
		
		if(::release.isInitialized) {
			albumArtists = release.artistsTitle
			albumName = release.title
			albumId = release.catalogId
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
		if(version.isNotEmpty())
			remix = version
		
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
		"Track(id='$id', artistsTitle='$artistsTitle', title='$title', artists=$artists, bpm=$bpm, artistsSplit=$artistsSplit, titleClean='$titleClean', remix='$remix', feat='$feat', extra='$extra', splitTitle=$splitTitle, release=${if(::release.isInitialized) release else null}, albumArtists='$albumArtists', albumId='$albumId', albumName='$albumName', trackNumber=$trackNumber)"
}
