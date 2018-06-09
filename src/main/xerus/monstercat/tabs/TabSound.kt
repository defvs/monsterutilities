package xerus.monstercat.tabs

import javafx.application.Platform
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.scene.control.*
import javafx.scene.media.AudioEqualizer
import javafx.scene.media.EqualizerBand
import javafx.geometry.Orientation
import xerus.ktutil.javafx.*
import xerus.ktutil.javafx.properties.*
import xerus.monstercat.Settings
import xerus.monstercat.api.Player

class TabSound : VTab() {
	var hint: Label? = null
	var eqBox: HBox? = null
	var eqModel: MutableList<Double> = mutableListOf()
	
	init {
		add(CheckBox("Enable Equalizer").bind(Settings.ENABLEEQUALIZER))
		Player.playerListeners.add { Platform.runLater(::updateEQBox) }
		
		hint = Label("Play a song to display the controls").run(::add)
	}
	
	private fun updateEQBox() {
		eqBox?.let(children::remove)
		eqBox = HBox()
		Player.equalizer?.let {
			// Remove hint once equalizer has been initialized
			hint?.let(children::remove)
			hint = null
			
			// Sync view with equalizer model
			var i = 0
			for (band in it.bands) {
				while (eqModel.size <= i) { eqModel.add(band.gain) }
				val index: Int = i
				eqBox!!.children.add(createEQBandView(band, eqModel[index], { eqModel[index] = it }))
				i++
			}
		}
		add(eqBox!!)
	}
	
	private fun createEQBandView(band: EqualizerBand, value: Double, listener: (Double) -> Unit): VBox {
		return VBox().apply {
			children.addAll(
				Slider(EqualizerBand.MIN_GAIN, EqualizerBand.MAX_GAIN, 1.0).apply {
					setOrientation(Orientation.VERTICAL)
					band.gainProperty().bind(valueProperty())
					valueProperty().set(value)
					valueProperty().listen { listener(it as Double) }
				},
				Label(band.centerFrequency.toString())
			)
		}
	}
}