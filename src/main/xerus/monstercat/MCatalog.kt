package xerus.monstercat

import java.io.IOException

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.HttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.Sheets.Spreadsheets
import com.google.api.services.sheets.v4.Sheets.Spreadsheets.Values

object MCatalog {

    val JSON_FACTORY: JsonFactory = JacksonFactory.getDefaultInstance()
    val HTTP_TRANSPORT: HttpTransport = GoogleNetHttpTransport.newTrustedTransport()!!

    lateinit var sheets: Spreadsheets
    val values: Values
        get() = sheets.values()

    /** Build an authorized Sheets API client service.  */
    internal fun initService(name: String, credential: Credential) {
        sheets = Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName(name)
                .build()
                .spreadsheets()
    }

    private val sheetid = "116LycNEkWChmHmDK2HM2WV85fO3p3YTYDATpAthL8_g"
    fun fetchSheet(tab: String, range: String?): MutableList<List<String>>? {
        var requestRange = tab
        if (!range.isNullOrEmpty())
            requestRange += "!$range"
        return try {
            val request = values.get(sheetid, requestRange).setKey("getResource(sheets-api-key)?.readText()")
            val result = request.execute()
            @Suppress("Unchecked_cast")
            result.getValues() as MutableList<List<String>>
        } catch (e: IOException) {
            null
        }

    }

}
