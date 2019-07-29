package xerus.monstercat.api

import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.scene.control.*
import javafx.scene.input.MouseButton
import javafx.scene.layout.VBox
import javafx.scene.media.MediaPlayer
import javafx.stage.Modality
import javafx.util.Callback
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import mu.KotlinLogging
import xerus.ktutil.javafx.*
import xerus.ktutil.javafx.properties.ImmutableObservable
import xerus.ktutil.javafx.properties.SimpleObservable
import xerus.ktutil.javafx.properties.bindSoft
import xerus.ktutil.javafx.ui.App
import xerus.monstercat.api.response.ConnectPlaylist
import xerus.monstercat.api.response.Track
import xerus.monstercat.monsterUtilities
import java.util.*

object Playlist {
	val logger = KotlinLogging.logger { }
	
	val tracks: ObservableList<Track> = FXCollections.observableArrayList()
	val history = ArrayDeque<Track>()
	val currentIndex = SimpleObservable<Int?>(null).apply {
		bindSoft({
			tracks.indexOf(Player.activeTrack.value).takeUnless { i -> i == -1 }
		}, Player.activeTrack, tracks)
	}
	
	var repeat = false
	var shuffle = false
	
	operator fun get(index: Int): Track? = tracks.getOrNull(index)
	
	/** This property prevents songs that come from the [history] from being added to it again,
	 * which would effectively create an infinite loop */
	var lastPolled: Track? = null
	
	fun getPrev(): Track? = history.pollLast()?.also {
		lastPolled = it
		logger.debug("Polled $it from History")
	} ?: currentIndex.value?.let { get(it - 1) }
	
	fun getNext() = when {
		shuffle -> nextSongRandom()
		repeat && (nextSong() == null) -> tracks[0]
		else -> nextSong()
	}
	
	fun addNext(track: Track) = tracks.apply {
		remove(track)
		add(currentIndex.value?.let { it + 1 } ?: 0, track)
	}
	
	fun add(track: Track): Boolean = tracks.run {
		remove(track)
		return add(track)
	}
	
	fun addAll(tracks: ArrayList<Track>) = this.tracks.addAll(tracks)
	
	fun removeAt(index: Int?) {
		if (index != null) tracks.removeAt(index)
		else tracks.removeAt(tracks.size - 1)
	}
	
	fun clear(){
		history.clear()
		tracks.clear()
	}
	
	fun setTracks(playlist: Collection<Track>) {
		history.clear()
		tracks.setAll(playlist)
	}
	
	fun nextSongRandom(): Track {
		val index = (Math.random() * tracks.size).toInt()
		return if(index == currentIndex.value && tracks.size > 1) nextSongRandom() else tracks[index]
	}
	fun nextSong(): Track? {
		val cur = currentIndex.value
		return when {
			cur == null -> tracks.firstOrNull()
			cur + 1 < tracks.size -> tracks[cur + 1]
			repeat -> tracks.firstOrNull()
			else -> return null
		}
	}
	
	fun addAll(tracks: ArrayList<Track>, asNext: Boolean = false) {
		if (asNext) tracks.reverse()
		tracks.forEach { track ->
			if (asNext) addNext(track)
			else add(track)
		}
	}
}

