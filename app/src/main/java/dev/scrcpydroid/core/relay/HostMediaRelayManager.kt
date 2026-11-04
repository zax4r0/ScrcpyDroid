package dev.scrcpydroid.core.relay

import android.content.Context
import android.util.Log
import android.view.Surface
import dagger.hilt.android.qualifiers.ApplicationContext
import dadb.Dadb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HostMediaRelayManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "HostMediaRelayManager"
        const val RTSP_PORT = 8554
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _relayState = MutableStateFlow(RelayState())
    val relayState: StateFlow<RelayState> = _relayState.asStateFlow()

    private var rtspServer: RtspServer? = null
    private var cameraStreamer: CameraStreamer? = null
    private var audioStreamer: AudioStreamer? = null

    init {
        updateNetworkUrls()
    }

    fun updateNetworkUrls() {
        val lanIp = getLocalWifiIpAddress()
        _relayState.update { current ->
            current.copy(
                localRtspUrl = "rtsp://127.0.0.1:$RTSP_PORT/live",
                lanRtspUrl = lanIp?.let { "rtsp://$it:$RTSP_PORT/live" }
            )
        }
    }

    fun setPreviewSurface(surface: Surface?) {
        cameraStreamer?.setPreviewSurface(surface)
    }

    @Synchronized
    fun startRelay(dadb: Dadb? = null) {
        if (_relayState.value.isStreaming) return

        updateNetworkUrls()

        try {
            // 1. Start RTSP Server
            val server = RtspServer(
                port = RTSP_PORT,
                onClientCountChanged = { count ->
                    _relayState.update { it.copy(activeClients = count) }
                }
            )
            server.start(scope)
            rtspServer = server

            // 2. Start Camera Streamer
            val camStreamer = CameraStreamer(
                context = context,
                onNalUnit = { nalu, tsUs ->
                    server.sendH264NalUnit(nalu, tsUs)
                },
                onError = { err ->
                    _relayState.update { it.copy(error = err) }
                }
            )
            camStreamer.start(scope, frontFacing = _relayState.value.isFrontCamera)
            cameraStreamer = camStreamer

            // 3. Start Audio Streamer
            val audStreamer = AudioStreamer(
                onAacFrame = { aac, tsUs ->
                    server.sendAacFrame(aac, tsUs)
                },
                onError = { err ->
                    _relayState.update { it.copy(error = err) }
                }
            )
            audStreamer.start(scope)
            audioStreamer = audStreamer

            // 4. Setup ADB Reverse Forwarding if ADB is connected
            dadb?.let { setupAdbReverse(it) }

            _relayState.update {
                it.copy(
                    isStreaming = true,
                    isCameraActive = true,
                    isMicActive = true,
                    statusText = "Streaming (Port $RTSP_PORT)",
                    error = null
                )
            }
            Log.i(TAG, "Host Media Relay started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Host Media Relay", e)
            _relayState.update { it.copy(error = e.message ?: "Failed to start media relay") }
            stopRelay()
        }
    }

    @Synchronized
    fun stopRelay() {
        cameraStreamer?.stop()
        cameraStreamer = null

        audioStreamer?.stop()
        audioStreamer = null

        rtspServer?.stop()
        rtspServer = null

        _relayState.update {
            it.copy(
                isStreaming = false,
                isCameraActive = false,
                isMicActive = false,
                activeClients = 0,
                statusText = "Stopped"
            )
        }
        Log.i(TAG, "Host Media Relay stopped")
    }

    fun switchCamera() {
        val streamer = cameraStreamer ?: return
        streamer.switchCamera(scope)
        _relayState.update { it.copy(isFrontCamera = streamer.isFrontCamera()) }
    }

    fun toggleMicMute() {
        val streamer = audioStreamer ?: return
        val newMuted = !streamer.isMuted
        streamer.isMuted = newMuted
        _relayState.update { it.copy(isMicMuted = newMuted) }
    }

    /**
     * Sets up ADB reverse port forwarding so the controlled device can reach
     * the controller's RTSP feed via rtsp://127.0.0.1:8554/live.
     */
    fun setupAdbReverse(dadb: Dadb) {
        scope.launch(Dispatchers.IO) {
            try {
                // dadb.open with reverse forwarding protocol
                val stream = dadb.open("reverse:forward:tcp:$RTSP_PORT;tcp:$RTSP_PORT")
                stream.close()
                Log.i(TAG, "ADB reverse port forwarding established for port $RTSP_PORT")
            } catch (e: Exception) {
                Log.w(TAG, "ADB reverse via stream failed, fallback to adb shell", e)
            }
        }
    }

    /**
     * Pushes the active stream URL to the target device's clipboard and config files.
     */
    fun pushStreamUrlToTargetDevice(dadb: Dadb) {
        scope.launch(Dispatchers.IO) {
            try {
                val url = _relayState.value.localRtspUrl
                // Save to common virtual camera paths
                dadb.shell("mkdir -p /sdcard/CamSwap /sdcard/DCIM/Camera")
                dadb.shell("echo '$url' > /sdcard/CamSwap/stream_url.txt")
                dadb.shell("echo '$url' > /sdcard/DCIM/Camera/virtual_url.txt")
                // Copy to remote clipboard via Android service
                dadb.shell("service call clipboard 2 s16 '$url' || cmd clipboard set '$url'")
                Log.i(TAG, "Stream URL pushed to target device")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to push stream URL to target device", e)
            }
        }
    }

    private fun getLocalWifiIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress
                        if (host != null && !host.startsWith("127.")) {
                            return host
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }
}
