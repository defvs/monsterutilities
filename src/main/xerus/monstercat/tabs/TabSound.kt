package xerus.monstercat.tabs

import javafx.geometry.Orientation
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
import javafx.scene.control.Slider
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.scene.media.EqualizerBand
import xerus.ktutil.javafx.*
import xerus.ktutil.javafx.properties.listen
import xerus.monstercat.Settings
import xerus.monstercat.Settings.EQUALIZERBANDS
import xerus.monstercat.api.Player

class TabSound : VTab() {
	private val hint: Label = Label("Play a song to display the controls")
	private val eqBox = HBox()
	var eqModel: MutableList<Double> = mutableListOf()
	
	init {
		addRow(CheckBox("Enable Equalizer").bind(Settings.ENABLEEQUALIZER), createButton("Reset") { resetEQ() })
		Player.activePlayer.listen { onFx(::updateEQBox) }
		children.addAll(hint, eqBox)
	}
	
	fun resetEQ() {
		EQUALIZERBANDS.clear()
		updateEQBox()
	}
	
	private fun updateEQBox() {
		eqBox.children.clear()
		Player.player?.audioEqualizer?.let { eq ->
			// Remove hint once equalizer has been initialized
			children.remove(hint)
			
			// Sync view with equalizer model
			eqModel = if(EQUALIZERBANDS.get().isNotEmpty()) EQUALIZERBANDS.all.map { it.toDouble() }.toMutableList() else MutableList(eq.bands.size) { 0.0 }
			eq.enabledProperty().bind(Settings.ENABLEEQUALIZER)
			eq.bands.forEachIndexed { index, band ->
				eqBox.children.add(createEQBandView(band, eqModel[index]) {
					eqModel[index] = it
					EQUALIZERBANDS.putMulti(*eqModel.map { it.toString() }.toTypedArray())
				})
			}
		} ?: run {
			Settings.ENABLEEQUALIZER.unbind()
			add(hint)
		}
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