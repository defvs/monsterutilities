package xerus.monstercat.api

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import xerus.monstercat.api.response.Artist

internal class APITest {
	
	@Test
	fun find() {
		check(API.find("Edge Of The World", "Razihel & Xilent")!!.artists, Artist("Razihel")) { v, e -> v.contains(e) }
		check(API.find("Edge Of The World", "Karma Fields")!!.artistsTitle, "Karma Fields")
	}
	
	fun <T, U> check(value: T, expected: U, test: (T, U) -> Boolean = { v, e -> v == e }) {
		Assertions.assertTrue(test(value, expected)) {
			"$value did not match $expected"
		}
	}
	
}