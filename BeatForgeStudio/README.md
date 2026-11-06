# BeatForge Studio

**Мультимедийная студия на Python 3.11 + PyQt6**: поиск музыки и битов,
аудиоредактор, видеоредактор, экспорт и публикация в соцсети — в одном
тёмном приложении.

```
pip install -r requirements.txt
playwright install chromium      # только для TikTok (необязательно)
python main.py
```

Требуется Python 3.10+ (проверено на 3.11). FFmpeg ищется в `PATH`, затем
используется бинарник из пакета `imageio-ffmpeg` — отдельная установка не нужна.

---

## Что умеет

### Поиск музыки и битов
* YouTube и SoundCloud через `yt-dlp` (поиск + предпросмотр 30 секунд).
* Режим «Биты»: жанровые чипы (Trap, Drill, Lo-Fi, Boom Bap, Phonk…),
  BPM-слайдер с допуском, тональность, настроение, фильтр «только бесплатные».
* Карточки с обложкой, длительностью, тегами; скачивание с прогрессом,
  отмена очереди, кэш в SQLite (`~/BeatForgeStudio/studio.db`).
* Кнопка «В проект» — скачанный трек попадает на дорожку аудиоредактора.

### Аудиоредактор
* До 8 дорожек, волна с зумом, выделение мышью, сетка по BPM.
* Cut / Copy / Paste / Split / Fade / Trim / Reverse / Normalize / Duplicate.
* Undo/redo на 50 шагов (паттерн «Команда», а не снимки состояния).
* Эффекты: 3-полосный EQ, компрессор, реверб, дилей, сатурация, лимитер,
  нормализация; пресеты («Vocal Warm», «Bass Boost», …), bypass у каждого.
* Реальный микс в фоне и прослушивание результата.

### Видеоредактор
* Таймлайн V1–V4 + A1–A4, snap к краям и тактам, перетаскивание клипов,
  изменение границ, контекстное меню.
* Превью кадров из FFmpeg (без QMediaPlayer), сетка третей и safe-area 9:16.
* Свойства клипа: тайминг, скорость (с сохранением тона), геометрия,
  прозрачность, громкость, переход, фильтры.
* 29 переходов `xfade`, цепочка собирается автоматически на стыках.
* Видеоэффекты: яркость/контраст/насыщенность/гамма/температура/оттенок/
  резкость/виньетка, размытие, зеркало, хромакей, PiP, пресеты стилей.
* Пресеты соцсетей: 16:9, 9:16, 1:1, 4:5, 4:3, 21:9.
* Текст: пресеты (YouTube Intro, TikTok Caption, Lower Third…), анимации
  (fade, typewriter, bounce, slide, pop, glitch) с живым превью.
* Субтитры: импорт/экспорт SRT/VTT/ASS, ручная правка, автогенерация Whisper.

### Экспорт
* Аудио: MP3 (128–320 кбит/с), WAV (16/24/32 бит), FLAC, OGG, M4A —
  с нормализацией до −1 dBFS и дитером.
* Видео: MP4 (H.264/H.265), WebM (VP9), MOV, GIF; пресеты «YouTube 1080p»,
  «YouTube 4K», «TikTok HD», «Instagram Reels».
* «Экспорт ссылкой»: file.io с автоматическим фолбэком на transfer.sh.
* Диалог прогресса с отменой, ETA, «Открыть папку», копированием ссылки.

### Публикация
* YouTube: OAuth2 Device Flow, resumable upload с прогрессом и повторами,
  отложенная публикация (`publishAt`), превью, плейлисты.
* TikTok: Playwright (загрузка через сохранённую сессию), проверка лимитов
  (до 10 минут, MP4/H.264) до публикации.
* Токены шифруются AES-GCM (ключ из пароля машины + соль в
  `~/.beatforge_studio/.salt`).

### Проекты и надёжность
* Формат `.bfs` (JSON): аудио, видео, тексты, субтитры, настройки сцены.
* Автосохранение раз в 2 минуты и восстановление после сбоя.
* Бандл проекта вместе с медиа для переноса на другой компьютер.
* Все ошибки — в лог и `QMessageBox`, приложение не падает
  (глобальный `excepthook`).
* Тяжёлые операции (поиск, загрузка, рендер, экспорт, публикация) — в
  `QThread` / `QThreadPool`, GUI-поток свободен.

---

## Интерфейс

| Тема | Значения |
|---|---|
| Фон | `#121212`, панели `#1B1B1B`, границы `#2C2C2C` |
| Акценты | `#FF6600` (действие), `#00BFFF` (вторичное) |
| Скругление | 8–10 px |
| Шрифт | Inter 10–13 pt (фолбэк: Segoe UI → DejaVu Sans) |
| Минимальный размер окна | 1200 × 700 |

Анимации: переключение вкладок 300 мс `OutCubic` (fade + slide), hover 150 мс,
каскадное появление карточек, slide-in тостов, «призрак» при drag & drop.
Всё отключается в настройках.

Горячие клавиши: `Ctrl+1…4` — вкладки, `Ctrl+N/O/S` — проект,
`Ctrl+E` — экспорт аудио, `Ctrl+Z / Ctrl+Shift+Z` — undo/redo,
`Ctrl+B` — сайдбар, `Space` — play/pause, `S` — разрез, `Del` — удалить клип.

---

## Структура

