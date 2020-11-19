package xerus.monstercat.api

import javafx.beans.Observable
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.*
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import xerus.ktutil.javafx.*
import xerus.ktutil.javafx.properties.SimpleObservable
import xerus.ktutil.javafx.properties.addListener
import xerus.ktutil.javafx.properties.dependOn
import xerus.ktutil.javafx.properties.listen
import xerus.ktutil.javafx.ui.controls.FadingHBox
import xerus.ktutil.javafx.ui.transitionToHeight
import xerus.ktutil.javafx.ui.verticalFade
import xerus.ktutil.square
import xerus.ktutil.write
import xerus.monstercat.Settings
import xerus.monstercat.api.response.Release
import xerus.monstercat.api.response.Track
import xerus.monstercat.monsterUtilities
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.schedule
import kotlin.math.pow

object Player: FadingHBox(true, targetHeight = 25) {
	private val logger = KotlinLogging.logger { }
	
	private val seekBar = ProgressBar(0.0).apply {
		id("seek-bar")
		setSize(height = 0.0)
		Settings.PLAYERSEEKBARHEIGHT.listen { if(player != null) transitionToHeight(Settings.PLAYERSEEKBARHEIGHT()) }
		
		maxWidth = Double.MAX_VALUE
		val handler = EventHandler<MouseEvent> { event ->
			if(event.button == MouseButton.PRIMARY) {
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
	
	internal val backgroundCover = SimpleObservable<Background?>(null)
	internal val container = VBox(seekBar, this).apply {
		id("player")
		setSize(height = 0.0)
		opacity = 0.0
	}
	
	override val fader = container.verticalFade(30, -1.0)
	
	init {
		container.alignment = Pos.CENTER
		maxHeight = Double.MAX_VALUE
		reset()
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
	
	/** Shows [text] in the [label] and adds a back Button that calls [reset] when clicked */
	private fun showError(text: String) {
		checkFx {
			showText(text)
			addButton { reset() }.id("back")
			fill(pos = 0)
			fill()
			if(Playlist.tracks.size < 2) {
				add(closeButton)
			} else {
				add(buttonWithId("skip") { playNext() }).tooltip("Skip")
				Timer().schedule(TimeUnit.SECONDS.toMillis(5)) {
					playNext()
				}
			}
		}
	}
	
	/** hides the Player and appears again displaying the latest Release */
	fun reset() {
		fadeOut()
		if(!Files.isDirectory(Settings.PLAYEREXPORTFILE())) {
			Files.newBufferedWriter(Settings.PLAYEREXPORTFILE(),
					StandardOpenOption.TRUNCATE_EXISTING,
					StandardOpenOption.WRITE,
					StandardOpenOption.CREATE
			).close()
			logger.debug("Cleared export file (${Settings.PLAYEREXPORTFILE()})")
		} else logger.warn("Export file ${Settings.PLAYEREXPORTFILE()} is a folder! Not overwriting.")
		GlobalScope.launch {
			val latest = Cache.getReleases().firstOrNull() ?: return@launch
			while(fading) delay(50)
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
		container.visibleProperty().listen { visible ->
			if(!visible) {
				disposePlayer()
				updateCover(null)
			}
		}
	}
	
	/** Plays the given [track] in the Player, stopping the previous MediaPlayer if necessary */
	fun playTrack(track: Track) {
		disposePlayer()
		if(!track.streamable) {
			showError("$track is currently not available for streaming!")
			return
		}
		GlobalScope.launch {
			logger.debug("Fetching stream url for $track")
			val streamUrl = APIConnection.getRedirectedStreamURL(track)
			logger.debug("Loading $track from '$streamUrl'")
			activePlayer.value = MediaPlayer(Media(streamUrl))
			updateVolume()
			onFx {
				activeTrack.value = track
			}
			playing("Loading $track")
			player?.run {
				play()
				setOnEndOfMedia { playNext() }
				setOnReady {
					label.text = "Now Playing: $track"
					if(!Files.isDirectory(Settings.PLAYEREXPORTFILE())) {
						Files.newBufferedWriter(Settings.PLAYEREXPORTFILE()).apply {
							write(track.toString(Settings.PLAYEREXPORTFILEPATTERN()))
						}.close()
						logger.debug("""Wrote "$track" into export file (${Settings.PLAYEREXPORTFILE()})""")
					} else logger.warn("Export file ${Settings.PLAYEREXPORTFILE()} is a folder! Not overwriting.")
					val total = totalDuration.toMillis()
					seekBar.progressProperty().dependOn(currentTimeProperty()) { it.toMillis() / total }
					seekBar.transitionToHeight(Settings.PLAYERSEEKBARHEIGHT(), 1.0)
				}
				setOnError {
					logger.warn("Error loading $track: $error", error)
					showError("Error loading $track: ${error.message?.substringAfter(": ")}")
				}
			}
		}
		updateCover(track.release.coverUrl)
	}
	
	/** Disposes the [activePlayer] and hides the [seekBar] */
	private fun disposePlayer() {
		player?.dispose()
		activePlayer.value = null
		activeTrack.value = null
		checkFx {
			seekBar.transitionToHeight(0.0)
		}
	}
	
	private val pauseButton = ToggleButton().id("play-pause")
		.tooltip("Pause / Play")
		.onClick { if(isSelected) player?.pause() else player?.play() }
	private val stopButton = Button().id("stop")
		.tooltip("Stop playing")
		.onClick {
			reset()
			Playlist.clear()
		}
	private val volumeSlider = Slider(0.0, 1.0, Settings.PLAYERVOLUME())
		.scrollable(0.05)
		.apply {
			prefWidth = 100.0
			valueProperty().listen { updateVolume() }
			tooltip = Tooltip("Volume")
		}
	private val shuffleButton = ToggleButton().id("shuffle")
		.tooltip("Shuffle")
		.apply { selectedProperty().bindBidirectional(Playlist.shuffle) }
	private val repeatButton = ToggleButton().id("repeat")
		.tooltip("Repeat all")
		.apply { selectedProperty().bindBidirectional(Playlist.repeat) }
	private val skipbackButton = buttonWithId("skipback") { playPrev() }
		.tooltip("Previous")
		.apply { Playlist.currentIndex.listen { value -> isVisible = value != 0 } }
	private val skipButton = buttonWithId("skip") { playNext() }
		.tooltip("Next")
		.apply {
			arrayOf<Observable>(Playlist.currentIndex, Playlist.repeat, Playlist.shuffle).addListener {
				isVisible = Playlist.currentIndex.value != Playlist.tracks.lastIndex || Playlist.repeat.value || Playlist.shuffle.value
			}
		}
	
	private var coverUrl: String? = null
	private fun playing(text: String) {
		onFx {
			showText(text)
			if(coverUrl != null) {
				val imageView = ImageView(Covers.getThumbnailImage(coverUrl!!, 24))
				imageView.setOnMouseClicked {
					if(it.button == MouseButton.PRIMARY) {
						monsterUtilities.viewCover(coverUrl!!)
					}
				}
				children.add(0, imageView)
				children.add(1, Region().setSize(4.0))
			}
			children.addAll(pauseButton.apply { isSelected = false }, stopButton, skipbackButton, skipButton, shuffleButton, repeatButton, volumeSlider)
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
		showText("Searching for \"$title\"...")
		updateCover(null)
		disposePlayer()
		GlobalScope.launch {
			val track = APIUtils.find(title, artists)
			if(track == null) {
				onFx { showError("Track not found") }
				return@launch
			}
			playTracks(listOf(track))
		}
	}
	
	fun playFromPlaylist(index: Int) {
		Playlist[index]?.also { playTrack(it) }
	}
	
	/** Plays this [release], creating an internal playlist when it has multiple Tracks */
	fun play(release: Release) {
		checkFx { showText("Searching for $release") }
		playTracks(release.tracks, 0)
	}
	
	/** Set the [tracks] as the internal playlist and start playing from the specified [index] */
	fun playTracks(tracks: List<Track>, index: Int = 0) {
		Playlist.setTracks(tracks)
		playFromPlaylist(index)
	}
	
	fun playNext() {
		Playlist.getNext()?.let { playTrack(it) } ?: reset()
	}
	
	fun playPrev() {
		Playlist.getPrev()?.also { playTrack(it) }
	}
	
	fun updateCover(coverUrl: String?) {
		if(coverUrl == this.coverUrl)
			return
		logger.debug("Updating cover: $coverUrl")
		this.coverUrl = coverUrl
		GlobalScope.launch {
			val image: Image? = coverUrl?.let { Covers.getThumbnailImage(it) }
			onFx {
				backgroundCover.value = image?.let {
					Background(BackgroundImage(image, BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT, BackgroundPosition.CENTER, BackgroundSize(100.0, 100.0, true, true, true, true)))
				}
			}
		}
	}
	
}
