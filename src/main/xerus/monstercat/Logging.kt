package xerus.monstercat

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.filter.LevelFilter
import ch.qos.logback.classic.filter.ThresholdFilter
import ch.qos.logback.classic.spi.Configurator
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.FileAppender
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.spi.ContextAwareBase
import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.launch
import mu.KotlinLogging
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import xerus.ktutil.currentSeconds
import xerus.ktutil.getStackTraceString
import java.io.File

private val logDir: File
	get() = cacheDir.resolve("logs").apply { mkdirs() }
private val logFile = logDir.resolve("log${currentSeconds()}.txt")
private var logLevel: Level = Level.WARN

internal fun initLogging(args: Array<String>) {
	args.indexOf("--loglevel").takeIf { it > -1 }?.let {
		logLevel = args.getOrNull(it + 1)?.let { Level.toLevel(it, null) } ?: run {
			println("WARNING: Loglevel argument given without a valid value! Use one of {OFF, ERROR, WARN, INFO, DEBUG, TRACE, ALL}")
			return@let
		}
	}
	Thread.setDefaultUncaughtExceptionHandler { thread, ex ->
		LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME).warn("Uncaught exception in $thread: ${ex.getStackTraceString()}")
	}
	val logger = KotlinLogging.logger { }
	logger.info("Console loglevel: $logLevel")
	logger.info("Logging to $logFile")
	GlobalScope.launch {
		val logs = logDir.listFiles()
		if (logs.size > 10) {
			logs.asSequence().sortedByDescending { it.name }.drop(5).filter {
				val timestamp = it.nameWithoutExtension.substring(3).toIntOrNull() ?: return@filter true
				timestamp + 200_000 < currentSeconds()
			}.also {
				val count = it.count()
				if (count > 0)
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