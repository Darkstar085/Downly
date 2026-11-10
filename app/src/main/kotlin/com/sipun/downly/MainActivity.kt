package com.sipun.downly

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.sipun.downly.core.notification.DownloadNotificationHelper
import com.sipun.downly.core.update.UpdateManager
import com.sipun.downly.core.update.UpdateNotificationHelper
import com.sipun.downly.presentation.screens.DownlyApp

class MainActivity : ComponentActivity() {
    private val checkUpdatesRequested = mutableStateOf(false)
    private val notificationUpdateRequested = mutableStateOf(false)
    private val installUpdateRequested = mutableStateOf(false)
    private val openHistoryRequested = mutableStateOf(false)

    private val notificationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        UpdateNotificationHelper.createChannel(applicationContext)
        handleUpdateIntent(intent)
        handleDownloadIntent(intent)
        requestNotificationPermissionIfNeeded()
        setContent {
            DownlyApp(
                checkUpdatesRequested = checkUpdatesRequested.value,
                onCheckUpdatesRequestConsumed = { checkUpdatesRequested.value = false },
                notificationUpdateRequested = notificationUpdateRequested.value,
                onNotificationUpdateRequestConsumed = {
                    notificationUpdateRequested.value = false
                },
                installUpdateRequested = installUpdateRequested.value,
                onInstallUpdateRequestConsumed = { installUpdateRequested.value = false },
                openHistoryRequested = openHistoryRequested.value,
                onOpenHistoryRequestConsumed = { openHistoryRequested.value = false },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleUpdateIntent(intent)
        handleDownloadIntent(intent)
    }

    private fun handleDownloadIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(DownloadNotificationHelper.EXTRA_OPEN_HISTORY, false) == true) {
            openHistoryRequested.value = true
            intent.removeExtra(DownloadNotificationHelper.EXTRA_OPEN_HISTORY)
        }
    }

    private fun handleUpdateIntent(intent: Intent?) {
        when (intent?.action) {
            UpdateManager.ACTION_SHOW_UPDATE -> notificationUpdateRequested.value = true
            UpdateManager.ACTION_INSTALL_UPDATE -> installUpdateRequested.value = true
            UpdateManager.ACTION_DOWNLOAD_UPDATE -> checkUpdatesRequested.value = true
        }
        if (
            intent?.action == UpdateManager.ACTION_SHOW_UPDATE ||
            intent?.action == UpdateManager.ACTION_INSTALL_UPDATE ||
            intent?.action == UpdateManager.ACTION_DOWNLOAD_UPDATE
        ) {
            intent.action = null
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
