package xerus.monstercat.api

import be.bluexin.drpc4k.jna.DiscordRichPresence
import be.bluexin.drpc4k.jna.RPCHandler
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import xerus.ktutil.getResource
import xerus.monstercat.logger

object DiscordRPC {
	
	private val apiKey
		get() = getResource("discordapi")!!.readText()
	
	val idlePresence = DiscordRPC()
	
	fun connect(apiKey: String = this.apiKey) {
		if (!RPCHandler.connected.get()) {
			RPCHandler.onErrored = { errorCode, message -> logger.warning("Discord RPC API failed to execute. Error #$errorCode, $message") }
			RPCHandler.onDisconnected = { errorCode, message -> logger.warning("Discord RPC Disconnected, Code #$errorCode, $message") }
			RPCHandler.connect(apiKey)
			logger.config("Connecting Discord RPC.")
		}
	}
	
	fun connectDelayed(millis: Long, apiKey: String = this.apiKey) {
		launch {
			delay(millis)
			connect(apiKey)
			updatePresence(idlePresence)
		}
	}
	
	fun disconnect() {
		if (RPCHandler.connected.get()) {
			logger.config("Disconnecting Discord RPC.")
			RPCHandler.disconnect()
			RPCHandler.finishPending()
		}
	}
	
	fun updatePresence(presence: DiscordRichPresence) {
		RPCHandler.ifConnectedOrLater {
			logger.finer("Connected to RPC, changing presence.")
			RPCHandler.updatePresence(presence)
			logger.fine("Changed presence to '${presence.details}'")
		}
	}
	
	operator fun invoke(details: String? = "", state: String? = null, largeKey: String? = "icon", smallKey: String? = null, smallText: String? = null) =
			DiscordRichPresence {
				if (details != null) this.details = details
				if (state != null) this.state = state
				if (largeKey != null) largeImageKey = largeKey
				if (smallKey != null) smallImageKey = smallKey
				if (smallText != null) smallImageText = smallText
			}
	
	operator fun invoke(artists: String, title: String) =
			invoke("Now Playing", "$artists - $title", "icon", "playing_music", "Playing Music")
	
}
