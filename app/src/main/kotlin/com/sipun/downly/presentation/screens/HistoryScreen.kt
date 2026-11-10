package com.sipun.downly.presentation.screens

import com.sipun.downly.R

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.sipun.downly.data.DownloadRecord
import com.sipun.downly.data.DownloadStatus
import com.sipun.downly.data.DuplicateDownloadPrompt
import com.sipun.downly.data.ScreenState
import com.sipun.downly.presentation.DownlyViewModel
import com.sipun.downly.presentation.duplicateChoiceFor
import com.sipun.downly.utils.downloadTotalSize
import com.sipun.downly.presentation.theme.DownlyUiShapes
import com.sipun.downly.presentation.theme.downlyCardBorder
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private enum class HistoryFilter { ALL, VIDEO, AUDIO, FILES }

@Composable
fun ActivityScreen(state: ScreenState, vm: DownlyViewModel, modifier: Modifier) {
    var filter by remember { mutableStateOf(HistoryFilter.ALL) }
    val searchQuery = state.historySearchQuery.trim().lowercase(Locale.ROOT)
    val visibleRecords = state.history.filter { record ->
        val isAudio = isAudioRecord(record)
        val isFile = record.mode == com.sipun.downly.data.MediaMode.FILE
        val matchesFilter = when (filter) {
            HistoryFilter.ALL -> true
            HistoryFilter.VIDEO -> !isAudio && !isFile
            HistoryFilter.AUDIO -> isAudio
            HistoryFilter.FILES -> isFile || (!isAudio && record.filePath?.contains(".") == true)
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
                OutlinedButton(onClick = vm::clearFinished, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50), contentPadding = PaddingValues(vertical = 14.dp)) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
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
                            if (state.history.isEmpty()) "Videos and audio you download will show up here." else "Try a different media filter.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        if (state.history.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    Icon(Icons.Rounded.Download, contentDescription = null, tint = colorResource(R.color.downly_accent), modifier = Modifier.size(17.dp))
                                    Text(stringResource(R.string.paste_link_to_start), style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
        items(visibleRecords, key = { it.id }) { record -> DownloadCard(record, vm, duplicateChoiceFor(record, state.duplicatePrompt)) }
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
    val accentGreen = androidx.compose.ui.graphics.Color(0xFF22C55E)
    Surface(
        onClick = onClick, modifier = modifier.height(40.dp), shape = RoundedCornerShape(50),
        color = if (selected) accentGreen else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null,
                    tint = if (selected) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(label, color = if (selected) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1,
                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun DownloadCard(record: DownloadRecord, vm: DownlyViewModel, duplicatePrompt: DuplicateDownloadPrompt? = null) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val audio = isAudioRecord(record)
    val fileName = record.filePath?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: record.title
    var showCompletedMessage by remember(record.id, record.status) {
        mutableStateOf(record.status == DownloadStatus.COMPLETE)
    }
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
        value = withContext(Dispatchers.IO) { resolveMediaDetails(context, record.filePath, audio) }
    }
    Card(modifier = Modifier.fillMaxWidth().animateContentSize(), shape = DownlyUiShapes.card, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = downlyCardBorder()) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(width = 92.dp, height = 76.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (audio) androidx.compose.ui.graphics.Color(0xFFEBDDFF) else MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (details.artwork != null) {
                        Image(details.artwork!!.asImageBitmap(), contentDescription = stringResource(R.string.media_artwork),
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Icon(if (audio) Icons.Rounded.MusicNote else Icons.Rounded.VideoFile,
                            contentDescription = if (audio) stringResource(R.string.audio_file) else stringResource(R.string.video_file),
                            tint = if (audio) androidx.compose.ui.graphics.Color(0xFF8754B4) else colorResource(R.color.downly_accent),
                            modifier = Modifier.size(38.dp))
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(fileName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.InsertDriveFile, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        Text(details.fileType.ifBlank { if (audio) stringResource(R.string.media_mp3) else stringResource(R.string.media_video) }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        Icon(Icons.Rounded.Storage, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        Text(details.size.takeIf { it != "—" } ?: downloadTotalSize(record.detail) ?: "—", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Icon(Icons.Rounded.CalendarMonth, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(record.timestamp)), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f))
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
                            ) { Icon(Icons.Rounded.Refresh, null, Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text(stringResource(R.string.replace_download), maxLines = 1) }
                        }
                    }
                }
            } else if (record.status == DownloadStatus.QUEUED || record.status == DownloadStatus.DOWNLOADING) {
                LinearProgressIndicator(progress = { record.progress }, modifier = Modifier.fillMaxWidth(), color = colorResource(R.color.downly_accent))
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
                OutlinedButton(
                    onClick = { vm.pause(record) },
                    modifier = Modifier.align(Alignment.End),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(Icons.Rounded.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.pause_download))
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
                            tint = if (record.status == DownloadStatus.FAILED) MaterialTheme.colorScheme.error else colorResource(R.color.downly_accent),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(record.detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (record.status == DownloadStatus.FAILED || record.status == DownloadStatus.PAUSED) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { if (record.status == DownloadStatus.FAILED) vm.retry(record) else vm.resume(record) },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.downly_accent))
                        ) {
                            Icon(if (record.status == DownloadStatus.FAILED) Icons.Rounded.Refresh else Icons.Rounded.PlayArrow,
                                contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(if (record.status == DownloadStatus.FAILED) R.string.retry_download else R.string.resume_download))
                        }
                    }
                }
            }
            if (record.status == DownloadStatus.COMPLETE && record.filePath != null) {
                Button(onClick = { playMedia(context, record.filePath, audio) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.downly_accent)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)) {
                    Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(stringResource(R.string.play_action), maxLines = 1)
                }
            }
        }
    }
}

