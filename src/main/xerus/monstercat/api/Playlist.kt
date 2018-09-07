package xerus.monstercat.api

import javafx.collections.FXCollections
import javafx.collections.ObservableList
import xerus.ktutil.javafx.properties.SimpleObservable
import xerus.ktutil.javafx.properties.bindSoft
import xerus.monstercat.logger
import java.util.*

typealias Track = PlaylistTrack

object Playlist {
	val tracks: ObservableList<Track> = FXCollections.observableArrayList()
	val history = ArrayDeque<Track>()
	val currentIndex = SimpleObservable<Int?>(null).apply {
		bindSoft({
			tracks.indexOf(Player.playlistTrack.value).takeUnless { i -> i == -1 }
		}, Player.playlistTrack, tracks)
	}
	
	var repeat = false
	var random = false
	
	operator fun get(index: Int): Track? = tracks.getOrNull(index)
	
	/** This property prevents songs that come from the [history] from being added to it again,
	 * which would effectively create an infinite loop */
	var lastPolled: PlaylistTrack? = null
	
	fun getPrev(): Track? = history.pollLast()?.also {
		lastPolled = it
		logger.finer("Polled $it from History")
	} ?: currentIndex.value?.let { get(it - 1) }
	
	fun getNext() = if (random) nextSongRandom() else nextSong()
	
	fun addNext(track: Track) = tracks.add(currentIndex.value?.let { it + 1 } ?: 0, track)
	
	fun removeAt(index: Int?) {
		if (index != null) tracks.removeAt(index)
		else tracks.removeAt(tracks.size - 1)
	}
	
	fun setTracks(playlist: Collection<Track>) {
		history.clear()
		tracks.setAll(playlist)
	}
	
	fun nextSongRandom(): Track = tracks[(Math.random() * tracks.size).toInt()]
	fun nextSong(): Track? {
		val cur = currentIndex.value
		return when {
			cur == null -> tracks.firstOrNull()
			cur + 1 < tracks.size -> tracks[cur + 1]
			repeat -> tracks.firstOrNull()
			else -> return null
		}
	}
}