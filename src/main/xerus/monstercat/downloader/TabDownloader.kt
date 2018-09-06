package xerus.monstercat.downloader

import javafx.beans.InvalidationListener
import javafx.beans.Observable
import javafx.beans.property.Property
import javafx.beans.property.SimpleIntegerProperty
import javafx.collections.ObservableList
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.*
import javafx.stage.Stage
import javafx.stage.StageStyle
import javafx.util.StringConverter
import kotlinx.coroutines.experimental.*
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import org.controlsfx.control.SegmentedButton
import org.controlsfx.control.TaskProgressView
import xerus.ktutil.*
import xerus.ktutil.helpers.Cache
import xerus.ktutil.helpers.ParserException
import xerus.ktutil.helpers.Timer
import xerus.ktutil.javafx.*
import xerus.ktutil.javafx.controlsfx.progressDialog
import xerus.ktutil.javafx.properties.*
import xerus.ktutil.javafx.ui.App
import xerus.ktutil.javafx.ui.FileChooser
import xerus.ktutil.javafx.ui.FilterableTreeItem
import xerus.ktutil.javafx.ui.controls.*
import xerus.monstercat.api.APIConnection
import xerus.monstercat.api.CookieValidity
import xerus.monstercat.api.response.*
import xerus.monstercat.globalThreadPool
import xerus.monstercat.logger
import xerus.monstercat.monsterUtilities
import xerus.monstercat.tabs.VTab
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong

private val qualities = arrayOf("mp3_128", "mp3_v2", "mp3_v0", "mp3_320", "flac", "wav")
val trackPatterns = ImmutableObservableList("%artistsTitle% - %title%", "%artists|, % - %title%", "%artists|enumeration% - %title%", "%artists|, % - %titleRaw%{ (feat. %feat%)}{ [%remix%]}")
val albumTrackPatterns = ImmutableObservableList("%artistsTitle% - %track% %title%", "%artists|enumeration% - %title%", *trackPatterns.items)

val TreeItem<out MusicItem>.normalisedValue
	get() = value.toString().trim().toLowerCase()

val String.normalised
	get() = trim().toLowerCase()

class TabDownloader : VTab() {
	
	private val releaseView = ReleaseView()
	private val trackView = TrackView()
	
	private val patternValid = SimpleObservable(false)
	private val noItemsSelected = SimpleObservable(true)
	
	private val searchField = TextField()
	private val releaseSearch = SearchRow(Searchable<Release, LocalDate>("Releases", Type.DATE) { it.releaseDate.substring(0, 10).toLocalDate() })
	
	init {
		FilterableTreeItem.autoExpand = true
		FilterableTreeItem.autoLeaf = false
		
		// Check if no items in the views are selected
		val itemListener = InvalidationListener { noItemsSelected.value = releaseView.checkedItems.size + trackView.checkedItems.size == 0 }
		releaseView.checkedItems.addListener(itemListener)
		trackView.checkedItems.addListener(itemListener)
		
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
		releaseView.predicate.bind({
			if (releaseSearch.predicate == alwaysTruePredicate && searchField.text.isEmpty()) null
			else { parent, value -> parent != releaseView.root && value.toString().contains(searchField.text, true) && releaseSearch.predicate.test(value) }
		}, searchField.textProperty(), releaseSearch.predicateProperty)
		trackView.root.bindPredicate(searchField.textProperty())
		searchField.textProperty().addListener { _ ->
			releaseView.root.children.forEach { (it as CheckBoxTreeItem).updateSelection() }
			trackView.root.updateSelection()
		}
		
		initialize()
	}
	
