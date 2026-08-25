package com.parlero.leitor

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.parlero.leitor.ui.CameraScreen
import com.parlero.leitor.ui.DocumentScreen
import com.parlero.leitor.ui.ReaderScreen
import com.parlero.leitor.ui.SettingsScreen

private const val PREFS_NAME = "parlero_prefs"
private const val KEY_VOICE = "voice"
private const val KEY_RATE = "rate"

// Voz padrão em português do Brasil; o usuário pode trocar em Configurações.
private const val DEFAULT_VOICE = "pt-BR-FranciscaNeural"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                ParleroRoot()
            }
        }
    }
}

private sealed class Screen {
    data object Leitura : Screen()
    data object Documento : Screen()
    data class Reader(val text: String) : Screen()
    data object Configuracoes : Screen()
}

@Composable
private fun ParleroRoot() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    var selectedVoice by remember {
        mutableStateOf(prefs.getString(KEY_VOICE, DEFAULT_VOICE) ?: DEFAULT_VOICE)
    }
    var ratePercent by remember { mutableStateOf(prefs.getInt(KEY_RATE, 0)) }
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Leitura) }

    val rateString = if (ratePercent >= 0) "+${ratePercent}%" else "${ratePercent}%"

    Scaffold(
        bottomBar = {
            if (currentScreen !is Screen.Reader) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentScreen == Screen.Leitura,
                        onClick = { currentScreen = Screen.Leitura },
                        icon = { Icon(Icons.Filled.CameraAlt, contentDescription = null) },
                        label = { Text("Texto Curto") }
                    )
                    NavigationBarItem(
                        selected = currentScreen == Screen.Documento,
                        onClick = { currentScreen = Screen.Documento },
                        icon = { Icon(Icons.Filled.Description, contentDescription = null) },
                        label = { Text("Documento") }
                    )
                    NavigationBarItem(
                        selected = currentScreen == Screen.Configuracoes,
                        onClick = { currentScreen = Screen.Configuracoes },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        label = { Text("Configurações") }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val screen = currentScreen) {
                Screen.Leitura -> CameraScreen(voice = selectedVoice, rate = rateString)
                Screen.Documento -> DocumentScreen(
                    onTextRecognized = { text -> currentScreen = Screen.Reader(text) }
                )
                is Screen.Reader -> ReaderScreen(
                    text = screen.text,
                    initialVoice = selectedVoice,
                    initialRatePercent = ratePercent,
                    onBack = { currentScreen = Screen.Documento }
                )
                Screen.Configuracoes -> SettingsScreen(
                    selectedVoice = selectedVoice,
                    speechRatePercent = ratePercent,
                    onVoiceSelected = {
                        selectedVoice = it
                        prefs.edit().putString(KEY_VOICE, it).apply()
                    },
                    onRateChanged = {
                        ratePercent = it
                        prefs.edit().putInt(KEY_RATE, it).apply()
                    }
                )
            }
        }
    }
}
