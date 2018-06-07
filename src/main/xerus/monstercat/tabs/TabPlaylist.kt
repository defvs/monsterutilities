package xerus.monstercat.tabs

import javafx.beans.property.SimpleStringProperty
import javafx.beans.value.ObservableValue
import javafx.scene.control.SelectionMode
import javafx.scene.control.TableColumn
import javafx.scene.control.TableView
import javafx.scene.input.MouseButton
import javafx.util.Callback
import xerus.ktutil.javafx.fill
import xerus.monstercat.api.Player
import xerus.monstercat.api.Playlist
import xerus.monstercat.api.Song


class TabPlaylist : VTab() {
	var table = TableView<Song>()

	init {
		prefWidth = 600.0

		table.items = Playlist.playlist

		val artistsCol = TableColumn<Song, String>("Artists")
		artistsCol.cellValueFactory = Callback<TableColumn.CellDataFeatures<Song, String>, ObservableValue<String>> { p ->
			SimpleStringProperty(p.value.artists)
		}
		artistsCol.prefWidthProperty().bind(widthProperty().divide(2))

		val titleCol = TableColumn<Song, String>("Title")
		titleCol.cellValueFactory = Callback<TableColumn.CellDataFeatures<Song, String>, ObservableValue<String>> { p ->
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
					Player.play(t.title, t.artists)
				}
			}
			if (me.button == MouseButton.MIDDLE && me.clickCount == 1) {
				val selected = table.selectionModel.selectedIndex
				Playlist.removeTrack(selected)
			}
		}

		fill(table)
	}
}
