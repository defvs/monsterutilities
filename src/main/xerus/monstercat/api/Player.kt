package xerus.monstercat.api

import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.VBox
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.scene.media.AudioEqualizer
import javafx.util.Duration
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import xerus.ktutil.helpers.Rater
import xerus.ktutil.javafx.*
import xerus.ktutil.javafx.properties.dependOn
import xerus.ktutil.javafx.properties.listen
import xerus.ktutil.javafx.ui.controls.FadingHBox
import xerus.ktutil.javafx.ui.transitionToHeight
import xerus.ktutil.javafx.ui.verticalFade
import xerus.ktutil.square
import xerus.ktutil.toInt
import xerus.monstercat.Settings
import xerus.monstercat.api.response.Release
import xerus.monstercat.api.response.Track
import xerus.monstercat.logger
import java.net.URLEncoder
import java.util.regex.Pattern
import kotlin.math.pow

object Player : FadingHBox(true, targetHeight = 25) {
	private val seekBar = ProgressBar(0.0).apply {
		id("seek-bar")
		setSize(height = 0.0)
		Settings.PLAYERSEEKBARHEIGHT.listen { if(player != null) transitionToHeight(Settings.PLAYERSEEKBARHEIGHT()) }
		
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
		box.visibleProperty().listen { visible -> if (!visible) disposePlayer() }
	}
	
	private val label = Label()
	private fun showText(text: String) {
		ensureVisible()
		checkFx {
			children.setAll(label)
			label.text = text
		}
	}
	
	private fun showBack(text: String) {
		checkFx {
			showText(text)
			addButton(handler = { resetNotification() }).id("back")
			fill(pos = 0)
			fill()
			add(closeButton)
		}
	}
	
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
	
	private fun stopPlaying() {
		resetNotification()
		disposePlayer()
	}
	
	private fun firePlayerListeners() {
		playerListeners.forEach { it(player) }
	}
	
	private fun disposePlayer() {
		Settings.ENABLEEQUALIZER.unbind()
		player?.dispose()
		player = null
		firePlayerListeners()
		checkFx {
			seekBar.transitionToHeight(0.0)
		}
	}
	
	// playing & controls
	
	private val pauseButton = ToggleButton().id("play-pause").onClick { if (isSelected) player?.pause() else player?.play() }
	private val stopButton = buttonWithId("stop") { stopPlaying() }
	private val volumeSlider = Slider(0.0, 1.0, Settings.PLAYERVOLUME()).scrollable(0.05).apply {
		prefWidth = 100.0
		valueProperty().addListener { _ -> setVolume() }
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
	
	private fun setVolume() {
		player?.volume = volumeSlider.value.square
	}
	
	private var player: MediaPlayer? = null
	// Listeners are notified when the MediaPlayer is swapped.
	val playerListeners = mutableListOf<(MediaPlayer?) -> Unit>()
	
	fun playTrack(track: Track) {
		disposePlayer()
		val hash = track.streamHash ?: run {
			showBack("$track is currently not available for streaming!")
			return
		}
		player = MediaPlayer(Media("https://s3.amazonaws.com/data.monstercat.com/blobs/$hash"))
		setVolume()
		playing("Loading $track")
		player!!.run {
			play()
			setOnReady {
				label.text = "Now Playing: $track"
				val total = totalDuration.toMillis()
				seekBar.progressProperty().dependOn(currentTimeProperty()) { it.toMillis() / total }
				seekBar.transitionToHeight(Settings.PLAYERSEEKBARHEIGHT(), 1.0)
			}
		}
		equalizer!!.enabledProperty().bind(Settings.ENABLEEQUALIZER)
		firePlayerListeners()
	}
	
	// find tracks and initiate player
	
	fun play(title: String, artists: String) {
		launch {
			showText("Searching for \"$title\"...")
			disposePlayer()
			// fetch tracks with given title
			val connection = APIConnection("catalog", "track").addQuery("fields", "artists", "artistsTitle", "title")
			URLEncoder.encode(title, "UTF-8")
					.split(Pattern.compile("%.."))
					.filter { it.isNotBlank() }
					.forEach { connection.addQuery("fuzzy", "title," + it.trim()) }
			val results = connection.getTracks().orEmpty()
			if (results.isEmpty()) {
				onFx {
					showBack("Track not found")
				}
				logger.fine("No results for $connection")
				return@launch
			}
			// find best fit by matching artists
			val rater = Rater(results[0], 0.0)
			results.forEach { track ->
				var prob = 0.0
				track.init()
				track.artists.forEach { artist -> if(artists.contains(artist.name)) prob++ }
				rater.update(track, prob / track.artists.size + (track.titleRaw == title).toInt())
			}
			// play
			playTrack(rater.obj!!)
			player?.setOnEndOfMedia { stopPlaying() }
			return@launch
		}
	}
	
	fun play(release: Release) {
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
	
	fun play(tracks: MutableList<Track>, index: Int) {
		playTrack(tracks[index])
		onFx {
			if (index > 0)
				children.add(children.size - 3, buttonWithId("skipback") { play(tracks, index - 1) })
			if (index < tracks.lastIndex)
				children.add(children.size - 3, buttonWithId("skip") { play(tracks, index + 1) })
		}
		player?.setOnEndOfMedia { if (tracks.lastIndex > index) play(tracks, index + 1) else stopPlaying() }
	}
	
	val equalizer
		get() = player?.audioEqualizer
}
