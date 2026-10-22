package dev.scrcpydroid.core.scrcpy

data class ScrcpyOptions(
    val maxSize: Int = 1920,
    val videoBitRate: Int = 8_000_000,
    val maxFps: Int = 60,
    val videoCodec: VideoCodec = VideoCodec.H264,
    val audioEnabled: Boolean = true,
    val audioCodec: String = "raw",
    val audioDup: Boolean = false,
    val controlEnabled: Boolean = true,
    val stayAwake: Boolean = true,
    val showTouches: Boolean = false,
    val powerOffOnClose: Boolean = false
) {
    companion object {
        const val SCRCPY_VERSION = "3.1"
        const val SERVER_PATH = "/data/local/tmp/scrcpy-server.jar"
    }

    fun toServerArgs(): String = buildString {
        append(SCRCPY_VERSION)
        append(" tunnel_forward=true")
        append(" video=true")
        append(" audio=$audioEnabled")
        if (audioEnabled) {
            append(" audio_codec=$audioCodec")
            append(" audio_dup=$audioDup")
        }
        append(" control=$controlEnabled")
        append(" cleanup=false")
        append(" max_size=$maxSize")
        append(" video_bit_rate=$videoBitRate")
        append(" max_fps=$maxFps")
        append(" video_codec=${videoCodec.id}")
        append(" send_dummy_byte=true")
        append(" send_device_meta=true")
        append(" send_frame_meta=true")
        append(" send_codec_meta=true")
        append(" stay_awake=$stayAwake")
        append(" show_touches=$showTouches")
        append(" power_off_on_close=$powerOffOnClose")
        append(" scid=-1")
    }
}

enum class VideoCodec(val id: String, val mimeType: String) {
    H264("h264", "video/avc"),
    H265("h265", "video/hevc")
}
