package com.rafper.cam

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.rafper.cam.camera.CameraController
import com.rafper.cam.ui.screen.CameraScreen
import com.rafper.cam.ui.theme.RafPerCamTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RafPerCamTheme {
                var granted by remember { mutableStateOf(false) }
                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    granted = permissions[Manifest.permission.CAMERA] == true
                }

                LaunchedEffect(Unit) {
                    launcher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                }

                if (granted) {
                    val controller = remember { CameraController(this@MainActivity) }
                    CameraScreen(controller = controller)
                }
            }
        }
    }
}
