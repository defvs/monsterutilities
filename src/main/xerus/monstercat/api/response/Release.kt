package xerus.monstercat.api.response

import com.google.api.client.util.Key
import mu.KotlinLogging
import xerus.ktutil.to

private val logger = KotlinLogging.logger { }

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
		
		if(!isType(Type.MIX, Type.PODCAST)) {
			val typeValue = Type.values().find {
				if(it.matcher?.invoke(this) == true || it.displayName == type)
					return@find true
				false
			}
			if(typeValue != null)
				type = typeValue.displayName
			else
				logger.warn("Unknown type for ${this.debugString()}!")
			isCollection = typeValue?.isCollection ?: true
		}
		return this
	}
	
	fun isType(vararg types: Type): Boolean = types.any { type.equals(it.displayName, true) }
	
	override fun toString(): String =
		renderedArtists.isEmpty().to("%2\$s", "%s - %s").format(renderedArtists, title)
	
	fun debugString(): String =
		"Release(id='$id', releaseDate='$releaseDate', type='$type', renderedArtists='$renderedArtists', title='$title', coverUrl='$coverUrl', downloadable=$downloadable, isCollection=$isCollection)"
	
	enum class Type(val displayName: String, val isCollection: Boolean, val matcher: (Release.() -> Boolean)? = null): CharSequence by displayName {
		MCOLLECTION("Monstercat Collection", true,
			{ title.startsWith("Monstercat 0") || title.startsWith("Monstercat Uncaged") || title.startsWith("Monstercat Instinct") }),
		BESTOF("Best of", true, { title.contains("Best of") || title.endsWith("Anniversary") }),
		ALBUM("Album", true),
		EP("EP", true),
		MIX("Mix", false, { type == "Mixes" }),
		SINGLE("Single", false),
		PODCAST("Podcast", false);
	}
	
}