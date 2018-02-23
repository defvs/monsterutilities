package xerus.monstercat.tabs

import javafx.scene.Node
import xerus.ktutil.javafx.ui.controls.FilteredTableView

open class TableTab : FetchTab() {

    val table = FilteredTableView(data, true)
    val predicate = table.predicate.apply { addListener { _ -> showFoundSnackbar() } }

    private fun showFoundSnackbar() {
        val rows = table.filteredData.size
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