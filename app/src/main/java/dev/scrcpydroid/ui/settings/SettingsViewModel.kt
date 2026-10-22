package dev.scrcpydroid.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scrcpydroid.core.ota.DownloadProgress
import dev.scrcpydroid.core.ota.OtaUpdateManager
import dev.scrcpydroid.core.ota.UpdateCheckResult
import dev.scrcpydroid.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val maxSize: Int = 1920,
    val videoBitRate: Int = 8_000_000,
    val maxFps: Int = 60,
    val videoCodec: String = "h264",
    val stayAwake: Boolean = true,
    val showTouches: Boolean = false,
    val audioEnabled: Boolean = true,
    val audioDup: Boolean = false,
    val dndOnDisconnect: Boolean = true,
    val lockOnDisconnect: Boolean = true,
    val turnScreenOffOnConnect: Boolean = false,
    val autoWakeOnConnect: Boolean = true,
    val clipboardSync: Boolean = true,
    val otaRepo: String = "zax4r0/ScrcpyDroid",
    val updateResult: UpdateCheckResult = UpdateCheckResult.Idle,
    val downloadProgress: DownloadProgress? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val otaUpdateManager: OtaUpdateManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.getOptions().collect { options ->
                _uiState.update {
                    it.copy(
                        maxSize = options.maxSize,
                        videoBitRate = options.videoBitRate,
                        maxFps = options.maxFps,
                        videoCodec = options.videoCodec.id,
                        stayAwake = options.stayAwake,
                        showTouches = options.showTouches,
                        audioEnabled = options.audioEnabled,
                        audioDup = options.audioDup,
                        dndOnDisconnect = options.dndOnDisconnect,
                        lockOnDisconnect = options.lockOnDisconnect,
                        turnScreenOffOnConnect = options.turnScreenOffOnConnect,
                        autoWakeOnConnect = options.autoWakeOnConnect,
                        clipboardSync = options.clipboardSync
                    )
                }
            }
        }

        viewModelScope.launch {
            settingsRepository.getOtaRepo().collect { repo ->
                _uiState.update { it.copy(otaRepo = repo) }
            }
        }
    }

    fun setMaxSize(size: Int) {
        viewModelScope.launch {
            settingsRepository.updateMaxSize(size)
        }
    }

    fun setVideoBitRate(bitRate: Int) {
        viewModelScope.launch {
            settingsRepository.updateVideoBitRate(bitRate)
        }
    }

    fun setMaxFps(fps: Int) {
        viewModelScope.launch {
            settingsRepository.updateMaxFps(fps)
        }
    }

    fun setVideoCodec(codec: String) {
        viewModelScope.launch {
            settingsRepository.updateVideoCodec(codec)
        }
    }

    fun setStayAwake(stayAwake: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateStayAwake(stayAwake)
        }
    }

    fun setShowTouches(showTouches: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateShowTouches(showTouches)
        }
    }

    fun setAudioEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateAudioEnabled(enabled)
        }
    }

    fun setAudioDup(dup: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateAudioDup(dup)
        }
    }

    fun setDndOnDisconnect(dnd: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateDndOnDisconnect(dnd)
        }
    }

    fun setLockOnDisconnect(lock: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateLockOnDisconnect(lock)
        }
    }

    fun setTurnScreenOffOnConnect(turnOff: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateTurnScreenOffOnConnect(turnOff)
        }
    }

    fun setAutoWakeOnConnect(wake: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateAutoWakeOnConnect(wake)
        }
    }

    fun setClipboardSync(sync: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateClipboardSync(sync)
        }
    }

    fun setOtaRepo(repo: String) {
        viewModelScope.launch {
            settingsRepository.updateOtaRepo(repo)
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _uiState.update { it.copy(updateResult = UpdateCheckResult.Checking) }
            val repo = _uiState.value.otaRepo
            val result = otaUpdateManager.checkForUpdates(repo)
            _uiState.update { it.copy(updateResult = result) }
        }
    }

    fun downloadAndInstall(url: String) {
        viewModelScope.launch {
            otaUpdateManager.downloadApk(url).collect { progress ->
                _uiState.update { it.copy(downloadProgress = progress) }
                if (progress is DownloadProgress.Completed) {
                    otaUpdateManager.installApk(progress.file)
                }
            }
        }
    }

    fun clearUpdateResult() {
        _uiState.update { it.copy(updateResult = UpdateCheckResult.Idle, downloadProgress = null) }
    }
}
