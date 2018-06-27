package xerus.monstercat.api

import xerus.monstercat.api.response.Artist

internal class PlayerTest {
	
	@org.junit.jupiter.api.Test
	fun find() {
		assert(Player.find("Edge Of The World", "Razihel & Xilent")?.artists?.contains(Artist("Razihel"))!!)
		assert(Player.find("Edge Of The World", "Karma Fields")?.artistsTitle == "Karma Fields")
	}
	
}