package xerus.monstercat.api.response

import com.google.api.client.util.Key

abstract class Artist {
	abstract var name: String
	abstract var artistId: String
	
	override fun toString() = name
	override fun hashCode() = artistId.hashCode()
	override fun equals(other: Any?): Boolean = this === other || (other is Artist && name == other.name)
}

class ArtistRel(@Key override var name: String = "", @Key var role: String = "", @Key var vendor: String? = null, @Key override var artistId: String = ""): Artist() {
	fun debugString() = "ArtistRel(name=$name, role=$role, vendor=$vendor)"
}

class FullArtist(@Key("_id") override var artistId: String = "", @Key override var name: String = ""): Artist()