package xerus.monstercat.tabs

import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.scene.control.*
import javafx.scene.input.KeyCode
import javafx.scene.input.MouseButton
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
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
import java.lang.Exception


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
		
		table.setRowFactory {
			TableRow<Track>().apply {
				Playlist.currentIndex.addListener { _, _, newValue ->
					style = if (index == newValue) {
						"-fx-background-color: #1f6601"
					} else {
						"-fx-background-color: transparent"
					}
				}
				itemProperty().listen {
					style = if (index == Playlist.currentIndex.value) {
						"-fx-background-color: #1f6601"
					} else {
						"-fx-background-color: transparent"
					}
				}
			}
		}
		
		table.selectionModel.selectionMode = SelectionMode.SINGLE
		
		table.setOnMouseClicked { me ->
			if (me.button == MouseButton.PRIMARY && me.clickCount == 2) {
				Player.playFromPlaylist(table.selectionModel.selectedIndex)
			}
			if (me.button == MouseButton.MIDDLE && me.clickCount == 1) {
				Playlist.removeAt(table.selectionModel.selectedIndex)
			}
		}
		
		table.setOnKeyPressed { ke ->
			if (ke.code == KeyCode.DELETE){
				Playlist.removeAt(table.selectionModel.selectedIndex)
			}else if (ke.code == KeyCode.ENTER){
				Player.playFromPlaylist(table.selectionModel.selectedIndex)
			}else if (ke.code == KeyCode.ADD || ke.code == KeyCode.PLUS){
				useSelectedTrack { Playlist.addNext(it) }
			}
		}
		
		table.placeholder = Label("Your playlist is empty.")
		
		val rightClickMenu = ContextMenu()
		rightClickMenu.items.addAll(
				MenuItem("Play") {
					Player.playFromPlaylist(table.selectionModel.selectedIndex)
				},
				MenuItem("Play Next") {
					useSelectedTrack { Playlist.addNext(it) }
				},
				MenuItem("Remove") {
					Playlist.removeAt(table.selectionModel.selectedIndex)
				},
				MenuItem("Clear playlist") {
					Playlist.clear()
					Player.reset()
				}
		)
		table.contextMenu = rightClickMenu
		
		val buttons = HBox()
		buttons.add(Label("From Monstercat.com :"))
		buttons.addButton("Open..."){
			openPlaylistDialog()
		}
		buttons.addButton("Save..."){
			savePlaylistDialog(Playlist.tracks)
		}
		
		add(buttons)
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
	
	private fun openPlaylistDialog(){
		val connection = APIConnection("playlist").fields(ConnectPlaylist::class)
		
		val parent = VBox()
		val stage = App.stage.createStage("Open playlist from Monstercat.com", parent)
		
		val connectTable = TableView<ConnectPlaylist>().apply {
			val playlists = FXCollections.observableArrayList<ConnectPlaylist>()
			
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
			
			setOnMouseClicked {
				if (it.button == MouseButton.PRIMARY && it.clickCount == 2)
					if (selectionModel.selectedItem != null) {
						GlobalScope.async {
							val apiConnection = APIConnection("playlist", selectionModel.selectedItem.id, "tracks")
							loadPlaylist(apiConnection)
							onFx { stage.close() }
						}
					}
			}
			
			if (APIConnection.connectValidity.value != ConnectValidity.NOGOLD && APIConnection.connectValidity.value != ConnectValidity.GOLD){
				placeholder = Label("Please connect using connect.sid in the downloader tab.")
				
			}else{
				placeholder = Label("Loading...")
				GlobalScope.async {
					val results = connection.getPlaylists()
					if (results != null && results.isNotEmpty())
						playlists.addAll(results)
					else
						onFx {
							placeholder = Label("No playlists were found on your account.")
						}
				}
			}
		}
		
		val urlField = TextField("").apply {
			promptText = "Enter the playlist's URL"
		}
		
		var fromUrl = false
		
		val buttons = HBox().apply {
			addButton("Load") {
				if (fromUrl){
					val playlistId = urlField.text.substringAfterLast("/")
					if (playlistId.length == 24){
						GlobalScope.async {
							val apiConnection = APIConnection("playlist", playlistId, "tracks")
							try {
								loadPlaylist(apiConnection)
								onFx { stage.close() }
							}catch (e: Exception){
								onFx {
									monsterUtilities.showAlert(Alert.AlertType.WARNING, "No playlist found", content = "No playlist were found at ${urlField.text}.")
								}
							}
						}
					}else{
						monsterUtilities.showAlert(Alert.AlertType.WARNING, "Playlist URL invalid", content = "${urlField.text} is not a valid URL.")
					}
				}else {
					if (connectTable.selectionModel.selectedItem != null) {
						GlobalScope.async {
							val apiConnection = APIConnection("playlist", connectTable.selectionModel.selectedItem.id, "tracks")
							loadPlaylist(apiConnection)
							onFx { stage.close() }
						}
					}
				}
			}
			addButton("From URL..."){}.apply {
				onClick {
					fromUrl = !fromUrl
					
					parent.children.removeAt(0)
					if (fromUrl) {
						text = "From account..."
						parent.children.add(0, urlField)
						parent.fill(pos = 1)
					} else {
						text = "From URL..."
						parent.children.removeAt(0)
						parent.fill(connectTable, 0)
					}
				}
			}
			addButton("Cancel") {
				stage.close()
			}
		}
		
		// parent.add(urlField)
		parent.add(buttons)
		parent.fill(connectTable, 0)
		stage.show()
	}
	
	private fun savePlaylistDialog(playlist: List<Track>){
		val connection = APIConnection("playlist").fields(ConnectPlaylist::class)
		
		val parent = VBox()
		val stage = App.stage.createStage("Save as...", parent)
		
		var existing = false
		
		val nameField = TextField("").apply { promptText = "Enter the playlist's name" }
		
		
		val connectTable = TableView<ConnectPlaylist>().apply {
			val playlists = FXCollections.observableArrayList<ConnectPlaylist>()
			
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
			
			if (APIConnection.connectValidity.value != ConnectValidity.NOGOLD && APIConnection.connectValidity.value != ConnectValidity.GOLD){
				placeholder = Label("Please connect using connect.sid in the downloader tab.")
				
			}else{
				placeholder = Label("Loading...")
				GlobalScope.async {
					val results = connection.getPlaylists()
					if (results != null && results.isNotEmpty())
						playlists.addAll(results)
					else
						onFx {
							placeholder = Label("No playlists were found on your account.")
						}
				}
			}
		}
		
		val buttons = HBox().apply {
			addButton("Already existing playlist..."){}.apply{
				onClick {
					existing = !existing
					
					parent.children.removeAt(1)
					if (existing){
						text = "New playlist..."
						parent.fill(connectTable, 1)
					}else{
						text = "Already existing playlist..."
						parent.children.add(1, nameField)
					}
					stage.sizeToScene()
				}
			}
			addButton("Save") {
				if (existing){
					val saveConnection = APIConnection("playlist", connectTable.selectionModel.selectedItem.id)
					saveConnection.editPlaylist(playlist)
				}else{
					val saveConnection = APIConnection("playlist")
					saveConnection.createPlaylist(if (nameField.text.isNullOrEmpty()) "Unnamed playlist" else nameField.text, playlist)
				}
				stage.close()
			}
			addButton("Cancel") {
				stage.close()
			}
		}
		parent.add(Label("Total : ${playlist.size} tracks"))
		parent.add(nameField)
		parent.add(buttons)
		stage.show()
	}
	
	inline fun useSelectedTrack(action: (Track) -> Unit) {
		action(table.selectionModel.selectedItem)
	}
	
}