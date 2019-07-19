package xerus.monstercat.downloader

import javafx.beans.value.ObservableValue
import javafx.collections.ListChangeListener
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.control.cell.CheckBoxTreeCell
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.util.Callback
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import xerus.ktutil.javafx.MenuItem
import xerus.ktutil.javafx.controlsfx.FilterableCheckTreeView
import xerus.ktutil.javafx.expandAll
import xerus.ktutil.javafx.onError
import xerus.ktutil.javafx.onFx
import xerus.ktutil.javafx.properties.SimpleObservable
import xerus.ktutil.javafx.properties.addOneTimeListener
import xerus.ktutil.javafx.properties.dependOn
import xerus.ktutil.javafx.properties.listen
import xerus.ktutil.javafx.ui.FilterableTreeItem
import xerus.monstercat.api.APIConnection
import xerus.monstercat.api.Cache
import xerus.monstercat.api.ConnectValidity
import xerus.monstercat.api.Covers
import xerus.monstercat.api.Player
import xerus.monstercat.api.response.MusicItem
import xerus.monstercat.api.response.Release
import xerus.monstercat.api.response.Track
import xerus.monstercat.globalDispatcher
import kotlin.math.max

class SongView(private val sorter: ObservableValue<ReleaseSorting>):
	FilterableCheckTreeView<MusicItem>(FilterableTreeItem(RootMusicItem("Loading Releases..."))) {
	val logger = KotlinLogging.logger { }
	
	val ready = SimpleObservable(false)
	val roots = HashMap<CharSequence, FilterableTreeItem<MusicItem>>()
	
	private val checkCellFactory: Callback<TreeView<MusicItem>, TreeCell<MusicItem>> = Callback {
		object: CheckBoxTreeCell<MusicItem>() {
			var listener = ListChangeListener<Node> { children.filterIsInstance<CheckBox>().firstOrNull()?.isDisable = true }
			
			init {
				itemProperty().listen { item ->
					if((item as? Release)?.downloadable == false ||
						(item is Track && (treeItem.parent.value  as? Release)?.downloadable == false)) {
						if(tooltip == null) {
							listener.onChanged(null)
							children.addListener(listener)
							styleClass.add("not-downloadable")
							tooltip = Tooltip("This Release is currently not downloadable")
						}
					} else {
						if(tooltip != null) {
							children.removeListener(listener)
							children.filterIsInstance<CheckBox>().firstOrNull()?.isDisable = false
							styleClass.remove("not-downloadable")
							tooltip = null
						}
					}
				}
			}
		}
	}
	private val loadingCellFactory: Callback<TreeView<MusicItem>, TreeCell<MusicItem>> = Callback {
		val loadingGif = ImageView(Image("img/loading-16.gif"))
		object: TreeCell<MusicItem>() {
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
		onReady {
			APIConnection.connectValidity.addListener { _, old, new ->
				if(old != new && new == ConnectValidity.GOLD)
					load()
			}
		}
	}
	
	/** Asynchronously fetches the Releases and updates the View when done */
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
		}
	}
	
	private fun showRoot(show: Boolean, text: String? = null) {
		if(show) {
			cellFactory = loadingCellFactory
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
		var done = 0
		releases.toList().forEach { release ->
			val treeItem = FilterableTreeItem(release as MusicItem)
			if(!release.downloadable) {
				if(notDownloadable < 3)
					logger.trace("Not downloadable: $release")
				notDownloadable++
				treeItem.selectedProperty().listen {
					if(it)
						treeItem.isSelected = false
				}
			}
			
			roots.getOrPut(release.type) {
				FilterableTreeItem(RootMusicItem(release.type))
			}.internalChildren.add(treeItem)
			release.tracks.takeIf { it.size > 1 }?.forEach { track ->
				treeItem.internalChildren.add(CheckBoxTreeItem(track))
			}
			GlobalScope.launch(globalDispatcher) {
				var image = Covers.getCoverImage(release.coverUrl, 16)
				image.onError {
					image = Covers.getCoverImage(release.coverUrl, 16, true)
					image.onError { logger.debug("Failed to load coverUrl ${release.coverUrl} for $release", it) }
				}
				onFx {
					treeItem.graphic = ImageView(image)
					done++
					if(done % 100 == 0 || done == releases.size)
						this@SongView.refresh()
				}
			}
		}
		logger.debug { "$notDownloadable of ${releases.size} Releases are not downloadable" }
		sorter.listen { sortReleases(it.selector) }.changed(null, null, sorter.value)
		roots.flatMap { it.value.internalChildren }.forEach { release -> (release as FilterableTreeItem).internalChildren.sortBy { (it.value as Track).albums.find { it.albumId == release.value.id }?.trackNumber } }
	}
	
	private fun <T: Comparable<T>> sortReleases(selector: (Release) -> T) {
		roots.forEach { _, item -> item.internalChildren.sortBy { selector(it.value as Release) } }
	}
	
	@Suppress("UNCHECKED_CAST")
	fun getItemsInCategory(category: CharSequence) =
		roots[category]!!.children as List<FilterableTreeItem<Release>>
	
}


private data class RootMusicItem(override var title: String, override var id: String = ""): MusicItem() {
	override fun toString() = title
}