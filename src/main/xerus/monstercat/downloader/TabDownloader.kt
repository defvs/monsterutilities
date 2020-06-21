package xerus.monstercat.downloader

import javafx.beans.Observable
import javafx.beans.property.Property
import javafx.collections.ObservableList
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.KeyCode
import javafx.scene.layout.*
import javafx.scene.text.Text
import javafx.stage.Modality
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
import xerus.ktutil.collections.nullIfEmpty
import xerus.ktutil.helpers.Named
import xerus.ktutil.helpers.ParserException
import xerus.ktutil.javafx.*
import xerus.ktutil.javafx.properties.*
import xerus.ktutil.javafx.ui.App
import xerus.ktutil.javafx.ui.FileChooser
import xerus.ktutil.javafx.ui.FilterableTreeItem
import xerus.ktutil.javafx.ui.controls.LogTextArea
import xerus.ktutil.javafx.ui.controls.SearchRow
import xerus.ktutil.javafx.ui.controls.Searchable
import xerus.ktutil.javafx.ui.controls.alwaysTruePredicate
import xerus.ktutil.javafx.ui.initWindowOwner
import xerus.ktutil.preferences.PropertySetting
import xerus.monstercat.api.APIConnection
import xerus.monstercat.api.ConnectValidity
import xerus.monstercat.api.Covers
import xerus.monstercat.api.response.ArtistRel
import xerus.monstercat.api.response.MusicItem
import xerus.monstercat.api.response.Release
import xerus.monstercat.api.response.Release.Type
import xerus.monstercat.api.response.Track
import xerus.monstercat.globalThreadPool
import xerus.monstercat.monsterUtilities
import xerus.monstercat.tabs.VTab
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue
import xerus.ktutil.javafx.ui.controls.Type as SearchType

private val qualities = arrayOf("mp3_320", "flac", "wav")
val trackPatterns = ImmutableObservableList(
	"%artistsTitle% - %title%",
	"%artists|enumerate% - %title%",
	"%artistsSplit|, % - %titleClean%{ (feat. %feat%)}{ (%extra%)}{ [%remix%]}",
	"%albumArtists% - %albumName% - %trackNumber% %title%",
	"%albumId% %trackNumber% - %artistsSplit|enumerate% - %titleClean%{ (feat. %feat%)}{ (%extra%)}{ [%remix%]}")

class TabDownloader: VTab() {
	
	private val songView = SongView(SONGSORTING)
	
	private val patternValid = SimpleObservable(false)
	private val noItemsSelected = SimpleObservable(true)
	
	private val searchField = TextField()
	private val releaseSearch = SearchRow(Searchable<Release, LocalDate>("Releases", SearchType.DATE) { it.releaseDate.substring(0, 10).toLocalDate() })
	
	init {
		FilterableTreeItem.autoLeaf = false
		
		// Check if no items in the views are selected
		songView.checkedItems.listen {
			noItemsSelected.value = it.size == 0
		}
		
		releaseSearch.conditionBox.select(releaseSearch.conditionBox.items[1])
		(releaseSearch.searchField as DatePicker).run {
			converter = object: StringConverter<LocalDate>() {
				override fun toString(`object`: LocalDate?) = `object`?.toString()
				override fun fromString(string: String?) = string?.toLocalDate()
			}
			if(LASTDOWNLOADTIME() > 0)
				(releaseSearch.searchField as DatePicker).value =
					LocalDateTime.ofEpochSecond(LASTDOWNLOADTIME().toLong(), 0, OffsetDateTime.now().offset).toLocalDate()
		}
		
		// Apply filters
		songView.predicate.bind({
			val searchText = searchField.text
			if(releaseSearch.predicate == alwaysTruePredicate && searchText.isEmpty()) null
			else { parent: TreeItem<MusicItem>, value: MusicItem ->
				val release = value as? Release
				parent != songView.root &&
					// Match titles
					(value.toString().contains(searchText, true) ||
						release?.tracks?.any { it.toString().contains(searchText, true) } ?: false) &&
					// Match Releasedate
					release?.let { releaseSearch.predicate.test(it) } ?: false
			}
		}, searchField.textProperty(), releaseSearch.predicateProperty)
		
		// Update Selection whenever filters change
		songView.predicate.listen {
			songView.getRootItems().forEach {
				it.updateSelection()
			}
		}
		
		initialize()
	}
	
