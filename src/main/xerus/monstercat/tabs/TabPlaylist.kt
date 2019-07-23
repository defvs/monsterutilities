package xerus.monstercat.tabs

import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.scene.control.*
import javafx.scene.input.KeyCode
import javafx.scene.input.MouseButton
import javafx.scene.layout.VBox
import javafx.stage.Modality
import javafx.util.Callback
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import xerus.ktutil.javafx.*
import xerus.ktutil.javafx.properties.ImmutableObservable
import xerus.ktutil.javafx.properties.listen
import xerus.ktutil.javafx.ui.App
import xerus.monstercat.api.*
import xerus.monstercat.api.response.ConnectPlaylist
import xerus.monstercat.api.response.Track
import xerus.monstercat.monsterUtilities


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

		fun removeFromPlaylist() = Playlist.removeAt(selectedIndex)
		fun playFromPlaylist() = Player.playFromPlaylist(selectedIndex)
		fun playNextPlaylist() = Playlist.addNext(selectedTrack)

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
		addButton("Playlists from Monstercat.com..."){ PlaylistManager.playlistDialog() }
		fill(table)
	}

	private val selectedTrack: Track
		get() = table.selectionModel.selectedItem
	private val selectedIndex: Int
		get() = table.selectionModel.selectedIndex
	
}