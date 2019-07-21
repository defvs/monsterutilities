package xerus.monstercat.api.response

import com.google.api.client.util.Key
import xerus.ktutil.helpers.Parsable

abstract class MusicItem: Parsable {
	abstract var id: String
	abstract var title: String
	fun formatArtists(artists: String) =
		if(artists == "Various Artists" || artists == "Various" || artists == "Monstercat" && title.contains("Monstercat")) "" else artists.trim()
}

data class Album(@Key var streamHash: String = "", @Key var albumId: String = "", @Key var trackNumber: Int = 0)