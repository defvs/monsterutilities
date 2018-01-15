package xerus.monstercat.api.response

import com.google.api.client.util.Key

fun Class<*>.declaredKeys() =
        declaredFields.mapNotNull { ((it.annotations.find { it is Key } as Key?) ?: return@mapNotNull null).value.takeUnless { it == "##default" } ?: it.name }.toTypedArray()

open class MultiResponse<T> {
    @Key
    lateinit var results: ArrayList<T>
    @Key
    var total: Int = 0
}

class ReleaseResponse : MultiResponse<Release>()
class TrackResponse : MultiResponse<Track>()


data class Album(@Key var streamHash: String = "", @Key var albumId: String = "", @Key var trackNumber: Int = 0)

class Artist(@Key var name: String = "") {
    override fun toString() = name
    override fun equals(other: Any?) = other is Artist && other.name == name
    override fun hashCode() = name.hashCode()
}

/** Session obtained by self/session */
data class Session(@Key var user: User? = null, @Key var settings: Settings? = null)

/** user infos, included in a session if the connect.sid is valid */
class User(@Key var goldService: Boolean = false)

/** user settings, included in a session if the connect.sid is valid */
class Settings(@Key var preferredDownloadFormat: String = "")
