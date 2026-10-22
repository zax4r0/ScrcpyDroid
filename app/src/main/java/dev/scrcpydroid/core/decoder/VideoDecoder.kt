package dev.scrcpydroid.core.decoder

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import android.view.Surface
import dev.scrcpydroid.core.scrcpy.VideoPacket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoDecoder @Inject constructor() {

    companion object {
        private const val TAG = "VideoDecoder"
        private const val DEQUEUE_TIMEOUT_US = 10_000L
    }

    private var codec: MediaCodec? = null
    private var decodeJob: Job? = null
    private val isRunning = AtomicBoolean(false)

    fun start(
        surface: Surface,
        codecMime: String,
        width: Int,
        height: Int,
        packetFlow: Flow<VideoPacket>
    ) {
        stop()

        isRunning.set(true)
        decodeJob = CoroutineScope(Dispatchers.Default).launch {
            try {
                runDecoder(surface, codecMime, width, height, packetFlow)
            } catch (e: CancellationException) {
                // Expected when decoding is stopped
            } catch (e: Exception) {
                Log.e(TAG, "Error in video decoding loop", e)
            } finally {
                releaseCodec()
            }
        }
    }

    private suspend fun CoroutineScope.runDecoder(
        surface: Surface,
        codecMime: String,
        width: Int,
        height: Int,
        packetFlow: Flow<VideoPacket>
    ) {
        var mediaCodec: MediaCodec? = null
        var isConfigured = false
        val bufferInfo = MediaCodec.BufferInfo()

        try {
            packetFlow.collect { packet ->
                if (!isRunning.get()) return@collect

                if (!isConfigured) {
                    val format = MediaFormat.createVideoFormat(codecMime, width, height)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
                    }

                    if (packet.isConfig) {
                        setupCodecSpecificData(format, codecMime, packet.data)
                    }

                    val newCodec = MediaCodec.createDecoderByType(codecMime)
                    newCodec.configure(format, surface, null, 0)
                    newCodec.start()
                    mediaCodec = newCodec
                    synchronized(this@VideoDecoder) {
                        codec = newCodec
                    }
                    isConfigured = true

                    // Launch output rendering loop
                    launch(Dispatchers.Default) {
                        drainOutput(newCodec, bufferInfo)
                    }

                    if (packet.isConfig) {
                        return@collect
                    }
                }

                val currentCodec = mediaCodec ?: return@collect
                feedInput(currentCodec, packet)
            }
        } finally {
            isRunning.set(false)
        }
    }

    private suspend fun feedInput(mediaCodec: MediaCodec, packet: VideoPacket) {
        var queued = false
        var attempts = 0
        while (!queued && isRunning.get() && attempts < 10) {
            try {
                val inputIndex = mediaCodec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                if (inputIndex >= 0) {
                    val inputBuffer = mediaCodec.getInputBuffer(inputIndex)
                    if (inputBuffer != null) {
                        inputBuffer.clear()
                        inputBuffer.put(packet.data)
                        val flags = when {
                            packet.isConfig -> MediaCodec.BUFFER_FLAG_CODEC_CONFIG
                            packet.isKeyFrame -> MediaCodec.BUFFER_FLAG_KEY_FRAME
                            else -> 0
                        }
                        mediaCodec.queueInputBuffer(inputIndex, 0, packet.data.size, packet.pts, flags)
                        queued = true
                    }
                } else {
                    attempts++
                    delay(2)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error queuing input buffer", e)
                break
            }
        }
    }

    private fun drainOutput(mediaCodec: MediaCodec, bufferInfo: MediaCodec.BufferInfo) {
        while (isRunning.get()) {
            try {
                val outputIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
                when {
                    outputIndex >= 0 -> {
                        mediaCodec.releaseOutputBuffer(outputIndex, true)
                    }
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        Log.d(TAG, "Decoder output format changed: ${mediaCodec.outputFormat}")
                    }
                    outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        // Timeout, keep draining
                    }
                }
            } catch (e: Exception) {
                if (isRunning.get()) {
                    Log.e(TAG, "Error draining output buffer", e)
                }
                break
            }
        }
    }

    private fun setupCodecSpecificData(format: MediaFormat, mimeType: String, data: ByteArray) {
        if (mimeType.contains("avc", ignoreCase = true) || mimeType.contains("h264", ignoreCase = true)) {
            val spsPps = splitH264Config(data)
            if (spsPps != null) {
                format.setByteBuffer("csd-0", ByteBuffer.wrap(spsPps.first))
                format.setByteBuffer("csd-1", ByteBuffer.wrap(spsPps.second))
            } else {
                format.setByteBuffer("csd-0", ByteBuffer.wrap(data))
            }
        } else {
            // H.265 / HEVC: set entire buffer as csd-0
            format.setByteBuffer("csd-0", ByteBuffer.wrap(data))
        }
    }

    private fun splitH264Config(data: ByteArray): Pair<ByteArray, ByteArray>? {
        val indices = mutableListOf<Int>()
        var i = 0
        while (i <= data.size - 4) {
            if (data[i] == 0.toByte() &&
                data[i + 1] == 0.toByte() &&
                data[i + 2] == 0.toByte() &&
                data[i + 3] == 1.toByte()
            ) {
                indices.add(i)
                i += 4
            } else {
                i++
            }
        }

        if (indices.size >= 2) {
            val sps = data.copyOfRange(indices[0], indices[1])
            val pps = data.copyOfRange(indices[1], data.size)
            return Pair(sps, pps)
        }
        return null
    }

    @Synchronized
    fun stop() {
        isRunning.set(false)
        decodeJob?.cancel()
        decodeJob = null
        releaseCodec()
    }

    @Synchronized
    private fun releaseCodec() {
        try {
            codec?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping codec: ${e.message}")
        }
        try {
            codec?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing codec: ${e.message}")
        }
        codec = null
    }
}
