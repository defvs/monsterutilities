package xerus.monstercat.api

import kotlinx.coroutines.*
import mu.KotlinLogging
import xerus.ktutil.currentSeconds
import xerus.ktutil.helpers.Refresher
import xerus.monstercat.*
import xerus.monstercat.api.response.Release
import xerus.monstercat.api.response.ReleaseList
import xerus.monstercat.api.response.Track
import xerus.monstercat.downloader.CONNECTSID
import java.io.File
import java.util.function.BiConsumer

object Cache : Refresher() {
	private val logger = KotlinLogging.logger { }
	
	private val releases = ArrayList<Release>()
	
	private val releaseCache: File
		get() = cacheDir.resolve("releases.json")
	
	private var lastRefresh = 0
	private var lastCookie: String = ""
	
	suspend fun getReleases(): ArrayList<Release> {
		if (lastCookie != CONNECTSID() || releases.isEmpty() || currentSeconds() - lastRefresh > 1000)
			refresh(true)
		job.join()
		return releases
	}
	
	/** Gets all tracks by flatMapping all the tracks of */
	suspend fun getTracks() =
		getReleases().flatMap { it.tracks }
	
	override suspend fun doRefresh() {
		refreshReleases()
	}
	
	private suspend fun refreshReleases() {
		logger.debug("Release refresh requested")
		val releaseConnection = APIConnection("catalog", "release")
			.fields(Release::class).limit(((currentSeconds() - lastRefresh) / 80_000).coerceIn(2, 5))
		lastRefresh = currentSeconds()
		lastCookie = CONNECTSID()
		
		if (releases.isEmpty() && Settings.ENABLECACHE() && releaseCache.exists())
			readCache()
		val results = releaseConnection.getReleases() ?: run {
			logger.info("Release refresh failed!")
			return
		}
		if (releases.containsAll(results) && fetchTracksForReleases()) {
			logger.debug("Releases are already up to date!")
			return
		}
		val ind = releases.lastIndexOf(results.last())
		
		if (ind == -1) {
			logger.info("Full Release refresh initiated")
			releaseConnection.removeQuery("limit").getReleases()?.let {
				releases.clear()
				releases.addAll(it.asReversed())
			} ?: run {
				logger.warn("Release refresh failed!")
				return
			}
			logger.info("Found ${releases.size} Releases")
		} else {
			val s = releases.size
			releases.removeAll(releases.subList(ind, releases.size))
			releases.addAll(results.asReversed())
			logger.info("${releases.size - s} new Releases added, now at ${releases.size}")
		}
		fetchTracksForReleases()
	}
	
	/** Fetches the tracks for each Release.
	 * @return true when it fetched the tracks for every Release successfully, false otherwise */
	private suspend fun fetchTracksForReleases(): Boolean {
		var failed = 0
		releases.associateWith { release ->
			if(release.tracks.isNotEmpty()) return@associateWith null
			GlobalScope.async(globalDispatcher) {
				val tracks = APIConnection("catalog", "release", release.id, "tracks").getTracks()
				if (tracks == null) {
					logger.warn("Couldn't fetch tracks for $release")
					failed++
					return@async false
				} else {
					release.tracks = tracks
					return@async true
				}
			}
		}.forEach { e ->
			if (e.value?.await() == false)
				releases.remove(e.key)
		}
		if (Settings.ENABLECACHE())
			writeCache()
		return failed == 0
	}
	
	private fun writeCache() {
		logger.trace("Writing ${releases.size} Releases to $releaseCache")
		releaseCache.writeText(Sheets.JSON_FACTORY.toPrettyString(releases))
		logger.debug("Wrote ${releases.size} Releases to $releaseCache")
	}
	
	private fun readCache(): Boolean {
		return try {
			releases.addAll(Sheets.JSON_FACTORY.fromString(releaseCache.reader().readText(), ReleaseList::class.java))
			logger.debug("Read ${releases.size} Releases from $releaseCache")
			true
		} catch (e: Throwable) {
			logger.debug("Cache corrupted - clearing: $e", e)
			releases.clear()
			false
		}
	}
	
	fun clear() {
		logger.debug("Clearing Cache")
		releaseCache.delete()
		releases.clear()
	}
	
}
