package com.sipun.downly.presentation.screens

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sipun.downly.R
import com.sipun.downly.data.DownloadRecord
import com.sipun.downly.data.DownloadStatus
import com.sipun.downly.data.DuplicateDownloadPrompt
import com.sipun.downly.data.MediaMode
import com.sipun.downly.data.ScreenState
import com.sipun.downly.presentation.DownlyViewModel
import com.sipun.downly.presentation.duplicateChoiceFor
import com.sipun.downly.presentation.theme.DownlyUiShapes
import com.sipun.downly.presentation.theme.downlyCardBorder
import com.sipun.downly.utils.downloadTotalSize
import com.sipun.downly.utils.resolveFileCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private enum class HistoryFilter { ALL, VIDEO, AUDIO, FILES }

fun isAudioRecord(record: DownloadRecord): Boolean {
    if (record.mode == MediaMode.AUDIO) return true
    val path = record.filePath.orEmpty().lowercase(Locale.ROOT)
    val url = record.url.lowercase(Locale.ROOT)
    val audioExts = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "wma", "m4b")
    val ext = path.substringAfterLast('.', url.substringAfterLast('.', "")).substringBefore('?')
    return ext in audioExts || path.contains("/music/")
}

fun isVideoRecord(record: DownloadRecord): Boolean {
    if (record.mode == MediaMode.VIDEO) return true
    val path = record.filePath.orEmpty().lowercase(Locale.ROOT)
    val url = record.url.lowercase(Locale.ROOT)
    val videoExts = setOf("mp4", "mkv", "webm", "avi", "mov", "flv", "wmv", "3gp", "m4v", "ts")
    val ext = path.substringAfterLast('.', url.substringAfterLast('.', "")).substringBefore('?')
    return ext in videoExts || path.contains("/videos/")
}

fun isFileRecord(record: DownloadRecord): Boolean {
    return !isVideoRecord(record) && !isAudioRecord(record)
}

