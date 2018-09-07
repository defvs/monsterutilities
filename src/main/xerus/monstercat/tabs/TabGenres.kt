package xerus.monstercat.tabs

import javafx.beans.InvalidationListener
import javafx.geometry.Insets
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.layout.VBox
import xerus.ktutil.helpers.RoughMap
import xerus.ktutil.helpers.Row
import xerus.ktutil.javafx.*
import xerus.ktutil.javafx.properties.listen
import xerus.ktutil.javafx.ui.FilterableTreeItem
import xerus.monstercat.Settings.GENRECOLORINTENSITY
import xerus.monstercat.Sheets
import xerus.monstercat.logger

val genreColors = RoughMap<String>()
val genreColor = { item: String? ->
	item?.let {
		"-fx-background-color: %s%02x".format(it, GENRECOLORINTENSITY())
	}
}

class TabGenres : FetchTab() {
	
	private val view = TreeTableView<Row>().apply {
		columnResizePolicy = TreeTableView.CONSTRAINED_RESIZE_POLICY
	}
	
	init {
		styleClass("tab-genres")
		val searchField = TextField()
		VBox.setMargin(searchField, Insets(0.0, 0.0, 6.0, 0.0)) // apparently can't set this in css
		val root = FilterableTreeItem(Row())
		root.bindPredicate(searchField.textProperty(), { row, text -> row.subList(0, 3).any { it.contains(text, true) } })
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
					repeat(curLevel - nextLevel) { cur = cur.parent as? FilterableTreeItem<Row> ?: cur.also { logger.warning("$cur should have a parent!") } }
				
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
				if (GENRECOLORINTENSITY() > 0) {
					val hex = cols.find("Hex") ?: return@apply
					itemProperty().listen { style = genreColor(it?.get(hex)) }
				}
			}
		}
		
		val columns = arrayOf<TreeTableColumn<Row, String?>>(
				TreeTableColumn("Genre") { it.value.value.firstOrNull { it.isNotEmpty() } },
				TreeTableColumn("Typical BPM") { it.value.value[cols.findUnsafe("BPM")] },
				TreeTableColumn("Typical Beat") { it.value.value[cols.findUnsafe("Beat")] })
		
		view.columns.addAll(*columns)
		columns.forEach {
			it.isSortable = false
			it.setCellFactory {
				object : TreeTableCell<Row, String?>() {
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
		
		children.add(searchField)
		fill(view)
	}
	
	override fun setPlaceholder(n: Node) = view.setPlaceholder(n)
	
	override fun refreshView() {
		view.refresh()
	}
	
}
