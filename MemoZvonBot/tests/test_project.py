import asyncio
import random
import tempfile
import unittest
from pathlib import Path
from unittest.mock import AsyncMock, patch

from call_result import generate_call_result, send_call_result
from database import Database
from meme_library import MEMES


class LibraryTest(unittest.TestCase):
    def test_all_scenarios(self):
        self.assertEqual(len(MEMES), 50)
        for name, meme in MEMES.items():
            self.assertEqual(name, meme['name'])
            self.assertTrue(meme['text'])
            self.assertGreaterEqual(len(meme['victim_responses']), 3)
            for response in meme['victim_responses']:
                self.assertLessEqual(response['duration_range'][0], response['duration_range'][1])
                self.assertTrue(all(line['role'] in ('caller', 'victim') for line in response['dialog']))
                with patch('call_result.random.choice', return_value=response):
                    report = generate_call_result(meme)
                    self.assertIn('вымышленный', report)
                    self.assertIn('Результат звонка', report)
                    self.assertIn('Настроение жертвы', report)

    def test_markdown_escape(self):
        meme = dict(MEMES['alo'], caller='Имя_[*`')
        report = generate_call_result(meme)
        self.assertIn(r'Имя\_\[\*\`', report)


class AsyncTest(unittest.IsolatedAsyncioTestCase):
    async def test_report(self):
        message = AsyncMock()
        await send_call_result(message, MEMES['pizza'], delay=0)
        message.answer.assert_awaited_once()
        self.assertEqual(message.answer.await_args.kwargs['parse_mode'], 'Markdown')

    async def test_random_delay(self):
        message = AsyncMock()
        with patch('call_result.asyncio.sleep', new_callable=AsyncMock) as sleep, patch('call_result.random.uniform', return_value=2.5):
            await send_call_result(message, MEMES['pizza'])
            sleep.assert_awaited_once_with(2.5)

    async def test_concurrent_reservations_and_stats(self):
        with tempfile.TemporaryDirectory() as tmp:
            db = Database(Path(tmp) / 'test.sqlite3')
            await db.init()
            values = await asyncio.gather(*(db.reserve(1, 15) for _ in range(10)))
            self.assertEqual(values.count(0), 1)
            self.assertTrue(all(value > 0 for value in values if value != 0))
            self.assertEqual(await db.reserve(2, 15), 0)
            await db.record_call(1, 'pizza')
            await db.record_call(1, 'pizza')
            await db.record_call(1, 'alo')
            self.assertEqual(await db.stats(1), (3, 'pizza'))
            self.assertEqual(await db.stats(2), (0, None))


if __name__ == '__main__':
    unittest.main()