@Composable
fun ActivityScreen(state: ScreenState, vm: DownlyViewModel, modifier: Modifier) {
    var filter by remember { mutableStateOf(HistoryFilter.ALL) }
    val searchQuery = state.historySearchQuery.trim().lowercase(Locale.ROOT)
    val visibleRecords = state.history.filter { record ->
        val isAudio = isAudioRecord(record)
        val isVideo = isVideoRecord(record)
        val isFile = isFileRecord(record)
        val matchesFilter = when (filter) {
            HistoryFilter.ALL -> true
            HistoryFilter.VIDEO -> isVideo
            HistoryFilter.AUDIO -> isAudio
            HistoryFilter.FILES -> isFile
        }
        val matchesSearch = searchQuery.isBlank() ||
            record.title.lowercase(Locale.ROOT).contains(searchQuery) ||
            record.url.lowercase(Locale.ROOT).contains(searchQuery)
        matchesFilter && matchesSearch
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (state.isSearchActive) {
            item {
                OutlinedTextField(
                    value = state.historySearchQuery,
                    onValueChange = vm::setHistorySearchQuery,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search downloads…") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        IconButton(onClick = {
                            vm.setHistorySearchQuery("")
                            vm.setIsSearchActive(false)
                        }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close search")
                        }
                    }
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                HistoryFilterChip("All", filter == HistoryFilter.ALL, Modifier.weight(1f)) { filter = HistoryFilter.ALL }
                HistoryFilterChip("Video", filter == HistoryFilter.VIDEO, Modifier.weight(1f), Icons.Rounded.VideoFile) { filter = HistoryFilter.VIDEO }
                HistoryFilterChip("Audio", filter == HistoryFilter.AUDIO, Modifier.weight(1f), Icons.Rounded.MusicNote) { filter = HistoryFilter.AUDIO }
                HistoryFilterChip("Files", filter == HistoryFilter.FILES, Modifier.weight(1f), Icons.AutoMirrored.Rounded.InsertDriveFile) { filter = HistoryFilter.FILES }
            }
        }
        if (state.history.any { it.status == DownloadStatus.COMPLETE || it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELLED }) {
            item {
                OutlinedButton(
                    onClick = vm::clearFinished,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.clear_finished_failed), fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (visibleRecords.isEmpty()) item {
            Box(
                modifier = Modifier.fillParentMaxHeight(0.52f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = DownlyUiShapes.card,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = downlyCardBorder()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 30.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(76.dp).clip(RoundedCornerShape(26.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.History,
                                contentDescription = null,
                                tint = colorResource(R.color.downly_accent),
                                modifier = Modifier.size(38.dp)
                            )
                        }
                        Text(
                            if (state.history.isEmpty()) "Your library is waiting" else "No downloads found",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            if (state.history.isEmpty()) "Videos, audio and files you download will show up here." else "Try a different filter.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        items(visibleRecords, key = { it.id }) { record ->
            DownloadCard(record, vm, duplicateChoiceFor(record, state.duplicatePrompt))
        }
    }
}

@Composable
private fun HistoryFilterChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    val accentGreen = Color(0xFF22C55E)
    Surface(
        onClick = onClick, modifier = modifier.height(40.dp), shape = RoundedCornerShape(50),
        color = if (selected) accentGreen else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    icon, contentDescription = null,
                    tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                label,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
fun DownloadCard(record: DownloadRecord, vm: DownlyViewModel, duplicatePrompt: DuplicateDownloadPrompt? = null) {
    val context = LocalContext.current
    val audio = isAudioRecord(record)
    val fileName = record.filePath?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: record.title
    var showCompletedMessage by remember(record.id, record.status) {
        mutableStateOf(record.status == DownloadStatus.COMPLETE)
    }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteStorageFile by remember { mutableStateOf(false) }

    LaunchedEffect(record.id, record.status) {
        if (record.status == DownloadStatus.COMPLETE) {
            showCompletedMessage = true
            delay(5_000)
            showCompletedMessage = false
        } else {
            showCompletedMessage = false
        }
    }

    val details by produceState(initialValue = MediaCardDetails(), key1 = record.filePath) {
        value = withContext(Dispatchers.IO) { resolveMediaDetails(context, record.filePath, record) }
    }

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = DownlyUiShapes.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = downlyCardBorder()
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Thumbnail / Artwork / Icon Container Box
                Box(
                    Modifier
                        .size(width = 92.dp, height = 76.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (details.artwork != null) {
                        Image(
                            details.artwork!!.asImageBitmap(),
                            contentDescription = "Media artwork",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            details.icon,
                            contentDescription = null,
                            tint = details.iconTint,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Info Column
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        fileName,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.InsertDriveFile, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                        Text(details.fileType, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        Icon(Icons.Rounded.Storage, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                        Text(details.size.takeIf { it != "—" } ?: downloadTotalSize(record.detail) ?: "—", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.CalendarMonth, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                        Text(
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(record.timestamp)),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (record.status == DownloadStatus.QUEUED || record.status == DownloadStatus.DOWNLOADING) {
                    IconButton(onClick = { vm.cancel(record) }) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.cancel_download))
                    }
                }
            }

            if (duplicatePrompt != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f))
                ) {
                    Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Duplicate file found", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onTertiaryContainer, fontWeight = FontWeight.SemiBold)
                        Text("Choose whether to keep the existing file or replace it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { vm.resolveDuplicate(replaceExisting = false) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(vertical = 9.dp)
                            ) { Text(stringResource(R.string.keep_existing), maxLines = 1) }
                            Button(
                                onClick = { vm.resolveDuplicate(replaceExisting = true) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(vertical = 9.dp)
                            ) {
                                Icon(Icons.Rounded.Refresh, null, Modifier.size(17.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(stringResource(R.string.replace_download), maxLines = 1)
                            }
                        }
                    }
                }
            } else if (record.status == DownloadStatus.QUEUED || record.status == DownloadStatus.DOWNLOADING) {
                LinearProgressIndicator(progress = { record.progress }, modifier = Modifier.fillMaxWidth(), color = Color(0xFF22C55E))
                val cleanedDetail = record.detail
                    .replace("KiB", "KB")
                    .replace("MiB", "MB")
                    .replace("GiB", "GB")
                    .replace("TiB", "TB")
                val progressText = if (cleanedDetail.contains("%")) {
                    cleanedDetail
                } else {
                    "${(record.progress * 100).toInt()}% · $cleanedDetail"
                }
                Text(progressText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                    }
                    OutlinedButton(
                        onClick = { vm.pause(record) },
                        shape = RoundedCornerShape(50)
                    ) {
                        Icon(Icons.Rounded.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.pause_download))
                    }
                }
            } else {
                if (record.status != DownloadStatus.COMPLETE || showCompletedMessage) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            when (record.status) {
                                DownloadStatus.COMPLETE -> Icons.Rounded.CheckCircle
                                DownloadStatus.FAILED -> Icons.Rounded.Refresh
                                else -> Icons.Rounded.Download
                            },
                            contentDescription = null,
                            tint = if (record.status == DownloadStatus.FAILED) MaterialTheme.colorScheme.error else Color(0xFF22C55E),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            record.detail.replace("KiB", "KB").replace("MiB", "MB").replace("GiB", "GB"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Action Button (Play / Resume / Retry)
                    if (record.status == DownloadStatus.COMPLETE && record.filePath != null) {
                        Button(
                            onClick = { playMedia(context, record.filePath, audio) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(stringResource(R.string.play_action), maxLines = 1)
                        }
                    } else if (record.status == DownloadStatus.FAILED || record.status == DownloadStatus.PAUSED) {
                        Button(
                            onClick = { if (record.status == DownloadStatus.FAILED) vm.retry(record) else vm.resume(record) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))
                        ) {
                            Icon(
                                if (record.status == DownloadStatus.FAILED) Icons.Rounded.Refresh else Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(if (record.status == DownloadStatus.FAILED) R.string.retry_download else R.string.resume_download))
                        }
                    }

                    // Remove Button beside Play/Resume button
                    OutlinedButton(
                        onClick = { showDeleteDialog = true },
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }

    // Remove Confirmation Dialog with Storage Delete Checkbox
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Remove download", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Are you sure you want to remove this item from your download list?")
                    if (!record.filePath.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { deleteStorageFile = !deleteStorageFile }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = deleteStorageFile,
                                onCheckedChange = { deleteStorageFile = it },
                                colors = CheckboxDefaults.colors(checkedColor = Color(0xFF22C55E))
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Also delete file from storage", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        vm.removeRecord(record, deleteStorageFile)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Remove", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private data class MediaCardDetails(
    val artwork: Bitmap? = null,
    val size: String = "—",
    val fileType: String = "",
    val icon: ImageVector = Icons.AutoMirrored.Rounded.InsertDriveFile,
    val iconTint: Color = Color(0xFF22C55E)
)

private fun resolveMediaDetails(context: Context, path: String?, record: DownloadRecord): MediaCardDetails {
    val cleanPath = path.orEmpty().lowercase(Locale.ROOT)
    val ext = cleanPath.substringAfterLast('.', "").ifBlank { record.title.substringAfterLast('.', "").lowercase(Locale.ROOT) }

    val catInfo = resolveFileCategory(ext, "", record.mode)

    var artwork: Bitmap? = null
    var size = "—"
    var mime = ext.uppercase(Locale.ROOT)

    if (!path.isNullOrBlank()) {
        val file = File(path)
        if (file.exists() && file.isFile) {
            size = formatSize(file.length())
        }

        // 1. Try Image thumbnail
        if (ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")) {
            artwork = runCatching {
                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }
                android.graphics.BitmapFactory.decodeFile(path, opts)
            }.getOrNull()
        }

        // 2. Try Video Frame via MediaMetadataRetriever
        if (artwork == null && (record.mode == MediaMode.VIDEO || ext in setOf("mp4", "mkv", "webm", "avi", "mov", "flv", "3gp", "m4v"))) {
            artwork = runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    val uri = findMediaUri(context, path, false)
                    if (uri != null) retriever.setDataSource(context, uri)
                    else retriever.setDataSource(path)
                    retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } finally {
                    runCatching { retriever.release() }
                }
            }.getOrNull()
        }

        // 3. Try Audio Embedded Picture
        if (artwork == null && (record.mode == MediaMode.AUDIO || ext in setOf("mp3", "m4a", "wav", "flac", "aac", "ogg", "opus"))) {
            artwork = runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    val uri = findMediaUri(context, path, true)
                    if (uri != null) retriever.setDataSource(context, uri)
                    else retriever.setDataSource(path)
                    val bytes = retriever.embeddedPicture
                    if (bytes != null) android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) else null
                } finally {
                    runCatching { retriever.release() }
                }
            }.getOrNull()
        }
    }

    val (icon, iconTint) = when (catInfo.categoryName) {
        "Video" -> Icons.Rounded.VideoFile to Color(0xFF22C55E)
        "Audio" -> Icons.Rounded.MusicNote to Color(0xFF8C55B5)
        "Photos" -> Icons.Rounded.Image to Color(0xFF14B8A6)
        "Documents" -> Icons.Rounded.Description to Color(0xFF3B82F6)
        "Compressed" -> Icons.Rounded.FolderZip to Color(0xFFF59E0B)
        "Programs" -> Icons.Rounded.Android to Color(0xFF10B981)
        "Torrents" -> Icons.Rounded.Download to Color(0xFFA855F7)
        else -> Icons.AutoMirrored.Rounded.InsertDriveFile to Color(0xFF64748B)
    }

    return MediaCardDetails(
        artwork = artwork,
        size = size,
        fileType = if (mime.isNotBlank()) mime else catInfo.badgeLabel,
        icon = icon,
        iconTint = iconTint
    )
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> String.format(Locale.ROOT, "%.1f GB", bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> String.format(Locale.ROOT, "%.1f MB", bytes / 1_000_000.0)
    bytes >= 1_000 -> String.format(Locale.ROOT, "%.0f KB", bytes / 1_000.0)
    else -> "$bytes B"
}

private fun findMediaUri(context: Context, path: String, audio: Boolean): Uri? {
    val name = path.substringAfterLast('/')
    val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
    return runCatching {
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
        context.contentResolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), selection, arrayOf(name), null)?.use { cursor ->
            if (cursor.moveToFirst()) ContentUris.withAppendedId(collection, cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))) else null
        }
    }.getOrNull()
}

private fun playMedia(context: Context, path: String, audio: Boolean) {
    val uri = findMediaUri(context, path, audio)
    val intent = if (uri != null) {
        Intent(Intent.ACTION_VIEW).setDataAndType(uri, if (audio) "audio/*" else "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } else {
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(context, context.getString(R.string.file_not_found_media_library), Toast.LENGTH_LONG).show()
            return
        }
        val fileUri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        Intent(Intent.ACTION_VIEW).setDataAndType(fileUri, if (audio) "audio/*" else "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    runCatching { context.startActivity(intent) }.onFailure {
        Toast.makeText(context, context.getString(R.string.no_app_to_play_file), Toast.LENGTH_LONG).show()
    }
}
