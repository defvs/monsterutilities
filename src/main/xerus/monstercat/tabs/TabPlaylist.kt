package xerus.monstercat.tabs

import javafx.beans.property.SimpleStringProperty
import javafx.beans.value.ObservableValue
import javafx.scene.control.*
import javafx.scene.input.MouseButton
import javafx.util.Callback
import xerus.ktutil.javafx.MenuItem
import xerus.ktutil.javafx.fill
import xerus.monstercat.api.Player
import xerus.monstercat.api.Playlist
import xerus.monstercat.api.response.Track


class TabPlaylist : VTab() {
	var table = TableView<Track>()

	init {
		prefWidth = 600.0

		table.items = Playlist.playlist

		val artistsCol = TableColumn<Track, String>("Artists")
		artistsCol.cellValueFactory = Callback<TableColumn.CellDataFeatures<Track, String>, ObservableValue<String>> { p ->
			SimpleStringProperty(p.value.artistsTitle)
		}
		artistsCol.prefWidthProperty().bind(widthProperty().divide(2))

		val titleCol = TableColumn<Track, String>("Title")
		titleCol.cellValueFactory = Callback<TableColumn.CellDataFeatures<Track, String>, ObservableValue<String>> { p ->
			SimpleStringProperty(p.value.title)
		}
		titleCol.prefWidthProperty().bind(widthProperty().divide(2))

		table.columns.addAll(artistsCol, titleCol)

		table.selectionModel.selectionMode = SelectionMode.SINGLE

		table.setOnMouseClicked { me ->
			if (me.button == MouseButton.PRIMARY && me.clickCount == 2) {
				val selected = table.selectionModel.selectedIndex
				val t = Playlist.select(selected)
				if (t != null) {
					Player.play(t.title, t.artistsTitle)
				}
			}
		}
		
		table.placeholder = Label("Nothing's in your playlist. Right Click any song in the catalog to add it here !")
		
		val rightClickMenu = ContextMenu()
		val item1 = MenuItem("Play") {
			val selected = table.selectionModel.selectedIndex
			val t = Playlist.select(selected)
			if (t != null) {
				Player.play(t.title, t.artistsTitle)
			}
		}
		val item2 = MenuItem("Play Next") {
			val selected = table.selectionModel.selectedIndex
			val t = Playlist.playlist[selected]
			if (t != null) {
				Playlist.addNext(t)
			}
		}
		val item3 = MenuItem("Remove") {
			val selected = table.selectionModel.selectedIndex
			Playlist.removeTrack(selected)
		}
		rightClickMenu.items.addAll(item1, item2, item3)
		table.contextMenu = rightClickMenu

		fill(table)
	}
}
