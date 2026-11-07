"""Проверка порядка отправки без Telegram, gTTS и FFmpeg."""
import asyncio
import unittest
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

from handlers.call import perform_call
from meme_library import MEMES


class FlowTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.message = SimpleNamespace(bot=SimpleNamespace(send_chat_action=AsyncMock()),
                                       chat=SimpleNamespace(id=1), answer=AsyncMock(), answer_voice=AsyncMock())
        self.limiter = SimpleNamespace(check=AsyncMock(return_value=0))
        self.tts = SimpleNamespace(render=AsyncMock(return_value=b'fake ogg'))
        self.db = SimpleNamespace(record_call=AsyncMock())

    async def test_voice_before_report_and_stats(self):
        async def result(message, meme):
            self.message.answer_voice.assert_awaited_once()
            self.assertEqual(meme['name'], 'pizza')
        with patch('handlers.call.send_call_result', side_effect=result) as report:
            await perform_call(self.message, 42, MEMES['pizza'], self.limiter, self.tts, self.db)
            report.assert_awaited_once()
        self.db.record_call.assert_awaited_once_with(42, 'pizza')
        self.assertEqual(self.message.answer_voice.await_args.args[0].filename, 'memozvon.ogg')

    async def test_tts_failure_skips_voice_result_and_stats(self):
        self.tts.render.side_effect = RuntimeError('tts unavailable')
        with patch('handlers.call.send_call_result', new_callable=AsyncMock) as report:
            await perform_call(self.message, 42, MEMES['pizza'], self.limiter, self.tts, self.db)
            report.assert_not_awaited()
        self.message.answer_voice.assert_not_awaited()
        self.db.record_call.assert_not_awaited()
        self.message.answer.assert_awaited_once()

    async def test_limit_blocks_generation(self):
        self.limiter.check.return_value = 5
        await perform_call(self.message, 42, MEMES['pizza'], self.limiter, self.tts, self.db)
        self.tts.render.assert_not_awaited()
        self.assertIn('5', self.message.answer.await_args.args[0])

    async def test_slow_stats_does_not_delay_report(self):
        started, release = asyncio.Event(), asyncio.Event()
        async def slow_record(*args):
            started.set()
            await release.wait()
        self.db.record_call.side_effect = slow_record
        async def report(*args):
            await asyncio.wait_for(started.wait(), 1)
            release.set()
        with patch('handlers.call.send_call_result', side_effect=report):
            await asyncio.wait_for(perform_call(self.message, 42, MEMES['pizza'], self.limiter, self.tts, self.db), 2)
