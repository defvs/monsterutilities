package xerus.monstercat.api.response

import com.google.api.client.util.Key
import mu.KotlinLogging
import xerus.ktutil.to

val logger = KotlinLogging.logger {}

data class Release(
	@Key("_id") override var id: String = "",
	@Key var releaseDate: String = "",
	@Key var type: String = "",
	@Key var renderedArtists: String = "",
	@Key override var title: String = "",
	@Key var coverUrl: String = "",
	@Key var downloadable: Boolean = false) : MusicItem() {
	
	@Key var isCollection: Boolean = false
	
	@Key var tracks: List<Track> = ArrayList()
	
	fun init(): Release {
		renderedArtists = formatArtists(renderedArtists)
		title = title.trim()
		releaseDate = releaseDate.substring(0, 10)
		coverUrl = coverUrl.replace(" ", "%20")
		tracks.forEach { it.setRelease(this) }
		
		if (!isType(Type.MIXES, Type.PODCAST)) {
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
	
	override fun toString(): String =
		renderedArtists.isEmpty().to("%2\$s", "%s - %s").format(renderedArtists, title)
	
	fun isType(vararg types: String): Boolean = types.any { type.equals(it, true) }
	
	object Type {
		val ALBUM = "Album"
		val SINGLE = "Single"
		val MIXES = "Mixes"
		val PODCAST = "Podcast"
		val MCOLLECTION = "Monstercat Collection"
		val BESTOF = "Best of"
	}
	
}