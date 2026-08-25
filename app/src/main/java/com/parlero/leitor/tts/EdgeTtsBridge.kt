package com.parlero.leitor.tts

import android.content.Context
import android.media.MediaPlayer
import com.chaquo.python.Python
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class EdgeVoice(val shortName: String, val locale: String, val gender: String)

/**
 * Fala texto usando as vozes neurais da Microsoft via a biblioteca Python `edge-tts`.
 *
 * O áudio sintetizado é salvo num cache em disco endereçado por conteúdo (hash de
 * texto+voz+velocidade+tom), então sintetizar a mesma frase de novo (ex.: ao voltar
 * uma frase no leitor, ou um prefetch que chegou atrasado) é instantâneo.
 */
class EdgeTtsBridge(private val context: Context) {

    private val python by lazy { Python.getInstance() }
    private val bridgeModule by lazy { python.getModule("tts_bridge") }
    private val cacheDir by lazy { File(context.cacheDir, "tts_cache").apply { mkdirs() } }

    private val bridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = ConcurrentHashMap<String, Deferred<File>>()

    private var activePlayer: MediaPlayer? = null

    /** Sintetiza (ou reaproveita do cache) e retorna o arquivo mp3, sem tocar. */
    suspend fun synthesize(text: String, voice: String, rate: String, pitch: String = "+0Hz"): File {
        val outFile = File(cacheDir, cacheKey(text, voice, rate, pitch) + ".mp3")
        if (outFile.exists()) return outFile

        val deferred = inFlight.computeIfAbsent(cacheKeyRaw(text, voice, rate, pitch)) {
            bridgeScope.async {
                bridgeModule.callAttr("synthesize", text, voice, rate, pitch, outFile.absolutePath)
                outFile
            }
        }
        return try {
            deferred.await()
        } finally {
            inFlight.remove(cacheKeyRaw(text, voice, rate, pitch), deferred)
        }
    }

    /** Toca um arquivo já sintetizado e suspende até terminar (ou ser cancelado/pausado→retomado). */
    suspend fun play(file: File) = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine<Unit> { cont ->
            val player = MediaPlayer()
            activePlayer = player
            try {
                player.setDataSource(file.absolutePath)
                player.setOnPreparedListener { it.start() }
                player.setOnCompletionListener {
                    if (activePlayer === it) activePlayer = null
                    it.release()
                    if (cont.isActive) cont.resume(Unit)
                }
                player.setOnErrorListener { mp, _, _ ->
                    if (activePlayer === mp) activePlayer = null
                    mp.release()
                    if (cont.isActive) cont.resumeWithException(IllegalStateException("Falha ao tocar áudio"))
                    true
                }
                cont.invokeOnCancellation {
                    if (activePlayer === player) activePlayer = null
                    player.release()
                }
                player.prepareAsync()
            } catch (e: Exception) {
                if (activePlayer === player) activePlayer = null
                player.release()
                if (cont.isActive) cont.resumeWithException(e)
            }
        }
    }

    /** Sintetiza e toca em sequência (usado pelo modo Texto Curto). */
    suspend fun speak(text: String, voice: String, rate: String = "+0%", pitch: String = "+0Hz") {
        if (text.isBlank()) return
        play(synthesize(text, voice, rate, pitch))
    }

    fun pause() {
        activePlayer?.let { if (it.isPlaying) it.pause() }
    }

    fun resume() {
        activePlayer?.let { if (!it.isPlaying) it.start() }
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

    private fun cacheKeyRaw(text: String, voice: String, rate: String, pitch: String) =
        "$voice|$rate|$pitch|$text"

    private fun cacheKey(text: String, voice: String, rate: String, pitch: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(cacheKeyRaw(text, voice, rate, pitch).toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
