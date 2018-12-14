package xerus.monstercat.downloader

import javafx.beans.Observable
import javafx.beans.property.Property
import javafx.collections.ObservableList
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.StackPane
import javafx.stage.Stage
import javafx.stage.StageStyle
import javafx.util.StringConverter
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.controlsfx.control.SegmentedButton
import org.controlsfx.control.TaskProgressView
import xerus.ktutil.*
import xerus.ktutil.helpers.Cache
import xerus.ktutil.helpers.ParserException
import xerus.ktutil.helpers.Timer
import xerus.ktutil.javafx.*
import xerus.ktutil.javafx.properties.*
import xerus.ktutil.javafx.ui.App
import xerus.ktutil.javafx.ui.FileChooser
import xerus.ktutil.javafx.ui.FilterableTreeItem
import xerus.ktutil.javafx.ui.controls.*
import xerus.monstercat.api.APIConnection
import xerus.monstercat.api.CookieValidity
import xerus.monstercat.api.response.Artist
import xerus.monstercat.api.response.MusicItem
import xerus.monstercat.api.response.Release
import xerus.monstercat.api.response.Track
import xerus.monstercat.globalThreadPool
import xerus.monstercat.monsterUtilities
import xerus.monstercat.tabs.VTab
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong

private val qualities = arrayOf("mp3_128", "mp3_v2", "mp3_v0", "mp3_320", "flac", "wav")
val trackPatterns = ImmutableObservableList("%artistsTitle% - %title%", "%artists|, % - %title%", "%artists|enumeration% - %title%", "%artists|, % - %titleClean%{ (feat. %feat%)}{ (%extra%)}{ [%remix%]}")
val albumTrackPatterns = ImmutableObservableList("%artistsTitle% - %track% %title%", "%artists|enumeration% - %title%", *trackPatterns.items)

// Todo selecting items is not recursive for track-items
class TabDownloader : VTab() {
	
	private val songView = SongView()
	
	private val patternValid = SimpleObservable(false)
	private val noItemsSelected = SimpleObservable(true)
	
	private val searchField = TextField()
	private val releaseSearch = SearchRow(Searchable<Release, LocalDate>("Releases", Type.DATE) { it.releaseDate.substring(0, 10).toLocalDate() })
	
	init {
		FilterableTreeItem.autoLeaf = false
		
		// Check if no items in the views are selected
		songView.checkedItems.listen { noItemsSelected.value = it.size == 0 }
		
		releaseSearch.conditionBox.select(releaseSearch.conditionBox.items[1])
		(releaseSearch.searchField as DatePicker).run {
			converter = object : StringConverter<LocalDate>() {
				override fun toString(`object`: LocalDate?) = `object`?.toString()
				override fun fromString(string: String?) = string?.toLocalDate()
			}
			if (LASTDOWNLOADTIME() > 0)
				(releaseSearch.searchField as DatePicker).value =
					LocalDateTime.ofEpochSecond(LASTDOWNLOADTIME().toLong(), 0, OffsetDateTime.now().offset).toLocalDate()
		}
		
		// Apply filters
		songView.predicate.bind({
			val searchText = searchField.text
			if (releaseSearch.predicate == alwaysTruePredicate && searchText.isEmpty()) null
			else { parent, value ->
				parent != songView.root &&
					// Match titles
					(value.toString().contains(searchText, true) || (value as? Release)?.let { it.tracks.any { it.toString().contains(searchText, true) } } ?: false) &&
					// Match Releasedate
					(value as? Release)?.let { releaseSearch.predicate.test(it) } ?: false
			}
		}, searchField.textProperty(), releaseSearch.predicateProperty)
		searchField.textProperty().listen {
			songView.root.children.forEach { (it as CheckBoxTreeItem).updateSelection() }
		}
		
		initialize()
	}
	
