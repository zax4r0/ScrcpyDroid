package dev.scrcpydroid.core.relay

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lightweight, zero-dependency RTSP Server for ScrcpyDroid.
 * Implements RFC 2326 (RTSP 1.0), RFC 3984 (H.264 over RTP), and RFC 3640 (AAC over RTP).
 * Supports TCP interleaved streaming (required for ADB reverse port-forwarding) and UDP.
 */
class RtspServer(
    private val port: Int = 8554,
    private val onClientCountChanged: (Int) -> Unit = {}
) {
    companion object {
        private const val TAG = "RtspServer"
        private const val VIDEO_PAYLOAD_TYPE = 96
        private const val AUDIO_PAYLOAD_TYPE = 97
        private const val SSRC_VIDEO = 0x11223344
        private const val SSRC_AUDIO = 0x55667788
        private const val MAX_RTP_PAYLOAD = 1400
    }

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val clients = ConcurrentHashMap<String, RtspClientSession>()

    // Codec parameter caches for SDP construction
    @Volatile var spsBytes: ByteArray? = null
    @Volatile var ppsBytes: ByteArray? = null
    @Volatile var aacConfigBytes: ByteArray? = byteArrayOf(0x12, 0x08) // 44.1kHz, 1 channel

    private val videoSeq = AtomicInteger(0)
    private val audioSeq = AtomicInteger(0)

    fun start(scope: CoroutineScope) {
        if (serverJob != null) return
        serverJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(port).apply {
                    reuseAddress = true
                }
                Log.i(TAG, "RTSP Server started on port $port")

                while (isActive) {
                    val socket = try {
                        serverSocket?.accept() ?: break
                    } catch (e: Exception) {
                        break
                    }
                    val clientId = "${socket.inetAddress.hostAddress}:${socket.port}"
                    Log.i(TAG, "New RTSP client connected: $clientId")
                    val session = RtspClientSession(clientId, socket)
                    clients[clientId] = session
                    onClientCountChanged(clients.size)

                    scope.launch(Dispatchers.IO) {
                        session.handleRequests()
                        clients.remove(clientId)
                        session.close()
                        onClientCountChanged(clients.size)
                        Log.i(TAG, "RTSP client disconnected: $clientId")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "RTSP Server error", e)
            } finally {
                stop()
            }
        }
    }

    fun stop() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null

        clients.values.forEach { it.close() }
        clients.clear()
        onClientCountChanged(0)

        serverJob?.cancel()
        serverJob = null
        Log.i(TAG, "RTSP Server stopped")
    }

    /**
     * Broadcasts an H.264 NAL unit to all connected, playing RTSP clients.
     */
    fun sendH264NalUnit(nalu: ByteArray, timestampUs: Long) {
        if (nalu.isEmpty()) return
        val nalType = nalu[0].toInt() and 0x1F

        // Cache SPS and PPS
        if (nalType == 7) spsBytes = nalu.copyOf()
        if (nalType == 8) ppsBytes = nalu.copyOf()

        // Clock rate for H.264 is 90000 Hz
        val rtpTimestamp = (timestampUs * 90 / 1000).toInt()

        if (nalu.size <= MAX_RTP_PAYLOAD) {
            // Single NAL unit packet
            val seq = videoSeq.getAndIncrement() and 0xFFFF
            val rtpPacket = buildRtpPacket(
                payloadType = VIDEO_PAYLOAD_TYPE,
                sequenceNumber = seq,
                timestamp = rtpTimestamp,
                ssrc = SSRC_VIDEO,
                marker = true,
                payload = nalu
            )
            broadcastRtp(channel = 0, isVideo = true, rtpPacket)
        } else {
            // FU-A fragmentation
            val nalHeader = nalu[0]
            val fnri = nalHeader.toInt() and 0x60
            val fuIndicator = (fnri or 28).toByte()

            var offset = 1
            var remaining = nalu.size - 1

            while (remaining > 0) {
                val chunkSize = minOf(remaining, MAX_RTP_PAYLOAD)
                val isStart = (offset == 1)
                val isEnd = (remaining == chunkSize)

                var fuHeader = nalType
                if (isStart) fuHeader = fuHeader or 0x80
                if (isEnd) fuHeader = fuHeader or 0x40

                val payload = ByteArray(2 + chunkSize)
                payload[0] = fuIndicator
                payload[1] = fuHeader.toByte()
                System.arraycopy(nalu, offset, payload, 2, chunkSize)

                val seq = videoSeq.getAndIncrement() and 0xFFFF
                val rtpPacket = buildRtpPacket(
                    payloadType = VIDEO_PAYLOAD_TYPE,
                    sequenceNumber = seq,
                    timestamp = rtpTimestamp,
                    ssrc = SSRC_VIDEO,
                    marker = isEnd,
                    payload = payload
                )
                broadcastRtp(channel = 0, isVideo = true, rtpPacket)

                offset += chunkSize
                remaining -= chunkSize
            }
        }
    }

    /**
     * Broadcasts an AAC audio frame to all connected RTSP clients.
     */
    fun sendAacFrame(aacData: ByteArray, timestampUs: Long, sampleRate: Int = 44100) {
        if (aacData.isEmpty()) return

        // Audio clock rate is sampleRate (e.g. 44100)
        val rtpTimestamp = (timestampUs * sampleRate / 1_000_000L).toInt()
        val seq = audioSeq.getAndIncrement() and 0xFFFF

        // AU Header for AAC-hbr (RFC 3640)
        // 2 bytes AU-headers-length (bits = 16) + 2 bytes AU-header (13-bit size + 3-bit index)
        val auHeader = ByteArray(4)
        auHeader[0] = 0x00
        auHeader[1] = 0x10 // 16 bits of header length
        val frameLen = aacData.size
        auHeader[2] = ((frameLen shr 5) and 0xFF).toByte()
        auHeader[3] = ((frameLen shl 3) and 0xF8).toByte()

        val rtpPayload = ByteArray(auHeader.size + aacData.size)
        System.arraycopy(auHeader, 0, rtpPayload, 0, auHeader.size)
        System.arraycopy(aacData, 0, rtpPayload, auHeader.size, aacData.size)

        val rtpPacket = buildRtpPacket(
            payloadType = AUDIO_PAYLOAD_TYPE,
            sequenceNumber = seq,
            timestamp = rtpTimestamp,
            ssrc = SSRC_AUDIO,
            marker = true,
            payload = rtpPayload
        )
        broadcastRtp(channel = 2, isVideo = false, rtpPacket)
    }

    private fun broadcastRtp(channel: Int, isVideo: Boolean, packet: ByteArray) {
        for (session in clients.values) {
            if (!session.isPlaying) continue
            try {
                if (session.isInterleaved) {
                    session.sendInterleaved(if (isVideo) session.videoInterleavedChannel else session.audioInterleavedChannel, packet)
                } else {
                    session.sendUdp(isVideo, packet)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error sending RTP to ${session.id}: ${e.message}")
            }
        }
    }

    private fun buildRtpPacket(
        payloadType: Int,
        sequenceNumber: Int,
        timestamp: Int,
        ssrc: Int,
        marker: Boolean,
        payload: ByteArray
    ): ByteArray {
        val rtp = ByteArray(12 + payload.size)
        // V=2, P=0, X=0, CC=0 -> 0x80
        rtp[0] = 0x80.toByte()
        rtp[1] = ((if (marker) 0x80 else 0) or (payloadType and 0x7F)).toByte()
        rtp[2] = ((sequenceNumber shr 8) and 0xFF).toByte()
        rtp[3] = (sequenceNumber and 0xFF).toByte()
        rtp[4] = ((timestamp shr 24) and 0xFF).toByte()
        rtp[5] = ((timestamp shr 16) and 0xFF).toByte()
        rtp[6] = ((timestamp shr 8) and 0xFF).toByte()
        rtp[7] = (timestamp and 0xFF).toByte()
        rtp[8] = ((ssrc shr 24) and 0xFF).toByte()
        rtp[9] = ((ssrc shr 16) and 0xFF).toByte()
        rtp[10] = ((ssrc shr 8) and 0xFF).toByte()
        rtp[11] = (ssrc and 0xFF).toByte()
        System.arraycopy(payload, 0, rtp, 12, payload.size)
        return rtp
    }

    private inner class RtspClientSession(val id: String, val socket: Socket) {
        var isPlaying = false
        var isInterleaved = true
        var videoInterleavedChannel = 0
        var audioInterleavedChannel = 2

        private var videoUdpSocket: DatagramSocket? = null
        private var audioUdpSocket: DatagramSocket? = null
        private var clientVideoPort = 0
        private var clientAudioPort = 0
        private val outputStream: OutputStream = socket.getOutputStream()
        private val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

        fun handleRequests() {
            try {
                while (!socket.isClosed) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue

                    val parts = line.split(" ")
                    if (parts.size < 3) continue
                    val method = parts[0]
                    val uri = parts[1]

                    var cseq = "1"
                    var transport = ""
                    var contentLength = 0

                    var headerLine: String?
                    while (reader.readLine().also { headerLine = it } != null && headerLine!!.isNotEmpty()) {
                        val header = headerLine!!
                        val colon = header.indexOf(':')
                        if (colon != -1) {
                            val key = header.substring(0, colon).trim()
                            val value = header.substring(colon + 1).trim()
                            if (key.equals("CSeq", ignoreCase = true)) cseq = value
                            if (key.equals("Transport", ignoreCase = true)) transport = value
                            if (key.equals("Content-Length", ignoreCase = true)) contentLength = value.toIntOrNull() ?: 0
                        }
                    }

                    if (contentLength > 0) {
                        reader.skip(contentLength.toLong())
                    }

                    when (method.uppercase()) {
                        "OPTIONS" -> handleOptions(cseq)
                        "DESCRIBE" -> handleDescribe(cseq)
                        "SETUP" -> handleSetup(cseq, uri, transport)
                        "PLAY" -> handlePlay(cseq)
                        "TEARDOWN" -> {
                            handleTeardown(cseq)
                            break
                        }
                        else -> {
                            sendResponse("RTSP/1.0 200 OK\r\nCSeq: $cseq\r\n\r\n")
                        }
                    }
                }
            } catch (e: Exception) {
                // Connection closed
            }
        }

        private fun handleOptions(cseq: String) {
            val response = "RTSP/1.0 200 OK\r\n" +
                    "CSeq: $cseq\r\n" +
                    "Public: DESCRIBE, SETUP, TEARDOWN, PLAY, PAUSE\r\n\r\n"
            sendResponse(response)
        }

        private fun handleDescribe(cseq: String) {
            val spsBase64 = spsBytes?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: "Z00AKp2oHgCJ+WbgICAoAAADAAgAAAMBlCA="
            val ppsBase64 = ppsBytes?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: "aO48gA=="
            val sprop = "$spsBase64,$ppsBase64"

            val sdp = StringBuilder()
                .append("v=0\r\n")
                .append("o=- 0 0 IN IP4 0.0.0.0\r\n")
                .append("s=ScrcpyDroid Host Media Relay\r\n")
                .append("c=IN IP4 0.0.0.0\r\n")
                .append("t=0 0\r\n")
                .append("m=video 0 RTP/AVP 96\r\n")
                .append("a=rtpmap:96 H264/90000\r\n")
                .append("a=fmtp:96 packetization-mode=1;profile-level-id=42e01f;sprop-parameter-sets=$sprop\r\n")
                .append("a=control:trackID=0\r\n")
                .append("m=audio 0 RTP/AVP 97\r\n")
                .append("a=rtpmap:97 MPEG4-GENERIC/44100/1\r\n")
                .append("a=fmtp:97 streamtype=5;profile-level-id=1;mode=AAC-hbr;config=1208;sizelength=13;indexlength=3;indexdeltalength=3\r\n")
                .append("a=control:trackID=1\r\n")
                .toString()

            val response = "RTSP/1.0 200 OK\r\n" +
                    "CSeq: $cseq\r\n" +
                    "Content-Type: application/sdp\r\n" +
                    "Content-Length: ${sdp.toByteArray().size}\r\n\r\n" +
                    sdp
            sendResponse(response)
        }

        private fun handleSetup(cseq: String, uri: String, transport: String) {
            val isAudio = uri.contains("trackID=1")
            var transportResponse = transport

            if (transport.contains("interleaved", ignoreCase = true) || transport.contains("RTP/AVP/TCP", ignoreCase = true)) {
                isInterleaved = true
                if (isAudio) {
                    audioInterleavedChannel = 2
                    transportResponse = "RTP/AVP/TCP;unicast;interleaved=2-3"
                } else {
                    videoInterleavedChannel = 0
                    transportResponse = "RTP/AVP/TCP;unicast;interleaved=0-1"
                }
            } else if (transport.contains("client_port", ignoreCase = true)) {
                isInterleaved = false
                val match = Regex("client_port=(\\d+)-(\\d+)").find(transport)
                val port = match?.groupValues?.get(1)?.toIntOrNull() ?: 5000
                if (isAudio) {
                    clientAudioPort = port
                    audioUdpSocket = DatagramSocket()
                } else {
                    clientVideoPort = port
                    videoUdpSocket = DatagramSocket()
                }
            }

            val response = "RTSP/1.0 200 OK\r\n" +
                    "CSeq: $cseq\r\n" +
                    "Session: 12345678\r\n" +
                    "Transport: $transportResponse\r\n\r\n"
            sendResponse(response)
        }

        private fun handlePlay(cseq: String) {
            isPlaying = true
            val response = "RTSP/1.0 200 OK\r\n" +
                    "CSeq: $cseq\r\n" +
                    "Session: 12345678\r\n" +
                    "Range: npt=0.000-\r\n\r\n"
            sendResponse(response)
        }

        private fun handleTeardown(cseq: String) {
            isPlaying = false
            val response = "RTSP/1.0 200 OK\r\n" +
                    "CSeq: $cseq\r\n" +
                    "Session: 12345678\r\n\r\n"
            sendResponse(response)
        }

        @Synchronized
        fun sendInterleaved(channel: Int, rtpPacket: ByteArray) {
            val header = ByteArray(4)
            header[0] = '$'.code.toByte()
            header[1] = channel.toByte()
            header[2] = ((rtpPacket.size shr 8) and 0xFF).toByte()
            header[3] = (rtpPacket.size and 0xFF).toByte()
            outputStream.write(header)
            outputStream.write(rtpPacket)
            outputStream.flush()
        }

        fun sendUdp(isVideo: Boolean, rtpPacket: ByteArray) {
            val (udpSocket, clientPort) = if (isVideo) {
                videoUdpSocket to clientVideoPort
            } else {
                audioUdpSocket to clientAudioPort
            }
            if (udpSocket != null && clientPort > 0) {
                val packet = DatagramPacket(rtpPacket, rtpPacket.size, socket.inetAddress, clientPort)
                udpSocket.send(packet)
            }
        }

        private fun sendResponse(response: String) {
            outputStream.write(response.toByteArray())
            outputStream.flush()
        }

        fun close() {
            isPlaying = false
            try {
                videoUdpSocket?.close()
                audioUdpSocket?.close()
                socket.close()
            } catch (_: Exception) {}
        }
    }
}
