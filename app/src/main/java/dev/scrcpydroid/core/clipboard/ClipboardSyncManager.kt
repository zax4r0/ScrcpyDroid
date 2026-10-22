package dev.scrcpydroid.core.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.scrcpydroid.core.scrcpy.ScrcpyClient
import dev.scrcpydroid.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClipboardSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scrcpyClient: ScrcpyClient,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private const val TAG = "ClipboardSyncManager"
    }

    private val clipboardManager: ClipboardManager? by lazy {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    }

    private var syncJob: Job? = null
    @Volatile
    private var lastSyncedText: String? = null

    private val clipChangedListener = ClipboardManager.OnPrimaryClipChangedListener {
        syncCurrentLocalToRemote()
    }

    fun start(scope: CoroutineScope) {
        stop()

        val cm = clipboardManager ?: return
        try {
            cm.addPrimaryClipChangedListener(clipChangedListener)
        } catch (e: Exception) {
            Log.w(TAG, "Failed adding clip changed listener: ${e.message}")
        }

        syncJob = scope.launch(Dispatchers.IO) {
            // Check initial local clipboard to sync on connect
            syncCurrentLocalToRemote()

            // Listen for incoming remote clipboard changes
            scrcpyClient.remoteClipboardFlow.collect { remoteText ->
                val options = settingsRepository.getOptions().first()
                if (!options.clipboardSync) return@collect
                if (remoteText == lastSyncedText || remoteText.isBlank()) return@collect

                lastSyncedText = remoteText
                withContext(Dispatchers.Main) {
                    try {
                        val clip = ClipData.newPlainText("ScrcpyDroid", remoteText)
                        cm.setPrimaryClip(clip)
                        Log.d(TAG, "Synced remote -> local clipboard: ${remoteText.take(40)}")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed setting local clip: ${e.message}")
                    }
                }
            }
        }
    }

    fun onResumeOrFocus() {
        syncCurrentLocalToRemote()
    }

    fun syncCurrentLocalToRemote() {
        val cm = clipboardManager ?: return
        try {
            val clip = cm.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).coerceToText(context)?.toString() ?: ""
                if (text.isNotBlank() && text != lastSyncedText) {
                    lastSyncedText = text
                    scrcpyClient.getControlWriter()?.setClipboard(text, paste = false)
                    Log.d(TAG, "Synced local -> remote clipboard: ${text.take(40)}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading local clip: ${e.message}")
        }
    }

    fun stop() {
        try {
            clipboardManager?.removePrimaryClipChangedListener(clipChangedListener)
        } catch (_: Exception) {}
        syncJob?.cancel()
        syncJob = null
    }
}
