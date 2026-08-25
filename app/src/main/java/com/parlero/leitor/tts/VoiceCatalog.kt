package com.parlero.leitor.tts

import android.content.Context
import org.json.JSONArray

/**
 * Catálogo de todas as vozes Edge TTS, empacotado em assets/edge_voices.json (gerado via
 * `edge_tts.list_voices()`). Carregar do bundle é instantâneo e não depende de rede —
 * só a síntese de fato (edge-tts) continua precisando de internet.
 */
object VoiceCatalog {
    fun loadAll(context: Context): List<EdgeVoice> {
        val json = context.assets.open("edge_voices.json").bufferedReader().use { it.readText() }
        val array = JSONArray(json)
        val voices = ArrayList<EdgeVoice>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            voices.add(
                EdgeVoice(
                    shortName = obj.getString("ShortName"),
                    locale = obj.getString("Locale"),
                    gender = obj.getString("Gender")
                )
            )
        }
        return voices
    }
}
