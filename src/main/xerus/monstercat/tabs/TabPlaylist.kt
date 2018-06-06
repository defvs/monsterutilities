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


class TabPlaylist : VTab(){
	var table = TablePlaylist()
	init {
		prefWidth = 600.0

		fill(table)
	}
}
class TablePlaylist : TableView<Song>() {
	init {
		items = Playlist.playlist

		val artistsCol = TableColumn<Song,String>("Artists")
		artistsCol.cellValueFactory = Callback<TableColumn.CellDataFeatures<Song, String>, ObservableValue<String>> {
			p -> SimpleStringProperty(p.value.artists)
		}
		artistsCol.prefWidthProperty().bind(widthProperty().divide(2))

		val titleCol = TableColumn<Song,String>("Title")
		titleCol.cellValueFactory = Callback<TableColumn.CellDataFeatures<Song, String>, ObservableValue<String>> {
			p -> SimpleStringProperty(p.value.title)
		}
		titleCol.prefWidthProperty().bind(widthProperty().divide(2))

		columns.addAll(artistsCol, titleCol)

		selectionModel.selectionMode = SelectionMode.SINGLE

		setOnMouseClicked { me ->
			if (me.button == MouseButton.PRIMARY && me.clickCount == 2){
				val selected = selectionModel.selectedIndex
				val t = Playlist.select(selected)
				if (t != null) {
					Player.play(t.title,t.artists)
				}
			}
			if (me.button == MouseButton.MIDDLE && me.clickCount == 1){
				val selected = selectionModel.selectedIndex
				Playlist.removeTrack(selected)
			}
		}
	}
}
