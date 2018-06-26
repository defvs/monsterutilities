package xerus.monstercat.api

import javafx.collections.FXCollections
import javafx.collections.ObservableList
import xerus.monstercat.api.response.Track

object Playlist {
	var playlist: ObservableList<Track> = FXCollections.observableArrayList()
	var currentTrack = 0
	var repeat = false
	var random: Boolean = false


	fun next(): Track? {
		when {
			currentTrack + 1 < playlist.size -> currentTrack++
			repeat -> currentTrack = 0
			else -> return null
		}
		return playlist[currentTrack]
	}

	fun prev(): Track? {
		if (currentTrack > 0) currentTrack--
		return playlist[currentTrack]
	}

	fun select(index: Int): Track? {
		if (index >= 0 && index < playlist.size) currentTrack = index
		return playlist[currentTrack]
	}

	fun getTracks() = playlist
	fun setTracks(playlist: MutableList<Track>) = this.playlist.addAll(playlist)

	fun addTrack(track: Track) = playlist.add(track)
	operator fun invoke(track: Track) = addTrack(track)
	operator fun invoke(vararg tracks: Track) = playlist.addAll(tracks)

	fun removeTrack(index: Int?) {
		if (index != null) playlist.removeAt(index)
		else playlist.removeAt(playlist.size - 1)
		if (playlist.size < currentTrack + 1) currentTrack = playlist.size - 1
	}

	fun clearTracks() {
		playlist.clear()
		currentTrack = 0
	}

	fun nextRandom(): Track? = select((Math.random() * (playlist.size)).toInt())
}