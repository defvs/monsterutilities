package xerus.monstercat.downloader

import javafx.beans.value.ObservableValue
import javafx.scene.control.*
import javafx.scene.control.cell.CheckBoxTreeCell
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.util.Callback
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import xerus.ktutil.getResourceAsFile
import xerus.ktutil.javafx.MenuItem
import xerus.ktutil.javafx.controlsfx.FilterableCheckTreeView
import xerus.ktutil.javafx.expandAll
import xerus.ktutil.javafx.onFx
import xerus.ktutil.javafx.properties.*
import xerus.ktutil.javafx.ui.FilterableTreeItem
import xerus.monstercat.api.*
import xerus.monstercat.api.response.MusicItem
import xerus.monstercat.api.response.Release
import xerus.monstercat.api.response.Track
import kotlin.math.max

class SongView(private val sorter: ObservableValue<ReleaseSorting>) :
	FilterableCheckTreeView<MusicItem>(FilterableTreeItem(RootMusicItem("Loading Releases..."))) {
	val logger = KotlinLogging.logger { }
	
	val ready = SimpleObservable(false)
	val roots = HashMap<String, FilterableTreeItem<MusicItem>>()
	
	private val checkCellFactory: Callback<TreeView<MusicItem>, TreeCell<MusicItem>> = Callback {
		CheckBoxTreeCell<MusicItem>().also { cell ->
			cell.itemProperty().listen { item ->
				if((item as? Release)?.downloadable == false ||
					(item is Track && (cell.treeItem.parent.value  as? Release)?.downloadable == false)) {
					if(cell.tooltip == null) {
						cell.styleClass.add("not-downloadable")
						cell.tooltip = Tooltip("This Release is currently not downloadable")
					}
				} else {
					if(cell.tooltip != null) {
						cell.styleClass.remove("not-downloadable")
						cell.tooltip = null
					}
				}
			}
		}
	}
	private val treeCellFactory: Callback<TreeView<MusicItem>, TreeCell<MusicItem>> = Callback {
		val loadingGif = ImageView(Image("img/loading-16.gif"))
		object : TreeCell<MusicItem>() {
			init {
				treeItemProperty().listen {
					if(it != null) {
						textProperty().dependOn(it.valueProperty()) { it.toString() }
						this.graphic = loadingGif
					}
				}
			}
		}
	}
	
	init {
		showRoot(true)
		setOnMouseClicked {
			if(it.clickCount == 2) {
				val selected = selectionModel.selectedItem ?: return@setOnMouseClicked
				val value = selected.value
				when(value) {
					is Release -> Player.play(value)
					is Track -> Player.playTrack(value)
					else -> selected.isExpanded = !selected.isExpanded
				}
			}
		}
		
		val defaultItems = {
			arrayOf(MenuItem("Expand all") { expandAll() },
				MenuItem("Collapse all") { expandAll(false) })
		}
		contextMenu = ContextMenu(*defaultItems())
		load()
	}
	
	fun load() {
		ready.value = false
		GlobalScope.launch {
			roots.values.clear()
			fetchItems()
			onFx {
				showRoot(false)
				root.internalChildren.setAll(roots.values)
				logger.debug("Completely loaded with ${roots.keys} displaying ${root.children.sumBy { r -> r.children.sumBy { t -> max(1, t.children.size) } }} items")
				ready.value = true
			}
			APIConnection.connectValidity.addListener { _, old, new ->
				if(old != new && new == ConnectValidity.GOLD)
					load()
			}
		}
	}
	
	private fun showRoot(show: Boolean, text: String? = null) {
		if(show) {
			cellFactory = treeCellFactory
			if(text != null)
				root.value = RootMusicItem(text)
			isShowRoot = true
		} else {
			isShowRoot = false
			cellFactory = checkCellFactory
		}
	}
	
	fun onReady(function: () -> Unit) {
		if(ready.value)
			function()
		else
			ready.addOneTimeListener { function() }
	}
	
	private suspend fun fetchItems() {
		var notDownloadable = 0
		val releases = Cache.getReleases()
		releases.forEach { release ->
			val treeItem = FilterableTreeItem(release as MusicItem)
			if(!release.downloadable)
				notDownloadable++
			roots.getOrPut(release.type) {
				FilterableTreeItem(RootMusicItem(release.type))
			}.internalChildren.add(treeItem)
			release.tracks.takeIf { it.size > 1 }?.forEach { track ->
				treeItem.internalChildren.add(CheckBoxTreeItem(track))
			}
		}
		logger.debug { "$notDownloadable of ${releases.size} Releases are not downloadable" }
		sorter.listen { sortReleases(it.selector) }.changed(null, null, sorter.value)
		roots.flatMap { it.value.internalChildren }.forEach { release -> (release as FilterableTreeItem).internalChildren.sortBy { (it.value as Track).albums.find { it.albumId == release.value.id }?.trackNumber } }
	}
	
	private fun <T : Comparable<T>> sortReleases(selector: (Release) -> T) {
		roots.forEach { _, item -> item.internalChildren.sortBy { selector(it.value as Release) } }
	}
	
	@Suppress("UNCHECKED_CAST")
	fun getItemsInCategory(category: String) =
		roots[category]!!.children as List<FilterableTreeItem<Release>>
	
}


private class RootMusicItem(override var title: String, override var id: String = "") : MusicItem() {
	override fun toString() = title
}