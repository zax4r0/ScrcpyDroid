package dev.scrcpydroid.core.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class DiscoveredDevice(
    val name: String,
    val host: String,
    val port: Int,
    val isPairing: Boolean = false,
    val serviceType: String = "",
    val discoveredAt: Long = System.currentTimeMillis()
)

@Singleton
class DeviceDiscoveryManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "DeviceDiscovery"
        const val SERVICE_TYPE_TLS_CONNECT = "_adb-tls-connect._tcp."
        const val SERVICE_TYPE_TLS_PAIRING = "_adb-tls-pairing._tcp."
        const val SERVICE_TYPE_ADB_PLAIN = "_adb._tcp."
    }

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private var multicastLock: WifiManager.MulticastLock? = null

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val activeListeners = ConcurrentHashMap<String, NsdManager.DiscoveryListener>()
    private val devicesMap = ConcurrentHashMap<String, DiscoveredDevice>()

    fun startDiscovery() {
        if (_isScanning.value) return

        try {
            multicastLock = wifiManager?.createMulticastLock("scrcpydroid_multicast_lock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire multicast lock: ${e.message}")
        }

        devicesMap.clear()
        _discoveredDevices.value = emptyList()
        _isScanning.value = true

        startDiscoveryForType(SERVICE_TYPE_TLS_CONNECT)
        startDiscoveryForType(SERVICE_TYPE_TLS_PAIRING)
        startDiscoveryForType(SERVICE_TYPE_ADB_PLAIN)
    }

    private fun startDiscoveryForType(serviceType: String) {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e(TAG, "Discovery failed for $serviceType: code $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e(TAG, "Stop discovery failed for $serviceType: code $errorCode")
            }

            override fun onDiscoveryStarted(serviceType: String?) {
                Log.d(TAG, "Discovery started for $serviceType")
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                Log.d(TAG, "Discovery stopped for $serviceType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service found: ${serviceInfo.serviceName} (${serviceInfo.serviceType})")
                resolveServiceSafely(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service lost: ${serviceInfo.serviceName}")
                val name = serviceInfo.serviceName
                devicesMap.keys.filter { it.startsWith("${name}_") || it == name }.forEach {
                    devicesMap.remove(it)
                }
                _discoveredDevices.value = devicesMap.values.toList()
            }
        }

        try {
            activeListeners[serviceType] = listener
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start discovery for $serviceType", e)
        }
    }

    private fun isLocalAddress(host: String): Boolean {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces() ?: return false
            val cleanHost = host.removePrefix("/").split("%")[0]
            for (iface in interfaces) {
                for (addr in iface.inetAddresses) {
                    val cleanAddr = (addr.hostAddress ?: "").removePrefix("/").split("%")[0]
                    if (cleanAddr.isNotEmpty() && (cleanAddr == cleanHost || cleanHost == "127.0.0.1" || cleanHost == "::1")) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking local address: ${e.message}")
        }
        return false
    }

    private fun resolveServiceSafely(serviceInfo: NsdServiceInfo) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                Log.w(TAG, "Resolve failed for ${serviceInfo?.serviceName}: error $errorCode")
            }

            override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                val host: InetAddress? = resolvedInfo.host
                val hostAddress = host?.hostAddress ?: return
                val port = resolvedInfo.port
                if (port <= 0) return

                // Ignore own device
                if (isLocalAddress(hostAddress)) {
                    Log.d(TAG, "Ignoring local/own device at $hostAddress:$port (${resolvedInfo.serviceName})")
                    return
                }

                val isPairing = resolvedInfo.serviceType?.contains("pairing", ignoreCase = true) == true
                val cleanName = cleanServiceName(resolvedInfo.serviceName)

                val device = DiscoveredDevice(
                    name = cleanName,
                    host = hostAddress,
                    port = port,
                    isPairing = isPairing,
                    serviceType = resolvedInfo.serviceType ?: ""
                )

                val key = "${resolvedInfo.serviceName}_${resolvedInfo.serviceType}"
                devicesMap[key] = device
                _discoveredDevices.value = devicesMap.values.toList()
                Log.d(TAG, "Device resolved: $cleanName at $hostAddress:$port (pairing=$isPairing)")
            }
        }

        try {
            nsdManager.resolveService(serviceInfo, resolveListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating resolve for ${serviceInfo.serviceName}", e)
        }
    }

    private fun cleanServiceName(raw: String): String {
        return raw.replace(Regex("._adb.*"), "")
            .replace(Regex("._tcp.*"), "")
            .replace("_", " ")
            .trim()
            .ifEmpty { raw }
    }

    fun stopDiscovery() {
        if (!_isScanning.value) return

        activeListeners.forEach { (type, listener) ->
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping discovery for $type: ${e.message}")
            }
        }
        activeListeners.clear()

        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing multicast lock: ${e.message}")
        }
        multicastLock = null

        _isScanning.value = false
    }
}
