package xerus.monstercat.api.response

import com.google.api.client.util.Key

open class ListResponse<T> {
	@Key
	lateinit var results: ArrayList<T>
	@Key
	var total: Int = 0
	
	override fun toString() = "${this.javaClass.simpleName}($total elements): $results"
}

class ReleaseResponse: ListResponse<Release>()
class TrackResponse: ListResponse<Track>()

class ReleaseList: ArrayList<Release>()