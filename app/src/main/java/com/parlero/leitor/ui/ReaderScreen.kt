package com.parlero.leitor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parlero.leitor.reader.ReaderController
import com.parlero.leitor.reader.segmentSentences
import com.parlero.leitor.tts.EdgeTtsBridge
import com.parlero.leitor.tts.EdgeVoice
import com.parlero.leitor.tts.VoiceCatalog
import androidx.compose.runtime.DisposableEffect

@Composable
fun ReaderScreen(
    text: String,
    initialVoice: String,
    initialRatePercent: Int,
    onBack: () -> Unit,
    onVoiceChanged: (String) -> Unit,
    onRateChanged: (Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ttsBridge = remember { EdgeTtsBridge(context) }
    val sentences = remember(text) { segmentSentences(text) }
    val controller = remember(sentences) {
        ReaderController(sentences, ttsBridge, scope, initialVoice, initialRatePercent)
    }

    DisposableEffect(controller) {
        onDispose { controller.stop() }
    }

    // Trocar a voz/velocidade aqui também atualiza o padrão do app (Configurações),
    // não fica só valendo pra essa sessão de leitura.
    LaunchedEffect(controller.voice) { onVoiceChanged(controller.voice) }
    LaunchedEffect(controller.ratePercent) { onRateChanged(controller.ratePercent) }

    LaunchedEffect(controller) {
        if (sentences.isNotEmpty()) controller.playFrom(0)
    }

    val listState = rememberLazyListState()
    LaunchedEffect(controller.currentIndex) {
        listState.animateScrollToItem(controller.currentIndex)
    }

    var showSettings by remember { mutableStateOf(false) }
    val allVoices = remember { VoiceCatalog.loadAll(context) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar para a câmera")
            }
            Text(
                "Leitura (${controller.currentIndex + 1}/${sentences.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showSettings = !showSettings }) {
                Icon(Icons.Filled.Tune, contentDescription = "Ajustes de leitura")
            }
        }

        if (showSettings) {
            ReaderSettingsPanel(controller = controller, allVoices = allVoices)
            HorizontalDivider()
        }

        if (sentences.isEmpty()) {
            Text(
                "Nenhum texto reconhecido para ler.",
                modifier = Modifier.fillMaxSize().padding(24.dp)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
            ) {
                itemsIndexed(sentences) { index, sentence ->
                    val isCurrent = index == controller.currentIndex
                    Surface(
                        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { controller.playFrom(index) }
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            sentence,
                            fontSize = 20.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { controller.previous() }) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Frase anterior")
            }
            IconButton(onClick = { controller.togglePlayPause() }) {
                Icon(
                    if (controller.isPlaying && !controller.isPaused) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (controller.isPlaying && !controller.isPaused) "Pausar" else "Tocar"
                )
            }
            IconButton(onClick = { controller.next() }) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Próxima frase")
            }
        }
    }
}

@Composable
private fun ReaderSettingsPanel(controller: ReaderController, allVoices: List<EdgeVoice>) {
    var voiceQuery by remember { mutableStateOf("") }
    val filteredVoices = remember(allVoices, voiceQuery) {
        if (voiceQuery.isBlank()) allVoices
        else allVoices.filter {
            it.locale.contains(voiceQuery, ignoreCase = true) ||
                it.shortName.contains(voiceQuery, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Voz atual: ${controller.voice}", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = voiceQuery,
            onValueChange = { voiceQuery = it },
            label = { Text("Buscar voz (idioma ou nome)") },
            modifier = Modifier.fillMaxWidth()
        )
        LazyColumn(modifier = Modifier.fillMaxWidth().height(160.dp)) {
            items(filteredVoices) { v ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { controller.voice = v.shortName }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = v.shortName == controller.voice,
                        onClick = { controller.voice = v.shortName }
                    )
                    Text("${v.locale} · ${v.gender} · ${v.shortName}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("Velocidade: ${controller.ratePercent}%", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = controller.ratePercent.toFloat(),
            onValueChange = { controller.ratePercent = it.toInt() },
            valueRange = -50f..50f
        )

        Text("Tom: ${controller.pitchHz}Hz", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = controller.pitchHz.toFloat(),
            onValueChange = { controller.pitchHz = it.toInt() },
            valueRange = -50f..50f
        )

        Text("Pausa entre frases: ${controller.pauseMs}ms", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = controller.pauseMs.toFloat(),
            onValueChange = { controller.pauseMs = it.toInt() },
            valueRange = 0f..2000f
        )
    }
}
