package com.rafper.cam.camera

import android.content.ContentValues
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.provider.MediaStore
import android.util.Range
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.rafper.cam.sensors.HorizonLockEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class CameraController(private val context: Context) {
    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private var camera: Camera? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var imageCapture: ImageCapture? = null
    private var recording: Recording? = null
    private var boundPreview: PreviewView? = null
    private var boundLifecycle: LifecycleOwner? = null
    private val horizonEngine = HorizonLockEngine(context)
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    fun bind(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        boundPreview = previewView
        boundLifecycle = lifecycleOwner
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val characteristics = getCurrentCharacteristics()
            val profile = inferDeviceProfile(characteristics)

            val previewBuilder = Preview.Builder()
            val captureBuilder = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            applyCamera2Tuning(previewBuilder, characteristics, profile)
            applyCamera2Tuning(captureBuilder, characteristics, profile)

            val preview = previewBuilder.build().also { it.surfaceProvider = previewView.surfaceProvider }
            imageCapture = captureBuilder.build()
            videoCapture = VideoCapture.withOutput(createRecorderForProfile(profile))
            provider.unbindAll()
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                if (_uiState.value.isRearCamera) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA,
                preview,
                imageCapture,
                videoCapture
            )
            _uiState.value = _uiState.value.copy(profileLabel = profile.label, recordingQuality = profile.defaultQuality)
            applyCameraQualityControls()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun createRecorderForProfile(profile: DeviceProfile): Recorder {
        val quality = when (profile.defaultQuality) {
            RecordingQuality.ECONOMY -> Quality.SD
            RecordingQuality.BALANCED -> Quality.HD
            RecordingQuality.PRO -> Quality.FHD
            RecordingQuality.ULTRA -> Quality.UHD
        }
        return Recorder.Builder().setExecutor(ContextCompat.getMainExecutor(context))
            .setQualitySelector(QualitySelector.from(quality, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
            .build()
    }

    private fun applyCamera2Tuning(builder: Any, characteristics: CameraCharacteristics?, profile: DeviceProfile) {
        val extender = when (builder) {
            is Preview.Builder -> Camera2Interop.Extender(builder)
            is ImageCapture.Builder -> Camera2Interop.Extender(builder)
            else -> return
        }
        extender.setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(profile.targetFpsRange.first, profile.targetFpsRange.second))
        extender.setCaptureRequestOption(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO)
        extender.setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_FAST)
        extender.setCaptureRequestOption(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_FAST)
        if (characteristics?.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)?.isNotEmpty() == true) {
            extender.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
        }
    }

    private fun getCurrentCharacteristics(): CameraCharacteristics? {
        return runCatching {
            val id = if (_uiState.value.isRearCamera) cameraManager.cameraIdList.first() else cameraManager.cameraIdList.last()
            cameraManager.getCameraCharacteristics(id)
        }.getOrNull()
    }

    private fun applyCameraQualityControls() {
        val control = camera?.cameraControl ?: return
        val info = camera?.cameraInfo ?: return
        val state = _uiState.value
        val stabilization = when (state.stabilizationMode) {
            StabilizationMode.OFF -> androidx.camera.core.VideoStabilizationMode.OFF
            StabilizationMode.STANDARD -> androidx.camera.core.VideoStabilizationMode.ON
            StabilizationMode.BRUTAL, StabilizationMode.HORIZON_LOCK, StabilizationMode.HORIZON_LOCK_BRUTAL -> androidx.camera.core.VideoStabilizationMode.HIGH_QUALITY
        }
        control.setVideoStabilizationMode(stabilization)
        control.setLinearZoom(((state.zoomRatio - state.minZoom) / (state.maxZoom - state.minZoom)).coerceIn(0f, 1f))
        if (state.flashMode != FlashMode.OFF && info.hasFlashUnit()) control.enableTorch(state.flashMode == FlashMode.ON) else control.enableTorch(false)
    }

    fun updateZoom(zoom: Float) { _uiState.value = _uiState.value.copy(zoomRatio = zoom.coerceIn(_uiState.value.minZoom, _uiState.value.maxZoom), focalLabel = "${"%.1f".format(zoom)}x"); applyCameraQualityControls() }
    fun toggleCamera() { _uiState.value = _uiState.value.copy(isRearCamera = !_uiState.value.isRearCamera); boundPreview?.let { pv -> boundLifecycle?.let { lc -> bind(pv, lc) } } }

    fun tapToFocus(x: Float, y: Float, width: Float, height: Float, meterFactory: PreviewView.MeteringPointFactory) {
        val point = meterFactory.createPoint(x, y)
        camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).setAutoCancelDuration(2, TimeUnit.SECONDS).build())
        _uiState.value = _uiState.value.copy(focusFeedback = FocusUiFeedback((x / width).coerceIn(0f, 1f), (y / height).coerceIn(0f, 1f), true), afAeLocked = true)
    }

    fun unlockAfAe() { _uiState.value = _uiState.value.copy(afAeLocked = false) }
    fun cycleStabilizationMode() { val e=StabilizationMode.entries; _uiState.value=_uiState.value.copy(stabilizationMode=e[(_uiState.value.stabilizationMode.ordinal+1)%e.size]); applyCameraQualityControls() }
    fun cycleHorizonLock() { val next=when(_uiState.value.horizonLockMode){HorizonLockMode.OFF->HorizonLockMode.ASSIST;HorizonLockMode.ASSIST->HorizonLockMode.FULL_LOCK;HorizonLockMode.FULL_LOCK->HorizonLockMode.OFF}; _uiState.value=_uiState.value.copy(horizonLockMode=next); horizonEngine.setMode(next) }
    fun toggleAi() { _uiState.value = _uiState.value.copy(aiEnabled = !_uiState.value.aiEnabled) }
    fun toggleHdr() { _uiState.value = _uiState.value.copy(hdrEnabled = !_uiState.value.hdrEnabled) }
    fun toggleGrid() { _uiState.value = _uiState.value.copy(showGrid = !_uiState.value.showGrid) }
    fun cycleFlash() { _uiState.value = _uiState.value.copy(flashMode = FlashMode.entries[(_uiState.value.flashMode.ordinal + 1) % FlashMode.entries.size]); applyCameraQualityControls() }
    fun cycleTimer() { _uiState.value = _uiState.value.copy(timerSeconds = listOf(0, 3, 5, 10)[(listOf(0,3,5,10).indexOf(_uiState.value.timerSeconds)+1)%4]) }
    fun cycleRatio() { _uiState.value = _uiState.value.copy(aspectRatio = AspectRatioOption.entries[(_uiState.value.aspectRatio.ordinal + 1) % AspectRatioOption.entries.size]) }
    fun cycleRecordingQuality() { val q=RecordingQuality.entries; _uiState.value=_uiState.value.copy(recordingQuality=q[(_uiState.value.recordingQuality.ordinal+1)%q.size]) }
    fun setMode(mode: CameraMode) { _uiState.value = _uiState.value.copy(mode = mode) }

    fun startSensors() = horizonEngine.start(_uiState.value.horizonLockMode)
    fun stopSensors() = horizonEngine.stop()
    fun syncHorizonData() { val out = horizonEngine.output.value; _uiState.value = _uiState.value.copy(rollCorrectionDegrees = out.rollDegrees, horizonStrength = out.horizonStrength, exposureStability = (1f - (out.microJitter / 15f)).coerceIn(0f, 1f)) }

    fun capturePhoto(onError: (ImageCaptureException) -> Unit = {}) {
        val name = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(System.currentTimeMillis())
        val options = ImageCapture.OutputFileOptions.Builder(context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, name); put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg") }).build()
        imageCapture?.takePicture(options, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback { override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) = Unit; override fun onError(exception: ImageCaptureException) = onError(exception) })
    }

    fun toggleRecording() {
        if (recording != null) { recording?.stop(); recording = null; _uiState.value = _uiState.value.copy(isRecording = false); return }
        val name = "capture-${System.currentTimeMillis()}"
        val mediaStoreOptions = MediaStoreOutputOptions.Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, name); put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4") }).build()
        recording = videoCapture?.output?.prepareRecording(context, mediaStoreOptions)?.withAudioEnabled()?.start(ContextCompat.getMainExecutor(context)) { if (it is VideoRecordEvent.Finalize) { recording = null; _uiState.value = _uiState.value.copy(isRecording = false) } }
        _uiState.value = _uiState.value.copy(isRecording = true)
    }
}
