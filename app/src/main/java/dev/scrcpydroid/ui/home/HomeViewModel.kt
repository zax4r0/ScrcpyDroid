package dev.scrcpydroid.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scrcpydroid.core.adb.AdbPairingManager
import dev.scrcpydroid.core.discovery.DeviceDiscoveryManager
import dev.scrcpydroid.core.discovery.DiscoveredDevice
import dev.scrcpydroid.core.ota.AppReleaseInfo
import dev.scrcpydroid.core.ota.DownloadProgress
import dev.scrcpydroid.core.ota.OtaUpdateManager
import dev.scrcpydroid.core.ota.UpdateCheckResult
import dev.scrcpydroid.core.scrcpy.ScrcpyClient
import dev.scrcpydroid.core.scrcpy.ScrcpyState
import dev.scrcpydroid.data.ConnectionRepository
import dev.scrcpydroid.data.SettingsRepository
import dev.scrcpydroid.data.model.DeviceConnection
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed interface PairingStatus {
    data object Idle : PairingStatus
    data object Pairing : PairingStatus
    data class Success(val message: String) : PairingStatus
    data class Error(val message: String) : PairingStatus
}

data class HomeUiState(
    val ipAddress: String = "",
    val port: String = "5555",
    val isConnecting: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val recentConnections: List<DeviceConnection> = emptyList(),
    // Discovery
    val discoveredDevices: List<DiscoveredDevice> = emptyList(),
    val isScanning: Boolean = false,
    // Pairing dialog
    val showPairingDialog: Boolean = false,
    val pairingHost: String = "",
    val pairingPort: String = "",
    val pairingCode: String = "",
    val pairingStatus: PairingStatus = PairingStatus.Idle,
    // OTA Update
    val updateResult: UpdateCheckResult = UpdateCheckResult.Idle,
    val downloadProgress: DownloadProgress? = null
)

sealed interface HomeNavigationEvent {
    data class NavigateToMirror(val host: String, val port: Int) : HomeNavigationEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val scrcpyClient: ScrcpyClient,
    private val connectionRepository: ConnectionRepository,
    private val settingsRepository: SettingsRepository,
    private val discoveryManager: DeviceDiscoveryManager,
    private val pairingManager: AdbPairingManager,
    private val otaUpdateManager: OtaUpdateManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<HomeNavigationEvent>()
    val navigationEvent: SharedFlow<HomeNavigationEvent> = _navigationEvent.asSharedFlow()

    init {
        // Collect recent connections
        viewModelScope.launch {
            connectionRepository.getRecentConnections().collect { connections ->
                _uiState.update { it.copy(recentConnections = connections) }
            }
        }

        // Collect ScrcpyClient state
        viewModelScope.launch {
            scrcpyClient.state.collect { state ->
                when (state) {
                    is ScrcpyState.Connecting,
                    is ScrcpyState.ServerStarting -> {
                        _uiState.update { it.copy(isConnecting = true, errorMessage = null) }
                    }
                    is ScrcpyState.Connected,
                    is ScrcpyState.Streaming,
                    is ScrcpyState.Disconnected -> {
                        _uiState.update { it.copy(isConnecting = false) }
                    }
                    is ScrcpyState.Error -> {
                        _uiState.update { it.copy(isConnecting = false, errorMessage = state.message) }
                    }
                }
            }
        }

        // Collect discovered devices from mDNS
        viewModelScope.launch {
            discoveryManager.discoveredDevices.collect { devices ->
                _uiState.update { it.copy(discoveredDevices = devices) }
            }
        }

        viewModelScope.launch {
            discoveryManager.isScanning.collect { scanning ->
                _uiState.update { it.copy(isScanning = scanning) }
            }
        }

        // Start mDNS discovery automatically
        startDiscovery()

        // Check for OTA update in background
        checkForUpdates()
    }

    fun startDiscovery() {
        discoveryManager.startDiscovery()
    }

    fun stopDiscovery() {
        discoveryManager.stopDiscovery()
    }

    fun onIpAddressChange(ip: String) {
        _uiState.update { it.copy(ipAddress = ip, errorMessage = null) }
    }

    fun onPortChange(port: String) {
        _uiState.update { it.copy(port = port, errorMessage = null) }
    }

