import asyncio

import edge_tts


def synthesize(text: str, voice: str, rate: str, pitch: str, out_path: str) -> None:
    """Sintetiza `text` com a voz Edge TTS `voice` e salva o mp3 em `out_path`.

    edge_tts.Communicate.save() é assíncrono; Chaquopy chama funções Python
    de forma síncrona a partir do Kotlin, então embrulhamos com asyncio.run.
    """

    async def _run() -> None:
        communicate = edge_tts.Communicate(text, voice=voice, rate=rate, pitch=pitch)
        await communicate.save(out_path)

    asyncio.run(_run())


def list_voices(locale_prefixes=None):
    """Retorna vozes disponíveis como strings "ShortName|Locale|Gender".

    Filtra por prefixo de locale (ex.: "pt-BR") quando informado, para não
    devolver as ~300+ vozes de todos os idiomas para o Kotlin.
    """

    async def _run():
        voices = await edge_tts.list_voices()
        if locale_prefixes:
            voices = [
                v for v in voices
                if any(v["Locale"].startswith(p) for p in locale_prefixes)
            ]
        return [f'{v["ShortName"]}|{v["Locale"]}|{v["Gender"]}' for v in voices]

    return asyncio.run(_run())
