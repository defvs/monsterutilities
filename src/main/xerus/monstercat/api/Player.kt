package xerus.monstercat.api

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
import java.util.logging.Level
import kotlin.math.pow

object Player : FadingHBox(true, targetHeight = 25) {
	
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
	
	init {
		box.alignment = Pos.CENTER
		maxHeight = Double.MAX_VALUE
		resetNotification()
	}
	
	private val label = Label()
	/** clears the [children] and shows the [label] with [text] */
	private fun showText(text: String) {
		ensureVisible()
		checkFx {
			children.setAll(label)
			label.text = text
		}
	}
	
	/** Shows [text] in the [label] and adds a back Button that calls [resetNotification] when clicked */
	private fun showBack(text: String) {
		checkFx {
			showText(text)
			addButton { resetNotification() }.id("back")
			fill(pos = 0)
			fill()
			add(closeButton)
		}
	}
	
	/** hides the Player and appears again with the latest Release */
	fun resetNotification() {
		fadeOut()
		launch {
			val latest = Releases.getReleases().lastOrNull() ?: return@launch
			while (fading) delay(50)
			showText("Latest Release: $latest")
			onFx {
				add(buttonWithId("play") { play(latest) })
				fill(pos = 0)
				fill()
				add(closeButton)
			}
		}
	}
	
	val activeTrack = SimpleObservable<Track?>(null)
	val activePlayer = SimpleObservable<MediaPlayer?>(null)
	val player get() = activePlayer.value
	
	init {
		box.visibleProperty().listen { visible -> if (!visible) disposePlayer() }
	}
	
	/** Plays the given [track] in the Player, stopping the previous MediaPlayer if necessary */
	fun playTrack(track: Track) {
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
				onFx {
					activeTrack.value = track
				}
			}
			setOnError {
				logger.log(Level.WARNING, "Error loading $track: $error", error)
				showBack("Error loading $track: ${error.message?.substringAfter(": ")}")
			}
		}
	}
	
	/** Stops playing, disposes the active MediaPlayer and calls [resetNotification] */
	fun stopPlaying() {
		activeTrack.value = null
		resetNotification()
	}
	
	private fun disposePlayer() {
		player?.dispose()
		activePlayer.value = null
		checkFx {
			seekBar.transitionToHeight(0.0)
		}
	}
	
	private val pauseButton = ToggleButton().id("play-pause").onClick { if (isSelected) player?.pause() else player?.play() }
	private val stopButton = buttonWithId("stop") { stopPlaying() }
	private val volumeSlider = Slider(0.0, 1.0, Settings.PLAYERVOLUME()).scrollable(0.05).apply {
		prefWidth = 100.0
		valueProperty().addListener { _ -> updateVolume() }
	}
	
	private fun playing(text: String) {
		onFx {
			showText(text)
			add(pauseButton.apply { isSelected = false })
			add(stopButton)
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
		launch {
			showText("Searching for \"$title\"...")
			disposePlayer()
			val track = API.find(title, artists)
			if (track == null) {
				onFx { showBack("Track not found") }
				return@launch
			}
			playTrack(track)
			player?.setOnEndOfMedia { stopPlaying() }
		}
	}
	
	/** Plays this [release], creating an internal playlist when it has multiple Tracks */
	fun play(release: Release) {
		checkFx { showText("Searching for $release") }
		launch {
			val results = APIConnection("catalog", "release", release.id, "tracks").getTracks()?.takeUnless { it.isEmpty() }
					?: run {
						showBack("No tracks found for Release $release")
						return@launch
					}
			playTracks(results, 0)
		}
	}
	
	/** Set the [tracks] as the internal playlist and start playing from the specified [index] */
	fun playTracks(tracks: MutableList<Track>, index: Int) {
		playTrack(tracks[index])
		onFx {
			if (index > 0)
				children.add(children.size - 3, buttonWithId("skipback") { playTracks(tracks, index - 1) })
			if (index < tracks.lastIndex)
				children.add(children.size - 3, buttonWithId("skip") { playTracks(tracks, index + 1) })
		}
		player?.setOnEndOfMedia { if (tracks.lastIndex > index) playTracks(tracks, index + 1) else stopPlaying() }
	}
	
}