```
BeatForgeStudio/
├── main.py                 точка входа, аргументы, excepthook
├── build.spec              PyInstaller onefile
├── requirements.txt
├── assets/                 styles.qss, fonts/, transitions/
├── core/
│   ├── audio_engine.py     буферы, воспроизведение, нормализация
│   ├── mixer.py            8 дорожек, клипы, рендер микса и стемов
│   ├── effects_audio.py    EQ/компрессор/реверб/дилей + пресеты
│   ├── video_engine.py     Scene → FFmpeg, кадры, превью, рендер
│   ├── transitions.py      29 xfade-переходов
│   ├── effects_video.py    цветокоррекция, размытие, хромакей
│   ├── text_overlay.py     текстовые слои и анимации (drawtext)
│   ├── subtitle_engine.py  SRT/VTT/ASS, Whisper
│   ├── exporter.py         аудио/видео экспорт, загрузка ссылкой
│   └── project_manager.py  .bfs, автосохранение, бандлы
├── search/                 yt-dlp поиск (YouTube, SoundCloud, биты)
├── social/                 OAuth YouTube, TikTok Playwright, шифрование
├── ui/
│   ├── main_window.py      окно, меню, транспорт, автосохранение
│   ├── search_tab.py       поиск музыки и битов
│   ├── audio_editor_tab.py редактор аудио + команды undo/redo
│   ├── video_editor_tab.py редактор видео + эффекты
│   ├── theme.py            три темы и токены QSS
│   ├── widgets/            сайдбар, таймлайн, волна, карточки, тосты…
│   └── dialogs/            экспорт, прогресс, настройки, текст,
│                           публикация, субтитры
└── utils/                  логгер, конфиг, FFmpeg, SQLite, загрузчик,
                            пул потоков, шрифты
```

---

## Настройки и данные

| Что | Где |
|---|---|
| Конфиг | `~/.beatforge_studio/config.json` |
| Лог | `~/.beatforge_studio/beatforge_studio.log` (5 МБ × 3) |
| Токены/соль | `~/.beatforge_studio/` (AES-GCM) |
| Кэш, загрузки, проекты, экспорт | `~/BeatForgeStudio/…` |
| База (треки, история, проекты, аккаунты) | `~/BeatForgeStudio/studio.db` |

Ключи командной строки: `--project file.bfs`, `--safe` (без анимаций и звука),
`--reset-config`, `--debug`, `--version`.

Пакетный экспорт без интерфейса:

```
python main.py --project mix.bfs --export-audio out.mp3
```

Окно не показывается, микс рендерится и кодируется сразу; код возврата
`0` — успех, `1` — нечего экспортировать или проект не открылся, `2` — ошибка
кодирования.

---

## Публикация: что нужно от пользователя

**YouTube.** Создайте OAuth client типа *Desktop* в Google Cloud Console,
скачайте JSON и положите его в `~/.beatforge_studio/`. Либо задайте
переменные окружения `BEATFORGE_YOUTUBE_CLIENT_ID` и
`BEATFORGE_YOUTUBE_CLIENT_SECRET`. Затем: «Публикация» → «Привязать YouTube» →
подтвердите код в браузере.

**TikTok.** `pip install playwright && playwright install chromium`, затем
«Привязать TikTok» — откроется браузер, после входа cookies сохранятся и
сессия переиспользуется.

**Субтитры Whisper.** `pip install openai-whisper` (модель скачается при первом
запуске). Без пакета работают импорт, ручная правка и экспорт.

---

## Сборка

**Готовый Windows-бинарник** собирается в CI и лежит в релизе:

* страница: <https://github.com/egorbelovziiik-design/WinLauncher/releases/tag/beatforge-windows-latest>
* прямая ссылка: <https://github.com/egorbelovziiik-design/WinLauncher/releases/download/beatforge-windows-latest/BeatForgeStudio.exe>

Один файл ~190 МБ, установка не нужна: скачал → запустил. Рядом лежит
`BeatForgeStudio.exe.sha256` для проверки целостности:

```
certutil -hashfile BeatForgeStudio.exe SHA256
```

Workflow `.github/workflows/build-windows-exe.yml` запускается на `windows-latest`
при пуше в ветку, вручную (Actions → Build Windows EXE) и на тег `v*` (тогда
релиз публикуется с заметками). Перед публикацией он проверяет, что внутри
архива есть PyQt6/`core`/`ui`, и держит собранное приложение запущенным 12 с —
падение на старте ломает сборку.

**Локально** (на своей Windows-машине):

```
pip install -r requirements.txt
pyinstaller build.spec
```

Получится один файл `dist/BeatForgeStudio[.exe]`. `assets/` упакован внутрь;
FFmpeg берётся из `imageio-ffmpeg` (он уже в зависимостях). Настройки и кэш
хранятся вне сборки и не теряются при обновлении.

Из сборки исключены неиспользуемые части, чтобы не раздувать файл: QtWebEngine,
QtQuick/QML, QtMultimedia, QtDesigner, QtSql, OpenCV, moviepy, numba/llvmlite.
Поэтому скорость «с сохранением тональности» и pitch-shift (librosa) в exe
недоступны — код это корректно обрабатывает; для полного набора запускайте
из исходников.

---

## Тестирование без звуковой карты

Движок аудио автоматически переходит в «немой» режим, если PortAudio
недоступен: позиция, микс и экспорт работают, звук не выводится. Это позволяет
запускать приложение в CI и на серверах:

```
python main.py --safe
```

---

## Лицензии

Проект учебный. Сторонние компоненты (PyQt6 — GPL/коммерческая, FFmpeg —
LGPL/GPL в зависимости от сборки, yt-dlp — Unlicense, librosa, moviepy,
Pillow, OpenCV и др.) распространяются под своими лицензиями; при
распространении сборки их условия нужно соблюдать.