private data class MediaCardDetails(val artwork: Bitmap? = null, val size: String = "—", val fileType: String = "")

private fun isAudioRecord(record: DownloadRecord): Boolean {
    val path = record.filePath.orEmpty().lowercase(Locale.ROOT)
    return path.endsWith(".mp3") || path.endsWith(".m4a") || path.endsWith(".aac") ||
        path.endsWith(".opus") || path.endsWith(".ogg") || path.endsWith(".wav") || path.startsWith("Music/")
}

private fun resolveMediaDetails(context: Context, path: String?, audio: Boolean): MediaCardDetails {
    if (path.isNullOrBlank()) return MediaCardDetails(fileType = if (audio) "Audio" else "Video")
    val name = path.substringAfterLast('/')
    val relativePath = path.substringBeforeLast('/', if (audio) "Download/Downly/Music" else "Download/Downly/Videos") + "/"
    val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
    var artwork: Bitmap? = null
    var size = "—"
    var mime = name.substringAfterLast('.', "").uppercase(Locale.ROOT)
    runCatching {
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.MIME_TYPE)
        val selection = MediaStore.MediaColumns.DISPLAY_NAME + " = ? AND " + MediaStore.MediaColumns.RELATIVE_PATH + " = ?"
        context.contentResolver.query(collection, projection, selection, arrayOf(name, relativePath), null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                size = formatSize(cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)))
                mime = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE))?.substringAfter('/')?.uppercase(Locale.ROOT) ?: mime
                val uri = ContentUris.withAppendedId(collection, id)
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    val bytes = if (audio) retriever.getEmbeddedPicture() else null
                    artwork = if (bytes != null) android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    else if (!audio) retriever.getFrameAtTime() else null
                } finally { retriever.release() }
            }
        }
    }
    return MediaCardDetails(artwork, size, mime)
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> String.format(Locale.ROOT, "%.1f GB", bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> String.format(Locale.ROOT, "%.1f MB", bytes / 1_000_000.0)
    bytes >= 1_000 -> String.format(Locale.ROOT, "%.0f KB", bytes / 1_000.0)
    else -> bytes.toString() + " B"
}

private fun findMediaUri(context: Context, path: String, audio: Boolean): Uri? {
    val name = path.substringAfterLast('/')
    val relativePath = path.substringBeforeLast('/', if (audio) "Download/Downly/Music" else "Download/Downly/Videos") + "/"
    val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
    return runCatching {
        val selection = MediaStore.MediaColumns.DISPLAY_NAME + " = ? AND " + MediaStore.MediaColumns.RELATIVE_PATH + " = ?"
        context.contentResolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), selection, arrayOf(name, relativePath), null)?.use { cursor ->
            if (cursor.moveToFirst()) ContentUris.withAppendedId(collection, cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))) else null
        }
    }.getOrNull()
}

private fun playMedia(context: Context, path: String, audio: Boolean) {
    val uri = findMediaUri(context, path, audio)
    if (uri == null) {
        Toast.makeText(context, context.getString(R.string.file_not_found_media_library), Toast.LENGTH_LONG).show()
        return
    }
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, if (audio) "audio/*" else "video/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(intent) }.onFailure {
        Toast.makeText(context, context.getString(R.string.no_app_to_play_file), Toast.LENGTH_LONG).show()
    }
}
