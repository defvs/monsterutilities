package xerus.monstercat.api

import javafx.collections.FXCollections
import javafx.collections.ObservableList

object Playlist {
	var playlist: ObservableList<Song> = FXCollections.observableArrayList()
	var currentTrack = 0
	var repeat = false
	var random: Boolean = false


	fun next(): Song? {
		when {
			currentTrack + 1 < playlist.size -> currentTrack++
			repeat -> currentTrack = 0
			else -> return null
		}
		return playlist[currentTrack]
	}

	fun prev(): Song? {
		if (currentTrack > 0) currentTrack--
		return playlist[currentTrack]
	}

	fun select(index: Int): Song? {
		if (index >= 0 && index < playlist.size) currentTrack = index
		return playlist[currentTrack]
	}

	fun getTracks() = playlist
	fun setTracks(playlist: MutableList<Song>) = this.playlist.addAll(playlist)

	operator fun invoke() = getTracks()
	operator fun invoke(playlist: MutableList<Song>) = setTracks(playlist)

	fun addTrack(track: Song) = playlist.add(track)
	operator fun invoke(track: Song) = addTrack(track)
	operator fun invoke(vararg tracks: Song) = playlist.addAll(tracks)

	fun removeTrack(index: Int?) {
		if (index != null) playlist.removeAt(index)
		else playlist.removeAt(playlist.size - 1)
		if (playlist.size < currentTrack + 1) currentTrack = playlist.size - 1
	}

	fun clearTracks() {
		playlist.clear()
		currentTrack = 0
	}

	fun nextRandom(): Song? = select((Math.random() * (playlist.size)).toInt())
}

class Song(title: String, artists: String) {

	var title = ""
	var artists = ""

	init {
		this.title = title
		this.artists = artists
	}
}