package com.parlero.leitor.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.parlero.leitor.ocr.OcrAnalyzer
import com.parlero.leitor.tts.EdgeTtsBridge
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Modo "Texto Curto": aponta a câmera e o app lê em voz alta assim que o texto estabiliza. */
@Composable
fun CameraScreen(voice: String, rate: String) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val ttsBridge = remember { EdgeTtsBridge(context) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    var candidateText by remember { mutableStateOf("") }
    var lastSpokenText by remember { mutableStateOf("") }
    var isSpeaking by remember { mutableStateOf(false) }

    // Debounce: só fala quando o texto reconhecido fica igual por ~700ms.
    // LaunchedEffect(candidateText) cancela e reinicia sozinho a cada mudança.
    LaunchedEffect(candidateText) {
        if (candidateText.isNotBlank() && candidateText != lastSpokenText) {
            delay(700)
            if (!isSpeaking) {
                lastSpokenText = candidateText
                val vibrator = context.getSystemService<Vibrator>()
                vibrator?.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
                isSpeaking = true
                scope.launch {
                    try {
                        ttsBridge.speak(candidateText, voice, rate)
                    } finally {
                        isSpeaking = false
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val controller = LifecycleCameraController(ctx)
                    controller.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    controller.setImageAnalysisAnalyzer(
                        ContextCompat.getMainExecutor(ctx),
                        OcrAnalyzer { text -> candidateText = text }
                    )
                    controller.bindToLifecycle(lifecycleOwner)
                    previewView.controller = controller
                    previewView
                }
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "É preciso permitir o uso da câmera para ler textos em voz alta.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        if (candidateText.isNotBlank()) {
            Text(
                text = candidateText,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(16.dp)
                    .semantics {
                        contentDescription = "Texto reconhecido: $candidateText"
                        liveRegion = LiveRegionMode.Polite
                    }
            )
        }
    }
}
