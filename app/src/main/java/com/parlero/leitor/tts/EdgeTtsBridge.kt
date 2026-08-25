package com.parlero.leitor.tts

import android.content.Context
import android.media.MediaPlayer
import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class EdgeVoice(val shortName: String, val locale: String, val gender: String)

/** Fala texto usando as vozes neurais da Microsoft via a biblioteca Python `edge-tts`. */
class EdgeTtsBridge(private val context: Context) {

    private val python by lazy { Python.getInstance() }
    private val bridgeModule by lazy { python.getModule("tts_bridge") }

    suspend fun speak(text: String, voice: String, rate: String = "+0%") {
        if (text.isBlank()) return
        val outFile = File(context.cacheDir, "tts_${UUID.randomUUID()}.mp3")
        withContext(Dispatchers.IO) {
            bridgeModule.callAttr("synthesize", text, voice, rate, outFile.absolutePath)
        }
        playAndAwaitCompletion(outFile)
    }

    private suspend fun playAndAwaitCompletion(file: File) = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine<Unit> { cont ->
            val player = MediaPlayer()
            try {
                player.setDataSource(file.absolutePath)
                player.setOnPreparedListener { it.start() }
                player.setOnCompletionListener {
                    it.release()
                    file.delete()
                    if (cont.isActive) cont.resume(Unit)
                }
                player.setOnErrorListener { mp, _, _ ->
                    mp.release()
                    file.delete()
                    if (cont.isActive) cont.resumeWithException(IllegalStateException("Falha ao tocar áudio"))
                    true
                }
                cont.invokeOnCancellation { player.release() }
                player.prepareAsync()
            } catch (e: Exception) {
                player.release()
                file.delete()
                if (cont.isActive) cont.resumeWithException(e)
            }
        }
    }

    /** Lista vozes Edge TTS, por padrão filtradas para português e inglês. */
    suspend fun listVoices(
        localePrefixes: List<String> = listOf("pt-BR", "pt-PT", "en-US")
    ): List<EdgeVoice> = withContext(Dispatchers.IO) {
        val result = bridgeModule.callAttr("list_voices", localePrefixes.toTypedArray())
        result.asList().mapNotNull { item ->
            val parts = item.toString().split("|")
            if (parts.size == 3) EdgeVoice(parts[0], parts[1], parts[2]) else null
        }
    }
}
