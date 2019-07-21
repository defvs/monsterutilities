@file:Suppress("DEPRECATION")

package xerus.monstercat.api

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.apache.http.HttpResponse
import org.apache.http.client.config.CookieSpecs
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.*
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.BasicCookieStore
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.impl.cookie.BasicClientCookie
import xerus.ktutil.collections.isEmpty
import xerus.ktutil.helpers.HTTPQuery
import xerus.ktutil.javafx.properties.SimpleObservable
import xerus.ktutil.javafx.properties.listen
import xerus.monstercat.Sheets
import xerus.monstercat.api.response.ReleaseResponse
import xerus.monstercat.api.response.Session
import xerus.monstercat.api.response.TrackResponse
import xerus.monstercat.api.response.declaredKeys
import xerus.monstercat.downloader.CONNECTSID
import xerus.monstercat.downloader.QUALITY
import java.io.IOException
import java.io.InputStream
import java.lang.ref.WeakReference
import java.net.URI
import kotlin.reflect.KClass


private val logger = KotlinLogging.logger { }

/** eases query creation to the Monstercat API */
class APIConnection(vararg path: String) : HTTPQuery<APIConnection>() {
	
	private val path: String = "/" + path.joinToString("/")
	val uri: URI
		get() = URI("https", "connect.monstercat.com", path, getQuery(), null)
	
	fun fields(clazz: KClass<*>) = addQuery("fields", *clazz.declaredKeys.toTypedArray())
	fun limit(limit: Int) = replaceQuery("limit", limit.toString())
	fun skip(skip: Int) = replaceQuery("skip", skip.toString())
	
	// Requesting
	
	/** calls [getContent] and uses a [com.google.api.client.json.JsonFactory]
	 * to parse the response onto a new instance of [T]
	 * @return the response parsed onto [T] or null if there was an error */
	fun <T> parseJSON(destination: Class<T>): T? {
		val inputStream = try {
			getContent()
		} catch(e: IOException) {
			return null
		}
		return try {
			Sheets.JSON_FACTORY.fromInputStream(inputStream, destination)
		} catch(e: Exception) {
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

	private var httpRequest: HttpUriRequest? = null
	/** Aborts this connection and thus terminates the InputStream if active */
	fun abort() {
		httpRequest?.abort()
	}
	
	// Direct Requesting

	fun execute(request: HttpUriRequest, context: HttpClientContext? = null) {
		httpRequest = request
		response = executeRequest(request, context)
	}
	
	private var response: HttpResponse? = null
	fun getResponse(): HttpResponse {
		if(response == null)
			execute(HttpGet(uri))
		return response!!
	}
	
	/**@return the content of the response
	 * @throws IOException when the connection fails */
	fun getContent(): InputStream {
		val resp = getResponse()
		if(!resp.entity.isRepeatable)
			response = null
		return resp.entity.content
	}
	
	override fun toString(): String = "APIConnection(uri=$uri)"
	
	companion object {
		val maxConnections = Runtime.getRuntime().availableProcessors().coerceAtLeast(2) * 50
		private var httpClient = createHttpClient(CONNECTSID())
		
		val connectValidity = SimpleObservable(ConnectValidity.NOCONNECTION, true)
		
		private lateinit var connectionManager: PoolingHttpClientConnectionManager
		
		init {
			checkConnectsid(CONNECTSID())
			CONNECTSID.listen { updateConnectsid(it) }
		}
		
		fun executeRequest(request: HttpUriRequest, context: HttpClientContext? = null): CloseableHttpResponse {
			logger.trace { "Connecting to ${request.uri}" }
			return httpClient.execute(request, context)
		}
		
		private fun updateConnectsid(connectsid: String) {
			val oldClient = httpClient
			val manager = connectionManager
			GlobalScope.launch {
				while(manager.totalStats.leased > 0)
					delay(200)
				oldClient.close()
			}
			httpClient = createHttpClient(connectsid)
			checkConnectsid(connectsid)
		}
		
		
		private fun createHttpClient(connectsid: String): CloseableHttpClient {
			return HttpClientBuilder.create()
				.setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build())
				.setDefaultCookieStore(BasicClientCookie("connect.sid", connectsid).run {
					domain = "connect.monstercat.com"
					path = "/"
					BasicCookieStore().also { it.addCookie(this) }
				})
				.setConnectionManager(createConnectionManager())
				.build()
		}
		
		private fun createConnectionManager(): PoolingHttpClientConnectionManager {
			connectionManager = PoolingHttpClientConnectionManager().apply {
				defaultMaxPerRoute = (maxConnections * 0.9).toInt()
				maxTotal = maxConnections
			}
			// trace ConnectionManager stats
			if(logger.isTraceEnabled)
				GlobalScope.launch {
					val name = connectionManager.javaClass.simpleName + "@" + connectionManager.hashCode()
					var stats = connectionManager.totalStats
					val managerWeak = WeakReference(connectionManager)
					while(!managerWeak.isEmpty()) {
						val newStats = managerWeak.get()?.totalStats ?: break
						if(stats.leased != newStats.leased || stats.pending != newStats.pending || stats.available != newStats.available) {
							logger.trace("$name: $newStats")
							stats = newStats
						}
						delay(500)
					}
				}
			return connectionManager
		}
		
		fun checkConnectsid(connectsid: String) {
			if(connectsid.isBlank()) {
				connectValidity.value = ConnectValidity.NOUSER
				return
			}
			GlobalScope.launch {
				val result = getConnectValidity(connectsid)
				if(QUALITY().isEmpty())
					result.session?.settings?.run {
						QUALITY.set(preferredDownloadFormat)
					}
				connectValidity.value = result.validity
			}
		}
		
		private fun getConnectValidity(connectsid: String): ConnectResult {
			val session = APIConnection("api", "self", "session").parseJSON(Session::class.java)
			val validity = when {
				session == null -> ConnectValidity.NOCONNECTION
				session.user == null -> ConnectValidity.NOUSER
				session.user!!.goldService -> {
					Cache.refresh(true)
					ConnectValidity.GOLD
				}
				else -> ConnectValidity.NOGOLD
			}
			return ConnectResult(connectsid, validity, session)
		}
		
		fun login(username: String, password: String): Boolean {
			val connection = APIConnection("v2", "signin")
			val context = HttpClientContext()
			connection.execute(HttpPost(connection.uri).apply {
				setHeader("Accept", "application/json")
				setHeader("Content-type", "application/json")
				entity = StringEntity("""{"email":"$username","password":"$password"}""")
			}, context)

			val code = connection.response?.statusLine?.statusCode
			logger.trace("Login API (POST) returned response code $code")
			if (code !in 200..206) return false
			CONNECTSID.value = (context.cookieStore.cookies.find { it.name == "connect.sid" }?.value ?: return false)
			return true
		}
		
		fun logout() {
			CONNECTSID.clear()
		}
		
		data class ConnectResult(val connectsid: String, val validity: ConnectValidity, val session: Session?)
	}
	
}

enum class ConnectValidity {
	NOUSER, NOGOLD, GOLD, NOCONNECTION
}
