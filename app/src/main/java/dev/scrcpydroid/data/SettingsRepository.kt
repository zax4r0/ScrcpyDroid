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
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "scrcpydroid_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
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
        val BLOCK_CONTROLLER_STATUS_BAR = booleanPreferencesKey("block_controller_status_bar")
        val OTA_REPO = stringPreferencesKey("ota_repo")
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
            val blockControllerStatusBar = preferences[Keys.BLOCK_CONTROLLER_STATUS_BAR] ?: true

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
                blockControllerStatusBar = blockControllerStatusBar
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
    suspend fun updateBlockControllerStatusBar(block: Boolean) = updateOption(Keys.BLOCK_CONTROLLER_STATUS_BAR, block)

    fun getOtaRepo(): Flow<String> = context.settingsDataStore.data.map { preferences ->
        preferences[Keys.OTA_REPO] ?: "zax4r0/ScrcpyDroid"
    }

    suspend fun updateOtaRepo(repo: String) = updateOption(Keys.OTA_REPO, repo)
}
