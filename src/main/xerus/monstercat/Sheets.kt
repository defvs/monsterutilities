package xerus.monstercat

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.HttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.Sheets.Spreadsheets
import com.google.api.services.sheets.v4.Sheets.Spreadsheets.Values
import xerus.ktutil.getResource
import java.io.IOException

object Sheets {
	
	val JSON_FACTORY: JsonFactory = JacksonFactory.getDefaultInstance()
	val HTTP_TRANSPORT: HttpTransport = GoogleNetHttpTransport.newTrustedTransport()
	
	lateinit var sheets: Spreadsheets
	val values: Values
		get() = sheets.values()
	
	/** Build an authorized Sheets API client service.  */
	internal fun initService(name: String) {
		sheets = Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, null)
			.setApplicationName(name)
			.build()
			.spreadsheets()
	}
	
	val mcatalog = "116LycNEkWChmHmDK2HM2WV85fO3p3YTYDATpAthL8_g"
	val genreSheet = "1xZUWWnll7HzDVmNj_W7cBfz9TTkl-fMMqHZ8derG-Dg"
	fun fetchMCatalogTab(tab: String, range: String? = null): MutableList<List<String>>? {
		var requestRange = tab
		if(!range.isNullOrEmpty())
			requestRange += "!$range"
		return try {
			val request = values.get(mcatalog, requestRange).setKey(getResource("sheets-api-key")?.readText())
			val result = request.execute()
			@Suppress("Unchecked_cast")
			result.getValues() as MutableList<List<String>>
		} catch(e: IOException) {
			null
		}
	}
	
}
