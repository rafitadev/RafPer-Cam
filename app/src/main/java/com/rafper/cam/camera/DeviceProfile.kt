package com.rafper.cam.camera

import android.hardware.camera2.CameraCharacteristics

data class DeviceProfile(
    val label: String,
    val targetFpsRange: Pair<Int, Int>,
    val defaultQuality: RecordingQuality,
    val stabilizationAggressiveness: Float
)

enum class RecordingQuality { ECONOMY, BALANCED, PRO, ULTRA }

fun inferDeviceProfile(characteristics: CameraCharacteristics?): DeviceProfile {
    val level = characteristics?.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
    return when (level) {
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> DeviceProfile("Ultra", 30 to 60, RecordingQuality.ULTRA, 1f)
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> DeviceProfile("Pro", 30 to 60, RecordingQuality.PRO, 0.9f)
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> DeviceProfile("Balanced", 24 to 30, RecordingQuality.BALANCED, 0.75f)
        else -> DeviceProfile("Economy", 24 to 30, RecordingQuality.ECONOMY, 0.6f)
    }
}
