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

                _state.update { current ->
                    current.copy(
                        settings = settings,
                        isRecordMode = if (settings == null) null else current.isRecordMode
                    )
                }

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


    fun takePhoto() = sendCommand(
        command = "1001",
        parameter = "0",
        action = CameraCommandAction.TakePhoto,
        successMessage = app.getString(R.string.message_photo_success)
    )

    fun startRecording() = sendCommand(
        command = "2001",
        parameter = "1",
        action = CameraCommandAction.StartRecording,
        successMessage = app.getString(R.string.message_start_recording_success)
    )

    fun stopRecording() = sendCommand(
        command = "2001",
        parameter = "0",
        action = CameraCommandAction.StopRecording,
        successMessage = app.getString(R.string.message_stop_recording_success)
    )

    fun setRecordMode(isRecordMode: Boolean) = sendCommand(
        command = "3001",
        parameter = if (isRecordMode) "1" else "0",
        action = CameraCommandAction.SetRecordMode(isRecordMode),
        successMessage = app.getString(
            if (isRecordMode) R.string.message_record_mode else R.string.message_photo_mode
        )
    )

    private fun sendCommand(
        command: String,
        parameter: String,
        action: CameraCommandAction,
        successMessage: String
    ) {
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


            if (result.success && action is CameraCommandAction.SetRecordMode) {
                _state.update { it.copy(isRecordMode = action.isRecordMode) }
            }

            val message = if (result.success) {
                successMessage
            } else {
                result.message ?: app.getString(R.string.message_command_failed)
            }

            _events.emit(
                CameraUiEvent.CommandCompleted(
                    action = action,
                    result = result.copy(message = message)
                )
            )
        }
    }

    private fun notifyMissingSettings() {
        viewModelScope.launch {

            _events.emit(
                CameraUiEvent.RequestSettings(app.getString(R.string.error_settings_missing))
            )
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

    val isExecutingCommand: Boolean = false,
    val isRecordMode: Boolean? = null
)

sealed class CameraUiEvent {
    data class ShowMessage(val message: String) : CameraUiEvent()
    data class RequestSettings(val reasonMessage: String? = null) : CameraUiEvent()

    data class CommandCompleted(
        val action: CameraCommandAction,
        val result: CameraCommandResult
    ) : CameraUiEvent()
}

sealed class CameraCommandAction {
    object TakePhoto : CameraCommandAction()
    object StartRecording : CameraCommandAction()
    object StopRecording : CameraCommandAction()
    data class SetRecordMode(val isRecordMode: Boolean) : CameraCommandAction()
}
