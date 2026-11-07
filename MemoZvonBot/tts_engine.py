"""Изоляция блокирующих gTTS и FFmpeg от event loop."""
import asyncio
from io import BytesIO

from gtts import gTTS
from pydub import AudioSegment
from audio_effects import add_call_effects


class TTSEngine:
    def __init__(self, concurrency: int):
        self._semaphore = asyncio.Semaphore(concurrency)

    @staticmethod
    def _render(text: str) -> bytes:
        mp3 = BytesIO()
        # gTTS выполняет HTTP-запросы; ограничиваем время каждого запроса.
        gTTS(text=text, lang='ru', timeout=(10, 30)).write_to_fp(mp3)
        mp3.seek(0)
        audio = add_call_effects(AudioSegment.from_file(mp3, format='mp3'))
        output = BytesIO()
        audio.export(output, format='ogg', codec='libopus', parameters=['-b:a', '48k'])
        return output.getvalue()

    async def render(self, text: str) -> bytes:
        async with self._semaphore:
            # Worker продолжает работу при отмене; удерживаем слот до его завершения.
            task = asyncio.create_task(asyncio.to_thread(self._render, text))
            try:
                return await asyncio.shield(task)
            except asyncio.CancelledError:
                try:
                    await task
                except Exception:
                    pass
                raise
