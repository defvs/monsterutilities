package xerus.monstercat.tabs

import javafx.collections.FXCollections
import javafx.scene.control.*
import javafx.scene.input.Clipboard
import javafx.scene.input.DataFormat
import javafx.scene.layout.GridPane
import javafx.scene.paint.Color
import javafx.scene.paint.Paint
import javafx.stage.Stage
import javafx.stage.StageStyle
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.mime.HttpMultipartMode
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.impl.client.HttpClientBuilder
import org.controlsfx.validation.*
import xerus.ktutil.*
import xerus.ktutil.javafx.*
import xerus.ktutil.javafx.properties.*
import xerus.ktutil.javafx.ui.App
import xerus.ktutil.javafx.ui.createAlert
import xerus.monstercat.*
import xerus.monstercat.downloader.DownloaderSettings
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TabSettings : VTab() {
	
	init {
		addButton("Show Changelog", { monsterUtilities.showChangelog() })
		addButton("Show Intro Dialog", { monsterUtilities.showIntro() })
		addButton("Send Feedback", { feedback() })
		addButton("Check for Updates", { monsterUtilities.checkForUpdate(true) })
		
		
		val startTab = ComboBox(FXCollections.observableArrayList("Previous"))
		addLabeled("Startup Tab", startTab)
		
		addLabeled("Skin", ComboBox(ImmutableObservableList(*Skins.availableSkins)).apply {
			valueProperty().bindBidirectional(Settings.SKIN)
		})
		val slider = Slider(0.0, 255.0, Settings.GENRECOLORS().toDouble())
		Settings.GENRECOLORS.dependOn(slider.valueProperty()) { it.toInt() }
		addLabeled("Genre color intensity", slider)
		
		addRow(CheckBox("Enable Cache").bind(Settings.ENABLECACHE))
		addRow(CheckBox("Update automatically").bind(Settings.AUTOUPDATE))
		
		addRow(
				createButton("Quick restart", {
					Settings.refresh()
					DownloaderSettings.refresh()
					App.restart()
				}).apply { prefWidth = 100.0 },
				createButton("Reset", {
					App.stage.createAlert(Alert.AlertType.WARNING, content = "Are you sure you want to RESET ALL SETTINGS?", buttons = *arrayOf(ButtonType.YES, ButtonType.CANCEL)).apply {
						initStyle(StageStyle.UTILITY)
						resultProperty().listen {
							if (it.buttonData == ButtonBar.ButtonData.YES) {
								try {
									Settings.reset()
									DownloaderSettings.reset()
									cachePath.delete()
								} catch (e: Exception) {
									monsterUtilities.showError(e)
								}
								App.restart()
							}
						}
						show()
					}
				}).apply {
					prefWidth = 100.0
					textFillProperty().bind(ImmutableObservable<Paint>(Color.hsb(0.0, 1.0, 0.8)))
				}
		)
		onJFX {
			startTab.items.addAll(monsterUtilities.tabs.map { it.tabName })
			val selectedTab = monsterUtilities.tabPane.selectionModel.selectedItemProperty()
			startTab.valueProperty().bindBidirectional(Settings.STARTUPTAB)
			Settings.LASTTAB.dependOn(selectedTab) { it.text }
		}
	}
	
	lateinit var dialog: Dialog<Pair<String, String>>
	fun feedback() {
		dialog = Dialog<Pair<String, String>>().apply {
			(dialogPane.scene.window as Stage).initWindowOwner(App.stage)
			val send = ButtonType("Send", ButtonBar.ButtonData.YES)
			dialogPane.buttonTypes.addAll(send, ButtonType.CANCEL)
			title = "Send Feedback"
			headerText = null
			val subjectField = TextField()
			val messageArea = TextArea()
			val support = ValidationSupport().apply {
				validationDecorator = minimalValidationDecorator
				registerValidator(subjectField, Validator<String> { control, value ->
					ValidationResult()
							.addMessageIf(control, "Only standard letters and \"?!.,-_\" allowed", Severity.ERROR,
									!Regex("[\\w \\-!.,?]*").matches(value))
							.addMessageIf(control, "Please keep the subject short", Severity.ERROR, value.length > 40)
				})
				registerValidator(messageArea, Validator<String> { control, value ->
					ValidationResult().addMessageIf(control, "The message is too long!", Severity.ERROR, value.length > 100_000)
				})
			}
			dialogPane.lookupButton(send).disableProperty().bind(support.invalidProperty())
			
			dialogPane.content = GridPane().apply {
				spacing(5)
				addRow(0, Label("Subject"), subjectField)
				addRow(1, Label("Message"), messageArea)
			}
			setResultConverter {
				return@setResultConverter if (it.buttonData == ButtonBar.ButtonData.CANCEL_CLOSE)
					null
				else {
					subjectField.text to messageArea.text
				}
			}
		}
		dialog.show()
		dialog.resultProperty().listen { result ->
			result?.run {
				sendFeedback(first, second)
			}
		}
	}
	
	/** @return false if it should be retried */
	private fun sendFeedback(subject: String, message: String) {
		val zipFile = cachePath.resolve("logs.zip").toFile()
		val logs = logDir.listFiles()
		ZipOutputStream(zipFile.outputStream()).use { zip ->
			logs.forEach {
				zip.putNextEntry(ZipEntry(it.name))
				FileInputStream(it).use {
					it.copyTo(zip)
				}
			}
		}
		logger.config("Sending request with subject '$subject' and ${logs.size} logs with a packed size of ${zipFile.length().byteCountString()}")
		val entity = MultipartEntityBuilder.create()
				.setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
				.addTextBody("subject", subject)
				.addTextBody("message", message)
				.addBinaryBody("log", zipFile)
				.build()
		val postRequest = HttpPost("http://monsterutilities.bplaced.net/feedback/")
		postRequest.entity = entity
		val response = HttpClientBuilder.create().build().execute(postRequest)
		val status = response.statusLine
		logger.finer("Response: $status")
		if (status.statusCode == 200) {
			monsterUtilities.showMessage("Your feedback was submitted successfully!")
		} else {
			val retry = ButtonType("Try again", ButtonBar.ButtonData.YES)
			val copy = ButtonType("Copy feedback message to clipboard", ButtonBar.ButtonData.NO)
			App.stage.createAlert(Alert.AlertType.WARNING, content = "Feedback submission failed. Error: ${status.statusCode} - ${status.reasonPhrase}",
					buttons = *arrayOf(retry, copy, ButtonType.CANCEL)).apply {
				resultProperty().listen {
					when (it) {
						retry -> onJFX { dialog.show() }
						copy -> Clipboard.getSystemClipboard().setContent(mapOf(Pair(DataFormat.PLAIN_TEXT, message)))
					}
				}
				show()
			}
		}
	}
	
}
