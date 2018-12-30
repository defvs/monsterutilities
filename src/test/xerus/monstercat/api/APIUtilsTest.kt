package xerus.monstercat.api

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import xerus.monstercat.api.response.Artist

internal class APIUtilsTest {
	
	@Test
	suspend fun find() {
		val edge = APIUtils.find("Edge Of The World", "Karma Fields")!!
		check(edge.artists, Artist("Razihel")) { v, e -> v.contains(e) }
		check(edge.artistsTitle, "Karma Fields")
	}
	
	fun <T, U> check(value: T, expected: U, test: (T, U) -> Boolean = { v, e -> v == e }) {
		Assertions.assertTrue(test(value, expected)) {
			"$value did not match $expected"
		}
	}
	
}