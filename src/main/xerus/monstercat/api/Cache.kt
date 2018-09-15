package xerus.monstercat.api

import mu.KotlinLogging
import xerus.ktutil.currentSeconds
import xerus.ktutil.helpers.Refresher
import xerus.monstercat.Settings
import xerus.monstercat.api.response.Release
import xerus.monstercat.api.response.Track
import xerus.monstercat.cacheDir
import xerus.monstercat.downloader.CONNECTSID
import java.io.File

object Releases : Refresher() {
	private val logger = KotlinLogging.logger { }
	
	private const val SEPARATOR = ";;"
	
	private val releaseCache: File
		get() = cacheDir.resolve("releases.txt")
	
	private var lastRefresh = 0
	private var lastCookie: String = ""
	
	private val releases = ArrayList<Release>()
	suspend fun getReleases(): ArrayList<Release> {
		if (lastCookie != CONNECTSID() || releases.isEmpty() || currentSeconds() - lastRefresh > 1000)
			refresh(true)
		job.join()
		return releases
	}
	
	override suspend fun doRefresh() {
		logger.debug("Release refresh requested")
		val releaseConnection = APIConnection("catalog", "release")
				.fields(Release::class).limit(((currentSeconds() - lastRefresh) / 80_000).coerceIn(2, 5))
		lastRefresh = currentSeconds()
		lastCookie = CONNECTSID()
		if (releases.isEmpty() && Settings.ENABLECACHE() && releaseCache.exists())
			readReleases()
		val rel = releaseConnection.getReleases() ?: run {
			logger.info("Release refresh failed!")
			return
		}
		if (releases.containsAll(rel)) {
			logger.debug("Releases are already up to date!")
			if (!releaseCache.exists() && Settings.ENABLECACHE())
				writeReleases()
			return
		}
		val ind = releases.lastIndexOf(rel.last())
		
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
			releases.addAll(rel.asReversed())
			logger.info("${releases.size - s} new Releases added, now at ${releases.size}")
		}
		if (Settings.ENABLECACHE())
			writeReleases()
	}
	
	private fun writeReleases() {
		releaseCache.bufferedWriter().use {
			for (r in releases)
				it.appendln(r.serialize().joinToString(SEPARATOR))
		}
		logger.debug("Wrote ${releases.size} Releases to Cache")
	}
	
	private fun readReleases(): Boolean {
		return try {
			releaseCache.bufferedReader().forEachLine {
				releases.add(Release(it.split(SEPARATOR).toTypedArray()).init())
			}
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

object Tracks {
	val tracks: ArrayList<Track>? = APIConnection("catalog", "track").fields(Track::class).getTracks()
}
