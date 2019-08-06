package xerus.monstercat.api

import javafx.beans.property.SimpleBooleanProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.scene.control.Alert
import mu.KotlinLogging
import xerus.ktutil.javafx.onFx
import xerus.ktutil.javafx.properties.SimpleObservable
import xerus.ktutil.javafx.properties.bindSoft
import xerus.monstercat.Settings.SKIPUNLICENSABLE
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
	
	private fun showUnsafeAlert(track: Track? = null) {
		onFx {
			monsterUtilities.showAlert(Alert.AlertType.WARNING, "Playlist", "Unlicensable tracks !",
				"Skipped adding ${track ?: "tracks"} according to your settings.")
		}
	}
	
	fun addNext(track: Track) {
		tracks.remove(track)
		if(track.licensable && SKIPUNLICENSABLE())
			tracks.add(currentIndex.value?.let { it + 1 } ?: 0, track)
		else if(SKIPUNLICENSABLE()) showUnsafeAlert(track)
	}
	
	fun add(track: Track) {
		tracks.remove(track)
		if(track.licensable && SKIPUNLICENSABLE())
			tracks.add(track)
		else if(SKIPUNLICENSABLE()) showUnsafeAlert(track)
	}
	
	fun removeAt(index: Int?) {
		tracks.removeAt(index ?: tracks.size - 1)
	}
	
	fun clear() {
		history.clear()
		tracks.clear()
	}
	
	fun setTracks(playlist: Collection<Track>) {
		history.clear()
		val checkedTracks = if(SKIPUNLICENSABLE()) removeUnsafe(playlist) else playlist
		if(checkedTracks.isEmpty()) showUnsafeAlert()
		tracks.setAll(checkedTracks)
	}
	
	private fun removeUnsafe(tracks: Collection<Track>) = tracks.filter { it.licensable && it.artistsTitle != "" && it.artistsTitle != "Monstercat" }
	
	fun getNextTrackRandom(): Track {
		val index = (Math.random() * (tracks.size - 1)).toInt().let { if(it >= currentIndex.value!!) it + 1 else it }.takeUnless { it >= tracks.size }
			?: 0
		return tracks[index]
	}
	
	fun getNextTrack(): Track? {
		val cur = currentIndex.value
		return when {
			cur == null -> tracks.firstOrNull()
			cur < tracks.lastIndex -> tracks[cur + 1]
			repeat.value -> tracks.firstOrNull()
			else -> return null
		}
	}
	
	fun addAll(tracks: ArrayList<Track>, asNext: Boolean = false) {
		this.tracks.removeAll(tracks)
		val checkedTracks = if(SKIPUNLICENSABLE()) removeUnsafe(tracks) else tracks
		if(checkedTracks.isEmpty()) showUnsafeAlert()
		this.tracks.addAll(if(asNext) currentIndex.value?.let { it + 1 } ?: 0 else this.tracks.lastIndex, checkedTracks)
	}
}
