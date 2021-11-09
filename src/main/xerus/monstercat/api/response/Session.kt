package xerus.monstercat.api.response

import com.google.api.client.util.Key

/** Session obtained by /self/session */
data class Session(@Key("User") var user: User? = null, @Key("Settings") var settings: Settings? = null)

/** User infos, included in a session if the cid is valid */
data class User(@Key("HasGold") var hasGold: Boolean = false)

/** User settings, included in a session if the cid is valid */
data class Settings(@Key("PreferredFormat") var preferredDownloadFormat: String = "")
