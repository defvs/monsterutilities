package xerus.monstercat.api

import javafx.collections.FXCollections
import javafx.collections.ObservableList
import xerus.monstercat.api.response.Track

object Playlist {
	var tracks: ObservableList<Track> = FXCollections.observableArrayList()
	
	var currentTrack = 0
	var repeat = false
	var random = false
	
	fun select(index: Int): Track {
		if (index >= 0 && index < tracks.size) currentTrack = index
		return tracks[currentTrack]
	}
	
	fun prev(): Track {
		if (currentTrack > 0) currentTrack--
		return tracks[currentTrack]
	}
	
	fun next() = if (random) nextSongRandom() else nextSong()
	
	fun addNext(track: Track) = tracks.add(currentTrack + 1, track)
	
	fun removeTrack(index: Int?) {
		if (index != null) tracks.removeAt(index)
		else tracks.removeAt(tracks.size - 1)
		if (tracks.size < currentTrack + 1) currentTrack = tracks.size - 1
	}
	
	fun setTracks(playlist: Collection<Track>) {
		tracks.setAll(playlist)
		currentTrack = 0
	}
	
	fun clearTracks() {
		tracks.clear()
		currentTrack = 0
	}
	
	fun nextSongRandom() = select((Math.random() * (tracks.size)).toInt())
	fun nextSong(): Track? {
		when {
			currentTrack + 1 < tracks.size -> currentTrack++
			repeat -> currentTrack = 0
			else -> return null
		}
		return tracks[currentTrack]
	}
}