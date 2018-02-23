package xerus.monstercat.tabs

import javafx.beans.InvalidationListener
import javafx.scene.Node
import javafx.scene.control.*
import xerus.ktutil.helpers.RoughMap
import xerus.ktutil.helpers.Row
import xerus.ktutil.javafx.*
import xerus.ktutil.javafx.properties.ConstantObservable
import xerus.ktutil.javafx.properties.listen
import xerus.ktutil.javafx.ui.FilterableTreeItem
import xerus.monstercat.Settings.GENRECOLORS
import xerus.monstercat.logger

val genreColors = RoughMap<String>()
val genreColor = { item: String? ->
	item?.let {
		"-fx-background-color: %s%02x".format(it, GENRECOLORS())
	}
}

class TabGenres : FetchTab() {
	
	private val view = TreeTableView<Row>().apply {
		columnResizePolicy = TreeTableView.CONSTRAINED_RESIZE_POLICY
	}
	
	init {
		val searchfield = TextField()
		val root = FilterableTreeItem(Row())
		root.bindPredicate(searchfield.textProperty(), { row, text -> row.subList(0, 3).any { it.contains(text, true) } })
		view.isShowRoot = false
		data.addListener(InvalidationListener {
			view.root = root
			root.internalChildren.clear()
			var cur = root
			var curLevel = 0
			
			val hex = cols.find("Hex")
			if (hex == null) logger.warning("No hex Column found!")
			
			var style = ""
			for (list in data) {
				if (list.isEmpty())
					continue
				val row = Row(10, *list.toTypedArray())
				val nextLevel = row.indexOfFirst { it.isNotEmpty() }
				if (nextLevel < curLevel)
					repeat(curLevel - nextLevel, { cur = cur.parent as FilterableTreeItem<Row> })
				
				if (hex != null) {
					if (nextLevel == 0) {
						style = row[hex]
						genreColors.put(row[0], style)
					}
					row[hex] = style
				}
				
				val new = FilterableTreeItem(row)
				cur.internalChildren.add(new)
				
				curLevel = nextLevel + 1
				cur = new
			}
			if (hex != null)
				refreshViews()
		})
		
		view.setRowFactory {
			TreeTableRow<Row>().apply {
				if (GENRECOLORS() > 0) {
					val hex = cols.find("Hex") ?: return@apply
					itemProperty().listen { style = genreColor(it?.get(hex)) }
				}
			}
		}
		
		val genre = TreeTableColumn<Row, String>("Genre")
		genre.setCellValueFactory { ConstantObservable(it.value.value.firstOrNull { it.isNotEmpty() }) }
		val bpm = TreeTableColumn<Row, String>("Typical BPM")
		bpm.setCellValueFactory { ConstantObservable(it.value.value[cols.findUnsafe("BPM")]) }
		val beat = TreeTableColumn<Row, String>("Typical Beat")
		beat.setCellValueFactory { ConstantObservable(it.value.value[cols.findUnsafe("Beat")]) }
		val examples = TreeTableColumn<Row, String>("Examples")
		examples.setCellValueFactory { ConstantObservable(it.value.value.filterIndexed { index, s -> !s.isNullOrEmpty() && cols.findAll("Examples").contains(index) }.joinToString(" / ")) }
		view.columns.addAll(genre, bpm, beat, examples)
		
		arrayOf(genre, bpm, beat, examples).forEach {
			it.isSortable = false
			it.setCellFactory {
				object : TreeTableCell<Row, String>() {
					override fun updateItem(item: String?, empty: Boolean) {
						super.updateItem(item, empty)
						if (item == null || empty) {
							text = null
							style = ""
						} else {
							text = item
							val treeItem = this.treeTableRow.treeItem
							font = if (treeItem != null && treeItem.parent === root) {
								font.bold()
							} else {
								style = ""
								if (item.contains("listed in", true))
									font.italic()
								else
									font.format()
							}
						}
					}
				}
			}
		}
		
		children.add(searchfield)
		fill(view)
	}
	
	override fun sheetToData(sheet: List<List<String>>) = super.sheetToData(sheet.subList(1, sheet.size))
	
	override fun setPlaceholder(n: Node) = view.setPlaceholder(n)
	
	override fun refreshView() {
		view.refresh()
	}
	
}
