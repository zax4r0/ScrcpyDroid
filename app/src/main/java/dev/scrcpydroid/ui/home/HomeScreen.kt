package dev.scrcpydroid.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.scrcpydroid.core.discovery.DiscoveredDevice
import dev.scrcpydroid.core.ota.DownloadProgress
import dev.scrcpydroid.core.ota.UpdateCheckResult
import dev.scrcpydroid.data.model.DeviceConnection
import dev.scrcpydroid.data.model.DeviceSettings
import dev.scrcpydroid.ui.settings.SettingsContent
import dev.scrcpydroid.ui.settings.resolutionToLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class HomeTab(val label: String, val icon: ImageVector) {
    CONNECT("Connect", Icons.Default.Cast),
    DEVICES("Devices", Icons.Default.Devices),
    SETTINGS("Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToMirror: (host: String, port: Int) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    var currentTab by rememberSaveable { mutableStateOf(HomeTab.CONNECT) }

    var renamingDevice by remember { mutableStateOf<DeviceConnection?>(null) }
    var deviceSettingsTarget by remember { mutableStateOf<DeviceConnection?>(null) }

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                is HomeNavigationEvent.NavigateToMirror -> {
                    onNavigateToMirror(event.host, event.port)
                }
            }
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when (currentTab) {
                                HomeTab.CONNECT -> Icons.Default.Cast
                                HomeTab.DEVICES -> Icons.Default.Devices
                                HomeTab.SETTINGS -> Icons.Default.Settings
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = when (currentTab) {
                                HomeTab.CONNECT -> "ScrcpyDroid"
                                HomeTab.DEVICES -> "Devices (${uiState.recentConnections.size})"
                                HomeTab.SETTINGS -> "Settings"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    if (currentTab != HomeTab.SETTINGS) {
                        IconButton(
                            onClick = {
                                if (uiState.isScanning) viewModel.stopDiscovery() else viewModel.startDiscovery()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Scan mDNS",
                                tint = if (uiState.isScanning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp
            ) {
                HomeTab.entries.forEach { tab ->
                    val isSelected = currentTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentTab = tab },
                        icon = {
                            if (tab == HomeTab.DEVICES && uiState.recentConnections.isNotEmpty()) {
                                BadgedBox(badge = {
                                    Badge { Text(uiState.recentConnections.size.toString()) }
                                }) {
                                    Icon(tab.icon, contentDescription = tab.label)
                                }
                            } else {
                                Icon(tab.icon, contentDescription = tab.label)
                            }
                        },
                        label = { Text(tab.label) }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        when (currentTab) {
            HomeTab.CONNECT -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. OTA Update Banner (if available)
                    if (uiState.updateResult is UpdateCheckResult.Available) {
                        item {
                            val info = (uiState.updateResult as UpdateCheckResult.Available).info
                            OtaUpdateBanner(
                                info = info,
                                progress = uiState.downloadProgress,
                                onDownload = { viewModel.startDownload(info.downloadUrl) },
                                onDismiss = { viewModel.dismissUpdateBanner() }
                            )
                        }
                    }

                    // 2. Wireless Debugging Connection Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Direct Connection",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = uiState.ipAddress,
                                        onValueChange = viewModel::onIpAddressChange,
                                        label = { Text("IP Address") },
                                        placeholder = { Text("192.168.1.x") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Decimal,
                                            imeAction = ImeAction.Next
                                        ),
                                        modifier = Modifier.weight(2f)
                                    )

                                    OutlinedTextField(
                                        value = uiState.port,
                                        onValueChange = viewModel::onPortChange,
                                        label = { Text("Port") },
                                        placeholder = { Text("5555") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Number,
                                            imeAction = ImeAction.Done
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onDone = {
                                                focusManager.clearFocus()
                                                viewModel.onConnect()
                                            }
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = {
                                            focusManager.clearFocus()
                                            viewModel.onConnect()
                                        },
                                        enabled = !uiState.isConnecting,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        if (uiState.isConnecting) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Connecting...")
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Connect")
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = { viewModel.openPairingDialog() },
                                        modifier = Modifier.height(48.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Link,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Pair Code")
                                    }
                                }
                            }
                        }
                    }

                    // 3. mDNS Discovered Devices
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.WifiTethering,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Nearby Devices (mDNS)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            if (uiState.isScanning) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Scanning...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    if (uiState.discoveredDevices.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "No wireless devices detected on your Wi-Fi yet.\nEnable 'Wireless Debugging' in Developer Options on your other phone to discover it automatically.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                    } else {
                        items(uiState.discoveredDevices) { device ->
                            DiscoveredDeviceCard(
                                device = device,
                                onConnect = { viewModel.onSelectDiscoveredDevice(device) }
                            )
                        }
                    }

                    // Quick prompt to check saved devices
                    if (uiState.recentConnections.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { currentTab = HomeTab.DEVICES }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Saved Devices (${uiState.recentConnections.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                TextButton(onClick = { currentTab = HomeTab.DEVICES }) {
                                    Text("View All")
                                }
                            }
                        }

                        items(uiState.recentConnections.take(3)) { connection ->
                            SavedDeviceCard(
                                connection = connection,
                                onConnect = { viewModel.onConnect(connection.host, connection.port.toString()) },
                                onRename = { renamingDevice = connection },
                                onConfigureSettings = { deviceSettingsTarget = connection },
                                onDelete = { viewModel.onDeleteConnection(connection) }
                            )
                        }
                    }
                }
            }

            HomeTab.DEVICES -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (uiState.recentConnections.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 24.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Devices,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Text(
                                        text = "No Saved Devices",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Connect to a device via the Connect tab to save it here. Each saved device can have custom nicknames and individual resolution, FPS, unlock credentials, and connection settings!",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    FilledTonalButton(
                                        onClick = { currentTab = HomeTab.CONNECT }
                                    ) {
                                        Text("Go to Connect Tab")
                                    }
                                }
                            }
                        }
                    } else {
                        item {
                            Text(
                                text = "Tap any device to connect. Tap the pencil to rename, or the sliders icon to customize per-device settings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        items(
                            items = uiState.recentConnections,
                            key = { it.uniqueKey }
                        ) { connection ->
                            SavedDeviceCard(
                                connection = connection,
                                onConnect = { viewModel.onConnect(connection.host, connection.port.toString()) },
                                onRename = { renamingDevice = connection },
                                onConfigureSettings = { deviceSettingsTarget = connection },
                                onDelete = { viewModel.onDeleteConnection(connection) }
                            )
                        }
                    }
                }
            }

            HomeTab.SETTINGS -> {
                SettingsContent(modifier = Modifier.padding(innerPadding))
            }
        }
    }

    // Wireless Debugging Pairing Dialog
    if (uiState.showPairingDialog) {
        WirelessPairingDialog(
            host = uiState.pairingHost,
            port = uiState.pairingPort,
            code = uiState.pairingCode,
            status = uiState.pairingStatus,
            onHostChange = viewModel::onPairingHostChange,
            onPortChange = viewModel::onPairingPortChange,
            onCodeChange = viewModel::onPairingCodeChange,
            onPair = viewModel::executePairing,
            onDismiss = viewModel::closePairingDialog
        )
    }

    // Rename Device Dialog
    renamingDevice?.let { device ->
        RenameDeviceDialog(
            connection = device,
            onSave = { newName ->
                viewModel.onRenameConnection(device, newName)
                renamingDevice = null
            },
            onDismiss = { renamingDevice = null }
        )
    }

    // Per-Device Settings Dialog
    deviceSettingsTarget?.let { device ->
        DeviceSettingsDialog(
            connection = device,
            onSave = { updatedSettings ->
                viewModel.onUpdateDeviceSettings(device, updatedSettings)
                deviceSettingsTarget = null
            },
            onDismiss = { deviceSettingsTarget = null }
        )
    }
}

