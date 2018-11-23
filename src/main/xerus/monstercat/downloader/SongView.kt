package xerus.monstercat.downloader

import javafx.beans.property.SimpleBooleanProperty
import javafx.scene.control.CheckBoxTreeItem
import javafx.scene.control.ContextMenu
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import xerus.ktutil.javafx.MenuItem
import xerus.ktutil.javafx.controlsfx.FilterableCheckTreeView
import xerus.ktutil.javafx.expandAll
import xerus.ktutil.javafx.onFx
import xerus.ktutil.javafx.properties.addOneTimeListener
import xerus.ktutil.javafx.ui.FilterableTreeItem
import xerus.monstercat.api.Cache
import xerus.monstercat.api.Player
import xerus.monstercat.api.response.MusicItem
import xerus.monstercat.api.response.Release
import xerus.monstercat.api.response.Track
import kotlin.math.max

class SongView : FilterableCheckTreeView<MusicItem>(RootMusicItem("Loading...")) {
	val logger = KotlinLogging.logger { }
	private val ready = SimpleBooleanProperty(false)
	val roots = HashMap<String, FilterableTreeItem<MusicItem>>()
	
	init {
		isShowRoot = true
		setOnMouseClicked {
			if (it.clickCount == 2) {
				val selected = selectionModel.selectedItem ?: return@setOnMouseClicked
				val value = selected.value
				if (value is Release)
					Player.play(value)
				else if (value is Track)
					Player.playTrack(value)
			}
		}
		
		val defaultItems = {
			arrayOf(MenuItem("Expand all") { expandAll() },
				MenuItem("Collapse all") { expandAll(false) })
		}
		contextMenu = ContextMenu(*defaultItems())
		/*val expandedItemMenu = ContextMenu(*defaultItems(), MenuItem("Collapse") {
			selectionModel.selectedItem.
		})
		contextMenuProperty().bindSoft({
			val selected = selectionModel.selectedItem ?: return@bindSoft defaultMenu
			when {
				selected.isExpanded -> expandedItemMenu
				else -> defaultMenu
			}
		}, selectionModel.selectedItemProperty())*/
		load()
	}
	
	fun load() {
		GlobalScope.launch {
			fetchItems()
			onFx {
				isShowRoot = false
				root.internalChildren.addAll(roots.values)
				logger.debug("Fully loaded up with ${roots.keys} displaying ${root.children.sumBy { r -> r.children.sumBy { t -> max(1, t.children.size) } }} items")
				ready.set(true)
			}
		}
	}
	
	fun onReady(function: () -> Unit) {
		if (ready.get())
			function()
		else
			ready.addOneTimeListener { function() }
	}
	
	suspend fun fetchItems() {
		Cache.getReleases().forEach { release ->
			if (!release.downloadable) {
				logger.trace { "Not displaying $release since it is not downloadable" }
				return@forEach
			}
			val treeItem = FilterableTreeItem(release as MusicItem)
			roots.getOrPut(release.type) {
				FilterableTreeItem(RootMusicItem(release.type))
			}.internalChildren.add(treeItem)
			release.tracks.takeIf { it.size > 1 }?.forEach { track ->
				treeItem.internalChildren.add(CheckBoxTreeItem(track))
			}
		}
		roots.forEach {
			it.value.internalChildren.run {
				sortBy { (it.value as Release).releaseDate }
				forEach { tr ->
					(tr as FilterableTreeItem).internalChildren.sortBy { (it.value as Track).albums.find { it.albumId == tr.value.id }?.trackNumber }
				}
			}
		}
		//roots.flatMap { it.value.internalChildren }.forEach { item -> (item as FilterableTreeItem).internalChildren.sortBy { (it.value as Track).albums.find { it.albumId == item.value.id }?.trackNumber } }
	}
	
	@Suppress("UNCHECKED_CAST")
	fun getItemsInCategory(category: String) =
		roots[category]!!.children as List<FilterableTreeItem<Release>>
	
}

private class RootMusicItem(override var title: String, override var id: String = "") : MusicItem() {
	override fun toString() = title
}