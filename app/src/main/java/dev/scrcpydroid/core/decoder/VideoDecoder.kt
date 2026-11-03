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
        private const val DEQUEUE_TIMEOUT_US = 10_000L // 10ms for reliable non-busy dequeue
    }

    private var codec: MediaCodec? = null
    private var decodeJob: Job? = null
    private val isRunning = AtomicBoolean(false)
    private var lastConfigData: ByteArray? = null

    // Session counter: bumped on every start(). Loops from an older session compare against it
    // so they can never shut down a newer decoder session after a quick stop() -> start().
    @Volatile
    private var generation = 0

    private fun isSessionActive(gen: Int): Boolean = isRunning.get() && generation == gen
    private var currentSurface: Surface? = null

    fun updateSurface(newSurface: Surface): Boolean {
        synchronized(this) {
            val c = codec ?: return false
            if (!isRunning.get()) return false
            return try {
                c.setOutputSurface(newSurface)
                currentSurface = newSurface
                Log.d(TAG, "setOutputSurface succeeded")
                true
            } catch (e: Exception) {
                Log.w(TAG, "setOutputSurface failed, will restart decoder", e)
                false
            }
        }
    }

    fun onSurfaceDestroyed() {
        // The output surface is gone (app backgrounded / view detached). Keeping the codec alive
        // against an abandoned surface leaves it in an error state on many vendor decoders, so
        // release it now and rebuild cleanly from cached SPS/PPS + a fresh IDR when the surface returns.
        stop()
    }

    fun clearConfig() {
        synchronized(this) {
            lastConfigData = null
        }
    }

    fun start(
        surface: Surface,
        codecMime: String,
        width: Int,
        height: Int,
        packetFlow: Flow<VideoPacket>,
        initialConfig: ByteArray? = null
    ) {
        if (initialConfig != null) {
            synchronized(this) {
                lastConfigData = initialConfig
            }
        }

        synchronized(this) {
            if (isRunning.get() && currentSurface === surface && codec != null) {
                Log.d(TAG, "Decoder already running on this surface")
                return
            }
        }

        stop()

        val gen: Int
        synchronized(this) {
            gen = ++generation
            currentSurface = surface
            isRunning.set(true)
        }
        decodeJob = CoroutineScope(Dispatchers.Default).launch {
            try {
                runDecoder(gen, surface, codecMime, width, height, packetFlow, initialConfig)
            } catch (e: CancellationException) {
                // Expected when decoding is stopped
            } catch (e: Exception) {
                Log.e(TAG, "Error in video decoding loop", e)
            }
        }
    }

    private suspend fun CoroutineScope.runDecoder(
        gen: Int,
        surface: Surface,
        codecMime: String,
        width: Int,
        height: Int,
        packetFlow: Flow<VideoPacket>,
        initialConfig: ByteArray? = null
    ) {
        var mediaCodec: MediaCodec? = null
        var isConfigured = false
        val bufferInfo = MediaCodec.BufferInfo()

        try {
            // If cached config is available, configure MediaCodec immediately!
            val cachedConfig = synchronized(this@VideoDecoder) { lastConfigData } ?: initialConfig
            if (cachedConfig != null) {
                val activeSurface = synchronized(this@VideoDecoder) { currentSurface } ?: surface
                val newCodec = createAndConfigureCodec(codecMime, width, height, activeSurface, cachedConfig)
                if (newCodec != null) {
                    mediaCodec = newCodec
                    synchronized(this@VideoDecoder) {
                        codec = newCodec
                    }
                    isConfigured = true

                    launch(Dispatchers.Default) {
                        drainOutput(gen, newCodec, bufferInfo)
                    }
                    Log.d(TAG, "MediaCodec pre-configured with cached SPS/PPS")
                } else {
                    Log.w(TAG, "Pre-configuration failed")
                    isConfigured = false
                }
            }

            var waitingForKeyFrame = true

            packetFlow.collect { packet ->
                if (!isRunning.get()) throw CancellationException("Decoder stopped")

                if (packet.isConfig) {
                    synchronized(this@VideoDecoder) {
                        lastConfigData = packet.data
                    }
                }

                if (!isConfigured) {
                    val configData = if (packet.isConfig) packet.data else (synchronized(this@VideoDecoder) { lastConfigData } ?: initialConfig)
                    val activeSurface = synchronized(this@VideoDecoder) { currentSurface } ?: surface
                    val newCodec = createAndConfigureCodec(codecMime, width, height, activeSurface, configData)
                    if (newCodec != null) {
                        mediaCodec = newCodec
                        synchronized(this@VideoDecoder) {
                            codec = newCodec
                        }
                        isConfigured = true

                        // Launch output rendering loop
                        launch(Dispatchers.Default) {
                            drainOutput(gen, newCodec, bufferInfo)
                        }
                    } else {
                        Log.e(TAG, "Failed to create decoder on packet")
                        return@collect
                    }

                    if (packet.isConfig) {
                        return@collect
                    }
                }

                // A newly started MediaCodec cannot decode P-frames without an IDR reference picture.
                // Drop non-keyframe packets until the first keyframe arrives to prevent buffer lockups.
                if (waitingForKeyFrame) {
                    if (packet.isKeyFrame) {
                        waitingForKeyFrame = false
                        Log.d(TAG, "Keyframe received, starting regular playback")
                    } else if (packet.isConfig) {
                        // SPS/PPS config is always allowed
                    } else {
                        // Drop P-frame while awaiting keyframe
                        return@collect
                    }
                }

                val currentCodec = mediaCodec ?: return@collect
                feedInput(gen, currentCodec, packet)
            }
        } finally {
            synchronized(this@VideoDecoder) {
                if (generation == gen) {
                    isRunning.set(false)
                    if (codec === mediaCodec) {
                        codec = null
                    }
                }
            }
            try { mediaCodec?.stop() } catch (_: Exception) {}
            try { mediaCodec?.release() } catch (_: Exception) {}
        }
    }

    private suspend fun feedInput(gen: Int, mediaCodec: MediaCodec, packet: VideoPacket) {
        var retries = 0
        while (isSessionActive(gen)) {
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
                        break
                    }
                } else {
                    retries++
                    if (retries > 30) {
                        // After ~300ms of saturated buffers, drop this packet so we don't stall the stream flow
                        Log.w(TAG, "Input buffers saturated, dropping frame to avoid stall")
                        break
                    }
                    delay(5)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error queuing input buffer", e)
                break
            }
        }
    }

    private fun drainOutput(gen: Int, mediaCodec: MediaCodec, bufferInfo: MediaCodec.BufferInfo) {
        while (isSessionActive(gen)) {
            try {
                var outputIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
                while (outputIndex >= 0 && isSessionActive(gen)) {
                    val surface = synchronized(this) { currentSurface }
                    if (surface != null && surface.isValid) {
                        try {
                            mediaCodec.releaseOutputBuffer(outputIndex, true)
                        } catch (e: Exception) {
                            try { mediaCodec.releaseOutputBuffer(outputIndex, false) } catch (_: Exception) {}
                        }
                    } else {
                        try { mediaCodec.releaseOutputBuffer(outputIndex, false) } catch (_: Exception) {}
                    }
                    outputIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0L)
                }
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    Log.d(TAG, "Decoder output format changed: ${mediaCodec.outputFormat}")
                }
            } catch (e: Exception) {
                if (isSessionActive(gen)) {
                    Log.w(TAG, "Decoder output failed, marking codec dead: ${e.message}")
                    // Mark the decoder as not running so feedInput stops waiting on a dead codec
                    // and the next start() performs a full rebuild instead of reusing it.
                    isRunning.set(false)
                }
                break
            }
        }
    }

    private fun createAndConfigureCodec(
        codecMime: String,
        width: Int,
        height: Int,
        surface: Surface,
        configData: ByteArray?
    ): MediaCodec? {
        val format = MediaFormat.createVideoFormat(codecMime, width, height)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
        }
        format.setInteger(MediaFormat.KEY_PRIORITY, 0)
        if (configData != null) {
            setupCodecSpecificData(format, codecMime, configData)
        }

        // Retry up to 3 times with brief backoff in case native BufferQueueProducer is still releasing previous buffer (err -22)
        for (attempt in 1..3) {
            var c: MediaCodec? = null
            try {
                c = MediaCodec.createDecoderByType(codecMime)
                c.configure(format, surface, null, 0)
                c.start()
                return c
            } catch (e: Exception) {
                Log.w(TAG, "Configure attempt $attempt failed: ${e.message}")
                try { c?.stop() } catch (_: Exception) {}
                try { c?.release() } catch (_: Exception) {}
                if (attempt < 3) {
                    try { Thread.sleep(60) } catch (_: Exception) {}
                }
            }
        }
        return null
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
        generation++
        isRunning.set(false)
        currentSurface = null
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
