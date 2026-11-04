package dev.scrcpydroid.core.relay

data class RelayState(
    val isStreaming: Boolean = false,
    val isCameraActive: Boolean = false,
    val isMicActive: Boolean = false,
    val isFrontCamera: Boolean = true,
    val isMicMuted: Boolean = false,
    val localRtspUrl: String = "rtsp://127.0.0.1:8554/live",
    val lanRtspUrl: String? = null,
    val localHttpUrl: String = "http://127.0.0.1:8080/live",
    val lanHttpUrl: String? = null,
    val activeClients: Int = 0,
    val videoFps: Int = 30,
    val statusText: String = "Idle",
    val error: String? = null
)
