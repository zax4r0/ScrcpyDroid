package dev.scrcpydroid.core.relay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.view.Surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

/**
 * Hardware-accelerated Camera2 streamer that encodes 720p/30fps H.264 video
 * and feeds it into the RTSP / HTTP server and optional UI preview.
 */
class CameraStreamer(
    private val context: Context,
    private val onNalUnit: (ByteArray, Long) -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        private const val TAG = "CameraStreamer"
        const val VIDEO_WIDTH = 1280
        const val VIDEO_HEIGHT = 720
        private const val FRAME_RATE = 30
        private const val BIT_RATE = 2_000_000 // 2 Mbps
        private const val I_FRAME_INTERVAL = 1 // 1 second keyframe interval
    }

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var mediaCodec: MediaCodec? = null
    private var inputSurface: Surface? = null

    private var previewSurface: Surface? = null
    private var isFrontFacing = true
    private var isStreaming = false
    private var encoderJob: Job? = null

    fun setPreviewSurface(surface: Surface?) {
        previewSurface = surface
        if (isStreaming && cameraDevice != null) {
            // Reconfigure capture session with new preview surface
            createCaptureSession()
        }
    }

    fun isFrontCamera(): Boolean = isFrontFacing

    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope, frontFacing: Boolean = true) {
        if (isStreaming) return
        isFrontFacing = frontFacing
        isStreaming = true

        val thread = HandlerThread("CameraStreamerThread").apply { start() }
        cameraThread = thread
        cameraHandler = Handler(thread.looper)

        try {
            setupEncoder()
            openCamera()
            startEncoderLoop(scope)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start camera streamer", e)
            onError(e.message ?: "Camera streaming failed")
            stop()
        }
    }

    fun switchCamera(scope: CoroutineScope) {
        if (!isStreaming) return
        isFrontFacing = !isFrontFacing
        try {
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            openCamera()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to switch camera", e)
        }
    }

    private fun setupEncoder() {
        val format = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            VIDEO_WIDTH,
            VIDEO_HEIGHT
        ).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
            }
        }

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = codec.createInputSurface()
        codec.start()
        mediaCodec = codec
        Log.i(TAG, "Hardware H.264 encoder configured (${VIDEO_WIDTH}x${VIDEO_HEIGHT})")
    }

    @SuppressLint("MissingPermission")
    private fun openCamera() {
        val targetFacing = if (isFrontFacing) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
        val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
            val chars = cameraManager.getCameraCharacteristics(id)
            chars.get(CameraCharacteristics.LENS_FACING) == targetFacing
        } ?: cameraManager.cameraIdList.firstOrNull() ?: throw IllegalStateException("No suitable camera found")

        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                createCaptureSession()
            }

            override fun onDisconnected(camera: CameraDevice) {
                camera.close()
                cameraDevice = null
            }

            override fun onError(camera: CameraDevice, error: Int) {
                camera.close()
                cameraDevice = null
                onError("Camera open error: $error")
            }
        }, cameraHandler)
    }

    private fun createCaptureSession() {
        val device = cameraDevice ?: return
        val encoderSurf = inputSurface ?: return

        val surfaces = mutableListOf<Surface>(encoderSurf)
        previewSurface?.let { surfaces.add(it) }

        try {
            val sessionCallback = object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    val requestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                        addTarget(encoderSurf)
                        previewSurface?.let { addTarget(it) }
                        set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                        set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(30, 30))
                    }
                    session.setRepeatingRequest(requestBuilder.build(), null, cameraHandler)
                    Log.i(TAG, "CameraCaptureSession started with ${surfaces.size} output surfaces")
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    onError("Failed to configure camera capture session")
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val outputConfigs = surfaces.map { android.hardware.camera2.params.OutputConfiguration(it) }
                val sessionConfig = android.hardware.camera2.params.SessionConfiguration(
                    android.hardware.camera2.params.SessionConfiguration.SESSION_REGULAR,
                    outputConfigs,
                    context.mainExecutor,
                    sessionCallback
                )
                device.createCaptureSession(sessionConfig)
            } else {
                @Suppress("DEPRECATION")
                device.createCaptureSession(surfaces, sessionCallback, cameraHandler)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create capture session", e)
        }
    }

    private fun startEncoderLoop(scope: CoroutineScope) {
        encoderJob = scope.launch(Dispatchers.IO) {
            val codec = mediaCodec ?: return@launch
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

                        val data = ByteArray(bufferInfo.size)
                        outputBuffer.get(data)

                        // Strip start code (0x00000001 or 0x000001) if present
                        val cleanNalu = extractNalu(data)
                        if (cleanNalu.isNotEmpty()) {
                            onNalUnit(cleanNalu, bufferInfo.presentationTimeUs)
                        }
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val format = codec.outputFormat
                    Log.i(TAG, "Encoder output format changed: $format")
                    val csd0 = format.getByteBuffer("csd-0") // SPS
                    val csd1 = format.getByteBuffer("csd-1") // PPS
                    if (csd0 != null) {
                        val sps = ByteArray(csd0.remaining())
                        csd0.get(sps)
                        onNalUnit(extractNalu(sps), 0)
                    }
                    if (csd1 != null) {
                        val pps = ByteArray(csd1.remaining())
                        csd1.get(pps)
                        onNalUnit(extractNalu(pps), 0)
                    }
                }
            }
        }
    }

    private fun extractNalu(data: ByteArray): ByteArray {
        var start = 0
        if (data.size >= 4 && data[0] == 0.toByte() && data[1] == 0.toByte() && data[2] == 0.toByte() && data[3] == 1.toByte()) {
            start = 4
        } else if (data.size >= 3 && data[0] == 0.toByte() && data[1] == 0.toByte() && data[2] == 1.toByte()) {
            start = 3
        }
        return if (start > 0) data.copyOfRange(start, data.size) else data
    }

    fun stop() {
        isStreaming = false
        encoderJob?.cancel()
        encoderJob = null

        try {
            captureSession?.close()
        } catch (_: Exception) {}
        captureSession = null

        try {
            cameraDevice?.close()
        } catch (_: Exception) {}
        cameraDevice = null

        try {
            mediaCodec?.stop()
            mediaCodec?.release()
        } catch (_: Exception) {}
        mediaCodec = null

        try {
            inputSurface?.release()
        } catch (_: Exception) {}
        inputSurface = null

        cameraThread?.quitSafely()
        cameraThread = null
        cameraHandler = null
        Log.i(TAG, "CameraStreamer stopped")
    }
}
