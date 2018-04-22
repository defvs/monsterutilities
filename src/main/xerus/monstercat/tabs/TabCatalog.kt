package xerus.monstercat.tabs

import com.sun.javafx.scene.control.skin.TableViewSkin
import javafx.collections.ListChangeListener
import javafx.scene.control.Label
import javafx.scene.control.TableRow
import javafx.scene.text.Font
import xerus.ktutil.*
import xerus.ktutil.helpers.KeyNotFoundException
import xerus.ktutil.javafx.TableColumn
import xerus.ktutil.javafx.fill
import xerus.ktutil.javafx.onJFX
import xerus.ktutil.javafx.properties.listen
import xerus.ktutil.javafx.textWidth
import xerus.ktutil.javafx.ui.controls.MultiSearchable
import xerus.ktutil.javafx.ui.controls.SearchView
import xerus.ktutil.javafx.ui.controls.SearchableColumn
import xerus.ktutil.javafx.ui.controls.Type
import xerus.monstercat.Settings
import xerus.monstercat.api.Player
import xerus.monstercat.logger
import java.time.LocalTime
import java.util.*
import kotlin.math.absoluteValue

class TabCatalog : TableTab() {
	
	private val searchView = SearchView<List<String>>()
	private val searchables = searchView.options
	
	init {
		prefWidth = 600.0
		val columns = Settings.LASTCATALOGCOLUMNS.all
		val visibleColumns = Settings.VISIBLECATALOGCOLUMNS()
		
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
		
		searchables.setAll(MultiSearchable("Any", Type.TEXT, { it }), MultiSearchable("Genre", Type.TEXT, { val c = cols.findAll("Genre"); it.filterIndexed { index, _ -> c.contains(index) } }))
		
		for (colName in columns) {
			try {
				val colValue = { list: List<String> -> list[cols.findUnsafe(colName)] }
				val col = when {
					colName.contains("bpm", true) ->
						SearchableColumn(colName, Type.INT, { it[cols.findUnsafe(colName)].toIntOrNull() }, colValue::invoke)
					colName.contains("date", true) ->
						SearchableColumn(colName, Type.DATE, converter@{ colValue(it).toLocalDate() }, colValue::invoke)
					colName.containsAny("time", "length") ->
						SearchableColumn(colName, Type.LENGTH, converter@{
							val split = colValue(it).split(":").map {
								it.toIntOrNull() ?: return@converter null
							}; LocalTime.of(0, split[0], split[1])
						}, colValue::invoke)
					colName.contains("genre", true) ->
						TableColumn<List<String>, String>(colName, { colValue(it.value) })
					else -> SearchableColumn(colName, Type.TEXT, colValue::invoke)
				}
				if (col is SearchableColumn<List<String>, *>)
					searchables.add(col)
				if (arrayOf("Date", "BPM", "Length").any { colName.contains(it, true) } || colName == "L")
					col.style = "-fx-alignment: CENTER"
				table.columns.add(col)
				col.isVisible = visibleColumns.contains(colName)
			} catch (_: KeyNotFoundException) {
			} catch (e: Exception) {
				XerusLogger.throwing("TabCatalog", "column initialization", e)
			}
		}
		children.add(searchView)
		predicate.bind(searchView.predicate)
		
		fill(table)
		table.visibleLeafColumns.addListener(ListChangeListener {
			it.next(); Settings.VISIBLECATALOGCOLUMNS.putMulti(*it.addedSubList.map { it.text }.toTypedArray())
		})
		table.setOnMouseClicked { me ->
			if (me.clickCount == 2) {
				val selected = table.selectionModel.selectedItem ?: return@setOnMouseClicked
				Player.play(selected[cols.findUnsafe("Track")].trim(), selected[cols.findUnsafe("Artist")])
			}
		}
	}
	
	override fun sheetToData(sheet: List<List<String>>) {
		super.sheetToData(sheet)
		Settings.LASTCATALOGCOLUMNS.putMulti(*cols.keys.toTypedArray())
		onJFX {
			val skin = table.skin as TableViewSkin<*>
			table.columns.forEach { col ->
				@Suppress("UNCHECKED_CAST")
				val widths = ArrayList<Double>(table.items.size)
				for (item in table.items) {
					// improve read font
					widths.add(col.getCellData(item).toString().textWidth(Font.font("System", 11.0)) + 6)
				}
				val avg = widths.average()
				val deviation = widths.sumByDouble { (it - avg).absoluteValue } / widths.size
				col.prefWidth = avg
				//(skin.tableHeaderRow.getColumnHeaderFor(col)?.lookup(".label") as? Label)?.font.printNamed("header font")
				col.minWidth = (avg - deviation).coerceAtLeast((skin.tableHeaderRow.getColumnHeaderFor(col)?.lookup(".label") as? Label)?.textWidth()?.plus(5)
						?: 20.0)
				col.maxWidth = widths.max()!!
				logger.finest("Catalog column %-11s avg %3.0f +-%2.0f  max %3.0f  min %2.0f".format(col.text, avg, deviation, col.maxWidth, col.minWidth))
			}
		}
	}
	
}
