package xerus.monstercat.tabs

import javafx.beans.value.ObservableValue
import javafx.scene.control.*
import javafx.scene.input.KeyCode
import javafx.scene.input.MouseButton
import javafx.util.Callback
import xerus.ktutil.javafx.MenuItem
import xerus.ktutil.javafx.fill
import xerus.ktutil.javafx.properties.ImmutableObservable
import xerus.ktutil.javafx.properties.listen
import xerus.monstercat.api.Player
import xerus.monstercat.api.Playlist
import xerus.monstercat.api.response.Track


class TabPlaylist : VTab() {
	private var table = TableView<Track>().apply {
		columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY
		items = Playlist.tracks
		columns.addAll(TableColumn<Track, String>("Artists").apply {
			cellValueFactory = Callback<TableColumn.CellDataFeatures<Track, String>, ObservableValue<String>> { p ->
				ImmutableObservable(p.value.artistsTitle)
			}
		}, TableColumn<Track, String>("Title").apply {
			cellValueFactory = Callback<TableColumn.CellDataFeatures<Track, String>, ObservableValue<String>> { p ->
				ImmutableObservable(p.value.title)
			}
		})

		setRowFactory {
			TableRow<Track>().apply {
				Playlist.currentIndex.listen {
					style = "-fx-background-color: ${if (index == it) "#1f6601" else "transparent"}"
				}
				itemProperty().listen {
					style = "-fx-background-color: ${if (index == Playlist.currentIndex.value) "#1f6601" else "transparent"}"
				}
			}
		}

		selectionModel.selectionMode = SelectionMode.SINGLE

		fun removeFromPlaylist() = useSelectedIndex { Playlist.removeAt(it) }
		fun playFromPlaylist() = useSelectedIndex { Player.playFromPlaylist(it) }
		fun playNextPlaylist() = useSelectedTrack { Playlist.addNext(it) }

		setOnMouseClicked { me ->
			if (me.button == MouseButton.PRIMARY && me.clickCount == 2) {
				playFromPlaylist()
			}
			if (me.button == MouseButton.MIDDLE && me.clickCount == 1) {
				removeFromPlaylist()
			}
		}
		setOnKeyPressed { ke ->
			if (ke.code == KeyCode.DELETE){
				removeFromPlaylist()
			}else if (ke.code == KeyCode.ENTER){
				playFromPlaylist()
			}else if (ke.code == KeyCode.ADD || ke.code == KeyCode.PLUS){
				playNextPlaylist()
			}
		}

		placeholder = Label("Your playlist is empty.")

		contextMenu = ContextMenu(
			MenuItem("Play") { playFromPlaylist() },
			MenuItem("Play Next") { playNextPlaylist() },
			MenuItem("Remove") { removeFromPlaylist() },
			MenuItem("Clear playlist") {
				Playlist.clear()
				Player.reset()
			}
		)
	}

	init {
	    fill(table)
	}
	
	private inline fun useSelectedTrack(action: (Track) -> Unit) {
		action(table.selectionModel.selectedItem)
	}
	private inline fun useSelectedIndex(action: (Int) -> Unit){
		action(table.selectionModel.selectedIndex)
	}
	
}