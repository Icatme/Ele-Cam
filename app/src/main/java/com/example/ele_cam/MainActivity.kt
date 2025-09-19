package com.example.ele_cam

import android.os.Bundle
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.ele_cam.camera.CameraSettingsDialogFragment
import com.example.ele_cam.camera.CameraUiEvent
import com.example.ele_cam.camera.CameraUiState
import com.example.ele_cam.camera.CameraViewModel
import com.example.ele_cam.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: CameraViewModel by viewModels()

    private var currentState: CameraUiState = CameraUiState()
    private var lastLoadedStreamUrl: String? = null
    private var isTestingConnection: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWebView()
        setupControls()
        observeViewModel()
    }

    private fun setupWebView() {
        binding.webView.apply {
            settings.loadsImagesAutomatically = true
            settings.javaScriptEnabled = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean = false

                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = false

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == false) return
                    val description = error?.description?.toString().orEmpty()
                    val message = if (description.isBlank()) {
                        getString(R.string.message_stream_error, getString(R.string.camera_settings_not_configured))
                    } else {
                        getString(R.string.message_stream_error, description)
                    }
                    Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                }
            }
            setOnClickListener { reload() }
        }
    }

    private fun setupControls() {
        binding.buttonEditSettings.setOnClickListener {
            viewModel.requestSettingsDialog()
        }
        binding.btnReload.setOnClickListener {
            val url = currentState.settings?.streamUrl
            if (url != null) {
                reloadStream(url)
            } else {
                viewModel.requestSettingsDialog()
            }
        }
        binding.btnPhoto.setOnClickListener { viewModel.takePhoto() }
        binding.btnRecordStart.setOnClickListener { viewModel.startRecording() }
        binding.btnRecordStop.setOnClickListener { viewModel.stopRecording() }
        binding.camSwitch.apply {
            isChecked = false
            text = getString(R.string.camera_mode_photo)
            setOnCheckedChangeListener { _, isChecked ->
                text = getString(if (isChecked) R.string.camera_mode_record else R.string.camera_mode_photo)
                if (isPressed) {
                    viewModel.setRecordMode(isChecked)
                }
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collect { state ->
                        currentState = state
                        updateUi(state)
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        handleEvent(event)
                    }
                }
                launch {
                    viewModel.isTestingConnection.collect { isTesting ->
                        isTestingConnection = isTesting
                        updateStatusText(currentState)
                    }
                }
            }
        }
    }

    private fun updateUi(state: CameraUiState) {
        val settings = state.settings
        if (settings != null) {
            binding.textStreamUrl.text = settings.streamUrl
            binding.textCommandEndpoint.isVisible = true
            binding.textCommandEndpoint.text = getString(
                R.string.label_command_endpoint,
                settings.commandHost,
                settings.commandPort
            )
            if (lastLoadedStreamUrl != settings.streamUrl) {
                loadStream(settings.streamUrl, force = true)
            }
        } else {
            binding.textStreamUrl.text = getString(R.string.camera_settings_not_configured)
            binding.textCommandEndpoint.isVisible = false
            binding.textCommandEndpoint.text = ""
            lastLoadedStreamUrl = null
        }

        val hasSettings = settings != null
        binding.btnReload.isEnabled = hasSettings

        val controlsEnabled = hasSettings && !state.isExecutingCommand
        binding.btnPhoto.isEnabled = controlsEnabled
        binding.btnRecordStart.isEnabled = controlsEnabled
        binding.btnRecordStop.isEnabled = controlsEnabled
        binding.camSwitch.isEnabled = controlsEnabled
        binding.camSwitch.text = getString(
            if (binding.camSwitch.isChecked) R.string.camera_mode_record else R.string.camera_mode_photo
        )

        updateStatusText(state)
    }

    private fun updateStatusText(state: CameraUiState) {
        val statusText = when {
            state.isExecutingCommand -> getString(R.string.camera_status_command_running)
            isTestingConnection -> getString(R.string.camera_status_testing_connection)
            else -> null
        }
        binding.textStatus.isVisible = statusText != null
        binding.textStatus.text = statusText ?: ""
    }

    private fun handleEvent(event: CameraUiEvent) {
        when (event) {
            is CameraUiEvent.ShowMessage ->
                Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG).show()

            is CameraUiEvent.RequestSettings -> {
                event.reasonMessage?.let {
                    Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                }
                showCameraSettingsDialog()
            }
        }
    }

    private fun showCameraSettingsDialog() {
        if (supportFragmentManager.findFragmentByTag(CameraSettingsDialogFragment.TAG) != null) {
            return
        }
        CameraSettingsDialogFragment.newInstance(currentState.settings)
            .show(supportFragmentManager, CameraSettingsDialogFragment.TAG)
    }

    private fun loadStream(url: String, force: Boolean = false) {
        if (force || lastLoadedStreamUrl != url) {
            binding.webView.loadUrl(url)
            lastLoadedStreamUrl = url
        }
    }

    private fun reloadStream(url: String) {
        if (lastLoadedStreamUrl == null) {
            loadStream(url, force = true)
        } else {
            binding.webView.reload()
        }
    }

    override fun onDestroy() {
        binding.webView.apply {
            loadUrl("about:blank")
            stopLoading()
            clearHistory()
            removeAllViews()
            destroy()
        }
        super.onDestroy()
    }
}
