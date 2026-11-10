package com.sipun.downly.presentation

import android.content.ContentValues
import android.content.ContentUris
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import com.sipun.downly.runtime.RuntimeBundleInstaller
import com.sipun.downly.data.DownloadRecord
import com.sipun.downly.data.DownloadStatus
import com.sipun.downly.data.DuplicateDownloadPrompt
import com.sipun.downly.data.MediaMode
import com.sipun.downly.data.MediaPreview
import com.sipun.downly.data.ScreenState
import com.sipun.downly.utils.buildMediaOptions
import com.sipun.downly.utils.decodeText
import com.sipun.downly.utils.downloadQualityTag
import com.sipun.downly.core.notification.DownloadNotificationHelper
import com.sipun.downly.core.service.DownloadWorkManager
import com.sipun.downly.utils.downloadFilenameQuality
import com.sipun.downly.utils.defaultQualityFor
import com.sipun.downly.utils.encodeText
import com.sipun.downly.utils.formatDuration
import com.sipun.downly.utils.friendlyError
import com.sipun.downly.utils.isValidUrl
import com.sipun.downly.utils.mediaIdentityKey
import com.sipun.downly.utils.recommendQuality
import com.sipun.downly.utils.recommendAudioOption
import com.sipun.youtubedl_android.YoutubeDL
import com.sipun.youtubedl_android.YoutubeDLRequest
import com.sipun.youtubedl_android.mapper.VideoInfo
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private object RuntimeLibraryDirectoryConfiguration {
    private var ffmpegDirectory: String? = null
    private var aria2cDirectory: String? = null
    private var youtubeDlDirectory: String? = null
    private val lock = ReentrantLock()

    fun configure(directory: File) {
        lock.withLock {
            val path = directory.canonicalPath

            check(ffmpegDirectory == null || ffmpegDirectory == path) {
                "Runtime libraries changed during this process. Restart Downly to apply the update."
            }
            check(aria2cDirectory == null || aria2cDirectory == path) {
                "Runtime libraries changed during this process. Restart Downly to apply the update."
            }
            check(youtubeDlDirectory == null || youtubeDlDirectory == path) {
                "Runtime libraries changed during this process. Restart Downly to apply the update."
            }

            if (ffmpegDirectory == null) {
                com.sipun.ffmpeg.FFmpeg.setRuntimeArchiveDirectory(directory)
                ffmpegDirectory = path
            }
            if (aria2cDirectory == null) {
                com.sipun.aria2c.Aria2c.setRuntimeArchiveDirectory(directory)
                aria2cDirectory = path
            }
            if (youtubeDlDirectory == null) {
                YoutubeDL.setRuntimeArchiveDirectory(directory)
                youtubeDlDirectory = path
            }
        }
    }
}

