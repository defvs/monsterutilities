package xerus.monstercat.downloader

import javafx.beans.InvalidationListener
import javafx.beans.Observable
import javafx.beans.property.Property
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.geometry.Insets
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.*
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import org.controlsfx.control.CheckTreeView
import org.controlsfx.control.SegmentedButton
import org.controlsfx.control.TaskProgressView
import xerus.ktutil.helpers.Cache
import xerus.ktutil.javafx.*
import xerus.ktutil.javafx.properties.*
import xerus.ktutil.javafx.ui.*
import xerus.ktutil.toLocalDate
import xerus.monstercat.api.*
import xerus.monstercat.api.response.Artist
import xerus.monstercat.api.response.Release
import xerus.monstercat.api.response.Track
import xerus.monstercat.logger
import xerus.monstercat.monsterUtilities
import xerus.monstercat.tabs.BaseTab
import java.time.LocalDate

private val qualities = arrayOf("mp3_128", "mp3_v2", "mp3_v0", "mp3_320", "flac", "wav")
val trackPatterns = UnmodifiableObservableList("{artistsTitle} - {title}", "{artists|, } - {title}", "{artists|enumeration} - {title}")
val albumTrackPatterns = UnmodifiableObservableList("{artistsTitle} - {album} - {track} {title}", "{artists|enumeration} - {title} - {album}", *trackPatterns.getArray())

class TabDownloader : VBox(5.0), BaseTab {
	
	private val releaseView = ReleaseView()
	private val trackView = TrackView()
	
	private val patternValid = SimpleObservable(false)
	private val noItemsSelected = SimpleObservable(false)
	
	private val searchField = TextField()
	private val releaseSearch = SearchRow(Searchable<Release, LocalDate>("Releases", Type.DATE, { it.releaseDate.substring(0, 10).toLocalDate() }))
	
	init {
		padding = Insets(5.0)
		
		FilterableTreeItem.autoExpand = true
		FilterableTreeItem.autoLeaf = false
		
		// Check if views are empty
		val itemListener = InvalidationListener { noItemsSelected.value = releaseView.checkedItems.size + trackView.checkedItems.size == 0 }
		releaseView.checkedItems.addListener(itemListener)
		trackView.checkedItems.addListener(itemListener)
		
		initialize()
	}
	
