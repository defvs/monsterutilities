package xerus.monstercat.api.response

import com.google.api.client.util.Key
import xerus.ktutil.helpers.Parsable

data class Release(
        @Key("_id") override var
        id: String = "",
        @Key var
        releaseDate: String = "",
        @Key var
        type: String = "",
        @Key @JvmField var
        renderedArtists: String = "",
        @Key @JvmField var
        title: String = "",
        @Key var
        coverUrl: String = "",
        @Key var
        downloadable: Boolean = false): MusicResponse, Parsable {

    constructor(line: Array<String>) : this(line[0], line[1], line[2], line[3], line[4], line[5], line[6] == "1")

    fun serialize(): Array<String> {
        val (v1, v2, v3, v4, v5, v6, v7) = this
        return arrayOf(v1, v2, v3, v4, v5, v6, if (v7) "1" else "0")
    }

    var isMulti: Boolean = false

    fun init(): Release {
        renderedArtists = if (renderedArtists == "Various Artists" || renderedArtists == "Monstercat" && title.contains("Monstercat")) "" else renderedArtists.trim()
        title = title.trim()
        releaseDate = releaseDate.substring(0, 10)

        if (!isType("Mixes", "Podcast")) {
            isMulti = true
            type = when {
                title.startsWith("Monstercat 0") || title.startsWith("Monstercat Uncaged") -> "Monstercat Collection"
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
            (if (renderedArtists.isEmpty()) "%2\$s" else "%s - %s").format(renderedArtists, title)

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
