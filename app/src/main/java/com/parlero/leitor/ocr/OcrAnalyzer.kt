package com.parlero.leitor.ocr

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Analisador de frames da câmera que roda OCR on-device (ML Kit) continuamente.
 * Descarta frames enquanto um reconhecimento anterior ainda está em andamento,
 * já que ML Kit é mais rápido que a taxa de frames mas não instantâneo.
 */
class OcrAnalyzer(private val onTextDetected: (String) -> Unit) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var busy = false

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null || busy) {
            imageProxy.close()
            return
        }
        busy = true
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val text = visionText.text.trim()
                if (text.isNotEmpty()) onTextDetected(text)
            }
            .addOnCompleteListener {
                busy = false
                imageProxy.close()
            }
    }
}
