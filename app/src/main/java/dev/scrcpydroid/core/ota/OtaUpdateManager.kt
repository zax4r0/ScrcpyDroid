package dev.scrcpydroid.core.ota

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.scrcpydroid.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class AppReleaseInfo(
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val isNewer: Boolean,
    val isPrerelease: Boolean = false
)

sealed interface UpdateCheckResult {
    data object Idle : UpdateCheckResult
    data object Checking : UpdateCheckResult
    data class Available(val info: AppReleaseInfo) : UpdateCheckResult
    data class UpToDate(val currentVersion: String) : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

sealed interface DownloadProgress {
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : DownloadProgress
    data class Completed(val file: File) : DownloadProgress
    data class Failed(val error: String) : DownloadProgress
}

@Singleton
class OtaUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "OtaUpdateManager"
        const val DEFAULT_REPO = "zax4r0/ScrcpyDroid"
    }

    val currentVersion: String
        get() = try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            pInfo.versionName ?: BuildConfig.VERSION_NAME
        } catch (_: Exception) {
            BuildConfig.VERSION_NAME
        }

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkForUpdates(repoOrUrl: String? = null, includePrereleases: Boolean = false): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val endpoint = when {
                !repoOrUrl.isNullOrBlank() && (repoOrUrl.startsWith("http://") || repoOrUrl.startsWith("https://")) -> repoOrUrl
                includePrereleases -> {
                    val repo = if (repoOrUrl.isNullOrBlank()) DEFAULT_REPO else repoOrUrl
                    "https://api.github.com/repos/$repo/releases"
                }
                else -> {
                    val repo = if (repoOrUrl.isNullOrBlank()) DEFAULT_REPO else repoOrUrl
                    "https://api.github.com/repos/$repo/releases/latest"
                }
            }
            val url = URL(endpoint)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "ScrcpyDroid-App")
            }

            if (connection.responseCode != 200) {
                return@withContext UpdateCheckResult.Error("Server returned code ${connection.responseCode}")
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val parsedJson = json.parseToJsonElement(body)

            val targetRelease = if (parsedJson is kotlinx.serialization.json.JsonArray) {
                parsedJson.mapNotNull { it.jsonObject }.firstOrNull { releaseObj ->
                    val hasApk = releaseObj["assets"]?.jsonArray?.any {
                        it.jsonObject["name"]?.jsonPrimitive?.content?.endsWith(".apk", ignoreCase = true) == true
                    } ?: false
                    hasApk
                } ?: return@withContext UpdateCheckResult.UpToDate(currentVersion)
            } else {
                parsedJson.jsonObject
            }

            val tagName = targetRelease["tag_name"]?.jsonPrimitive?.content ?: "1.0.0"
            val cleanVersion = tagName.trimStart('v', 'V')
            val bodyNotes = targetRelease["body"]?.jsonPrimitive?.content ?: "Bug fixes and improvements"
            val isPrerelease = targetRelease["prerelease"]?.jsonPrimitive?.booleanOrNull ?: false

            var apkUrl: String? = null
            targetRelease["assets"]?.jsonArray?.forEach { assetElement ->
                val assetObj = assetElement.jsonObject
                val name = assetObj["name"]?.jsonPrimitive?.content ?: ""
                val browserUrl = assetObj["browser_download_url"]?.jsonPrimitive?.content
                if (name.endsWith(".apk", ignoreCase = true) && browserUrl != null) {
                    apkUrl = browserUrl
                }
            }

            val isNewer = isVersionNewer(cleanVersion, currentVersion)
            val finalUrl = apkUrl ?: ""

            val info = AppReleaseInfo(
                versionName = cleanVersion,
                releaseNotes = bodyNotes,
                downloadUrl = finalUrl,
                isNewer = isNewer,
                isPrerelease = isPrerelease
            )

            if ((isNewer || (includePrereleases && cleanVersion != currentVersion)) && finalUrl.isNotBlank()) {
                UpdateCheckResult.Available(info)
            } else {
                UpdateCheckResult.UpToDate(currentVersion)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Update check failed: ${e.message}", e)
            UpdateCheckResult.Error(e.message ?: "Failed to check for updates")
        }
    }

    private fun isVersionNewer(remote: String, current: String): Boolean {
        try {
            val remoteParts = remote.split('.', '-').mapNotNull { it.toIntOrNull() }
            val currentParts = current.split('.', '-').mapNotNull { it.toIntOrNull() }

            val maxLen = maxOf(remoteParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
        } catch (_: Exception) {}
        return false
    }

    fun downloadApk(downloadUrl: String): Flow<DownloadProgress> = flow {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val targetFile = File(updatesDir, "ScrcpyDroid-update.apk")
        if (targetFile.exists()) targetFile.delete()

        try {
            val url = URL(downloadUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
                instanceFollowRedirects = true
            }

            val totalBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connection.contentLengthLong
            } else {
                connection.contentLength.toLong()
            }

            var downloadedBytes = 0L
            connection.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
                        emit(DownloadProgress.Downloading(progress, downloadedBytes, totalBytes))
                    }
                }
            }

            emit(DownloadProgress.Completed(targetFile))
        } catch (e: Exception) {
            Log.e(TAG, "Download error: ${e.message}", e)
            emit(DownloadProgress.Failed(e.message ?: "Download failed"))
        }
    }.flowOn(Dispatchers.IO)

    fun installApk(file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Install failed: ${e.message}", e)
        }
    }
}
