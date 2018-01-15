@file:Suppress("DEPRECATION")

package xerus.monstercat.api

import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.params.ClientPNames
import org.apache.http.client.params.CookiePolicy
import org.apache.http.client.protocol.ClientContext
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.impl.client.BasicCookieStore
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.impl.cookie.BasicClientCookie
import org.apache.http.protocol.BasicHttpContext
import xerus.ktutil.helpers.HTTPQuery
import xerus.ktutil.pair
import xerus.monstercat.MCatalog
import xerus.monstercat.api.response.*
import xerus.monstercat.downloader.CONNECTSID
import xerus.monstercat.downloader.QUALITY
import java.io.IOException
import java.io.InputStream
import java.net.URI

/** eases query creation to the Monstercat API */
class APIConnection(vararg path: String) : HTTPQuery<APIConnection>() {
	
	private val path: String = "/api/" + path.joinToString("/")
	val uri: URI
		get() = URI("https", "connect.monstercat.com", path, getQuery(), null)
	
	fun fields(clazz: Class<*>) = addQuery("fields", *clazz.declaredKeys())
	fun limit(limit: Int) = addQuery("limit", limit.toString())
	
	// Requesting
	
	/** @throws IOException when the connection fails */
	fun <T> parseJSON(destination: Class<T>): T = MCatalog.JSON_FACTORY.fromInputStream(getContent(), destination)
	
	/** @return null when the connection fails, else the parsed result */
	fun getReleases() = try {
		parseJSON(ReleaseResponse::class.java).results.map { it.init() }
	} catch (e: Exception) {
		null
	}
	
	/** @return null when the connection fails, else the parsed result */
	fun getTracks() = try {
		parseJSON(TrackResponse::class.java).results
	} catch (e: Exception) {
		null
	}
	
	/** Aborts this connection and thus terminates the InputStream if active */
	fun abort() = httpGet?.abort()
	
	// Direct Requesting
	
	private var httpGet: HttpGet? = null
	fun execute() {
		httpGet = HttpGet(uri)
		// add Cookie
		val httpContext = BasicHttpContext().apply {
			setAttribute(ClientContext.COOKIE_STORE, BasicClientCookie("connect.sid", CONNECTSID.get()).run {
				domain = "connect.monstercat.com"
				path = "/"
				BasicCookieStore().also { it.addCookie(this) }
			})
		}
		// execute
		response = DefaultHttpClient().apply {
			params.setParameter(ClientPNames.COOKIE_POLICY, CookiePolicy.BROWSER_COMPATIBILITY)
		}.execute(httpGet, httpContext)
	}
	
	private var response: HttpResponse? = null
	fun getResponse(): HttpResponse {
		if (response == null)
			execute()
		return response!!
	}
	
	/** @throws IOException when the connection fails */
	fun getContent(): InputStream {
		val resp = getResponse()
		if (!resp.entity.isRepeatable)
			response = null
		return resp.entity.content
	}
	
	override fun toString(): String = uri.toString()
	
	companion object {
		private var cache: Pair<String, CookieValidity>? = null
		fun checkCookie(): CookieValidity {
			return if (cache == null || cache?.first != CONNECTSID()) {
				val session = APIConnection("self", "session").parseJSON(Session::class.java)
				when {
					session.user == null -> CookieValidity.NOUSER
					session.user!!.goldService -> {
						Releases.refresh(true)
						CookieValidity.GOLD
					}
					else -> CookieValidity.NOGOLD
				}.also {
					if (QUALITY().isEmpty())
						session.settings?.run {
							QUALITY.set(preferredDownloadFormat)
						}
					cache = CONNECTSID().pair { it }
				}
			} else
				cache!!.second
		}
	}
	
}

enum class CookieValidity {
	NOUSER, NOGOLD, GOLD
}
