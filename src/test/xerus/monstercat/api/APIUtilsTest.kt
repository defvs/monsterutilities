package xerus.monstercat.api

import io.kotlintest.inspectors.forAll
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import kotlinx.coroutines.runBlocking
import xerus.ktutil.printIt

data class SongSearch(val searchTerm: String, val expectedTerm: String? = null)

internal class APIUtilsTest: StringSpec({
	
	"APIUtils.find" {
		arrayOf(
			SongSearch("Karma Fields - Edge of the World"),
			SongSearch("Julian Calor - Monster (feat. Trove)", "Julian Calor feat. Trove - Monster"),
			SongSearch("Rootkit - Voyage (Kage Remix)")
		).forAll {
			runBlocking {
				val search = it.searchTerm.split(" - ")
				val result = APIUtils.find(search[1], search[0])
				val expected = (it.expectedTerm ?: it.searchTerm).split(" - ", " (", ")")
				result shouldBe Cache.getTracks().find {
					it.artistsTitle == expected[0] && it.title == expected[1] && (expected.size < 3 || it.version == expected[2])
				}.printIt()
			}
		}
	}
	
})