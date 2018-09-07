package xerus.monstercat.tabs

import javafx.beans.value.ObservableValue
import javafx.scene.control.*
import javafx.scene.input.MouseButton
import javafx.util.Callback
import xerus.ktutil.javafx.MenuItem
import xerus.ktutil.javafx.fill
import xerus.ktutil.javafx.properties.ImmutableObservable
import xerus.monstercat.api.Player
import xerus.monstercat.api.Playlist
import xerus.monstercat.api.response.Track


class TabPlaylist : VTab() {
	var table = TableView<Track>().apply {
		columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY
	}
	
	init {
		table.items = Playlist.tracks
		
		table.columns.addAll(TableColumn<Track, String>("Artists").apply {
			cellValueFactory = Callback<TableColumn.CellDataFeatures<Track, String>, ObservableValue<String>> { p ->
				ImmutableObservable(p.value.artistsTitle)
			}
		}, TableColumn<Track, String>("Title").apply {
			cellValueFactory = Callback<TableColumn.CellDataFeatures<Track, String>, ObservableValue<String>> { p ->
				ImmutableObservable(p.value.title)
			}
		})
		
		table.selectionModel.selectionMode = SelectionMode.SINGLE
		
		table.setOnMouseClicked { me ->
			if (me.button == MouseButton.PRIMARY && me.clickCount == 2) {
				useSelectedTrack { Player.play(it.title, it.artistsTitle) }
			}
		}
		
		table.placeholder = Label("Nothing's in your playlist. Right Click any song in the catalog to add it here!")
		
		val rightClickMenu = ContextMenu()
		val item1 = MenuItem("Play") {
			useSelectedTrack { Player.play(it.title, it.artistsTitle) }
		}
		val item2 = MenuItem("Play Next") {
			useSelectedTrack { Playlist.addNext(it) }
		}
		val item3 = MenuItem("Remove") {
			Playlist.removeTrack(table.selectionModel.selectedIndex)
		}
		rightClickMenu.items.addAll(item1, item2, item3)
		table.contextMenu = rightClickMenu
		
		fill(table)
	}
	
	inline fun useSelectedTrack(action: (Track) -> Unit) {
		Playlist[table.selectionModel.selectedIndex]?.let { action(it) }
	}
	
}
