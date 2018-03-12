package xerus.monstercat.api.response

import com.google.api.client.util.Key

open class ListResponse<T> {
	@Key
	lateinit var results: ArrayList<T>
	@Key
	var total: Int = 0
}

class ReleaseResponse: ListResponse<Release>()
class TrackResponse: ListResponse<Track>()
