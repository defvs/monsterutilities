package xerus.monstercat.api.response

import com.google.api.client.util.Key

/** Session obtained by self/session */
data class Session(@Key var user: User? = null, @Key var settings: Settings? = null)

/** user infos, included in a session if the connect.sid is valid */
class User(@Key var goldService: Boolean = false)

/** user settings, included in a session if the connect.sid is valid */
class Settings(@Key var preferredDownloadFormat: String = "")
