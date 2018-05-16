package xerus.monstercat.api

import xerus.ktutil.*
import xerus.ktutil.helpers.Refresher
import xerus.monstercat.Settings
import xerus.monstercat.api.response.Release
import xerus.monstercat.api.response.Track
import xerus.monstercat.cachePath
import xerus.monstercat.downloader.CONNECTSID
import xerus.monstercat.logger
import java.io.File

object Releases : Refresher() {
	
	private const val SEPARATOR = ";;"
	
	private val releaseCache: File
		get() = cachePath.resolve("releases.txt").toFile()
	
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
		logger.finer("Release refresh requested")
		val releaseConnection = APIConnection("catalog", "release")
				.fields(Release::class.java).limit(((currentSeconds() - lastRefresh) / 80_000).coerceIn(2, 5))
		lastRefresh = currentSeconds()
		lastCookie = CONNECTSID()
		if (releases.isEmpty() && Settings.ENABLECACHE() && releaseCache.exists())
			readReleases()
		val rel = releaseConnection.getReleases() ?: run {
			logger.info("Release refresh failed!")
			return
		}
		if (releases.containsAll(rel)) {
			logger.finer("Releases are already up to date!")
			if (!releaseCache.exists() && Settings.ENABLECACHE())
				writeReleases()
			return
		}
		val ind = releases.lastIndexOf(rel.last())
		
		if (ind == -1) {
			logger.fine("Full Release refresh initiated")
			releaseConnection.removeQuery("limit").getReleases()?.let {
				releases.clear()
				releases.addAll(it.asReversed())
			} ?: run {
				logger.warning("Release refresh failed!")
				return
			}
			logger.fine("Found ${releases.size} Releases")
		} else {
			val s = releases.size
			releases.removeAll(releases.subList(ind, releases.size))
			releases.addAll(rel.asReversed())
			logger.fine("${releases.size - s} new Releases added, now at ${releases.size}")
		}
		if (Settings.ENABLECACHE())
			writeReleases()
	}
	
	private fun writeReleases() {
		releaseCache.bufferedWriter().use {
			for (r in releases)
				it.appendln(r.serialize().joinToString(SEPARATOR))
		}
		logger.fine("Wrote ${releases.size} Releases to Cache")
	}
	
	private fun readReleases(): Boolean {
		return try {
			releaseCache.bufferedReader().forEachLine {
				releases.add(Release(it.split(SEPARATOR).toTypedArray()).init())
			}
			true
		} catch (e: Throwable) {
			releases.clear()
			false
		}
	}
	
}

object Tracks {
	val tracks: ArrayList<Track>? = APIConnection("catalog", "track").fields(Track::class.java).getTracks()
}
