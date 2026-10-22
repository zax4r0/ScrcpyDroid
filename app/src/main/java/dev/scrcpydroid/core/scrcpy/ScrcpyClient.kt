package dev.scrcpydroid.core.scrcpy

import android.content.Context
import dadb.AdbStream
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.scrcpydroid.core.adb.AdbConnectionManager
import dev.scrcpydroid.core.adb.AdbKeyManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ScrcpyState {
    data object Disconnected : ScrcpyState
    data object Connecting : ScrcpyState
    data object ServerStarting : ScrcpyState
    data class Connected(val deviceInfo: DeviceInfo) : ScrcpyState
    data object Streaming : ScrcpyState
    data class Error(val message: String) : ScrcpyState
}

@Singleton
class ScrcpyClient @Inject constructor(
    private val adbConnectionManager: AdbConnectionManager,
    private val adbKeyManager: AdbKeyManager,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ScrcpyClient"
    }

    private val clientScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _state = MutableStateFlow<ScrcpyState>(ScrcpyState.Disconnected)
    val state: StateFlow<ScrcpyState> = _state.asStateFlow()

    private var serverStream: AdbStream? = null
    private var videoStream: AdbStream? = null
    private var controlStream: AdbStream? = null

    private var videoParser: VideoPacketParser? = null
    private var controlWriter: ControlMessageWriter? = null
    private var deviceInfo: DeviceInfo? = null

    suspend fun connect(
        host: String,
        port: Int,
        options: ScrcpyOptions = ScrcpyOptions()
    ) = withContext(Dispatchers.IO) {
        try {
            disconnectInternal()
            _state.value = ScrcpyState.Connecting

            // 2. Connect ADB
            val adbResult = adbConnectionManager.connect(host, port)
            if (adbResult.isFailure) {
                throw adbResult.exceptionOrNull() ?: IOException("Failed to connect ADB to $host:$port")
            }

            // 3. Push scrcpy-server.jar from assets
            context.assets.open("scrcpy-server.jar").use { inputStream ->
                adbConnectionManager.pushFile(inputStream, ScrcpyOptions.SERVER_PATH)
            }

            // 4. Update state to ServerStarting
            _state.value = ScrcpyState.ServerStarting

            // 5. Launch server via shell
            val serverCmd = "CLASSPATH=${ScrcpyOptions.SERVER_PATH} app_process / com.genymobile.scrcpy.Server ${options.toServerArgs()}"
            android.util.Log.d(TAG, "Launching scrcpy-server: $serverCmd")
            serverStream = adbConnectionManager.executeShell(serverCmd)

            // 6. Connect video stream with retry while server initializes
            var vStream: AdbStream? = null
            var lastError: Exception? = null
            for (attempt in 1..10) {
                delay(200)
                try {
                    vStream = adbConnectionManager.openStream("localabstract:scrcpy")
                    android.util.Log.d(TAG, "Video stream opened on attempt $attempt")
                    break
                } catch (e: Exception) {
                    lastError = e
                }
            }
            if (vStream == null) {
                throw lastError ?: IOException("Failed to open video stream to scrcpy server")
            }
            videoStream = vStream

            // 7. Open control stream BEFORE reading handshake
            // scrcpy-server blocks in DesktopConnection.open() until all requested sockets (video + control) are accepted!
            if (options.controlEnabled) {
                android.util.Log.d(TAG, "Opening control stream...")
                val cStream = adbConnectionManager.openStream("localabstract:scrcpy")
                controlStream = cStream
                controlWriter = ControlMessageWriter(cStream.sink)
                android.util.Log.d(TAG, "Control stream opened successfully")
            } else {
                controlStream = null
                controlWriter = null
            }

            // 8. Now read handshake from video stream (sent by server once all connections are accepted)
            android.util.Log.d(TAG, "Reading scrcpy handshake...")
            val parser = VideoPacketParser(vStream.source)
            videoParser = parser
            val info = parser.readHandshake()
            deviceInfo = info
            android.util.Log.d(TAG, "Handshake received: $info")

            // 9. Update state to Connected with device info
            _state.value = ScrcpyState.Connected(info)
        } catch (e: Exception) {
            val errorMessage = e.message ?: "Scrcpy connection failed"
            android.util.Log.e(TAG, "Scrcpy connect error: $errorMessage", e)
            _state.value = ScrcpyState.Error(errorMessage)
            disconnectInternal()
            throw e
        }
    }

    fun getVideoPacketFlow(): Flow<VideoPacket> = flow {
        val parser = videoParser ?: throw IllegalStateException("Video stream is not initialized")
        _state.value = ScrcpyState.Streaming
        try {
            while (currentCoroutineContext().isActive) {
                val packet = parser.readPacket()
                emit(packet)
            }
        } catch (e: Exception) {
            if (e !is CancellationException) {
                _state.value = ScrcpyState.Error(e.message ?: "Video stream error")
            }
            throw e
        }
    }.flowOn(Dispatchers.IO)

    fun getControlWriter(): ControlMessageWriter? = controlWriter

    fun getDeviceInfo(): DeviceInfo? = deviceInfo

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        disconnectInternal()
        _state.value = ScrcpyState.Disconnected
    }

    private suspend fun disconnectInternal() {
        try {
            controlStream?.close()
        } catch (_: Exception) {}
        controlStream = null
        controlWriter = null

        try {
            videoStream?.close()
        } catch (_: Exception) {}
        videoStream = null
        videoParser = null

        try {
            serverStream?.close()
        } catch (_: Exception) {}
        serverStream = null

        try {
            adbConnectionManager.disconnect()
        } catch (_: Exception) {}

        deviceInfo = null
    }
}
