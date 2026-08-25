package com.parlero.leitor.reader

// Quebra de linha do OCR normalmente é só o texto embrulhando na foto, não fim de frase.
private val LINE_BREAKS = Regex("\\s*\\n+\\s*")

// Frase termina em ./;/!/?, seguido de espaço. (;) foi pedido explicitamente; !/? são
// tratados como equivalentes a ponto final, senão perguntas/exclamações nunca quebrariam.
private val SENTENCE_BOUNDARY = Regex("(?<=[.;!?])\\s+")

/** Divide o texto reconhecido em frases, para navegação/leitura período por período. */
fun segmentSentences(text: String): List<String> =
    text.replace(LINE_BREAKS, " ")
        .split(SENTENCE_BOUNDARY)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
