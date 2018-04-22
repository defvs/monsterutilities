package xerus.monstercat.tabs

import javafx.scene.control.Control
import javafx.scene.layout.Pane
import javafx.scene.layout.VBox
import org.controlsfx.validation.decoration.GraphicValidationDecoration

val minimalValidationDecorator = object : GraphicValidationDecoration() {
	override fun applyRequiredDecoration(target: Control?) {}
}

interface BaseTab {
	
	val tabName: String
		get() = javaClass.simpleName.substring(3)
	
	fun asNode() = this as Pane
	
}

abstract class VTab : VBox(), BaseTab {
	init {
		styleClass.add("vtab")
	}
}