	private fun initialize() {
		children.clear()
		
		// Download directory
		val chooser = FileChooser(App.stage, DOWNLOADDIR().toFile(), null, "Download directory")
		chooser.selectedFile.listen { DOWNLOADDIR.set(it.toPath()) }
		add(HBox(chooser.textField(), createButton("Select Folders") {
			Stage().run {
				val pane = gridPane(padding = 5)
				scene = Scene(pane)
				pane.addRow(0, chooser.textField(), chooser.button().allowExpand(vertical = false))
				pane.addRow(2, Label("Singles Subfolder").centerText(), TextField().bindText(DOWNLOADDIRSINGLE).placeholder("Subfolder (root if empty)"))
				pane.addRow(3, Label("Albums Subfolder").centerText(), TextField().bindText(DOWNLOADDIRALBUM).placeholder("Subfolder (root if empty)"))
				pane.addRow(4, Label("Mixes Subfolder").centerText(), TextField().bindText(DOWNLOADDIRMIXES).placeholder("Subfolder (root if empty)"))
				pane.addRow(5, Label("Podcast Subfolder").centerText(), TextField().bindText(DOWNLOADDIRPODCAST).placeholder("Subfolder (root if empty)"))
				pane.children.forEach {
					GridPane.setVgrow(it, Priority.SOMETIMES)
					GridPane.setHgrow(it, if (it is TextField) Priority.ALWAYS else Priority.SOMETIMES)
				}
				initWindowOwner(App.stage)
				pane.prefWidth = 700.0
				initStyle(StageStyle.UTILITY)
				isResizable = false
				show()
			}
		}))
		
		// Patterns
		fun patternLabel(pattern: Property<String>, track: Track) =
			Label().apply {
				textProperty().dependOn(pattern) {
					try {
						track.toString(pattern.value).also { patternValid.value = true }
					} catch (e: ParserException) {
						patternValid.value = false
						"No such field: " + e.cause?.cause?.message
					} catch (e: Exception) {
						patternValid.value = false
						monsterUtilities.showError(e, "Error while parsing filename pattern")
						e.toString()
					}
				}
			}
		
		val patternPane = gridPane()
		patternPane.add(Label("Singles pattern"),
			0, 0, 1, 2)
		patternPane.add(ComboBox<String>(trackPatterns).apply { isEditable = true; editor.textProperty().bindBidirectional(TRACKNAMEPATTERN) },
			1, 0)
		patternPane.add(patternLabel(TRACKNAMEPATTERN,
			Track().apply {
				title = "Bring The Madness (feat. Mayor Apeshit) (Aero Chord Remix)"
				artistsTitle = "Excision & Pegboard Nerds"
				artists = listOf(Artist("Pegboard Nerds"), Artist("Excision"))
			}),
			1, 1)
		patternPane.add(Label("Album Tracks pattern"),
			0, 2, 1, 2)
		patternPane.add(ComboBox<String>(albumTrackPatterns).apply { isEditable = true; editor.textProperty().bindBidirectional(ALBUMTRACKNAMEPATTERN) },
			1, 2)
		patternPane.add(patternLabel(ALBUMTRACKNAMEPATTERN,
			ReleaseFile("Gareth Emery & Standerwick - 3 Saving Light (INTERCOM Remix) [feat. HALIENE]", "Saving Light (The Remixes) [feat. HALIENE]")),
			1, 3)
		add(patternPane)
		
		// SongView
		add(searchField)
		add(HBox(5.0, Label("Releasedate").grow(Priority.NEVER), releaseSearch.conditionBox, releaseSearch.searchField))
		fill(songView)
		
		// Quality selector
		val buttons = ImmutableObservableList(*qualities.map {
			ToggleButton(it.replace('_', ' ').toUpperCase()).apply {
				userData = it
				isSelected = userData == QUALITY()
			}
		}.toTypedArray())
		add(SegmentedButton(buttons)).toggleGroup.selectedToggleProperty().listen { if (it != null) QUALITY.put(it.userData) }
		QUALITY.listen { buttons.forEach { button -> button.isSelected = button.userData == it } }
		
		// Misc options
		addLabeled("Keep separate cover arts for ", ComboBox<String>(ImmutableObservableList("Nothing", "Collections", "Collections & Singles")).apply {
			selectionModel.select(DOWNLOADCOVERS())
			selectionModel.selectedIndexProperty().listen { DOWNLOADCOVERS.set(it.toInt()) }
		})
		
		addLabeled("Album Mixes", ComboBox<String>(ImmutableObservableList("Keep", "Exclude", "Separate")).apply {
			selectionModel.select(ALBUMMIXES())
			selectionModel.selectedItemProperty().listen { ALBUMMIXES.set(it) }
		})
		
		addLabeled("Cover art size", Spinner(object : SpinnerValueFactory<Int>() {
			init {
				valueProperty().bindBidirectional(COVERARTSIZE)
			}
			
			override fun increment(steps: Int) {
				for (i in 0 until steps)
					if (value < 8000)
						value *= 2
			}
			
			override fun decrement(steps: Int) {
				for (i in 0 until steps)
					value /= 2
			}
		}))
		
		val epAsSingle = CheckBox("Treat Collections with less than")
		epAsSingle.isSelected = EPS_TO_SINGLES() > 0
		val epAsSingleAmount = intSpinner(2, 20, EPS_TO_SINGLES().takeIf { it != 0 } ?: 4)
		arrayOf(epAsSingle.selectedProperty(), epAsSingleAmount.valueProperty()).addListener {
			EPS_TO_SINGLES.set(if (epAsSingle.isSelected) epAsSingleAmount.value else 0)
		}
		addRow(epAsSingle, epAsSingleAmount, Label(" Songs as Singles"))
		
		addRow(CheckBox("Exclude already downloaded Songs").tooltip("Only works if the Patterns and Folders are correctly set")
			.also {
				it.selectedProperty().listen { selected ->
					songView.onReady {
						GlobalScope.launch {
							if (selected) {
								songView.roots.forEach {
									it.value.internalChildren.removeIf {
										(it.value as Release).path().exists()
									}
								}
							} else {
								songView.root.internalChildren.clear()
								songView.roots.clear()
								launch {
									songView.load()
								}
							}
						}
					}
				}
			})
		
		addRow(createButton("Smart select") {
			songView.onReady {
				GlobalScope.launch {
					val albums = arrayOf(songView.roots["Album"], songView.roots["EP"]).filterNotNull()
					albums.forEach { it.isSelected = true }
					@Suppress("UNCHECKED_CAST")
					val selectedAlbums = albums.flatMap { it.children } as List<FilterableTreeItem<Release>>
					val selectedTracks = selectedAlbums.flatMapTo(ArrayList()) { it.value.tracks }
					val filtered = selectedAlbums.filter {
						val releaseTracks = (it.value as Release).tracks
						if (releaseTracks.all { (selectedTracks.indexOf(it) != selectedTracks.lastIndexOf(it)).printIt(it) }) {
							it.isSelected = false
							val tracks = ArrayList(releaseTracks)
							selectedTracks.removeIf { track -> (track in tracks).also { bool -> if (bool) tracks.remove(track) } }
							return@filter true
						}
						false
					}
					logger.trace { "Filtered out $filtered" }
					songView.getItemsInCategory(Release.Type.SINGLE).filter {
						val tracks = it.value.tracks
						if(tracks.size == 1) {
							it.isSelected = true
							selectedTracks.add(tracks.first())
							return@filter false
						} else {
							return@filter true
						}
					}.plus(songView.getItemsInCategory(Release.Type.MCOLLECTION)).forEach {
						it.children.forEach {
							@Suppress("UNCHECKED_CAST")
							val tr = it as CheckBoxTreeItem<Track>
							if(tr.value !in selectedTracks) {
								tr.isSelected = true
								selectedTracks.add(tr.value)
							}
						}
					}
				}
			}
		}.tooltip("Selects all Albums+EPs and then all other Songs that are not included in these"))
		
		addRow(TextField().apply {
			promptText = "connect.sid"
			// todo better instructions
			tooltip = Tooltip("Log into monstercat.com from your browser, find the cookie \"connect.sid\" from \"connect.monstercat.com\" and copy the content into here (which usually starts with \"s%3A\")")
			textProperty().bindBidirectional(CONNECTSID)
			maxWidth = Double.MAX_VALUE
		}.grow(), Button("Start Download").apply {
			arrayOf<Observable>(patternValid, noItemsSelected, CONNECTSID, QUALITY).addListener {
				checkFx { refreshDownloadButton(this) }
			}.invalidated(CONNECTSID)
		})
	}
	
