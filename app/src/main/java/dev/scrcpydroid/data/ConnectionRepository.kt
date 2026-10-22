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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
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

            val updatedList = (listOf(connection) + currentList.filterNot {
                it.host == connection.host && it.port == connection.port
            }).take(10)

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
                it.host == connection.host && it.port == connection.port
            }

            preferences[RECENT_CONNECTIONS] = json.encodeToString(updatedList)
        }
    }
}
