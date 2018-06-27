package xerus.ktutil.javafx.controlsfx

import javafx.concurrent.Task
import javafx.stage.Stage
import org.controlsfx.dialog.ProgressDialog
import xerus.ktutil.javafx.ui.App

fun <T> Task<T>.progressDialog() = ProgressDialog(this).also {
	it.title = title
	it.headerText = title
	if(App.initialized)
		it.initOwner(App.stage)
	it.stage.setOnCloseRequest { cancel() }
}

val ProgressDialog.stage get() = dialogPane.scene.window as Stage