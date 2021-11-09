package xerus.monstercat.api.response

import com.google.api.client.util.Key

abstract class Artist {
	abstract var name: String
	abstract var artistId: String
	
	override fun toString() = name
	override fun hashCode() = artistId.hashCode()
	override fun equals(other: Any?): Boolean = this === other || (other is Artist && name == other.name)
}

class ArtistRel(
	@Key("Name") override var name: String = "",
	@Key("Role") var role: String = "",
	@Key("Id") override var artistId: String = "",
	@Key("URI") var uri: String? = null
): Artist() {
	fun debugString() = "ArtistRel(id=$artistId, name=$name, role=$role)"
}

class FullArtist(@Key("Id") override var artistId: String = "", @Key("Name") override var name: String = ""): Artist()