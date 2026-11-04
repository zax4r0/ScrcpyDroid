package dev.scrcpydroid.core.relay

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

/**
 * Microphone audio capture and AAC encoder for ScrcpyDroid Host Media Relay.
 */
class AudioStreamer(
    private val onAacFrame: (ByteArray, Long) -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        private const val TAG = "AudioStreamer"
        const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BIT_RATE = 64_000 // 64 kbps mono
    }

    private var audioRecord: AudioRecord? = null
    private var mediaCodec: MediaCodec? = null
    private var captureJob: Job? = null
    private var encoderJob: Job? = null

    @Volatile var isMuted = false
    @Volatile var isStreaming = false

    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope) {
        if (isStreaming) return
        isStreaming = true

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = maxOf(minBufferSize * 2, 4096)

        try {
            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("AudioRecord initialization failed")
            }
            record.startRecording()
            audioRecord = record

            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8192)
            }

            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            mediaCodec = codec

            startAudioLoops(scope, bufferSize)
            Log.i(TAG, "AudioStreamer started (44.1kHz AAC-LC)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioStreamer", e)
            onError(e.message ?: "Audio capture failed")
            stop()
        }
    }

    private fun startAudioLoops(scope: CoroutineScope, bufferSize: Int) {
        val record = audioRecord ?: return
        val codec = mediaCodec ?: return

        // Audio capture coroutine
        captureJob = scope.launch(Dispatchers.IO) {
            val pcmBuffer = ByteArray(2048)
            var presentationTimeUs = 0L

            while (isActive && isStreaming) {
                val bytesRead = record.read(pcmBuffer, 0, pcmBuffer.size)
                if (bytesRead > 0) {
                    if (isMuted) {
                        pcmBuffer.fill(0, 0, bytesRead)
                    }

                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        if (inputBuffer != null) {
                            inputBuffer.clear()
                            inputBuffer.put(pcmBuffer, 0, bytesRead)
                            codec.queueInputBuffer(inputIndex, 0, bytesRead, presentationTimeUs, 0)
                        }
                    }
                    presentationTimeUs += (bytesRead * 1_000_000L) / (SAMPLE_RATE * 2) // 16-bit mono = 2 bytes/sample
                }
            }
        }

        // AAC output coroutine
        encoderJob = scope.launch(Dispatchers.IO) {
            val bufferInfo = MediaCodec.BufferInfo()
            while (isActive && isStreaming) {
                val outputIndex = try {
                    codec.dequeueOutputBuffer(bufferInfo, 10_000)
                } catch (e: Exception) {
                    break
                }

                if (outputIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                        val aacData = ByteArray(bufferInfo.size)
                        outputBuffer.get(aacData)

                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            onAacFrame(aacData, bufferInfo.presentationTimeUs)
                        }
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                }
            }
        }
    }

    fun stop() {
        isStreaming = false
        captureJob?.cancel()
        captureJob = null
        encoderJob?.cancel()
        encoderJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null

        try {
            mediaCodec?.stop()
            mediaCodec?.release()
        } catch (_: Exception) {}
        mediaCodec = null
        Log.i(TAG, "AudioStreamer stopped")
    }
}
