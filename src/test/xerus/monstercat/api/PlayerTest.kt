package xerus.monstercat.api

import xerus.monstercat.api.response.Artist

internal class APITest {
	
	@org.junit.jupiter.api.Test
	fun find() {
		assert(API.find("Edge Of The World", "Razihel & Xilent")!!.artists.contains(Artist("Razihel")))
		assert(API.find("Edge Of The World", "Karma Fields")!!.artistsTitle == "Karma Fields")
	}
	
}