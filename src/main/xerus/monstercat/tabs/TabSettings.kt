package xerus.monstercat.tabs

import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.scene.control.*
import javafx.scene.input.Clipboard
import javafx.scene.input.DataFormat
import javafx.scene.layout.GridPane
import javafx.scene.paint.Color
import javafx.scene.paint.Paint
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.mime.HttpMultipartMode
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.impl.client.HttpClientBuilder
import org.controlsfx.validation.Severity
import org.controlsfx.validation.ValidationResult
import org.controlsfx.validation.ValidationSupport
import org.controlsfx.validation.Validator
import xerus.ktutil.javafx.*
import xerus.ktutil.javafx.properties.ImmutableObservable
import xerus.ktutil.javafx.properties.ImmutableObservableList
import xerus.ktutil.javafx.properties.dependOn
import xerus.ktutil.javafx.properties.listen
import xerus.ktutil.javafx.ui.App
import xerus.ktutil.javafx.ui.createAlert
import xerus.ktutil.pair
import xerus.monstercat.*
import xerus.monstercat.downloader.DownloaderSettings
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.prefs.BackingStoreException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


class TabSettings : VTab() {
	
	init {
		addButton("Show Changelog", { monsterUtilities.showChangelog() })
		addButton("Show Intro Dialog", { monsterUtilities.showIntro() })
		addButton("Send Feedback", { feedback() })
		addButton("Check for Updates", { monsterUtilities.checkForUpdate(true) })
		
		Settings.ENABLECACHE.listen { selected ->
			logger.fine("Cache " + (if (selected) "en" else "dis") + "abled")
			if (selected) {
				FetchTab.writeCache()
			}
		}
		
		addRow(CheckBox("Enable Cache").bind(Settings.ENABLECACHE)
				//,CheckBox("Download unstable build").bind(Settings.UNSTABLE)
		)
		val startTab = ComboBox(FXCollections.observableArrayList("Previous"))
		addLabeled("Startup Tab", startTab)
		
		Settings.UNSTABLE.addListener(object : ChangeListener<Boolean> {
			override fun changed(o: ObservableValue<out Boolean>, old: Boolean, new: Boolean) {
				if (new) {
					val alert = monsterUtilities.showAlert(Alert.AlertType.CONFIRMATION, title = "Are you sure?",
							content = "Unstable builds contain the latest features and fixes, but may also introduce unexpected bugs, regressions and incompatible changes. Use at your own risk!.\n" +
									"The unstable version can be used alongside the stable one and will forcibly update itself whenever possible.")
					alert.resultProperty().addListener { _ ->
						if (alert.result.buttonData == ButtonBar.ButtonData.YES) {
							monsterUtilities.checkForUpdate(true, true)
						} else {
							Settings.UNSTABLE.removeListener(this)
							Settings.UNSTABLE.set(false)
							Settings.UNSTABLE.addListener(this)
						}
					}
				}
			}
		})
		
		Settings.SKIN.listen { monsterUtilities.scene.applySkin(it) }
		addLabeled("Skin", ComboBox(ImmutableObservableList(*Skins.availableSkins)).apply {
			valueProperty().bindBidirectional(Settings.SKIN)
		})
		val slider = Slider(0.0, 255.0, Settings.GENRECOLORS.get().toDouble())
		slider.minorTickCount = 16
		slider.isSnapToTicks = true
		Settings.GENRECOLORS.dependOn(slider.valueProperty()) { it.toInt() }
		addLabeled("Genre Color strength", slider)
		
		addRow(
				createButton("Quick restart", {
					Settings.refresh()
					DownloaderSettings.refresh()
					App.restart()
				}),
				createButton("Reset all settings", {
					monsterUtilities.showAlert(Alert.AlertType.CONFIRMATION, content = "Are you sure you want to RESET ALL SETTINGS?").resultProperty().listen {
						if (it.buttonData == ButtonBar.ButtonData.YES) {
							try {
								Settings.reset()
								DownloaderSettings.reset()
							} catch (e: BackingStoreException) {
								monsterUtilities.showError(e)
							}
							App.restart()
						}
					}
				}).apply { textFillProperty().bind(ImmutableObservable<Paint>(Color.hsb(0.0, 1.0, 0.8))) }
		)
		onJFX {
			startTab.items.addAll(monsterUtilities.tabs.map { it.tabName })
			val selectedTab = monsterUtilities.tabPane.selectionModel.selectedItemProperty()
			startTab.valueProperty().bindBidirectional(Settings.STARTUPTAB)
			Settings.LASTTAB.dependOn(selectedTab) { it.text }
		}
	}
	