    fun onSelectDiscoveredDevice(device: DiscoveredDevice) {
        if (device.isPairing) {
            openPairingDialog(device.host, device.port.toString())
        } else {
            _uiState.update {
                it.copy(
                    ipAddress = device.host,
                    port = device.port.toString(),
                    errorMessage = null
                )
            }
            onConnect(device.host, device.port.toString())
        }
    }

    fun openPairingDialog(defaultHost: String = "", defaultPort: String = "") {
        val pairingDev = _uiState.value.discoveredDevices.firstOrNull { it.isPairing }
        val host = defaultHost.ifEmpty { pairingDev?.host ?: _uiState.value.ipAddress }
        val port = defaultPort.ifEmpty { pairingDev?.port?.toString() ?: "" }
        _uiState.update {
            it.copy(
                showPairingDialog = true,
                pairingHost = host,
                pairingPort = port,
                pairingCode = "",
                pairingStatus = PairingStatus.Idle
            )
        }
    }

    fun closePairingDialog() {
        _uiState.update { it.copy(showPairingDialog = false) }
    }

    fun onPairingHostChange(host: String) {
        _uiState.update { it.copy(pairingHost = host) }
    }

    fun onPairingPortChange(port: String) {
        _uiState.update { it.copy(pairingPort = port) }
    }

    fun onPairingCodeChange(code: String) {
        _uiState.update { it.copy(pairingCode = code) }
    }

    fun executePairing() {
        val host = _uiState.value.pairingHost.trim()
        val port = _uiState.value.pairingPort.trim().toIntOrNull()
        val code = _uiState.value.pairingCode.trim()

        if (host.isBlank() || port == null || code.length < 6) {
            _uiState.update {
                it.copy(pairingStatus = PairingStatus.Error("Please enter host, valid port, and 6-digit code"))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(pairingStatus = PairingStatus.Pairing) }
            val result = pairingManager.pair(host, port, code)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            pairingStatus = PairingStatus.Success("Pairing successful! You can now connect."),
                            successMessage = "Device paired successfully! Connecting...",
                            ipAddress = host
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(pairingStatus = PairingStatus.Error(error.message ?: "Pairing failed"))
                    }
                }
            )
        }
    }

    fun onConnect(host: String = _uiState.value.ipAddress, port: String = _uiState.value.port) {
        val trimmedHost = host.trim()
        val portInt = port.trim().toIntOrNull()

        if (trimmedHost.isBlank()) {
            _uiState.update { it.copy(errorMessage = "IP address cannot be empty") }
            return
        }

        if (portInt == null || portInt !in 1..65535) {
            _uiState.update { it.copy(errorMessage = "Port must be a valid number between 1 and 65535") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, errorMessage = null) }
            try {
                val options = settingsRepository.getOptions().first()
                scrcpyClient.connect(trimmedHost, portInt, options)

                val deviceInfo = scrcpyClient.getDeviceInfo()
                val deviceName = deviceInfo?.name?.ifEmpty { trimmedHost } ?: trimmedHost

                connectionRepository.addConnection(
                    DeviceConnection(
                        host = trimmedHost,
                        port = portInt,
                        deviceName = deviceName,
                        lastConnected = System.currentTimeMillis()
                    )
                )

                _navigationEvent.emit(HomeNavigationEvent.NavigateToMirror(trimmedHost, portInt))
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        errorMessage = e.message ?: "Failed to connect to $trimmedHost:$portInt"
                    )
                }
            }
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _uiState.update { it.copy(updateResult = UpdateCheckResult.Checking) }
            val repo = settingsRepository.getOtaRepo().first()
            val result = otaUpdateManager.checkForUpdates(repo)
            _uiState.update { it.copy(updateResult = result) }
        }
    }

    fun startDownload(url: String) {
        viewModelScope.launch {
            otaUpdateManager.downloadApk(url).collect { progress ->
                _uiState.update { it.copy(downloadProgress = progress) }
                if (progress is DownloadProgress.Completed) {
                    otaUpdateManager.installApk(progress.file)
                }
            }
        }
    }

    fun installDownloadedFile(file: File) {
        otaUpdateManager.installApk(file)
    }

    fun dismissUpdateBanner() {
        _uiState.update { it.copy(updateResult = UpdateCheckResult.Idle) }
    }

    fun onDeleteConnection(connection: DeviceConnection) {
        viewModelScope.launch {
            connectionRepository.removeConnection(connection)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearSuccess() {
        _uiState.update { it.copy(successMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        discoveryManager.stopDiscovery()
    }
}
