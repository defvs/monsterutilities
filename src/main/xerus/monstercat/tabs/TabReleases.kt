package xerus.monstercat.tabs

import javafx.animation.FadeTransition
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.Group
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.control.Separator
import javafx.scene.control.Tooltip
import javafx.scene.effect.GaussianBlur
import javafx.scene.image.ImageView
import javafx.scene.input.KeyCode
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.util.Duration
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.controlsfx.control.GridCell
import org.controlsfx.control.GridView
import xerus.ktutil.javafx.*
import xerus.ktutil.javafx.properties.SimpleObservable
import xerus.ktutil.javafx.properties.listen
import xerus.ktutil.nullIfEmpty
import xerus.monstercat.api.Cache
import xerus.monstercat.api.Covers
import xerus.monstercat.api.Player
import xerus.monstercat.api.response.Release
import xerus.monstercat.api.response.Track
import xerus.monstercat.monsterUtilities


class TabReleases: StackTab() {
	private var cols = SimpleObservable(3)
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
	
	init {
		gridView.items = releases
		GlobalScope.launch {
			val releases = Cache.getReleases()
			onFx { this@TabReleases.releases.setAll(releases) }
		}
		val colEditor = Group(HBox(0.0,
			createButton("-") { cols.value = (cols.value - 1).coerceAtLeast(2) },
			createButton("+") { cols.value = (cols.value + 1).coerceAtMost(4) }))
		add(gridView)
		add(colEditor)
		setAlignment(colEditor, Pos.BOTTOM_LEFT)
	}
	
	private fun showRelease(release: Release) {
		val parent = VBox()
		
		val tracks = FXCollections.observableArrayList(release.tracks)
		val tracksView = ListView<Track>(tracks).apply { setCellFactory { TrackListCell() } }
		
		val infoHeader = HBox(16.0,
			ImageView(Covers.getThumbnailImage(release.coverUrl, 256)).apply {
				effect = GaussianBlur(10.0)
				GlobalScope.launch {
					val cover = Covers.getCoverImage(release.coverUrl, 256)
					onFx { image = cover; effect = null }
				}
			},
			Separator(Orientation.VERTICAL)
		)
		infoHeader.fill(VBox(
			Label(release.title).apply { style += "-fx-font-size: 32px; -fx-font-weight: bold;" },
			Label(release.renderedArtists.nullIfEmpty()?.let { "by $it" }
				?: "Various Artists").apply { style += "-fx-font-size: 24px;" },
			HBox(Label(release.releaseDate), Label("${release.tracks.size} tracks")).apply { style += "-fx-font-size: 16px;" },
			HBox(
				buttonWithId("play") { Player.play(release) }.styleClass("releaseButton"),
				buttonWithId("add") { /* TODO : Playlist add when merged */ }.styleClass("releaseButton"),
				buttonWithId("download") { /* TODO : Tick in Downloader tab */ }.styleClass("releaseButton")
			).apply { fill(pos = 0) }
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
		}
		
		FadeTransition(Duration(300.0), parent).apply {
			fromValue = 0.0
			toValue = 1.0
		}.play()
		add(parent).toFront()
		
		setOnKeyPressed {
			if(it.code == KeyCode.ESCAPE) {
				FadeTransition(Duration(300.0), parent).apply {
					fromValue = 1.0
					toValue = 0.0
					setOnFinished { children.remove(parent) }
				}.play()
			}
		}
	}
	
	class ReleaseGridCell(private val context: TabReleases): GridCell<Release>() {
		override fun updateItem(item: Release?, empty: Boolean) {
			super.updateItem(item, empty)
			if(empty || item == null) {
				graphic = null
			} else {
				val coverImage = Covers.getCachedCover(item.coverUrl, 1024, 256)
					?: Covers.getThumbnailImage(item.coverUrl, 256)
				val cover = ImageView(coverImage)
				
				cover.fitHeight = context.cellSize
				cover.fitWidth = context.cellSize
				context.cols.listen {
					cover.fitHeight = context.cellSize
					cover.fitWidth = context.cellSize
				}
				cover.effect = GaussianBlur(5.0)
				graphic = StackPane(cover,
					Label(item.toString()).apply {
						background = Background(BackgroundFill(Color.BLACK.apply { setOpacity(0.6) }, CornerRadii(8.0), Insets.EMPTY))
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
	
	class TrackListCell: ListCell<Track>() {
		override fun updateItem(item: Track?, empty: Boolean) {
			super.updateItem(item, empty)
			if(empty || item == null) graphic = null
			else {
				val parent = HBox()
				parent.add(HBox(
					buttonWithId("play") { Player.playTrack(item) }.styleClass("releaseButton"),
					buttonWithId("add") { /* TODO : Add to playlist once the branch is merged */ }.styleClass("releaseButton")
				).apply { alignment = Pos.CENTER_LEFT })
				parent.fill(HBox(Label(item.toString()) /* TODO : Unlicensable alert */), 0)
				
				graphic = parent
			}
		}
	}
}

