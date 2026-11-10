package com.sipun.downly.core.service

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sipun.downly.core.notification.DownloadNotificationHelper
import com.sipun.downly.data.DownloadRecord
import com.sipun.downly.data.MediaMode
import com.sipun.downly.presentation.DownlyViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

private const val ID_KEY = "download_id"
private const val URL_KEY = "download_url"
private const val TITLE_KEY = "download_title"
private const val MODE_KEY = "download_mode"
private const val QUALITY_KEY = "download_quality"

object DownloadWorkManager {
    private val completionListeners = CopyOnWriteArraySet<(String) -> Unit>()
    private val progressListeners = CopyOnWriteArraySet<(Int, Int, String) -> Unit>()
    private val pauseRequests = ConcurrentHashMap.newKeySet<String>()

    fun enqueue(context: Context, record: DownloadRecord) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(
                workDataOf(
                    ID_KEY to record.id,
                    URL_KEY to record.url,
                    TITLE_KEY to record.title,
                    MODE_KEY to record.mode.name,
                    QUALITY_KEY to record.quality,
                )
            )
            .addTag("downly-download")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "downly-download-${record.id}",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: Context, id: String) {
        WorkManager.getInstance(context).cancelUniqueWork("downly-download-$id")
    }

    fun requestPause(id: String) {
        pauseRequests += id
    }

    fun consumePauseRequest(id: String): Boolean = pauseRequests.remove(id)

    fun addCompletionListener(listener: (String) -> Unit): () -> Unit {
        completionListeners += listener
        return { completionListeners -= listener }
    }

    fun notifyFinished(id: String) {
        completionListeners.forEach { listener -> listener(id) }
    }

    fun addProgressListener(listener: (Int, Int, String) -> Unit): () -> Unit {
        progressListeners += listener
        return { progressListeners -= listener }
    }

    fun notifyProgress(notificationId: Int, percent: Int, detail: String) {
        progressListeners.forEach { listener -> listener(notificationId, percent, detail) }
    }
}

class DownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val id = inputData.getString(ID_KEY).orEmpty()
        DownloadNotificationHelper.createChannel(applicationContext)
        return ForegroundInfo(
            id.hashCode(),
            DownloadNotificationHelper.buildProgressNotification(
                applicationContext,
                id.hashCode(),
                inputData.getString(TITLE_KEY).orEmpty().ifBlank { "Downloading media…" },
                0,
                "Starting download…",
                id
            ),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getString(ID_KEY) ?: return@withContext Result.failure()
        val mode =
            inputData.getString(MODE_KEY)?.let { runCatching { MediaMode.valueOf(it) }.getOrNull() }
                ?: return@withContext Result.failure()
        val record = DownloadRecord(
            id = id,
            url = inputData.getString(URL_KEY).orEmpty(),
            title = inputData.getString(TITLE_KEY).orEmpty(),
            status = com.sipun.downly.data.DownloadStatus.QUEUED,
            mode = mode,
            quality = inputData.getString(QUALITY_KEY).orEmpty()
        )
        runCatching {
            setForeground(getForegroundInfo())
        }

        val viewModel = DownlyViewModel()
        viewModel.attach(applicationContext, observeDownloadCompletion = false)
        var attempts = 0
        while (!viewModel.state.value.initialized && attempts++ < 120) {
            delay(500)
        }
        if (!viewModel.state.value.initialized) return@withContext Result.retry()

        viewModel.executeDownloadFromWorker(
            record,
            mode,
            record.quality
        )
        viewModel.flushHistoryForWorker(id)
        DownloadWorkManager.notifyFinished(id)
        Result.success()
    }
}
