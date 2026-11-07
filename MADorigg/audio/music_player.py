"""Воспроизведение фоновой музыки и одиночных эффектов.

Вся музыка и эффекты генерируются через numpy (см. ``sound_generator``)
и кешируются после первого воспроизведения.
"""

from __future__ import annotations

import logging
from typing import Optional

import pygame

import config
from audio import sound_generator as gen

logger = logging.getLogger(__name__)


class MusicPlayer:
    """Управляет звуковыми каналами: музыка (0) и эффекты (1+)."""

    MUSIC_CHANNEL = 0

    def __init__(self) -> None:
        self._cache: dict[str, pygame.mixer.Sound] = {}
        self._music_channel: Optional[pygame.mixer.Channel] = None
        self._base_music_volume: float = 0.6
        self._music_volume: float = 0.6
        self._intensity: float = 1.0   # нарастающее напряжение
        self._fade_out: Optional[tuple[float, float]] = None  # (остаток, шаг)

        if pygame.mixer.get_init() is None:
            logger.warning("Mixer not initialized — audio disabled.")
            return

        pygame.mixer.set_num_channels(16)
        self._music_channel = pygame.mixer.Channel(self.MUSIC_CHANNEL)

    # ------------------------------------------------------------------
    # Кеш звуков
    # ------------------------------------------------------------------
    def _get(self, name: str) -> Optional[pygame.mixer.Sound]:
        """Вернуть (с кешированием) звук по имени."""
        if pygame.mixer.get_init() is None:
            return None
        if name not in self._cache:
            factories = {
                "kick": gen.generate_kick,
                "snare": gen.generate_snare,
                "hat": gen.generate_hat,
                "explosion": gen.generate_explosion,
                "hit": gen.generate_hit,
                "siren": gen.generate_siren,
                "crowd": gen.generate_crowd,
                "fanfare": gen.generate_fanfare,
                "fire": gen.generate_fire,
                "ice": gen.generate_ice,
                "gong": gen.generate_gong,
                "sword": gen.generate_sword,
                "death": gen.generate_death,
                "mega": gen.generate_mega_explosion,
            }
            factory = factories.get(name)
            if factory is None:
                logger.warning("Unknown sound: %s", name)
                return None
            try:
                self._cache[name] = factory()
            except Exception as exc:
                logger.error("Failed to generate '%s': %s", name, exc)
                return None
        return self._cache[name]

    # ------------------------------------------------------------------
    # Музыка
    # ------------------------------------------------------------------
    def prepare_music(self, bpm: int, duration: float) -> None:
        """Предварительно сгенерировать и закешировать фоновый трек."""
        if pygame.mixer.get_init() is None:
            return
        key = f"music_{bpm}_{duration:.0f}"
        if key not in self._cache:
            try:
                self._cache[key] = gen.generate_epic_music(bpm, duration)
            except Exception as exc:
                logger.error("Music generation failed: %s", exc)

    def play_music(self, bpm: int, duration: float, volume: float = 0.6) -> None:
        """Запустить фоновый трек (бесконечный луп)."""
        if self._music_channel is None:
            return
        self.prepare_music(bpm, duration)
        key = f"music_{bpm}_{duration:.0f}"
        sound = self._cache.get(key)
        if sound is None:
            return
        self._base_music_volume = volume
        self._music_volume = volume
        self._intensity = 1.0
        self._fade_out = None
        self._music_channel.set_volume(volume)
        self._music_channel.play(sound, loops=-1)

    def set_intensity(self, factor: float) -> None:
        """Множитель громкости музыки (нарастающее напряжение)."""
        self._intensity = max(0.0, min(2.0, factor))
        self._apply_music_volume()

    def raise_intensity(self, amount: float = 0.05) -> None:
        """Увеличить напряжение (например, +5% каждые 5 секунд)."""
        self.set_intensity(self._intensity + amount)

    def _apply_music_volume(self) -> None:
        if self._music_channel is None:
            return
        self._music_volume = min(
            1.0, self._base_music_volume * self._intensity
        )
        self._music_channel.set_volume(self._music_volume)

    def fade_out_music(self, duration: float = 1.5) -> None:
        """Плавно затушить музыку за ``duration`` секунд."""
        if self._music_channel is None or not self._music_channel.get_busy():
            return
        steps = max(1, int(duration * 30))
        self._fade_out = (duration, self._music_volume / steps)

    def update(self, dt: float) -> None:
        """Обновление (вызывать каждый кадр): обработка fade-out музыки."""
        if self._fade_out is None or self._music_channel is None:
            return
        _remaining, step = self._fade_out
        self._music_volume = max(0.0, self._music_volume - step)
        self._music_channel.set_volume(self._music_volume)
        if self._music_volume <= 0.001:
            self._music_channel.stop()
            self._fade_out = None

    def stop_music(self) -> None:
        """Остановить музыку сразу."""
        if self._music_channel is not None:
            self._music_channel.stop()
        self._fade_out = None

    # ------------------------------------------------------------------
    # Эффекты
    # ------------------------------------------------------------------
    def play(self, name: str, volume: float = 1.0, tries: int = 6) -> None:
        """Проиграть эффект на свободном канале."""
        if pygame.mixer.get_init() is None:
            return
        sound = self._get(name)
        if sound is None:
            return
        channel = pygame.mixer.find_channel(True)
        if channel is None:
            # Все каналы заняты — остановим самый тихий (запасной вариант)
            for i in range(1, pygame.mixer.get_num_channels()):
                ch = pygame.mixer.Channel(i)
                if not ch.get_busy():
                    channel = ch
                    break
            if channel is None:
                _ = tries
                channel = pygame.mixer.Channel(1)
        channel.set_volume(max(0.0, min(1.0, volume)))
        channel.play(sound)

    def stop_all(self) -> None:
        """Полная остановка всех каналов."""
        if pygame.mixer.get_init() is None:
            return
        pygame.mixer.stop()
        self._fade_out = None
