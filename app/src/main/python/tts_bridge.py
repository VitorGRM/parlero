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
