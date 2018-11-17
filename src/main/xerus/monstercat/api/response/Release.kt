package xerus.monstercat.api.response

import com.google.api.client.util.Key
import mu.KotlinLogging
import xerus.ktutil.to
import xerus.monstercat.api.APIConnection

val logger = KotlinLogging.logger {}

data class Release(
		@Key("_id") override var
		id: String = "",
		@Key var
		releaseDate: String = "",
		@Key var
		type: String = "",
		@Key @JvmField var
		renderedArtists: String = "",
		@Key override var
		title: String = "",
		@Key var
		coverUrl: String = "",
		@Key var
		downloadable: Boolean = false) : MusicItem() {
	
	@Key
	var tracks: List<Track>? = null
		get() {
			if (field == null) fetchTracks()
			return field
		}
	
	private fun fetchTracks() {
		logger.trace("Fetching tracks for $this")
		tracks = APIConnection("catalog", "release", id, "tracks").getTracks()
	}
	
	var isMulti: Boolean = false
	
	fun init(): Release {
		renderedArtists = formatArtists(renderedArtists)
		title = title.trim()
		releaseDate = releaseDate.substring(0, 10)
		
		if (!isType("Mixes", "Podcast")) {
			isMulti = true
			type = when {
				title.startsWith("Monstercat 0") || title.startsWith("Monstercat Uncaged") || title.startsWith("Monstercat Instinct") -> "Monstercat Collection"
				title.contains("Best of") || title.endsWith("Anniversary") -> "Best of"
				type == "EP" || type == "Album" -> "Album"
				else -> {
					isMulti = false
					type
				}
			}
		}
		return this
	}
	
	override fun toString(): String =
		renderedArtists.isEmpty().to("%2\$s", "%s - %s").format(renderedArtists, title)
	
	fun isType(vararg types: String): Boolean = types.any { type.equals(it, true) }
	
}


/*enum class ReleaseType {
    ALBUM,
    SINGLE,
    MIXES,
    PODCAST,
    MCOLLECTION,
    BESTOF;
}*/
