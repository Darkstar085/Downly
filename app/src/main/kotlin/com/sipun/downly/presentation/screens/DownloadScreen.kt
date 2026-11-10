package com.sipun.downly.presentation.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sipun.downly.R
import com.sipun.downly.data.MediaMode
import com.sipun.downly.data.ScreenState
import com.sipun.downly.presentation.DownlyViewModel
import com.sipun.downly.utils.formatBytes

@Composable
fun DownloadScreen(
    state: ScreenState,
    vm: DownlyViewModel,
    modifier: Modifier
) {
    val context = LocalContext.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val accentGreen = Color(0xFF22C55E)

    LaunchedEffect(Unit) {
        focusManager.clearFocus()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth().animateContentSize(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(accentGreen.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Link,
                                contentDescription = null,
                                tint = accentGreen,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Paste a link",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "YouTube, websites or direct file links",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                                if (text.isNotBlank()) vm.setUrl(text.trim())
                                else Toast.makeText(context, context.getString(R.string.clipboard_empty), Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                Icons.Rounded.ContentPaste,
                                contentDescription = "Paste",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(10.dp).size(20.dp)
                            )
                        }
                    }

                    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

                    OutlinedTextField(
                        value = state.url,
                        onValueChange = vm::setUrl,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        trailingIcon = {
                            if (state.url.isNotBlank()) {
                                IconButton(onClick = { vm.setUrl("") }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentGreen,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            cursorColor = accentGreen,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            vm.fetchInfo()
                        })
                    )

                    Button(
                        onClick = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            vm.fetchInfo()
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        enabled = !state.busy && state.url.isNotBlank(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentGreen,
                            contentColor = Color.White,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        if (state.busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            val btnText = if (!state.initialized) "Installing libraries..." else "Fetching..."
                            Text(btnText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        } else {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Fetch", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }

                    if (state.busy) {
                        val subText = if (!state.initialized) {
                            state.message.ifBlank { "Setting up YouTube-DL & FFmpeg for the first time. Please wait..." }
                        } else {
                            "Analyzing link and getting download options..."
                        }
                        Text(
                            subText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        if (state.preview == null && !state.busy) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Supported Formats",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CategoryCard(
                            icon = Icons.Rounded.PlayArrow,
                            iconTint = Color(0xFF22C55E),
                            title = "Videos",
                            subtitle = "YouTube, MP4, MKV...",
                            modifier = Modifier.weight(1f)
                        )
                        CategoryCard(
                            icon = Icons.Rounded.MusicNote,
                            iconTint = Color(0xFF8C55B5),
                            title = "Music",
                            subtitle = "MP3, M4A, FLAC...",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CategoryCard(
                            icon = Icons.Rounded.Image,
                            iconTint = Color(0xFF14B8A6),
                            title = "Images",
                            subtitle = "JPG, PNG, WEBP...",
                            modifier = Modifier.weight(1f)
                        )
                        CategoryCard(
                            icon = Icons.Rounded.Description,
                            iconTint = Color(0xFF3B82F6),
                            title = "Documents",
                            subtitle = "PDF, DOC, XLS, TXT...",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CategoryCard(
                            icon = Icons.Rounded.FolderZip,
                            iconTint = Color(0xFFF59E0B),
                            title = "Archives",
                            subtitle = "ZIP, RAR, 7Z, ISO...",
                            modifier = Modifier.weight(1f)
                        )
                        CategoryCard(
                            icon = Icons.Rounded.Android,
                            iconTint = Color(0xFF10B981),
                            title = "Apps & Tools",
                            subtitle = "APK, EXE, DEB...",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        if (state.preview != null && !state.preview.isDirectFile) {
            val preview = state.preview

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 110.dp, height = 74.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!preview.thumbnailUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = preview.thumbnailUrl,
                                    contentDescription = "Thumbnail",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    Icons.Rounded.Movie,
                                    contentDescription = null,
                                    tint = accentGreen,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            if (preview.duration.isNotBlank()) {
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color.Black.copy(alpha = 0.78f)
                                ) {
                                    Text(
                                        preview.duration,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                preview.title,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFF0000)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                val maxQuality = preview.qualityOptions.firstOrNull()?.quality ?: "1080p"
                                val estBytes = preview.qualityOptions.firstOrNull()?.estimatedBytes
                                val estSizeStr = estBytes?.let { formatBytes(it) } ?: "1.2 GB"
                                Text(
                                    "${preview.uploader.ifBlank { "YouTube" }} • $maxQuality • $estSizeStr",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Download as",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val isVideo = state.mode == MediaMode.VIDEO
                        Surface(
                            onClick = { vm.setMode(MediaMode.VIDEO) },
                            modifier = Modifier.weight(1f).height(80.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = if (isVideo) accentGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(
                                if (isVideo) 2.dp else 1.dp,
                                if (isVideo) accentGreen else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Movie,
                                        contentDescription = null,
                                        tint = if (isVideo) accentGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        "Video",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isVideo) accentGreen else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    "(with audio)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        val isAudio = state.mode == MediaMode.AUDIO
                        Surface(
                            onClick = { vm.setMode(MediaMode.AUDIO) },
                            modifier = Modifier.weight(1f).height(80.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = if (isAudio) accentGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(
                                if (isAudio) 2.dp else 1.dp,
                                if (isAudio) accentGreen else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.MusicNote,
                                        contentDescription = null,
                                        tint = if (isAudio) accentGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        "Audio",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isAudio) accentGreen else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    "(audio only) (MP3, M4A, etc.)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Quality",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Box {
                        Surface(
                            onClick = { vm.setQualityDropdownExpanded(!state.qualityDropdownExpanded) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val currentLabel = if (state.mode == MediaMode.VIDEO) {
                                    val opt = preview.qualityOptions.firstOrNull { it.quality == state.recommendedQuality }
                                    if (opt != null) "${opt.quality} (Recommended)" else "1080p (Recommended)"
                                } else {
                                    "Highest Audio Quality"
                                }
                                Text(
                                    currentLabel,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Icon(
                                    Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = "Expand",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = state.qualityDropdownExpanded,
                            onDismissRequest = { vm.setQualityDropdownExpanded(false) }
                        ) {
                            if (state.mode == MediaMode.VIDEO) {
                                preview.qualityOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text("${option.quality} ${if (option.quality == state.recommendedQuality) " (Recommended)" else ""}") },
                                        onClick = {
                                            vm.setQuality("video:${option.formatSelector}")
                                            vm.setQualityDropdownExpanded(false)
                                        }
                                    )
                                }
                            } else {
                                preview.audioOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text("${option.quality} (${option.formatId})") },
                                        onClick = {
                                            vm.setQuality("audio:${option.formatId}")
                                            vm.setQualityDropdownExpanded(false)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                AdvancedDownloadSettings(
                    mode = state.mode,
                    settings = state.downloadSettings,
                    onSettingsChange = vm::setDownloadSettings
                )
            }

            item {
                Button(
                    onClick = vm::startBestDownload,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentGreen, contentColor = Color.White)
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Download", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }

        if (state.preview != null && state.preview.isDirectFile) {
            val preview = state.preview

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.InsertDriveFile,
                                contentDescription = null,
                                tint = accentGreen,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                state.customFileName.ifBlank { preview.directFileName },
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "Direct file • ${preview.mimeType.ifBlank { "application/octet-stream" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = accentGreen.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        preview.fileCategory,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = accentGreen,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer
                                ) {
                                    Text(
                                        preview.fileSize?.let { formatBytes(it) } ?: "82.4 MB",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Download to",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Rounded.Folder, contentDescription = null, tint = accentGreen, modifier = Modifier.size(20.dp))
                                Text(
                                    state.downloadLocation,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "File name",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    OutlinedTextField(
                        value = state.customFileName,
                        onValueChange = vm::setCustomFileName,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentGreen,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            cursorColor = accentGreen,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    )
                }
            }

            item {
                Surface(
                    onClick = { vm.setAdvancedExpanded(!state.advancedExpanded) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Rounded.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                Text("Advanced options", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                            }
                            Icon(
                                if (state.advancedExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (state.advancedExpanded) {
                            Spacer(Modifier.height(10.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            Spacer(Modifier.height(10.dp))
                            Text("MIME type: ${preview.mimeType}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Direct URL download", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = vm::startBestDownload,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentGreen, contentColor = Color.White)
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Download", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}


@Composable
private fun AdvancedDownloadSettings(
    mode: MediaMode,
    settings: com.sipun.downly.data.DownloadSettings,
    onSettingsChange: (com.sipun.downly.data.DownloadSettings) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedTab by remember(mode) { mutableStateOf(if (mode == MediaMode.AUDIO) 1 else 0) }
    val showVideo = mode == MediaMode.VIDEO && selectedTab == 0
    val showAudio = mode == MediaMode.AUDIO || selectedTab == 1

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Advanced options", modifier = Modifier.weight(1f).padding(start = 8.dp), fontWeight = FontWeight.SemiBold)
                if (expanded) {
                    TextButton(onClick = { onSettingsChange(com.sipun.downly.data.DownloadSettings()) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Text("Reset")
                    }
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = if (expanded) "Collapse advanced options" else "Expand advanced options")
                }
            }
            if (expanded) {
                if (mode == MediaMode.VIDEO) {
                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Video") })
                        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Audio") })
                    }
                } else {
                    Text("Audio", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                if (showVideo) {
                    SettingRow("Container", settings.videoFormat, listOf("Auto", "MP4", "MKV", "WebM")) {
                        onSettingsChange(settings.copy(videoFormat = it))
                    }
                    SettingRow("Video codec", settings.videoCodec, listOf("Auto", "H.264", "H.265", "AV1")) {
                        onSettingsChange(settings.copy(videoCodec = it))
                    }
                    SettingRow("FPS preference", settings.videoFps, listOf("Original", "30 fps", "60 fps")) {
                        onSettingsChange(settings.copy(videoFps = it))
                    }
                    SettingRow("Audio codec", settings.videoAudioCodec, listOf("Original", "AAC", "Opus")) {
                        onSettingsChange(settings.copy(videoAudioCodec = it))
                    }
                    SettingToggleRow("Subtitles", settings.subtitles) { onSettingsChange(settings.copy(subtitles = it)) }
                    SettingToggleRow("Embed thumbnail", settings.videoThumbnail) { onSettingsChange(settings.copy(videoThumbnail = it)) }
                    SettingToggleRow("Embed metadata", settings.videoMetadata) { onSettingsChange(settings.copy(videoMetadata = it)) }
                }
                if (showAudio) {
                    SettingRow("Format", settings.audioFormat, listOf("MP3", "M4A", "AAC", "FLAC", "WAV", "Opus")) {
                        onSettingsChange(settings.copy(audioFormat = it))
                    }
                    SettingRow("Bitrate", settings.audioBitrate, listOf("96 kbps", "128 kbps", "192 kbps", "256 kbps", "320 kbps")) {
                        onSettingsChange(settings.copy(audioBitrate = it))
                    }
                    SettingRow("Sample rate", settings.audioSampleRate, listOf("Original", "44.1 kHz", "48 kHz")) {
                        onSettingsChange(settings.copy(audioSampleRate = it))
                    }
                    SettingRow("Channels", settings.audioChannels, listOf("Original", "Stereo", "Mono")) {
                        onSettingsChange(settings.copy(audioChannels = it))
                    }
                    SettingToggleRow("Normalize volume", settings.normalizeAudio) { onSettingsChange(settings.copy(normalizeAudio = it)) }
                    SettingToggleRow("Embed thumbnail", settings.audioThumbnail) { onSettingsChange(settings.copy(audioThumbnail = it)) }
                    SettingToggleRow("Embed metadata", settings.audioMetadata) { onSettingsChange(settings.copy(audioMetadata = it)) }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, value: String, options: List<String>, onSelect: (String) -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        Box {
            TextButton(onClick = { menuExpanded = true }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                Text(value, style = MaterialTheme.typography.labelLarge)
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = { onSelect(option); menuExpanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun CategoryCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconTint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 11.sp
                )
            }
        }
    }
}
