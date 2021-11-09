package xerus.monstercat.api.response

import com.google.api.client.util.Key

open class ListResponse<T> {
	@Key("Data")
	lateinit var results: ArrayList<T>
	@Key("Total")
	var total: Int = 0
	
	override fun toString() = "${this.javaClass.simpleName}($total elements): $results"
}

class ReleaseListResponse {
	@Key("Releases") lateinit var releases: ListResponse<Release>
}
class TrackListResponse {
	@Key("Tracks") lateinit var tracks: ListResponse<Release>
}

class ReleaseList: ArrayList<Release>()