package xerus.monstercat.tabs

import javafx.scene.control.Control
import javafx.scene.layout.Pane
import org.controlsfx.validation.decoration.GraphicValidationDecoration

val minimalDecorator = object : GraphicValidationDecoration() {
	override fun applyRequiredDecoration(target: Control?) {}
}

interface BaseTab {
	
	val tabName: String
		get() = javaClass.simpleName.substring(3)
	
	fun asNode() = this as Pane
	
}
