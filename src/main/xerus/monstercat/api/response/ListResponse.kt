package xerus.monstercat.api.response

import com.google.api.client.util.Key

open class ListResponse<T> {
	@Key
	lateinit var results: ArrayList<T>
	@Key
	var total: Int = 0
	
	override fun toString() = "${this.javaClass.simpleName}($total elements): $results"
}

class ReleaseListResponse: ListResponse<Release>()
class TrackListResponse: ListResponse<Track>()

class ReleaseList: ArrayList<Release>()