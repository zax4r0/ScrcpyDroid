package dev.scrcpydroid.ui.mirror

import android.util.Log
import android.view.Surface
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scrcpydroid.core.decoder.VideoDecoder
import dev.scrcpydroid.core.scrcpy.ControlMessageWriter
import dev.scrcpydroid.core.scrcpy.DeviceInfo
import dev.scrcpydroid.core.scrcpy.ScrcpyClient
import dev.scrcpydroid.core.scrcpy.ScrcpyState
import dev.scrcpydroid.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MirrorUiState(
    val deviceInfo: DeviceInfo? = null,
    val isConnected: Boolean = false,
    val showControls: Boolean = true,
    val errorMessage: String? = null,
    val fps: Int = 60,
    val blockControllerStatusBar: Boolean = true
)

@HiltViewModel
class MirrorViewModel @Inject constructor(
    private val scrcpyClient: ScrcpyClient,
    private val videoDecoder: VideoDecoder,
    private val settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "MirrorViewModel"
    }

    private val _uiState = MutableStateFlow(MirrorUiState())
    val uiState: StateFlow<MirrorUiState> = _uiState.asStateFlow()

    init {
        val host: String? = savedStateHandle["host"]
        val port: Int = savedStateHandle["port"] ?: 5555

        // Collect Settings
        viewModelScope.launch {
            settingsRepository.getOptions().collect { options ->
                _uiState.update { it.copy(blockControllerStatusBar = options.blockControllerStatusBar) }
            }
        }

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
                val options = settingsRepository.getOptions().first()
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

    fun startDecoding(surface: Surface) {
        val info = scrcpyClient.getDeviceInfo() ?: _uiState.value.deviceInfo
        val codecMime = info?.codec?.mimeType ?: "video/avc"
        val width = info?.width ?: 1080
        val height = info?.height ?: 1920
        val packetFlow = scrcpyClient.getVideoPacketFlow()
        videoDecoder.start(surface, codecMime, width, height, packetFlow)
    }

    fun stopDecoding() {
        videoDecoder.stop()
    }

    fun onTouchEvent(action: Int, x: Float, y: Float, pressure: Float = 1.0f) {
        viewModelScope.launch(Dispatchers.IO) {
            val writer = scrcpyClient.getControlWriter() ?: return@launch
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

            try {
                writer.injectTouchEvent(
                    action = scrcpyAction,
                    pointerId = 0L,
                    x = x.toInt().coerceIn(0, width),
                    y = y.toInt().coerceIn(0, height),
                    screenWidth = width,
                    screenHeight = height,
                    pressure = pressure
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to inject touch event", e)
            }
        }
    }

    fun pressBack() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                scrcpyClient.getControlWriter()?.pressBack()
            } catch (e: Exception) {
                Log.e(TAG, "Failed pressBack", e)
            }
        }
    }

    fun pressHome() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                scrcpyClient.getControlWriter()?.pressHome()
            } catch (e: Exception) {
                Log.e(TAG, "Failed pressHome", e)
            }
        }
    }

    fun pressAppSwitch() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                scrcpyClient.getControlWriter()?.pressAppSwitch()
            } catch (e: Exception) {
                Log.e(TAG, "Failed pressAppSwitch", e)
            }
        }
    }

    fun rotateDevice() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                scrcpyClient.getControlWriter()?.rotateDevice()
            } catch (e: Exception) {
                Log.e(TAG, "Failed rotateDevice", e)
            }
        }
    }

    fun expandNotificationPanel() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                scrcpyClient.getControlWriter()?.expandNotificationPanel()
            } catch (e: Exception) {
                Log.e(TAG, "Failed expandNotificationPanel", e)
            }
        }
    }

    fun collapsePanels() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                scrcpyClient.getControlWriter()?.collapsePanels()
            } catch (e: Exception) {
                Log.e(TAG, "Failed collapsePanels", e)
            }
        }
    }

    fun toggleControls() {
        _uiState.update { it.copy(showControls = !it.showControls) }
    }

    fun setShowControls(show: Boolean) {
        _uiState.update { it.copy(showControls = show) }
    }

    fun disconnect() {
        viewModelScope.launch {
            videoDecoder.stop()
            scrcpyClient.disconnect()
            _uiState.update { it.copy(isConnected = false) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        videoDecoder.stop()
    }
}
