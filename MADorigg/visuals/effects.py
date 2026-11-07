"""Эффекты экрана: тряска, вспышки, глитч, виньетка."""

from __future__ import annotations

import random

import pygame


class ScreenShake:
    """Тряска экрана: смещение кадра на случайные пиксели."""

    def __init__(self) -> None:
        self.amplitude: float = 0.0
        self.time_left: float = 0.0
        self.duration: float = 0.0
        self.offset_x: int = 0
        self.offset_y: int = 0

    def trigger(self, amplitude: float, duration: float) -> None:
        """Запустить тряску: амплитуда в px, длительность в сек."""
        # Не уменьшаем уже идущую более сильную тряску
        if self.time_left > 0 and self.amplitude >= amplitude:
            return
        self.amplitude = amplitude
        self.duration = max(0.001, duration)
        self.time_left = self.duration

    def update(self, dt: float) -> None:
        """Обновить смещение (вызывать раз в кадр)."""
        if self.time_left <= 0.0:
            self.offset_x = 0
            self.offset_y = 0
            return
        self.time_left = max(0.0, self.time_left - dt)
        decay = self.time_left / self.duration
        amp = self.amplitude * decay
        self.offset_x = int(random.uniform(-amp, amp))
        self.offset_y = int(random.uniform(-amp, amp))

    @property
    def active(self) -> bool:
        return self.time_left > 0.0


class Flash:
    """Полноэкранная вспышка с затуханием."""

    def __init__(self) -> None:
        self.color: tuple[int, int, int] = (255, 255, 255)
        self.time_left: float = 0.0
        self.duration: float = 0.0
        self.peak_alpha: int = 255

    def trigger(
        self,
        color: tuple[int, int, int] = (255, 255, 255),
        duration: float = 0.3,
        peak_alpha: int = 255,
    ) -> None:
        """Запустить вспышку."""
        self.color = color
        self.duration = max(0.001, duration)
        self.time_left = self.duration
        self.peak_alpha = peak_alpha

    def update(self, dt: float) -> None:
        if self.time_left > 0.0:
            self.time_left = max(0.0, self.time_left - dt)

    def draw(self, surface: pygame.Surface) -> None:
        """Нарисовать вспышку (альфа убывает линейно)."""
        if self.time_left <= 0.0:
            return
        ratio = self.time_left / self.duration
        alpha = int(self.peak_alpha * ratio)
        overlay = pygame.Surface(surface.get_size(), pygame.SRCALPHA)
        overlay.fill((*self.color, alpha))
        surface.blit(overlay, (0, 0))

    @property
    def active(self) -> bool:
        return self.time_left > 0.0


class Glitch:
    """Глитч: горизонтальные полосы со сдвигом + разъезд RGB-каналов."""

    def __init__(self) -> None:
        self.time_left: float = 0.0
        self.duration: float = 0.0

    def trigger(self, duration: float = 0.35) -> None:
        """Запустить глитч-эффект."""
        self.duration = max(0.001, duration)
        self.time_left = self.duration

    def update(self, dt: float) -> None:
        if self.time_left > 0.0:
            self.time_left = max(0.0, self.time_left - dt)

    @property
    def active(self) -> bool:
        return self.time_left > 0.0

    def apply(self, surface: pygame.Surface) -> None:
        """Разрезать кадр на полосы, сдвинуть их и смешать каналы RGB."""
        if not self.active:
            return
        width, height = surface.get_size()
        # 1) Горизонтальные полосы
        n_bands = random.randint(4, 9)
        for _ in range(n_bands):
            band_h = random.randint(6, max(7, height // 12))
            y = random.randint(0, max(0, height - band_h))
            shift = random.randint(-20, 20)
            try:
                band = surface.subsurface((0, y, width, band_h)).copy()
            except ValueError:
                continue
            surface.blit(band, (shift, y))

        # 2) Разъезд RGB-каналов: красный влево, зелёный по центру, синий вправо
        shift = random.randint(3, 8)
        composite = surface.copy()
        composite.fill((0, 0, 0))

        red_only = surface.copy()
        red_only.fill((255, 0, 0), special_flags=pygame.BLEND_RGBA_MULT)
        composite.blit(red_only, (-shift, 0), special_flags=pygame.BLEND_RGBA_ADD)

        green_only = surface.copy()
        green_only.fill((0, 255, 0), special_flags=pygame.BLEND_RGBA_MULT)
        composite.blit(green_only, (0, 0), special_flags=pygame.BLEND_RGBA_ADD)

        blue_only = surface.copy()
        blue_only.fill((0, 0, 255), special_flags=pygame.BLEND_RGBA_MULT)
        composite.blit(blue_only, (shift, 0), special_flags=pygame.BLEND_RGBA_ADD)

        surface.blit(composite, (0, 0))


def vignette(surface: pygame.Surface, strength: int = 140) -> None:
    """Затемнение по краям экрана."""
    width, height = surface.get_size()
    overlay = pygame.Surface((width, height), pygame.SRCALPHA)
    edge = max(24, min(width, height) // 8)

    # Верх
    for i in range(edge):
        alpha = int(strength * (1.0 - i / edge))
        pygame.draw.line(overlay, (0, 0, 0, alpha), (0, i), (width, i))
    # Низ
    for i in range(edge):
        alpha = int(strength * (i / edge))
        pygame.draw.line(
            overlay, (0, 0, 0, alpha), (0, height - edge + i), (width, height - edge + i)
        )
    # Лево
    for i in range(edge):
        alpha = int(strength * (1.0 - i / edge))
        pygame.draw.line(overlay, (0, 0, 0, alpha), (i, 0), (i, height))
    # Право
    for i in range(edge):
        alpha = int(strength * (i / edge))
        pygame.draw.line(
            overlay, (0, 0, 0, alpha), (width - edge + i, 0), (width - edge + i, height)
        )
    surface.blit(overlay, (0, 0))
