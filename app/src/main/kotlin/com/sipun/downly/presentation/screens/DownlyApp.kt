package com.sipun.downly.presentation.screens

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.sipun.downly.R
import com.sipun.downly.data.MediaMode
import com.sipun.downly.presentation.DownlyViewModel
import com.sipun.downly.presentation.theme.DownlyTheme
import com.sipun.downly.presentation.theme.DownlyUiShapes
import com.sipun.downly.presentation.theme.downlyCardBorder
import com.sipun.downly.ui.update.UpdateContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private data class ExistingMediaDetails(
    val name: String,
    val mimeType: String,
    val sizeBytes: Long?,
    val modifiedMillis: Long?,
    val durationMillis: Long?
)

private fun loadExistingMediaDetails(
    context: Context,
    uriString: String,
    fallbackName: String
): ExistingMediaDetails {
    val uri = runCatching { Uri.parse(uriString) }.getOrNull()
    if (uri != null && uri.scheme == "file") {
        val file = File(uri.path.orEmpty())
        if (file.exists()) {
            val ext = file.extension.lowercase(Locale.ROOT)
            return ExistingMediaDetails(
                name = file.name,
                mimeType = ext.uppercase(Locale.ROOT),
                sizeBytes = file.length(),
                modifiedMillis = file.lastModified(),
                durationMillis = null
            )
        }
    }
    val projection = arrayOf(
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.MIME_TYPE,
        MediaStore.MediaColumns.SIZE,
        MediaStore.MediaColumns.DATE_MODIFIED,
        MediaStore.Video.VideoColumns.DURATION
    )
    return runCatching {
        if (uri == null) return@runCatching ExistingMediaDetails(fallbackName, "", null, null, null)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                fun longValue(column: String): Long? {
                    val index = cursor.getColumnIndex(column)
                    return if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else null
                }

                val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                ExistingMediaDetails(
                    name = if (nameIndex >= 0) cursor.getString(nameIndex)
                        ?: fallbackName else fallbackName,
                    mimeType = if (mimeIndex >= 0) cursor.getString(mimeIndex).orEmpty() else "",
                    sizeBytes = longValue(MediaStore.MediaColumns.SIZE),
                    modifiedMillis = longValue(MediaStore.MediaColumns.DATE_MODIFIED)?.let { if (it < 10_000_000_000L) it * 1000 else it },
                    durationMillis = longValue(MediaStore.Video.VideoColumns.DURATION)
                )
            } else ExistingMediaDetails(fallbackName, "", null, null, null)
        } ?: ExistingMediaDetails(fallbackName, "", null, null, null)
    }.getOrElse { ExistingMediaDetails(fallbackName, "", null, null, null) }
}