	private fun initialize() {
		children.clear()
		songView.load()
		
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
					GridPane.setHgrow(it, if(it is TextField) Priority.ALWAYS else Priority.SOMETIMES)
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
					} catch(e: FieldNotFoundException) {
						patternValid.value = false
						"No such field: " + e.fieldName
					} catch(e: Exception) {
						patternValid.value = false
						monsterUtilities.showError(e, "Error while parsing filename pattern")
						e.toString()
					}
				}
			}
		
		val patternPane = gridPane()
		// todo grow combobox without reducing label size
		patternPane.add(Label("Single Track pattern"),
			0, 0, 1, 2)
		patternPane.add(ComboBox<String>(trackPatterns).apply { isEditable = true; editor.textProperty().bindBidirectional(TRACKNAMEPATTERN) },
			1, 0)
		patternPane.add(patternLabel(TRACKNAMEPATTERN,
			Track().apply {
				title = "Bring The Madness (feat. Mayor Apeshit) (Aero Chord Remix)"
				artistsTitle = "Excision & Pegboard Nerds"
				artists = listOf(ArtistRel("Pegboard Nerds", "Primary"), ArtistRel("Aero Chord", "Remixer"))
				albumName = "Bring The Madness (The Remixes)"
				albumId = "MCEP068"
				trackNumber = 1
				albumArtists = "Excision, Pegboard Nerds"
			}),
			1, 1)
		patternPane.add(Label("Collection Track pattern"),
			0, 2, 1, 2)
		patternPane.add(ComboBox<String>(trackPatterns).apply { isEditable = true; editor.textProperty().bindBidirectional(ALBUMTRACKNAMEPATTERN) },
			1, 2)
		patternPane.add(patternLabel(ALBUMTRACKNAMEPATTERN,
			Track().apply {
				title = "Saving Light (INTERCOM Remix) [feat. HALIENE]"
				artistsTitle = "Gareth Emery & Standerwick"
				artists = listOf(ArtistRel("Gareth Emery", "Primary"), ArtistRel("Standerwick", "Primary"),
					ArtistRel("HALIENE", "Featured"), ArtistRel("INTERCOM", "Remixer"))
				albumName = "Saving Light (The Remixes) [feat. HALIENE]"
				albumId = "MCEP120"
				trackNumber = 3
				albumArtists = "Gareth Emery & Standerwick"
			}),
			1, 3)
		add(patternPane)
		
		// SongView
		add(searchField)
		add(HBox(5.0, Label("Releasedate").grow(Priority.NEVER), releaseSearch.conditionBox, releaseSearch.searchField, Label("Sort by"), createComboBox(SONGSORTING)))
		fill(songView)
		
		// Quality selector
		val buttons = ImmutableObservableList(*qualities.map {
			ToggleButton(it.replace('_', ' ').toUpperCase()).apply {
				userData = it
				isSelected = userData == QUALITY()
			}
		}.toTypedArray())
		add(SegmentedButton(buttons)).toggleGroup.selectedToggleProperty().listen { if(it != null) QUALITY.put(it.userData) }
		QUALITY.listen { buttons.forEach { button -> button.isSelected = button.userData == it } }
		
		// Misc options
		addLabeled("Download separate cover arts for ", createComboBox(DOWNLOADCOVERS))
		addLabeled("Album Mixes", createComboBox(ALBUMMIXES))
		
		addLabeled("Cover art size", Spinner(object: SpinnerValueFactory<Int>() {
			init {
				valueProperty().bindBidirectional(COVERARTSIZE)
			}
			
			override fun increment(steps: Int) {
				for(i in 0 until steps)
					if(value < 8000)
						value *= 2
			}
			
			override fun decrement(steps: Int) {
				for(i in 0 until steps)
					value /= 2
			}
		})).children.addAll(Label("Cover naming pattern"), TextField().bindText(COVERPATTERN))
		
		val epAsSingle = CheckBox("Treat Collections with less than")
		epAsSingle.isSelected = EPSTOSINGLES() > 0
		val epAsSingleAmount = intSpinner(2, 20, EPSTOSINGLES().takeIf { it != 0 } ?: 3)
		arrayOf(epAsSingle.selectedProperty(), epAsSingleAmount.valueProperty()).addListener {
			EPSTOSINGLES.set(if(epAsSingle.isSelected) epAsSingleAmount.value else 0)
		}
		addRow(epAsSingle, epAsSingleAmount, Label(" Songs as Singles"))
		
		addRow(CheckBox("Exclude already downloaded Releases")
			.tooltip("Only works if the Patterns and Folders are correctly set")
			.apply {
				selectedProperty().listen { selected ->
					logger.debug("Exclude already downloaded Releases: $selected")
					songView.onReady {
						if(selected) {
							GlobalScope.launch {
								songView.getRootItems().forEach { root -> root.isExpanded = false }
								var removed = 0
								songView.getRootItems().forEach { root ->
									root.internalChildren.removeIf { item ->
										val result = (item.value as Release).run {
											val folder = downloadFolder()
											if(!folder.exists())
												return@run false
											tracks.all { track ->
												(track.isAlbumMix && ALBUMMIXES() != AlbumMixes.KEEP) ||
													folder.resolve(track.toFileName(isMulti()).addFormatSuffix()).exists()
											}
										}
										if(result) {
											removed++
											songView.checkModel.clearCheck(item)
											logger.trace { "Excluded ${item.value}" }
										}
										result
									}
								}
								logger.debug("Removed $removed already downloaded Release(s)")
							}
						} else {
							songView.load()
						}
					}
				}
			})
		
		addRow(createButton("Smart select") {
			songView.onReady {
				GlobalScope.launch {
					val albumRoots = songView.getRootItems(Type.ALBUM, Type.EP)
					albumRoots.forEach { it.isSelected = true }
					@Suppress("UNCHECKED_CAST")
					val selectedAlbums = albumRoots.flatMap { it.children } as List<FilterableTreeItem<Release>>
					val selectedTracks = selectedAlbums.flatMapTo(ArrayList()) { it.value.tracks }
					val filtered = selectedAlbums.filter {
						val releaseTracks = (it.value as Release).tracks
						if(releaseTracks.all { (selectedTracks.indexOf(it) != selectedTracks.lastIndexOf(it)) }) {
							it.isSelected = false
							val tracks = ArrayList(releaseTracks)
							selectedTracks.removeIf { track -> (track in tracks).also { bool -> if(bool) tracks.remove(track) } }
							return@filter true
						}
						false
					}
					logger.trace { "Filtered out Collections in Smart Select: $filtered" }
					songView.getItemsInCategory(Type.SINGLE)
						?.filter {
							val tracks = it.value.tracks
							if(tracks.size == 1) { // an actual single, check it directly
								val track = tracks.first()
								if(!selectedTracks.contains(track)) {
									it.isSelected = true
									selectedTracks.add(track)
								}
								return@filter false
							} else { // has multiple tracks, check with collections
								return@filter true
							}
						}.orEmpty()
						.plus(songView.getItemsInCategory(Type.MCOLLECTION).orEmpty())
						.nullIfEmpty()
						?.forEach {
							it.children.forEach {
								@Suppress("UNCHECKED_CAST")
								val tr = it as CheckBoxTreeItem<Track>
								if(tr.value !in selectedTracks) {
									tr.isSelected = true
									selectedTracks.add(tr.value)
								}
							}
						}
						?: logger.warn("Smart select did not find any tracks!")
				}
			}
		}.tooltip("Selects all Albums+EPs and then all other Songs that are not included in these"))
		
		addRow(TextField(CONNECTSID()).apply {
			promptText = "connect.sid"
			tooltip = Tooltip("Log in on monstercat.com from your browser, find the cookie \"connect.sid\" from \"connect2.monstercat.com\" and copy the content into here")
			val textListener = textProperty().debounce(400) { text ->
				CONNECTSID.set(text)
			}
			CONNECTSID.listen { textProperty().setWithoutListener(it, textListener) }
			maxWidth = Double.MAX_VALUE
		}.grow(), createButton("?") {
			Alert(Alert.AlertType.NONE, null, ButtonType.OK).apply {
				title = "How to get your connect.sid"
				dialogPane.content = VBox(
					Label("""Log in on monstercat.com from your browser, go to your browser's cookies (usually somewhere in settings), find the cookie "connect.sid" from "connect.monstercat.com" and copy the content into the Textfield. It should start with with "s%3A"."""),
					HBox(Label("If you use Chrome, you can simply paste this into the address bar after logging in:"), TextField().apply {
						isEditable = false
						maxWidth = Double.MAX_VALUE
						text = "chrome://settings/cookies/detail?site=connect.monstercat.com"
						onFx {
							prefWidth = Text(text).let {
								it.font = font
								it.layoutBounds.width + padding.left + padding.right + 2.0
							}
						}
					}),
					Label("Note that your connect.sid might expire at some point, then simply go through these steps again."))
				dialogPane.minHeight = Region.USE_PREF_SIZE
				initWindowOwner(App.stage)
				show()
			}
		}, Button("Checking connection...").apply {
			prefWidth = 400.0
			CONNECTSID.listen {
				checkFx {
					isDisable = true
					text = "Verifying connect.sid..."
				}
				logger.trace("Verifying connect.sid...")
			}
			arrayOf<Observable>(patternValid, noItemsSelected, APIConnection.connectValidity, QUALITY, songView.ready).addListener {
				onFx { refreshDownloadButton(this) }
			}
			if(APIConnection.connectValidity.value != ConnectValidity.NOCONNECTION)
				refreshDownloadButton(this)
		})
	}
	
	private fun refreshDownloadButton(button: Button) {
		updateDownloadButtonAction(button, false, true)
		button.isDisable = true
		
		var hasGold = false
		var valid = false
		var login = false
		val text = when(APIConnection.connectValidity.value) {
			ConnectValidity.NOCONNECTION -> "No connection"
			ConnectValidity.NOUSER -> {
				login = true
				if(CONNECTSID.value.isEmpty())
					"Click to login to Monstercat..."
				else
					"Invalid connect.sid! Click to login to Monstercat..."
			}
			ConnectValidity.NOGOLD -> "No Gold subscription"
			ConnectValidity.GOLD -> {
				hasGold = true
				when {
					!songView.ready.value -> "Fetching Releases..."
					!patternValid.value -> "Invalid naming pattern"
					QUALITY().isEmpty() -> "No format selected"
					noItemsSelected.value -> "No Tracks selected"
					else -> {
						valid = true
						"Start Download"
					}
				}
			}
		}
		
		logger.trace("Setting download button text to $text")
		button.text = text
		button.isDisable = (hasGold && !valid)
		button.tooltip = Tooltip(if(hasGold) "Click to start downloading the selected Tracks" else "Click to connect using your Monstercat.com credentials")
		updateDownloadButtonAction(button, valid, login)
	}
	
	private fun openLoginDialog() {
		val parent = VBox()
		val stage = App.stage.createStage("Login to Monstercat.com", parent)
		
		val emailField = TextField("").apply { promptText = "Email address" }
		val passwordField = PasswordField().apply { promptText = "Password" }
		
		val errorMessage = Label("Wrong username / password").apply { isVisible = false; managedProperty().bind(visibleProperty()) }
		val loadingGif = ImageView(Image("img/loading-16.gif")).apply { isVisible = false }
		
		val buttonHBox = HBox()
		
		fun login() {
			if(emailField.text.isEmpty() || passwordField.text.isEmpty())
				return
			
			buttonHBox.isDisable = true
			errorMessage.isVisible = false
			loadingGif.isVisible = true
			stage.sizeToScene()
			
			GlobalScope.launch {
				val login = APIConnection.login(emailField.text, passwordField.text)
				logger.debug("Login as ${emailField.text} returned $login")
				onFx {
					if(login) {
						stage.close()
					} else {
						errorMessage.isVisible = true
						loadingGif.isVisible = false
						buttonHBox.isDisable = false
						
						stage.sizeToScene()
					}
				}
			}
		}
		passwordField.setOnKeyPressed { if(it.code == KeyCode.ENTER) login() }
		
		parent.children.addAll(emailField, passwordField, errorMessage,
			buttonHBox.apply {
				addButton("Login") {
					login()
				}
				addButton("Cancel") { stage.close() }
				add(loadingGif)
			}
		)
		stage.apply {
			initModality(Modality.WINDOW_MODAL)
			show()
		}
	}
	
	private fun updateDownloadButtonAction(button: Button, valid: Boolean, noConnection: Boolean) {
		when {
			valid -> button.setOnAction { Downloader() }
			noConnection -> button.setOnAction { openLoginDialog() }
			else -> button.setOnAction {
				button.isDisable = true
				button.text = "Verifying connect.sid..."
				logger.trace("Verifying connect.sid...")
				APIConnection.checkConnectsid(CONNECTSID())
			}
		}
	}
	
	inner class Downloader {
		
		private val log = LogTextArea()
		private val items: Map<Release, Collection<Track>>
		
		private lateinit var downloader: Job
		private lateinit var tasks: ObservableList<Download>
		
		init {
			@Suppress("UNCHECKED_CAST")
			items = songView.getRootItems().flatMap { it.internalChildren }.filter {
				val item = it as FilterableTreeItem
				item.isSelected || item.internalChildren.any { (it as CheckBoxTreeItem).isSelected }
			}.associate {
				val release = (it.value as Release)
				release to ((it as FilterableTreeItem<MusicItem>).internalChildren.takeUnless { it.isEmpty() }
					?.filter { (it as CheckBoxTreeItem).isSelected }?.map { it.value as Track } ?: release.tracks)
			}
			onFx {
				buildUI()
				startDownload()
			}
		}
		
		private var tracksLeft = items.values.sumBy { it.size }
		private val state = DownloaderState(items.size)
		/** Stores the average time it took to download a track for each Release */
		private val times = ArrayList<Long>(items.size)
		
		private fun buildUI() {
			children.clear()
			
			val cancelButton = Button("Finish").onClick {
				isDisable = true
				downloader.cancel()
			}
			add(cancelButton.allowExpand(vertical = false))
			
			addLabeled("Download Threads", intSpinner(0, 5) syncWith DOWNLOADTHREADS)
			val progressLabel = Label("0 / ${state.total} Errors: 0")
			val progressBar = ProgressBar()
			add(StackPane(progressBar.allowExpand(vertical = false), progressLabel))
			add(log)
			var counter: Job? = null
			var timeLeft = 0L
			state.listen {
				counter?.cancel()
				val s = state.success
				val e = state.errors
				val done = s + e
				progressBar.progress = done / state.total.toDouble()
				
				if(state.isDone) {
					val text = "$done / ${state.total} Errors: $e"
					logger.debug("Downloader done: $text")
					onFx {
						progressLabel.text = text
					}
				} else {
					counter = GlobalScope.launch {
						val estimate = (tracksLeft * times.average()).toLong() / 1000 / DOWNLOADTHREADS()
						// do not adjust if difference smaller than 8%
						timeLeft = if(timeLeft > 0 && (estimate.toDouble() / timeLeft - 1).absoluteValue < 0.08) timeLeft else estimate
						logger.trace("Estimated time left for $tracksLeft tracks: ${formatTimeDynamic(estimate, estimate.coerceAtLeast(60))}")
						while(timeLeft > 0) {
							onFx {
								progressLabel.text = "$done / ${state.total} Errors: $e - Estimated time left: " + formatTimeDynamic(timeLeft, timeLeft.coerceAtLeast(60))
							}
							delay(TimeUnit.SECONDS.toMillis(1))
							timeLeft--
						}
					}
				}
			}
			
			val taskView = TaskProgressView<Download>()
			taskView.setGraphicFactory {
				ImageView(Covers.getThumbnailImage(it.coverUrl, 64))
			}
			tasks = taskView.tasks
			tasks.listen {
				if(it.isEmpty()) {
					state.done()
					cancelButton.apply {
						setOnMouseClicked { initialize() }
						text = "Back"
						isDisable = false
					}
				}
			}
			fill(taskView)
		}
		
		private fun startDownload() {
			logger.info("Starting Downloader for ${items.size} Releases with $tracksLeft Tracks")
			downloader = GlobalScope.launch {
				log("Download started")
				var queued = 0
				items@ for(item in items) {
					val download: Download = ReleaseDownload(item.key, item.value)
					val tracks = item.value.size
					download.setOnCancelled {
						tracksLeft -= tracks
						state.cancel()
						log("Cancelled ${item.key}")
					}
					download.setOnSucceeded {
						tracksLeft -= tracks
						times.add(download.get() / tracks)
						state.success()
						log("Downloaded ${item.key}")
					}
					download.setOnFailed {
						tracksLeft -= tracks
						val exception = download.exception
						state.error(exception)
						logger.error("$download failed with $exception", exception)
						log("Error downloading ${download.item}: " + if(exception is ParserException) exception.message else exception.str())
					}
					try {
						globalThreadPool.execute(download)
						queued++
						onFx { tasks.add(download); queued-- }
					} catch(exception: Throwable) {
						logger.error("$download could not be started in TabDownloader because of $exception", exception)
						log("Could not start download for $item: $exception")
					}
					while(tasks.size + queued >= DOWNLOADTHREADS())
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

inline fun <reified T> createComboBox(setting: PropertySetting<T>) where T: Enum<T>, T: Named = ComboBox<T>(ImmutableObservableList(*enumValues<T>())).apply {
	converter = object: StringConverter<T>() {
		val values = enumValues<T>().associateBy { it.displayName }
		override fun toString(`object`: T) = `object`.displayName
		override fun fromString(string: String) = values[string]
	}
	selectionModel.select(setting())
	selectionModel.selectedItemProperty().listen { setting.set(it) }
}