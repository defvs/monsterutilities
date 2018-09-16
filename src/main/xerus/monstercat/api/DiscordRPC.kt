package xerus.monstercat.api

import be.bluexin.drpc4k.jna.DiscordRichPresence
import be.bluexin.drpc4k.jna.RPCHandler
import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import mu.KotlinLogging
import xerus.ktutil.getResource
import xerus.ktutil.javafx.properties.listen
import xerus.ktutil.javafx.ui.App

object DiscordRPC {
	private val logger = KotlinLogging.logger { }
	
	private val apiKey
		get() = getResource("discordapi")!!.readText()
	
	private val idlePresence = DiscordRPC()
	
	init {
		Player.activeTrack.listen { track ->
			updatePresence(if (track == null) idlePresence else createPresence(track.artistsTitle, track.title))
		}
	}
	
	fun connect(delay: Int = 0) {
		GlobalScope.launch {
			delay(delay)
			if (!RPCHandler.connected.get()) {
				RPCHandler.onReady = {
					logger.info("Ready")
					RPCHandler.updatePresence(idlePresence)
				}
				RPCHandler.onErrored = { errorCode, message -> logger.warn("Discord RPC Error #$errorCode, $message") }
				RPCHandler.connect(apiKey)
				logger.info("Connecting")
				App.stage.setOnHiding { disconnect() }
			}
		}
	}
	
	fun disconnect() {
		if (RPCHandler.connected.get()) {
			logger.info("Disconnecting")
			RPCHandler.disconnect()
			RPCHandler.finishPending()
			logger.debug("Disconnected")
		}
	}
	
	fun updatePresence(presence: DiscordRichPresence) {
		RPCHandler.ifConnectedOrLater {
			RPCHandler.updatePresence(presence)
			if (presence != idlePresence)
				logger.debug("Changed Rich Presence to '${presence.details} - ${presence.state}'")
		}
	}
	
	fun createPresence(artists: String, title: String) =
			invoke(artists, title, "icon", "playing_music", "Playing Music")
	
	operator fun invoke(details: String? = "", state: String? = null, largeKey: String? = "icon", smallKey: String? = null, smallText: String? = null) =
			DiscordRichPresence {
				if (details != null) this.details = details
				if (state != null) this.state = state
				if (largeKey != null) largeImageKey = largeKey
				if (smallKey != null) smallImageKey = smallKey
				if (smallText != null) smallImageText = smallText
			}
	
}
