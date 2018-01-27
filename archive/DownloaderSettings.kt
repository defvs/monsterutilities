private val DOWNLOADERSETTINGSNODE: Preferences = Tools.suppressErr(Supplier<Preferences> { Preferences.userRoot().node("/xerus/monstercat/downloader") })

enum class DownloaderSettingsSwing constructor(private val key: String, defaultValue: Any = "") : SwingSetting {
	DONWLOADDIR("directory", "Monstercat"),
	TYPES("types"),
	LIMIT("limit", 0),
	ALBUMMIXES("albummixes", "Include"),
	MISC("miscoptions", "fastskip" + multiSeparator + "newestfirst"),
	FILENAMEPATTERN("filenamepattern", TabDownloader.patterns[0]),
	QUALITY("quality", "MP3 V2"),
	CONNECTSID("connect.sid"),
	LASTDOWNLOADED("lastdownloadedRelease"),
	DOWNLOADTHREADS("threads", 3);
	
	private val defaultVal: String = defaultValue.toString()
	
	override fun getName() = key
	
	override fun getDefault() = defaultVal
	
	override fun getPrefs() = DOWNLOADERSETTINGSNODE
	
}