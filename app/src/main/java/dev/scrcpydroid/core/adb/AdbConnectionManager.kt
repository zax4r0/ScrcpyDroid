package dev.scrcpydroid.core.adb

import dadb.AdbStream
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okio.source
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data class Connected(val deviceName: String) : ConnectionState
    data class Error(val message: String) : ConnectionState
}

@Singleton
class AdbConnectionManager @Inject constructor(
    private val adbKeyManager: AdbKeyManager
) {
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var dadb: Dadb? = null
    val currentDadb: Dadb? get() = dadb

    suspend fun connect(host: String, port: Int): Result<Dadb> = withContext(Dispatchers.IO) {
        try {
            _connectionState.value = ConnectionState.Connecting
            disconnectInternal()

            val keyPair = adbKeyManager.getOrCreateKeyPair()
            val dadbInstance = Dadb.create(host, port, keyPair)
            dadb = dadbInstance

            val deviceName = try {
                val shellResponse = dadbInstance.shell("getprop ro.product.model")
                optimizeAdbSocket(dadbInstance)
                shellResponse.output.trim().ifEmpty { "$host:$port" }
            } catch (_: Exception) {
                optimizeAdbSocket(dadbInstance)
                "$host:$port"
            }

            _connectionState.value = ConnectionState.Connected(deviceName)
            Result.success(dadbInstance)
        } catch (e: Exception) {
            val errorMessage = e.message ?: "Failed to connect to $host:$port"
            _connectionState.value = ConnectionState.Error(errorMessage)
            disconnectInternal()
            Result.failure(e)
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        disconnectInternal()
        _connectionState.value = ConnectionState.Disconnected
    }

    private fun disconnectInternal() {
        try {
            dadb?.close()
        } catch (_: Exception) {
        } finally {
            dadb = null
        }
    }

    suspend fun pushFile(inputStream: InputStream, remotePath: String): Unit = withContext(Dispatchers.IO) {
        val current = dadb ?: throw IllegalStateException("ADB connection not established")
        inputStream.source().use { source ->
            current.push(
                source = source,
                remotePath = remotePath,
                mode = 420,
                lastModifiedMs = System.currentTimeMillis()
            )
        }
    }

    suspend fun executeShell(command: String): AdbStream = withContext(Dispatchers.IO) {
        val current = dadb ?: throw IllegalStateException("ADB connection not established")
        optimizeAdbSocket(current)
        current.open("shell:$command")
    }

    suspend fun openStream(destination: String): AdbStream = withContext(Dispatchers.IO) {
        val current = dadb ?: throw IllegalStateException("ADB connection not established")
        optimizeAdbSocket(current)
        current.open(destination)
    }

    private fun optimizeAdbSocket(dadbInstance: Dadb) {
        try {
            val field = dadbInstance.javaClass.getDeclaredField("connection")
            field.isAccessible = true
            val pair = field.get(dadbInstance) as? Pair<*, *>
            val socket = pair?.second as? java.net.Socket
            socket?.let { s ->
                if (!s.tcpNoDelay) s.tcpNoDelay = true
                s.trafficClass = 0x10 // IPTOS_LOWDELAY
                s.setPerformancePreferences(0, 2, 1) // Prioritize low latency
                s.sendBufferSize = 256 * 1024
                s.receiveBufferSize = 2 * 1024 * 1024
            }
        } catch (_: Exception) {
            // Ignored if reflection is not available
        }
    }
}
