package com.parlero.leitor.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.parlero.leitor.BuildConfig
import com.parlero.leitor.tts.EdgeTtsBridge
import com.parlero.leitor.tts.EdgeVoice
import com.parlero.leitor.update.UpdateChecker
import com.parlero.leitor.update.UpdateInfo
import kotlinx.coroutines.launch

private sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val progress: Int) : UpdateState
    data class Error(val message: String) : UpdateState
}

@Composable
fun SettingsScreen(
    selectedVoice: String,
    speechRatePercent: Int,
    onVoiceSelected: (String) -> Unit,
    onRateChanged: (Int) -> Unit,
) {
    val context = LocalContext.current
    val ttsBridge = remember { EdgeTtsBridge(context) }
    val updateChecker = remember { UpdateChecker(context) }
    val scope = rememberCoroutineScope()

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

    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }

    fun startDownloadAndInstall(info: UpdateInfo) {
        scope.launch {
            try {
                updateState = UpdateState.Downloading(0)
                val file = updateChecker.downloadApk(info.downloadUrl) { progress ->
                    updateState = UpdateState.Downloading(progress)
                }
                updateChecker.installApk(file)
                updateState = UpdateState.Idle
            } catch (e: Exception) {
                updateState = UpdateState.Error("Falha ao baixar a atualização. Verifique sua internet.")
            }
        }
    }

    val installPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val pending = updateState
        if (updateChecker.canRequestPackageInstalls() && pending is UpdateState.Available) {
            startDownloadAndInstall(pending.info)
        } else {
            updateState = UpdateState.Error("É preciso permitir a instalação de apps desconhecidos para atualizar.")
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
            else -> LazyColumn(modifier = Modifier.weight(1f)) {
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

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        Text("Atualizações", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Versão instalada: ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(8.dp))

        when (val state = updateState) {
            is UpdateState.Idle -> Button(onClick = {
                updateState = UpdateState.Checking
                scope.launch {
                    updateState = try {
                        updateChecker.checkForUpdate()?.let { UpdateState.Available(it) } ?: UpdateState.UpToDate
                    } catch (e: Exception) {
                        UpdateState.Error("Não foi possível verificar atualizações. Verifique sua internet.")
                    }
                }
            }) { Text("Verificar atualizações") }

            is UpdateState.Checking -> CircularProgressIndicator()

            is UpdateState.UpToDate -> Text("Você já está na versão mais recente.")

            is UpdateState.Available -> Column {
                Text("Nova versão disponível: ${state.info.version}")
                if (state.info.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(state.info.notes, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    if (updateChecker.canRequestPackageInstalls()) {
                        startDownloadAndInstall(state.info)
                    } else {
                        installPermissionLauncher.launch(updateChecker.requestInstallPermissionIntent())
                    }
                }) { Text("Baixar e instalar") }
            }

            is UpdateState.Downloading -> Column {
                Text("Baixando atualização... ${state.progress}%")
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { state.progress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            is UpdateState.Error -> Column {
                Text(state.message)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { updateState = UpdateState.Idle }) { Text("Tentar de novo") }
            }
        }
    }
}
