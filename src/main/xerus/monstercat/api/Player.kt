package xerus.monstercat.api

import javafx.collections.ListChangeListener
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.VBox
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import xerus.ktutil.javafx.*
import xerus.ktutil.javafx.properties.SimpleObservable
import xerus.ktutil.javafx.properties.dependOn
import xerus.ktutil.javafx.properties.listen
import xerus.ktutil.javafx.ui.controls.FadingHBox
import xerus.ktutil.javafx.ui.transitionToHeight
import xerus.ktutil.javafx.ui.verticalFade
import xerus.ktutil.square
import xerus.monstercat.Settings
import xerus.monstercat.api.response.Release
import xerus.monstercat.api.response.Track
import xerus.monstercat.logger
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import kotlin.math.pow

object Player : FadingHBox(true, targetHeight = 25) {
	
	val activePlayer = SimpleObservable<MediaPlayer?>(null)
	val player get() = activePlayer.value
	
	val playlistTrack = SimpleObservable<PlaylistTrack?>(null)
	val activeTrack = SimpleObservable<Track?>(null)
	
	private val seekBar = ProgressBar(0.0).apply {
		id("seek-bar")
		setSize(height = 0.0)
		Settings.PLAYERSEEKBARHEIGHT.listen { if (player != null) transitionToHeight(Settings.PLAYERSEEKBARHEIGHT()) }
		
		maxWidth = Double.MAX_VALUE
		val handler = EventHandler<MouseEvent> { event ->
			if (event.button == MouseButton.PRIMARY) {
				val b1 = layoutBounds
				val mouseX = event.sceneX
				val percent = (mouseX - b1.minX) / (b1.maxX - b1.minX)
				progress = percent + 2 / width
				player?.run {
					seek(Duration(totalDuration.toMillis().times(progress)))
				}
			}
		}
		onMousePressed = handler
		onMouseDragged = handler
		setOnScroll {
			player?.run {
				seek(Duration(currentTime.toMillis() + it.deltaY * 2.0.pow(Settings.PLAYERSCROLLSENSITIVITY())))
			}
		}
	}
	
	internal val box = VBox(seekBar, this).apply {
		id("player")
		setSize(height = 0.0)
		opacity = 0.0
	}
	override val fader = box.verticalFade(30, -1.0)
	
	private val label = Label()
	
	init {
		box.alignment = Pos.CENTER
		box.visibleProperty().listen { visible -> if (!visible) disposePlayer() }
		maxHeight = Double.MAX_VALUE
		reset()
		activeTrack.listen { if(it != null && playlistTrack.value != Playlist.lastPolled) Playlist.history.add(PlaylistTrack(it.title, it.artistsTitle)) }
		Playlist.tracks.addListener(ListChangeListener {
			it.next()
			if(it.addedSize == it.list.size && playlistTrack.value == null) {
				logger.finer("Automatically starting Playlist")
				playNext()
			}
		})
	}
	
	/** clears the [children] and shows the [label] with [text] */
	private fun showText(text: String) {
		ensureVisible()
		checkFx {
			children.setAll(label)
			label.text = text
		}
	}
	
	/** Shows [text] in the [label] and adds a back Button that calls [reset] when clicked */
	private fun showBack(text: String) {
		checkFx {
			showText(text)
			addButton { reset() }.id("back")
			if (!Playlist.tracks.isEmpty()) {
				addButton { playNextOrStop() }.id("skip")
				launch {
					delay(2, TimeUnit.SECONDS)
					if (label.text == text && box.opacity == 1.0 && playNext() != null)
						logger.finer("Automatically skipping broken song")
				}
			}
			fill(pos = 0)
			fill()
			add(closeButton)
		}
	}
	
	/** hides the Player and appears again displaying the latest Release */
	fun reset() {
		fadeOut()
		launch {
			val latest = Releases.getReleases().lastOrNull() ?: return@launch
			while (fading) {
				println(opacity)
				delay(50)
			}
			showText("Latest Release: $latest")
			onFx {
				add(buttonWithId("play") { play(latest) })
				fill(pos = 0)
				fill()
				add(closeButton)
			}
		}
	}
	