private fun loadExistingVideoThumbnail(context: Context, uriString: String): Bitmap? {
    val uri = Uri.parse(uriString)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val thumbnail = runCatching {
            context.contentResolver.loadThumbnail(uri, Size(480, 320), null)
        }.getOrNull()
        if (thumbnail != null) return thumbnail
    }
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    } catch (_: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

private fun formatFileSize(bytes: Long?): String {
    if (bytes == null || bytes < 0L) return "Unknown"
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.getDefault(), "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.getDefault(), "%.1f MB", mb)
    return String.format(Locale.getDefault(), "%.2f GB", mb / 1024.0)
}

private fun formatMediaDuration(milliseconds: Long?): String? {
    if (milliseconds == null || milliseconds <= 0L) return null
    val totalSeconds = milliseconds / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownlyApp(
    vm: DownlyViewModel = viewModel(),
    checkUpdatesRequested: Boolean = false,
    onCheckUpdatesRequestConsumed: () -> Unit = {},
    notificationUpdateRequested: Boolean = false,
    onNotificationUpdateRequestConsumed: () -> Unit = {},
    installUpdateRequested: Boolean = false,
    onInstallUpdateRequestConsumed: () -> Unit = {},
    openHistoryRequested: Boolean = false,
    onOpenHistoryRequestConsumed: () -> Unit = {},
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var requestAnotherUpdateCheck by remember { mutableStateOf(false) }
    var showBatteryPrompt by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.attach(context) }
    LaunchedEffect(openHistoryRequested) {
        if (openHistoryRequested) {
            vm.setTab(1)
            onOpenHistoryRequestConsumed()
        }
    }
    LaunchedEffect(state.initialized) {
        if (state.initialized) {
            delay(4500)
            if (state.duplicatePrompt == null && !com.sipun.downly.utils.isIgnoringBatteryOptimizations(
                    context
                )
            ) {
                showBatteryPrompt = true
            }
        }
    }
    LaunchedEffect(state.duplicatePrompt?.recordId) {
        if (state.duplicatePrompt != null) showBatteryPrompt = false
    }
    BackHandler(enabled = state.tab != 0) { vm.setTab(0) }

    val darkTheme = when (state.themeMode) {
        "Light" -> false
        "Dark", "Pure Black" -> true
        else -> isSystemInDarkTheme()
    }

    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as android.app.Activity).window
        androidx.compose.runtime.SideEffect {
            androidx.core.view.WindowCompat.getInsetsController(
                window,
                view
            ).isAppearanceLightStatusBars = !darkTheme
        }
    }

    DownlyTheme(
        darkTheme = darkTheme,
        dynamicColor = state.dynamicColor,
        pureBlack = state.themeMode == "Pure Black"
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        if (state.tab != 0) {
                            IconButton(onClick = { vm.setTab(0) }) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        }
                    },
                    title = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                when (state.tab) {
                                    1 -> "Downloads"
                                    2 -> stringResource(R.string.settings_title)
                                    else -> "Downly"
                                },
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                when (state.tab) {
                                    1 -> "Active and completed downloads"
                                    2 -> stringResource(R.string.settings_subtitle)
                                    else -> stringResource(R.string.home_tagline)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    actions = {
                        if (state.tab == 0) {
                            IconButton(onClick = { vm.setTab(1) }) {
                                Icon(
                                    Icons.Rounded.History,
                                    contentDescription = stringResource(R.string.download_history)
                                )
                            }
                            IconButton(onClick = { vm.setTab(2) }) {
                                Icon(
                                    Icons.Rounded.Settings,
                                    contentDescription = stringResource(R.string.settings_title)
                                )
                            }
                        } else if (state.tab == 1) {
                            IconButton(onClick = { vm.setIsSearchActive(!state.isSearchActive) }) {
                                Icon(Icons.Rounded.Search, contentDescription = "Search")
                            }
                            IconButton(onClick = { vm.setTab(2) }) {
                                Icon(
                                    Icons.Rounded.Settings,
                                    contentDescription = stringResource(R.string.settings_title)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        ) { padding ->
            AnimatedContent(
                targetState = state.tab,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                label = "downly_screen_transition"
            ) { tab ->
                when (tab) {
                    0 -> DownloadScreen(state, vm, Modifier.padding(padding))
                    1 -> ActivityScreen(state, vm, Modifier.padding(padding))
                    else -> SettingsScreen(
                        state,
                        vm,
                        Modifier.padding(padding),
                        onCheckForUpdates = { requestAnotherUpdateCheck = true })
                }
            }
        }

        UpdateContent(
            checkRequested = checkUpdatesRequested || requestAnotherUpdateCheck,
            onCheckRequestConsumed = {
                requestAnotherUpdateCheck = false
                onCheckUpdatesRequestConsumed()
            },
            onRequestCheck = { requestAnotherUpdateCheck = true },
            notificationRequested = notificationUpdateRequested,
            onNotificationRequestConsumed = onNotificationUpdateRequestConsumed,
            installRequested = installUpdateRequested,
            onInstallRequestConsumed = onInstallUpdateRequestConsumed,
        )

        if (showBatteryPrompt) {
            com.sipun.downly.ui.components.ExpressiveBottomSheetDialog(
                onDismiss = { showBatteryPrompt = false },
                title = stringResource(R.string.battery_optimization_dialog_title),
                message = stringResource(R.string.battery_optimization_dialog_message),
                confirmText = stringResource(R.string.battery_optimization_dialog_action),
                onConfirm = {
                    showBatteryPrompt = false
                    com.sipun.downly.utils.requestIgnoreBatteryOptimizations(context)
                },
                dismissText = stringResource(R.string.action_cancel),
                onDismissClick = { showBatteryPrompt = false },
                icon = {
                    Icon(
                        Icons.Rounded.Bolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            )
        }

        state.duplicatePrompt?.let { prompt ->
            val ext = prompt.fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
            val catInfo = com.sipun.downly.utils.resolveFileCategory(ext, "", prompt.mode)
            val targetFolder = catInfo.folderPath
            val details = remember(prompt.existingUri, prompt.fileName) {
                loadExistingMediaDetails(context, prompt.existingUri, prompt.fileName)
            }
            val isAudio = prompt.mode == MediaMode.AUDIO
            val duration = formatMediaDuration(details.durationMillis)
            val videoThumbnail by produceState<Bitmap?>(
                initialValue = null,
                prompt.existingUri,
                isAudio
            ) {
                value = if (isAudio) null else withContext(Dispatchers.IO) {
                    loadExistingVideoThumbnail(context, prompt.existingUri)
                }
            }
            Dialog(
                onDismissRequest = {
                    if (!state.duplicateReplacementInProgress) vm.resolveDuplicate(replaceExisting = false)
                },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.94f)
                        .widthIn(max = 560.dp),
                    shape = DownlyUiShapes.dialog,
                    color = MaterialTheme.colorScheme.surface,
                    border = downlyCardBorder(),
                    tonalElevation = 0.dp,
                    shadowElevation = 18.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF22C55E).copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Folder,
                                    contentDescription = null,
                                    tint = Color(0xFF22C55E),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    stringResource(R.string.file_already_exists),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "A file with this name is already in your library.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = DownlyUiShapes.card,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = downlyCardBorder()
                        ) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isAudio) {
                                        Box(
                                            Modifier
                                                .size(80.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(Color(0xFFEEDFFF)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Rounded.MusicNote,
                                                contentDescription = stringResource(R.string.audio_file),
                                                tint = colorResource(R.color.downly_audio_accent),
                                                modifier = Modifier.size(34.dp)
                                            )
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(width = 104.dp, height = 80.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(MaterialTheme.colorScheme.surfaceContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (videoThumbnail != null) {
                                                AsyncImage(
                                                    model = videoThumbnail,
                                                    contentDescription = stringResource(R.string.existing_video_thumbnail),
                                                    modifier = Modifier.fillMaxWidth(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Icon(
                                                    Icons.Rounded.VideoFile,
                                                    contentDescription = stringResource(R.string.video_file),
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(38.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(
                                        Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text(
                                            details.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.Folder,
                                                contentDescription = stringResource(R.string.download_folder),
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                targetFolder,
                                                color = MaterialTheme.colorScheme.primary,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        if (!isAudio && duration != null) {
                                            Text(
                                                duration,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                }
                                Surface(
                                    shape = DownlyUiShapes.nestedCard,
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    border = downlyCardBorder()
                                ) {
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(9.dp)
                                    ) {
                                        DetailRow(
                                            Icons.AutoMirrored.Rounded.InsertDriveFile,
                                            "File type",
                                            if (isAudio) "MP3 (Audio)" else details.mimeType.substringAfter(
                                                '/'
                                            ).uppercase().ifBlank { "Video" })
                                        DetailRow(
                                            Icons.Rounded.Storage,
                                            "File size",
                                            formatFileSize(details.sizeBytes)
                                        )
                                        DetailRow(
                                            Icons.Rounded.CalendarMonth,
                                            "Last modified",
                                            details.modifiedMillis?.let {
                                                DateFormat.getDateTimeInstance(
                                                    DateFormat.MEDIUM,
                                                    DateFormat.SHORT
                                                ).format(Date(it))
                                            } ?: "Unknown"
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            "Replace the existing file with this download, or keep the current one?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (state.duplicateReplacementInProgress) {
                            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "Replacing existing file…",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        } else Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.OutlinedButton(
                                onClick = { vm.resolveDuplicate(replaceExisting = false) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 8.dp,
                                    vertical = 13.dp
                                )
                            ) {
                                Text(
                                    stringResource(R.string.keep_existing),
                                    maxLines = 1,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Button(
                                onClick = { vm.resolveDuplicate(replaceExisting = true) },
                                modifier = Modifier.weight(1.12f),
                                shape = RoundedCornerShape(50),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 8.dp,
                                    vertical = 13.dp
                                ),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(
                                    Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    stringResource(R.string.replace_download),
                                    maxLines = 1,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .width(28.dp)
                .size(20.dp)
        )
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
