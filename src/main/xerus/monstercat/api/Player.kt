package xerus.monstercat.api

import javafx.scene.control.*
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import xerus.ktutil.helpers.Rater
import xerus.ktutil.javafx.*
import xerus.ktutil.javafx.ui.FadingHBox
import xerus.ktutil.toInt
import xerus.monstercat.api.response.Release
import xerus.monstercat.api.response.Track
import xerus.monstercat.logger
import java.net.URLEncoder
import java.util.regex.Pattern

object Player : FadingHBox(false) {
	
	private val label = Label()
	
	init {
		id("controls")
		resetNotification()
		opacityProperty().addListener { _ -> if(opacity == 0.0) disposePlayer() }
	}
	
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
			val latest = Releases.getReleases().last()
			while (fading) delay(50)
			showText("Latest Release: $latest")
			onJFX {
				add(Button().id("play").onClick { Player.play(latest) })
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
	}
	
	// playing & controls
	
	private val pauseButton = ToggleButton().id("play-pause").onClick { if (isSelected) player?.pause() else player?.play() }
	private val stopButton = Button().id("stop").onClick { stopPlaying() }
	private val volumeSlider = Slider(0.1, 1.0, 0.4).apply { prefWidth = 100.0; valueProperty().addListener { _ -> setVolume() } }
	
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
		player?.volume = Math.pow(volumeSlider.value, 2.0)
	}
	
	private var player: MediaPlayer? = null
	fun playTrack(track: Track) {
		disposePlayer()
		val hash = track.streamHash ?: run {
			showBack("$track is currently not available for streaming!")
			return
		}
		player = MediaPlayer(Media("https://s3.amazonaws.com/data.monstercat.com/blobs/" + hash))
		setVolume()
		player!!.play()
		playing("Loading $track")
		player!!.setOnReady { label.text = "Now Playing: $track" }
	}
	
	// find tracks and initiate player
	
	fun play(title: String, artists: String) {
		launch {
			showText("Searching for \"$title\"...")
			disposePlayer()
			// fetch tracks with given title
			val connection = APIConnection("catalog", "track").addQuery("fields", "artists", "artistsTitle", "title")
			URLEncoder.encode(title, "UTF-8").split(Pattern.compile("%.."))
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
			results.forEach {
				var prob = 0.0
				it.artists.forEach { prob += artists.contains(it.name).toInt() }
				rater.update(it, prob / it.artists.size)
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
			val results = APIConnection("catalog", "release", release.id, "tracks").getTracks()?.takeUnless { it.isEmpty() } ?: run {
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
				children.add(children.size - 3, Button().id("skipback").onClick { play(tracks, index - 1) })
			if (index < tracks.lastIndex)
				children.add(children.size - 3, Button().id("skip").onClick { play(tracks, index + 1) })
		}
		player?.setOnEndOfMedia { if (tracks.lastIndex > index) play(tracks, index + 1) else stopPlaying() }
	}
	
}