object PlaylistManager {
	suspend fun loadPlaylist(apiConnection: APIConnection){
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
			if (Player.player?.status != MediaPlayer.Status.DISPOSED)
				Player.reset()
			Playlist.setTracks(tracks)
		}
	}

	/** Opens the playlist manager dialog
	 * Allows to load, save, and manage playlists stored on Monstercat.com
	 */
	fun playlistDialog(){
		val connection = APIConnection("api", "playlist").fields(ConnectPlaylist::class)

		val parent = VBox()
		val stage = App.stage.createStage("Monstercat.com Playlists", parent)
		stage.initModality(Modality.WINDOW_MODAL)
		val connectTable = TableView<ConnectPlaylist>()

		// Common playlist functions
		fun load(runAfter: () -> Unit = {}) {
			if (connectTable.selectionModel.selectedItem != null) {
				GlobalScope.async {
					loadPlaylist(APIConnection("api", "playlist", connectTable.selectionModel.selectedItem.id, "tracks"))
					onFx { runAfter.invoke() }
				}
			}
		}
		fun loadUrl(runAfter: () -> Unit = {}) {
			val subParent = VBox()
			val subStage = stage.createStage("Load from URL", subParent)
			subStage.initModality(Modality.WINDOW_MODAL)
			val textField = TextField().apply { promptText = "URL" }
			subParent.add(textField)
			subParent.addRow(createButton("Load"){
				val playlistId = textField.text.substringAfterLast("/")
				if (playlistId.length == 24){
					GlobalScope.async {
						try {
							loadPlaylist(APIConnection("api", "playlist", playlistId, "tracks"))
							onFx { subStage.close(); runAfter.invoke() }
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
		fun replace(runAfter: () -> Unit = {}) {
			if (connectTable.selectionModel.selectedItem != null) {
				GlobalScope.async {
					APIConnection.editPlaylist(connectTable.selectionModel.selectedItem.id, tracks = Playlist.tracks)
					onFx { runAfter.invoke() }
				}
			}
		}
		fun delete(runAfter: () -> Unit = {}) {
			if (connectTable.selectionModel.selectedItem != null) {
				GlobalScope.async {
					APIConnection.editPlaylist(connectTable.selectionModel.selectedItem.id, deleted = true)
					onFx { runAfter.invoke() }
				}
			}
		}
		fun new(runAfter: () -> Unit = {}) {
			val subParent = VBox()
			val subStage = stage.createStage("New Playlist", subParent)
			subStage.initModality(Modality.WINDOW_MODAL)
			val textField = TextField().apply { promptText = "Name" }
			val publicTick = CheckBox("Public")
			subParent.children.addAll(textField, publicTick)
			subParent.addRow(createButton("Create"){
				GlobalScope.async {
					APIConnection.createPlaylist(textField.text.let { if (it.isBlank()) "New Playlist" else it }, Playlist.tracks, publicTick.isSelected)
					onFx { subStage.close(); runAfter.invoke() }
				}
			}, createButton("Cancel"){
				subStage.close()
			})
			subStage.show()
		}
		fun rename(runAfter: () -> Unit = {}) {
			val subParent = VBox()
			val subStage = stage.createStage("Rename Playlist", subParent)
			subStage.initModality(Modality.WINDOW_MODAL)
			val textField = TextField().apply { promptText = "Name" }
			subParent.add(textField)
			subParent.addRow(createButton("Rename"){
				if (connectTable.selectionModel.selectedItem != null) {
					GlobalScope.async {
						APIConnection.editPlaylist(connectTable.selectionModel.selectedItem.id, name = textField.text.let { if (it.isBlank()) "Unnamed" else it })
						onFx { subStage.close(); runAfter.invoke() }
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
			columns.addAll(TableColumn<ConnectPlaylist, String>("Name") { it.value.name },
					TableColumn<ConnectPlaylist, String>("Size") { it.value.tracks.size.toString() })

			items = playlists

			selectionModel.selectionMode = SelectionMode.SINGLE

			setOnMouseClicked { if (it.button == MouseButton.PRIMARY && it.clickCount == 2) load() }

			val publicMenuItem = CheckMenuItem("Public", {
				if (connectTable.selectionModel.selectedItem != null) {
					GlobalScope.async {
						APIConnection.editPlaylist(connectTable.selectionModel.selectedItem.id, public = it)
						onFx { updatePlaylists() }
					}
				}
			})
			contextMenu = ContextMenu(publicMenuItem, SeparatorMenuItem(), MenuItem("Save into") { replace { updatePlaylists() }; }, MenuItem("Rename playlist") { rename { updatePlaylists() } }, MenuItem("Delete playlist") { delete { updatePlaylists() } })
			contextMenu.setOnShown { publicMenuItem.isSelected = selectionModel.selectedItem?.public ?: false }

			updatePlaylists()
		}

		parent.add(Label("Tip : You can right-click a playlist to edit it without the window closing each time !"))
		parent.addRow(createButton("Load"){ load(); stage.close() }, createButton("From URL..."){ loadUrl { stage.close() } }, createButton("Save into selected"){ replace { stage.close() } }, createButton("Save as new..."){ new { stage.close() } })
		parent.fill(connectTable, 0)
		stage.show()
	}
}