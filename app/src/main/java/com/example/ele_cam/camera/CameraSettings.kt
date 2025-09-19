package com.example.ele_cam.camera

/**
 * Holds the network configuration required to connect to the action camera.
 */
data class CameraSettings(
    val streamUrl: String,
    val commandHost: String,
    val commandPort: Int
) {
    companion object {
        const val DEFAULT_PORT = 80
    }
}
