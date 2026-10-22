package dev.scrcpydroid.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okio.BufferedSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioPlayer @Inject constructor() {
    companion object {
        private const val TAG = "AudioPlayer"
        private const val SAMPLE_RATE = 48000
    }

    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null

    fun start(source: BufferedSource, scope: CoroutineScope) {
        stop()

        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = (minBufferSize * 4).coerceAtLeast(8192)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack = track
        track.play()

        playbackJob = scope.launch(Dispatchers.IO) {
            try {
                // 1. Read 4-byte audio codec ID header
                val codecId = source.readInt()
                Log.d(TAG, "Audio stream started, codec ID: 0x${Integer.toHexString(codecId)}")

                // 2. Continuous packet reading
                while (currentCoroutineContext().isActive) {
                    val ptsAndFlags = source.readLong()
                    val packetSize = source.readInt()
                    if (packetSize <= 0) continue

                    val pcmData = source.readByteArray(packetSize.toLong())
                    track.write(pcmData, 0, pcmData.size)
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Log.w(TAG, "Audio playback loop ended: ${e.message}")
                }
            } finally {
                stopTrack()
            }
        }
    }

    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        stopTrack()
    }

    private fun stopTrack() {
        try {
            audioTrack?.apply {
                if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                    pause()
                    flush()
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioTrack: ${e.message}")
        } finally {
            audioTrack = null
        }
    }
}
