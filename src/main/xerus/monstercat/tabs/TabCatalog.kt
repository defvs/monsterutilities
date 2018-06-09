package xerus.monstercat.tabs

import com.sun.javafx.scene.control.skin.TableViewSkin
import javafx.collections.ListChangeListener
import javafx.scene.control.Label
import javafx.scene.control.TableColumn
import javafx.scene.control.TableRow
import javafx.scene.input.MouseButton
import javafx.scene.text.Font
import xerus.ktutil.containsAny
import xerus.ktutil.javafx.*
import xerus.ktutil.javafx.properties.listen
import xerus.ktutil.javafx.ui.controls.*
import xerus.ktutil.toLocalDate
import xerus.monstercat.Settings
import xerus.monstercat.api.Player
import xerus.monstercat.api.Playlist
import xerus.monstercat.api.Song
import xerus.monstercat.logger
import java.time.LocalTime
import java.util.*
import kotlin.math.absoluteValue

class TabCatalog : TableTab() {
	
	private val searchView = SearchView<List<String>>()
	private val searchables = searchView.options
	
	init {
		prefWidth = 600.0
		
		table.setRowFactory {
			TableRow<List<String>>().apply {
				val genre = cols.find("Genre") ?: return@apply
				itemProperty().listen {
					style = genreColor(it?.get(genre)?.let {
						genreColors.find(it)
								?: genreColors.find(it.split(' ').map { it.first() }.joinToString(separator = ""))
					}) ?: "-fx-background-color: transparent"
				}
			}
		}
		
		searchables.setAll(MultiSearchable("Any", Type.TEXT, { it }), MultiSearchable("Genre", Type.TEXT, { val c = cols.findAll("genre"); it.filterIndexed { index, _ -> c.contains(index) } }))
		setColumns(Settings.LASTCATALOGCOLUMNS.all)
		
		children.add(searchView)
		predicate.bind(searchView.predicate)
		
		fill(table)
		table.visibleLeafColumns.addListener(ListChangeListener {
			it.next(); Settings.VISIBLECATALOGCOLUMNS.putMulti(*it.addedSubList.map { it.text }.toTypedArray())
		})
		table.setOnMouseClicked { me ->
			if (me.clickCount == 2 && me.button == MouseButton.PRIMARY) {
				val selected = table.selectionModel.selectedItem ?: return@setOnMouseClicked
				Playlist.clearTracks()
				Playlist(Song(selected[cols.findUnsafe("Track")].trim(), selected[cols.findUnsafe("Artist")]))
				Player.play(selected[cols.findUnsafe("Track")].trim(), selected[cols.findUnsafe("Artist")])
			}
			if (me.clickCount == 1 && me.button == MouseButton.MIDDLE) {
				val selected = table.selectionModel.selectedItem ?: return@setOnMouseClicked
				Playlist(Song(selected[cols.findUnsafe("Track")].trim(), selected[cols.findUnsafe("Artist")]))
			}
		}
	}
	
	private fun setColumns(columns: List<String>) {
		val visibleColumns = Settings.VISIBLECATALOGCOLUMNS()
		val newColumns = ArrayList<TableColumn<List<String>, *>>(columns.size)
		for (colName in columns) {
			val existing = table.columns.find { it.text == colName }
			if (existing != null) {
				newColumns.add(existing)
				continue
			}
			try {
				val colValue = { list: List<String> -> cols.find(colName)?.let { list[it] }.also { if (it == null) logger.warning("Column $colName not found!") } }
				val col = when {
					colName.contains("bpm", true) ->
						SearchableColumn(colName, Type.INT, { colValue(it)!!.toIntOrNull() }, colValue::invoke)
					colName.contains("date", true) ->
						SearchableColumn(colName, Type.DATE, converter@{ colValue(it)!!.toLocalDate() }, colValue::invoke)
					colName.containsAny("time", "length") ->
						SearchableColumn(colName, Type.LENGTH, converter@{
							val split = colValue(it)!!.split(":").map {
								it.toIntOrNull() ?: return@converter null
							}; LocalTime.of(0, split[0], split[1])
						}, colValue::invoke)
					colName.contains("genre", true) ->
						TableColumn<List<String>, String>(colName, { colValue(it.value)!! })
					else -> SearchableColumn(colName, Type.TEXT, colValue::invoke)
				}
				if (col is SearchableColumn<List<String>, *>)
					searchables.add(col)
				if (colName.containsAny("Date", "BPM", "Length", "Key", "Brand") || colName == "FR")
					col.style = "-fx-alignment: CENTER"
				newColumns.add(col)
				col.isVisible = visibleColumns.contains(colName, true)
			} catch (e: Exception) {
				logger.throwing("TabCatalog", "column initialization", e)
			}
		}
		table.columns.setAll(newColumns)
	}
	
	override fun sheetToData(sheet: List<List<String>>) {
		super.sheetToData(sheet.drop(1))
		Settings.LASTCATALOGCOLUMNS.putMulti(*cols.keys.toTypedArray())
		onFx {
			val skin = table.skin as TableViewSkin<*>
			setColumns(cols.keys)
			table.columns.forEach { col ->
				@Suppress("UNCHECKED_CAST")
				val widths = ArrayList<Double>(table.items.size)
				for (item in table.items) {
					// improve read font
					widths.add(col.getCellData(item).toString().textWidth(Font.font("System", 11.0)) + 6)
				}
				val avg = widths.average()
				val deviation = widths.sumByDouble { (it - avg).absoluteValue } / widths.size
				//(skin.tableHeaderRow.getColumnHeaderFor(col)?.lookup(".label") as? Label)?.font.printNamed("header font")
				col.prefWidth = avg
				col.minWidth = (avg - deviation)
						.coerceAtLeast((skin.tableHeaderRow.getColumnHeaderFor(col)?.lookup(".label") as? Label)?.textWidth()?.plus(5)
								?.coerceAtLeast(30.0) ?: 30.0)
				col.maxWidth = widths.max()!!
				logger.finest("Catalog column %-11s avg %3.0f +-%2.0f  max %3.0f  min %2.0f".format(col.text, avg, deviation, col.maxWidth, col.minWidth))
			}
		}
	}
	
}