class DownlyViewModel : ViewModel() {
    private val worker = Executors.newSingleThreadExecutor()
    private val cancelledIds = ConcurrentHashMap.newKeySet<String>()
    private val pausedIds = ConcurrentHashMap.newKeySet<String>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val persistenceWorker = Executors.newSingleThreadExecutor()
    private val _state = MutableStateFlow(ScreenState())
    val state: StateFlow<ScreenState> = _state.asStateFlow()
    private var appContext: Context? = null
    private var unregisterWorkerListener: (() -> Unit)? = null
    private var unregisterProgressListener: (() -> Unit)? = null
    private val initializationLock = ReentrantLock()
    @Volatile private var initializationInFlight = false
    @Volatile private var initializationComplete = false
    @Volatile private var historyLoaded = false
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    fun attach(context: Context, observeDownloadCompletion: Boolean = true) {
        if (appContext == null) {
            appContext = context.applicationContext
            if (observeDownloadCompletion) {
                unregisterWorkerListener = DownloadWorkManager.addCompletionListener { id ->
                    reloadRecordFromWorker(id)
                }
                unregisterProgressListener = DownloadWorkManager.addProgressListener { notificationId, percent, detail ->
                    val id = _state.value.history.firstOrNull { it.id.hashCode() == notificationId }?.id ?: return@addProgressListener
                    setRecord(id) {
                        copy(
                            status = DownloadStatus.DOWNLOADING,
                            progress = (percent / 100f).coerceIn(0f, 1f),
                            detail = detail
                        )
                    }
                }
            }
            val settings = appContext!!.getSharedPreferences("downly-settings", Context.MODE_PRIVATE)
            val savedTheme = settings.getString("theme_mode", "System") ?: "System"
            val dynamicColor = settings.getBoolean("dynamic_color", false)
            val restoredHistory = readHistory(appContext!!)
            val restoredDuplicatePrompt = readDuplicatePrompt(appContext!!)
            _state.value = _state.value.copy(
                themeMode = savedTheme,
                dynamicColor = dynamicColor,
                history = restoredHistory,
                duplicatePrompt = restoredDuplicatePrompt
            )
            historyLoaded = true
            connectivityManager = appContext!!.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = updateConnectionType()
                override fun onLost(network: Network) = updateConnectionType()
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = updateConnectionType()
            }
            networkCallback = callback
            runCatching { connectivityManager?.registerDefaultNetworkCallback(callback) }
            updateConnectionType()
        }
        initialize()
    }

    fun setUrl(value: String) = mutate { copy(url = value, preview = null, message = "") }
    fun setMode(value: MediaMode) = mutate { copy(mode = value) }
    fun setQuality(value: String) = mutate { copy(quality = value) }
    fun setTab(value: Int) = mutate { copy(tab = value) }
    fun setCustomFileName(value: String) = mutate { copy(customFileName = value) }
    fun setAdvancedExpanded(value: Boolean) = mutate { copy(advancedExpanded = value) }
    fun setQualityDropdownExpanded(value: Boolean) = mutate { copy(qualityDropdownExpanded = value) }
    fun setHistorySearchQuery(value: String) = mutate { copy(historySearchQuery = value) }
    fun setIsSearchActive(value: Boolean) = mutate { copy(isSearchActive = value) }
    fun setHistoryFilter(value: String) = mutate { copy(historyFilter = value) }

    fun setThemeMode(value: String) {
        mutate { copy(themeMode = value) }
        appContext?.getSharedPreferences("downly-settings", Context.MODE_PRIVATE)
            ?.edit()
            ?.putString("theme_mode", value)
            ?.putBoolean("dynamic_color", _state.value.dynamicColor)
            ?.apply()
    }

    fun setDynamicColor(value: Boolean) {
        mutate { copy(dynamicColor = value) }
        appContext?.getSharedPreferences("downly-settings", Context.MODE_PRIVATE)
            ?.edit()?.putBoolean("dynamic_color", value)?.apply()
    }

    fun initialize() {
        initializationLock.withLock {
            if (initializationComplete || initializationInFlight || _state.value.initialized) return
            initializationInFlight = true
        }
        mutate { copy(busy = true, libraryProgress = null, message = "Preparing Libraries…") }
        worker.execute {
            val context = appContext
            if (context == null) {
                initializationLock.withLock { initializationInFlight = false }
                return@execute
            }
            try {
                mutate { copy(message = "Downloading Libraries archive…", libraryProgress = null) }
                val runtimeDir = RuntimeBundleInstaller.ensureInstalled(context) { progress ->
                    mutate { copy(libraryProgress = progress) }
                }
                RuntimeLibraryDirectoryConfiguration.configure(runtimeDir)
                val baseDir = File(context.noBackupFilesDir, "youtubedl-android")
                val packagesDir = File(baseDir, "packages")

                val pythonArchive = File(runtimeDir, "libpython.zip.so")
                if (pythonArchive.isFile) {
                    val pythonDir = File(packagesDir, "python")
                    val version = com.sipun.youtubedl_common.utils.RuntimeArchiveUtils.sha256(pythonArchive)
                    val root = File(pythonDir, "usr/lib")
                    val versions = root.listFiles()?.filter {
                        it.isDirectory && it.name.matches(Regex("python3\\.\\d+"))
                    }.orEmpty()
                    val isValid = versions.any { File(it, "encodings/__init__.py").isFile }
                    if (!isValid || version != com.sipun.youtubedl_common.SharedPrefsHelper[context, "pythonLibVersion"]) {
                        com.sipun.youtubedl_common.utils.RuntimeArchiveUtils.installArchive(pythonArchive, pythonDir) { staged ->
                            val rootStaged = File(staged, "usr/lib")
                            val versionsStaged = rootStaged.listFiles()?.filter {
                                it.isDirectory && it.name.matches(Regex("python3\\.\\d+"))
                            }.orEmpty()
                            if (!versionsStaged.any { File(it, "encodings/__init__.py").isFile }) {
                                throw IOException("Python runtime archive is missing its standard library")
                            }
                        }
                        com.sipun.youtubedl_common.SharedPrefsHelper.update(context, "pythonLibVersion", version)
                    }
                }

                val ffmpegArchive = File(runtimeDir, "libffmpeg.zip.so")
                if (ffmpegArchive.isFile) {
                    val ffmpegDir = File(packagesDir, "ffmpeg")
                    val version = com.sipun.youtubedl_common.utils.RuntimeArchiveUtils.sha256(ffmpegArchive)
                    val libraryDirectory = File(ffmpegDir, "usr/lib")
                    val needsInstall = !libraryDirectory.isDirectory ||
                        libraryDirectory.list()?.isEmpty() != false ||
                        version != com.sipun.youtubedl_common.SharedPrefsHelper[context, "ffmpegLibVersion"]
                    if (needsInstall) {
                        com.sipun.youtubedl_common.utils.RuntimeArchiveUtils.installArchive(ffmpegArchive, ffmpegDir) { staged ->
                            val stagedLibraries = File(staged, "usr/lib")
                            if (!stagedLibraries.isDirectory || stagedLibraries.list()?.isEmpty() != false) {
                                throw IOException("FFmpeg archive contains no runtime libraries")
                            }
                        }
                        com.sipun.youtubedl_common.SharedPrefsHelper.update(context, "ffmpegLibVersion", version)
                    }
                }

                val aria2cArchive = File(runtimeDir, "libaria2c.zip.so")
                if (aria2cArchive.isFile) {
                    val aria2cDir = File(packagesDir, "aria2c")
                    val version = com.sipun.youtubedl_common.utils.RuntimeArchiveUtils.sha256(aria2cArchive)
                    val libraryDirectory = File(aria2cDir, "usr/lib")
                    val needsInstall = !libraryDirectory.isDirectory ||
                        libraryDirectory.list()?.isEmpty() != false ||
                        version != com.sipun.youtubedl_common.SharedPrefsHelper[context, "aria2cLibVersion"]
                    if (needsInstall) {
                        com.sipun.youtubedl_common.utils.RuntimeArchiveUtils.installArchive(aria2cArchive, aria2cDir) { staged ->
                            val stagedLibraries = File(staged, "usr/lib")
                            if (!stagedLibraries.isDirectory || stagedLibraries.list()?.isEmpty() != false) {
                                throw IOException("aria2 archive contains no runtime libraries")
                            }
                        }
                        com.sipun.youtubedl_common.SharedPrefsHelper.update(context, "aria2cLibVersion", version)
                    }
                }

                com.sipun.ffmpeg.FFmpeg.init(context)
                runCatching { com.sipun.aria2c.Aria2c.init(context) }
                YoutubeDL.init(context)
                initializationLock.withLock {
                    initializationComplete = true
                    initializationInFlight = false
                }
                mutate { copy(initialized = true, busy = false, libraryProgress = 1f, message = "Downloader ready") }
            } catch (error: Exception) {
                initializationLock.withLock {
                    initializationInFlight = false
                    initializationComplete = false
                }
                mutate { copy(initialized = false, busy = false, libraryProgress = null, message = friendlyError(error)) }
            }
        }
    }

    fun fetchInfo() {
        val current = _state.value
        val rawUrl = current.url.trim()
        val url = com.sipun.downly.utils.normalizeUrl(rawUrl)
        if (!isValidUrl(url)) {
            mutate { copy(message = "Paste a valid http/https URL first.") }
            return
        }
        mutate { copy(url = url) }
        if (!current.initialized) {
            initialize()
            mutate { copy(message = "Downloader is starting. Please tap Fetch info again shortly.") }
            return
        }
        mutate { copy(busy = true, message = "Analyzing link and getting download options…", preview = null) }
        worker.execute {
            try {
                val directProbe = com.sipun.downly.utils.probeDirectFile(url)
                if (directProbe != null) {
                    val preview = MediaPreview(
                        title = directProbe.fileName,
                        url = url,
                        isDirectFile = true,
                        mimeType = directProbe.mimeType,
                        fileSize = directProbe.fileSize,
                        fileExtension = directProbe.extension,
                        directFileName = directProbe.fileName,
                        fileCategory = directProbe.category
                    )
                    mutate {
                        copy(
                            busy = false,
                            preview = preview,
                            mode = MediaMode.FILE,
                            customFileName = directProbe.fileName,
                            message = "Direct file link ready"
                        )
                    }
                    return@execute
                }

                val info: VideoInfo = YoutubeDL.getInfo(url)
                val mediaOptions = buildMediaOptions(info.formats.orEmpty(), info.duration)
                val preview = MediaPreview(
                    title = info.title?.takeIf { it.isNotBlank() } ?: url,
                    uploader = info.uploader.orEmpty(),
                    duration = formatDuration(info.duration),
                    url = info.webpageUrl ?: url,
                    thumbnailUrl = info.thumbnail?.takeIf { it.startsWith("https://") || it.startsWith("http://") },
                    durationSeconds = info.duration,
                    qualityOptions = mediaOptions.qualityOptions,
                    audioOptions = mediaOptions.audioOptions,
                    isDirectFile = false
                )
                mutate { copy(busy = false, preview = preview, mode = MediaMode.VIDEO, recommendedQuality = recommendQuality(preview.qualityOptions, connectionType), message = "Media details ready") }
            } catch (error: Exception) {
                val fallbackProbe = com.sipun.downly.utils.probeDirectFile(url)
                if (fallbackProbe != null) {
                    val preview = MediaPreview(
                        title = fallbackProbe.fileName,
                        url = url,
                        isDirectFile = true,
                        mimeType = fallbackProbe.mimeType,
                        fileSize = fallbackProbe.fileSize,
                        fileExtension = fallbackProbe.extension,
                        directFileName = fallbackProbe.fileName,
                        fileCategory = fallbackProbe.category
                    )
                    mutate {
                        copy(
                            busy = false,
                            preview = preview,
                            mode = MediaMode.FILE,
                            customFileName = fallbackProbe.fileName,
                            message = "Direct file link ready"
                        )
                    }
                } else {
                    mutate { copy(busy = false, message = friendlyError(error)) }
                }
            }
        }
    }

    fun startBestDownload() {
        val current = _state.value
        if (current.preview?.isDirectFile == true) {
            startDownload()
            return
        }
        val selectedQuality = when (current.mode) {
            MediaMode.VIDEO -> current.preview?.qualityOptions
                ?.maxByOrNull { it.height }
                ?.let { "video:${it.formatSelector}" }
                ?: current.recommendedQuality
            MediaMode.AUDIO -> current.preview?.audioOptions
                ?.let { recommendAudioOption(it) }
                ?.let { "audio:${it.formatId}" }
            MediaMode.FILE -> "Best"
        }
        startDownload(selectedQuality)
    }

    fun startDownload(qualityOverride: String? = null) {
        val current = _state.value
        val url = current.url.trim()
        if (!isValidUrl(url)) {
            mutate { copy(message = "Paste a valid http/https URL first.") }
            return
        }
        if (!current.initialized) {
            initialize()
            mutate { copy(message = "Downloader is starting. Retry in a moment.") }
            return
        }
        if (current.history.any {
                it.url == url && it.status in setOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING)
            }) {
            mutate { copy(message = "This link is already active.") }
            return
        }
        val selectedTitle = if (current.preview?.isDirectFile == true) {
            current.customFileName.ifBlank { current.preview.title }
        } else {
            current.preview?.title ?: Uri.parse(url).host.orEmpty()
        }
        val selectedQuality = qualityOverride ?: if (current.mode == MediaMode.VIDEO && current.quality == "Smart") current.recommendedQuality else current.quality
        val record = DownloadRecord(
            url = url,
            title = selectedTitle,
            status = DownloadStatus.QUEUED,
            mode = current.mode,
            quality = selectedQuality
        )
        queueDownloadRecord(record)
        scheduleDownload(record, current.mode, selectedQuality, current.connectionType)
    }

    private fun queueDownloadRecord(record: DownloadRecord) {
        val context = appContext ?: return
        val queue = {
            val current = _state.value
            val next = current.copy(
                history = enqueueDownloadRecord(current.history, record),
                url = "",
                preview = null,
                tab = 1,
                message = "Added to download queue"
            )
            _state.value = next
            persistHistoryImmediately(context, next.history)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            queue()
        } else {
            val queued = java.util.concurrent.CountDownLatch(1)
            mainHandler.post {
                queue()
                queued.countDown()
            }
            queued.await()
        }
    }

    private fun removeDownloadRecord(id: String) {
        val context = appContext ?: return
        val remove = {
            val current = _state.value
            val next = current.copy(
                history = current.history.filterNot { it.id == id },
                message = "Download cancelled"
            )
            _state.value = next
            persistHistoryImmediately(context, next.history)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            remove()
        } else {
            val removed = java.util.concurrent.CountDownLatch(1)
            mainHandler.post {
                remove()
                removed.countDown()
            }
            removed.await()
        }
    }

    private fun scheduleDownload(record: DownloadRecord, mode: MediaMode, quality: String, connectionType: String) {
        appContext?.let { context ->
            DownloadWorkManager.enqueue(context, record.copy(mode = mode, quality = quality), connectionType)
        }
    }

    fun executeDownloadFromWorker(
        record: DownloadRecord,
        mode: MediaMode,
        quality: String,
        connectionType: String
    ) {
        executeDownload(record, mode, quality, connectionType)
    }

    fun flushHistoryForWorker(id: String) {
        val latch = java.util.concurrent.CountDownLatch(1)
        mainHandler.post { latch.countDown() }
        latch.await()
        val context = appContext ?: return
        val persistenceLatch = java.util.concurrent.CountDownLatch(1)
        persistenceWorker.execute { persistenceLatch.countDown() }
        persistenceLatch.await()
        val workerRecord = _state.value.history.firstOrNull { it.id == id }
        val encoded = encodeHistory(mergeWorkerRecordIntoHistory(readHistory(context), workerRecord))
        context.getSharedPreferences("downly-history", Context.MODE_PRIVATE)
            .edit()
            .putString("records", encoded)
            .commit()
    }

    private fun executeDownload(record: DownloadRecord, mode: MediaMode, quality: String, connectionType: String) {
        if (cancelledIds.remove(record.id)) return
        try {
            setRecord(record.id) { copy(status = DownloadStatus.DOWNLOADING, detail = "Starting download…") }
            val context = appContext ?: error("Application context is unavailable.")
            val root = File(context.getExternalFilesDir(null) ?: context.filesDir, "Downly")
            val folder = File(root, record.id)
            if (!folder.exists() && !folder.mkdirs()) error("Unable to create download folder.")
            val request = YoutubeDLRequest(record.url)
                .addOption("--newline")
                .addOption("--continue")

            if (mode == MediaMode.FILE) {
                val outputFileName = record.title.ifBlank { "download-${System.currentTimeMillis()}" }
                request.addOption("-o", File(folder, outputFileName).absolutePath)
            } else {
                request.addOption("-o", File(folder, "%(title).140B - %(id)s.%(ext)s").absolutePath)
                    .addOption("--no-playlist")
                if (mode == MediaMode.AUDIO) {
                    val audioFormatId = quality.takeIf { it.startsWith("audio:") }?.removePrefix("audio:")
                    val formatSpec = if (!audioFormatId.isNullOrBlank()) "$audioFormatId/ba/b" else "ba/b"
                    request.addOption("-f", formatSpec)
                        .addOption("-x")
                        .addOption("--audio-format", "mp3")
                        .addOption("--audio-quality", if (connectionType == "Mobile data") "5" else "0")
                } else {
                    val explicitSelector = quality.takeIf { it.startsWith("video:") }?.removePrefix("video:")
                    val maxHeight = quality.removeSuffix("p").toIntOrNull()
                    val format = explicitSelector?.takeIf { it.isNotBlank() }
                        ?: if (maxHeight != null) "bv*[height<=$maxHeight]+ba/b[height<=$maxHeight]/bv*+ba/b" else "bv*+ba/b"
                    request.addOption("-f", format)
                        .addOption("-S", "res,fps")
                        .addOption("--merge-output-format", "mp4")
                }
            }

            if (cancelledIds.remove(record.id)) return
            val notifId = record.id.hashCode()
            DownloadNotificationHelper.showProgress(
                context,
                notifId,
                record.title,
                0,
                "Starting download…",
                record.id
            )
            YoutubeDL.execute(request, record.id) { percent, eta, line ->
                val fraction = (percent / 100f).coerceIn(0f, 1f)
                val cleanedLine = com.sipun.downly.utils.cleanYtDlpLine(line)
                val detailText = cleanedLine.take(150).ifBlank { if (eta > 0) "About ${eta}s left" else "Downloading…" }
                DownloadNotificationHelper.showProgress(context, notifId, record.title, percent.toInt(), detailText, record.id)
                setRecord(record.id) {
                    copy(status = DownloadStatus.DOWNLOADING, progress = fraction,
                        detail = detailText)
                }
            }
            val saved = folder.listFiles()?.filter { file ->
                file.isFile && !file.name.endsWith(".part") &&
                    !file.name.endsWith(".ytdl") && !file.name.endsWith(".temp")
            }?.maxByOrNull { it.lastModified() }
                ?: throw IOException("Download finished, but output file was not found.")
            val mediaId = extractMediaId(saved.name)
            val mediaIdentity = mediaId?.let { mediaIdentityKey(it, mode, quality) }
            val normalized = if (mode == MediaMode.FILE) saved.name else normalizeDownloadedFileName(saved, record.title, mode, quality)
            val outputFile = if (normalized != saved.name) {
                val renamed = File(saved.parentFile, normalized)
                if (saved.renameTo(renamed)) renamed else saved
            } else saved
            val duplicate = findExistingMedia(context, outputFile.name, mode, mediaIdentity)
            if (duplicate != null) {
                mutate {
                    copy(
                        duplicatePrompt = DuplicateDownloadPrompt(
                            recordId = record.id,
                            sourcePath = outputFile.absolutePath,
                            existingUri = duplicate.first.toString(),
                            existingPath = duplicate.second,
                            mode = mode,
                            fileName = outputFile.name
                        ),
                        message = "This file is already downloaded. Choose whether to replace it."
                    )
                }
                persistDuplicatePrompt(context, DuplicateDownloadPrompt(
                    recordId = record.id,
                    sourcePath = outputFile.absolutePath,
                    existingUri = duplicate.first.toString(),
                    existingPath = duplicate.second,
                    mode = mode,
                    fileName = outputFile.name
                ))
                if (mediaIdentity != null) saveMediaIdMapping(context, mediaIdentity, duplicate.first)
                setRecord(record.id) { copy(detail = "Waiting for duplicate choice…") }
                return
            }
            val publicPath = publishToMediaStore(context, outputFile, mode, mediaIdentity)
            outputFile.delete()
            DownloadNotificationHelper.showComplete(context, notifId, record.title)
            setRecord(record.id) {
                copy(status = DownloadStatus.COMPLETE, progress = 1f, detail = "Download complete",
                    filePath = publicPath)
            }
        } catch (error: Exception) {
            val latest = _state.value.history.firstOrNull { it.id == record.id }
            val status = when {
                DownloadWorkManager.consumePauseRequest(record.id) ||
                    pausedIds.remove(record.id) || latest?.status == DownloadStatus.PAUSED -> DownloadStatus.PAUSED
                cancelledIds.remove(record.id) || latest?.status == DownloadStatus.CANCELLED ||
                    error is YoutubeDL.CanceledException -> DownloadStatus.CANCELLED
                else -> DownloadStatus.FAILED
            }
            val notifId = record.id.hashCode()
            appContext?.let { ctx ->
                if (status == DownloadStatus.PAUSED) {
                    DownloadNotificationHelper.showPaused(ctx, notifId, record.title, record.id)
                } else if (status == DownloadStatus.CANCELLED) {
                    DownloadNotificationHelper.cancel(ctx, notifId)
                } else {
                    DownloadNotificationHelper.showFailed(ctx, notifId, record.title, friendlyError(error))
                }
            }
            setRecord(record.id) {
                copy(status = status, detail = if (status == DownloadStatus.PAUSED) "Paused · tap Resume to continue" else friendlyError(error))
            }
        } finally {
            appContext?.let { persistHistory(it) }
        }
    }

    private fun normalizeDownloadedFileName(file: File, title: String, mode: MediaMode, quality: String): String {
        val cleanTitle = title
            .replace(Regex("#[\\p{L}\\p{N}_-]+"), " ")
            .replace('_', ' ')
            .replace(Regex("[\\p{Cntrl}<>:\"/\\\\|?*]"), " ")
            .replace(Regex("[^\\p{L}\\p{N} .()'-]"), " ")
            .replace(Regex("\\s+"), " ")
            .replace(Regex("^[\\s._-]+|[\\s._-]+$"), "")
            .take(100)
            .trimEnd()
            .ifBlank { "Downly download" }
            .let { value ->
                val letters = value.filter { it.isLetter() }
                if (letters.isNotEmpty() && letters.all { it.isUpperCase() }) {
                    value.lowercase().split(' ').joinToString(" ") { word ->
                        word.replaceFirstChar { char ->
                            if (char.isLowerCase()) char.titlecase() else char.toString()
                        }
                    }
                } else value
            }
        return "$cleanTitle-${downloadFilenameQuality(mode, quality)}." + file.extension.lowercase().ifBlank { "mp4" }
    }

    private fun findExistingMedia(context: Context, fileName: String, mode: MediaMode, identityKey: String?): Pair<Uri, String>? {
        val relativeDirectory = when (mode) {
            MediaMode.AUDIO -> Environment.DIRECTORY_DOWNLOADS + "/Downly/Music/"
            MediaMode.VIDEO -> Environment.DIRECTORY_DOWNLOADS + "/Downly/Videos/"
            MediaMode.FILE -> Environment.DIRECTORY_DOWNLOADS + "/Downly/"
        }
        val altDirectory = relativeDirectory.trimEnd('/')
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val knownUri = identityKey?.let { readMediaIdMapping(context, it) }
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME)
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ? OR ${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        context.contentResolver.query(
            collection, projection, selection, arrayOf(relativeDirectory, altDirectory), null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
                val matchesName = name.equals(fileName, ignoreCase = true)
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                val uri = ContentUris.withAppendedId(collection, id)
                val matchesStoredId = knownUri != null && knownUri == uri.toString()
                if (matchesName || matchesStoredId) {
                    return uri to (relativeDirectory + name)
                }
            }
        }
        return null
    }

    private fun saveMediaIdMapping(context: Context, mediaId: String, uri: Uri) {
        context.getSharedPreferences("downly-media-ids", Context.MODE_PRIVATE)
            .edit().putString(mediaId, uri.toString()).apply()
    }

    private fun readMediaIdMapping(context: Context, mediaId: String): String? {
        return context.getSharedPreferences("downly-media-ids", Context.MODE_PRIVATE)
            .getString(mediaId, null)
    }

    private fun extractMediaId(fileName: String): String? {
        val baseName = fileName.substringBeforeLast('.', fileName)
            .replace(Regex("\\s+\\(\\d+\\)$"), "")
            .trim()
        val bracketedId = Regex("\\[([^]]+)]").find(baseName)?.groupValues?.getOrNull(1)
        if (!bracketedId.isNullOrBlank()) return bracketedId.trim()

        val separator = " - "
        if (!baseName.contains(separator)) return null
        return baseName.substringAfterLast(separator).trim()
            .replace(Regex("\\s+\\(\\d+\\)$"), "")
            .takeIf { it.isNotBlank() }
    }

    fun resolveDuplicate(replaceExisting: Boolean) {
        val prompt = _state.value.duplicatePrompt ?: return
        val context = appContext ?: return
        worker.execute {
            try {
                val source = File(prompt.sourcePath)
                if (replaceExisting) {
                    mutate { copy(duplicateReplacementInProgress = true, message = "Replacing existing file…") }
                    val publicPath = replaceMediaStoreItem(context, Uri.parse(prompt.existingUri), source, prompt.existingPath)
                    source.delete()
                    val replacement = _state.value.history.firstOrNull { it.id == prompt.recordId }
                        ?.copy(
                            status = DownloadStatus.COMPLETE,
                            progress = 1f,
                            detail = "Download complete · replaced existing file",
                            filePath = publicPath
                        )
                    if (replacement != null) {
                        mutate {
                            copy(
                                history = reconcileReplacedHistory(history.map { if (it.id == replacement.id) replacement else it }, replacement, publicPath),
                                duplicatePrompt = null,
                                duplicateReplacementInProgress = false,
                                message = "Existing file replaced"
                            )
                        }
                        clearDuplicatePrompt(context)
                    } else {
                        mutate { copy(duplicatePrompt = null, duplicateReplacementInProgress = false, message = "Existing file replaced") }
                        clearDuplicatePrompt(context)
                    }
                } else {
                    source.delete()
                    setRecord(prompt.recordId) {
                        copy(status = DownloadStatus.COMPLETE, progress = 1f,
                            detail = "Already downloaded · kept existing file", filePath = prompt.existingPath)
                    }
                    mutate { copy(duplicatePrompt = null, duplicateReplacementInProgress = false, message = "Kept the existing download") }
                    clearDuplicatePrompt(context)
                }
            } catch (error: Exception) {
                mutate { copy(duplicatePrompt = null, duplicateReplacementInProgress = false, message = friendlyError(error)) }
                setRecord(prompt.recordId) { copy(status = DownloadStatus.FAILED, detail = friendlyError(error)) }
                clearDuplicatePrompt(context)
            } finally {
                appContext?.let { persistHistory(it) }
            }
        }
    }

    private fun replaceMediaStoreItem(context: Context, existingUri: Uri, source: File, existingPath: String): String {
        val resolver = context.contentResolver
        val backup = File.createTempFile("downly-replace-", ".bak", context.cacheDir)
        try {
            replaceWithRollback(
                backup = {
                    resolver.openInputStream(existingUri)?.use { input ->
                        backup.outputStream().use { output -> input.copyTo(output) }
                    } ?: throw IOException("Could not back up the existing download.")
                },
                replace = {
                    resolver.openOutputStream(existingUri, "wt")?.use { output ->
                        source.inputStream().use { input -> input.copyTo(output) }
                    } ?: throw IOException("Could not write the replacement file.")
                },
                restore = {
                    resolver.openOutputStream(existingUri, "wt")?.use { output ->
                        backup.inputStream().use { input -> input.copyTo(output) }
                    } ?: throw IOException("Could not restore the existing download.")
                }
            )
            return existingPath
        } finally {
            backup.delete()
        }
    }


    private fun publishToMediaStore(context: Context, source: File, mode: MediaMode, identityKey: String?): String {
        val resolver = context.contentResolver
        val relativeDirectory = when (mode) {
            MediaMode.AUDIO -> Environment.DIRECTORY_DOWNLOADS + "/Downly/Music/"
            MediaMode.VIDEO -> Environment.DIRECTORY_DOWNLOADS + "/Downly/Videos/"
            MediaMode.FILE -> Environment.DIRECTORY_DOWNLOADS + "/Downly/"
        }
        val mimeType = when (mode) {
            MediaMode.AUDIO -> "audio/mpeg"
            MediaMode.FILE -> {
                when (source.extension.lowercase()) {
                    "apk" -> "application/vnd.android.package-archive"
                    "zip" -> "application/zip"
                    "pdf" -> "application/pdf"
                    "png" -> "image/png"
                    "jpg", "jpeg" -> "image/jpeg"
                    "webp" -> "image/webp"
                    "mp4" -> "video/mp4"
                    "mp3" -> "audio/mpeg"
                    else -> "application/octet-stream"
                }
            }
            MediaMode.VIDEO -> {
                when (source.extension.lowercase()) {
                    "webm" -> "video/webm"
                    "mkv" -> "video/x-matroska"
                    "mov" -> "video/quicktime"
                    else -> "video/mp4"
                }
            }
        }
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, source.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDirectory)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
            ?: throw IOException("Could not create the file in $relativeDirectory")
        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IOException("Could not write the downloaded file.")
            val publishedValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            if (resolver.update(uri, publishedValues, null, null) <= 0) {
                throw IOException("Could not publish the downloaded file.")
            }
            if (!identityKey.isNullOrBlank()) saveMediaIdMapping(context, identityKey, uri)
            return relativeDirectory + source.name
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    fun pause(record: DownloadRecord) {
        DownloadWorkManager.requestPause(record.id)
        appContext?.let { DownloadWorkManager.cancel(it, record.id) }
        appContext?.let { DownloadNotificationHelper.cancel(it, record.id.hashCode()) }
        val current = _state.value.history.firstOrNull { it.id == record.id } ?: return
        if (current.status == DownloadStatus.QUEUED) {
            pausedIds.add(record.id)
            setRecord(record.id) { copy(status = DownloadStatus.PAUSED, detail = "Paused · tap Resume to continue") }
            return
        }
        if (current.status == DownloadStatus.DOWNLOADING) {
            pausedIds.add(record.id)
            setRecord(record.id) { copy(status = DownloadStatus.PAUSED, detail = "Pausing…") }
            if (!YoutubeDL.destroyProcessById(record.id)) {
                pausedIds.remove(record.id)
                setRecord(record.id) { copy(status = DownloadStatus.DOWNLOADING, detail = "Downloading…") }
            }
        }
    }

    fun resume(record: DownloadRecord) {
        val current = _state.value.history.firstOrNull { it.id == record.id } ?: return
        if (current.status != DownloadStatus.PAUSED) return
        pausedIds.remove(record.id)
        val mode = current.mode
        val quality = current.quality
        setRecord(record.id) { copy(status = DownloadStatus.QUEUED, detail = "Resuming download…") }
        scheduleDownload(current.copy(status = DownloadStatus.QUEUED), mode, quality, _state.value.connectionType)
    }

    fun retry(record: DownloadRecord) {
        val current = _state.value.history.firstOrNull { it.id == record.id } ?: return
        if (current.status != DownloadStatus.FAILED) return
        setRecord(record.id) { copy(status = DownloadStatus.QUEUED, progress = 0f, detail = "Retrying download…") }
        scheduleDownload(current.copy(status = DownloadStatus.QUEUED), current.mode, current.quality, _state.value.connectionType)
    }

    fun cancel(record: DownloadRecord) {
        cancelledIds.add(record.id)
        pausedIds.remove(record.id)
        appContext?.let { DownloadWorkManager.cancel(it, record.id) }
        appContext?.let { DownloadNotificationHelper.cancel(it, record.id.hashCode()) }
        YoutubeDL.destroyProcessById(record.id)
        removeDownloadRecord(record.id)
    }

    fun removePaused(record: DownloadRecord) {
        appContext?.let { DownloadNotificationHelper.cancel(it, record.id.hashCode()) }
        val current = _state.value.history.firstOrNull { it.id == record.id } ?: return
        if (current.status != DownloadStatus.PAUSED || current.detail == "Pausing…") return
        pausedIds.remove(record.id)
        cancelledIds.remove(record.id)
        mutate {
            copy(
                history = history.filterNot { it.id == record.id },
                message = "Paused download removed"
            )
        }
        val context = appContext ?: return
        worker.execute {
            val root = File(context.getExternalFilesDir(null) ?: context.filesDir, "Downly")
            File(root, record.id).deleteRecursively()
        }
    }

    fun delete(record: DownloadRecord) {
        removeRecord(record, deleteFileFromStorage = false)
    }

    fun removeRecord(record: DownloadRecord, deleteFileFromStorage: Boolean) {
        val context = appContext
        cancelledIds.add(record.id)
        pausedIds.remove(record.id)
        appContext?.let { DownloadWorkManager.cancel(it, record.id) }
        appContext?.let { DownloadNotificationHelper.cancel(it, record.id.hashCode()) }
        YoutubeDL.destroyProcessById(record.id)

        mutate {
            copy(
                history = history.filterNot { it.id == record.id },
                message = if (deleteFileFromStorage) "Download and file removed" else "Download removed from list"
            )
        }

        if (context != null) {
            worker.execute {
                val root = File(context.getExternalFilesDir(null) ?: context.filesDir, "Downly")
                File(root, record.id).deleteRecursively()

                if (deleteFileFromStorage && !record.filePath.isNullOrBlank()) {
                    runCatching {
                        val path = record.filePath
                        val name = path.substringAfterLast('/')
                        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
                        context.contentResolver.delete(collection, selection, arrayOf(name))
                    }
                    runCatching {
                        val file = File(record.filePath)
                        if (file.exists()) file.delete()
                    }
                }
                persistHistory(context)
            }
        }
    }

    fun clearFinished() {
        mutate {
            copy(history = history.filterNot {
                it.status in setOf(DownloadStatus.COMPLETE, DownloadStatus.FAILED, DownloadStatus.CANCELLED)
            })
        }
    }

    private fun readHistory(context: Context): List<DownloadRecord> {
        val stored = context.getSharedPreferences("downly-history", Context.MODE_PRIVATE)
            .getString("records", null) ?: return emptyList()
        return stored.lineSequence().mapNotNull { line ->
            val p = line.split('|')
            if (p.size < 8) return@mapNotNull null
            runCatching {
                val status = DownloadStatus.valueOf(p[3]).let {
                    if (it == DownloadStatus.DOWNLOADING || it == DownloadStatus.QUEUED) DownloadStatus.FAILED else it
                }
                DownloadRecord(
                    id = p[0], url = decodeText(p[1]), title = decodeText(p[2]), status = status,
                    progress = p[4].toFloatOrNull() ?: 0f, detail = decodeText(p[5]),
                    filePath = p[6].takeIf { it != "-" }?.let(::decodeText),
                    timestamp = p[7].toLongOrNull() ?: System.currentTimeMillis(),
                    mode = p.getOrNull(8)?.let { runCatching { MediaMode.valueOf(it) }.getOrNull() } ?: MediaMode.VIDEO,
                    quality = p.getOrNull(9)?.let(::decodeText) ?: "Best"
                )
            }.getOrNull()
        }.toList()
    }

    private fun persistHistory(context: Context, snapshot: List<DownloadRecord> = _state.value.history) {
        if (!historyLoaded) return
        val encoded = encodeHistory(snapshot)
        persistenceWorker.execute {
            context.getSharedPreferences("downly-history", Context.MODE_PRIVATE).edit()
                .putString("records", encoded).commit()
        }
    }

    private fun persistHistoryImmediately(context: Context, snapshot: List<DownloadRecord>) {
        if (!historyLoaded) return
        val saved = java.util.concurrent.CountDownLatch(1)
        val encoded = encodeHistory(snapshot)
        persistenceWorker.execute {
            context.getSharedPreferences("downly-history", Context.MODE_PRIVATE).edit()
                .putString("records", encoded).commit()
            saved.countDown()
        }
        saved.await()
    }

    private fun persistDuplicatePrompt(context: Context, prompt: DuplicateDownloadPrompt) {
        context.getSharedPreferences("downly-history", Context.MODE_PRIVATE).edit()
            .putString("duplicate_prompt", listOf(
                prompt.recordId, prompt.sourcePath, prompt.existingUri, prompt.existingPath,
                prompt.mode.name, prompt.fileName
            ).joinToString("|") { encodeText(it) })
            .apply()
    }

    private fun clearDuplicatePrompt(context: Context) {
        context.getSharedPreferences("downly-history", Context.MODE_PRIVATE).edit()
            .remove("duplicate_prompt").apply()
    }

    private fun readDuplicatePrompt(context: Context): DuplicateDownloadPrompt? {
        val encoded = context.getSharedPreferences("downly-history", Context.MODE_PRIVATE)
            .getString("duplicate_prompt", null) ?: return null
        val parts = encoded.split('|')
        if (parts.size != 6) return null
        return runCatching {
            DuplicateDownloadPrompt(
                recordId = decodeText(parts[0]), sourcePath = decodeText(parts[1]),
                existingUri = decodeText(parts[2]), existingPath = decodeText(parts[3]),
                mode = MediaMode.valueOf(decodeText(parts[4])), fileName = decodeText(parts[5])
            )
        }.getOrNull()
    }

    private fun encodeHistory(snapshot: List<DownloadRecord>): String = snapshot.joinToString("\n") { item ->
            listOf(item.id, encodeText(item.url), encodeText(item.title), item.status.name,
                item.progress.toString(), encodeText(item.detail), item.filePath?.let(::encodeText) ?: "-",
                item.timestamp.toString(), item.mode.name, encodeText(item.quality)).joinToString("|")
        }

    private fun reloadRecordFromWorker(id: String) {
        val context = appContext ?: return
        val updated = readHistory(context).firstOrNull { it.id == id } ?: return
        val restoredPrompt = duplicatePromptForHistory(readHistory(context), readDuplicatePrompt(context))
        mutate {
            copy(
                history = history.map { if (it.id == id) updated else it },
                duplicatePrompt = restoredPrompt
            )
        }
    }

    private fun setRecord(id: String, change: DownloadRecord.() -> DownloadRecord) {
        mutate { copy(history = history.map { if (it.id == id) it.change() else it }) }
    }

    private fun mutate(change: ScreenState.() -> ScreenState) {
        mainHandler.post {
            val next = _state.value.change()
            _state.value = next
            appContext?.let { persistHistory(it, next.history) }
        }
    }

    private fun updateConnectionType() {
        val manager = connectivityManager ?: return
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
        val type = when {
            capabilities == null -> "Offline"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Connected"
        }
        mutate { copy(connectionType = type, recommendedQuality = preview?.let { recommendQuality(it.qualityOptions, type) } ?: defaultQualityFor(type)) }
    }

    override fun onCleared() {
        unregisterWorkerListener?.invoke()
        unregisterProgressListener?.invoke()
        networkCallback?.let { callback -> runCatching { connectivityManager?.unregisterNetworkCallback(callback) } }
        worker.shutdownNow()
        persistenceWorker.shutdown()
    }
}

