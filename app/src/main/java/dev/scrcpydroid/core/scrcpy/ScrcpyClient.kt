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
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.BufferedSource
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

import dev.scrcpydroid.core.audio.AudioPlayer

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
    private val audioPlayer: AudioPlayer,
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
    private var audioStream: AdbStream? = null
    private var controlStream: AdbStream? = null

    private var videoJob: Job? = null
    private val _videoPacketFlow = MutableSharedFlow<VideoPacket>(
        replay = 0,
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    @Volatile
    private var lastConfigPacket: VideoPacket? = null

    private var controlReaderJob: Job? = null
    private val _remoteClipboardFlow = MutableSharedFlow<String>(
        replay = 1,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val remoteClipboardFlow: Flow<String> = _remoteClipboardFlow.asSharedFlow()

    private var videoParser: VideoPacketParser? = null
    private var controlWriter: ControlMessageWriter? = null
    private var deviceInfo: DeviceInfo? = null
    private var lastOptions: ScrcpyOptions? = null

    suspend fun connect(
        host: String,
        port: Int,
        options: ScrcpyOptions = ScrcpyOptions()
    ) = withContext(Dispatchers.IO) {
        try {
            lastOptions = options
            disconnectInternal()
            _state.value = ScrcpyState.Connecting

            // 2. Connect ADB
            val adbResult = adbConnectionManager.connect(host, port)
            val dadb = adbResult.getOrThrow()

            // Disable auto-rotate on controlled device so it follows controller device exclusively
            try {
                dadb.shell("settings put system accelerometer_rotation 0")
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Failed disabling accelerometer_rotation: ${e.message}")
            }

            // Auto-wake & auto-unlock target device if enabled
            if (options.autoUnlockOnConnect) {
                unlockDevice(options.devicePassword)
            } else if (options.autoWakeOnConnect) {
                try {
                    val lockState = getDeviceLockState()
                    if (!lockState.isScreenAwake) {
                        dadb.shell("input keyevent 224")
                        android.util.Log.d(TAG, "Woke device screen on connect")
                    }
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Failed autoWake on connect: ${e.message}")
                }
            }

            // 3. Push scrcpy-server.jar from assets
            context.assets.open("scrcpy-server.jar").use { inputStream ->
                adbConnectionManager.pushFile(inputStream, ScrcpyOptions.SERVER_PATH)
            }

            // 4. Update state to ServerStarting
            _state.value = ScrcpyState.ServerStarting

            // 5. Build post-exit cleanup chain for server termination (even if disconnect is accidental or network loss)
            val cleanupList = mutableListOf<String>()
            if (options.dndOnDisconnect) {
                cleanupList.add("cmd notification set_dnd none")
                cleanupList.add("for s in 0 1 2 3 4 5; do cmd media_session volume --stream \$s --set 0; done")
            }
            if (options.lockOnDisconnect) {
                cleanupList.add("input keyevent 223")
            }
            val cleanupChain = if (cleanupList.isNotEmpty()) {
                " ; ( " + cleanupList.joinToString(" ; ") + " )"
            } else ""

            // Launch server via shell
            val serverCmd = "CLASSPATH=${ScrcpyOptions.SERVER_PATH} app_process / com.genymobile.scrcpy.Server ${options.toServerArgs()}$cleanupChain"
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

            // 7. Connect audio stream (if audioEnabled)
            if (options.audioEnabled) {
                try {
                    android.util.Log.d(TAG, "Opening audio stream to localabstract:scrcpy")
                    val aStream = adbConnectionManager.openStream("localabstract:scrcpy")
                    audioStream = aStream
                    audioPlayer.start(aStream.source, clientScope)
                    android.util.Log.d(TAG, "Audio stream opened and AudioPlayer started")
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Could not open audio stream: ${e.message}")
                    audioStream = null
                }
            } else {
                audioStream = null
            }

            // 8. Open control stream BEFORE reading handshake
            // scrcpy-server blocks in DesktopConnection.open() until all requested sockets (video, audio, control) are accepted!
            if (options.controlEnabled) {
                android.util.Log.d(TAG, "Opening control stream...")
                val cStream = adbConnectionManager.openStream("localabstract:scrcpy")
                controlStream = cStream
                controlWriter = ControlMessageWriter(cStream.sink)
                startControlReader(cStream.source)
                android.util.Log.d(TAG, "Control stream opened and control reader started")

                if (options.turnScreenOffOnConnect) {
                    clientScope.launch {
                        delay(400)
                        try {
                            controlWriter?.setDisplayPower(false)
                            android.util.Log.d(TAG, "turnScreenOffOnConnect: turned display power off")
                        } catch (e: Exception) {
                            android.util.Log.w(TAG, "turnScreenOffOnConnect failed: ${e.message}")
                        }
                    }
                }
            } else {
                controlStream = null
                controlWriter = null
            }

            // 9. Now read handshake from video stream (sent by server once all connections are accepted)
            android.util.Log.d(TAG, "Reading scrcpy handshake...")
            val parser = VideoPacketParser(vStream.source)
            videoParser = parser
            val info = parser.readHandshake()
            deviceInfo = info
            android.util.Log.d(TAG, "Handshake received: $info")

            // 10. Start background persistent video reader
            startVideoReader(parser)

            // 11. Update state to Connected with device info
            _state.value = ScrcpyState.Connected(info)
        } catch (e: Exception) {
            val errorMessage = e.message ?: "Scrcpy connection failed"
            android.util.Log.e(TAG, "Scrcpy connect error: $errorMessage", e)
            _state.value = ScrcpyState.Error(errorMessage)
            disconnectInternal()
            throw e
        }
    }

    private fun startVideoReader(parser: VideoPacketParser) {
        videoJob?.cancel()
        videoJob = clientScope.launch(Dispatchers.IO) {
            _state.value = ScrcpyState.Streaming
            try {
                while (isActive) {
                    val packet = parser.readPacket()
                    if (packet.isConfig) {
                        lastConfigPacket = packet
                    }
                    _videoPacketFlow.emit(packet)
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    android.util.Log.e(TAG, "Video reader error: ${e.message}", e)
                    _state.value = ScrcpyState.Error(e.message ?: "Video stream error")
                }
            }
        }
    }

    private fun startControlReader(source: BufferedSource) {
        controlReaderJob?.cancel()
        controlReaderJob = clientScope.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    val type = source.readByte().toInt() and 0xFF
                    when (type) {
                        0 -> { // TYPE_CLIPBOARD from remote device
                            val length = source.readInt()
                            if (length in 1..262144) {
                                val textBytes = source.readByteArray(length.toLong())
                                val text = String(textBytes, Charsets.UTF_8)
                                android.util.Log.d(TAG, "Received remote clipboard (${text.length} chars)")
                                _remoteClipboardFlow.emit(text)
                            } else if (length > 262144) {
                                source.skip(length.toLong())
                            }
                        }
                        1 -> { // TYPE_ACK_CLIPBOARD
                            val sequence = source.readLong()
                        }
                        2 -> { // TYPE_UHID_OUTPUT
                            val id = source.readShort()
                            val length = source.readShort().toInt() and 0xFFFF
                            source.skip(length.toLong())
                        }
                        else -> {
                            android.util.Log.w(TAG, "Unknown control message type received: $type")
                        }
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    android.util.Log.d(TAG, "Control reader loop ended: ${e.message}")
                }
            }
        }
    }

    fun getVideoPacketFlow(): Flow<VideoPacket> = _videoPacketFlow.asSharedFlow()

    fun getLastConfigPacket(): VideoPacket? = lastConfigPacket

    fun getControlWriter(): ControlMessageWriter? = controlWriter

    fun getDeviceInfo(): DeviceInfo? = deviceInfo

    fun getDeviceId(): String = adbConnectionManager.deviceId

    fun getLastOptions(): ScrcpyOptions? = lastOptions

    data class DeviceLockState(
        val isScreenAwake: Boolean,
        val isKeyguardShowing: Boolean,
        val isDeviceLocked: Boolean
    ) {
        val isUnlocked: Boolean
            get() = isScreenAwake && !isKeyguardShowing && !isDeviceLocked
    }

    suspend fun getDeviceLockState(): DeviceLockState = withContext(Dispatchers.IO) {
        val dadb = adbConnectionManager.currentDadb
            ?: return@withContext DeviceLockState(isScreenAwake = true, isKeyguardShowing = false, isDeviceLocked = false)

        try {
            val cmd = "echo \"WAKE=\$(dumpsys power 2>/dev/null | grep -E 'mWakefulness=' | head -n 1)\" ; " +
                      "echo \"KG=\$(dumpsys window policy 2>/dev/null | grep -E 'isKeyguardShowing|isKeyguardLocked' | head -n 1 || dumpsys window 2>/dev/null | grep -E 'mKeyguardShowing|isKeyguardShowing' | head -n 1)\" ; " +
                      "echo \"TRUST=\$(dumpsys trust 2>/dev/null | grep -iE 'deviceLocked=' | head -n 1)\""

            val response = dadb.shell(cmd).output
            var isAwake = false
            var isKeyguardShowing = false
            var isDeviceLocked = false

            for (line in response.lines()) {
                val lower = line.trim().lowercase()
                when {
                    lower.startsWith("wake=") -> {
                        isAwake = lower.contains("awake")
                    }
                    lower.startsWith("kg=") -> {
                        if (lower.contains("keyguardshowing=true") || lower.contains("iskeyguardlocked=true") || lower.contains("mshowinglockscreen=true")) {
                            isKeyguardShowing = true
                        } else if (lower.contains("keyguardshowing=false") || lower.contains("iskeyguardlocked=false")) {
                            isKeyguardShowing = false
                        }
                    }
                    lower.startsWith("trust=") -> {
                        if (lower.contains("devicelocked=true")) {
                            isDeviceLocked = true
                        } else if (lower.contains("devicelocked=false")) {
                            isDeviceLocked = false
                        }
                    }
                }
            }

            DeviceLockState(
                isScreenAwake = isAwake,
                isKeyguardShowing = isKeyguardShowing,
                isDeviceLocked = isDeviceLocked
            )
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed reading lock state: ${e.message}")
            DeviceLockState(isScreenAwake = true, isKeyguardShowing = false, isDeviceLocked = false)
        }
    }

    suspend fun unlockDevice(password: String? = null) = withContext(Dispatchers.IO) {
        val dadb = adbConnectionManager.currentDadb ?: return@withContext
        val pwd = password?.trim() ?: lastOptions?.devicePassword?.trim()

        try {
            // Step 1: Check if already unlocked & awake
            var state = getDeviceLockState()
            android.util.Log.d(TAG, "Check initial lock state: $state")

            if (state.isUnlocked) {
                android.util.Log.i(TAG, "Device is already awake and unlocked. Skipping unlock touches.")
                return@withContext
            }

            // Step 2: Wake screen if asleep
            if (!state.isScreenAwake) {
                dadb.shell("input keyevent 224")
                delay(200)
                state = getDeviceLockState()
                if (state.isUnlocked) {
                    android.util.Log.i(TAG, "Device woke up unlocked. Stopping.")
                    return@withContext
                }
            }

            // Step 3: Dismiss keyguard gently without touches
            dadb.shell("wm dismiss-keyguard ; input keyevent 82")
            delay(150)
            state = getDeviceLockState()
            if (state.isUnlocked) {
                android.util.Log.i(TAG, "Device unlocked via dismiss-keyguard. Stopping.")
                return@withContext
            }

            // Step 4: Swipe up to bring up bouncer or dismiss swipe lock
            dadb.shell("input swipe 500 1600 500 600 150")
            delay(250)
            state = getDeviceLockState()
            if (state.isUnlocked) {
                android.util.Log.i(TAG, "Device unlocked via swipe up. Stopping.")
                return@withContext
            }

            // Step 5: If password configured and still locked, submit credentials
            if (!pwd.isNullOrEmpty()) {
                val escaped = pwd.replace(" ", "%s").replace("'", "'\\''")
                android.util.Log.d(TAG, "Device still locked, entering password...")
                dadb.shell("input text '$escaped' ; sleep 0.15 ; input keyevent 66")
                delay(300)
                state = getDeviceLockState()
                if (state.isUnlocked) {
                    android.util.Log.i(TAG, "Device unlocked via password input.")
                } else {
                    android.util.Log.d(TAG, "Password submitted, current state: $state")
                }
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed unlockDevice: ${e.message}")
        }
    }

    suspend fun setRemoteRotation(rotation: Int) = withContext(Dispatchers.IO) {
        val dadb = adbConnectionManager.currentDadb ?: return@withContext
        try {
            // Disable remote auto-rotate and lock to identical orientation
            dadb.shell("settings put system accelerometer_rotation 0 ; settings put system user_rotation $rotation")
            controlWriter?.resetVideo()
            android.util.Log.d(TAG, "Synced remote rotation to $rotation")
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed setRemoteRotation: ${e.message}")
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        disconnectInternal()
        _state.value = ScrcpyState.Disconnected
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private suspend fun disconnectInternal() {
        try {
            val opts = lastOptions
            val cmds = mutableListOf<String>()
            cmds.add("settings put system accelerometer_rotation 1")
            if (opts != null && (opts.dndOnDisconnect || opts.lockOnDisconnect)) {
                if (opts.dndOnDisconnect) {
                    cmds.add("cmd notification set_dnd none")
                    cmds.add("for s in 0 1 2 3 4 5; do cmd media_session volume --stream \$s --set 0; done")
                }
                if (opts.lockOnDisconnect) {
                    cmds.add("input keyevent 223")
                }
            }
            if (cmds.isNotEmpty()) {
                adbConnectionManager.currentDadb?.shell(cmds.joinToString(" ; "))
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed running disconnect shell cleanup: ${e.message}")
        }

        try {
            audioPlayer.stop()
        } catch (_: Exception) {}

        try {
            videoJob?.cancel()
        } catch (_: Exception) {}
        videoJob = null
        lastConfigPacket = null
        _videoPacketFlow.resetReplayCache()

        try {
            controlReaderJob?.cancel()
        } catch (_: Exception) {}
        controlReaderJob = null
        _remoteClipboardFlow.resetReplayCache()

        try {
            audioStream?.close()
        } catch (_: Exception) {}
        audioStream = null

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
