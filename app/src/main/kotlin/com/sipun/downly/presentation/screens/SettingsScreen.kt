package com.sipun.downly.presentation.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.ImageView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import com.sipun.downly.BuildConfig
import com.sipun.downly.R
import com.sipun.downly.data.ScreenState
import com.sipun.downly.presentation.DownlyViewModel
import com.sipun.downly.presentation.theme.DownlyUiShapes
import com.sipun.downly.presentation.theme.downlyCardBorder
import com.sipun.downly.presentation.theme.downlyDialogBorder

@Composable
fun SettingsScreen(
    state: ScreenState,
    vm: DownlyViewModel,
    modifier: Modifier,
    onCheckForUpdates: () -> Unit = {}
) {
    var showThemePreferences by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val selectedThemeIcon = when (state.themeMode) {
        "Light" -> Icons.Rounded.LightMode
        "Dark", "Pure Black" -> Icons.Rounded.DarkMode
        else -> Icons.Rounded.BrightnessAuto
    }
    val selectedThemeName = when (state.themeMode) {
        "Light" -> stringResource(R.string.theme_light)
        "Dark" -> stringResource(R.string.theme_dark)
        "Pure Black" -> stringResource(R.string.theme_pure_black)
        else -> stringResource(R.string.theme_system_default)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.appearance_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                shape = DownlyUiShapes.card,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = downlyCardBorder()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showThemePreferences = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(46.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(
                                selectedThemeIcon,
                                contentDescription = null,
                                modifier = Modifier.padding(11.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                stringResource(R.string.theme_title),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                stringResource(R.string.theme_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Text(
                                if (state.themeMode == "System") stringResource(R.string.theme_system) else selectedThemeName,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(46.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(
                                Icons.Rounded.Palette,
                                contentDescription = null,
                                modifier = Modifier.padding(11.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                stringResource(R.string.dynamic_color_title),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                stringResource(
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) R.string.dynamic_color_description
                                    else R.string.dynamic_color_unavailable
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.dynamicColor,
                            onCheckedChange = vm::setDynamicColor,
                            enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.about_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = DownlyUiShapes.card,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = downlyCardBorder()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAboutDialog = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(46.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(
                                Icons.Rounded.Info,
                                contentDescription = null,
                                modifier = Modifier.padding(11.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                stringResource(R.string.about_app_title),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                stringResource(R.string.about_app_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(context.getString(R.string.about_source_url))
                                    )
                                )
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(46.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(
                                Icons.Rounded.Code,
                                contentDescription = null,
                                modifier = Modifier.padding(11.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                stringResource(R.string.about_source_title),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                stringResource(R.string.about_source_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAboutDialog) {
        Dialog(
            onDismissRequest = { showAboutDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.82f),
                shape = DownlyUiShapes.dialog,
                color = MaterialTheme.colorScheme.surface,
                border = downlyCardBorder(),
                tonalElevation = 0.dp,
                shadowElevation = 18.dp
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AndroidView(
                        factory = { viewContext ->
                            ImageView(viewContext).apply {
                                setImageDrawable(
                                    viewContext.packageManager.getApplicationIcon(viewContext.packageName)
                                )
                            }
                        },
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        stringResource(R.string.about_dialog_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val dynamicProperties = rememberLottieDynamicProperties(
                        rememberLottieDynamicProperty(
                            LottieProperty.COLOR,
                            primaryColor.toArgb(),
                            "**"
                        )
                    )

                    val heartColor = Color(0xFFFF4B4B)
                    val heartDynamicProperties = rememberLottieDynamicProperties(
                        rememberLottieDynamicProperty(
                            LottieProperty.COLOR,
                            heartColor.toArgb(),
                            "**"
                        )
                    )

                    val downloadComposition by rememberLottieComposition(
                        LottieCompositionSpec.RawRes(
                            R.raw.about_download
                        )
                    )
                    val downloadProgress by animateLottieCompositionAsState(
                        composition = downloadComposition,
                        iterations = LottieConstants.IterateForever,
                        isPlaying = true,
                        restartOnPlay = true
                    )
                    if (downloadComposition != null) {
                        LottieAnimation(
                            composition = downloadComposition,
                            progress = { downloadProgress },
                            contentScale = ContentScale.Fit,
                            dynamicProperties = dynamicProperties,
                            modifier = Modifier.size(width = 160.dp, height = 90.dp)
                        )
                    } else {
                        Box(modifier = Modifier.size(width = 160.dp, height = 90.dp))
                    }
                    Surface(
                        onClick = {
                            onCheckForUpdates()
                        },
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = downlyCardBorder()
                    ) {
                        Text(
                            stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            stringResource(R.string.about_made_with),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val heartComposition by rememberLottieComposition(
                            LottieCompositionSpec.RawRes(
                                R.raw.about_heart
                            )
                        )
                        val heartProgress by animateLottieCompositionAsState(
                            composition = heartComposition,
                            iterations = LottieConstants.IterateForever,
                            isPlaying = true,
                            restartOnPlay = true
                        )
                        if (heartComposition != null) {
                            LottieAnimation(
                                composition = heartComposition,
                                progress = { heartProgress },
                                contentScale = ContentScale.Fit,
                                dynamicProperties = heartDynamicProperties,
                                modifier = Modifier.size(26.dp)
                            )
                        } else {
                            Box(modifier = Modifier.size(26.dp))
                        }
                        Text(
                            stringResource(R.string.about_by_sipun),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }

    if (showThemePreferences) {
        AlertDialog(
            onDismissRequest = { showThemePreferences = false },
            modifier = Modifier.downlyDialogBorder(),
            shape = DownlyUiShapes.dialog,
            containerColor = MaterialTheme.colorScheme.surface,
            confirmButton = {},
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        stringResource(R.string.theme_preference_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.theme_preference_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ThemeOption(
                        title = stringResource(R.string.theme_system),
                        description = stringResource(R.string.theme_system_description),
                        icon = { Icon(Icons.Rounded.BrightnessAuto, contentDescription = null) },
                        selected = state.themeMode == "System",
                        onClick = {
                            vm.setThemeMode("System")
                            showThemePreferences = false
                        }
                    )
                    ThemeOption(
                        title = stringResource(R.string.theme_light),
                        description = stringResource(R.string.theme_light_description),
                        icon = { Icon(Icons.Rounded.LightMode, contentDescription = null) },
                        selected = state.themeMode == "Light",
                        onClick = {
                            vm.setThemeMode("Light")
                            showThemePreferences = false
                        }
                    )
                    ThemeOption(
                        title = stringResource(R.string.theme_dark),
                        description = stringResource(R.string.theme_dark_description),
                        icon = { Icon(Icons.Rounded.DarkMode, contentDescription = null) },
                        selected = state.themeMode == "Dark",
                        onClick = {
                            vm.setThemeMode("Dark")
                            showThemePreferences = false
                        }
                    )
                    ThemeOption(
                        title = stringResource(R.string.theme_pure_black),
                        description = stringResource(R.string.theme_pure_black_description),
                        icon = { Icon(Icons.Rounded.DarkMode, contentDescription = null) },
                        selected = state.themeMode == "Pure Black",
                        onClick = {
                            vm.setThemeMode("Pure Black")
                            showThemePreferences = false
                        }
                    )
                }
            }
        )
    }
}

@Composable
private fun ThemeOption(
    title: String,
    description: String,
    icon: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        animationSpec = tween(180),
        label = "theme_option_container"
    )
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = DownlyUiShapes.card,
        border = downlyCardBorder(selected),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(14.dp),
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Box(contentAlignment = Alignment.Center) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.material3.LocalContentColor provides
                                (if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                    ) {
                        icon()
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Surface(
                    modifier = Modifier.size(26.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = stringResource(R.string.theme_selected),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    }
}
