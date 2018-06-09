package xerus.monstercat.tabs

import javafx.application.Platform
import javafx.geometry.Orientation
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
import javafx.scene.control.Slider
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.scene.media.EqualizerBand
import xerus.ktutil.javafx.add
import xerus.ktutil.javafx.bind
import xerus.ktutil.javafx.properties.listen
import xerus.ktutil.javafx.scrollable
import xerus.monstercat.Settings
import xerus.monstercat.api.Player

class TabSound : VTab() {
	var hint: Label? = null
	val eqBox = HBox()
	var eqModel: MutableList<Double> = mutableListOf()
	
	init {
		add(CheckBox("Enable Equalizer").bind(Settings.ENABLEEQUALIZER))
		Player.activePlayer.listen { Platform.runLater(::updateEQBox) }
		
		hint = Label("Play a song to display the controls").run(::add)
		add(eqBox)
	}
	
	private fun updateEQBox() {
		eqBox.children.clear()
		Player.player?.audioEqualizer?.let { eq ->
			// Remove hint once equalizer has been initialized
			hint?.let(children::remove)
			hint = null
			
			// Sync view with equalizer model
			eq.enabledProperty().bind(Settings.ENABLEEQUALIZER)
			var i = 0
			for (band in eq.bands) {
				while (eqModel.size <= i)
					eqModel.add(band.gain)
				val index: Int = i
				eqBox.children.add(createEQBandView(band, eqModel[index], { eqModel[index] = it }))
				i++
			}
		} ?: Settings.ENABLEEQUALIZER.unbind()
	}
	
	private fun createEQBandView(band: EqualizerBand, value: Double, listener: (Double) -> Unit): VBox {
		return VBox().apply {
			children.addAll(
					Slider(EqualizerBand.MIN_GAIN, EqualizerBand.MAX_GAIN, 1.0).apply {
						orientation = Orientation.VERTICAL
						band.gainProperty().bind(valueProperty())
						valueProperty().set(value)
						valueProperty().listen { listener(it as Double) }
					}.scrollable(),
					Label(band.centerFrequency.toString())
			)
		}
	}
}