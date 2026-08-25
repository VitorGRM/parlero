package com.parlero.leitor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
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
import com.parlero.leitor.tts.EdgeTtsBridge
import com.parlero.leitor.tts.EdgeVoice

@Composable
fun SettingsScreen(
    selectedVoice: String,
    speechRatePercent: Int,
    onVoiceSelected: (String) -> Unit,
    onRateChanged: (Int) -> Unit,
) {
    val context = LocalContext.current
    val ttsBridge = remember { EdgeTtsBridge(context) }

    var voices by remember { mutableStateOf<List<EdgeVoice>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            voices = ttsBridge.listVoices()
        } catch (e: Exception) {
            error = "Não foi possível carregar as vozes. Verifique sua conexão com a internet."
        } finally {
            loading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Configurações", style = MaterialTheme.typography.headlineSmall)

        Spacer(modifier = Modifier.height(24.dp))
        Text("Velocidade da fala: ${speechRatePercent}%", style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = speechRatePercent.toFloat(),
            onValueChange = { onRateChanged(it.toInt()) },
            valueRange = -50f..50f,
            steps = 19
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text("Voz", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))

        when {
            loading -> CircularProgressIndicator()
            error != null -> Text(error!!)
            voices.isEmpty() -> Text("Nenhuma voz encontrada.")
            else -> LazyColumn {
                items(voices) { voice ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = voice.shortName == selectedVoice,
                            onClick = { onVoiceSelected(voice.shortName) }
                        )
                        Column {
                            Text("${voice.locale} · ${voice.gender}")
                            Text(voice.shortName, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
