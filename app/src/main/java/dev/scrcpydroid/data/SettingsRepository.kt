package dev.scrcpydroid.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.scrcpydroid.core.scrcpy.ScrcpyOptions
import dev.scrcpydroid.core.scrcpy.VideoCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "scrcpydroid_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connectionRepository: ConnectionRepository
) {
    object Keys {
        val MAX_SIZE = intPreferencesKey("max_size")
        val VIDEO_BIT_RATE = intPreferencesKey("video_bit_rate")
        val MAX_FPS = intPreferencesKey("max_fps")
        val VIDEO_CODEC = stringPreferencesKey("video_codec")
        val STAY_AWAKE = booleanPreferencesKey("stay_awake")
        val SHOW_TOUCHES = booleanPreferencesKey("show_touches")
        val AUDIO_ENABLED = booleanPreferencesKey("audio_enabled")
        val AUDIO_DUP = booleanPreferencesKey("audio_dup")
        val DND_ON_DISCONNECT = booleanPreferencesKey("dnd_on_disconnect")
        val LOCK_ON_DISCONNECT = booleanPreferencesKey("lock_on_disconnect")
        val TURN_SCREEN_OFF_ON_CONNECT = booleanPreferencesKey("turn_screen_off_on_connect")
        val AUTO_WAKE_ON_CONNECT = booleanPreferencesKey("auto_wake_on_connect")
        val CLIPBOARD_SYNC = booleanPreferencesKey("clipboard_sync")
        val AUTO_UNLOCK_ON_CONNECT = booleanPreferencesKey("auto_unlock_on_connect")
        val DEVICE_PASSWORD = stringPreferencesKey("device_password")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val DARK_THEME_MODE = stringPreferencesKey("dark_theme_mode")
        val OTA_REPO = stringPreferencesKey("ota_repo")
        val OTA_INCLUDE_PRERELEASE = booleanPreferencesKey("ota_include_prerelease")
    }

    private val optionsFlow: Flow<ScrcpyOptions> = context.settingsDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val maxSize = preferences[Keys.MAX_SIZE] ?: 1920
            val videoBitRate = preferences[Keys.VIDEO_BIT_RATE] ?: 8_000_000
            val maxFps = preferences[Keys.MAX_FPS] ?: 60
            val codecStr = preferences[Keys.VIDEO_CODEC] ?: "h264"
            val stayAwake = preferences[Keys.STAY_AWAKE] ?: true
            val showTouches = preferences[Keys.SHOW_TOUCHES] ?: false
            val audioEnabled = preferences[Keys.AUDIO_ENABLED] ?: true
            val audioDup = preferences[Keys.AUDIO_DUP] ?: false
            val dndOnDisconnect = preferences[Keys.DND_ON_DISCONNECT] ?: true
            val lockOnDisconnect = preferences[Keys.LOCK_ON_DISCONNECT] ?: true
            val turnScreenOffOnConnect = preferences[Keys.TURN_SCREEN_OFF_ON_CONNECT] ?: false
            val autoWakeOnConnect = preferences[Keys.AUTO_WAKE_ON_CONNECT] ?: true
            val clipboardSync = preferences[Keys.CLIPBOARD_SYNC] ?: true
            val autoUnlockOnConnect = preferences[Keys.AUTO_UNLOCK_ON_CONNECT] ?: true
            val devicePassword = preferences[Keys.DEVICE_PASSWORD] ?: ""

            val videoCodec = if (codecStr.equals("h265", ignoreCase = true)) {
                VideoCodec.H265
            } else {
                VideoCodec.H264
            }

            ScrcpyOptions(
                maxSize = maxSize,
                videoBitRate = videoBitRate,
                maxFps = maxFps,
                videoCodec = videoCodec,
                audioEnabled = audioEnabled,
                audioCodec = "raw",
                audioDup = audioDup,
                controlEnabled = true,
                stayAwake = stayAwake,
                showTouches = showTouches,
                powerOffOnClose = false,
                dndOnDisconnect = dndOnDisconnect,
                lockOnDisconnect = lockOnDisconnect,
                turnScreenOffOnConnect = turnScreenOffOnConnect,
                autoWakeOnConnect = autoWakeOnConnect,
                clipboardSync = clipboardSync,
                autoUnlockOnConnect = autoUnlockOnConnect,
                devicePassword = devicePassword
            )
        }

    fun getOptions(): Flow<ScrcpyOptions> = optionsFlow

    suspend fun <T> updateOption(key: Preferences.Key<T>, value: T) {
        context.settingsDataStore.edit { preferences ->
            preferences[key] = value
        }
    }

    suspend fun updateMaxSize(size: Int) = updateOption(Keys.MAX_SIZE, size)
    suspend fun updateVideoBitRate(bitRate: Int) = updateOption(Keys.VIDEO_BIT_RATE, bitRate)
    suspend fun updateMaxFps(fps: Int) = updateOption(Keys.MAX_FPS, fps)
    suspend fun updateVideoCodec(codec: String) = updateOption(Keys.VIDEO_CODEC, codec)
    suspend fun updateStayAwake(stayAwake: Boolean) = updateOption(Keys.STAY_AWAKE, stayAwake)
    suspend fun updateShowTouches(showTouches: Boolean) = updateOption(Keys.SHOW_TOUCHES, showTouches)
    suspend fun updateAudioEnabled(enabled: Boolean) = updateOption(Keys.AUDIO_ENABLED, enabled)
    suspend fun updateAudioDup(dup: Boolean) = updateOption(Keys.AUDIO_DUP, dup)
    suspend fun updateDndOnDisconnect(dnd: Boolean) = updateOption(Keys.DND_ON_DISCONNECT, dnd)
    suspend fun updateLockOnDisconnect(lock: Boolean) = updateOption(Keys.LOCK_ON_DISCONNECT, lock)
    suspend fun updateTurnScreenOffOnConnect(turnOff: Boolean) = updateOption(Keys.TURN_SCREEN_OFF_ON_CONNECT, turnOff)
    suspend fun updateAutoWakeOnConnect(wake: Boolean) = updateOption(Keys.AUTO_WAKE_ON_CONNECT, wake)
    suspend fun updateClipboardSync(sync: Boolean) = updateOption(Keys.CLIPBOARD_SYNC, sync)
    suspend fun updateAutoUnlockOnConnect(autoUnlock: Boolean) = updateOption(Keys.AUTO_UNLOCK_ON_CONNECT, autoUnlock)
    suspend fun updateDevicePassword(password: String) = updateOption(Keys.DEVICE_PASSWORD, password)

    fun isDynamicColor(): Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[Keys.DYNAMIC_COLOR] ?: true
    }

    suspend fun updateDynamicColor(enabled: Boolean) = updateOption(Keys.DYNAMIC_COLOR, enabled)

    fun getDarkThemeMode(): Flow<String> = context.settingsDataStore.data.map { preferences ->
        preferences[Keys.DARK_THEME_MODE] ?: "system"
    }

    suspend fun updateDarkThemeMode(mode: String) = updateOption(Keys.DARK_THEME_MODE, mode)

    suspend fun getOptionsForDevice(host: String, port: Int, deviceId: String = ""): ScrcpyOptions {
        val global = getOptions().first()
        val connection = connectionRepository.getConnection(host, port, deviceId)
        val dev = connection?.customSettings ?: return global
        if (dev.useGlobalSettings) return global

        val videoCodec = when (dev.videoCodec?.lowercase()) {
            "h265" -> VideoCodec.H265
            "h264" -> VideoCodec.H264
            else -> global.videoCodec
        }

        return global.copy(
            maxSize = dev.maxSize ?: global.maxSize,
            videoBitRate = dev.videoBitRate ?: global.videoBitRate,
            maxFps = dev.maxFps ?: global.maxFps,
            videoCodec = videoCodec,
            audioEnabled = dev.audioEnabled ?: global.audioEnabled,
            autoUnlockOnConnect = dev.autoUnlock ?: global.autoUnlockOnConnect,
            devicePassword = dev.devicePassword ?: global.devicePassword,
            turnScreenOffOnConnect = dev.turnScreenOffOnConnect ?: global.turnScreenOffOnConnect,
            stayAwake = dev.stayAwake ?: global.stayAwake
        )
    }

    fun getOtaRepo(): Flow<String> = context.settingsDataStore.data.map { preferences ->
        preferences[Keys.OTA_REPO] ?: "zax4r0/ScrcpyDroid"
    }

    suspend fun updateOtaRepo(repo: String) = updateOption(Keys.OTA_REPO, repo)

    fun getOtaIncludePrerelease(): Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[Keys.OTA_INCLUDE_PRERELEASE] ?: false
    }

    suspend fun updateOtaIncludePrerelease(include: Boolean) = updateOption(Keys.OTA_INCLUDE_PRERELEASE, include)
}
