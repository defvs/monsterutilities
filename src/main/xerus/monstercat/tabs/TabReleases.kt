package xerus.monstercat.tabs

import javafx.animation.FadeTransition
import javafx.beans.property.SimpleBooleanProperty
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.Group
import javafx.scene.control.*
import javafx.scene.effect.GaussianBlur
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.util.Duration
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.controlsfx.control.GridCell
import org.controlsfx.control.GridView
import xerus.ktutil.javafx.*
import xerus.ktutil.javafx.properties.SimpleObservable
import xerus.ktutil.javafx.properties.addListener
import xerus.ktutil.javafx.properties.listen
import xerus.ktutil.nullIfEmpty
import xerus.monstercat.api.Cache
import xerus.monstercat.api.Covers
import xerus.monstercat.api.Player
import xerus.monstercat.api.response.Release
import xerus.monstercat.api.response.Track
import xerus.monstercat.monsterUtilities


class TabReleases: StackTab() {
	private var cols = SimpleObservable(2)
	val cellSize: Double
		get() = monsterUtilities.window.width / cols.value - 16.0 * (cols.value + 1)
	
	private val releases = FXCollections.observableArrayList<Release>()
	
	private val gridView = GridView<Release>().apply {
		setCellFactory {
			ReleaseGridCell(this@TabReleases).apply { setOnMouseClicked { showRelease(this.item) } }
		}
		horizontalCellSpacing = 16.0
		verticalCellSpacing = 16.0
		
		fun setCellSize() {
			cellWidth = cellSize
			cellHeight = cellSize
		}
		setCellSize()
		cols.listen { setCellSize() }
		monsterUtilities.window.widthProperty().listen { setCellSize() }
	}
	
	private val listView = ListView<Release>().apply {
		setCellFactory {
			ReleaseListCell().apply { setOnMouseClicked { showRelease(this.item) } }
		}
	}
	
	private val blurLowRes = SimpleBooleanProperty(false)
	
	init {
		gridView.items = releases
		listView.items = releases
		GlobalScope.launch {
			val releases = Cache.getReleases()
			onFx { this@TabReleases.releases.setAll(releases) }
		}
		val gridEditor = HBox(0.0,
			createButton("-") { cols.value = (cols.value - 1).coerceAtLeast(2) },
			createButton("+") { cols.value = (cols.value + 1).coerceAtMost(4) },
			CheckBox("Blur low-res").bind(blurLowRes).tooltip("May affect performance while loading covers, but looks less pixelated")
		)
		val colEditor = Group(
			VBox(
				CheckBox("Grid View").apply { isSelected = false }.onClick {
					gridEditor.isDisable = !isSelected
					val tab = this@TabReleases
					tab.children.removeAll(listView, gridView)
					if(isSelected) {
						tab.children.add(0, gridView)
					}else{
						tab.children.add(0, listView)
					}
				},
				gridEditor
			).apply {
				background = Background(BackgroundFill(Color(0.0, 0.0, 0.0, 0.7), CornerRadii(8.0), Insets.EMPTY))
				padding = Insets(8.0)
			}
		)
		
		val placeholder = Group(HBox(ImageView(Image("img/loading-16.gif")), Label("Loading Releases...")))
		add(placeholder)
		setAlignment(placeholder, Pos.CENTER)
		
		releases.listen {
			onFx {
				if(!it.isNullOrEmpty()) {
					add(listView)
					add(colEditor)
					setAlignment(colEditor, Pos.BOTTOM_LEFT)
					children.remove(placeholder)
				} else {
					children.removeAll(listView, gridView, colEditor)
					add(placeholder)
					setAlignment(placeholder, Pos.CENTER)
				}
			}
		}
		
	}
	
