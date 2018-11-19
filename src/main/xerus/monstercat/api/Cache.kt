package xerus.monstercat.api

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import xerus.ktutil.currentSeconds
import xerus.ktutil.helpers.Refresher
import xerus.monstercat.*
import xerus.monstercat.api.response.Release
import xerus.monstercat.api.response.ReleaseList
import xerus.monstercat.downloader.CONNECTSID
import java.io.File

object Cache : Refresher() {
	private val logger = KotlinLogging.logger { }
	
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
	
	override suspend fun doRefresh() {
		refreshReleases()
	}
	
	private val releases = ArrayList<Release>()
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
		if (releases.containsAll(results)) {
			logger.debug("Releases are already up to date!")
			if (!releaseCache.exists() && Settings.ENABLECACHE())
				writeCache()
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
		var cancelled = false
		releases.map { release ->
			GlobalScope.launch(globalDispatcher) {
				if (cancelled)
					return@launch
				val releaseTracks = release.tracks
				if (releaseTracks == null) {
					logger.warn("Couldn't fetch tracks for $release")
					cancelled = true
				}
			}
		}.forEach { it.join() }
		if (Settings.ENABLECACHE())
			writeCache()
	}
	
	private fun writeCache() {
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
			releaseCache.delete()
			releases.clear()
			false
		}
	}
	
	fun clear() = releases.clear()
	
}
