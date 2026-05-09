package com.rafper.cam.ui.screen

import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.rafper.cam.camera.CameraController
import com.rafper.cam.camera.CameraMode
import com.rafper.cam.camera.HorizonLockMode
import com.rafper.cam.ui.components.ModeSelector
import com.rafper.cam.ui.components.QuickToggle
import kotlinx.coroutines.delay

@Composable
fun CameraScreen(controller: CameraController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by controller.uiState.collectAsState()

    LaunchedEffect(Unit) {
        controller.startSensors()
        while (true) {
            controller.syncHorizonData()
            delay(16)
        }
    }
    DisposableEffect(Unit) { onDispose { controller.stopSensors() } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = {
            PreviewView(context).also { preview ->
                controller.bind(preview, lifecycleOwner)
                preview.setOnTouchListener { _, event ->
                    controller.tapToFocus(event.x, event.y, preview.width.toFloat(), preview.height.toFloat(), preview.meteringPointFactory)
                    true
                }
            }
        }, modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures {} })

        if (state.showGrid) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width; val h = size.height
                for (i in 1..2) {
                    drawLine(Color.White.copy(0.2f), Offset(w * i / 3f, 0f), Offset(w * i / 3f, h))
                    drawLine(Color.White.copy(0.2f), Offset(0f, h * i / 3f), Offset(w, h * i / 3f))
                }
            }
        }

        state.focusFeedback?.let {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Color(0xFFFFDE59), radius = 28f, center = Offset(it.normalizedX * size.width, it.normalizedY * size.height), style = Stroke(4f))
            }
        }

        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                QuickToggle("AI", state.aiEnabled) { controller.toggleAi() }
                QuickToggle("HDR", state.hdrEnabled) { controller.toggleHdr() }
                QuickToggle("Flash", state.flashMode.name != "OFF") { controller.cycleFlash() }
                QuickToggle("Timer ${state.timerSeconds}s", state.timerSeconds > 0) { controller.cycleTimer() }
                QuickToggle("Ratio", true) { controller.cycleRatio() }
                QuickToggle("Q ${state.recordingQuality.name}", true) { controller.cycleRecordingQuality() }
                QuickToggle("Grid", state.showGrid) { controller.toggleGrid() }
            }

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(260.dp)) {
                    rotate(-state.rollCorrectionDegrees) {
                        drawArc(Color.White.copy(alpha = 0.22f), 200f, 140f, false, style = Stroke(26f))
                    }
                }
                Text("${state.focalLabel} • EXP ${(state.exposureStability * 100).toInt()}% • ${state.profileLabel}", color = Color.White)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    QuickToggle("H-Lock ${state.horizonLockMode.name}", state.horizonLockMode != HorizonLockMode.OFF) { controller.cycleHorizonLock() }
                    QuickToggle("Stab ${state.stabilizationMode.name}", state.stabilizationMode.name != "OFF") { controller.cycleStabilizationMode() }
                    QuickToggle("AF/AE", state.afAeLocked) { controller.unlockAfAe() }
                }
                ModeSelector(listOf("Documents", "Video", "Photo", "Portrait", "Night"), CameraMode.entries.indexOf(state.mode)) {
                    controller.setMode(CameraMode.entries[it])
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                    Text("🖼", color = Color.White)
                    Box(Modifier.size(86.dp).background(if (state.isRecording) Color.Red else Color.White, CircleShape).pointerInput(Unit) { detectTapGestures(onTap = { if (state.mode == CameraMode.VIDEO) controller.toggleRecording() else controller.capturePhoto() }) })
                    Text("🔄", color = Color.White, modifier = Modifier.pointerInput(Unit) { detectTapGestures(onTap = { controller.toggleCamera() }) })
                }
            }
        }
    }
}
