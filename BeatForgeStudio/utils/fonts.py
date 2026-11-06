"""Загрузка шрифтов приложения (Inter, при наличии) и подбор фолбэков.

Порядок:

1. файлы ``assets/fonts/*.ttf|otf`` регистрируются в Qt (работает и в onefile);
2. если Inter не найден в системе, Qt использует первый доступный из списка
   ``FALLBACK_FAMILIES`` — приложение остаётся читабельным.

Модуль не падает, если шрифтов нет: это необязательная полировка.
"""

from __future__ import annotations

import logging
from pathlib import Path
from typing import List, Optional

LOG = logging.getLogger(__name__)

#: Папка со шрифтами внутри проекта.
FONTS_DIR = Path(__file__).resolve().parent.parent / "assets" / "fonts"

#: Запасные семейства (по приоритету).
FALLBACK_FAMILIES: List[str] = [
    "Inter", "Segoe UI", "SF Pro Text", "Roboto", "Noto Sans",
    "DejaVu Sans", "Arial", "Helvetica",
]

#: Семейство, выбранное после инициализации.
_loaded_family: Optional[str] = None


def load_fonts() -> List[str]:
    """Зарегистрировать шрифты из ``assets/fonts`` в QFontDatabase.

    Returns:
        Список семейств, добавленных из файлов (может быть пустым).
    """
    global _loaded_family
    loaded: List[str] = []
    if not FONTS_DIR.exists():
        return loaded
    try:
        from PyQt6.QtGui import QFontDatabase
    except Exception as exc:  # pragma: no cover - Qt без GUI
        LOG.debug("QFontDatabase недоступен: %s", exc)
        return loaded

    for path in sorted(FONTS_DIR.glob("*")):
        if path.suffix.lower() not in (".ttf", ".otf", ".ttc"):
            continue
        font_id = QFontDatabase.addApplicationFont(str(path))
        if font_id < 0:
            LOG.warning("Шрифт не загрузился: %s", path.name)
            continue
        families = QFontDatabase.applicationFontFamilies(font_id)
        loaded.extend(families)
        LOG.info("Шрифт загружен: %s → %s", path.name, ", ".join(families))
    if loaded:
        _loaded_family = loaded[0]
    return loaded


def preferred_family(requested: str = "Inter") -> str:
    """Выбрать доступное семейство шрифта.

    Args:
        requested: Желаемое семейство (обычно из настроек).

    Returns:
        Первое доступное из списка (или ``requested`` как последний шанс).
    """
    try:
        from PyQt6.QtGui import QFontDatabase

        available = set(QFontDatabase.families())
    except Exception:
        return requested
    for family in [requested] + FALLBACK_FAMILIES:
        if family in available:
            return family
    return requested
