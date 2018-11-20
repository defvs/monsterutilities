package xerus.monstercat.downloader

import xerus.ktutil.javafx.properties.AbstractObservableValue

class DownloaderState(var total: Int, success: Int = 0, errors: Int = 0) : AbstractObservableValue<DownloaderState>() {
	var errors = errors
		private set
	var success = success
		private set
	
	override fun getValue() = this
	
	fun success() {
		success++
		listeners.notifyChange(null, this)
	}
	
	fun error(exception: Throwable) {
		success++
		listeners.notifyChange(null, this)
	}
	
}