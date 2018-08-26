package xerus.monstercat.downloader

import javafx.scene.control.TreeItem
import kotlinx.coroutines.experimental.launch
import xerus.ktutil.javafx.controlsfx.FilterableCheckTreeView
import xerus.ktutil.javafx.onFx
import xerus.ktutil.javafx.ui.FilterableTreeItem
import xerus.monstercat.api.Cache
import xerus.monstercat.api.Player
import xerus.monstercat.api.response.MusicItem
import xerus.monstercat.api.response.Release
import xerus.monstercat.api.response.Track

class SongView : FilterableCheckTreeView<MusicItem>(RootMusicItem("Releases")) {
	var ready = false
	val roots = HashMap<String, FilterableTreeItem<MusicItem>>()
	
	init {
		isShowRoot = false
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
		load()
	}
	
	fun load() {
		launch {
			fetchItems()
			onFx {
				root.internalChildren.addAll(roots.values)
				ready = true
			}
		}
	}
	
	suspend fun fetchItems() {
		Cache.getReleases().forEach { release ->
			val treeItem = FilterableTreeItem(release as MusicItem)
			roots.getOrPut(release.type) {
				FilterableTreeItem(RootMusicItem(release.type))
			}.internalChildren.add(treeItem)
			release.tracks?.takeIf { it.size > 1 }?.forEach { track ->
				treeItem.internalChildren.add(TreeItem(track))
			}
		}
		roots.forEach { it.value.internalChildren.sortBy { (it.value as Release).releaseDate } }
		//roots.flatMap { it.value.internalChildren }.forEach { item -> (item as FilterableTreeItem).internalChildren.sortBy { (it.value as Track).albums.find { it.albumId == item.value.id }?.trackNumber } }
	}
	
}

private class RootMusicItem(override var title: String, override var id: String = "") : MusicItem() {
	override fun toString() = title
}