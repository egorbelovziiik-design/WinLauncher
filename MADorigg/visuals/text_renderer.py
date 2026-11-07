"""Текст с эффектами: глитч, пульсация, счётчик, финальные надписи."""

from __future__ import annotations

import math
import random

import pygame

import config


class TextRenderer:
    """Рендер текста с кешированием шрифтов и эффектами."""

    def __init__(self) -> None:
        self._fonts: dict[tuple[str, int, bool], pygame.font.Font] = {}

    # ------------------------------------------------------------------
    def _font(self, size: int, bold: bool = False) -> pygame.font.Font:
        """Получить (или создать) шрифт заданного размера."""
        key = ("default", max(8, int(size)), bold)
        if key not in self._fonts:
            font = pygame.font.Font(None, key[1])
            font.set_bold(bold)
            self._fonts[key] = font
        return self._fonts[key]

    # ------------------------------------------------------------------
    def draw(
        self,
        surface: pygame.Surface,
        text: str,
        size: int,
        color: tuple[int, int, int],
        y: int,
        center_x: int | None = None,
        bold: bool = True,
    ) -> pygame.Rect:
        """Обычный текст по центру (или с заданным center_x)."""
        font = self._font(size, bold)
        img = font.render(text, True, color)
        rect = img.get_rect()
        cx = center_x if center_x is not None else surface.get_width() // 2
        rect.center = (cx, y)
        surface.blit(img, rect)
        return rect

    # ------------------------------------------------------------------
    def glitch(
        self,
        surface: pygame.Surface,
        text: str,
        size: int,
        y: int,
        t: float,
        center_x: int | None = None,
        amplitude: float = 5.0,
    ) -> None:
        """Глитч-текст: буквы дрожат и мерцают красным↔синим."""
        font = self._font(size, bold=True)
        cx = center_x if center_x is not None else surface.get_width() // 2

        # Собираем поверхность целиком, затем рвём на полосы
        base = font.render(text, True, config.WHITE)
        base_rect = base.get_rect(center=(cx, y))

        # Дрожащие буквы разными цветами
        x = base_rect.left
        for i, ch in enumerate(text):
            if ch == " ":
                x += font.size(" ")[0]
                continue
            jitter_x = math.sin(t * 31.0 + i * 1.7) * amplitude
            jitter_y = math.cos(t * 27.0 + i * 2.3) * amplitude * 0.6
            phase = int(t * 8.0 + i) % 3
            if phase == 0:
                color = (255, 40, 40)
            elif phase == 1:
                color = (60, 90, 255)
            else:
                color = config.WHITE
            ch_img = font.render(ch, True, color)
            surface.blit(ch_img, (x + jitter_x, base_rect.top + jitter_y))
            x += ch_img.get_width()

        # Горизонтальные полосы-сдвиги поверх надписи
        for _ in range(3):
            band_h = random.randint(3, max(4, size // 4))
            by = random.randint(base_rect.top, max(base_rect.top + 1, base_rect.bottom - band_h))
            shift = random.randint(-10, 10)
            try:
                band = surface.subsurface(
                    (base_rect.left, by, base_rect.width, band_h)
                ).copy()
            except ValueError:
                continue
            surface.blit(band, (base_rect.left + shift, by))

    # ------------------------------------------------------------------
    def pulse(
        self,
        surface: pygame.Surface,
        text: str,
        size: int,
        color: tuple[int, int, int],
        y: int,
        t: float,
        speed: float = 6.0,
        center_x: int | None = None,
        bold: bool = True,
    ) -> None:
        """Пульсирующий текст (размер слегка дышит)."""
        factor = 1.0 + 0.06 * math.sin(t * speed)
        actual = max(8, int(size * factor))
        self.draw(surface, text, actual, color, y, center_x=center_x, bold=bold)

    # ------------------------------------------------------------------
    def score_hud(
        self,
        surface: pygame.Surface,
        red_count: int,
        blue_count: int,
    ) -> None:
        """Счётчик сверху: RED: 45 | ⚔ | BLUE: 38 (кружки и мечи нарисованы)."""
        width = surface.get_width()
        size = max(28, width // 45)
        font = self._font(size, bold=True)
        y = int(surface.get_height() * 0.045)

        red_text = font.render(f"RED: {red_count}", True, (255, 90, 90))
        blue_text = font.render(f"BLUE: {blue_count}", True, (110, 170, 255))

        gap = size // 2
        sword_zone = size  # место под нарисованные скрещённые мечи
        total = red_text.get_width() + gap + sword_zone + gap + blue_text.get_width()
        x = (width - total) // 2

        # Кружки-индикаторы
        r = size // 3
        pygame.draw.circle(surface, (255, 40, 40), (x - r - 6, y), r)
        pygame.draw.circle(surface, (40, 120, 255), (x + total + r + 6, y), r)

        surface.blit(red_text, (x, y - red_text.get_height() // 2))
        x += red_text.get_width() + gap

        # Скрещённые мечи (рисуемые, без emoji-глифов)
        sx, sy = x + sword_zone // 2, y
        blade = size // 2
        pygame.draw.line(
            surface, config.GOLD, (sx - blade, sy - blade), (sx + blade, sy + blade), 3
        )
        pygame.draw.line(
            surface, config.GOLD, (sx - blade, sy + blade), (sx + blade, sy - blade), 3
        )
        pygame.draw.line(
            surface, (230, 230, 240),
            (sx - blade + 4, sy - blade + 4), (sx + blade - 4, sy + blade - 4), 1
        )
        pygame.draw.line(
            surface, (230, 230, 240),
            (sx - blade + 4, sy + blade - 4), (sx + blade - 4, sy - blade + 4), 1
        )
        # Рукояти
        pygame.draw.circle(surface, (200, 160, 40), (sx - blade + 2, sy - blade + 2), 4)
        pygame.draw.circle(surface, (200, 160, 40), (sx + blade - 2, sy + blade - 2), 4)
        x += sword_zone + gap
        surface.blit(blue_text, (x, y - blue_text.get_height() // 2))

    # ------------------------------------------------------------------
    def victory(
        self,
        surface: pygame.Surface,
        winner: str,
        t: float,
    ) -> None:
        """Огромная золотая надпись победителя с пульсацией."""
        width, height = surface.get_size()
        base = max(48, width // 16)
        factor = 1.0 + 0.10 * math.sin(t * 7.0)
        size = int(base * factor)

        if winner == "red":
            text = "КРАСНЫЕ ПОБЕДИЛИ!"
        else:
            text = "СИНИЕ ПОБЕДИЛИ!"

        y = int(height * 0.16)
        font = self._font(size, bold=True)
        img = font.render(text, True, config.GOLD)
        rect = img.get_rect(center=(width // 2, y))

        # Золотое свечение
        glow = font.render(text, True, (180, 120, 0))
        for dx, dy in ((-3, 0), (3, 0), (0, -3), (0, 3)):
            surface.blit(glow, rect.move(dx, dy))
        surface.blit(img, rect)

        # Цветной кружок-смайлик победителя
        r = size // 2
        circle_x = rect.left - r - size // 3
        circle_y = y
        color = (255, 40, 40) if winner == "red" else (40, 120, 255)
        pygame.draw.circle(surface, color, (circle_x, circle_y), r)
        pygame.draw.circle(surface, (255, 255, 255), (circle_x, circle_y), r, 4)

    # ------------------------------------------------------------------
    def centered_line(
        self,
        surface: pygame.Surface,
        text: str,
        size: int,
        color: tuple[int, int, int],
        y_frac: float = 0.5,
    ) -> None:
        """Простая строка по центру (доля высоты)."""
        y = int(surface.get_height() * y_frac)
        self.draw(surface, text, size, color, y)

    def draw_devil(
        self,
        surface: pygame.Surface,
        cx: int,
        cy: int,
        radius: int,
        t: float = 0.0,
    ) -> None:
        """Нарисованный дьявол 😈 (вместо emoji-глифа)."""
        r = max(4, radius)
        # Рога
        pygame.draw.polygon(
            surface, (200, 20, 20),
            [
                (cx - int(r * 0.75), cy - int(r * 0.55)),
                (cx - int(r * 1.15), cy - int(r * 1.5)),
                (cx - int(r * 0.3), cy - int(r * 0.9)),
            ],
        )
        pygame.draw.polygon(
            surface, (200, 20, 20),
            [
                (cx + int(r * 0.75), cy - int(r * 0.55)),
                (cx + int(r * 1.15), cy - int(r * 1.5)),
                (cx + int(r * 0.3), cy - int(r * 0.9)),
            ],
        )
        # Голова
        pygame.draw.circle(surface, (230, 40, 40), (cx, cy), r)
        pygame.draw.circle(surface, (140, 10, 10), (cx, cy), r, max(1, r // 8))
        # Глаза
        eye_dx = int(r * 0.4)
        eye_y = cy - int(r * 0.15)
        pygame.draw.circle(surface, (255, 240, 90), (cx - eye_dx, eye_y), max(2, r // 4))
        pygame.draw.circle(surface, (255, 240, 90), (cx + eye_dx, eye_y), max(2, r // 4))
        pygame.draw.circle(surface, (20, 0, 0), (cx - eye_dx, eye_y), max(1, r // 8))
        pygame.draw.circle(surface, (20, 0, 0), (cx + eye_dx, eye_y), max(1, r // 8))
        # Ухмылка
        mouth_rect = pygame.Rect(cx - int(r * 0.6), cy + int(r * 0.05), int(r * 1.2), int(r * 0.7))
        pygame.draw.arc(
            surface, (60, 0, 0), mouth_rect,
            math.pi * 0.15, math.pi * 0.85,
            max(2, r // 6),
        )
        _ = t
