package com.sipun.downly.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sipun.downly.core.service.DownloadWorkManager
import com.sipun.youtubedl_android.YoutubeDL

class DownloadNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(DownloadNotificationHelper.EXTRA_NOTIFICATION_ID, 0)
        val title = intent.getStringExtra(DownloadNotificationHelper.EXTRA_TITLE).orEmpty()
        val downloadId =
            intent.getStringExtra(DownloadNotificationHelper.EXTRA_DOWNLOAD_ID).orEmpty()
        when (intent.action) {
            DownloadNotificationHelper.ACTION_PAUSE -> {
                DownloadWorkManager.requestPause(downloadId)
                DownloadWorkManager.cancel(context, downloadId)
                YoutubeDL.destroyProcessById(downloadId)
                DownloadNotificationHelper.showPaused(context, notificationId, title, downloadId)
            }

            DownloadNotificationHelper.ACTION_CANCEL,
            DownloadNotificationHelper.ACTION_REMOVE -> {
                DownloadWorkManager.cancel(context, downloadId)
                YoutubeDL.destroyProcessById(downloadId)
                DownloadNotificationHelper.cancel(context, notificationId)
            }
        }
    }
}