	fun feedback() {
		val dialog = Dialog<Pair<String, String>>().apply {
			initOwner(App.stage)
			val send = ButtonType("Send", ButtonBar.ButtonData.YES)
			dialogPane.buttonTypes.addAll(send, ButtonType.CANCEL)
			title = "Send Feedback"
			headerText = null
			val subject = TextField()
			val message = TextArea()
			val support = ValidationSupport()
			support.validationDecorator = minimalValidationDecorator
			support.registerValidator(subject, Validator<String> { control, value ->
				ValidationResult()
						.addMessageIf(control, "Only standard letters and \"?!.,-_\" allowed", Severity.ERROR,
								!Regex("[\\w \\-!.,?]*").matches(value))
						.addMessageIf(control, "Please keep the subject short", Severity.ERROR, value.length > 40)
			})
			support.registerValidator(message, Validator<String> { control, value ->
				ValidationResult().addMessageIf(control, "The message is too long!", Severity.ERROR, value.length > 100_000)
			})
			dialogPane.lookupButton(send).disableProperty().bind(support.invalidProperty())
			
			dialogPane.content = GridPane().apply {
				spacing(5)
				addRow(0, Label("Subject"), subject)
				addRow(1, Label("Message"), message)
			}
			setResultConverter {
				return@setResultConverter if (it.buttonData == ButtonBar.ButtonData.CANCEL_CLOSE)
					null
				else {
					subject.text.pair { message.text }
				}
			}
		}
		dialog.show()
		dialog.resultProperty().listen { result ->
			result?.run {
				if (!sendFeedback(first, second))
					onJFX { dialog.show() }
			}
		}
	}
	
	/** @return false if it should be retried */
	private fun sendFeedback(subject: String, message: String): Boolean {
		val zipFile = cachePath.resolve("logs.zip").toFile()
		val logs = logDir.listFiles()
		ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
			logs.forEach {
				zip.putNextEntry(ZipEntry(it.name))
				FileInputStream(it).use {
					it.copyTo(zip)
				}
			}
		}
		logger.config("Sending request with subject \"$subject\" and ${logs.size} logs with a packed size of ${zipFile.length()}")
		val entity = MultipartEntityBuilder.create()
				.setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
				.addTextBody("subject", subject)
				.addTextBody("message", message)
				.addBinaryBody("log", zipFile)
				.build()
		val postRequest = HttpPost("http://monsterutilities.bplaced.net/feedback")
		postRequest.entity = entity
		val response = HttpClientBuilder.create().build().execute(postRequest)
		val status = response.statusLine
		logger.finer("Response: $status")
		when (status.statusCode) {
			200 -> monsterUtilities.showMessage("Your feedback was sent successfully!")
			else -> {
				val retry = ButtonType("Try again", ButtonBar.ButtonData.YES)
				val copy = ButtonType("Copy feedback message to clipboard", ButtonBar.ButtonData.NO)
				val type = App.stage.createAlert(Alert.AlertType.WARNING, content = "The feedback wasn't submitted correctly. Error Code: ${status.statusCode}",
						buttons = *arrayOf(retry, copy, ButtonType.CANCEL))
						.showAndWait()
				when (type.orElse(null)) {
					retry -> return false
					copy -> Clipboard.getSystemClipboard().setContent(mapOf(Pair(DataFormat.PLAIN_TEXT, message)))
				}
			}
		}
		return true
	}
	
}
