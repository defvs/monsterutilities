package xerus.monstercat.tabs

import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Group
import javafx.scene.control.Label
import javafx.scene.control.Tooltip
import javafx.scene.effect.GaussianBlur
import javafx.scene.image.ImageView
import javafx.scene.layout.Background
import javafx.scene.layout.BackgroundFill
import javafx.scene.layout.CornerRadii
import javafx.scene.layout.HBox
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.controlsfx.control.GridCell
import org.controlsfx.control.GridView
import xerus.ktutil.javafx.add
import xerus.ktutil.javafx.createButton
import xerus.ktutil.javafx.onFx
import xerus.ktutil.javafx.properties.SimpleObservable
import xerus.ktutil.javafx.properties.listen
import xerus.monstercat.api.Cache
import xerus.monstercat.api.Covers
import xerus.monstercat.api.response.Release
import xerus.monstercat.monsterUtilities

class TabReleases: StackTab() {
	private var cols = SimpleObservable(3)
	val cellSize: Double
		get() = monsterUtilities.window.width / cols.value - 16.0 * (cols.value + 1)
	
	private val releases = FXCollections.observableArrayList<Release>()
	
	private val gridView = GridView<Release>().apply {
		setCellFactory {
			ReleasesGridCell(this@TabReleases).apply { setOnMouseClicked { showRelease(this.item) } }
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
		StackPane.setAlignment(colEditor, Pos.BOTTOM_LEFT)
	}
	
	private fun showRelease(release: Release) {
		val parent = HBox()
		add(parent)
	}
	
	class ReleasesGridCell(private val context: TabReleases): GridCell<Release>() {
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
}

