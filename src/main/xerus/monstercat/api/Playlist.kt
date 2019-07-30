package xerus.monstercat.api

import javafx.beans.property.SimpleBooleanProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import mu.KotlinLogging
import xerus.ktutil.javafx.properties.SimpleObservable
import xerus.ktutil.javafx.properties.bindSoft
import xerus.monstercat.api.response.Track
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

	val repeat = SimpleBooleanProperty(false)
	val shuffle = SimpleBooleanProperty(false)
	
	operator fun get(index: Int): Track? = tracks.getOrNull(index)
	
	/** This property prevents songs that come from the [history] from being added to it again,
	 * which would effectively create an infinite loop */
	var lastPolled: Track? = null
	
	fun getPrev(): Track? = history.pollLast()?.also {
		lastPolled = it
		logger.debug("Polled $it from History")
	} ?: currentIndex.value?.let { get(it - 1) }
	
	fun getNext() = when {
		shuffle.value -> getNextTrackRandom()
		repeat.value && (getNextTrack() == null) -> tracks[0]
		else -> getNextTrack()
	}
	
	fun addNext(track: Track) {
		tracks.remove(track)
		tracks.add(currentIndex.value?.let { it + 1 } ?: 0, track)
	}
	
	fun add(track: Track) {
		tracks.remove(track)
		tracks.add(track)
	}
	
	fun removeAt(index: Int?) {
		tracks.removeAt(index ?: tracks.size - 1)
	}
	
	fun clear(){
		history.clear()
		tracks.clear()
	}
	
	fun setTracks(playlist: Collection<Track>) {
		history.clear()
		tracks.setAll(playlist)
	}
	
	fun getNextTrackRandom(): Track {
		val index = (Math.random() * tracks.size).toInt()
		return if(index == currentIndex.value && tracks.size > 1) getNextTrackRandom() else tracks[index]
	}
	fun getNextTrack(): Track? {
		val cur = currentIndex.value
		return when {
			cur == null -> tracks.firstOrNull()
			cur + 1 < tracks.size -> tracks[cur + 1]
			repeat.value -> tracks.firstOrNull()
			else -> return null
		}
	}
	
	fun addAll(tracks: ArrayList<Track>, asNext: Boolean = false) {
		tracks.removeAll(tracks)
		if (asNext)
			tracks.addAll(currentIndex.value?.let { it + 1 } ?: 0, tracks)
		else
			tracks.addAll(tracks)
	}
}