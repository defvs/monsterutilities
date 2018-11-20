@file:Suppress("DEPRECATION")

package xerus.monstercat.api

import mu.KotlinLogging
import org.apache.http.HttpResponse
import org.apache.http.client.config.CookieSpecs
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.BasicCookieStore
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.impl.cookie.BasicClientCookie
import xerus.ktutil.helpers.HTTPQuery
import xerus.monstercat.Sheets
import xerus.monstercat.api.response.*
import xerus.monstercat.downloader.CONNECTSID
import xerus.monstercat.downloader.QUALITY
import java.io.IOException
import java.io.InputStream
import java.net.URI
import kotlin.reflect.KClass

private val logger = KotlinLogging.logger { }

/** eases query creation to the Monstercat API */
class APIConnection(vararg path: String) : HTTPQuery<APIConnection>() {
	
	private val path: String = "/api/" + path.joinToString("/")
	val uri: URI
		get() = URI("https", "connect.monstercat.com", path, getQuery(), null)
	
	fun fields(clazz: KClass<*>) = addQuery("fields", *clazz.declaredKeys.toTypedArray())
	fun limit(limit: Int) = addQuery("limit", limit.toString())
	
	// Requesting
	
	/** calls [getContent] and uses a [com.google.api.client.json.JsonFactory]
	 * to parse the response onto a new instance of [T]
	 * @return the response parsed onto [T] or null if there is an error */
	fun <T> parseJSON(destination: Class<T>): T? {
		val inputStream = try {
			getContent()
		} catch (e: IOException) {
			return null
		}
		return try {
			Sheets.JSON_FACTORY.fromInputStream(inputStream, destination)
		} catch (e: Exception) {
			logger.warn("Error parsing response of $uri: $e", e)
			null
		}
	}
	
	/** @return null when the connection fails, else the parsed result */
	fun getReleases() =
		parseJSON(ReleaseResponse::class.java)?.results?.map { it.init() }
	
	/** @return null when the connection fails, else the parsed result */
	fun getTracks() =
		parseJSON(TrackResponse::class.java)?.results
	
	/** Aborts this connection and thus terminates the InputStream if active */
	fun abort() {
		httpGet?.abort()
	}
	
	// Direct Requesting
	
	private var httpGet: HttpGet? = null
	fun execute() {
		httpGet = HttpGet(uri)
		logger.trace("$this connecting")
		val conf = RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build()
		response = HttpClientBuilder.create().setDefaultRequestConfig(conf).setDefaultCookieStore(cookies()).build().execute(httpGet)
	}
	
	private var response: HttpResponse? = null
	fun getResponse(): HttpResponse {
		if (response == null)
			execute()
		return response!!
	}
	
	/**@return the content of the response
	 * @throws IOException when the connection fails */
	fun getContent(): InputStream {
		val resp = getResponse()
		if (!resp.entity.isRepeatable)
			response = null
		return resp.entity.content
	}
	
	override fun toString(): String = "APIConnection(uri=$uri)"
	
	companion object {
		private var cache: Pair<String, CookieValidity>? = null
		fun checkCookie(): CookieValidity {
			return if (cache == null || cache?.first != CONNECTSID()) {
				val session = APIConnection("self", "session").parseJSON(Session::class.java) ?: return CookieValidity.NOCONNECTION
				when {
					session.user == null -> CookieValidity.NOUSER
					session.user!!.goldService -> {
						Cache.refresh(true)
						CookieValidity.GOLD
					}
					else -> CookieValidity.NOGOLD
				}.also {
					if (QUALITY().isEmpty())
						session.settings?.run {
							QUALITY.set(preferredDownloadFormat)
						}
					cache = CONNECTSID() to it
				}
			} else
				cache!!.second
		}
		
		fun cookies() = BasicClientCookie("connect.sid", CONNECTSID()).run {
			domain = "connect.monstercat.com"
			path = "/"
			BasicCookieStore().also { it.addCookie(this) }
		}
	}
	
}

enum class CookieValidity {
	NOUSER, NOGOLD, GOLD, NOCONNECTION
}