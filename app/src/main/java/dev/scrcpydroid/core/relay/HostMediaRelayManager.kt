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
    private var reverseStream: AutoCloseable? = null

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
                onRequestKeyFrame = {
                    cameraStreamer?.requestKeyFrame()
                },
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
        try {
            reverseStream?.close()
        } catch (_: Exception) {}
        reverseStream = null

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
                reverseStream?.close()
                // Keep reverse forwarding stream open for the entire duration of the relay session
                val stream = dadb.open("reverse:forward:tcp:$RTSP_PORT;tcp:$RTSP_PORT")
                reverseStream = stream
                Log.i(TAG, "ADB reverse port forwarding held open for port $RTSP_PORT")
            } catch (e: Exception) {
                Log.w(TAG, "ADB reverse via stream failed: ${e.message}")
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
                // Save to CamSwap official config file
                dadb.shell("su -c 'mkdir -p /sdcard/DCIM/Camera1 /sdcard/DCIM/Camera'")
                val cfg = "{\\\"injection_mode\\\":\\\"lsposed\\\",\\\"disable_module\\\":false,\\\"play_video_sound\\\":true,\\\"force_private_dir\\\":false,\\\"disable_toast\\\":false,\\\"enable_random_play\\\":false,\\\"enable_mic_hook\\\":true,\\\"mic_hook_mode\\\":\\\"mute\\\",\\\"enable_photo_fake\\\":true,\\\"notification_control_enabled\\\":false,\\\"overlay_control_enabled\\\":false,\\\"media_source_type\\\":\\\"stream\\\",\\\"stream_url\\\":\\\"$url\\\",\\\"stream_auto_reconnect\\\":true,\\\"stream_enable_local_fallback\\\":false,\\\"stream_transport_hint\\\":\\\"tcp\\\",\\\"stream_timeout_ms\\\":5000}"
                dadb.shell("su -c 'echo \"$cfg\" > /sdcard/DCIM/Camera1/cs_config.json && chmod 666 /sdcard/DCIM/Camera1/cs_config.json'")

                // Broadcast config reload to CamSwap and all hooked apps
                dadb.shell("am broadcast -a io.github.zensu357.camswap.ACTION_UPDATE_CONFIG --es config_json \"$cfg\" 2>/dev/null")

                // Copy to remote clipboard via Android service
                dadb.shell("service call clipboard 2 s16 '$url' || cmd clipboard set '$url'")
                Log.i(TAG, "CamSwap stream config pushed to target device")
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
