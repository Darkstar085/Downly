package com.sipun.downly.data

import java.util.UUID

enum class MediaMode { VIDEO, AUDIO, FILE }
enum class DownloadStatus { QUEUED, DOWNLOADING, PAUSED, COMPLETE, FAILED, CANCELLED }

data class DownloadRecord(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val title: String,
    val status: DownloadStatus,
    val progress: Float = 0f,
    val detail: String = "Queued",
    val filePath: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val mode: MediaMode = MediaMode.VIDEO,
    val quality: String = "Best"
)

data class QualityOption(
    val quality: String,
    val height: Int,
    val estimatedBytes: Long?,
    val formatSelector: String = "",
    val sizeIsApproximate: Boolean = true
)
data class AudioOption(
    val formatId: String,
    val quality: String,
    val bitrate: Int,
    val estimatedBytes: Long?,
    val sizeIsApproximate: Boolean = true
)
data class MediaOptions(
    val qualityOptions: List<QualityOption>,
    val audioOptions: List<AudioOption>
)

data class MediaPreview(
    val title: String,
    val uploader: String = "",
    val duration: String = "",
    val url: String,
    val thumbnailUrl: String? = null,
    val durationSeconds: Int = 0,
    val qualityOptions: List<QualityOption> = emptyList(),
    val audioOptions: List<AudioOption> = emptyList(),
    val isDirectFile: Boolean = false,
    val mimeType: String = "",
    val fileSize: Long? = null,
    val fileExtension: String = "",
    val directFileName: String = "",
    val fileCategory: String = "FILE"
)

data class DuplicateDownloadPrompt(
    val recordId: String,
    val sourcePath: String,
    val existingUri: String,
    val existingPath: String,
    val mode: MediaMode,
    val fileName: String
)

data class DownloadSettings(
    val videoFormat: String = "Auto",
    val videoCodec: String = "Auto",
    val videoFps: String = "Original",
    val videoAudioCodec: String = "Original",
    val subtitles: Boolean = false,
    val videoThumbnail: Boolean = true,
    val videoMetadata: Boolean = true,
    val audioFormat: String = "MP3",
    val audioBitrate: String = "320 kbps",
    val audioSampleRate: String = "Original",
    val audioChannels: String = "Original",
    val normalizeAudio: Boolean = false,
    val audioThumbnail: Boolean = true,
    val audioMetadata: Boolean = true
)

data class ScreenState(
    val url: String = "",
    val initialized: Boolean = false,
    val busy: Boolean = false,
    val libraryProgress: Float? = null,
    val message: String = "Starting downloader…",
    val preview: MediaPreview? = null,
    val mode: MediaMode = MediaMode.VIDEO,
    val quality: String = "Smart",
    val connectionType: String = "Checking connection…",
    val recommendedQuality: String = "480p",
    val tab: Int = 0,
    val history: List<DownloadRecord> = emptyList(),
    val duplicatePrompt: DuplicateDownloadPrompt? = null,
    val duplicateReplacementInProgress: Boolean = false,
    val themeMode: String = "System",
    val dynamicColor: Boolean = false,
    val customFileName: String = "",
    val downloadLocation: String = "Downloads/Downly",
    val advancedExpanded: Boolean = false,
    val qualityDropdownExpanded: Boolean = false,
    val historySearchQuery: String = "",
    val isSearchActive: Boolean = false,
    val historyFilter: String = "ALL",
    val downloadSettings: DownloadSettings = DownloadSettings()
)

