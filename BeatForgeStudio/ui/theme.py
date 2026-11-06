"""Темы оформления BeatForge Studio.

Тема — это словарь токенов (цвета, шрифт), который подставляется в
``assets/styles.qss`` вместо ``@TOKEN@``. Есть три темы: ``dark`` (по
умолчанию), ``light`` и ``amoled``.

Пример::

    from ui.theme import apply_theme
    apply_theme(app, "amoled")
"""

from __future__ import annotations

import logging
from pathlib import Path
from typing import Dict, Optional

from PyQt6.QtGui import QColor, QFont, QPalette
from PyQt6.QtWidgets import QApplication

from utils.config import get_config

LOG = logging.getLogger(__name__)

ASSETS_DIR = Path(__file__).resolve().parent.parent / "assets"
STYLES_FILE = ASSETS_DIR / "styles.qss"


DARK: Dict[str, str] = {
    "BG": "121212",
    "PANEL": "1B1B1B",
    "PANEL_ALT": "1F1F1F",
    "PANEL_HI": "242424",
    "INPUT": "171717",
    "BORDER": "2C2C2C",
    "HOVER": "2E2E2E",
    "PRESSED": "383838",
    "TEXT": "EDEDED",
    "TEXT_MUTED": "9A9A9A",
    "TEXT_DISABLED": "5A5A5A",
    "ACCENT": "FF6600",
    "ACCENT_HOVER": "FF7F26",
    "ACCENT_PRESSED": "E05600",
    "ACCENT_DIM": "7A3C12",
    "ACCENT_SOFT": "2B1B10",
    "ACCENT2": "00BFFF",
    "ACCENT2_HOVER": "33CFFF",
    "SCROLL": "3A3A3A",
    "SCROLL_HOVER": "4A4A4A",
    "SUCCESS": "44D492",
    "WARNING": "FFD166",
    "DANGER": "FF6B6B",
}

LIGHT: Dict[str, str] = {
    "BG": "F5F5F7",
    "PANEL": "FFFFFF",
    "PANEL_ALT": "FAFAFA",
    "PANEL_HI": "F0F0F3",
    "INPUT": "FFFFFF",
    "BORDER": "DCDCE2",
    "HOVER": "ECECF1",
    "PRESSED": "E0E0E8",
    "TEXT": "1B1B1F",
    "TEXT_MUTED": "6B6B75",
    "TEXT_DISABLED": "ABABB5",
    "ACCENT": "E85D04",
    "ACCENT_HOVER": "F97316",
    "ACCENT_PRESSED": "C24A00",
    "ACCENT_DIM": "F3B48B",
    "ACCENT_SOFT": "FFEDE1",
    "ACCENT2": "0088BB",
    "ACCENT2_HOVER": "00A5E0",
    "SCROLL": "C9C9D2",
    "SCROLL_HOVER": "B2B2BE",
    "SUCCESS": "1F9D58",
    "WARNING": "B8860B",
    "DANGER": "C0392B",
}

AMOLED: Dict[str, str] = {
    **DARK,
    "BG": "000000",
    "PANEL": "0A0A0A",
    "PANEL_ALT": "0D0D0D",
    "PANEL_HI": "161616",
    "INPUT": "0A0A0A",
    "BORDER": "1E1E1E",
    "HOVER": "1C1C1C",
    "PRESSED": "262626",
    "ACCENT_SOFT": "22160C",
    "SCROLL": "2A2A2A",
}

THEMES: Dict[str, Dict[str, str]] = {"dark": DARK, "light": LIGHT, "amoled": AMOLED}


def load_qss(theme: str = "dark", font_family: str = "Inter", font_size: int = 11) -> str:
    """Прочитать QSS и подставить токены темы.

    Args:
        theme: ``dark`` | ``light`` | ``amoled``.
        font_family: Семейство шрифта.
        font_size: Размер шрифта в pt.

    Returns:
        Готовый CSS-текст (пустая строка, если файл не найден).
    """
    tokens = THEMES.get(theme, DARK)
    try:
        qss = STYLES_FILE.read_text(encoding="utf-8")
    except OSError as exc:
        LOG.error("Не удалось прочитать %s: %s", STYLES_FILE, exc)
        return ""
    text = qss.replace("@FONT@", font_family)
    for key, value in tokens.items():
        # В словаре хранятся «голые» hex-строки (FF6600) — подставляем
        # полноценный цвет, чтобы QSS оставался валидным (#FF6600).
        css_value = value if value.startswith(("#", "rgba", "rgb")) else f"#{value}"
        text = text.replace(f"@{key}@", css_value)
    # масштабирование шрифта: базовый 11pt в QSS
    if font_size != 11:
        text = text.replace("font-size: 11pt;", f"font-size: {font_size}pt;")
    return text


def apply_theme(app: QApplication, theme: Optional[str] = None,
                font_family: Optional[str] = None,
                font_size: Optional[int] = None) -> str:
    """Применить тему к приложению (QSS + QPalette + шрифт).

    Args:
        app: Экземпляр QApplication.
        theme: Имя темы (по умолчанию из конфига).
        font_family: Шрифт (по умолчанию из конфига).
        font_size: Размер шрифта.

    Returns:
        Имя применённой темы.
    """
    cfg = get_config().ui
    name = theme or cfg.theme
    family = font_family or cfg.font_family
    size = font_size or cfg.font_size

    tokens = THEMES.get(name, DARK)
    app.setStyle("Fusion")

    palette = QPalette()
    palette.setColor(QPalette.ColorRole.Window, QColor(tokens["BG"]))
    palette.setColor(QPalette.ColorRole.WindowText, QColor(tokens["TEXT"]))
    palette.setColor(QPalette.ColorRole.Base, QColor(tokens["PANEL"]))
    palette.setColor(QPalette.ColorRole.AlternateBase, QColor(tokens["PANEL_ALT"]))
    palette.setColor(QPalette.ColorRole.Text, QColor(tokens["TEXT"]))
    palette.setColor(QPalette.ColorRole.Button, QColor(tokens["PANEL_HI"]))
    palette.setColor(QPalette.ColorRole.ButtonText, QColor(tokens["TEXT"]))
    palette.setColor(QPalette.ColorRole.Highlight, QColor(tokens["ACCENT"]))
    palette.setColor(QPalette.ColorRole.HighlightedText, QColor("101010"))
    palette.setColor(QPalette.ColorRole.ToolTipBase, QColor(tokens["PANEL_HI"]))
    palette.setColor(QPalette.ColorRole.ToolTipText, QColor(tokens["TEXT"]))
    palette.setColor(QPalette.ColorRole.PlaceholderText, QColor(tokens["TEXT_MUTED"]))
    app.setPalette(palette)

    font = QFont(family, size)
    font.setStyleHint(QFont.StyleHint.SansSerif)
    app.setFont(font)

    app.setStyleSheet(load_qss(name, family, size))
    LOG.info("Тема применена: %s (%s %spt)", name, family, size)
    return name


def color(token: str, theme: Optional[str] = None) -> str:
    """Вернуть цвет темы по имени токена (для QPainter-виджетов).

    Args:
        token: Имя токена (``ACCENT``, ``PANEL``, …).
        theme: Тема (по умолчанию текущая из настроек).
    """
    name = theme or get_config().ui.theme
    value = THEMES.get(name, DARK).get(token, "FF6600")
    return value if value.startswith(("#", "rgb")) else f"#{value}"
