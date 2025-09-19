package com.example.ele_cam.camera

/**
 * Result returned from executing a command against the action camera.
 */
data class CameraCommandResult(
    val success: Boolean,
    val message: String? = null
)
