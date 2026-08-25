package com.parlero.leitor.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Modo "Documento": usa o ML Kit Document Scanner (mesma família de tecnologia por trás de
 * apps tipo CamScanner) para detectar a página, cortar e corrigir a perspectiva automaticamente
 * — a UI de captura é toda do próprio Google Play Services, não é mais feita à mão aqui.
 * O texto reconhecido de cada página é concatenado e mandado para a tela de leitura.
 */
@Composable
fun DocumentScreen(onTextRecognized: (String) -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val scope = rememberCoroutineScope()

    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
        val pages = scanResult?.pages
        if (pages.isNullOrEmpty()) {
            errorMessage = "Nenhuma página capturada."
            return@rememberLauncherForActivityResult
        }
        isProcessing = true
        errorMessage = null
        scope.launch {
            val texts = mutableListOf<String>()
            for (page in pages) {
                try {
                    val inputImage = InputImage.fromFilePath(context, page.imageUri)
                    val text = recognizeText(recognizer, inputImage)
                    if (text.isNotBlank()) texts += text
                } catch (e: Exception) {
                    // Segue pras outras páginas mesmo se uma falhar.
                }
            }
            isProcessing = false
            val fullText = texts.joinToString("\n\n")
            if (fullText.isBlank()) {
                errorMessage = "Nenhum texto encontrado nas páginas escaneadas."
            } else {
                onTextRecognized(fullText)
            }
        }
    }

    fun startScan() {
        val act = activity ?: run {
            errorMessage = "Não foi possível abrir o scanner."
            return
        }
        errorMessage = null
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(10)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(act)
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener {
                errorMessage = "Não foi possível abrir o scanner de documentos."
            }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isProcessing) {
            CircularProgressIndicator()
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))
            Text("Reconhecendo texto...", style = MaterialTheme.typography.bodyLarge)
        } else {
            Text(
                "Escaneie um documento: a página é detectada, cortada e endireitada automaticamente.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            Button(onClick = { startScan() }) {
                Icon(Icons.Filled.DocumentScanner, contentDescription = null)
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(4.dp))
                Text("Escanear documento")
            }
            if (errorMessage != null) {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))
                Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private suspend fun recognizeText(recognizer: TextRecognizer, image: InputImage): String =
    suspendCancellableCoroutine { cont ->
        recognizer.process(image)
            .addOnSuccessListener { if (cont.isActive) cont.resume(it.text) }
            .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
    }
