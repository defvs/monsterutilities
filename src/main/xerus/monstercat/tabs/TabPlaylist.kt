package xerus.monstercat.tabs

import javafx.scene.control.ContextMenu
import javafx.scene.control.Label
import javafx.scene.control.SelectionMode
import javafx.scene.control.TableRow
import javafx.scene.control.TableView
import javafx.scene.input.KeyCode
import javafx.scene.input.MouseButton
import xerus.ktutil.javafx.MenuItem
import xerus.ktutil.javafx.TableColumn
import xerus.ktutil.javafx.fill
import xerus.ktutil.javafx.properties.listen
import xerus.monstercat.api.Player
import xerus.monstercat.api.Playlist
import xerus.monstercat.api.response.Track


class TabPlaylist: VTab() {
	private var table = TableView<Track>().apply {
		// Inherent properties
		columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY
		selectionModel.selectionMode = SelectionMode.SINGLE
		placeholder = Label("Your playlist is empty.")
		
		// Events
		fun removeFromPlaylist() = Playlist.removeAt(selectedIndex)
		
		fun playFromPlaylist() = Player.playFromPlaylist(selectedIndex)
		fun playNextPlaylist() = Playlist.addNext(selectedTrack)
		
		setOnMouseClicked { me ->
			when {
				me.button == MouseButton.PRIMARY && me.clickCount == 2 -> playFromPlaylist()
				me.button == MouseButton.MIDDLE && me.clickCount == 1 -> removeFromPlaylist()
			}
		}
		setOnKeyPressed { ke ->
			when(ke.code) {
				KeyCode.DELETE -> removeFromPlaylist()
				KeyCode.ENTER -> playFromPlaylist()
				KeyCode.ADD, KeyCode.PLUS -> playNextPlaylist()
				else -> return@setOnKeyPressed
			}
		}
		contextMenu = ContextMenu(
			MenuItem("Play") { playFromPlaylist() },
			MenuItem("Play Next") { playNextPlaylist() },
			MenuItem("Remove") { removeFromPlaylist() },
			MenuItem("Clear playlist") {
				Playlist.clear()
				Player.reset()
			}
		)
		
		// Columns and rows
		columns.addAll(TableColumn<Track, String>("Artists") { it.value.artistsTitle },
			TableColumn<Track, String>("Title") { it.value.title })
		
		setRowFactory {
			TableRow<Track>().apply {
				Playlist.currentIndex.listen {
					style = "-fx-background-color: ${if(index == it) "#1f6601" else "transparent"}"
				}
				itemProperty().listen {
					style = "-fx-background-color: ${if(index == Playlist.currentIndex.value) "#1f6601" else "transparent"}"
				}
			}
		}
	}
	
	init {
		table.items = Playlist.tracks
		fill(table)
	}
	
	private val selectedTrack: Track
		get() = table.selectionModel.selectedItem
	private val selectedIndex: Int
		get() = table.selectionModel.selectedIndex
	
}