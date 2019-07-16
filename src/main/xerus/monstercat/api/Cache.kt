package xerus.monstercat.api

import javafx.collections.FXCollections
import javafx.collections.ObservableList
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import mu.KotlinLogging
import xerus.ktutil.currentSeconds
import xerus.ktutil.helpers.Refresher
import xerus.monstercat.Settings
import xerus.monstercat.Sheets
import xerus.monstercat.api.response.Release
import xerus.monstercat.api.response.ReleaseList
import xerus.monstercat.api.response.ReleaseResponse
import xerus.monstercat.api.response.Track
import xerus.monstercat.cacheDir
import xerus.monstercat.downloader.CONNECTSID
import xerus.monstercat.globalDispatcher
import java.io.File

private const val cacheVersion = 3

object Cache: Refresher() {
	private val logger = KotlinLogging.logger { }
	
	private val releaseCache: File
		get() = cacheDir.resolve("releases.json")
	
	private var lastRefresh = 0
	private var lastCookie: String = ""
	
	val releases: ObservableList<Release> = FXCollections.observableArrayList<Release>()
	
	suspend fun getReleases(): List<Release> {
		if(lastCookie != CONNECTSID() || releases.isEmpty() || currentSeconds() - lastRefresh > 1000)
			refresh(true)
		job.join()
		return releases
	}
	
	/** Gets all tracks by flatMapping all the tracks of all Releases. Two tracks with the same id will only yield one (hashSet) */
	suspend fun getTracks(): Collection<Track> =
		getReleases().flatMap { it.tracks }.toHashSet()
	
	/** Gets all tracks by flatMapping all the tracks of all Releases. Will return all tracks, regardless of id duplicates.
	 * This is very useful when you need a separate Track for each Release it is part of.*/
	suspend fun getAllTracks(): Collection<Track> =
		getReleases().flatMap { it.tracks }
	
	override suspend fun doRefresh() {
		refreshReleases()
	}
	
	private suspend fun refreshReleases() {
		logger.debug("Release refresh requested")
		lastRefresh = currentSeconds()
		lastCookie = CONNECTSID()
		
		if(releases.isEmpty() && Settings.ENABLECACHE())
			readCache()
		val releaseResponse = APIConnection("catalog", "release").fields(Release::class)
			.limit(((currentSeconds() - lastRefresh) / 80_000).coerceIn(4, 9))
			.parseJSON(ReleaseResponse::class.java)?.also { it.results.forEach { it.init() } }
			?: run {
				logger.info("Release refresh failed!")
				return
			}
		val results = releaseResponse.results
		
		val releaseConnection = APIConnection("catalog", "release").fields(Release::class)
		when {
			releaseResponse.total - releases.size > results.size || !releases.contains(results.last()) -> {
				logger.info("Full Release refresh initiated")
				releases.clear()
				var i = 0
				while(true) {
					val result = releaseConnection.skip(i * 49).getReleases()
					if(result == null) {
						logger.warn("Release refresh failed!")
						break
					} else if(result.isEmpty()) {
						break
					}
					releases.removeAll(result)
					releases.addAll(result)
					i++
				}
				logger.info("API returned ${releases.size} Releases")
			}
			releases.containsAll(results) && releases.size == releaseResponse.total -> {
				logger.debug("Releases are already up to date!")
			}
			else -> {
				val s = releases.size
				releases.removeAll(results)
				releases.addAll(0, results)
				logger.info("${releases.size - s} new Releases added, now at ${releases.size}")
			}
		}
		fetchTracksForReleases()
	}
	
	/** Fetches the tracks for each Release.
	 * @return true iff it fetched the tracks for every Release successfully */
	private suspend fun fetchTracksForReleases(): Boolean {
		logger.info("Fetching Tracks for ${releases.size} Releases")
		var failed = 0
		var success = false
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
					success = true
					return@async true
				}
			}
		}.forEach { e ->
			if(e.value?.await() == false)
				releases.remove(e.key)
		}
		if(!success && failed == 0)
			logger.debug("Tracks are already up to date!")
		else
			logger.debug { "Fetched ${releases.sumBy { it.tracks.size }} Tracks with $failed failures" }
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
		releases.clear()
		cacheDir.deleteRecursively()
	}
	
}
