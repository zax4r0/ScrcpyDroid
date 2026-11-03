package dev.scrcpydroid.ui.mirror

import android.util.Log
import android.view.Surface
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scrcpydroid.core.clipboard.ClipboardSyncManager
import dev.scrcpydroid.core.decoder.VideoDecoder
import dev.scrcpydroid.core.scrcpy.ControlMessageWriter
import dev.scrcpydroid.core.scrcpy.DeviceInfo
import dev.scrcpydroid.core.scrcpy.ScrcpyClient
import dev.scrcpydroid.core.scrcpy.ScrcpyState
import dev.scrcpydroid.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.SocketException
import javax.inject.Inject

private data class TouchEventData(
    val action: Int,
    val pointerId: Long,
    val x: Int,
    val y: Int,
    val screenWidth: Int,
    val screenHeight: Int,
    val pressure: Float
)

data class MirrorUiState(
    val deviceInfo: DeviceInfo? = null,
    val isConnected: Boolean = false,
    val showControls: Boolean = true,
    val errorMessage: String? = null,
    val fps: Int = 60,
    val isDisplayPowerOn: Boolean = true
)

@HiltViewModel
class MirrorViewModel @Inject constructor(
    private val scrcpyClient: ScrcpyClient,
    private val videoDecoder: VideoDecoder,
    private val settingsRepository: SettingsRepository,
    private val clipboardSyncManager: ClipboardSyncManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "MirrorViewModel"
    }

    private val _uiState = MutableStateFlow(MirrorUiState())
    val uiState: StateFlow<MirrorUiState> = _uiState.asStateFlow()

    val host: String? = savedStateHandle["host"]
    val port: Int = savedStateHandle["port"] ?: 5555
    private var lastAttachedSurface: Surface? = null
    private var isReconnecting = false

    private val touchChannel = Channel<TouchEventData>(capacity = 128)

    init {
        // Dedicated low-latency touch worker loop (zero coroutine allocations per touch event)
        viewModelScope.launch(Dispatchers.IO) {
            for (touch in touchChannel) {
                val writer = scrcpyClient.getControlWriter() ?: continue
                try {
                    writer.injectTouchEvent(
                        action = touch.action,
                        pointerId = touch.pointerId,
                        x = touch.x,
                        y = touch.y,
                        screenWidth = touch.screenWidth,
                        screenHeight = touch.screenHeight,
                        pressure = touch.pressure
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to inject touch event", e)
                    handleConnectionFailure(e)
                }
            }
        }

        // Start clipboard synchronization with remote device
        clipboardSyncManager.start(viewModelScope)

        // Collect ScrcpyClient state
        viewModelScope.launch {
            scrcpyClient.state.collect { state ->
                when (state) {
                    is ScrcpyState.Connected -> {
                        _uiState.update {
                            it.copy(
                                deviceInfo = state.deviceInfo,
                                isConnected = true,
                                errorMessage = null
                            )
                        }
                    }
                    is ScrcpyState.Streaming -> {
                        val info = scrcpyClient.getDeviceInfo()
                        _uiState.update {
                            it.copy(
                                deviceInfo = info ?: it.deviceInfo,
                                isConnected = true,
                                errorMessage = null
                            )
                        }
                    }
                    is ScrcpyState.Error -> {
                        _uiState.update {
                            it.copy(
                                isConnected = false,
                                errorMessage = state.message
                            )
                        }
                        autoReconnect()
                    }
                    is ScrcpyState.Disconnected -> {
                        _uiState.update {
                            it.copy(isConnected = false)
                        }
                    }
                    else -> {}
                }
            }
        }

        // Auto-connect if not already connected
        if (!host.isNullOrBlank() && scrcpyClient.state.value is ScrcpyState.Disconnected) {
            autoConnect(host, port)
        } else {
            // Already connected from HomeScreen
            val info = scrcpyClient.getDeviceInfo()
            if (info != null) {
                _uiState.update { it.copy(deviceInfo = info, isConnected = true) }
            }
        }
    }

    private fun autoConnect(host: String, port: Int) {
        viewModelScope.launch {
            try {
                val options = settingsRepository.getOptionsForDevice(host, port, scrcpyClient.getDeviceId())
                _uiState.update { it.copy(fps = options.maxFps) }
                scrcpyClient.connect(host, port, options)
            } catch (e: Exception) {
                Log.e(TAG, "Failed auto-connecting to $host:$port", e)
                _uiState.update {
                    it.copy(
                        isConnected = false,
                        errorMessage = e.message ?: "Failed to connect to $host:$port"
                    )
                }
            }
        }
    }

    fun autoReconnect() {
        val h = host
        if (h.isNullOrBlank() || isReconnecting) return
        isReconnecting = true

        viewModelScope.launch {
            try {
                Log.i(TAG, "Connection lost, auto-reconnecting to $h:$port...")
                _uiState.update { it.copy(isConnected = false, errorMessage = "Reconnecting...") }
                videoDecoder.stop()
                scrcpyClient.disconnect()
                delay(400)
                val options = settingsRepository.getOptionsForDevice(h, port, scrcpyClient.getDeviceId())
                scrcpyClient.connect(h, port, options)
                Log.i(TAG, "Auto-reconnect successful!")
                _uiState.update { it.copy(isConnected = true, errorMessage = null) }
                if (options.autoUnlockOnConnect) {
                    scrcpyClient.unlockDevice(options.devicePassword)
                }
                val surface = lastAttachedSurface
                if (surface != null && surface.isValid) {
                    startDecoding(surface)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Auto-reconnect failed: ${e.message}")
                _uiState.update { it.copy(errorMessage = "Connection lost. Tap to reconnect.") }
            } finally {
                isReconnecting = false
            }
        }
    }

    private fun handleConnectionFailure(e: Throwable) {
        if (e is SocketException || e is IOException) {
            Log.w(TAG, "Socket failure detected: ${e.message}")
            autoReconnect()
        }
    }

    fun startDecoding(surface: Surface) {
        lastAttachedSurface = surface
        val info = scrcpyClient.getDeviceInfo() ?: _uiState.value.deviceInfo
        val codecMime = info?.codec?.mimeType ?: "video/avc"
        val width = info?.width ?: 1080
        val height = info?.height ?: 1920
        val packetFlow = scrcpyClient.getVideoPacketFlow()
        val initialConfig = scrcpyClient.getLastConfigPacket()?.data
        videoDecoder.start(surface, codecMime, width, height, packetFlow, initialConfig)

        // Request a fresh IDR keyframe and config packet from scrcpy server
        resetVideo()
    }

    fun onSurfaceDestroyed() {
        videoDecoder.onSurfaceDestroyed()
    }

    fun stopDecoding() {
        videoDecoder.stop()
    }

    private var lastResetVideoTime = 0L

    fun resetVideo() {
        val now = System.currentTimeMillis()
        if (now - lastResetVideoTime < 300) {
            return
        }
        lastResetVideoTime = now

        viewModelScope.launch(Dispatchers.IO) {
            try {
                scrcpyClient.getControlWriter()?.resetVideo()
                Log.d(TAG, "Sent resetVideo to scrcpy server")
            } catch (e: Exception) {
                Log.w(TAG, "Failed resetVideo", e)
                handleConnectionFailure(e)
            }
        }
    }

    fun onTouchEvent(action: Int, x: Float, y: Float, pressure: Float = 1.0f) {
        val info = _uiState.value.deviceInfo ?: scrcpyClient.getDeviceInfo()
        val width = info?.width ?: 1080
        val height = info?.height ?: 1920

        val scrcpyAction = when (action) {
            0 -> ControlMessageWriter.ACTION_DOWN
            1 -> ControlMessageWriter.ACTION_UP
            2 -> ControlMessageWriter.ACTION_MOVE
            3 -> ControlMessageWriter.ACTION_UP
            else -> action
        }

        touchChannel.trySend(
            TouchEventData(
                action = scrcpyAction,
                pointerId = 0L,
                x = x.toInt().coerceIn(0, width),
                y = y.toInt().coerceIn(0, height),
                screenWidth = width,
                screenHeight = height,
                pressure = pressure
            )
        )
    }

    fun pressBack() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                scrcpyClient.getControlWriter()?.pressBack()
            } catch (e: Exception) {
                Log.e(TAG, "Failed pressBack", e)
                handleConnectionFailure(e)
            }
        }
    }

    fun pressHome() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                scrcpyClient.getControlWriter()?.pressHome()
            } catch (e: Exception) {
                Log.e(TAG, "Failed pressHome", e)
                handleConnectionFailure(e)
            }
        }
    }

    fun pressAppSwitch() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                scrcpyClient.getControlWriter()?.pressAppSwitch()
            } catch (e: Exception) {
                Log.e(TAG, "Failed pressAppSwitch", e)
                handleConnectionFailure(e)
            }
        }
    }

    fun rotateDevice() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                scrcpyClient.getControlWriter()?.rotateDevice()
            } catch (e: Exception) {
                Log.e(TAG, "Failed rotateDevice", e)
                handleConnectionFailure(e)
            }
        }
    }

    private var lastSyncedRotation = -1

    fun syncRotation(rotation: Int) {
        if (rotation == lastSyncedRotation) return
        lastSyncedRotation = rotation

        viewModelScope.launch(Dispatchers.IO) {
            try {
                scrcpyClient.setRemoteRotation(rotation)
            } catch (e: Exception) {
                Log.w(TAG, "Failed syncing rotation: ${e.message}")
            }
        }
    }

    fun expandNotificationPanel() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                scrcpyClient.getControlWriter()?.expandNotificationPanel()
            } catch (e: Exception) {
                Log.e(TAG, "Failed expandNotificationPanel", e)
                handleConnectionFailure(e)
            }
        }
    }

    fun collapsePanels() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                scrcpyClient.getControlWriter()?.collapsePanels()
            } catch (e: Exception) {
                Log.e(TAG, "Failed collapsePanels", e)
                handleConnectionFailure(e)
            }
        }
    }

    fun setDisplayPower(on: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                scrcpyClient.getControlWriter()?.setDisplayPower(on)
                _uiState.update { it.copy(isDisplayPowerOn = on) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed setDisplayPower", e)
                handleConnectionFailure(e)
            }
        }
    }

    fun toggleDisplayPower() {
        val next = !_uiState.value.isDisplayPowerOn
        setDisplayPower(next)
    }

    fun toggleControls() {
        _uiState.update { it.copy(showControls = !it.showControls) }
    }

    fun setShowControls(show: Boolean) {
        _uiState.update { it.copy(showControls = show) }
    }

    fun unlockDevice() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val h = host
                val options = if (h != null) settingsRepository.getOptionsForDevice(h, port, scrcpyClient.getDeviceId()) else settingsRepository.getOptions().first()
                scrcpyClient.unlockDevice(options.devicePassword)
            } catch (e: Exception) {
                Log.w(TAG, "Failed manual unlockDevice", e)
                handleConnectionFailure(e)
            }
        }
    }

    fun onAppResumed() {
        clipboardSyncManager.onResumeOrFocus()
        val currentState = scrcpyClient.state.value
        if (currentState is ScrcpyState.Error || currentState is ScrcpyState.Disconnected) {
            Log.i(TAG, "Resumed with disconnected state, auto-reconnecting to $host:$port")
            autoReconnect()
        } else {
            resetVideo()
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val h = host
                    val options = if (h != null) settingsRepository.getOptionsForDevice(h, port, scrcpyClient.getDeviceId()) else settingsRepository.getOptions().first()
                    if (options.autoUnlockOnConnect) {
                        scrcpyClient.unlockDevice(options.devicePassword)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed resume unlock: ${e.message}")
                }
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            clipboardSyncManager.stop()
            videoDecoder.stop()
            videoDecoder.clearConfig()
            scrcpyClient.disconnect()
            _uiState.update { it.copy(isConnected = false) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        clipboardSyncManager.stop()
        videoDecoder.stop()
    }
}
