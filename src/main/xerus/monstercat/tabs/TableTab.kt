package xerus.monstercat.tabs

import javafx.collections.transformation.FilteredList
import javafx.collections.transformation.SortedList
import javafx.scene.Node
import javafx.scene.control.TableView

open class TableTab : FetchTab() {

    private val viewdata = FilteredList(data)
    private val sortedData = SortedList(viewdata)
    val predicate = viewdata.predicateProperty()!!.apply { addListener { _ -> showFoundSnackbar() } }

    val table: TableView<List<String>> = TableView<List<String>>(sortedData).apply {
        columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY
        isTableMenuButtonVisible = true
        sortedData.comparatorProperty().bind(comparatorProperty())
    }

    private fun showFoundSnackbar() {
        val rows = viewdata.size
        if (rows == data.size) {
            if (notification.text.get() != snackbarTextCache)
                notification.hide()
        } else {
            showNotification("Found $rows results", false)
        }
    }

    override fun setPlaceholder(n: Node) {
        table.placeholder = n
    }
    
    override fun refreshView() {
        table.refresh()
    }

}