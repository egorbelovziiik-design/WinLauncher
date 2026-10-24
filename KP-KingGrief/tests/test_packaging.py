"""
Проверка упаковки: спецификация PyInstaller, скрытые импорты,
пути в режиме `frozen` и поведение точки входа без токена.

Сама сборка бинарника выполняется `build/build_exe.py`
(на Windows — `build/build.bat` или CI-workflow).
"""

from __future__ import annotations

import ast
import importlib
import os
import subprocess
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parent.parent
SPEC = ROOT / "build" / "KP-KingGrief.spec"


def spec_hiddenimports() -> list[str]:
    """Достаёт список hiddenimports прямо из .spec (без запуска PyInstaller)."""
    tree = ast.parse(SPEC.read_text(encoding="utf-8"))
    for node in ast.walk(tree):
        if isinstance(node, ast.Assign):
            targets = [t.id for t in node.targets if isinstance(t, ast.Name)]
            if "hiddenimports" in targets:
                return [elt.value for elt in node.value.elts if isinstance(elt, ast.Constant)]
    raise AssertionError("В спецификации не найден список hiddenimports")


def test_spec_exists_and_parses():
    assert SPEC.exists(), "build/KP-KingGrief.spec отсутствует"
    ast.parse(SPEC.read_text(encoding="utf-8"))


def test_all_hiddenimports_are_importable():
    """Опечатка в hiddenimports = нерабочий EXE. Проверяем каждый модуль."""
    broken = []
    for module in spec_hiddenimports():
        try:
            importlib.import_module(module)
        except Exception as exc:  # noqa: BLE001
            broken.append(f"{module}: {type(exc).__name__}: {exc}")
    assert not broken, "Не импортируются модули:\n" + "\n".join(broken)


def test_all_project_modules_importable():
    """Каждый модуль проекта должен импортироваться без ошибок."""
    modules = []
    for path in sorted(ROOT.rglob("*.py")):
        relative = path.relative_to(ROOT)
        if any(part in ("tests", "build", ".venv", "build_temp", "dist") for part in relative.parts):
            continue
        if path.name == "main.py":
            modules.append("main")
            continue
        modules.append(".".join(relative.with_suffix("").parts))

    broken = []
    for module in modules:
        try:
            importlib.import_module(module)
        except Exception as exc:  # noqa: BLE001
            broken.append(f"{module}: {type(exc).__name__}: {exc}")
    assert modules, "не найдено ни одного модуля проекта"
    assert not broken, "Ошибки импорта:\n" + "\n".join(broken)


def test_spec_declares_every_project_package():
    """Все пакеты бота должны быть в hiddenimports (иначе PyInstaller их потеряет)."""
    declared = set(spec_hiddenimports())
    required = {
        "bot.client", "bot.events", "bot.permissions",
        "commands.reports", "commands.warnings", "commands.staff",
        "commands.statistics", "commands.settings",
        "database.connection", "database.migrations", "database.models",
        "services.report_tracker", "services.role_manager",
        "services.scheduler", "services.warning_manager",
        "utils.embeds", "utils.logging", "utils.pagination", "utils.periods",
    }
    missing = required - declared
    assert not missing, f"В .spec не объявлены: {missing}"


def test_frozen_paths_point_next_to_executable(tmp_path, monkeypatch):
    """В собранном EXE данные и логи создаются рядом с файлом программы."""
    fake_exe = tmp_path / "dist" / "КП-KingGrief.exe"
    fake_exe.parent.mkdir(parents=True)
    fake_exe.write_bytes(b"")

    import config

    monkeypatch.setattr(config.sys, "frozen", True, raising=False)
    monkeypatch.setattr(config.sys, "executable", str(fake_exe), raising=False)
    assert config._app_dir() == fake_exe.parent

    monkeypatch.delattr(config.sys, "frozen", raising=False)
    assert config._app_dir() == ROOT


def test_build_script_reports_correct_artifact_name():
    sys.path.insert(0, str(ROOT / "build"))
    try:
        import build_exe
    finally:
        sys.path.pop(0)
    name = build_exe.exe_name()
    assert name.startswith("КП-KingGrief")
    if sys.platform == "win32":
        assert name.endswith(".exe")


def test_requirements_pins_discord_py():
    text = (ROOT / "requirements.txt").read_text(encoding="utf-8")
    assert "discord.py>=2.4" in text
    assert "APScheduler" in text
    assert "python-dotenv" in text


def test_env_example_has_token_placeholder():
    text = (ROOT / ".env.example").read_text(encoding="utf-8")
    assert "DISCORD_TOKEN=" in text
    assert "OWNER_ID=" in text


def test_token_is_never_hardcoded():
    """Токен не должен встречаться в исходниках."""
    suspicious = []
    for path in ROOT.rglob("*.py"):
        relative = path.relative_to(ROOT)
        if any(part in ("tests", ".venv", "build_temp", "dist") for part in relative.parts):
            continue
        text = path.read_text(encoding="utf-8")
        for line in text.split("\n"):
            stripped = line.strip()
            if stripped.startswith("#"):
                continue
            if "DISCORD_TOKEN" in line and ("=" in line) and "getenv" not in line and "os.environ" not in line:
                if "DISCORD_TOKEN=" in line and 'DISCORD_TOKEN="' in line:
                    suspicious.append(f"{path}: {stripped}")
    assert not suspicious, "Похоже на зашитый токен:\n" + "\n".join(suspicious)


def test_missing_token_exits_with_clear_message(tmp_path):
    """Без токена программа печатает понятную инструкцию и завершается с кодом 1."""
    env = {
        **os.environ,
        "KP_DB_PATH": str(tmp_path / "t.db"),
        "KP_DATA_DIR": str(tmp_path),
        "KP_LOG_DIR": str(tmp_path / "logs"),
        "KP_BACKUP_DIR": str(tmp_path / "backups"),
        "KP_LOCK_FILE": str(tmp_path / "bot.lock"),
    }
    env.pop("DISCORD_TOKEN", None)
    result = subprocess.run(
        [sys.executable, str(ROOT / "main.py")],
        cwd=str(ROOT),
        env=env,
        capture_output=True,
        text=True,
        timeout=120,
        stdin=subprocess.DEVNULL,
    )
    assert result.returncode == 1, result.stdout + result.stderr
    assert "DISCORD_TOKEN" in result.stdout
    assert ".env" in result.stdout
