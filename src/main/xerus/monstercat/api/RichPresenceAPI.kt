package xerus.monstercat.api

import be.bluexin.drpc4k.jna.DiscordRichPresence
import be.bluexin.drpc4k.jna.RPCHandler
import xerus.ktutil.getResource
import xerus.monstercat.logger
import java.util.*
import kotlin.concurrent.schedule

object RichPresenceAPI {

    val idlePresencePreset = DiscordRichPresence {
        details = "Idle"
        largeImageKey = "icon"
    }

	fun connect(apiKey: String = getKeyFromRes()) {
		implementErrorHandler()
		if (!RPCHandler.connected.get()) {
			RPCHandler.connect(apiKey)
			logger.info("Connecting Discord RPC.")
		}
	}

	fun getKeyFromRes(): String = getResource("discordapi")!!.readText()

	fun connectDelayed(millis: Long, apiKey: String = getKeyFromRes()) {
		Timer().schedule(millis) {
			connect(apiKey)
			updatePresence(idlePresencePreset)
		}
	}

	fun disconnect() {
		if (RPCHandler.connected.get()) {
			logger.info("Disconnecting Discord RPC.")
			RPCHandler.disconnect()
			RPCHandler.finishPending()
		}
	}

	fun updatePresence(presence: DiscordRichPresence) {
		RPCHandler.ifConnectedOrLater {
			logger.info("Connected to RPC, changing presence.")
			RPCHandler.updatePresence(presence)
			logger.info("Changed presence to ${presence.details}")
		}
	}

	operator fun invoke(details: String? = "Idle", state: String? = null, largeKey: String? = "icon", smallKey: String? = null, smallText: String? = null) = DiscordRichPresence {
			if (details != null) this.details = details
			if (state != null) this.state = state
			if (largeKey != null) largeImageKey = largeKey
			if (smallKey != null) smallImageKey = smallKey
			if (smallText != null) smallImageText = smallText
		}

	operator fun invoke(artists: String, title: String) = invoke("Now Playing", "$artists - $title", "icon", "playing_music", "Playing Music")

	private fun implementErrorHandler() {
		RPCHandler.onErrored = { errorCode, message ->
			logger.warning("Discord RPC API failed to execute. Error #$errorCode, $message")
		}
		RPCHandler.onErrored = { errorCode, message ->
			logger.warning("Discord RPC Disconnected, Code #$errorCode, $message")
		}
	}
}