	private fun refreshDownloadButton(button: Button) {
		button.text = "Checking..."
		GlobalScope.launch {
			var valid = false
			val text = when (APIConnection.checkCookie()) {
				CookieValidity.NOCONNECTION -> "No connection"
				CookieValidity.NOUSER -> "Invalid connect.sid"
				CookieValidity.NOGOLD -> "Account is not subscribed to Gold"
				CookieValidity.GOLD -> when {
					!patternValid.value -> "Invalid pattern"
					QUALITY().isEmpty() -> "No format selected"
					noItemsSelected.value -> "Nothing selected to download"
					else -> {
						valid = true
						"Start Download"
					}
				}
			}
			onFx {
				button.text = text
				if (valid) button.setOnAction { Downloader() }
				else button.setOnAction { refreshDownloadButton(button) }
			}
		}
	}
	
	inner class Downloader {
		
		private val timer = Timer()
		private val log = LogTextArea()
		private val items: Map<Release, Collection<Track>?>
		private var total: Int
		
		private lateinit var downloader: Job
		private lateinit var tasks: ObservableList<Download>
		
		init {
			@Suppress("UNCHECKED_CAST")
			items = songView.roots.values.flatMap { it.internalChildren }.filter {
				val item = it as FilterableTreeItem
				item.isSelected || item.internalChildren.any { (it as CheckBoxTreeItem).isSelected }
			}.associate {
				(it.value as Release) to (it as FilterableTreeItem<MusicItem>).internalChildren.takeUnless { it.isEmpty() }
					?.filter { (it as CheckBoxTreeItem).isSelected }?.map { it.value as Track }
			}
			logger.info("Starting Downloader for ${items.size} Releases")
			total = items.size
			onFx {
				buildUI()
				startDownload()
			}
		}
		
		private fun buildUI() {
			children.clear()
			
			val cancelButton = Button("Finish").onClick {
				isDisable = true
				downloader.cancel()
			}
			add(cancelButton.allowExpand(vertical = false))
			
			addLabeled("Download Threads", intSpinner(0, 5) syncTo DOWNLOADTHREADS)
			val progressLabel = Label("0 / $total Errors: 0")
			val progressBar = ProgressBar()
			add(StackPane(progressBar.allowExpand(vertical = false), progressLabel))
			add(log)
			var counter: Job? = null
			var time = 0L
			state.listen {
				counter?.cancel()
				val s = state.success
				val e = state.errors
				val done = s + e
				val estimatedLength = total * lengths.mapIndexed { index, element -> element * Math.pow(1.6, index.toDouble()) }.sum() / lengths.size.downTo(1).sum()
				progressBar.progress = done / total.toDouble()
				if (done == total)
					progressLabel.text = "$done / $total Errors: $e"
				else
					counter = GlobalScope.launch {
						val estimate = ((estimatedLength / lengths.sum().coerceAtLeast(1.0) + total / done - 2) * timer.time() / 1000).roundToLong()
						time = if (time > 0) (time * 9 + estimate) / 10 else estimate
						logger.trace("Estimate: ${formatTimeDynamic(estimate, estimate.coerceAtLeast(60))} Weighed: ${formatTimeDynamic(time, time.coerceAtLeast(60))}")
						while (time > 0) {
							onFx {
								progressLabel.text = "$done / $total Errors: $e - Estimated time left: " + formatTimeDynamic(time, time.coerceAtLeast(60))
							}
							delay(TimeUnit.SECONDS.toMillis(1))
							time--
						}
					}
			}
			
			val taskView = TaskProgressView<Download>()
			val thumbnailCache = Cache<String, Image>()
			taskView.setGraphicFactory {
				ImageView(thumbnailCache.getOrPut(it.coverUrl) {
					Image(getCover(it, 64))
				})
			}
			tasks = taskView.tasks
			tasks.listen {
				if (it.isEmpty()) {
					cancelButton.apply {
						setOnMouseClicked { initialize() }
						text = "Back"
						isDisable = false
					}
				}
			}
			fill(taskView)
		}
		
		private val lengths = ArrayList<Double>(items.size)
		private val state = DownloaderState(total)
		private fun startDownload() {
			downloader = GlobalScope.launch {
				log("Download started")
				var queued = 0
				for (item in items) {
					val download = ReleaseDownload(item.key, item.value)
					download.setOnCancelled {
						total--
						log("Cancelled ${item.key}")
					}
					download.setOnSucceeded {
						lengths.add(download.length.toDouble() / 10000)
						state.success()
						log("Downloaded ${item.key}")
					}
					download.setOnFailed {
						val exception = download.exception
						state.error(exception)
						logger.error("$download failed with $exception", exception)
						log("Error downloading ${download.item}: " + if (exception is ParserException) exception.message else exception.str())
					}
					try {
						globalThreadPool.execute(download)
						queued++
						onFx { tasks.add(download); queued-- }
					} catch (exception: Throwable) {
						logger.error("$download could not be started in TabDownloader because of $exception", exception)
						log("Could not start download for $item: $exception")
					}
					while (tasks.size + queued >= DOWNLOADTHREADS())
						delay(200)
				}
				LASTDOWNLOADTIME.set(currentSeconds())
			}
		}
		
		private fun log(msg: String) {
			log.appendln("${formattedTime()} $msg")
		}
		
	}
	
}

/*override fun init() {
	if () {
		val alert = Alert(Alert.AlertType.CONFIRMATION, "The last Download ended unfinished. Do you want to continue it now?", ButtonType.YES, ButtonType.NO, ButtonType("Ask later"))
		when (alert.showAndWait().getOrNull()) {
			ButtonType.YES -> {
				monsterUtilities.tabPane.selectionModel.select(monsterUtilities.tabs.indexOf(this))
				startDownload()
			}
			ButtonType.NO -> { reset }
		}
	}
}*/
