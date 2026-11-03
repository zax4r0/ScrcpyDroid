package dev.scrcpydroid.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.scrcpydroid.data.model.DeviceConnection
import dev.scrcpydroid.data.model.DeviceSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.connectionDataStore: DataStore<Preferences> by preferencesDataStore(name = "scrcpydroid_connections")

@Singleton
class ConnectionRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val RECENT_CONNECTIONS = stringPreferencesKey("recent_connections")

    fun getRecentConnections(): Flow<List<DeviceConnection>> = context.connectionDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val jsonString = preferences[RECENT_CONNECTIONS]
            if (jsonString.isNullOrEmpty()) {
                emptyList()
            } else {
                try {
                    json.decodeFromString<List<DeviceConnection>>(jsonString)
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }

    suspend fun getConnection(host: String, port: Int, deviceId: String = ""): DeviceConnection? {
        val list = getRecentConnections().first()
        return if (deviceId.isNotBlank()) {
            list.find { it.id == deviceId } ?: list.find { it.host == host && it.port == port }
        } else {
            list.find { it.host == host && it.port == port }
        }
    }

    suspend fun addConnection(connection: DeviceConnection) {
        context.connectionDataStore.edit { preferences ->
            val jsonString = preferences[RECENT_CONNECTIONS]
            val currentList = if (jsonString.isNullOrEmpty()) {
                emptyList()
            } else {
                try {
                    json.decodeFromString<List<DeviceConnection>>(jsonString)
                } catch (e: Exception) {
                    emptyList()
                }
            }

            // Find existing device by persistent deviceId first, or fallback to host:port
            val existing = if (connection.id.isNotBlank()) {
                currentList.find { it.id == connection.id } ?: currentList.find { it.host == connection.host && it.port == connection.port }
            } else {
                currentList.find { it.host == connection.host && it.port == connection.port }
            }

            val merged = if (existing != null) {
                connection.copy(
                    id = if (connection.id.isNotBlank()) connection.id else existing.id,
                    customName = existing.customName.ifBlank { connection.customName },
                    customSettings = existing.customSettings ?: connection.customSettings,
                    deviceName = if (connection.deviceName.isNotBlank() && connection.deviceName != "${connection.host}:${connection.port}") {
                        connection.deviceName
                    } else existing.deviceName.ifBlank { connection.deviceName },
                    lastConnected = System.currentTimeMillis()
                )
            } else {
                connection
            }

            // Keep ONLY ONE entry for this physical device: filter out old entries matching ID or host:port
            val updatedList = (listOf(merged) + currentList.filterNot {
                (merged.id.isNotBlank() && it.id == merged.id) || (it.host == merged.host && it.port == merged.port)
            }).take(20)

            preferences[RECENT_CONNECTIONS] = json.encodeToString(updatedList)
        }
    }

    suspend fun renameConnection(key: String, newName: String) {
        context.connectionDataStore.edit { preferences ->
            val jsonString = preferences[RECENT_CONNECTIONS] ?: return@edit
            val currentList = try {
                json.decodeFromString<List<DeviceConnection>>(jsonString)
            } catch (e: Exception) {
                emptyList()
            }

            val updatedList = currentList.map {
                if (it.uniqueKey == key || it.id == key || it.host == key || "${it.host}:${it.port}" == key) {
                    it.copy(customName = newName.trim())
                } else it
            }

            preferences[RECENT_CONNECTIONS] = json.encodeToString(updatedList)
        }
    }

    suspend fun updateDeviceSettings(key: String, settings: DeviceSettings) {
        context.connectionDataStore.edit { preferences ->
            val jsonString = preferences[RECENT_CONNECTIONS] ?: return@edit
            val currentList = try {
                json.decodeFromString<List<DeviceConnection>>(jsonString)
            } catch (e: Exception) {
                emptyList()
            }

            val updatedList = currentList.map {
                if (it.uniqueKey == key || it.id == key || it.host == key || "${it.host}:${it.port}" == key) {
                    it.copy(customSettings = settings)
                } else it
            }

            preferences[RECENT_CONNECTIONS] = json.encodeToString(updatedList)
        }
    }

    suspend fun removeConnection(connection: DeviceConnection) {
        context.connectionDataStore.edit { preferences ->
            val jsonString = preferences[RECENT_CONNECTIONS] ?: return@edit
            val currentList = try {
                json.decodeFromString<List<DeviceConnection>>(jsonString)
            } catch (e: Exception) {
                emptyList()
            }

            val updatedList = currentList.filterNot {
                (connection.id.isNotBlank() && it.id == connection.id) || (it.host == connection.host && it.port == connection.port)
            }

            preferences[RECENT_CONNECTIONS] = json.encodeToString(updatedList)
        }
    }
}
