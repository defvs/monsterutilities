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
		addButton("Playlists from Monstercat.com..."){ playlistDialog() }
		fill(table)
	}
	
	private suspend fun loadPlaylist(apiConnection: APIConnection){
		val tracks = apiConnection.getTracks()
		tracks?.forEachIndexed { index, track ->
			val found = Cache.getTracks().find {
				it.id == track.id
			}
			if (found != null)
				tracks[index] = found
			else
				tracks.removeAt(index)
		}
		if (tracks != null && tracks.isNotEmpty()) {
			Player.reset()
			Playlist.setTracks(tracks)
		}
	}

	private fun playlistDialog(){
		val connection = APIConnection("api", "playlist").fields(ConnectPlaylist::class)

		val parent = VBox()
		val stage = App.stage.createStage("Monstercat.com Playlists", parent)
		stage.initModality(Modality.WINDOW_MODAL)
		val connectTable = TableView<ConnectPlaylist>()

		// Common playlist functions
		fun load() {
			if (connectTable.selectionModel.selectedItem != null) {
				GlobalScope.async {
					loadPlaylist(APIConnection("api", "playlist", connectTable.selectionModel.selectedItem.id, "tracks"))
				}
			}
		}
		fun loadUrl() {
			val subParent = VBox()
			val subStage = App.stage.createStage("Load from URL", subParent)
			subStage.initModality(Modality.WINDOW_MODAL)
			val textField = TextField().apply { promptText = "URL" }
			subParent.add(textField)
			subParent.addRow(createButton("Load"){
				val playlistId = textField.text.substringAfterLast("/")
				if (playlistId.length == 24){
					GlobalScope.async {
						try {
							loadPlaylist(APIConnection("api", "playlist", playlistId, "tracks"))
							onFx { subStage.close() }
						}catch (e: Exception){
							onFx {
								monsterUtilities.showAlert(Alert.AlertType.WARNING, "No playlist found", content = "No playlist were found at ${textField.text}.")
							}
						}
					}
				}else{
					monsterUtilities.showAlert(Alert.AlertType.WARNING, "Playlist URL invalid", content = "${textField.text} is not a valid URL.")
				}
			}, createButton("Cancel"){
				subStage.close()
			})
			subStage.show()
		}
		fun replace() {
			if (connectTable.selectionModel.selectedItem != null) {
				GlobalScope.async {
					APIConnection("v2", "playlist", connectTable.selectionModel.selectedItem.id).editPlaylist(Playlist.tracks)
				}
			}
		}
		fun delete() {
			if (connectTable.selectionModel.selectedItem != null) {
				GlobalScope.async {
					APIConnection("v2", "playlist", connectTable.selectionModel.selectedItem.id).editPlaylist(deleted = true)
				}
			}
		}
		fun new() {
			val subParent = VBox()
			val subStage = App.stage.createStage("New Playlist", subParent)
			subStage.initModality(Modality.WINDOW_MODAL)
			val textField = TextField().apply { promptText = "Name" }
			val publicTick = CheckBox("Public")
			subParent.children.addAll(textField, publicTick)
			subParent.addRow(createButton("Create"){
				GlobalScope.async {
					APIConnection("api", "playlist").createPlaylist(textField.text.let { if (it.isBlank()) "New Playlist" else it }, Playlist.tracks, publicTick.isSelected)
					onFx { subStage.close() }
				}
			}, createButton("Cancel"){
				subStage.close()
			})
			subStage.show()
		}
		fun rename() {
			val subParent = VBox()
			val subStage = App.stage.createStage("New Playlist", subParent)
			subStage.initModality(Modality.WINDOW_MODAL)
			val textField = TextField().apply { promptText = "Name" }
			subParent.add(textField)
			subParent.addRow(createButton("Create"){
				if (connectTable.selectionModel.selectedItem != null) {
					GlobalScope.async {
						APIConnection("v2", "playlist", connectTable.selectionModel.selectedItem.id).editPlaylist(name = textField.text.let { if (it.isBlank()) "Unnamed" else it })
						onFx { subStage.close(); }
					}
				}
			}, createButton("Cancel"){
				subStage.close()
			})
			subStage.show()
		}

		val playlists = FXCollections.observableArrayList<ConnectPlaylist>()
		fun updatePlaylists() {
			playlists.clear()
			if (APIConnection.connectValidity.value != ConnectValidity.NOGOLD && APIConnection.connectValidity.value != ConnectValidity.GOLD){
				connectTable.placeholder = Label("Please connect using connect.sid in the downloader tab.")
			}else{
				connectTable.placeholder = Label("Loading...")
				GlobalScope.async {
					val results = connection.getPlaylists()
					if (results != null && results.isNotEmpty())
						playlists.addAll(results)
					else
						onFx {
							connectTable.placeholder = Label("No playlists were found on your account.")
						}
				}
			}
		}

		connectTable.apply {

			columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY
			columns.addAll(TableColumn<ConnectPlaylist, String>("Name").apply {
				cellValueFactory = Callback<TableColumn.CellDataFeatures<ConnectPlaylist, String>, ObservableValue<String>> { p ->
					ImmutableObservable(p.value.name)
				}
			}, TableColumn<ConnectPlaylist, String>("Size").apply {
				cellValueFactory = Callback<TableColumn.CellDataFeatures<ConnectPlaylist, String>, ObservableValue<String>> { p ->
					ImmutableObservable(p.value.tracks.size.toString())
				}
			})

			items = playlists

			selectionModel.selectionMode = SelectionMode.SINGLE

			setOnMouseClicked { if (it.button == MouseButton.PRIMARY && it.clickCount == 2) load() }

			val publicMenuItem = CheckMenuItem("Public", {
				if (connectTable.selectionModel.selectedItem != null) {
					GlobalScope.async {
						APIConnection("api", "playlist", connectTable.selectionModel.selectedItem.id).editPlaylist(public = it)
						onFx { updatePlaylists() }
					}
				}
			})
			contextMenu = ContextMenu(publicMenuItem, SeparatorMenuItem(), MenuItem("Save into") { replace(); updatePlaylists() }, MenuItem("Rename playlist") { rename(); updatePlaylists() }, MenuItem("Delete playlist") { delete(); updatePlaylists() })
			contextMenu.setOnShown { publicMenuItem.isSelected = selectionModel.selectedItem?.public ?: false }

			updatePlaylists()
		}

		parent.add(Label("Tip : You can right-click a playlist to edit it without the window closing each time !"))
		parent.addRow(createButton("Load"){ load(); stage.close() }, createButton("From URL..."){ loadUrl(); stage.close() }, createButton("Save into selected"){ replace(); stage.close() }, createButton("Save as new..."){ new(); stage.close() })
		parent.fill(connectTable, 0)
		stage.show()
	}

	private val selectedTrack: Track
		get() = table.selectionModel.selectedItem
	private val selectedIndex: Int
		get() = table.selectionModel.selectedIndex
	
}