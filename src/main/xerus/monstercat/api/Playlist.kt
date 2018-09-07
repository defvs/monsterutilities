package xerus.monstercat.api

import javafx.collections.FXCollections
import javafx.collections.ObservableList
import xerus.ktutil.javafx.properties.dependentObservable
import xerus.monstercat.api.response.Track
import java.util.*

object Playlist {
	val tracks: ObservableList<Track> = FXCollections.observableArrayList()
	val history = ArrayDeque<Track>()
	val currentTrack = Player.activeTrack.dependentObservable {
		tracks.indexOf(it).takeUnless { i -> i == -1 }
	}
	
	var repeat = false
	var random = false
	
	operator fun get(index: Int): Track = tracks[index]
	
	fun getPrev(): Track? = history.poll()
	
	fun next() = if (random) nextSongRandom() else nextSong()
	
	fun addNext(track: Track) = tracks.add(currentTrack.value?.let { it + 1 } ?: 0, track)
	
	fun removeTrack(index: Int?) {
		if (index != null) tracks.removeAt(index)
		else tracks.removeAt(tracks.size - 1)
	}
	
	fun setTracks(playlist: Collection<Track>) {
		history.clear()
		tracks.setAll(playlist)
	}
	
	fun nextSongRandom(): Track = tracks[(Math.random() * tracks.size).toInt()]
	fun nextSong(): Track? {
		val cur = currentTrack.value
		return when {
			cur == null -> tracks.firstOrNull()
			cur + 1 < tracks.size -> tracks[cur + 1]
			repeat -> tracks.firstOrNull()
			else -> return null
		}
	}
}