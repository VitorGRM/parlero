package com.parlero.leitor.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.TimeUnit

/**
 * Modo "Documento": foca e tira a foto tocando na tela (como no Seeing AI) ou pelo botão,
 * roda OCR na página inteira e manda o texto reconhecido para a tela de leitura (ReaderScreen).
 */
@Composable
fun DocumentScreen(voice: String, rate: String, onTextRecognized: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

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

    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var controllerRef by remember { mutableStateOf<LifecycleCameraController?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val controller = LifecycleCameraController(ctx)
                    controller.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    controller.imageCaptureMode = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                    controller.bindToLifecycle(lifecycleOwner)
                    previewView.controller = controller
                    controllerRef = controller

                    // Toque na tela: foca naquele ponto (como o Seeing AI) e, logo em
                    // seguida, tira a foto — sem precisar de um botão separado.
                    previewView.setOnTouchListener { view, event ->
                        if (event.action == MotionEvent.ACTION_UP && !isProcessing) {
                            val point = previewView.meteringPointFactory.createPoint(event.x, event.y)
                            val meteringAction = FocusMeteringAction.Builder(
                                point,
                                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                            ).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                            controller.cameraControl?.startFocusAndMetering(meteringAction)

                            isProcessing = true
                            view.postDelayed({
                                capturePage(
                                    controller, ctx, recognizer,
                                    onResult = { text -> isProcessing = false; onTextRecognized(text) },
                                    onError = { msg -> isProcessing = false; errorMessage = msg }
                                )
                            }, 350)
                        }
                        view.performClick()
                        true
                    }

                    previewView
                }
            )
        } else {
            Text(
                "É preciso permitir o uso da câmera para fotografar documentos.",
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }

        if (isProcessing) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Toque em qualquer ponto da tela para focar e fotografar",
                color = androidx.compose.ui.graphics.Color.White,
                style = MaterialTheme.typography.bodySmall
            )
            if (errorMessage != null) {
                Text(errorMessage!!, color = androidx.compose.ui.graphics.Color.Red)
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(4.dp))
            Row(horizontalArrangement = Arrangement.Center) {
                ExtendedFloatingActionButton(
                    text = { Text("Capturar página") },
                    icon = { Icon(Icons.Filled.CameraAlt, contentDescription = null) },
                    onClick = {
                        // Alternativa acessível ao toque livre na tela (não depende de mirar um ponto exato).
                        val controller = controllerRef ?: return@ExtendedFloatingActionButton
                        if (!isProcessing) {
                            isProcessing = true
                            capturePage(
                                controller, context, recognizer,
                                onResult = { text -> isProcessing = false; onTextRecognized(text) },
                                onError = { msg -> isProcessing = false; errorMessage = msg }
                            )
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
private fun capturePage(
    controller: LifecycleCameraController,
    context: Context,
    recognizer: TextRecognizer,
    onResult: (String) -> Unit,
    onError: (String) -> Unit,
) {
    controller.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val mediaImage = image.image
                if (mediaImage == null) {
                    image.close()
                    onError("Não foi possível capturar a imagem.")
                    return
                }
                val inputImage = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
                recognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        if (visionText.text.isBlank()) {
                            onError("Nenhum texto encontrado nessa foto.")
                        } else {
                            onResult(visionText.text)
                        }
                    }
                    .addOnFailureListener { onError("Falha ao reconhecer o texto.") }
                    .addOnCompleteListener { image.close() }
            }

            override fun onError(exception: ImageCaptureException) {
                onError("Falha ao tirar a foto.")
            }
        }
    )
}
