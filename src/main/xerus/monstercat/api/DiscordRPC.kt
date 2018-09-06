package xerus.monstercat.api

import be.bluexin.drpc4k.jna.DiscordRichPresence
import be.bluexin.drpc4k.jna.RPCHandler
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import xerus.ktutil.getResource
import xerus.ktutil.javafx.properties.listen
import xerus.ktutil.javafx.ui.App
import xerus.monstercat.logger

object DiscordRPC {
	
	private val apiKey
		get() = getResource("discordapi")!!.readText()
	
	private val idlePresence = DiscordRPC()
	
	init {
		Player.activeTrack.listen { track ->
			updatePresence(if (track == null) idlePresence else createPresence(track.artistsTitle, track.title))
		}
	}
	
	fun connect(delay: Int = 0) {
		launch {
			delay(delay)
			if (!RPCHandler.connected.get()) {
				RPCHandler.onReady = {
					logger.fine("Discord Rich Presence ready!")
					RPCHandler.updatePresence(idlePresence)
				}
				RPCHandler.onErrored = { errorCode, message -> logger.warning("Discord RPC Error #$errorCode, $message") }
				RPCHandler.connect(apiKey)
				logger.config("Connecting Discord RPC")
				App.stage.setOnHiding { disconnect() }
			}
		}
	}
	
	fun disconnect() {
		if (RPCHandler.connected.get()) {
			logger.config("Disconnecting Discord RPC")
			RPCHandler.disconnect()
			RPCHandler.finishPending()
			logger.finer("Disconnected Discord RPC")
		}
	}
	
	fun updatePresence(presence: DiscordRichPresence) {
		RPCHandler.ifConnectedOrLater {
			RPCHandler.updatePresence(presence)
			if (presence != idlePresence)
				logger.finer("Changed Discord RPC to '${presence.details} - ${presence.state}'")
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
