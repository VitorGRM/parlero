package com.parlero.leitor.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.parlero.leitor.tts.EdgeTtsBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Orquestra a leitura frase-por-frase: toca a frase atual, prepara (prefetch) a próxima
 * enquanto a atual está tocando, e permite navegar/pular/pausar/retomar/pular direto pra
 * uma frase específica. O cache de áudio em si vive no EdgeTtsBridge (por conteúdo),
 * então voltar para uma frase já lida não espera rede de novo.
 */
class ReaderController(
    val sentences: List<String>,
    private val ttsBridge: EdgeTtsBridge,
    private val scope: CoroutineScope,
    initialVoice: String,
    initialRatePercent: Int,
) {
    var voice by mutableStateOf(initialVoice)
    var ratePercent by mutableIntStateOf(initialRatePercent)
    var pitchHz by mutableIntStateOf(0)
    var pauseMs by mutableIntStateOf(300)

    var currentIndex by mutableIntStateOf(0)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var isPaused by mutableStateOf(false)
        private set

    private var playbackJob: Job? = null

    private val rateString get() = if (ratePercent >= 0) "+${ratePercent}%" else "${ratePercent}%"
    private val pitchString get() = if (pitchHz >= 0) "+${pitchHz}Hz" else "${pitchHz}Hz"

    fun playFrom(index: Int) {
        if (index !in sentences.indices) return
        playbackJob?.cancel()
        isPaused = false
        isPlaying = true
        playbackJob = scope.launch {
            var i = index
            while (i < sentences.size) {
                currentIndex = i
                if (i + 1 < sentences.size) prefetch(i + 1)
                val file = ttsBridge.synthesize(sentences[i], voice, rateString, pitchString)
                ttsBridge.play(file)
                if (i + 1 >= sentences.size) break
                delay(pauseMs.toLong())
                i++
            }
            isPlaying = false
            isPaused = false
        }
    }

    fun togglePlayPause() {
        if (!isPlaying) {
            playFrom(currentIndex)
            return
        }
        if (isPaused) {
            ttsBridge.resume()
            isPaused = false
        } else {
            ttsBridge.pause()
            isPaused = true
        }
    }

    fun next() = playFrom((currentIndex + 1).coerceAtMost(sentences.size - 1))

    fun previous() = playFrom((currentIndex - 1).coerceAtLeast(0))

    fun stop() {
        playbackJob?.cancel()
        isPlaying = false
        isPaused = false
    }

    private fun prefetch(index: Int) {
        scope.launch {
            runCatching { ttsBridge.synthesize(sentences[index], voice, rateString, pitchString) }
        }
    }
}