	/** Plays the given [track] in the Player, stopping the previous MediaPlayer if necessary */
	fun play(track: Track) {
		playlistTrack.value = PlaylistTrack(track.title, track.artistsTitle)
		activeTrack.value = null
		val hash = track.streamHash ?: run {
			showBack("$track is currently not available for streaming!")
			return
		}
		logger.finer("Loading $track from $hash")
		activePlayer.value = MediaPlayer(Media("https://s3.amazonaws.com/data.monstercat.com/blobs/$hash"))
		updateVolume()
		playing("Loading $track")
		player?.run {
			play()
			setOnReady {
				label.text = "Now Playing: $track"
				val total = totalDuration.toMillis()
				seekBar.progressProperty().dependOn(currentTimeProperty()) { it.toMillis() / total }
				seekBar.transitionToHeight(Settings.PLAYERSEEKBARHEIGHT(), 1.0)
				checkFx { activeTrack.value = track }
			}
			setOnError {
				logger.warning("Error loading $track: $error")
				showBack("Error loading $track: ${error.message?.substringAfter(": ")}")
			}
			setOnEndOfMedia {
				playNextOrStop()
			}
		}
	}
	
	/** Disposes the [activePlayer] and hides the [seekBar] */
	private fun disposePlayer() {
		logger.finer("Disposing Player")
		player?.dispose()
		activePlayer.value = null
		activeTrack.value = null
		playlistTrack.value = null
		checkFx {
			seekBar.transitionToHeight(0.0)
		}
	}
	
	private val pauseButton = ToggleButton().id("play-pause").onClick { if (isSelected) player?.pause() else player?.play() }
	private val stopButton = buttonWithId("stop") { reset() }
	private val prevButton = buttonWithId("skipback") { Playlist.getPrev()?.let { play(it.title, it.artistsTitle) } }
	private val nextButton = buttonWithId("skip") { playNext() }
	private val randomButton = ToggleButton().id("shuffle").onClick { Playlist.random = isSelected }
	private val repeatButton = ToggleButton().id("repeat").onClick { Playlist.repeat = isSelected }
	private val volumeSlider = Slider(0.0, 1.0, Settings.PLAYERVOLUME()).scrollable(0.05).apply {
		prefWidth = 100.0
		valueProperty().addListener { _ -> updateVolume() }
	}
	
	private fun playing(text: String) {
		onFx {
			showText(text)
			add(pauseButton.apply { isSelected = false })
			add(stopButton)
			add(prevButton)
			add(nextButton)
			add(randomButton)
			add(repeatButton)
			add(volumeSlider)
			fill(pos = 0)
			fill()
			add(closeButton)
		}
	}
	
	/** Adjusts the volume to match the [volumeSlider] */
	private fun updateVolume() {
		player?.volume = volumeSlider.value.square
	}
	
	/** Finds the best match for the given [title] and [artists] and starts playing it */
	fun play(title: String, artists: String) {
		disposePlayer()
		playlistTrack.value = PlaylistTrack(title, artists)
		showText("Searching for \"$title\"...")
		launch {
			play(API.find(title, artists) ?: run {
				onFx { showBack("Could not find $artists - $title") }
				return@launch
			})
		}
	}
	
	/** Plays this [release], creating an internal playlist when it has multiple Tracks */
	fun play(release: Release) {
		playlistTrack.value = PlaylistTrack()
		checkFx { showText("Searching for $release") }
		launch {
			val results = APIConnection("catalog", "release", release.id, "tracks").getTracks()?.takeUnless { it.isEmpty() }
					?: run {
						showBack("No tracks found for Release $release")
						return@launch
					}
			play(results, 0)
		}
	}
	
	/** Set the [tracks] as the internal playlist and start playing from the specified [index] */
	fun play(tracks: MutableList<Track>, index: Int) {
		Playlist.setTracks(tracks.map { it.toPlaylistTrack() })
		play(tracks[index])
	}
	
	fun playNext() = Playlist.getNext()?.let { play(it.title, it.artistsTitle) }
	fun playNextOrStop() = playNext() ?: reset()
	
}

fun Track.toPlaylistTrack() = PlaylistTrack(title, artistsTitle)

class PlaylistTrack(val title: String = "", val artistsTitle: String = "") {
	override fun equals(other: Any?): Boolean =
			other is PlaylistTrack && title == other.title && (artistsTitle in other.artistsTitle || other.artistsTitle in artistsTitle)
	
	override fun toString(): String =
			"PlaylistTrack(title='$title', artistsTitle='$artistsTitle')"
	
	override fun hashCode(): Int {
		var result = title.hashCode()
		result = 31 * result + artistsTitle.hashCode()
		return result
	}
	
}