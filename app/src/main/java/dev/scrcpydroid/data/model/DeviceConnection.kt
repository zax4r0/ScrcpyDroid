package dev.scrcpydroid.data.model

import kotlinx.serialization.Serializable

@Serializable
data class DeviceConnection(
    val host: String,
    val port: Int,
    val deviceName: String = "",
    val lastConnected: Long = System.currentTimeMillis()
)
