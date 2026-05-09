package com.rafper.cam.camera

enum class CameraMode { DOCUMENTS, VIDEO, PHOTO, PORTRAIT, NIGHT }
enum class HorizonLockMode { OFF, ASSIST, FULL_LOCK }
enum class StabilizationMode { OFF, STANDARD, BRUTAL, HORIZON_LOCK, HORIZON_LOCK_BRUTAL }
enum class FlashMode { OFF, ON, AUTO }
enum class AspectRatioOption { RATIO_4_3, RATIO_16_9, FULL }

data class FocusUiFeedback(
    val normalizedX: Float,
    val normalizedY: Float,
    val locked: Boolean
)

data class CameraUiState(
    val mode: CameraMode = CameraMode.PHOTO,
    val aiEnabled: Boolean = false,
    val hdrEnabled: Boolean = true,
    val flashMode: FlashMode = FlashMode.OFF,
    val timerSeconds: Int = 0,
    val showGrid: Boolean = true,
    val horizonLockMode: HorizonLockMode = HorizonLockMode.OFF,
    val stabilizationMode: StabilizationMode = StabilizationMode.BRUTAL,
    val aspectRatio: AspectRatioOption = AspectRatioOption.FULL,
    val isRearCamera: Boolean = true,
    val zoomRatio: Float = 1f,
    val minZoom: Float = 0.6f,
    val maxZoom: Float = 8f,
    val focalLabel: String = "1x",
    val focusFeedback: FocusUiFeedback? = null,
    val rollCorrectionDegrees: Float = 0f,
    val horizonStrength: Float = 0f,
    val exposureStability: Float = 1f,
    val stabilizationStrength: Float = 1f,
    val isRecording: Boolean = false,
    val recordingQuality: RecordingQuality = RecordingQuality.BALANCED,
    val profileLabel: String = "Balanced",
    val afAeLocked: Boolean = false
)
