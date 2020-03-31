package xerus.monstercat.api.response

import com.google.api.client.util.Key

/**
 * Response sent by the API when doing a request to the endpoint for a Release
 * This is the only way to get the tracks of a release starting with API v2
 */
data class ReleaseResponse(
	@Key var release: Release? = null,
	@Key var tracks: ArrayList<Track>? = null
)