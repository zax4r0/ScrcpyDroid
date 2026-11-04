package dev.scrcpydroid.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
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
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        SettingsContent(
            viewModel = viewModel,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@Composable
fun SettingsContent(
    viewModel: SettingsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    var showPasswordDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            // Appearance & Theme Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(title = "APPEARANCE")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Dynamic Color (Monet)
                        ListItem(
                            headlineContent = { Text("Dynamic Color (Monet)", fontWeight = FontWeight.Medium) },
                            supportingContent = { Text("Match app colors to system wallpaper (Android 12+)") },
                            leadingContent = {
                                SettingIcon(
                                    imageVector = Icons.Default.Palette,
                                    tint = MaterialTheme.colorScheme.primary,
                                    bg = MaterialTheme.colorScheme.primaryContainer
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = uiState.dynamicColor,
                                    onCheckedChange = { viewModel.setDynamicColor(it) }
                                )
                            },
                            modifier = Modifier.clickable { viewModel.setDynamicColor(!uiState.dynamicColor) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )

                        SettingsDivider()

                        // Theme Mode
                        var themeExpanded by remember { mutableStateOf(false) }
                        ListItem(
                            headlineContent = { Text("Theme Mode", fontWeight = FontWeight.Medium) },
                            supportingContent = { Text("Choose dark, light, or follow system theme") },
                            leadingContent = {
                                SettingIcon(
                                    imageVector = Icons.Default.Brightness4,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    bg = MaterialTheme.colorScheme.secondaryContainer
                                )
                            },
                            trailingContent = {
                                Box {
                                    FilledTonalButton(
                                        onClick = { themeExpanded = true },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = when (uiState.darkThemeMode) {
                                                "dark" -> "Dark"
                                                "light" -> "Light"
                                                else -> "System"
                                            },
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = null
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = themeExpanded,
                                        onDismissRequest = { themeExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Follow System") },
                                            onClick = {
                                                viewModel.setDarkThemeMode("system")
                                                themeExpanded = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Dark Theme") },
                                            onClick = {
                                                viewModel.setDarkThemeMode("dark")
                                                themeExpanded = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Light Theme") },
                                            onClick = {
                                                viewModel.setDarkThemeMode("light")
                                                themeExpanded = false
                                            }
                                        )
                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }

            // Video Stream Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(title = "STREAM QUALITY")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Resolution
                        var resolutionExpanded by remember { mutableStateOf(false) }
                        ListItem(
                            headlineContent = { Text("Resolution", fontWeight = FontWeight.Medium) },
                            supportingContent = { Text("Maximum dimension limit in pixels") },
                            leadingContent = {
                                SettingIcon(
                                    imageVector = Icons.Default.HighQuality,
                                    tint = MaterialTheme.colorScheme.primary,
                                    bg = MaterialTheme.colorScheme.primaryContainer
                                )
                            },
                            trailingContent = {
                                Box {
                                    FilledTonalButton(
                                        onClick = { resolutionExpanded = true },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
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

                        SettingsDivider()

                        // Bitrate Slider
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    SettingIcon(
                                        imageVector = Icons.Default.Speed,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        bg = MaterialTheme.colorScheme.secondaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(
                                            text = "Bitrate",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "Target video compression rate",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    text = "${uiState.videoBitRate / 1_000_000} Mbps",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
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

                        SettingsDivider()

                        // Max FPS
                        var fpsExpanded by remember { mutableStateOf(false) }
                        ListItem(
                            headlineContent = { Text("Max FPS", fontWeight = FontWeight.Medium) },
                            supportingContent = { Text("Target framerate limit") },
                            leadingContent = {
                                SettingIcon(
                                    imageVector = Icons.Default.SlowMotionVideo,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    bg = MaterialTheme.colorScheme.tertiaryContainer
                                )
                            },
                            trailingContent = {
                                Box {
                                    FilledTonalButton(
                                        onClick = { fpsExpanded = true },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
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
            }

            // Codec Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(title = "VIDEO CODEC")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // H.264
                        ListItem(
                            headlineContent = { Text("H.264 (AVC)", fontWeight = FontWeight.Medium) },
                            supportingContent = { Text("Standard, maximum compatibility across all devices") },
                            leadingContent = {
                                SettingIcon(
                                    imageVector = Icons.Default.Movie,
                                    tint = MaterialTheme.colorScheme.primary,
                                    bg = MaterialTheme.colorScheme.primaryContainer
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

                        SettingsDivider()

                        // H.265
                        ListItem(
                            headlineContent = { Text("H.265 (HEVC)", fontWeight = FontWeight.Medium) },
                            supportingContent = { Text("High efficiency, higher clarity at lower bitrates") },
                            leadingContent = {
                                SettingIcon(
                                    imageVector = Icons.Default.MovieFilter,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    bg = MaterialTheme.colorScheme.secondaryContainer
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
            }

            // Device Control Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(title = "DEVICE CONTROL")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Stay Awake
                        ListItem(
                            headlineContent = { Text("Stay Awake", fontWeight = FontWeight.Medium) },
                            supportingContent = { Text("Keep remote display awake while mirroring session is active") },
                            leadingContent = {
                                SettingIcon(
                                    imageVector = Icons.Default.BrightnessHigh,
                                    tint = MaterialTheme.colorScheme.primary,
                                    bg = MaterialTheme.colorScheme.primaryContainer
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

                        SettingsDivider()

                        // Show Touches
                        ListItem(
                            headlineContent = { Text("Show Touches", fontWeight = FontWeight.Medium) },
                            supportingContent = { Text("Display touch circle feedback points on remote screen") },
                            leadingContent = {
                                SettingIcon(
                                    imageVector = Icons.Default.TouchApp,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    bg = MaterialTheme.colorScheme.secondaryContainer
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

                        SettingsDivider()

                        // Forward Audio
                        ListItem(
                            headlineContent = { Text("Forward Audio", fontWeight = FontWeight.Medium) },
                            supportingContent = { Text("Stream sound to this phone and mute connected device") },
                            leadingContent = {
                                SettingIcon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    bg = MaterialTheme.colorScheme.tertiaryContainer
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
            }

            // Automation & Privacy Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(title = "AUTOMATION & PRIVACY")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Total Silence (DND) on Disconnect
                        ListItem(
                            headlineContent = { Text("Total Silence (DND) on Disconnect", fontWeight = FontWeight.Medium) },
                            supportingContent = { Text("Mute all media, alarms, rings, and notifications when session ends") },
                            leadingContent = {
                                SettingIcon(
                                    imageVector = Icons.Default.DoNotDisturbOn,
                                    tint = MaterialTheme.colorScheme.error,
                                    bg = MaterialTheme.colorScheme.errorContainer
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

                        SettingsDivider()

                        // Lock Screen on Disconnect
                        ListItem(
                            headlineContent = { Text("Lock Screen on Disconnect", fontWeight = FontWeight.Medium) },
                            supportingContent = { Text("Put remote device to sleep and lock display when disconnected") },
                            leadingContent = {
                                SettingIcon(
                                    imageVector = Icons.Default.Lock,
                                    tint = MaterialTheme.colorScheme.primary,
                                    bg = MaterialTheme.colorScheme.primaryContainer
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

                        SettingsDivider()

                        // Screen Off on Connect
                        ListItem(
                            headlineContent = { Text("Screen Off on Connect", fontWeight = FontWeight.Medium) },
                            supportingContent = { Text("Keep physical screen dark while mirroring to save battery") },
                            leadingContent = {
                                SettingIcon(
                                    imageVector = Icons.Default.VisibilityOff,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    bg = MaterialTheme.colorScheme.secondaryContainer
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

                        SettingsDivider()

                        // Wake Screen on Connect
                        ListItem(
                            headlineContent = { Text("Wake Screen on Connect", fontWeight = FontWeight.Medium) },
                            supportingContent = { Text("Automatically wake display when starting mirroring session") },
                            leadingContent = {
                                SettingIcon(
                                    imageVector = Icons.Default.WbSunny,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    bg = MaterialTheme.colorScheme.tertiaryContainer
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

                        SettingsDivider()

                        // Bidirectional Clipboard Sync
                        ListItem(
                            headlineContent = { Text("Clipboard Sync (Bidirectional)", fontWeight = FontWeight.Medium) },
                            supportingContent = { Text("Automatically sync copied text between both devices") },
                            leadingContent = {
                                SettingIcon(
                                    imageVector = Icons.Default.ContentPaste,
                                    tint = MaterialTheme.colorScheme.primary,
                                    bg = MaterialTheme.colorScheme.primaryContainer
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

                        SettingsDivider()

                        // Auto-Unlock on Connect/Reconnect
                        ListItem(
                            headlineContent = { Text("Auto-Unlock on Connect", fontWeight = FontWeight.Medium) },
                            supportingContent = { Text("Automatically unlock remote phone when connecting or reconnecting") },
                            leadingContent = {
                                SettingIcon(
                                    imageVector = Icons.Default.LockOpen,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    bg = MaterialTheme.colorScheme.secondaryContainer
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = uiState.autoUnlockOnConnect,
                                    onCheckedChange = { viewModel.setAutoUnlockOnConnect(it) }
                                )
                            },
                            modifier = Modifier.clickable { viewModel.setAutoUnlockOnConnect(!uiState.autoUnlockOnConnect) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )

                        SettingsDivider()

                        // Remote Password / PIN
                        ListItem(
                            headlineContent = { Text("Global Password / PIN", fontWeight = FontWeight.Medium) },
                            supportingContent = {
                                Text(
                                    if (uiState.devicePassword.isNotEmpty()) "•••••••• (Password configured)"
                                    else "Not configured (swipe/no password only)"
                                )
                            },
                            leadingContent = {
                                SettingIcon(
                                    imageVector = Icons.Default.Key,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    bg = MaterialTheme.colorScheme.tertiaryContainer
                                )
                            },
                            modifier = Modifier.clickable { showPasswordDialog = true },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }

            // Updates & About Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(title = "UPDATES & ABOUT")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent = { Text("Version", fontWeight = FontWeight.Medium) },
                            supportingContent = { Text("v${uiState.appVersion} • Scrcpy Protocol v3.1") },
                            leadingContent = {
                                SettingIcon(
                                    imageVector = Icons.Default.Info,
                                    tint = MaterialTheme.colorScheme.primary,
                                    bg = MaterialTheme.colorScheme.primaryContainer
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )

                        SettingsDivider()

                        ListItem(
                            headlineContent = { Text("Include Pre-releases", fontWeight = FontWeight.Medium) },
                            supportingContent = { Text("Check and install beta / preview builds before official releases") },
                            leadingContent = {
                                SettingIcon(
                                    imageVector = Icons.Default.NewReleases,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    bg = MaterialTheme.colorScheme.tertiaryContainer
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = uiState.includePrereleases,
                                    onCheckedChange = { viewModel.setIncludePrereleases(it) }
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )

                        SettingsDivider()

                        ListItem(
                            headlineContent = { Text("Check for Updates", fontWeight = FontWeight.Medium) },
                            supportingContent = {
                                when (uiState.updateResult) {
                                    is dev.scrcpydroid.core.ota.UpdateCheckResult.Checking -> Text("Checking GitHub releases...")
                                    is dev.scrcpydroid.core.ota.UpdateCheckResult.Available -> {
                                        val info = (uiState.updateResult as dev.scrcpydroid.core.ota.UpdateCheckResult.Available).info
                                        if (info.isPrerelease) Text("Pre-release v${info.versionName} available!") else Text("New version v${info.versionName} available!")
                                    }
                                    is dev.scrcpydroid.core.ota.UpdateCheckResult.UpToDate -> Text("App is up to date (v${uiState.appVersion})")
                                    is dev.scrcpydroid.core.ota.UpdateCheckResult.Error -> Text((uiState.updateResult as dev.scrcpydroid.core.ota.UpdateCheckResult.Error).message)
                                    else -> Text("Updates sourced from github.com/${uiState.otaRepo}")
                                }
                            },
                            leadingContent = {
                                SettingIcon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    bg = MaterialTheme.colorScheme.secondaryContainer
                                )
                            },
                            trailingContent = {
                                if (uiState.updateResult is dev.scrcpydroid.core.ota.UpdateCheckResult.Checking) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                } else {
                                    FilledTonalButton(
                                        onClick = { viewModel.checkForUpdates() },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Check", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // OTA Update Dialog
        if (uiState.updateResult is dev.scrcpydroid.core.ota.UpdateCheckResult.Available) {
            val updateInfo = (uiState.updateResult as dev.scrcpydroid.core.ota.UpdateCheckResult.Available).info
            AlertDialog(
                onDismissRequest = { viewModel.clearUpdateResult() },
                shape = RoundedCornerShape(28.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                icon = {
                    Icon(
                        imageVector = if (updateInfo.isPrerelease) Icons.Default.NewReleases else Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = if (updateInfo.isPrerelease) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        text = if (updateInfo.isPrerelease) "Pre-release Available: v${updateInfo.versionName}" else "Update Available: v${updateInfo.versionName}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (updateInfo.isPrerelease) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = "PRE-RELEASE / BETA CHANNEL",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Text(updateInfo.releaseNotes)
                        if (uiState.downloadProgress is dev.scrcpydroid.core.ota.DownloadProgress.Downloading) {
                            val p = (uiState.downloadProgress as dev.scrcpydroid.core.ota.DownloadProgress.Downloading).progress
                            LinearProgressIndicator(
                                progress = { p },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("Downloading: ${(p * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.downloadAndInstall(updateInfo.downloadUrl) },
                        enabled = uiState.downloadProgress !is dev.scrcpydroid.core.ota.DownloadProgress.Downloading,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(if (updateInfo.isPrerelease) "Install Pre-release" else "Download & Install", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.clearUpdateResult() }) {
                        Text("Later")
                    }
                }
            )
        }

        // Global Password Dialog
        if (showPasswordDialog) {
            var passwordInput by remember(uiState.devicePassword) { mutableStateOf(uiState.devicePassword) }
            var passwordVisible by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { showPasswordDialog = false },
                shape = RoundedCornerShape(28.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                title = { Text("Remote Password / PIN", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Enter the default PIN or password used to unlock devices without custom per-device credentials.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("PIN / Password") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                            ),
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.setDevicePassword(passwordInput)
                            showPasswordDialog = false
                        },
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (uiState.devicePassword.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    viewModel.setDevicePassword("")
                                    showPasswordDialog = false
                                }
                            ) {
                                Text("Clear", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        TextButton(onClick = { showPasswordDialog = false }) {
                            Text("Cancel")
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun SettingIcon(imageVector: ImageVector, tint: Color, bg: Color) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 58.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    )
}

@Composable
internal fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 12.dp)
    )
}

internal fun resolutionToLabel(maxSize: Int): String = when (maxSize) {
    1280 -> "720p"
    1920 -> "1080p"
    2560 -> "1440p"
    0 -> "Native"
    else -> "${maxSize}p"
}