	private fun initialize() {
		children.clear()
		
		// Download directory
		val chooser = FileChooser(App.stage, DOWNLOADDIR().toFile(), null, "Download directory")
		chooser.selectedFile.addListener { _, _, new -> DOWNLOADDIR.set(new.toPath()) }
		add(chooser.hBox)
		
		// Patterns
		val patternPane = gridPane()
		val patternLabel = { property: Property<String>, test: Track ->
			Label().apply {
				textProperty().bindSoft({
					try {
						test.toString(property.value).also { patternValid.value = true }
					} catch (e: NoSuchFieldException) {
						patternValid.value = false
						"No such field: " + e.message
					} catch (e: Exception) {
						patternValid.value = false
						monsterUtilities.showError(e, "Error parsing pattern")
						e.toString()
					}
				}, property)
			}
		}
		patternPane.add(Label("Single naming pattern"),
				0, 0, 1, 2)
		patternPane.add(ComboBox<String>(trackPatterns).apply { isEditable = true; editor.textProperty().bindBidirectional(TRACKNAMEPATTERN) },
				1, 0)
		patternPane.add(patternLabel(TRACKNAMEPATTERN,
				Track(title = "Bring The Madness (feat. Mayor Apeshit) (Aero Chord Remix)", artistsTitle = "Excision & Pegboard Nerds", artists = listOf(Artist("Pegboard Nerds"), Artist("Excision")))),
				1, 1)
		patternPane.add(Label("Album Track naming pattern"),
				0, 2, 1, 2)
		patternPane.add(ComboBox<String>(albumTrackPatterns).apply { isEditable = true; editor.textProperty().bindBidirectional(ALBUMTRACKNAMEPATTERN) },
				1, 2)
		patternPane.add(patternLabel(ALBUMTRACKNAMEPATTERN,
				ReleaseFile("Rogue, Stonebank & Slips & Slurs - Monstercat Uncaged Vol. 1 - 1 Unity", false)),
				1, 3)
		add(patternPane)
		
		// Apply filters
		add(searchField)
		releaseView.predicate.bind({
			if (releaseSearch.predicate == alwaysTruePredicate && searchField.text.isEmpty()) null
			else { parent, value -> parent != releaseView.root && value.toString().contains(searchField.text, true) && releaseSearch.predicate.test(value) }
		}, searchField.textProperty(), releaseSearch.predicateProperty)
		trackView.root.bindPredicate(searchField.textProperty())
		
		// add Views
		val pane = gridPane()
		pane.add(HBox(5.0, Label("Releasedate").priority(Priority.NEVER), releaseSearch.conditionBox, releaseSearch.searchField), 0, 0, 2, 1)
		pane.add(releaseView, 0, 1, 2, 1)
		pane.add(trackView, 2, 0, 2, 2)
		pane.addRow(2
				, Label("Singles Subfolder (root if empty)"), TextField().bindText(SINGLEFOLDER)
				, Label("Tracks Subfolder (root if empty)"), TextField().bindText(TRACKFOLDER))
		pane.maxWidth = Double.MAX_VALUE
		pane.children.forEach { GridPane.setHgrow(it, Priority.ALWAYS) }
		fill(pane)
		
		// Qualities
		val buttons = UnmodifiableObservableList(*qualities.map {
			ToggleButton(it.replace('_', ' ').toUpperCase()).apply {
				userData = it
				isSelected = userData == QUALITY()
			}
		}.toTypedArray())
		add(SegmentedButton(buttons)).toggleGroup.selectedToggleProperty().addListener { _, _, new -> if (new != null) QUALITY.put(new.userData) }
		QUALITY.addListener { _ -> buttons.forEach { it.isSelected = it.userData == QUALITY() } }
		
		// Misc options
		//addRow(CheckBox("Fast skip").apply { selectedProperty().bindBidirectional(FASTSKIP) })
		addRow(TextField().apply {
			promptText = "connect.sid"
			tooltip = Tooltip("Log into monstercat.com from your browser, find the cookie \"connect.sid\" from \"connect.monstercat.com\" and copy the content into here (which usually starts like \"s%3A\")")
			textProperty().bindBidirectional(CONNECTSID)
			maxWidth = Double.MAX_VALUE
		}.priority(), Button("Start Download").onClick { startDownload() }.apply {
			arrayOf<Observable>(patternValid, noItemsSelected, CONNECTSID, QUALITY).forEach {
				it.addListener { refreshButton(this) }
			}
			refreshButton(this)
		})
		
	}
	
	private fun refreshButton(button: Labeled) {
		launch {
			var disable = true
			val text = when (APIConnection.checkCookie()) {
				CookieValidity.NOUSER -> "Invalid connect.sid"
				CookieValidity.NOGOLD -> "Account is not subscribed to Gold"
				CookieValidity.GOLD -> when {
					!patternValid.value -> "Invalid pattern"
					QUALITY().isEmpty() -> "No format selected"
					noItemsSelected.value -> "Nothing selected to download"
					else -> {
						disable = false
						"Start Download"
					}
				}
			}
			onJFX {
				button.textProperty().set(text);
				button.disableProperty().set(disable)
			}
		}
	}
	
