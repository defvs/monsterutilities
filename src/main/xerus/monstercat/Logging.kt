package xerus.monstercat

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.filter.ThresholdFilter
import ch.qos.logback.classic.spi.Configurator
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.FileAppender
import ch.qos.logback.core.spi.ContextAwareBase
import javafx.scene.control.Alert
import javafx.scene.control.ButtonType
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import xerus.ktutil.currentSeconds
import xerus.ktutil.getStackTraceString
import xerus.ktutil.javafx.properties.listen
import xerus.ktutil.replaceIllegalFileChars
import xerus.monstercat.tabs.TabSettings
import java.io.File
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

val logDir: File
	get() = dataDir.resolve("logs").apply { mkdirs() }

private fun Int.padDate() = toString().padStart(2, '0')
private val logFile = logDir.resolve("log_" +
	"${Calendar.getInstance().let { "${(it.get(Calendar.MONTH) + 1).padDate()}-${it.get(Calendar.DAY_OF_MONTH).padDate()}-${it.get(Calendar.HOUR_OF_DAY).padDate()}" }}_" +
	"${currentSeconds()}.txt")
private var logLevel: Level = Level.WARN

internal fun initLogging(args: Array<String>) {
	args.indexOf("--loglevel").takeIf { it > -1 }?.let {
		logLevel = args.getOrNull(it + 1)?.let { Level.toLevel(it, null) } ?: run {
			println("WARNING: Loglevel argument given without a valid value! Use one of {OFF, ERROR, WARN, INFO, DEBUG, TRACE, ALL}")
			return@let
		}
	}
	val lastPrompt = AtomicInteger(0)
	var agreed = false
	Thread.setDefaultUncaughtExceptionHandler { thread, e ->
		val trace = "$thread: ${e.getStackTraceString()}"
		val rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)
		rootLogger.warn("Uncaught exception in $trace")
		fun sendReport() = GlobalScope.launch {
			TabSettings.sendFeedback(TabSettings.Companion.Feedback("automated report of ${e.javaClass.name.replaceIllegalFileChars()}",
				"Automated log report for uncaught exception in $trace"))
		}
		if(agreed) {
			rootLogger.info("Automatically sending report")
			sendReport()
		} else if(lastPrompt.getAndSet(Int.MAX_VALUE) < currentSeconds() - 30) {
			doWhenReady {
				rootLogger.debug("Showing prompt for submitting uncaught exception")
				showAlert(Alert.AlertType.WARNING, "Internal Error", "An Internal Error has occured: $e",
					"Please let me submit your logs for debugging purposes. Thanks :)", ButtonType.YES, ButtonType.NO, ButtonType("Do not ask me again this session")).resultProperty().listen {
					agreed = false
					when(it) {
						ButtonType.YES -> {
							agreed = true
							sendReport()
						}
						ButtonType.NO -> {
						}
						else -> return@listen
					}
					rootLogger.debug("Send report response: $it")
					lastPrompt.set(currentSeconds())
				}
			}
		}
	}
	
	val logger = KotlinLogging.logger { }
	logger.info("Console loglevel: $logLevel")
	logger.info("Logging to $logFile")
	GlobalScope.launch {
		val logs = logDir.apply { mkdirs() }.listFiles()
		if(logs.size > 10) {
			logs.asSequence().sortedByDescending { it.name }.drop(5).filter {
				it.lastModified() + 50 * 360_000 < System.currentTimeMillis()
			}.also {
				val count = it.count()
				if(count > 0)
					logger.debug("Deleting $count old logs")
			}.forEach { it.delete() }
		}
	}
}

internal class LogbackConfigurator : ContextAwareBase(), Configurator {
	
	override fun configure(lc: LoggerContext) {
		
		val encoder = PatternLayoutEncoder().apply {
			context = lc
			pattern = "%d{HH:mm:ss} [%-25.25thread] %-5level  %-30logger{30} %msg%n"
			start()
		}
		
		val consoleAppender = ConsoleAppender<ILoggingEvent>().apply {
			name = "console"
			context = lc
			this.encoder = encoder
			addFilter(ThresholdFilter().apply {
				setLevel(logLevel.toString())
				start()
			})
			start()
		}
		
		val fileAppender = FileAppender<ILoggingEvent>().apply {
			name = "file"
			file = logFile.toString()
			context = lc
			this.encoder = encoder
			start()
		}
		
		val rootLogger = lc.getLogger(Logger.ROOT_LOGGER_NAME)
		if(logLevel.levelInt < Level.DEBUG_INT)
			rootLogger.level = logLevel
		rootLogger.addAppender(consoleAppender)
		rootLogger.addAppender(fileAppender)
	}
	
}