package xerus.monstercat.tabs

import javafx.beans.property.SimpleStringProperty
import javafx.beans.value.ObservableValue
import javafx.scene.control.Label
import javafx.scene.control.SelectionMode
import javafx.scene.control.TableColumn
import javafx.scene.control.TableView
import javafx.scene.input.MouseButton
import javafx.util.Callback
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
			if (me.button == MouseButton.MIDDLE && me.clickCount == 1) {
				val selected = table.selectionModel.selectedIndex
				Playlist.removeTrack(selected)
			}
		}
		
		table.placeholder = Label("Nothing's in your playlist. Middle Click any song in the catalog to add it here !")

		fill(table)
	}
}
