package xerus.monstercat.connect

import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.params.ClientPNames
import org.apache.http.client.params.CookiePolicy
import org.apache.http.client.protocol.ClientContext
import org.apache.http.impl.client.BasicCookieStore
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.impl.cookie.BasicClientCookie
import org.apache.http.protocol.BasicHttpContext
import xerus.ktutil.helpers.HTTPQuery
import xerus.monstercat.MCatalog
import xerus.monstercat.downloader.CONNECTSID
import java.io.IOException
import java.io.InputStream
import java.net.URI

/*
private val httpContext = BasicHttpContext()
/** Set connect.sid cookie */
private var connectCookie = BasicClientCookie("connect.sid", CONNECTSID.get()).apply {
	domain = "connect.monstercat.com"
	path = "/"
	val store = BasicCookieStore()
	store.addCookie(this)
	httpContext.setAttribute(ClientContext.COOKIE_STORE, store)
}

fun refreshConnectCookie(): Boolean {
	val connect = CONNECTSID.get()
	if (connect != connectCookie.value) {
		connectCookie = BasicClientCookie("connect.sid", api).apply {
			domain = "connect.monstercat.com"
			path = "/"
			val store = BasicCookieStore()
			store.addCookie(this)
			httpContext.setAttribute(ClientContext.COOKIE_STORE, store)
		}
		return true
	}
	return false
}

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
		response = DefaultHttpClient().apply { params.setParameter(ClientPNames.COOKIE_POLICY, CookiePolicy.BROWSER_COMPATIBILITY) }.execute(httpGet, httpContext)
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
	
}
*/
