package com.sipun.downly.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sipun.downly.R
import com.sipun.downly.core.update.AppUpdate
import com.sipun.downly.core.update.DownloadProgress
import com.sipun.downly.presentation.theme.DownlyUiShapes
import com.sipun.downly.presentation.theme.downlyCardBorder
import com.sipun.downly.presentation.theme.downlyDialogBorder

@Composable
fun UpdateDialog(
    update: AppUpdate,
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    onDownload: () -> Unit,
    onInstall: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    ExpressiveBottomSheetDialog(
        onDismiss = onDismiss,
        title = stringResource(if (isDownloaded) R.string.update_ready else R.string.update_title),
        message = stringResource(R.string.update_version_available, update.version),
        confirmText = when {
            isDownloaded -> stringResource(R.string.update_install)
            isDownloading -> stringResource(R.string.update_downloading)
            else -> stringResource(R.string.update_download)
        },
        onConfirm = if (isDownloaded) onInstall else onDownload,
        dismissText = stringResource(R.string.update_later),
        onDismissClick = onDismiss,
        icon = {
            Icon(
                Icons.Default.NewReleases,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = DownlyUiShapes.nestedCard,
                    border = downlyCardBorder(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                    ),
                ) {
                    Column(
                        Modifier.padding(horizontal = 15.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        UpdateInfoRow(
                            Icons.Default.NewReleases,
                            stringResource(R.string.update_label_version),
                            update.version
                        )
                        UpdateInfoRow(
                            Icons.Default.Inventory2,
                            stringResource(R.string.update_label_download),
                            formatSize(update.size)
                        )
                        update.releaseDate?.let {
                            UpdateInfoRow(
                                Icons.Default.NewReleases,
                                stringResource(R.string.update_label_released),
                                it
                            )
                        }
                    }
                }
                if (update.notes.isNotBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            stringResource(R.string.update_whats_new),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 132.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            update.notes.lineSequence()
                                .filter { it.isNotBlank() }
                                .forEach { note ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            "•",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            note,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 12.sp,
                                        )
                                    }
                                }
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun DownloadProgressDialog(
    update: AppUpdate,
    appIcon: ImageBitmap,
    progress: DownloadProgress,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        modifier = Modifier.downlyDialogBorder(),
        icon = {
            Image(
                bitmap = appIcon,
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(62.dp),
            )
        },
        title = {
            Text(
                stringResource(R.string.update_downloading_title),
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            )
        },
        text = {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.update_version_name, update.version),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                )
                LinearProgressIndicator(
                    progress = { (progress.percent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(
                            R.string.update_progress_size,
                            formatSize(progress.downloadedBytes),
                            formatSize(if (progress.totalBytes > 0L) progress.totalBytes else update.size)
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                    Text(
                        stringResource(R.string.update_progress_percent, progress.percent),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                    )
                }
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = DownlyUiShapes.nestedCard,
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        shape = DownlyUiShapes.dialog,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    )
}

@Composable
private fun UpdateInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}

@Composable
private fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> stringResource(R.string.update_size_unknown)
    bytes < 1024L * 1024L -> stringResource(R.string.update_size_kb, bytes / 1024f)
    else -> stringResource(R.string.update_size_mb, bytes / (1024f * 1024f))
}
