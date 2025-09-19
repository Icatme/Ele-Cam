package com.example.ele_cam.camera

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Performs HTTP requests against the action camera control interface.
 */
class CameraCommandService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(5, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
) {

    suspend fun executeCommand(
        settings: CameraSettings,
        command: String,
        parameter: String
    ): CameraCommandResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(buildCommandUrl(settings, command, parameter))
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    CameraCommandResult(success = true)
                } else {
                    val message = "HTTP ${response.code}: ${response.message}"
                    CameraCommandResult(success = false, message = message)
                }
            }
        }.getOrElse { throwable ->
            val message = throwable.localizedMessage ?: throwable.javaClass.simpleName
            CameraCommandResult(success = false, message = message)
        }
    }

    suspend fun testConnection(settings: CameraSettings): CameraCommandResult =
        executeCommand(settings, TEST_COMMAND, TEST_PARAMETER)

    private fun buildCommandUrl(
        settings: CameraSettings,
        command: String,
        parameter: String
    ): HttpUrl {
        val builder = HttpUrl.Builder()
            .scheme("http")
            .host(settings.commandHost)

        if (settings.commandPort != CameraSettings.DEFAULT_PORT) {
            builder.port(settings.commandPort)
        }

        return builder
            .addQueryParameter("custom", "1")
            .addQueryParameter("cmd", command)
            .addQueryParameter("par", parameter)
            .build()
    }

    private companion object {
        private const val TEST_COMMAND = "3010"
        private const val TEST_PARAMETER = "0"
    }
}
