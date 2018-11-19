package xerus.monstercat.api.response

import com.google.api.client.util.Key
import xerus.ktutil.helpers.Parsable
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

val KClass<*>.declaredKeys
	get() = memberProperties.mapNotNull {
		(it.javaField?.getDeclaredAnnotation(Key::class.java)
			?: return@mapNotNull null).value.takeUnless { it == "##default" } ?: it.name
	}

abstract class MusicItem : Parsable {
	abstract var id: String
	abstract var title: String
	fun formatArtists(artists: String) =
		if (artists == "Various Artists" || artists == "Various" || artists == "Monstercat" && title.contains("Monstercat")) "" else artists.trim()
}

data class Album(@Key var streamHash: String = "", @Key var albumId: String = "", @Key var trackNumber: Int = 0)

class Artist(@Key var name: String = "") {
	override fun toString() = name
	override fun equals(other: Any?) = other is Artist && other.name == name
	override fun hashCode() = name.hashCode()
}