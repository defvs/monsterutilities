package xerus.monstercat.api.response

import com.google.api.client.util.Key
import xerus.ktutil.to

data class Release(
	@Key("_id") override var id: String = "",
	@Key var catalogId: String = "",
	@Key var releaseDate: String = "",
	@Key var type: String = "",
	@Key var renderedArtists: String = "",
	@Key override var title: String = "",
	@Key var coverUrl: String = "",
	@Key var downloadable: Boolean = false): MusicItem() {
	
	@Key var isCollection: Boolean = false
	
	@Key var tracks: List<Track> = ArrayList()
		set(value) {
			value.forEach { it.release = this }
			field = value
		}
	
	fun init(): Release {
		renderedArtists = formatArtists(renderedArtists)
		title = title.trim()
		releaseDate = releaseDate.substring(0, 10)
		coverUrl = coverUrl.replace(" ", "%20").replace("[", "%5B").replace("]", "%5D")
		
		if(!isType(Type.MIXES, Type.PODCAST)) {
			isCollection = true
			type = when {
				title.startsWith("Monstercat 0") || title.startsWith("Monstercat Uncaged") || title.startsWith("Monstercat Instinct") -> Type.MCOLLECTION
				title.contains("Best of") || title.endsWith("Anniversary") -> Type.BESTOF
				type == "EP" || type == "Album" -> Type.ALBUM
				else -> {
					isCollection = false
					type
				}
			}
		}
		return this
	}
	
	fun isType(vararg types: String): Boolean = types.any { type.equals(it, true) }
	
	override fun toString(): String =
		renderedArtists.isEmpty().to("%2\$s", "%s - %s").format(renderedArtists, title)
	
	fun debugString(): String =
		"Release(id='$id', releaseDate='$releaseDate', type='$type', renderedArtists='$renderedArtists', title='$title', coverUrl='$coverUrl', downloadable=$downloadable, isCollection=$isCollection)"
	
	object Type {
		val ALBUM = "Album"
		val SINGLE = "Single"
		val MIXES = "Mixes"
		val PODCAST = "Podcast"
		val MCOLLECTION = "Monstercat Collection"
		val BESTOF = "Best of"
	}
	
}