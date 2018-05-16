package xerus.monstercat.api

import javafx.event.EventHandler
import javafx.scene.control.*
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.VBox
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import xerus.ktutil.helpers.Rater
import xerus.ktutil.javafx.*
import xerus.ktutil.javafx.properties.dependOn
import xerus.ktutil.javafx.properties.listen
import xerus.ktutil.javafx.ui.controls.FadingHBox
import xerus.ktutil.javafx.ui.verticalTransition
import xerus.ktutil.square
import xerus.ktutil.toInt
import xerus.monstercat.Settings
import xerus.monstercat.api.response.Release
import xerus.monstercat.api.response.Track
import xerus.monstercat.logger
import java.net.URLEncoder
import java.util.regex.Pattern

object Player : FadingHBox(true, true, 25) {
	private val seekBar = ProgressBar(0.0).apply {
		id("seek-bar")
		setSize(height = 6.0)
		isVisible = false
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
	}
	
	internal val box = VBox(seekBar, this).apply {
		id("player")
		setSize(height = 0.0)
		opacity = 0.0
	}
	override val fader = box.verticalTransition(30, true)
	
	init {
		resetNotification()
		box.visibleProperty().listen { visible -> if (!visible) disposePlayer() }
	}
	
	private val label = Label()
	private fun showText(text: String) {
		ensureVisible()
		checkJFX {
			children.setAll(label)
			label.text = text
		}
	}
	
	private fun showBack(text: String) {
		checkJFX {
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
			onJFX {
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
	
	private fun disposePlayer() {
		player?.dispose()
		player = null
		checkJFX { 
			seekBar.progress = 0.0
			seekBar.isVisible = false
		}
	}
	
	// playing & controls
	
	private val pauseButton = ToggleButton().id("play-pause").onClick { if (isSelected) player?.pause() else player?.play() }
	private val stopButton = buttonWithId("stop") { stopPlaying() }
	private val volumeSlider = Slider(0.1, 1.0, Settings.PLAYERVOLUME()).apply {
		prefWidth = 100.0
		valueProperty().addListener { _ -> setVolume() }
	}
	
	private fun playing(text: String) {
		onJFX {
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
				seekBar.isVisible = true
			}
		}
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
				onJFX {
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
				if (track.titleRaw == title)
					prob++
				track.artists.forEach { artist -> prob += artists.contains(artist.name).toInt() }
				rater.update(track, prob / track.artists.size + (track.titleRaw == title).toInt())
			}
			// play
			playTrack(rater.obj!!)
			player?.setOnEndOfMedia { stopPlaying() }
			return@launch
		}
	}
	
	fun play(release: Release) {
		checkJFX { showText("Searching for $release") }
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
		onJFX {
			if (index > 0)
				children.add(children.size - 3, buttonWithId("skipback") { play(tracks, index - 1) })
			if (index < tracks.lastIndex)
				children.add(children.size - 3, buttonWithId("skip") { play(tracks, index + 1) })
		}
		player?.setOnEndOfMedia { if (tracks.lastIndex > index) play(tracks, index + 1) else stopPlaying() }
	}
	
}
