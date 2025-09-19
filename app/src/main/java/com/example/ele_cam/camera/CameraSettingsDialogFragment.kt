package com.example.ele_cam.camera

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.ele_cam.R
import com.example.ele_cam.databinding.DialogCameraSettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class CameraSettingsDialogFragment : DialogFragment() {

    private var _binding: DialogCameraSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CameraViewModel by activityViewModels()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogCameraSettingsBinding.inflate(LayoutInflater.from(context))
        initializeFields()
        setupInputListeners()

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.camera_settings_title)
            .setView(binding.root)
            .setPositiveButton(R.string.action_save, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            updateTestingState(viewModel.isTestingConnection.value)
            binding.buttonTestConnection.setOnClickListener { onTestConnectionClicked() }
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                val settings = readSettings() ?: return@setOnClickListener
                viewModel.updateCameraSettings(settings)
                dismiss()
            }
        }

        return dialog
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isTestingConnection.collect { isTesting ->
                        updateTestingState(isTesting)
                    }
                }
                launch {
                    viewModel.testResults.collect { result ->
                        displayTestResult(result)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun initializeFields() {
        val args = arguments
        val streamUrl = args?.getString(ARG_STREAM_URL).orEmpty()
        val host = args?.getString(ARG_COMMAND_HOST).orEmpty()
        val port = args?.getInt(ARG_COMMAND_PORT, CameraSettings.DEFAULT_PORT)

        if (streamUrl.isNotBlank()) {
            binding.inputStreamUrl.setText(streamUrl)
        }
        if (host.isNotBlank()) {
            binding.inputCommandHost.setText(host)
        }
        binding.inputCommandPort.setText(port.toString())
    }

    private fun setupInputListeners() {
        binding.inputStreamUrl.doAfterTextChanged {
            binding.streamUrlLayout.error = null
            clearTestResult()
        }
        binding.inputCommandHost.doAfterTextChanged {
            binding.commandHostLayout.error = null
            clearTestResult()
        }
        binding.inputCommandPort.doAfterTextChanged {
            binding.commandPortLayout.error = null
            clearTestResult()
        }
    }

    private fun onTestConnectionClicked() {
        val settings = readSettings() ?: return
        clearTestResult()
        viewModel.testCameraConnection(settings)
    }

    private fun readSettings(): CameraSettings? {
        val streamUrl = binding.inputStreamUrl.text?.toString()?.trim().orEmpty()
        val host = binding.inputCommandHost.text?.toString()?.trim().orEmpty()
        val portText = binding.inputCommandPort.text?.toString()?.trim().orEmpty()

        var isValid = true

        if (streamUrl.isBlank()) {
            binding.streamUrlLayout.error = getString(R.string.error_stream_url_required)
            isValid = false
        }

        if (host.isBlank()) {
            binding.commandHostLayout.error = getString(R.string.error_command_host_required)
            isValid = false
        }

        val port = portText.toIntOrNull()
        if (port == null || port !in 1..65535) {
            binding.commandPortLayout.error = getString(R.string.error_command_port_invalid)
            isValid = false
        }

        return if (isValid) {
            CameraSettings(streamUrl, host, port!!)
        } else {
            null
        }
    }

    private fun updateTestingState(isTesting: Boolean) {
        binding.progressTestConnection.isVisible = isTesting
        binding.buttonTestConnection.isEnabled = !isTesting
        (dialog as? AlertDialog)?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = !isTesting
    }

    private fun displayTestResult(result: CameraCommandResult) {
        binding.textTestResult.isVisible = true
        binding.textTestResult.text = result.message
        val colorRes = if (result.success) {
            android.R.color.holo_green_dark
        } else {
            android.R.color.holo_red_dark
        }
        binding.textTestResult.setTextColor(ContextCompat.getColor(requireContext(), colorRes))
    }

    private fun clearTestResult() {
        binding.textTestResult.isVisible = false
    }

    companion object {
        private const val ARG_STREAM_URL = "arg_stream_url"
        private const val ARG_COMMAND_HOST = "arg_command_host"
        private const val ARG_COMMAND_PORT = "arg_command_port"

        const val TAG = "CameraSettingsDialog"

        fun newInstance(settings: CameraSettings?): CameraSettingsDialogFragment {
            val fragment = CameraSettingsDialogFragment()
            fragment.arguments = Bundle().apply {
                putString(ARG_STREAM_URL, settings?.streamUrl)
                putString(ARG_COMMAND_HOST, settings?.commandHost)
                putInt(ARG_COMMAND_PORT, settings?.commandPort ?: CameraSettings.DEFAULT_PORT)
            }
            return fragment
        }
    }
}