@Composable
fun SavedDeviceCard(
    connection: DeviceConnection,
    onConnect: () -> Unit,
    onRename: () -> Unit,
    onConfigureSettings: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onConnect),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = connection.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${connection.host}:${connection.port}" +
                                if (connection.deviceName.isNotBlank() && connection.deviceName != connection.displayName) " • ${connection.deviceName}" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onConnect,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Connect")
                }
            }

            // Custom settings chip if enabled
            val hasCustom = connection.customSettings?.useGlobalSettings == false
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (hasCustom) {
                    val settings = connection.customSettings!!
                    val parts = mutableListOf<String>()
                    settings.maxSize?.let { parts.add(resolutionToLabel(it)) }
                    settings.maxFps?.let { parts.add("${it}fps") }
                    if (settings.autoUnlock == true) parts.add("Auto-Unlock")
                    AssistChip(
                        onClick = onConfigureSettings,
                        label = { Text("Custom: ${if (parts.isNotEmpty()) parts.joinToString(" • ") else "Configured"}") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                } else {
                    Text(
                        text = "Using global settings",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = onRename,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Rename",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onConfigureSettings,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Device Settings",
                            tint = if (hasCustom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RenameDeviceDialog(
    connection: DeviceConnection,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var nameInput by remember { mutableStateOf(connection.customName.ifBlank { connection.deviceName }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Device", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Set a friendly nickname for this device (e.g. 'Living Room TV', 'POCO F4 Gaming'):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Device Nickname") },
                    placeholder = { Text(connection.deviceName.ifBlank { "${connection.host}:${connection.port}" }) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(nameInput)
                    onDismiss()
                }
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                if (connection.customName.isNotBlank()) {
                    TextButton(
                        onClick = {
                            onSave("")
                            onDismiss()
                        }
                    ) {
                        Text("Clear", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
fun DeviceSettingsDialog(
    connection: DeviceConnection,
    onSave: (DeviceSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val existing = connection.customSettings ?: DeviceSettings()
    var useCustom by remember { mutableStateOf(!existing.useGlobalSettings) }
    var maxSize by remember { mutableStateOf(existing.maxSize ?: 1920) }
    var videoBitRate by remember { mutableStateOf(existing.videoBitRate ?: 8_000_000) }
    var maxFps by remember { mutableStateOf(existing.maxFps ?: 60) }
    var videoCodec by remember { mutableStateOf(existing.videoCodec ?: "h264") }
    var audioEnabled by remember { mutableStateOf(existing.audioEnabled ?: true) }
    var autoUnlock by remember { mutableStateOf(existing.autoUnlock ?: true) }
    var devicePassword by remember { mutableStateOf(existing.devicePassword ?: "") }
    var passwordVisible by remember { mutableStateOf(false) }
    var turnScreenOffOnConnect by remember { mutableStateOf(existing.turnScreenOffOnConnect ?: false) }
    var stayAwake by remember { mutableStateOf(existing.stayAwake ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Settings: ${connection.displayName}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${connection.host}:${connection.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Switch: Use Custom Settings for this Device
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { useCustom = !useCustom }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Custom Settings", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (useCustom) "Override global settings for this device" else "Using global settings",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = useCustom,
                        onCheckedChange = { useCustom = it }
                    )
                }

                if (useCustom) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    // Resolution
                    var resExpanded by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Resolution", style = MaterialTheme.typography.bodyMedium)
                        Box {
                            TextButton(onClick = { resExpanded = true }) {
                                Text(resolutionToLabel(maxSize), fontWeight = FontWeight.SemiBold)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(
                                expanded = resExpanded,
                                onDismissRequest = { resExpanded = false }
                            ) {
                                listOf(0 to "Native", 2560 to "1440p", 1920 to "1080p", 1280 to "720p").forEach { (res, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            maxSize = res
                                            resExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Max FPS
                    var fpsExpanded by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Max FPS", style = MaterialTheme.typography.bodyMedium)
                        Box {
                            TextButton(onClick = { fpsExpanded = true }) {
                                Text("$maxFps FPS", fontWeight = FontWeight.SemiBold)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(
                                expanded = fpsExpanded,
                                onDismissRequest = { fpsExpanded = false }
                            ) {
                                listOf(30, 60, 90, 120).forEach { fps ->
                                    DropdownMenuItem(
                                        text = { Text("$fps FPS") },
                                        onClick = {
                                            maxFps = fps
                                            fpsExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Video Bitrate
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Video Bitrate", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${videoBitRate / 1_000_000} Mbps",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = (videoBitRate / 1_000_000).toFloat().coerceIn(1f, 20f),
                            onValueChange = { videoBitRate = it.toInt() * 1_000_000 },
                            valueRange = 1f..20f,
                            steps = 18
                        )
                    }

                    // Codec
                    var codecExpanded by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Codec", style = MaterialTheme.typography.bodyMedium)
                        Box {
                            TextButton(onClick = { codecExpanded = true }) {
                                Text(if (videoCodec == "h265") "H.265 (HEVC)" else "H.264 (AVC)", fontWeight = FontWeight.SemiBold)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(
                                expanded = codecExpanded,
                                onDismissRequest = { codecExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("H.264 (AVC)") },
                                    onClick = {
                                        videoCodec = "h264"
                                        codecExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("H.265 (HEVC)") },
                                    onClick = {
                                        videoCodec = "h265"
                                        codecExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    // Auto-Unlock for this device
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { autoUnlock = !autoUnlock }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Auto-Unlock on Connect", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = autoUnlock, onCheckedChange = { autoUnlock = it })
                    }

                    // Device Password / PIN
                    OutlinedTextField(
                        value = devicePassword,
                        onValueChange = { devicePassword = it },
                        label = { Text("Device Password / PIN") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Hide" else "Show"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Audio Enabled
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { audioEnabled = !audioEnabled }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Forward Audio", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = audioEnabled, onCheckedChange = { audioEnabled = it })
                    }

                    // Screen Off on Connect
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { turnScreenOffOnConnect = !turnScreenOffOnConnect }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Turn Screen Off on Connect", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = turnScreenOffOnConnect, onCheckedChange = { turnScreenOffOnConnect = it })
                    }

                    // Stay Awake
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { stayAwake = !stayAwake }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Keep Remote Awake", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = stayAwake, onCheckedChange = { stayAwake = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val settings = DeviceSettings(
                        useGlobalSettings = !useCustom,
                        maxSize = if (useCustom) maxSize else null,
                        videoBitRate = if (useCustom) videoBitRate else null,
                        maxFps = if (useCustom) maxFps else null,
                        videoCodec = if (useCustom) videoCodec else null,
                        audioEnabled = if (useCustom) audioEnabled else null,
                        autoUnlock = if (useCustom) autoUnlock else null,
                        devicePassword = if (useCustom) devicePassword else null,
                        turnScreenOffOnConnect = if (useCustom) turnScreenOffOnConnect else null,
                        stayAwake = if (useCustom) stayAwake else null
                    )
                    onSave(settings)
                    onDismiss()
                }
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                if (useCustom) {
                    TextButton(
                        onClick = {
                            onSave(DeviceSettings(useGlobalSettings = true))
                            onDismiss()
                        }
                    ) {
                        Text("Reset", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
fun DiscoveredDeviceCard(
    device: DiscoveredDevice,
    onConnect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onConnect),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (device.isPairing) {
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (device.isPairing) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (device.isPairing) Icons.Default.Link else Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = if (device.isPairing) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${device.host}:${device.port}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (device.isPairing) {
                        Text(
                            text = "Ready to pair",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            FilledTonalButton(
                onClick = onConnect,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(if (device.isPairing) "Pair" else "Connect")
            }
        }
    }
}

@Composable
fun WirelessPairingDialog(
    host: String,
    port: String,
    code: String,
    status: PairingStatus,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onPair: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Wireless Debugging Pairing")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "On target device, open Settings > Developer Options > Wireless Debugging > 'Pair device with pairing code'.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = host,
                    onValueChange = onHostChange,
                    label = { Text("IP Address") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = port,
                    onValueChange = onPortChange,
                    label = { Text("Pairing Port (from pairing dialog)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = code,
                    onValueChange = onCodeChange,
                    label = { Text("6-Digit Pairing Code") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                when (status) {
                    is PairingStatus.Pairing -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pairing...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    is PairingStatus.Success -> {
                        Text(
                            text = "Paired successfully! You can now connect.",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    is PairingStatus.Error -> {
                        Text(
                            text = "Error: ${status.message}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    else -> {}
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onPair,
                enabled = status !is PairingStatus.Pairing && host.isNotBlank() && port.isNotBlank() && code.isNotBlank()
            ) {
                Text("Pair")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun OtaUpdateBanner(
    info: dev.scrcpydroid.core.ota.AppReleaseInfo,
    progress: DownloadProgress?,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Update Available: ${info.versionName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (info.releaseNotes.isNotBlank()) {
                Text(
                    text = info.releaseNotes.take(150) + if (info.releaseNotes.length > 150) "..." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }

            when (progress) {
                is DownloadProgress.Downloading -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(
                            progress = { progress.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Downloading: ${(progress.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                is DownloadProgress.Completed -> {
                    Text(
                        text = "Download complete. Tap to install.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is DownloadProgress.Failed -> {
                    Text(
                        text = "Download failed: ${progress.error}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                else -> {
                    FilledTonalButton(
                        onClick = onDownload,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Download & Install")
                    }
                }
            }
        }
    }
}
