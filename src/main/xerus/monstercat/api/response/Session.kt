package xerus.monstercat.api.response

import com.google.api.client.util.Key

/** Session obtained by /self/session */
data class Session(@Key var user: User? = null, @Key var settings: Settings? = null)

/** User infos, included in a session if the connect.sid is valid */
data class User(@Key var hasGold: Boolean = false)

/** User settings, included in a session if the connect.sid is valid */
data class Settings(@Key var preferredDownloadFormat: String = "")