	private fun showRelease(release: Release) {
		val parent = VBox()
		
		val tracks = FXCollections.observableArrayList(release.tracks)
		val tracksView = ListView<Track>(tracks).apply { setCellFactory { TrackListCell() } }
		
		val infoHeader = HBox(16.0,
			ImageView(Covers.getThumbnailImage(release.coverUrl, 256)).apply {
				effect = GaussianBlur(10.0)
				val cachedCover = Covers.getCachedCover(release.coverUrl, 256, 256)
				if(cachedCover != null) {
					image = cachedCover
					effect = null
				} else {
					image = Covers.getThumbnailImage(release.coverUrl, 256)
					GlobalScope.launch {
						val image = Covers.getCover(release.coverUrl, 256).use { Covers.createImage(it, 256) }
						onFx { this@apply.image = image; effect = null }
					}
				}
				setOnMouseClicked { monsterUtilities.viewCover(release.coverUrl) }
			},
			Separator(Orientation.VERTICAL)
		)
		infoHeader.fill(VBox(
			Label(release.title).apply { style += "-fx-font-size: 32px; -fx-font-weight: bold;" },
			Label(release.renderedArtists.nullIfEmpty()?.let { "by $it" }
				?: "Various Artists").apply { style += "-fx-font-size: 24px;" },
			HBox(Label(release.releaseDate), Label("${release.tracks.size} tracks")).apply { style += "-fx-font-size: 16px;" },
			HBox(
				buttonWithId("play") { Player.play(release) },
				buttonWithId("satin-add") { /* TODO : Playlist add when merged */ }, // TODO : Add icon
				buttonWithId("satin-open") { /* TODO : Tick in Downloader tab */ }.tooltip("Show in downloader") // TODO : Save icon
			).id("controls").apply { fill(pos = 0) }
		).apply { fill(pos = 3) })
		
		parent.style += "-fx-background-color: -fx-background;"
		parent.add(infoHeader)
		parent.add(Separator(Orientation.HORIZONTAL))
		parent.fill(tracksView)
		parent.addButton("Back") {
			FadeTransition(Duration(300.0), parent).apply {
				fromValue = 1.0
				toValue = 0.0
				setOnFinished { children.remove(parent) }
			}.play()
		}.apply { isCancelButton = true }
		
		FadeTransition(Duration(300.0), parent).apply {
			fromValue = 0.0
			toValue = 1.0
		}.play()
		add(parent).toFront()
	}
	
	class ReleaseGridCell(private val context: TabReleases): GridCell<Release>() {
		override fun updateItem(item: Release?, empty: Boolean) {
			super.updateItem(item, empty)
			if(empty || item == null) {
				graphic = null
			} else {
				val lowRes = SimpleBooleanProperty(true)
				val cover = ImageView()
				
				val cachedCover = Covers.getCachedCover(item.coverUrl, 256, 256)
				if(cachedCover != null) {
					cover.image = cachedCover
					lowRes.value = false
				} else {
					cover.image = Covers.getThumbnailImage(item.coverUrl, 256)
					lowRes.value = true
					GlobalScope.launch {
						val image = Covers.getCover(item.coverUrl, 256).use { Covers.createImage(it, 256) }
						lowRes.value = false
						onFx { cover.image = image }
					}
				}
				
				cover.fitHeight = context.cellSize
				cover.fitWidth = context.cellSize
				context.cols.listen {
					cover.fitHeight = context.cellSize
					cover.fitWidth = context.cellSize
				}
				
				cover.effect = if(context.blurLowRes.value && lowRes.value) GaussianBlur(5.0) else null
				arrayOf(context.blurLowRes, lowRes).addListener {
					cover.effect = if(context.blurLowRes.value && lowRes.value) GaussianBlur(5.0) else null
				}
				
				graphic = StackPane(cover,
					Label(item.toString()).apply {
						background = Background(BackgroundFill(Color(0.0, 0.0, 0.0, 0.7), CornerRadii(8.0), Insets.EMPTY))
						padding = Insets(8.0)
						translateY = -16.0
					}
				).apply {
					alignment = Pos.BOTTOM_CENTER; tooltip = Tooltip(item.toString())
					if(item.earlyAccess) style += "-fx-border-color: gold; -fx-border-width: 4px; -fx-border-radius: 8px"
				}
			}
		}
	}
	
	class ReleaseListCell: ListCell<Release>() {
		override fun updateItem(item: Release?, empty: Boolean) {
			super.updateItem(item, empty)
			if(empty || item == null) {
				graphic = null
			} else {
				val cover = ImageView(Covers.getThumbnailImage(item.coverUrl, 256)).apply { fitHeight = 64.0; fitWidth = 64.0; }
				graphic = HBox(cover, Label(item.toString()).apply { font = Font(14.0); padding = Insets(16.0) })
			}
		}
	}
	
	class TrackListCell: ListCell<Track>() {
		override fun updateItem(item: Track?, empty: Boolean) {
			super.updateItem(item, empty)
			if(empty || item == null) graphic = null
			else {
				val parent = HBox()
				parent.add(HBox(
					buttonWithId("play") { Player.playTrack(item) },
					buttonWithId("satin-add") { /* TODO : Add to playlist once the branch is merged */ }, // TODO : Add icon
					buttonWithId("satin-open") { /* TODO : Tick in Downloader tab */ }.tooltip("Show in downloader") // TODO: Save icon
				).id("controls").apply { alignment = Pos.CENTER_LEFT })
				parent.fill(HBox(Label(item.toString()) /* TODO : Unlicensable alert */), 0)
				
				graphic = parent
			}
		}
	}
}