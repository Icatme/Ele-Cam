package com.example.ele_cam.camera

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ele_cam.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val app: Application = application
    private val repository = CameraSettingsRepository(app)
    private val commandService = CameraCommandService()

    private val _state = MutableStateFlow(CameraUiState())
    val state: StateFlow<CameraUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<CameraUiEvent>()
    val events = _events.asSharedFlow()

    private val _testResults = MutableSharedFlow<CameraCommandResult>()
    val testResults = _testResults.asSharedFlow()

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    private var hasPromptedForSettings = false

    init {
        viewModelScope.launch {
            repository.cameraSettingsFlow.collect { settings ->
                _state.update { it.copy(settings = settings) }

                if (settings == null) {
                    if (!hasPromptedForSettings) {
                        hasPromptedForSettings = true
                        _events.emit(CameraUiEvent.RequestSettings())
                    }
                } else {
                    hasPromptedForSettings = false
                }
            }
        }
    }

    fun updateCameraSettings(settings: CameraSettings) {
        viewModelScope.launch {
            repository.updateSettings(settings)
        }
    }

    fun requestSettingsDialog() {
        viewModelScope.launch {
            _events.emit(CameraUiEvent.RequestSettings())
        }
    }

    fun takePhoto() = sendCommand("1001", "0", app.getString(R.string.message_photo_success))

    fun startRecording() =
        sendCommand("2001", "1", app.getString(R.string.message_start_recording_success))

    fun stopRecording() =
        sendCommand("2001", "0", app.getString(R.string.message_stop_recording_success))

    fun setRecordMode(isRecordMode: Boolean) = sendCommand(
        "3001",
        if (isRecordMode) "1" else "0",
        app.getString(if (isRecordMode) R.string.message_record_mode else R.string.message_photo_mode)
    )

    private fun sendCommand(command: String, parameter: String, successMessage: String) {
        val settings = _state.value.settings
        if (settings == null) {
            notifyMissingSettings()
            return
        }

        if (_state.value.isExecutingCommand) {
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isExecutingCommand = true) }
            val result = commandService.executeCommand(settings, command, parameter)
            _state.update { it.copy(isExecutingCommand = false) }

            if (result.success) {
                _events.emit(CameraUiEvent.ShowMessage(successMessage))
            } else {
                val errorMessage = result.message ?: app.getString(R.string.message_command_failed)
                _events.emit(CameraUiEvent.ShowMessage(errorMessage))
            }
        }
    }

    private fun notifyMissingSettings() {
        viewModelScope.launch {
            _events.emit(CameraUiEvent.ShowMessage(app.getString(R.string.error_settings_missing)))
            _events.emit(CameraUiEvent.RequestSettings())
        }
    }

    fun testCameraConnection(settings: CameraSettings) {
        viewModelScope.launch {
            _isTestingConnection.value = true
            val result = commandService.testConnection(settings)
            _isTestingConnection.value = false
            val message = when {
                result.message != null -> result.message
                result.success -> app.getString(R.string.message_test_connection_success)
                else -> app.getString(R.string.message_test_connection_failed)
            }
            _testResults.emit(result.copy(message = message))
        }
    }
}

data class CameraUiState(
    val settings: CameraSettings? = null,
    val isExecutingCommand: Boolean = false
)

sealed class CameraUiEvent {
    data class ShowMessage(val message: String) : CameraUiEvent()
    data class RequestSettings(val reasonMessage: String? = null) : CameraUiEvent()
}