	private suspend fun awaitReady() {
		while (!releaseView.ready || !trackView.ready)
			delay(100)
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
				pane.addRow(1, Label("Tracks Subfolder").centerText(), TextField().bindText(TRACKFOLDER).placeholder("Subfolder (root if empty)"))
				pane.addRow(2, Label("Singles Subfolder").centerText(), TextField().bindText(SINGLEFOLDER).placeholder("Subfolder (root if empty)"))
				pane.addRow(3, Label("Albums Subfolder").centerText(), TextField().bindText(ALBUMFOLDER).placeholder("Subfolder (root if empty)"))
				pane.addRow(4, Label("Mixes Subfolder").centerText(), TextField().bindText(MIXESFOLDER).placeholder("Subfolder (root if empty)"))
				pane.addRow(5, Label("Podcast Subfolder").centerText(), TextField().bindText(PODCASTFOLDER).placeholder("Subfolder (root if empty)"))
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
				Track(title = "Bring The Madness (feat. Mayor Apeshit) (Aero Chord Remix)", artistsTitle = "Excision & Pegboard Nerds", artists = listOf(Artist("Pegboard Nerds"), Artist("Excision")))),
				1, 1)
		patternPane.add(Label("Album Tracks pattern"),
				0, 2, 1, 2)
		patternPane.add(ComboBox<String>(albumTrackPatterns).apply { isEditable = true; editor.textProperty().bindBidirectional(ALBUMTRACKNAMEPATTERN) },
				1, 2)
		patternPane.add(patternLabel(ALBUMTRACKNAMEPATTERN,
				ReleaseFile("Gareth Emery & Standerwick - 3 Saving Light (INTERCOM Remix) [feat. HALIENE]", "Saving Light (The Remixes) [feat. HALIENE]")),
				1, 3)
		add(patternPane)
		
		// TODO EPS_TO_SINGLES
		// SongView
		add(searchField)
		fill(gridPane().apply {
			add(HBox(5.0, Label("Releasedate").grow(Priority.NEVER), releaseSearch.conditionBox, releaseSearch.searchField), 0, 0)
			add(releaseView, 0, 1)
			add(trackView, 1, 0, 1, 2)
			maxWidth = Double.MAX_VALUE
			children.forEach { GridPane.setHgrow(it, Priority.ALWAYS) }
		})
		
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
		addLabeled("Keep separate cover arts for ", ComboBox<String>(ImmutableObservableList("Nothing", "Albums", "Albums & Singles")).apply {
			selectionModel.select(DOWNLOADCOVERS())
			selectionModel.selectedIndexProperty().listen { DOWNLOADCOVERS.set(it.toInt()) }
		})
		
		/* TODO EPs to Singles
		val epAsSingle = CheckBox("Treat EPs with less than")
		epAsSingle.isSelected = EPS_TO_SINGLES() > 0
		val epAsSingleAmount = intSpinner(2, 20, EPS_TO_SINGLES().takeIf { it != 0 } ?: 4)
		arrayOf(epAsSingle.selectedProperty(), epAsSingleAmount.valueProperty()).addListener {
			EPS_TO_SINGLES.set(if (epAsSingle.isSelected) epAsSingleAmount.value else 0)
		}
		addRow(epAsSingle, epAsSingleAmount, Label(" Songs as Singles")) */
		
		addRow(CheckBox("Exclude already downloaded Songs").tooltip("Only works if the Patterns and Folders are correctly set")
				.also {
					it.selectedProperty().listen {
						if (it) {
							launch {
								awaitReady()
								releaseView.roots.forEach {
									it.value.internalChildren.removeIf {
										it.value.path().exists()
									}
								}
								trackView.root.internalChildren.removeIf { it.value.path().exists() }
							}
						} else {
							releaseView.root.internalChildren.clear()
							releaseView.roots.clear()
							trackView.root.internalChildren.clear()
							launch {
								releaseView.load()
								trackView.load()
							}
						}
					}
				})
		
		addRow(createButton("Smart select") {
			trackView.checkModel.clearChecks()
			SimpleTask("", "Fetching Tracks for Releases") {
				awaitReady()
				val albums = arrayOf(releaseView.roots["Album"], releaseView.roots["EP"]).filterNotNull()
				albums.forEach { it.isSelected = true }
				val context = newFixedThreadPoolContext(30, "Fetching Tracks for Releases")
				val dont = arrayOf("Album", "EP", "Single")
				val deferred = (albums.flatMap { it.children } + releaseView.roots.filterNot { it.value.value.title in dont }.flatMap { it.value.internalChildren }.filterNot { it.value.isMulti })
						.map {
							async(context) {
								if (!isActive) return@async null
								APIConnection("catalog", "release", it.value.id, "tracks").getTracks()?.map { it.toString().normalised }
							}
						}
				logger.finer("Fetching Tracks for ${deferred.size} Releases")
				val max = deferred.size
				val tracksToExclude = HashSet<String>(max)
				var progress = 0
				for (job in deferred) {
					if (isCancelled) {
						job.cancel()
					} else {
						tracksToExclude.addAll(job.await() ?: continue)
						updateProgress(progress++, max)
					}
				}
				logger.finest { "Tracks to exclude: " + tracksToExclude.joinToString() }
				context.close()
				if (!isCancelled) {
					if (tracksToExclude.isEmpty() && albums.isNotEmpty()) {
						monsterUtilities.showMessage("You seem to have no internet connection at the moment!")
						return@SimpleTask
					}
					onFx {
						trackView.root.internalChildren.forEach {
							(it as CheckBoxTreeItem).isSelected = it.normalisedValue !in tracksToExclude
						}
					}
				}
			}.progressDialog().show()
		}.tooltip("Selects all Albums+EPs and then all Tracks that are not included in these"))
		
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
		launch {
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
		private val items: List<MusicItem>
		private var total: Int
		
		private lateinit var downloader: Job
		private lateinit var tasks: ObservableList<Download>
		
		init {
			releaseView.clearPredicate()
			trackView.clearPredicate()
			val releases: List<MusicItem> = releaseView.checkedItems.filter { it.isLeaf }.map { it.value }
			val tracks: List<MusicItem> = trackView.checkedItems.filter { it.isLeaf }.map { it.value }
			logger.fine("Starting Downloader for ${releases.size} Releases and ${tracks.size} Tracks")
			items = tracks + releases
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
			}.allowExpand(vertical = false)
			add(cancelButton)
			
			val threadSpinner = intSpinner(0, 5) syncTo DOWNLOADTHREADS
			addLabeled("Download Threads", threadSpinner)
			val progressLabel = Label("0 / ${items.size} Errors: 0")
			val progressBar = ProgressBar().allowExpand(vertical = false)
			add(StackPane(progressBar, progressLabel))
			add(log)
			var counter: Job? = null
			var time = 0L
			arrayOf(success, errors).addListener {
				counter?.cancel()
				val s = success.value
				val e = errors.value
				val done = s + e
				val estimatedLength = total * lengths.mapIndexed { index, element -> element * Math.pow(1.6, index.toDouble()) }.sum() / lengths.size.downTo(1).sum()
				progressBar.progress = done / total.toDouble()
				if (done == total)
					progressLabel.text = "$done / $total Errors: $e"
				else
					counter = launch {
						val estimate = ((estimatedLength / lengths.sum() + total / done - 2) * timer.time() / 1000).roundToLong()
						time = if (time > 0) (time * 9 + estimate) / 10 else estimate
						logger.finest("Estimate: ${formatTimeDynamic(estimate, estimate.coerceAtLeast(60))} Weighed: ${formatTimeDynamic(time, time.coerceAtLeast(60))}")
						while (time > 0) {
							onFx {
								progressLabel.text = "$done / $total Errors: $e - Estimated time left: " + formatTimeDynamic(time, time.coerceAtLeast(60))
							}
							delay(1, TimeUnit.SECONDS)
							time--
						}
					}
			}
			
			val taskView = TaskProgressView<Download>()
			val thumbnailCache = Cache<String, Image>()
			taskView.setGraphicFactory {
				ImageView(thumbnailCache.getOrPut(it.coverUrl) {
					val url = "$it?image_width=64".replace(" ", "%20")
					Image(HttpClientBuilder.create().build().execute(HttpGet(url)).entity.content)
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
		private val success = SimpleIntegerProperty()
		private val errors = SimpleIntegerProperty()
		private fun startDownload() {
			downloader = launch {
				log("Download started")
				for (item in items) {
					val download = item.downloadTask()
					download.setOnCancelled {
						log("Cancelled $item")
						total--
					}
					download.setOnSucceeded {
						lengths.add(download.length.div(10000).toDouble())
						success.value++
						log("Downloaded $item")
					}
					download.setOnFailed {
						errors.value++
						val exception = download.exception
						logger.throwing("Downloader", "download", exception)
						log("Error downloading ${download.item}: " + if (exception is ParserException) exception.message else exception.str())
					}
					var added = false
					try {
						globalThreadPool.execute(download)
						onFx { tasks.add(download); added = true }
					} catch (e: Throwable) {
						logger.throwing("TabDownloader", "downloader", e)
						log("Could not start download for $item: $e")
					}
					while (!added || tasks.size >= DOWNLOADTHREADS())
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
