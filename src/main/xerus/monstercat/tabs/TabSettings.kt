package xerus.monstercat.tabs

import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.scene.control.*
import javafx.scene.input.Clipboard
import javafx.scene.input.DataFormat
import javafx.scene.layout.GridPane
import javafx.scene.paint.Color
import javafx.scene.paint.Paint
import javafx.stage.Stage
import javafx.stage.StageStyle
import javafx.util.StringConverter
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mu.KLogging
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.mime.HttpMultipartMode
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.impl.client.HttpClientBuilder
import org.controlsfx.validation.Severity
import org.controlsfx.validation.ValidationResult
import org.controlsfx.validation.ValidationSupport
import org.controlsfx.validation.Validator
import xerus.ktutil.byteCountString
import xerus.ktutil.javafx.*
import xerus.ktutil.javafx.properties.ImmutableObservable
import xerus.ktutil.javafx.properties.ImmutableObservableList
import xerus.ktutil.javafx.properties.dependOn
import xerus.ktutil.javafx.properties.listen
import xerus.ktutil.javafx.ui.App
import xerus.ktutil.javafx.ui.createAlert
import xerus.monstercat.Settings
import xerus.monstercat.api.Cache
import xerus.monstercat.cacheDir
import xerus.monstercat.dataDir
import xerus.monstercat.downloader.DownloaderSettings
import xerus.monstercat.logDir
import xerus.monstercat.monsterUtilities
import java.awt.Desktop
import java.io.PrintStream
import java.nio.file.attribute.FileTime
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TabSettings: VTab() {
	
	init {
		addButton("Show Changelog") { monsterUtilities.showChangelog() }
		addButton("Show Intro Dialog") { monsterUtilities.showIntro() }
		addButton("Send Feedback") { feedback() }
		
		val startTab = ComboBox(FXCollections.observableArrayList("Previous"))
		onFx {
			startTab.items.addAll(monsterUtilities.tabs.map { it.tabName })
			val selectedTab = monsterUtilities.tabPane.selectionModel.selectedItemProperty()
			startTab.valueProperty().bindBidirectional(Settings.STARTUPTAB)
			Settings.LASTTAB.dependOn(selectedTab) { it.text }
		}
		addLabeled("Startup Tab:", startTab)
		
		addLabeled("Theme:", ComboBox(ImmutableObservableList(*Themes.values())).apply {
			converter = object: StringConverter<Themes>() {
				override fun toString(theme: Themes) = theme.toString().toLowerCase().capitalize()
				override fun fromString(string: String) = Themes.valueOf(string.toUpperCase())
			}
			valueProperty().bindBidirectional(Settings.THEME)
		})
		addLabeled("Genre color intensity", Slider(0.0, 255.0, Settings.GENRECOLORINTENSITY().toDouble()).scrollable(15.0).also { slider ->
			Settings.GENRECOLORINTENSITY.dependOn(slider.valueProperty()) { it.toInt() }
		})
		addLabeled("Background cover intensity", Slider(0.0, 0.6, Settings.BACKRGOUNDCOVEROPACITY()).scrollable(0.1).also { slider ->
			Settings.BACKRGOUNDCOVEROPACITY.dependOn(slider.valueProperty()) { it.toDouble() }
		})
		
		addLabeled("Player Seekbar scroll sensitivity", doubleSpinner(0.0, initial = Settings.PLAYERSCROLLSENSITIVITY()).apply {
			Settings.PLAYERSCROLLSENSITIVITY.bind(valueProperty())
		})
		addLabeled("Player Seekbar height", Slider(0.0, 15.0, Settings.PLAYERSEEKBARHEIGHT()).scrollable(1.5).apply {
			@Suppress("UNCHECKED_CAST")
			Settings.PLAYERSEEKBARHEIGHT.bind(valueProperty() as ObservableValue<out Double>)
		})
		
		addRow(CheckBox("Enable Cache").bind(Settings.ENABLECACHE))
		if(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN))
			addRow(createButton("Open Cache directory") {
				GlobalScope.launch {
					Desktop.getDesktop().open(cacheDir)
				}
			})
		addButton("Check for Updates") { monsterUtilities.checkForUpdate(true) }
		addRow(CheckBox("Check for Updates on startup").bind(Settings.AUTOUPDATE))
		
		val buttonWidth = 160.0
		addRow(
			createButton("Quick Restart") {
				Settings.refresh()
				DownloaderSettings.refresh()
				App.restart()
			}.apply { prefWidth = buttonWidth },
			createButton("Clear cache & Restart") {
				Settings.refresh()
				DownloaderSettings.refresh()
				try {
					Cache.clear()
					App.restart()
				} catch(e: Exception) {
					monsterUtilities.showError(e)
				}
			}.apply { prefWidth = buttonWidth },
			createButton("Reset") {
				App.stage.createAlert(Alert.AlertType.WARNING, content = "Are you sure you want to RESET ALL SETTINGS?", buttons = *arrayOf(ButtonType.YES, ButtonType.CANCEL)).apply {
					initStyle(StageStyle.UTILITY)
					resultProperty().listen {
						if(it.buttonData == ButtonBar.ButtonData.YES) {
							try {
								Settings.clear()
								DownloaderSettings.clear()
								Cache.clear()
							} catch(e: Exception) {
								monsterUtilities.showError(e)
							}
							App.restart()
						}
					}
					show()
				}
			}.apply {
				prefWidth = buttonWidth
				textFillProperty().bind(ImmutableObservable<Paint>(Color.hsb(0.0, 1.0, 0.8)))
			}
		)
	}
	
	fun feedback() {
		val dialog = Dialog<Feedback>().apply {
			(dialogPane.scene.window as Stage).initWindowOwner(App.stage)
			val send = ButtonType("Send", ButtonBar.ButtonData.YES)
			dialogPane.buttonTypes.addAll(send, ButtonType.CANCEL)
			title = "Send Feedback"
			headerText = null
			val subjectField = TextField()
			val messageArea = TextArea()
			messageArea.prefRowCount = 6
			val validation = ValidationSupport().apply {
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
			dialogPane.lookupButton(send).disableProperty().bind(validation.invalidProperty())
			
			dialogPane.content = GridPane().apply {
				spacing(5)
				addRow(0, Label("Subject"), subjectField)
				addRow(1, Label("Message"), messageArea)
			}
			setResultConverter {
				return@setResultConverter if(it.buttonData == ButtonBar.ButtonData.CANCEL_CLOSE)
					null
				else {
					Feedback(subjectField.text, messageArea.text)
				}
			}
		}
		dialog.show()
		dialog.resultProperty().listen { result ->
			logger.trace { "Submitting: $result" }
			result?.let { feedback ->
				val response = sendFeedback(feedback)
				val status = response.statusLine
				logger.debug("Feedback Response: $status")
				if(status.statusCode == 200) {
					monsterUtilities.showMessage("Your feedback was submitted successfully!")
				} else {
					val retry = ButtonType("Try again", ButtonBar.ButtonData.YES)
					val copy = ButtonType("Copy feedback message to clipboard", ButtonBar.ButtonData.NO)
					App.stage.createAlert(Alert.AlertType.WARNING, content = "Feedback submission failed. Error: ${status.statusCode} - ${status.reasonPhrase}",
						buttons = *arrayOf(retry, copy, ButtonType.CANCEL)).apply {
						resultProperty().listen {
							when(it) {
								retry -> onFx { dialog.show() }
								copy -> Clipboard.getSystemClipboard().setContent(mapOf(Pair(DataFormat.PLAIN_TEXT, feedback.message)))
							}
						}
						show()
					}
				}
			}
		}
	}
	
	companion object: KLogging() {
		
		fun sendFeedback(feedback: Feedback): CloseableHttpResponse {
			val zipFile = dataDir.resolve("report.zip")
			System.getProperties().list(PrintStream(cacheDir.resolve("System.properties.txt").outputStream()))
			val files = cacheDir.listFiles() + logDir.listFiles()
			ZipOutputStream(zipFile.outputStream()).use { zip ->
				files.filter { it.isFile && it != zipFile }.forEach { file ->
					val entry = ZipEntry(file.toString().removePrefix(dataDir.toString()).replace('\\', '/').trim('/'))
					entry.lastModifiedTime = FileTime.from(file.lastModified(), TimeUnit.MILLISECONDS)
					zip.putNextEntry(entry)
					file.inputStream().use { it.copyTo(zip) }
				}
			}
			logger.info("Sending feedback '${feedback.subject}' with a packed size of ${zipFile.length().byteCountString()}")
			val entity = MultipartEntityBuilder.create()
				.setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
				.addTextBody("subject", feedback.subject)
				.addTextBody("message", feedback.message)
				.addBinaryBody("log", zipFile)
				.build()
			val postRequest = HttpPost("http://monsterutilities.bplaced.net/feedback/")
			postRequest.entity = entity
			return HttpClientBuilder.create().build().execute(postRequest)
		}
		
		data class Feedback(val subject: String, val message: String)
	}
	
}
