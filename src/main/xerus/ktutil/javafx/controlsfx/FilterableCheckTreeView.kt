package xerus.ktutil.javafx.controlsfx

import javafx.collections.ObservableList
import javafx.scene.control.TreeItem
import org.controlsfx.control.CheckTreeView
import xerus.ktutil.javafx.ui.FilterableTreeItem

open class FilterableCheckTreeView<T : Any>(val root: FilterableTreeItem<T>) : CheckTreeView<T>() {
	constructor(rootValue: T) : this(FilterableTreeItem(rootValue))
	
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
