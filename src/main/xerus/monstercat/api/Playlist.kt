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
import kotlin.random.Random
import kotlin.random.nextInt

object Playlist {
	val logger = KotlinLogging.logger { }
	
	val tracks: ObservableList<Track> = FXCollections.observableArrayList()
	val history = ArrayDeque<Track>()
	val currentIndex = SimpleObservable<Int?>(null).apply {
		bindSoft({
			tracks.indexOf(Player.activeTrack.value).takeUnless { it == -1 }
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
	
	/** Displays an warning dialog, telling the user about the unlicensable status of the [Track] (s) he's trying to add.
	 * @param track : Can be null if unknown, [Track.toString] (Artist - Title) will be shown to the user if given.
	 */
	private fun showUnlicensableAlert(track: Track? = null) {
		onFx {
			monsterUtilities.showAlert(Alert.AlertType.WARNING, "Playlist", "Unlicensable tracks !",
				"Skipped adding ${track ?: "tracks"} according to your settings.")
		}
	}
	
	/** Removes unlicensable [Track]s (including mixes and podcasts, which are recognizable by their [Track.artistsTitle] being "Monstercat")
	 * @param tracks : [Collection] of [Track]s from which should be deducted tracks which are not [Track.creatorFriendly].
	 * @return : The received [tracks] with unlicensable tracks removed. Warning, can end up being empty !
	 */
	private fun removeUnlicensable(tracks: Collection<Track>) = tracks.filter { it.creatorFriendly && it.artistsTitle != "" && it.artistsTitle != "Monstercat" }
	
	fun addNext(track: Track) {
		tracks.remove(track)
		if(track.creatorFriendly || !SKIPUNLICENSABLE())
			tracks.add(currentIndex.value?.let { it + 1 } ?: 0, track)
		else showUnlicensableAlert(track)
	}
	
	fun add(track: Track) {
		tracks.remove(track)
		if(track.creatorFriendly || !SKIPUNLICENSABLE())
			tracks.add(track)
		else showUnlicensableAlert(track)
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
		val checkedTracks = if(SKIPUNLICENSABLE()) removeUnlicensable(playlist) else playlist
		if(checkedTracks.isEmpty()) showUnlicensableAlert()
		tracks.setAll(checkedTracks)
	}
	
	fun getNextTrackRandom(): Track {
		val index = Random.nextInt(0..tracks.lastIndex)
			.let { if(it >= currentIndex.value!!) it + 1 else it }
			.takeUnless { it >= tracks.size } ?: 0
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
		val checkedTracks = if(SKIPUNLICENSABLE()) removeUnlicensable(tracks) else tracks
		if(checkedTracks.isEmpty()) showUnlicensableAlert()
		this.tracks.addAll(if(asNext) currentIndex.value?.plus(1) ?: 0 else this.tracks.lastIndex.coerceAtLeast(0), checkedTracks)
	}
}
