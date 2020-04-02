package xerus.monstercat.api.response

import com.google.api.client.util.Key

/**
 * Response sent by the API when doing a request to the endpoint for a Release.
 * This is the only way to get the tracks of a release starting with API v2.
 * Endpoint is connect.monstercat.com/v2/catalog/release/([Release.catalogId])
 *
 * @param release an instance of the requested [Release]
 * @param tracks a list of [tracks][Track] that appears on the [release]
 * @param related other releases that might be of interest for the user
 */
data class ReleaseResponse(
	@Key var release: Release? = null,
	@Key var tracks: ArrayList<Track>? = null,
	@Key var related: ReleaseListResponse? = null
)