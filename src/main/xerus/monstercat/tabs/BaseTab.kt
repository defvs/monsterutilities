package xerus.monstercat.tabs

import javafx.scene.control.Control
import javafx.scene.layout.Pane
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import mu.KotlinLogging
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
	protected val logger = KotlinLogging.logger(javaClass.name)
	
	init {
		styleClass.add("vtab")
	}
}

abstract class StackTab : StackPane(), BaseTab {
	protected val logger = KotlinLogging.logger(javaClass.name)
	
	init {
		styleClass.add("vtab")
	}
}