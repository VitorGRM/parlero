package com.parlero.leitor.ai

import android.content.Context
import android.net.Uri
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val MODEL_URL =
    "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
private const val MODEL_FILE_NAME = "gemma-4-E2B-it.litertlm"

private const val OCR_PROMPT =
    "Transcreva integralmente todo o texto visível nesta imagem, exatamente como está " +
        "escrito, na ordem de leitura natural. Responda apenas com o texto transcrito, " +
        "sem comentários, explicações ou formatação adicional."

/**
 * IA local (Gemma 4 E2B, via LiteRT-LM) pra reconhecer texto em imagens sem depender de
 * nenhuma API — o modelo roda inteiramente no aparelho depois de baixado uma vez (~2.4GB,
 * apache-2.0, sem precisar de login/token). É bem mais pesado e lento que o ML Kit,
 * então isso é sempre opt-in (ver Configurações).
 */
class LocalAiOcr(private val context: Context) {

    private val modelFile: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, MODEL_FILE_NAME)

    private var engine: Engine? = null

    fun isModelDownloaded(): Boolean = modelFile.exists() && modelFile.length() > 0

    fun modelSizeMb(): Long = if (modelFile.exists()) modelFile.length() / (1024 * 1024) else 0

    suspend fun downloadModel(onProgress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        val tmpFile = File(modelFile.parentFile, "$MODEL_FILE_NAME.part")
        val connection = URL(MODEL_URL).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 15_000
        connection.connect()
        val total = connection.contentLengthLong
        try {
            connection.inputStream.use { input ->
                tmpFile.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    var read = input.read(buffer)
                    while (read >= 0) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) onProgress((downloaded * 100 / total).toInt())
                        read = input.read(buffer)
                    }
                }
            }
            tmpFile.renameTo(modelFile)
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun ensureEngine(): Engine = withContext(Dispatchers.IO) {
        engine ?: run {
            val config = EngineConfig(
                modelPath = modelFile.absolutePath,
                // CPU puro: o Galaxy A32 não tem GPU/driver confiável pra acelerar isso.
                backend = Backend.CPU(),
                visionBackend = Backend.CPU(),
                cacheDir = context.cacheDir.path,
            )
            val newEngine = Engine(config)
            newEngine.initialize()
            engine = newEngine
            newEngine
        }
    }

    /** Roda a IA local sobre uma imagem (path de arquivo, não content://) e retorna o texto lido. */
    suspend fun readTextFromImage(imagePath: String): String = withContext(Dispatchers.IO) {
        val eng = ensureEngine()
        eng.createConversation().use { conversation ->
            val response = conversation.sendMessage(
                Contents.of(
                    Content.ImageFile(imagePath),
                    Content.Text(OCR_PROMPT),
                )
            )
            response.contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString("") { it.text }
                .trim()
        }
    }

    fun close() {
        engine?.close()
        engine = null
    }

    fun deleteModel(): Boolean {
        close()
        return modelFile.delete()
    }
}

/** Resolve uma Uri (do scanner de documentos) para um path de arquivo de verdade. */
fun resolveToFilePath(context: Context, uri: Uri): String {
    if (uri.scheme == "file") return uri.path!!
    val tmp = File(context.cacheDir, "page_${System.currentTimeMillis()}.jpg")
    context.contentResolver.openInputStream(uri)?.use { input ->
        tmp.outputStream().use { output -> input.copyTo(output) }
    }
    return tmp.absolutePath
}
