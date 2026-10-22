package dev.scrcpydroid.core.scrcpy

import okio.BufferedSource

data class DeviceInfo(
    val name: String,
    val codec: VideoCodec,
    val width: Int,
    val height: Int
)

data class VideoPacket(
    val pts: Long,
    val isConfig: Boolean,
    val isKeyFrame: Boolean,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VideoPacket

        if (pts != other.pts) return false
        if (isConfig != other.isConfig) return false
        if (isKeyFrame != other.isKeyFrame) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = pts.hashCode()
        result = 31 * result + isConfig.hashCode()
        result = 31 * result + isKeyFrame.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

class VideoPacketParser(
    private val source: BufferedSource
) {
    companion object {
        private const val CONFIG_FLAG = 1L shl 63
        private const val KEY_FRAME_FLAG = 1L shl 62
        private const val PTS_MASK = (1L shl 62) - 1L

        private const val CODEC_ID_H264 = 0x68323634 // "h264"
        private const val CODEC_ID_H265 = 0x68323635 // "h265"
    }

    fun readHandshake(): DeviceInfo {
        // 1. Dummy byte (1B)
        source.readByte()

        // 2. Device meta (64B null-terminated string)
        val deviceMetaBytes = source.readByteArray(64)
        val zeroIndex = deviceMetaBytes.indexOf(0.toByte())
        val nameLength = if (zeroIndex >= 0) zeroIndex else 64
        val name = String(deviceMetaBytes, 0, nameLength, Charsets.UTF_8).trim()

        // 3. Codec ID (4B BE)
        val codecId = source.readInt()
        val codec = when (codecId) {
            CODEC_ID_H264 -> VideoCodec.H264
            CODEC_ID_H265 -> VideoCodec.H265
            else -> VideoCodec.H264
        }

        // 4. Initial video width (4B BE)
        val width = source.readInt()

        // 5. Initial video height (4B BE)
        val height = source.readInt()

        return DeviceInfo(
            name = name,
            codec = codec,
            width = width,
            height = height
        )
    }

    fun readPacket(): VideoPacket {
        val ptsAndFlags = source.readLong()
        val packetSize = source.readInt()

        val isConfig = (ptsAndFlags and CONFIG_FLAG) != 0L
        val isKeyFrame = (ptsAndFlags and KEY_FRAME_FLAG) != 0L
        val pts = ptsAndFlags and PTS_MASK

        val data = source.readByteArray(packetSize.toLong())

        return VideoPacket(
            pts = pts,
            isConfig = isConfig,
            isKeyFrame = isKeyFrame,
            data = data
        )
    }
}
