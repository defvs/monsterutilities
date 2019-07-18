package xerus.monstercat.api

import io.kotlintest.matchers.collections.shouldNotContain
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import xerus.monstercat.api.response.ArtistRel

internal class APIUtilsTest: StringSpec({
	
	"find Edge of the World by Karma Fields" {
		val edge = APIUtils.find("Edge Of The World", "Karma Fields")!!
		edge.artists shouldNotContain ArtistRel("Razihel")
		edge.artistsTitle shouldBe "Karma Fields"
	}
	
})