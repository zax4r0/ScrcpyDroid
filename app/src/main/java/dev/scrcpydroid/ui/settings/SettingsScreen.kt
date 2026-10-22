package dev.scrcpydroid.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Video Section
            SectionHeader(title = "VIDEO")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Resolution
                    var resolutionExpanded by remember { mutableStateOf(false) }
                    ListItem(
                        headlineContent = { Text("Resolution") },
                        supportingContent = { Text("Maximum dimension in pixels") },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.HighQuality,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Box {
                                TextButton(onClick = { resolutionExpanded = true }) {
                                    Text(
                                        text = resolutionToLabel(uiState.maxSize),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null
                                    )
                                }
                                DropdownMenu(
                                    expanded = resolutionExpanded,
                                    onDismissRequest = { resolutionExpanded = false }
                                ) {
                                    listOf(
                                        "720p" to 1280,
                                        "1080p" to 1920,
                                        "1440p" to 2560,
                                        "Native" to 0
                                    ).forEach { (label, value) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                viewModel.setMaxSize(value)
                                                resolutionExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    // Bitrate Slider
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = "Bitrate",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            Text(
                                text = "${uiState.videoBitRate / 1_000_000} Mbps",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = (uiState.videoBitRate / 1_000_000).toFloat().coerceIn(1f, 20f),
                            onValueChange = { mbps ->
                                viewModel.setVideoBitRate(mbps.toInt() * 1_000_000)
                            },
                            valueRange = 1f..20f,
                            steps = 18,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    // Max FPS
                    var fpsExpanded by remember { mutableStateOf(false) }
                    ListItem(
                        headlineContent = { Text("Max FPS") },
                        supportingContent = { Text("Target framerate limit") },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.SlowMotionVideo,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Box {
                                TextButton(onClick = { fpsExpanded = true }) {
                                    Text(
                                        text = "${uiState.maxFps} FPS",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null
                                    )
                                }
                                DropdownMenu(
                                    expanded = fpsExpanded,
                                    onDismissRequest = { fpsExpanded = false }
                                ) {
                                    listOf(30, 60, 90, 120).forEach { fps ->
                                        DropdownMenuItem(
                                            text = { Text("$fps FPS") },
                                            onClick = {
                                                viewModel.setMaxFps(fps)
                                                fpsExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }

            // Codec Section
            SectionHeader(title = "CODEC")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // H.264
                    ListItem(
                        headlineContent = { Text("H.264 (AVC)") },
                        supportingContent = { Text("Standard, maximum compatibility") },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            RadioButton(
                                selected = uiState.videoCodec.equals("h264", ignoreCase = true),
                                onClick = { viewModel.setVideoCodec("h264") }
                            )
                        },
                        modifier = Modifier.clickable { viewModel.setVideoCodec("h264") },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    // H.265
                    ListItem(
                        headlineContent = { Text("H.265 (HEVC)") },
                        supportingContent = { Text("High efficiency, better quality at lower bitrates") },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.MovieFilter,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            RadioButton(
                                selected = uiState.videoCodec.equals("h265", ignoreCase = true),
                                onClick = { viewModel.setVideoCodec("h265") }
                            )
                        },
                        modifier = Modifier.clickable { viewModel.setVideoCodec("h265") },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }

            // Device Section
            SectionHeader(title = "DEVICE")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Stay Awake
                    ListItem(
                        headlineContent = { Text("Stay Awake") },
                        supportingContent = { Text("Keep remote device awake while mirroring") },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.BrightnessHigh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = uiState.stayAwake,
                                onCheckedChange = { viewModel.setStayAwake(it) }
                            )
                        },
                        modifier = Modifier.clickable { viewModel.setStayAwake(!uiState.stayAwake) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    // Show Touches
                    ListItem(
                        headlineContent = { Text("Show Touches") },
                        supportingContent = { Text("Display touch circle feedback on remote device") },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.TouchApp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = uiState.showTouches,
                                onCheckedChange = { viewModel.setShowTouches(it) }
                            )
                        },
                        modifier = Modifier.clickable { viewModel.setShowTouches(!uiState.showTouches) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    // Forward Audio
                    ListItem(
                        headlineContent = { Text("Forward Audio") },
                        supportingContent = { Text("Stream sound to this phone and mute connected device") },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = uiState.audioEnabled,
                                onCheckedChange = { viewModel.setAudioEnabled(it) }
                            )
                        },
                        modifier = Modifier.clickable { viewModel.setAudioEnabled(!uiState.audioEnabled) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }

            // Automation & Privacy Section
            SectionHeader(title = "AUTOMATION & PRIVACY")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // DND on Disconnect
                    ListItem(
                        headlineContent = { Text("Total Silence (DND) on Disconnect") },
                        supportingContent = { Text("Silence all sounds, media, alarms, rings, and notifications on target device upon disconnect") },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.DoNotDisturbOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = uiState.dndOnDisconnect,
                                onCheckedChange = { viewModel.setDndOnDisconnect(it) }
                            )
                        },
                        modifier = Modifier.clickable { viewModel.setDndOnDisconnect(!uiState.dndOnDisconnect) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    // Lock Screen on Disconnect
                    ListItem(
                        headlineContent = { Text("Lock Screen on Disconnect") },
                        supportingContent = { Text("Put remote device to sleep and lock display when session ends") },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = uiState.lockOnDisconnect,
                                onCheckedChange = { viewModel.setLockOnDisconnect(it) }
                            )
                        },
                        modifier = Modifier.clickable { viewModel.setLockOnDisconnect(!uiState.lockOnDisconnect) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    // Screen Off on Connect
                    ListItem(
                        headlineContent = { Text("Screen Off on Connect") },
                        supportingContent = { Text("Keep physical screen dark while mirroring to save battery and avoid accidental touches") },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = uiState.turnScreenOffOnConnect,
                                onCheckedChange = { viewModel.setTurnScreenOffOnConnect(it) }
                            )
                        },
                        modifier = Modifier.clickable { viewModel.setTurnScreenOffOnConnect(!uiState.turnScreenOffOnConnect) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    // Wake Screen on Connect
                    ListItem(
                        headlineContent = { Text("Wake Screen on Connect") },
                        supportingContent = { Text("Automatically wake up remote display when starting connection") },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.WbSunny,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = uiState.autoWakeOnConnect,
                                onCheckedChange = { viewModel.setAutoWakeOnConnect(it) }
                            )
                        },
                        modifier = Modifier.clickable { viewModel.setAutoWakeOnConnect(!uiState.autoWakeOnConnect) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    // Bidirectional Clipboard Sync
                    ListItem(
                        headlineContent = { Text("Clipboard Sync (Bidirectional)") },
                        supportingContent = { Text("Automatically sync copied text between both phones in real time") },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = uiState.clipboardSync,
                                onCheckedChange = { viewModel.setClipboardSync(it) }
                            )
                        },
                        modifier = Modifier.clickable { viewModel.setClipboardSync(!uiState.clipboardSync) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }

            // Updates & About Section
            SectionHeader(title = "UPDATES & ABOUT")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text("Version") },
                        supportingContent = { Text("1.0.0 (Scrcpy Protocol v3.1)") },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    ListItem(
                        headlineContent = { Text("Update Source") },
                        supportingContent = { Text("github.com/${uiState.otaRepo}") },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    ListItem(
                        headlineContent = { Text("Check for Updates") },
                        supportingContent = {
                            when (uiState.updateResult) {
                                is dev.scrcpydroid.core.ota.UpdateCheckResult.Checking -> Text("Checking GitHub releases...")
                                is dev.scrcpydroid.core.ota.UpdateCheckResult.Available -> Text("New update available!")
                                is dev.scrcpydroid.core.ota.UpdateCheckResult.UpToDate -> Text("App is up to date")
                                is dev.scrcpydroid.core.ota.UpdateCheckResult.Error -> Text((uiState.updateResult as dev.scrcpydroid.core.ota.UpdateCheckResult.Error).message)
                                else -> Text("Check latest ScrcpyDroid release")
                            }
                        },
                        trailingContent = {
                            if (uiState.updateResult is dev.scrcpydroid.core.ota.UpdateCheckResult.Checking) {
                                androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                TextButton(onClick = { viewModel.checkForUpdates() }) {
                                    Text("Check")
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // OTA Update Dialog
        if (uiState.updateResult is dev.scrcpydroid.core.ota.UpdateCheckResult.Available) {
            val updateInfo = (uiState.updateResult as dev.scrcpydroid.core.ota.UpdateCheckResult.Available).info
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { viewModel.clearUpdateResult() },
                title = { Text("Update Available: v${updateInfo.versionName}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(updateInfo.releaseNotes)
                        if (uiState.downloadProgress is dev.scrcpydroid.core.ota.DownloadProgress.Downloading) {
                            val p = (uiState.downloadProgress as dev.scrcpydroid.core.ota.DownloadProgress.Downloading).progress
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { p },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("Downloading: ${(p * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    androidx.compose.material3.Button(
                        onClick = { viewModel.downloadAndInstall(updateInfo.downloadUrl) },
                        enabled = uiState.downloadProgress !is dev.scrcpydroid.core.ota.DownloadProgress.Downloading
                    ) {
                        Text("Download & Install")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.clearUpdateResult() }) {
                        Text("Later")
                    }
                }
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp)
    )
}

private fun resolutionToLabel(maxSize: Int): String = when (maxSize) {
    1280 -> "720p"
    1920 -> "1080p"
    2560 -> "1440p"
    0 -> "Native"
    else -> "${maxSize}p"
}