	private fun startDownload() {
		children.clear()
		releaseView.clearPredicate()
		trackView.clearPredicate()
		
		val taskView = TaskProgressView<Downloader>()
		val cache = Cache<String, Image>()
		taskView.setGraphicFactory { ImageView(cache.get(it.coverUrl, { Image(it + "?image_width=64", true) })) }
		val job = launch {
			val releases = releaseView.checkedItems.filter { it.isLeaf }.map { it.value }
			if (releases.isNotEmpty()) {
				logger.fine("Starting Downloads for releases $releases")
				for (release in releases) {
					val task = ReleaseDownloader(release)
					var added = false
					onJFX { taskView.tasks.add(task); added = true }
					task.launch()
					while (!added || taskView.tasks.size >= DOWNLOADTHREADS())
						delay(200)
				}
			}
			val tracks = trackView.checkedItems.filter { it.isLeaf }.map { it.value }
			if (tracks.isNotEmpty()) {
				logger.fine("Starting Downloads for tracks $tracks")
				for (track in tracks) {
					val task = TrackDownloader(track)
					var added = false
					onJFX { taskView.tasks.add(task); added = true }
					task.launch()
					while (!added || taskView.tasks.size >= DOWNLOADTHREADS())
						delay(200)
				}
			}
		}
		
		val cancelButton = Button("Finish").onClick { this.disableProperty().set(true); job.cancel() }
		cancelButton.maxWidth = Double.MAX_VALUE
		add(cancelButton)
		
		val threadSpinner = intSpinner(0, initial = DOWNLOADTHREADS())
		DOWNLOADTHREADS.bind(threadSpinner.valueProperty())
		addLabeled("Download Threads", threadSpinner)
		// todo progress indicator
		fill(taskView)
		taskView.tasks.addListener(ListChangeListener {
			if (it.list.size == 0)
				cancelButton.apply {
					setOnMouseClicked { initialize() }
					text = "Back"
					isDisable = false
				}
		})
	}
	
}

class TrackView : FilterableCheckTreeView<Track>(Track(title = "Tracks")) {
	init {
		setOnMouseClicked {
			if (it.clickCount == 2) {
				val selected = selectionModel.selectedItem ?: return@setOnMouseClicked
				if (selected.isLeaf)
					Player.playTrack(selected.value)
			}
		}
		launch {
			Tracks.tracks?.sortedBy { it.toString() }?.forEach {
				root.internalChildren.add(FilterableTreeItem(it))
			}
		}
	}
}

class ReleaseView : FilterableCheckTreeView<Release>(Release(title = "Releases")) {
	init {
		setOnMouseClicked {
			if (it.clickCount == 2) {
				val selected = selectionModel.selectedItem ?: return@setOnMouseClicked
				if (selected.isLeaf)
					Player.play(selected.value)
			}
		}
		launch {
			val roots = arrayOf("Single", "Album", "Monstercat Collection", "Best of", "Podcast", "Mixes").associate { Pair(it, FilterableTreeItem(Release(title = it))) }
			//roots.forEach { _, treeItem -> treeItem.bindPredicate(searchField.textProperty()) }
			Releases.getReleases().forEach {
				roots[it.type]?.internalChildren?.add(FilterableTreeItem(it))
						?: logger.warning("Unknown Release type: ${it.type}")
			}
			onJFX { root.internalChildren.addAll(roots.values) }
		}
	}
}

open class FilterableCheckTreeView<T : Any>(rootValue: T) : CheckTreeView<T>() {
	val root = FilterableTreeItem(rootValue)
	val checkedItems: ObservableList<TreeItem<T>>
		get() = checkModel.checkedItems
	val predicate
		get() = root.predicateProperty()
	
	init {
		root.isExpanded = true
		setRoot(root)
	}
	
	fun clearPredicate() {
		predicate.unbind()
		predicate.set { _, _ -> true }
	}
}

/*
private fun downloaded(code: Int, name: Any, additional: String? = null) {
	downloadedCount++
	if (code < downloaded.size)
		downloaded[code] = downloaded[code] + 1
	var message = messages[code] + " " + name
	if (additional != null)
		message += ": " + additional
	log(message)
	if (code < downloaded.size)
		updateStatus("Done: %s / %s", downloadedCount, releases.size)
	if (downloaded[0] > 0) {
		val avgTime = (System.currentTimeMillis() - startTime) / downloaded[0]
		estimatedTime.text = "Estimated time left: " + format.format(Date(avgTime * (releases.size - downloadedCount)))
	}
}
*/


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