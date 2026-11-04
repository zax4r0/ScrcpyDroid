package dev.scrcpydroid.core.relay

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class TargetSwapStatus(
    val isChecking: Boolean = false,
    val isRootAvailable: Boolean = false,
    val isLsposedAvailable: Boolean = false,
    val isCamSwapInstalled: Boolean = false,
    val isModuleEnabled: Boolean = false,
    val scopedAppsCount: Int = 0,
    val statusMessage: String = "Ready",
    val error: String? = null
)

@Singleton
class TargetCamSwapManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "TargetCamSwapManager"
        const val CAMSWAP_PACKAGE = "io.github.zensu357.camswap"
        const val CAMSWAP_DOWNLOAD_URL =
            "https://github.com/zensu357/Android-CamSwap-OpenSource/releases/download/v2.8/app-arm64-v8a-release.apk"
        const val LSP_CLI = "/data/adb/lspd/cli"
    }

    private val _status = MutableStateFlow(TargetSwapStatus())
    val status: StateFlow<TargetSwapStatus> = _status.asStateFlow()

    suspend fun checkTargetStatus(dadb: Dadb): TargetSwapStatus = withContext(Dispatchers.IO) {
        _status.update { it.copy(isChecking = true, error = null) }
        try {
            // 1. Check Root
            val rootRes = dadb.shell("su -c id 2>/dev/null || id").output
            val hasRoot = rootRes.contains("uid=0(root)")

            if (!hasRoot) {
                val s = TargetSwapStatus(
                    isChecking = false,
                    isRootAvailable = false,
                    statusMessage = "Root (Magisk/KernelSU) not found on target phone"
                )
                _status.value = s
                return@withContext s
            }

            // 2. Check LSPosed CLI
            val lspRes = dadb.shell("su -c 'test -x $LSP_CLI && echo OK'").output
            val hasLsposed = lspRes.contains("OK")

            // 3. Check CamSwap Package
            val pkgRes = dadb.shell("pm path $CAMSWAP_PACKAGE").output
            val isInstalled = pkgRes.contains("package:")

            // 4. Check Scoped Apps Count
            var scopedCount = 0
            if (hasLsposed && isInstalled) {
                val scopeRes = dadb.shell("su -c '$LSP_CLI scope ls $CAMSWAP_PACKAGE 2>/dev/null'").output
                scopedCount = scopeRes.lines().count { it.contains("/0") || it.contains("0 ") }
            }

            val ready = hasRoot && hasLsposed && isInstalled && scopedCount > 0
            val msg = when {
                ready -> "Ready • $scopedCount apps scoped"
                !isInstalled -> "LSPosed active • CamSwap not installed"
                scopedCount == 0 -> "CamSwap installed • Not yet scoped"
                else -> "Ready"
            }

            val s = TargetSwapStatus(
                isChecking = false,
                isRootAvailable = true,
                isLsposedAvailable = hasLsposed,
                isCamSwapInstalled = isInstalled,
                isModuleEnabled = hasLsposed && isInstalled,
                scopedAppsCount = scopedCount,
                statusMessage = msg
            )
            _status.value = s
            return@withContext s
        } catch (e: Exception) {
            Log.e(TAG, "Failed checking target status", e)
            val s = TargetSwapStatus(
                isChecking = false,
                error = e.message ?: "Failed querying target device"
            )
            _status.value = s
            return@withContext s
        }
    }

    suspend fun autoDeployAndConfigure(
        dadb: Dadb,
        rtspUrl: String = "rtsp://127.0.0.1:8554/live",
        onProgress: (String) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            onProgress("Checking target root access...")
            val rootRes = dadb.shell("su -c id 2>/dev/null").output
            if (!rootRes.contains("uid=0(root)")) {
                return@withContext Result.failure(Exception("Target device does not have root access (su)"))
            }

            // 1. Install CamSwap APK if missing
            val pkgRes = dadb.shell("pm path $CAMSWAP_PACKAGE").output
            if (!pkgRes.contains("package:")) {
                onProgress("Downloading CamSwap virtual camera module...")
                val apkFile = downloadCamSwapApk(onProgress)

                onProgress("Pushing and installing CamSwap to target device...")
                dadb.push(apkFile, "/data/local/tmp/camswap.apk")
                val installRes = dadb.shell("pm install -r /data/local/tmp/camswap.apk ; rm -f /data/local/tmp/camswap.apk").output
                if (!installRes.contains("Success")) {
                    return@withContext Result.failure(Exception("Failed installing CamSwap: $installRes"))
                }
            }

            // 2. Grant permissions
            onProgress("Granting permissions...")
            dadb.shell("pm grant $CAMSWAP_PACKAGE android.permission.READ_MEDIA_VIDEO 2>/dev/null")
            dadb.shell("pm grant $CAMSWAP_PACKAGE android.permission.READ_EXTERNAL_STORAGE 2>/dev/null")

            // 3. Enable module in LSPosed
            onProgress("Enabling CamSwap in LSPosed...")
            dadb.shell("su -c '$LSP_CLI modules enable $CAMSWAP_PACKAGE 2>/dev/null'")

            // 4. Automatically scope ALL user apps by default
            onProgress("Scoping all installed user apps by default...")
            val scopeCmd = "su -c 'apps=$(pm list packages -3 | cut -d: -f2 | grep -v $CAMSWAP_PACKAGE | sed \"s/$/\\/0/\" | tr \"\\n\" \" \"); $LSP_CLI scope add $CAMSWAP_PACKAGE \$apps'"
            dadb.shell(scopeCmd)

            // 5. Push CamSwap cs_config.json configuration
            onProgress("Configuring stream URL in CamSwap...")
            val configJson = """
            {
              "injection_mode": "lsposed",
              "disable_module": false,
              "play_video_sound": true,
              "force_private_dir": false,
              "disable_toast": false,
              "enable_random_play": false,
              "enable_mic_hook": true,
              "mic_hook_mode": "mute",
              "enable_photo_fake": true,
              "notification_control_enabled": false,
              "overlay_control_enabled": false,
              "media_source_type": "stream",
              "stream_url": "$rtspUrl",
              "stream_auto_reconnect": true,
              "stream_enable_local_fallback": false,
              "stream_transport_hint": "tcp",
              "stream_timeout_ms": 5000
            }
            """.trimIndent().replace("\n", "").replace("\"", "\\\"")

            dadb.shell("su -c 'mkdir -p /sdcard/DCIM/Camera1 /sdcard/DCIM/Camera'")
            dadb.shell("su -c 'cat << \"EOF\" > /sdcard/DCIM/Camera1/cs_config.json\n{\n  \"injection_mode\": \"lsposed\",\n  \"disable_module\": false,\n  \"play_video_sound\": true,\n  \"force_private_dir\": false,\n  \"disable_toast\": false,\n  \"enable_random_play\": false,\n  \"enable_mic_hook\": true,\n  \"mic_hook_mode\": \"mute\",\n  \"enable_photo_fake\": true,\n  \"notification_control_enabled\": false,\n  \"overlay_control_enabled\": false,\n  \"media_source_type\": \"stream\",\n  \"stream_url\": \"$rtspUrl\",\n  \"stream_auto_reconnect\": true,\n  \"stream_enable_local_fallback\": false,\n  \"stream_transport_hint\": \"tcp\",\n  \"stream_timeout_ms\": 5000\n}\nEOF\nchmod 666 /sdcard/DCIM/Camera1/cs_config.json'")

            // Broadcast config update to active hooks
            dadb.shell("am broadcast -a io.github.zensu357.camswap.ACTION_UPDATE_CONFIG --es config_json \"$configJson\" 2>/dev/null")

            // Copy to clipboard as helper
            dadb.shell("service call clipboard 2 s16 '$rtspUrl' || cmd clipboard set '$rtspUrl'")

            // Refresh status
            checkTargetStatus(dadb)
            onProgress("Target virtual camera ready!")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Auto-deploy failed", e)
            _status.update { it.copy(error = e.message) }
            Result.failure(e)
        }
    }

    suspend fun restartTargetApps(dadb: Dadb, onProgress: (String) -> Unit = {}): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            onProgress("Restarting scoped target apps...")
            val killCmd = "su -c 'for p in $(pm list packages -3 | cut -d: -f2); do am force-stop \$p; done'"
            dadb.shell(killCmd)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed stopping target apps", e)
            Result.failure(e)
        }
    }

    suspend fun rebootTarget(dadb: Dadb, soft: Boolean = true): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (soft) {
                dadb.shell("su -c setprop ctl.restart zygote")
            } else {
                dadb.shell("su -c reboot")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed rebooting target", e)
            Result.failure(e)
        }
    }

    private fun downloadCamSwapApk(onProgress: (String) -> Unit): File {
        val cacheFile = File(context.cacheDir, "camswap-arm64-v8a.apk")
        if (cacheFile.exists() && cacheFile.length() > 5_000_000) {
            return cacheFile
        }

        val url = URL(CAMSWAP_DOWNLOAD_URL)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
        }
        val totalBytes = connection.contentLengthLong

        connection.inputStream.use { input ->
            FileOutputStream(cacheFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var downloaded = 0L
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloaded += bytesRead
                    if (totalBytes > 0) {
                        val pct = ((downloaded * 100) / totalBytes).toInt()
                        onProgress("Downloading CamSwap ($pct%)...")
                    }
                }
            }
        }
        return cacheFile
    }
}
