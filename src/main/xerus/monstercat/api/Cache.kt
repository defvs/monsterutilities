package xerus.monstercat.api

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import mu.KotlinLogging
import xerus.ktutil.currentSeconds
import xerus.ktutil.helpers.Refresher
import xerus.monstercat.*
import xerus.monstercat.api.response.Release
import xerus.monstercat.api.response.ReleaseList
import xerus.monstercat.downloader.CONNECTSID
import java.io.File

private const val cacheVersion = 2

object Cache: Refresher() {
	private val logger = KotlinLogging.logger { }
	
	private val releases = ArrayList<Release>()
	
	private val releaseCache: File
		get() = cacheDir.resolve("releases.json")
	
	private var lastRefresh = 0
	private var lastCookie: String = ""
	
	suspend fun getReleases(): ArrayList<Release> {
		if(lastCookie != CONNECTSID() || releases.isEmpty() || currentSeconds() - lastRefresh > 1000)
			refresh(true)
		job.join()
		return releases
	}
	
	/** Gets all tracks by flatMapping all the tracks of all Releases */
	suspend fun getTracks() =
		getReleases().flatMap { it.tracks }
	
	override suspend fun doRefresh() {
		refreshReleases()
	}
	
	private suspend fun refreshReleases() {
		logger.debug("Release refresh requested")
		val releaseConnection = APIConnection("catalog", "release")
			.fields(Release::class).limit(((currentSeconds() - lastRefresh) / 80_000).coerceIn(2, 9))
		lastRefresh = currentSeconds()
		lastCookie = CONNECTSID()
		
		if(releases.isEmpty() && Settings.ENABLECACHE())
			readCache()
		val results = releaseConnection.getReleases() ?: run {
			logger.info("Release refresh failed!")
			return
		}
		if(releases.containsAll(results) && fetchTracksForReleases()) {
			logger.debug("Releases are already up to date!")
			return
		}
		val ind = releases.lastIndexOf(results.last())
		
		if(ind == -1) {
			logger.info("Full Release refresh initiated")
			releaseConnection.removeQuery("limit").getReleases()?.let {
				releases.clear()
				releases.addAll(it.asReversed())
			} ?: run {
				logger.warn("Release refresh failed!")
				return
			}
			logger.info("API returned ${releases.size} Releases")
		} else {
			val s = releases.size
			releases.removeAll(releases.subList(ind, releases.size))
			releases.addAll(results.asReversed())
			logger.info("${releases.size - s} new Releases added, now at ${releases.size}")
		}
		fetchTracksForReleases()
	}
	
	/** Fetches the tracks for each Release.
	 * @return true iff it fetched the tracks for every Release successfully */
	private suspend fun fetchTracksForReleases(): Boolean {
		logger.info("Fetching Tracks for ${releases.size} Releases")
		var failed = 0
		releases.associateWith { release ->
			if(release.tracks.isNotEmpty()) return@associateWith null
			GlobalScope.async(globalDispatcher) {
				val tracks = APIConnection("catalog", "release", release.id, "tracks").getTracks()
				if(tracks == null) {
					logger.warn("Couldn't fetch tracks for $release")
					failed++
					return@async false
				} else {
					release.tracks = tracks
					return@async true
				}
			}
		}.forEach { e ->
			if(e.value?.await() == false)
				releases.remove(e.key)
		}
		logger.debug { "Fetched ${releases.sumBy { it.tracks.size }} Tracks" }
		if(Settings.ENABLECACHE())
			writeCache()
		return failed == 0
	}
	
	private fun writeCache() {
		logger.trace("Writing ${releases.size} Releases to $releaseCache")
		releaseCache.writeText(cacheVersion.toString().padStart(2, '0') + "\n" + Sheets.JSON_FACTORY.toPrettyString(releases))
		logger.debug("Wrote ${releases.size} Releases to $releaseCache")
	}
	
	private fun readCache(): Boolean {
		if(!releaseCache.exists())
			return false
		return try {
			releaseCache.reader().use { reader ->
				val version = CharArray(3).let {
					reader.read(it)
					String(it, 0, 2).toIntOrNull()
				}
				if(version != cacheVersion) {
					logger.debug("Cache outdated - current: $version new: $cacheVersion")
					return false
				}
				releases.addAll(Sheets.JSON_FACTORY.fromString(reader.readText(), ReleaseList::class.java))
			}
			logger.debug("Read ${releases.size} Releases from $releaseCache")
			true
		} catch(e: Throwable) {
			logger.debug("Cache reading failed: $e", e)
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
