package com.example.ele_cam.camera

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.cameraSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "camera_settings"
)

/**
 * Persists and exposes the network configuration for the action camera.
 */
class CameraSettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.cameraSettingsDataStore

    val cameraSettingsFlow: Flow<CameraSettings?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val streamUrl = preferences[STREAM_URL_KEY].orEmpty()
            val host = preferences[COMMAND_HOST_KEY].orEmpty()
            val port = preferences[COMMAND_PORT_KEY] ?: CameraSettings.DEFAULT_PORT

            if (streamUrl.isBlank() || host.isBlank()) {
                null
            } else {
                CameraSettings(streamUrl, host, port)
            }
        }

    suspend fun updateSettings(settings: CameraSettings) {
        dataStore.edit { preferences ->
            preferences[STREAM_URL_KEY] = settings.streamUrl
            preferences[COMMAND_HOST_KEY] = settings.commandHost
            preferences[COMMAND_PORT_KEY] = settings.commandPort
        }
    }

    private companion object {
        val STREAM_URL_KEY = stringPreferencesKey("stream_url")
        val COMMAND_HOST_KEY = stringPreferencesKey("command_host")
        val COMMAND_PORT_KEY = intPreferencesKey("command_port")
    }
}
