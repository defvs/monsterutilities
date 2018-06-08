package xerus.monstercat.api.response

import com.google.api.client.util.Key
import java.nio.file.Path

fun Class<*>.declaredKeys() =
        declaredFields.mapNotNull { ((it.annotations.find { it is Key } as Key?) ?: return@mapNotNull null).value.takeUnless { it == "##default" } ?: it.name }.toTypedArray()

interface MusicResponse {
    var id: String
}

data class Album(@Key var streamHash: String = "", @Key var albumId: String = "", @Key var trackNumber: Int = 0)

class Artist(@Key var name: String = "") {
    override fun toString() = name
    override fun equals(other: Any?) = other is Artist && other.name == name
    override fun hashCode() = name.hashCode()
}