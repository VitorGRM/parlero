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
import com.parlero.leitor.ai.LocalAiOcr
import com.parlero.leitor.ai.resolveToFilePath
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
fun DocumentScreen(useLocalAi: Boolean, onTextRecognized: (String) -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val localAiOcr = remember { LocalAiOcr(context) }
    val scope = rememberCoroutineScope()

    var isProcessing by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val useLocalAiNow = useLocalAi && localAiOcr.isModelDownloaded()

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
            pages.forEachIndexed { index, page ->
                progressText = if (useLocalAiNow) {
                    "Reconhecendo com IA local: página ${index + 1} de ${pages.size} (pode demorar bastante)..."
                } else {
                    "Reconhecendo texto: página ${index + 1} de ${pages.size}..."
                }
                try {
                    val text = if (useLocalAiNow) {
                        localAiOcr.readTextFromImage(resolveToFilePath(context, page.imageUri))
                    } else {
                        recognizeText(recognizer, InputImage.fromFilePath(context, page.imageUri))
                    }
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
            Text(progressText, style = MaterialTheme.typography.bodyLarge)
        } else {
            Text(
                "Escaneie um documento: a página é detectada, cortada e endireitada automaticamente.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            if (useLocalAi) {
                Text(
                    if (useLocalAiNow) "IA local ativada — o reconhecimento será mais lento." else "IA local ativada, mas o modelo ainda não foi baixado (Configurações). Usando o reconhecimento padrão por enquanto.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
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
