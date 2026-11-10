package com.sipun.downly.core.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sipun.downly.MainActivity
import com.sipun.downly.R
import com.sipun.downly.core.service.DownloadWorkManager

object DownloadNotificationHelper {
    internal const val CHANNEL_ID = "media_downloads"
    internal const val ACTION_PAUSE = "com.sipun.downly.action.PAUSE_DOWNLOAD"
    internal const val ACTION_CANCEL = "com.sipun.downly.action.CANCEL_DOWNLOAD"
    internal const val ACTION_REMOVE = "com.sipun.downly.action.REMOVE_DOWNLOAD"
    internal const val EXTRA_NOTIFICATION_ID = "notification_id"
    internal const val EXTRA_TITLE = "title"
    internal const val EXTRA_DOWNLOAD_ID = "download_id"
    internal const val EXTRA_OPEN_HISTORY = "open_history"

    private val lastProgressNotifTime = java.util.concurrent.ConcurrentHashMap<Int, Long>()
    private val lastProgressNotifPercent = java.util.concurrent.ConcurrentHashMap<Int, Int>()

    fun createChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Media Downloads",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows progress and status of video and audio downloads"
                    setShowBadge(false)
                }
            )
        }
    }

    fun showProgress(
        context: Context,
        notificationId: Int,
        title: String,
        percent: Int,
        detail: String,
        downloadId: String? = null
    ) {
        createChannel(context)
        DownloadWorkManager.notifyProgress(notificationId, percent, detail)
        if (!canNotify(context)) return

        val now = System.currentTimeMillis()
        val lastTime = lastProgressNotifTime[notificationId] ?: 0L

        val shouldNotify = percent <= 0 || percent >= 100 ||
            ((now - lastTime) >= 1000L)

        if (shouldNotify) {
            lastProgressNotifTime[notificationId] = now
            lastProgressNotifPercent[notificationId] = percent
            runCatching {
                context.getSystemService(NotificationManager::class.java)
                    .notify(notificationId, buildProgressNotification(context, notificationId, title, percent, detail, downloadId))
            }
        }
    }

    fun buildProgressNotification(
        context: Context,
        notificationId: Int,
        title: String,
        percent: Int,
        detail: String,
        downloadId: String? = null
    ): android.app.Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(NotificationProgressFormatter.progressText(percent, detail))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setProgress(100, percent.coerceIn(0, 100), percent == 0)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Pause",
                actionPendingIntent(context, ACTION_PAUSE, notificationId, title, downloadId)
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                actionPendingIntent(context, ACTION_CANCEL, notificationId, title, downloadId)
            )

        return builder.build()
    }

    fun showPaused(context: Context, notificationId: Int, title: String, downloadId: String?) {
        lastProgressNotifTime.remove(notificationId)
        lastProgressNotifPercent.remove(notificationId)
        if (!canNotify(context)) return
        createChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_HISTORY, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText("Download paused")
            .setContentIntent(pendingIntent)
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setStyle(NotificationCompat.BigTextStyle().bigText("Download paused"))
            .addAction(
                android.R.drawable.ic_media_play,
                "Resume in app",
                pendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_delete,
                "Remove",
                actionPendingIntent(context, ACTION_REMOVE, notificationId, title, downloadId)
            )
        runCatching {
            context.getSystemService(NotificationManager::class.java).notify(notificationId, builder.build())
        }
    }

    fun showComplete(
        context: Context,
        notificationId: Int,
        title: String
    ) {
        lastProgressNotifTime.remove(notificationId)
        lastProgressNotifPercent.remove(notificationId)
        if (!canNotify(context)) return
        createChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Download Complete")
            .setContentText(title)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOngoing(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)

        runCatching {
            context.getSystemService(NotificationManager::class.java)
                .notify(notificationId, builder.build())
        }
    }

    fun showFailed(
        context: Context,
        notificationId: Int,
        title: String,
        error: String
    ) {
        lastProgressNotifTime.remove(notificationId)
        lastProgressNotifPercent.remove(notificationId)
        if (!canNotify(context)) return
        createChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Download Failed")
            .setContentText("$title: $error")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOngoing(false)
            .setCategory(NotificationCompat.CATEGORY_ERROR)

        runCatching {
            context.getSystemService(NotificationManager::class.java)
                .notify(notificationId, builder.build())
        }
    }

    fun cancel(context: Context, notificationId: Int) {
        lastProgressNotifTime.remove(notificationId)
        lastProgressNotifPercent.remove(notificationId)
        runCatching {
            context.getSystemService(NotificationManager::class.java).cancel(notificationId)
        }
    }

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    private fun actionPendingIntent(
        context: Context,
        action: String,
        notificationId: Int,
        title: String,
        downloadId: String?
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        notificationId xor action.hashCode(),
        Intent(context, DownloadNotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_DOWNLOAD_ID, downloadId)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

internal object NotificationProgressFormatter {
    fun progressText(
        percent: Int,
        detail: String,
        speed: String? = null,
        eta: String? = null
    ): String = listOfNotNull(
        "${percent.coerceIn(0, 100)}%",
        detail.takeIf { it.isNotBlank() },
        speed?.takeIf { it.isNotBlank() },
        eta?.takeIf { it.isNotBlank() }?.let { "ETA $it" }
    ).joinToString(" • ")
}
