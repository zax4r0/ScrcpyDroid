package dev.scrcpydroid.data.model

import kotlinx.serialization.Serializable

@Serializable
data class DeviceSettings(
    val useGlobalSettings: Boolean = true,
    val maxSize: Int? = null,
    val videoBitRate: Int? = null,
    val maxFps: Int? = null,
    val videoCodec: String? = null, // "h264" or "h265"
    val audioEnabled: Boolean? = null,
    val autoUnlock: Boolean? = null,
    val devicePassword: String? = null,
    val turnScreenOffOnConnect: Boolean? = null,
    val stayAwake: Boolean? = null
)

@Serializable
data class DeviceConnection(
    val id: String = "",
    val host: String,
    val port: Int,
    val deviceName: String = "",
    val customName: String = "",
    val lastConnected: Long = System.currentTimeMillis(),
    val customSettings: DeviceSettings? = null
) {
    val uniqueKey: String
        get() = id.ifBlank { "${host}:${port}" }

    val displayName: String
        get() = customName.ifBlank { deviceName.ifBlank { "$host:$port" } }
}
