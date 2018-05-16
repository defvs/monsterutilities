package xerus.monstercat.tabs

import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.Label
import xerus.ktutil.helpers.DelayedRefresher
import xerus.ktutil.helpers.RoughMap
import xerus.ktutil.helpers.SimpleRefresher
import xerus.ktutil.javafx.add
import xerus.ktutil.javafx.createButton
import xerus.ktutil.javafx.onJFX
import xerus.ktutil.javafx.styleClass
import xerus.ktutil.javafx.ui.controls.Snackbar
import xerus.ktutil.readObject
import xerus.ktutil.writeObject
import xerus.monstercat.MCatalog.fetchSheet
import xerus.monstercat.Settings
import xerus.monstercat.api.Releases
import xerus.monstercat.logger
import xerus.monstercat.monsterUtilities
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

const val snackbarTextCache = "MCatalog was restored from cache"

abstract class FetchTab : VTab() {
	
	val cols = RoughMap<Int>()
	val data: ObservableList<List<String>> = FXCollections.observableArrayList()
	
	protected open val request: String = ""
	private val retryButton: Button = createButton("Try again") {
		setPlaceholder(Label("Fetching..."))
		sheetFetcher()
	}
	
	val sheetFetcher = SimpleRefresher {
		onJFX { setPlaceholder(Label("Fetching...")) }
		logger.fine("Fetching MCatalog $tabName")
		val sheet = fetchSheet(tabName, request)
		if (sheet != null) {
			readSheet(sheet)
			writeCache(sheet)
		} else if (data.isEmpty())
			restoreCache()
		onJFX {
			if (data.isEmpty()) {
				logger.finer("Showing retry button for $tabName because data is empty")
				setPlaceholder(retryButton)
			} else
				setPlaceholder(Label("No matches found!"))
		}
	}
	
	init {
		onJFX {
			add(notification)
			setPlaceholder(Label("Loading..."))
		}
		styleClass("fetch-tab")
		sheetFetcher()
	}
	
	abstract fun setPlaceholder(n: Node)
	
	fun readSheet(sheet: MutableList<List<String>>) {
		readCols(sheet[0])
		onJFX {
			sheetToData(sheet.subList(1, sheet.size))
		}
	}
	
	fun readCols(row: List<String>) {
		cols.clear()
		row.forEachIndexed { i, s -> cols.put(s, i) }
	}
	
	open fun sheetToData(sheet: List<List<String>>) {
		data.setAll(sheet)
	}
	
	// region caching
	
	private val cachePath: Path
		get() = xerus.monstercat.cachePath.resolve("MCatalog $tabName")
	
	private fun writeCache(sheet: Any) {
		if (!Settings.ENABLECACHE())
			return
		logger.fine("Writing cache file $cachePath")
		try {
			writeObject(cachePath.toFile(), sheet)
		} catch (e: IOException) {
			monsterUtilities.showError(e, "Couldn't write $tabName cache!")
		}
	}
	
	private fun restoreCache() {
		if (!Settings.ENABLECACHE())
			return
		try {
			readSheet(readObject(cachePath.toFile()))
			logger.fine("Restored cache file $cachePath")
			showNotification(snackbarTextCache)
		} catch (ignored: FileNotFoundException) {
		} catch (e: Throwable) {
			logger.throwing(javaClass.simpleName, "restoreCache", e)
			Files.delete(cachePath)
		}
	}
	
	//endregion
	
	protected val notification = Snackbar()
	
	fun showNotification(text: String, reopen: Boolean = true) =
		notification.showText(text, reopen)
	
	override fun toString(): String = "FetchTab for $tabName"
	
	abstract fun refreshView()
	
	companion object {
		init {
			Settings.GENRECOLORS.addListener { _ -> viewRefresher() }
		}
		
		private val viewRefresher = DelayedRefresher(400) {
			refreshViews()
		}
		
		fun refreshViews() {
			forAllFetchTabs { refreshView() }
		}
		
		fun writeCache() {
			Releases.refresh()
			forAllFetchTabs { sheetFetcher.refresh() }
		}
		
		private inline fun forAllFetchTabs(runnable: FetchTab.() -> Unit) =
				monsterUtilities.tabsByClass<FetchTab>().forEach { runnable(it) }
		
	}
	
}
