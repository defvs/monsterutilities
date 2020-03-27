package xerus.monstercat.api.response

import com.google.api.client.util.Key

data class MixedReleaseResponse(
	@Key var release: Release? = null,
	@Key var tracks: ArrayList<Track>? = null
)