# MADorigg 😈⚔️

Эпичная битва двух смайликов за контроль над экраном — десктопное пранк-приложение
на Python + pygame. **Весь звук генерируется numpy, все спрайты рисуются
`pygame.draw` — ни одного внешнего файла ресурсов.**

> ⚠️ **ВНИМАНИЕ:** приложение резко выкручивает громкость системы на 100%
> и орёт эпичной музыкой ≈ 40 секунд. Громкость **гарантированно
> восстанавливается** в `finally` — даже при падении и при Alt+F4.
> Не запускай в наушниках у спящего друга. Безвредно и ничего не удаляет
> (самоудаление — опция, по умолчанию выключено: `SELF_DELETE = False`).

## 🚀 Запуск (Windows 10/11)

```bat
pip install -r requirements.txt
python main.py
```

Выход в любой момент: **Alt+F4** или **ESC** — громкость, эквалайзер
и курсор восстановятся.

## 📦 Сборка .exe (PyInstaller)

```bat
build_exe.bat
```

или вручную:

```bat
pip install pyinstaller
pyinstaller --noconfirm --onefile --windowed --name MADorigg ^
  --collect-all pycaw --collect-all comtypes --collect-all numpy ^
  --collect-all pygame main.py
```

Готовый файл: `dist\MADorigg.exe`.

> В CI (GitHub Actions) `.exe` собирается автоматически: вкладка
> **Actions → Build MADorigg .exe → Run workflow** → скачайте артефакт
> `MADorigg-exe`.

## 🎬 Сценарий (≈42 секунды)

| Фаза | Время | Что происходит |
|------|-------|----------------|
| 0 | 0–2 c | Полноэкранный topmost, курсор скрыт, **тихо** сохраняется громкость/EQ, чёрный экран |
| 1 | 2–5 c | Громкость → 100% за 1 c, bass boost +12 dB / treble +6 dB, взрывная музыка 140 BPM, белая вспышка, глитч-титр **MADorigg** |
| 2 | 5–8 c | Экран делится вертикальной молнией: слева красный 😡, справа синий 😎, гонг + рёв толпы, «БИТВА ЗА ЭКРАН НАЧИНАЕТСЯ!» |
| 3 | 8–30 c | Армии человечков 5–10/сек на сторону, рукопашная (дистанция <15 px → вспышка, 50/50 гибель), огненные шары 🔥 и ледяные лучи ❄️ каждые 2 c, мерцающая линия фронта, глитчи каждые 5–8 c, тряска экрана, счёт **RED ⚔ BLUE**, напряжение +5%/5 c |
| 4 | 30–38 c | Победитель ×3 (случайно 50/50), мега-атака, взрыв проигравшего (50+ частиц), победитель прыгает в центре 300×300 среди фейерверков, «КРАСНЫЕ/СИНИЕ ПОБЕДИЛИ!» золотом |
| 5 | 38–42 c | Fade-to-black, fade-out звука, **восстановление громкости/EQ/курсора**, «MADorigg was here 😈», выход |

## 🗂 Структура

```
MADorigg/
├── main.py                 # Точка входа, главный цикл, try/finally restore
├── config.py               # Константы, длительности фаз, цвета
├── requirements.txt
├── audio/
│   ├── volume_control.py   # Громкость Windows (pycaw), fade в потоке, restore
│   ├── sound_generator.py  # Все звуки: numpy → pygame.sndarray
│   └── music_player.py     # Фоновая музыка + эффекты, кеш, fade-out
├── visuals/
│   ├── screen.py           # FULLSCREEN|NOFRAME, HWND_TOPMOST, курсор
│   ├── particles.py        # До 1000 частиц, пресеты (fire/ice/spark/confetti/smoke)
│   ├── effects.py          # Тряска, вспышки, глитч (RGB-разъезд), виньетка
│   ├── background.py       # Градиенты красный/синий + молния-разделитель
│   └── text_renderer.py    # Глитч, пульсация, счёт, победный текст
├── entities/
│   ├── boss.py             # Смайлики-боссы (pygame.draw, анимации, ауры)
│   ├── soldier.py          # Человечки 20×20, 2 кадра бега, крестик ✖
│   ├── projectile.py       # fireball / ice_beam / mega
│   └── army.py             # Волны, подсчёт, boost/kill в финале
├── battle/
│   ├── battle_manager.py   # Главный цикл, мир, переходы фаз
│   ├── collision.py        # Melee <15 px, действие снарядов
│   └── front_line.py       # Зигзаг-линия, сдвиг по силе армий, 20/80% → финал
├── phases/
│   ├── phase_intro.py      # Фазы 0–1
│   ├── phase_bosses.py     # Фаза 2
│   ├── phase_battle.py     # Фаза 3
│   ├── phase_finale.py     # Фаза 4
│   └── phase_cleanup.py    # Фаза 5
├── build_exe.bat           # Сборка .exe в один клик (Windows)
└── README.md
```

## ⚙️ config.py — главные настройки

| Константа | Значение | Описание |
|-----------|----------|----------|
| `PHASE_INTRO_DURATION` | 5.0 | Фазы 0–1 |
| `PHASE_BOSSES_DURATION` | 3.0 | Появление боссов |
| `PHASE_BATTLE_DURATION` | (15.0, 22.0) | Рандом длительности битвы |
| `PHASE_FINALE_DURATION` | 5.0 | Финал |
| `PHASE_CLEANUP_DURATION` | 4.0 | Очистка и выход |
| `MAX_SOLDIERS` | 150 | Лимит армии на сторону |
| `MAX_PARTICLES` | 1000 | Лимит частиц |
| `SELF_DELETE` | False | Опциональное удаление .exe после выхода |

## 🧪 Тестирование без Windows

`volume_control` автоматически откатывается на заглушку вне Windows —
приложение полностью запускается и в Linux/macOS (громкость системы,
разумеется, не меняется):

```bash
pip install -r requirements.txt
python main.py
```

## 🔧 Технические детали

- **Звук:** каждый эффект — numpy-массив → `pygame.sndarray.make_sound`.
  Музыка строится по паттерну: kick/snare/hat + бас (A–F–C–G, ля минор)
  + мелодия, 140/160 BPM.
- **Громкость:** `pycaw.IAudioEndpointVolume`, плавное изменение — в
  `threading.Thread`; `restore_volume()` останавливает fade и ставит
  сохранённое значение. Вызывается в `finally` в `main.py`.
- **Topmost:** `ctypes.windll.user32.SetWindowPos(hwnd, -1, ...)` —
  HWND_TOPMOST, SWP_NOMOVE|SWP_NOSIZE.
- **Глитч:** горизонтальные полосы со сдвигом + разъезд RGB-каналов
  через `BLEND_RGBA_MULT/ADD`.
- **Столкновения:** пространственная сетка 40 px — O(n) вместо O(n²).
