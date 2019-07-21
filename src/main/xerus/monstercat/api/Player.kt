package xerus.monstercat.api

import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.control.*
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
import xerus.ktutil.javafx.properties.dependOn
import xerus.ktutil.javafx.properties.listen
import xerus.ktutil.javafx.ui.controls.FadingHBox
import xerus.ktutil.javafx.ui.transitionToHeight
import xerus.ktutil.javafx.ui.verticalFade
import xerus.ktutil.square
import xerus.monstercat.Settings
import xerus.monstercat.api.response.Release
import xerus.monstercat.api.response.Track
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
			if (Playlist.tracks.size < 2){
				add(closeButton)
			}else{
				add(buttonWithId("skip") { playNext() }).apply {
					tooltip = Tooltip("Skip")
				}
				Timer().schedule(TimeUnit.SECONDS.toMillis(5)) {
					playNext()
				}
			}
		}
	}
	
	/** hides the Player and appears again displaying the latest Release */
	fun reset() {
		fadeOut()
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
		val hash = track.streamHash ?: run {
			showError("$track is currently not available for streaming!")
			return
		}
		updateCover(track.release.coverUrl)
		logger.debug("Loading $track from $hash")
		activePlayer.value = MediaPlayer(Media("https://s3.amazonaws.com/data.monstercat.com/blobs/$hash"))
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
			.onClick { if (isSelected) player?.pause() else player?.play() }
			.apply { tooltip = Tooltip("Pause / Play") }
	private val stopButton = Button().id("stop")
			.onClick {
				reset()
				Playlist.clear()
			}
			.apply { tooltip = Tooltip("Stop playing") }
	private val volumeSlider = Slider(0.0, 1.0, Settings.PLAYERVOLUME())
			.scrollable(0.05)
			.apply {
				prefWidth = 100.0
				valueProperty().listen { updateVolume() }
				tooltip = Tooltip("Volume")
			}
	private val shuffleButton = ToggleButton().id("shuffle")
			.onClick { Playlist.shuffle = isSelected }
			.apply { tooltip = Tooltip("Shuffle") }
	private val repeatButton = ToggleButton().id("repeat")
			.onClick { Playlist.repeat = isSelected }
			.apply { tooltip = Tooltip("Repeat all") }
	
	private var coverUrl: String? = null
	private fun playing(text: String) {
		onFx {
			showText(text)
			if(coverUrl != null) {
				children.add(0, ImageView(Covers.getCoverImage(coverUrl!!, 24)))
				children.add(1, Region().setSize(4.0))
			}
			add(pauseButton.apply { isSelected = false })
			add(stopButton)
			add(buttonWithId("skipback") { playPrev() }).apply {
				tooltip = Tooltip("Previous")
			}
			add(buttonWithId("skip") { playNext() }).apply {
				tooltip = Tooltip("Next")
			}
			add(shuffleButton.apply { isSelected = Playlist.shuffle })
			add(repeatButton.apply { isSelected = Playlist.repeat })
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
		updateCover(null)
		showText("Searching for \"$title\"...")
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
	
	fun playFromPlaylist(index: Int){
		val track = Playlist[index]
		if (track != null) {
			playTrack(track)
		}
	}
	
	/** Plays this [release], creating an internal playlist when it has multiple Tracks */
	fun play(release: Release) {
		checkFx { showText("Searching for $release") }
		updateCover(release.coverUrl)
		playTracks(release.tracks, 0)
	}
	
	/** Set the [tracks] as the internal playlist and start playing from the specified [index] */
	fun playTracks(tracks: List<Track>, index: Int = 0) {
		Playlist.setTracks(tracks)
		playFromPlaylist(index)
	}
	
	fun playNext(){
		val next = Playlist.getNext()
		if (next == null){
			reset()
		}else{
			playTrack(next)
			
		}
	}
	
	fun playPrev(){
		val prev = Playlist.getPrev()
		if (prev != null){
			playTrack(prev)
		}
	}
	
	fun updateCover(coverUrl: String?) {
		logger.debug("Updating cover: $coverUrl")
		this.coverUrl = coverUrl
		checkFx {
			backgroundCover.value = coverUrl?.let {
				Background(BackgroundImage(Covers.getCoverImage(coverUrl), BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT, BackgroundPosition.CENTER, BackgroundSize(100.0, 100.0, true, true, true, true)))
			}
		}
	}
	
}
