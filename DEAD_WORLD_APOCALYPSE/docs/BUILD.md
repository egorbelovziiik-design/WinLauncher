# Сборка Windows EXE

## Вариант A — автоматически через GitHub Actions (рекомендуется)

1. Откройте репозиторий на GitHub → вкладка **Actions** → workflow **«DEAD WORLD: Windows EXE»**.
2. Нажмите **Run workflow** (или сборка уже запустилась от пуша в ветку).
3. После завершения скачайте:
   - артефакт **DEAD_WORLD_APOCALYPSE_RELEASE** со страницы запуска, **или**
   - ZIP из **Releases** (тег `deadworld-v0.1.0`).
4. Распакуйте ZIP целиком и запустите **`DeadWorldApocalypse.exe`**.
   Не вынимайте EXE из папки — рядом лежат ресурсы игры (`_internal`).

Требования: Windows 10/11 x64, ~400 МБ места. Python не нужен.

## Вариант B — собрать самому на Windows

Нужен Python 3.11 (python.org, при установке отметить «Add to PATH»):

```bat
cd DEAD_WORLD_APOCALYPSE
build\build_windows.bat
```

Результат: папка `DEAD_WORLD_APOCALYPSE_RELEASE\DeadWorldApocalypse\DeadWorldApocalypse.exe`.

## Вариант C — запуск из исходников (любая ОС)

```bash
pip install -r requirements.txt
python main.py
```

## Проверка сборки

```bash
DeadWorldApocalypse.exe --selftest   :: создаст selftest_result.txt со строкой SELFTEST_OK
python -B -m pytest tests/ -q       :: юнит-тесты из исходников
python main.py --smoke              :: headless-прогон: мир+зомби+выстрел+сейв (SMOKE_OK)
```

## Перегенерация ассетов (Blender)

```bash
pip install "bpy==4.2.*" numpy pillow
python tools/gen_textures.py
python tools/gen_audio.py
python blender/generate_all.py        # или открыть скрипты в Blender и выполнить
python -B -m pytest tests/ -q         # валидация геометрии/анимаций/сборки китов
```

> В обычном Blender: *Scripting → Open → `blender/generate_all.py` → Run Script*.
> Модули `dw_lib.py`/`dw_props.py` должны лежать рядом (как в репозитории).

## Где лежат сохранения и настройки

`%APPDATA%\DeadWorldApocalypse\` — `settings.json`, `save_autosave.json`, `save_slot1..3.json`.
