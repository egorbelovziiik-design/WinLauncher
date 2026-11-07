"""Генерация полностью вымышленного отчёта, а не реальной записи звонка."""
import asyncio
import random
from typing import Any


def _escape(value: Any) -> str:
    text = str(value).replace('\\', '\\\\')
    for ch in ('_', '*', '`', '['):
        text = text.replace(ch, '\\' + ch)
    return text


def generate_call_result(meme: dict) -> str:
    """Случайно выбрать сценарий и продолжительность, подготовить Telegram Markdown."""
    response = random.choice(meme['victim_responses'])
    duration = random.randint(*response['duration_range'])
    lines = ['📋 *Результат звонка*', '━━━━━━━━━━━━━━━━━━━',
             '🎭 Это вымышленный сценарий, а не настоящий звонок.',
             f"📞 *Звонок от:* {_escape(meme['caller'])} {meme['emoji']}",
             f'⏱ *Длительность:* {duration} сек',
             f"📶 *Статус:* {_escape(response['status'])}",
             f"🎤 *Голос жертвы:* {_escape(response['voice_type'])}", '', '💬 *Диалог:*']
    for line in response['dialog']:
        is_caller = line['role'] == 'caller'
        speaker = meme['caller'] if is_caller else 'Жертва'
        lines.append(f"{'🟢' if is_caller else '🔴'} *{_escape(speaker)}:* «{_escape(line['text'])}»")
    lines.extend(['', f"😤 *Настроение жертвы:* {_escape(response['mood'])}",
                  f"⭐ *Оценка звонка:* {_escape(response['rating'])} — {_escape(response['rating_comment'])}",
                  f"🔁 *Хочешь ещё?* /call {_escape(meme['name'])}"])
    return '\n'.join(lines)


async def send_call_result(message: Any, meme: dict, delay: float | None = None) -> None:
    """Через 2–4 секунды отправить отчёт (delay можно задать для тестов)."""
    await asyncio.sleep(random.uniform(2.0, 4.0) if delay is None else delay)
    await message.answer(generate_call_result(meme), parse_mode='Markdown')
