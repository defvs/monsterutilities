class DownloaderSwing : BasePanel() {
	
	private val messages = arrayOf("Downloaded", "Skipped", "Error downloading", "Cancelled")
	
	private val basepath: Path = DOWNLOADDIR.get()
	private val doSkip: Boolean = MISC.getMulti("fastskip")
	private val mixes: String = ALBUMMIXES.get()
	private val quality = QUALITY.get().replace(' ', '_').toLowerCase()
	
	private lateinit var cancelButton: JButton
	private var canceled: Int = 0
	
	private lateinit var statusLabel: JLabel
	private lateinit var estimatedTime: JLabel
	private var startTime: Long = 0
	
	private var downloadedCount: Int = 0
	private val downloaded = IntArray(3)
	
	private var maxDownloaders: Int = 0
	private var workers: Int = 0
	private var releases: MutableList<Release> = ArrayList()
	
	private var logArea: JLog = JLog()
	
	override fun registerComponents() {
		cancelButton = regButton("Finish", ActionListener {
			canceled++
			updateStatus(cancelButton.text + "ing...")
			if (canceled == 2)
				cancelButton.isEnabled = false
			cancelButton.text = "Cancel"
		}, 0)
		regLabel("Maximum Download Threads", 1)
		statusLabel = regLabel("", 0)
		estimatedTime = regLabel("", 1)
		//new MediaPlayer(new Media())
		maxDownloaders = DOWNLOADTHREADS.int
		val threadSelector = JSpinner(SpinnerNumberModel(maxDownloaders, 0, 15, 1))
		threadSelector.addChangeListener { maxDownloaders = Integer.valueOf(threadSelector.value.toString())!! }
		reg(DownloaderSettingsSwing.DOWNLOADTHREADS.addField(threadSelector), constraints(2).setWeight(0.2))
		
		add(logArea.get(), constraints().setWeight(20.0))
	}
	
	init {
		launch {
			logger.fine("DownloadWorker started")
			
			var limit = LIMIT.int
			if (limit == 0)
				limit = Integer.MAX_VALUE
			val lastDownloaded = LASTDOWNLOADED.get()
			val newestfirst = MISC.getMulti("newestfirst")
			if (doSkip)
				logger.fine("Fast skip activated")
			
			updateStatus("Fetching Releases...")
			val fetchedReleases = ReleaseFetcher.getReleases()
			logger.fine("Retrieved " + fetchedReleases.size + " releases")
			if (!lastDownloaded.isEmpty()) {
				logger.finer("Last Downloaded: " + lastDownloaded)
				var index = -1
				val it = fetchedReleases.listIterator()
				while (it.hasNext() && index == -1) {
					val cur = it.next().id
					if (cur == lastDownloaded)
						index = it.previousIndex()
				}
				if (index == -1) {
					showMessage("Unfortunately we couldn't find where you left off last time!", "Continuator failed", "WARN")
					return@launch
				}
				releases.addAll(if (newestfirst) fetchedReleases.subList(0, index) else fetchedReleases.subList(index, fetchedReleases.size))
				limit -= index
			} else {
				releases.addAll(fetchedReleases)
			}
			val downloadableTypes = TYPES.get()
			logger.finer("Downloadable: " + downloadableTypes)
			releases.removeIf { !it.downloadable || !downloadableTypes.contains(it.type) }
			if (newestfirst)
				Collections.reverse(releases)
			
			if (releases.size > limit)
				releases = releases.subList(0, limit)
			
			// Download
			updateStatus("Downloading " + releases.size + " Releases...")
			MISC.putMulti("fastskip", false)
			var i = 0
			startTime = System.currentTimeMillis()
			while (i < releases.size && canceled == 0) {
				ReleaseDownloader(releases[i]).execute()
				i++
				Tools.sleepWhile(2) { workers >= maxDownloaders }
			}
			Tools.sleepWhile(2) { workers > 0 }
		}.invokeOnCompletion {
			if (canceled == 0)
				LASTDOWNLOADED.put("")
			if (canceled < 2) {
				MISC.putMulti("fastskip", true)
				logger.fine("Fast skip activated because the download wasn't canceled")
			}
			SwingUtilities.invokeLater {
				cancelButton.text = "Back"
				cancelButton.removeActionListener(cancelButton.actionListeners[0])
				cancelButton.addActionListener { goBack() }
				cancelButton.isEnabled = true
			}
			updateStatus("Downloaded %s Releases, skipped %s and encountered %s Errors", downloaded[0], downloaded[1], downloaded[2])
			logger.config("Download finished, downloaded: " + Arrays.toString(downloaded))
		}
	}
	
	private val format = DateFormat.getTimeInstance()
	/**
	 * indicates that the download of a Release finished
	 * Codes: 0 - finished regularly, 1 - skipped, 2 - errored, 3 - canceled
	 */
	private fun downloaded(code: Int, name: Any, additional: String? = null) {
		downloadedCount++
		if (code < downloaded.size)
			downloaded[code] = downloaded[code] + 1
		var message = messages[code] + " " + name
		if (additional != null)
			message += ": " + additional
		log(message)
		if (code < downloaded.size)
			updateStatus("Done: %s / %s", downloadedCount, releases.size)
		if (downloaded[0] > 0) {
			val avgTime = (System.currentTimeMillis() - startTime) / downloaded[0]
			estimatedTime.text = "Estimated time left: " + format.format(Date(avgTime * (releases.size - downloadedCount)))
		}
	}
	
	private fun updateStatus(status: String, vararg args: Any) {
		SwingUtilities.invokeLater { statusLabel.text = String.format(status, *args) }
	}
	
	private inner class ReleaseDownloader(private val release: Release) : SwingWorker<Boolean, Long>() {
		
		private val buffer = ByteArray(1024)
		private var progBar: FileProgressBar? = null
		
		init {
			workers++
			logger.finer("ReleaseDownloader created for " + release)
		}
		
		override fun doInBackground(): Boolean? {
			var path = basepath
			if (release.isMulti) {
				path = path.resolve(release.toString().replaceIllegalFileChars())
				if (doSkip && Files.exists(path)) {
					logger.finer("Skipping $release because it exists already and fast skip is enabled")
					return false
				}
			} else if (release.isType("Podcast"))
				path = path.resolve("Podcasts")
			else if (release.isType("Mixes"))
				path = path.resolve("Mixes")
			Files.createDirectories(path)
			
			SwingUtilities.invokeLater {
				progBar = FileProgressBar(release.toString())
				val l = progBar!!.label
				reg(l, 0)
				reg(progBar!!.progressBar, constraints(1).setWeights(0.6))
				add(progBar!!.progressLabel, constraints(2).setWeight(0.2))
			}
			val con = APIConnection("release", release.id, "download").addQueries("method=download", "type=" + quality)
			val response = con.getResponse()
			SwingUtilities.invokeLater { progBar!!.setMax(response.entity.contentLength) }
			val zis = ZipInputStream(response.entity.content)
			val cis = CountingInputStream(zis)
			try {
				zip@ while (true) {
					val entry = zis.nextEntry ?: break
					if (entry.isDirectory)
						continue
					val name = entry.name.split("/").last()
					if (name == "cover.false" || !release.isMulti && name.contains("cover."))
						continue
					var targetFile = getFile(if (release.isType("Podcast") && name.contains("Music only")) name.substring(0, name.length - 13) else name, path)
					if (!release.isMulti && targetFile.exists() && doSkip)
						return false
					if (name.contains("Album Mix")) {
						when (mixes) {
							"Separate" -> {
								val am = path.parent.resolve("Album Mixes")
								Files.createDirectories(am)
								targetFile = am.resolve(targetFile.name).toFile()
							}
							"Exclude" -> continue@zip
						}
					}
					downloadFile(cis, targetFile)
					if (canceled > 1) {
						downloaded(3, release)
						return null
					}
					if (release.isType("Podcast") && name.contains("Music Only"))
						return true
				}
			} catch (e: Exception) {
				logger.throwing("Downloader", "doInBackground", e)
				downloaded(2, release, e.toString())
				return null
			} finally {
				con.abort()
			}
			return true
		}
		
		override fun process(progress: List<Long>?) {
			progBar!!.updateProgress(Tools.getLast(progress))
		}
		
		@Throws(IOException::class)
		private fun downloadFile(cis: CountingInputStream, file: File) {
			logger.finest("Downloading " + file)
			progBar!!.setText(String.format("%s [%s]", release.toString(), file.name))
			FileOutputStream(file).use { output ->
				var length: Int
				while (true) {
					length = cis.read(buffer)
					if (length < 1) return
					output.write(buffer, 0, length)
					publish(cis.count)
					if (canceled > 1) {
						output.close()
						file.delete()
						return
					}
				}
			}
		}
		
		override fun done() {
			if (progBar != null)
				removeAll(progBar!!.label, progBar!!.progressBar, progBar!!.progressLabel)
			workers--
			try {
				val res = get() ?: return
				LASTDOWNLOADED.put(release.id)
				downloaded(if (res) 0 else 1, release)
			} catch (e: Exception) {
				logger.throwing("Downloader", "DownloadWorker.done", e)
			}
			
		}
	}
	
	fun getFile(name: String, path: Path = basepath): File {
		return if (name.contains("cover.")) path.resolve(name).toFile() else File(String.format("%s.%s", path.resolve(ReleaseFile.toFileName(name)?.replaceIllegalFileChars()), quality.split("_").first()))
	}
	
	private fun goBack() {
		parent.remove(this)
	}
	
	private fun log(text: String, vararg args: Any) {
		SwingUtilities.invokeLater { logArea.log(text, *args) }
	}
	
}