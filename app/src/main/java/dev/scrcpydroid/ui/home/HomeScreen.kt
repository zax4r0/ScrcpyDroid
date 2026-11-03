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
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import java.io.File

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

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

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
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = {
                    Column {
                        Text(
                            text = when (currentTab) {
                                HomeTab.CONNECT -> "ScrcpyDroid"
                                HomeTab.DEVICES -> "Saved Devices"
                                HomeTab.SETTINGS -> "Settings"
                            },
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (currentTab == HomeTab.CONNECT) {
                            Text(
                                text = "High-performance Android wireless mirror",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (currentTab == HomeTab.DEVICES) {
                            Text(
                                text = "${uiState.recentConnections.size} configured ${if (uiState.recentConnections.size == 1) "device" else "devices"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (currentTab != HomeTab.SETTINGS) {
                        FilledTonalIconButton(
                            onClick = {
                                if (uiState.isScanning) viewModel.stopDiscovery() else viewModel.startDiscovery()
                            },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (uiState.isScanning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Scan mDNS",
                                tint = if (uiState.isScanning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.mediumTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp
            ) {
                HomeTab.entries.forEach { tab ->
                    val isSelected = currentTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentTab = tab },
                        icon = {
                            if (tab == HomeTab.DEVICES && uiState.recentConnections.isNotEmpty()) {
                                BadgedBox(badge = {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ) {
                                        Text(uiState.recentConnections.size.toString())
                                    }
                                }) {
                                    Icon(tab.icon, contentDescription = tab.label)
                                }
                            } else {
                                Icon(tab.icon, contentDescription = tab.label)
                            }
                        },
                        label = {
                            Text(
                                tab.label,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        when (currentTab) {
            HomeTab.CONNECT -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
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

                    // 2. Google-Style Direct Connection Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(28.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lan,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "Wireless Debugging",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Connect to device IP & wireless port",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = uiState.ipAddress,
                                        onValueChange = viewModel::onIpAddressChange,
                                        label = { Text("IP Address") },
                                        placeholder = { Text("192.168.1.x") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(16.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                                        ),
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
                                        shape = RoundedCornerShape(16.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                                        ),
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
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            focusManager.clearFocus()
                                            viewModel.onConnect()
                                        },
                                        enabled = !uiState.isConnecting,
                                        modifier = Modifier
                                            .weight(1.3f)
                                            .height(52.dp),
                                        shape = RoundedCornerShape(26.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    ) {
                                        if (uiState.isConnecting) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(22.dp),
                                                strokeWidth = 2.5.dp,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text("Connecting...", fontWeight = FontWeight.SemiBold)
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Connect", fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    FilledTonalButton(
                                        onClick = { viewModel.openPairingDialog() },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(52.dp),
                                        shape = RoundedCornerShape(26.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Link,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Pair Code", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }

                    // 3. Nearby Devices (mDNS) Section
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Nearby Devices",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                if (uiState.isScanning) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(10.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Searching",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }

                            if (!uiState.isScanning) {
                                TextButton(onClick = { viewModel.startDiscovery() }) {
                                    Text("Scan", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    if (uiState.discoveredDevices.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                                ),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(18.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.WifiTethering,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Text(
                                        text = "Ensure 'Wireless Debugging' is switched ON in Developer Options on your target phone.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
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

                    // 4. Quick Saved Devices section
                    if (uiState.recentConnections.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Saved Devices (${uiState.recentConnections.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(onClick = { currentTab = HomeTab.DEVICES }) {
                                    Text("View All", fontWeight = FontWeight.SemiBold)
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

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

            HomeTab.DEVICES -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (uiState.recentConnections.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 40.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                ),
                                shape = RoundedCornerShape(28.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Devices,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                    Text(
                                        text = "No Saved Devices",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Devices you connect to will appear here. ScrcpyDroid tracks each device by persistent ID so IP and port changes never create duplicates.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Button(
                                        onClick = { currentTab = HomeTab.CONNECT },
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier.padding(top = 8.dp)
                                    ) {
                                        Text("Go to Connect")
                                    }
                                }
                            }
                        }
                    } else {
                        item {
                            Text(
                                text = "Tap any device card to connect instantly. Tap sliders to tune resolution, FPS, or unlock credentials.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp)
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

                        item {
                            Spacer(modifier = Modifier.height(16.dp))
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
    val hasCustom = connection.customSettings?.useGlobalSettings == false

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onConnect),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
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
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = connection.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
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
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.height(38.dp)
                ) {
                    Text("Connect", fontWeight = FontWeight.Bold)
                }
            }

            // Custom settings chip or status badge
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
                        label = {
                            Text(
                                "Profile: ${if (parts.isNotEmpty()) parts.joinToString(" • ") else "Custom"}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Text(
                            text = "Global profile",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalIconButton(
                        onClick = onRename,
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Rename",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    FilledTonalIconButton(
                        onClick = onConfigureSettings,
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (hasCustom) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Device Settings",
                            tint = if (hasCustom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    FilledTonalIconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DiscoveredDeviceCard(
    device: DiscoveredDevice,
    onConnect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onConnect),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (device.isPairing) {
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (device.isPairing) MaterialTheme.colorScheme.tertiaryContainer
                            else MaterialTheme.colorScheme.secondaryContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (device.isPairing) Icons.Default.Link else Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = if (device.isPairing) MaterialTheme.colorScheme.onTertiaryContainer
                        else MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${device.host}:${device.port}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (device.isPairing) {
                        Text(
                            text = "Pairing service available",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            FilledTonalButton(
                onClick = onConnect,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.height(38.dp)
            ) {
                Text(
                    text = if (device.isPairing) "Pair" else "Connect",
                    fontWeight = FontWeight.Bold
                )
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
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Text(
                "Rename Device",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Set a friendly nickname for this device (e.g. 'Living Room TV', 'POCO F4 Gaming'):",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Device Nickname") },
                    placeholder = { Text(connection.deviceName.ifBlank { "${connection.host}:${connection.port}" }) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(nameInput)
                    onDismiss()
                },
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Column {
                Text(
                    text = connection.displayName,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(2.dp))
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Switch: Use Custom Settings for this Device
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { useCustom = !useCustom }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Custom Profile", fontWeight = FontWeight.Bold)
                            Text(
                                text = if (useCustom) "Dedicated settings for this device" else "Using global settings",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = useCustom,
                            onCheckedChange = { useCustom = it }
                        )
                    }
                }

                if (useCustom) {
                    // Resolution
                    var resExpanded by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Resolution", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Box {
                            FilledTonalButton(
                                onClick = { resExpanded = true },
                                shape = RoundedCornerShape(12.dp)
                            ) {
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
                        Text("Max FPS", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Box {
                            FilledTonalButton(
                                onClick = { fpsExpanded = true },
                                shape = RoundedCornerShape(12.dp)
                            ) {
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
                            Text("Video Bitrate", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
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
                        Text("Video Codec", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Box {
                            FilledTonalButton(
                                onClick = { codecExpanded = true },
                                shape = RoundedCornerShape(12.dp)
                            ) {
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

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Auto-Unlock for this device
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { autoUnlock = !autoUnlock }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Unlock", fontWeight = FontWeight.Medium)
                            Text("Unlock screen automatically on connect", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = autoUnlock, onCheckedChange = { autoUnlock = it })
                    }

                    // Device Password / PIN
                    OutlinedTextField(
                        value = devicePassword,
                        onValueChange = { devicePassword = it },
                        label = { Text("Device Password / PIN") },
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
                        Text("Forward Audio", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
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
                        Text("Screen Off on Connect", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
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
                        Text("Keep Remote Awake", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Switch(checked = stayAwake, onCheckedChange = { stayAwake = it })
                    }
                }
            }
        },
        confirmButton = {
            Button(
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
                },
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text("Wireless Pairing", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = port,
                    onValueChange = onPortChange,
                    label = { Text("Pairing Port (from dialog)") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = code,
                    onValueChange = onCodeChange,
                    label = { Text("6-Digit Pairing Code") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                when (status) {
                    is PairingStatus.Pairing -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Pairing with device...", style = MaterialTheme.typography.bodySmall)
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
                enabled = status !is PairingStatus.Pairing && host.isNotBlank() && port.isNotBlank() && code.isNotBlank(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("Pair", fontWeight = FontWeight.Bold)
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
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Update Available: ${info.versionName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (info.releaseNotes.isNotBlank()) {
                Text(
                    text = info.releaseNotes.take(150) + if (info.releaseNotes.length > 150) "..." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
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
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
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
                    Button(
                        onClick = onDownload,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("Download & Install", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
