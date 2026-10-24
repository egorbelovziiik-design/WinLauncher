# 🛡️ КП-KingGrief

Discord-бот для автоматизированного контроля персонала сервера **KingGrief**:
отчёты, предупреждения, снятие с должности, статистика и интерактивная панель управления.

Полностью на русском языке. Discord Slash Commands, Embeds, кнопки, меню выбора, пагинация.

---

## 📦 Возможности

| Раздел | Что умеет |
|---|---|
| 📋 **Отчёты** | Автоматическая регистрация отчётов в `отчеты-кп` по настраиваемым критериям, принятие/отклонение кнопкой, реакция на редактирование и удаление сообщения |
| 📊 **`/ot`** | Кто сдал и кто не сдал. Фильтры: период, пользователь, роль (комбинируются). Кнопки: 🔄 Обновить · ✅ Сдали · ❌ Не сдали · 📊 Статистика · 📥 Скачать TXT. Пагинация |
| ⚠️ **Варны** | `/warn 1·2·3`, `/addwarn`, `/unwarn`, `/warns`, `/clearwarns`. После 3-го варна — снятие с должности **после подтверждения администратора** |
| 🎭 **Контроль ролей** | Автоопределение сотрудников по ролям, обновление должности, сохранение истории при уходе |
| 📈 **Статистика** | `/stats`, `/top`, `/history`, `/check`, `/profile` |
| 🎛️ **Панель** | `/panel` — интерактивное управление без ручного ввода команд |
| ⏰ **Автопроверка** | Каждое воскресенье 20:00 (Europe/Moscow), время настраивается. Варны за отсутствие отчёта **не выдаются** |
| 💾 **База данных** | SQLite: `staff`, `reports`, `warnings`, `punishments`, `settings`, `audit_logs`, `staff_roles`, `report_periods` + резервные копии и восстановление |
| 🔐 **Права** | 6 уровней, настраиваются по Discord ID ролей |

---

## 📁 Структура проекта

```
KP-KingGrief/
├── main.py                  # точка входа
├── config.py                # конфигурация и значения по умолчанию
├── requirements.txt
├── .env.example
├── Dockerfile
├── docker-compose.yml
├── pytest.ini
│
├── bot/
│   ├── client.py            # KingGriefBot, автопереподключение, single-instance lock
│   ├── events.py            # on_message / on_message_edit / on_member_update …
│   └── permissions.py       # уровни прав и декоратор @require
│
├── commands/
│   ├── reports.py           # /ot  /report  /reports
│   ├── warnings.py          # /warn /addwarn /unwarn /warns /clearwarns
│   ├── staff.py             # /staff /profile /check /history
│   ├── statistics.py        # /stats /top /panel /help
│   └── settings.py          # /setup /settings /sync /permissions
│
├── database/
│   ├── connection.py        # async-обёртка SQLite, бэкапы, восстановление
│   ├── migrations.py        # версионная схема БД
│   └── models.py            # dataclasses + репозитории
│
├── services/
│   ├── report_tracker.py    # классификация и учёт отчётов
│   ├── warning_manager.py   # варны и снятие с должности
│   ├── role_manager.py      # поиск ролей, синхронизация, безопасное снятие
│   └── scheduler.py         # APScheduler: еженедельная проверка, бэкапы, watchdog
│
├── utils/
│   ├── embeds.py            # все Embed'ы
│   ├── pagination.py        # пагинация
│   ├── periods.py           # периоды (день/неделя/месяц) и тайм-зоны
│   └── logging.py           # ротация логов
│
├── build/
│   ├── build_exe.py         # сборка PyInstaller
│   ├── build.bat            # сборка Windows EXE одним кликом
│   └── KP-KingGrief.spec
│
└── tests/                   # 111 тестов (pytest + pytest-asyncio)
```

> CI-workflow сборки EXE лежит в **корне репозитория**:
> `.github/workflows/build-windows.yml` — GitHub читает workflow только оттуда.

---

## 1️⃣ Как создать Discord-бота

1. Откройте <https://discord.com/developers/applications>.
2. Нажмите **New Application** → имя `КП-KingGrief` → **Create**.
3. Перейдите в раздел **Bot** (слева).
4. Нажмите **Reset Token** → **Copy** — это ваш `DISCORD_TOKEN`.

> ⚠️ Токен — это пароль бота. Никому его не показывайте и не кладите в Git.

## 2️⃣ Как получить токен

Токен выдаётся **один раз** при нажатии **Reset Token** в разделе **Bot**.
Если вы его потеряли — нажмите **Reset Token** ещё раз (старый перестанет работать).

Впишите его в файл `.env`:

```env
DISCORD_TOKEN=MTIzNDU2Nzg5.XXXXXX.ваш_токен
```

## 3️⃣ Как включить Message Content Intent

Раздел **Bot** → блок **Privileged Gateway Intents** → включите:

- ✅ **MESSAGE CONTENT INTENT** — бот читает текст отчётов;
- ✅ **SERVER MEMBERS INTENT** — бот видит роли участников.

Без этих двух переключателей бот **не запустится** и выведет ошибку
`PrivilegedIntentsRequired`.

## 4️⃣ Как включить Server Members Intent

Тот же блок **Privileged Gateway Intents** → **SERVER MEMBERS INTENT**.

Бот включает оба интента автоматически:

```python
intents.message_content = True
intents.members = True
```

## 5️⃣ Как пригласить бота на сервер

1. Раздел **OAuth2** → **OAuth2 URL Generator**.
2. В **Scopes** отметьте: `bot`, `applications.commands`.
3. В **Bot Permissions** отметьте права из следующего пункта.
4. Скопируйте ссылку из **Generated URL**, откройте её в браузере,
   выберите сервер **KingGrief** и нажмите **Authorize**.

## 6️⃣ Какие права предоставить боту

Минимальный набор:

| Право | Зачем |
|---|---|
| `View Channels` | видеть каналы |
| `Send Messages` | отвечать на команды |
| `Embed Links` | красивые Embed'ы |
| `Attach Files` | выгрузка TXT в `/ot` |
| `Read Message History` | историческое сканирование канала отчётов |
| `Manage Roles` | **снятие контролируемой роли после 3 варнов** |
| `Use Application Commands` | slash-команды |

**Важно про иерархию ролей:** роль бота должна стоять в списке ролей
**выше** всех контролируемых ролей (Админ, Гл.Модер, … Стажер).
Иначе Discord не позволит снять роль, и бот честно сообщит об этом.

Дополнительно поставьте галочку **Administrator** только если полностью
доверяете боту — для работы она не требуется.

## 7️⃣ Как настроить роли

Бот находит контролируемые роли **по названию**:

```
Стажер · Мл.Модер · Модер · Ст.Модер · Гл.Модер · Мл.Админ · Админ
```

Дополнительно (по умолчанию выключены, включаются кнопкой в `/setup`):

```
Д.Модер · Д.Ст.Модер · Д.Гл.Модер · Д.Админ
```

Права доступа задаются корзинами ролей — `/setup` → **🎭 Роли прав**:

| Уровень | Роли по умолчанию | Что может |
|---|---|---|
| 👑 OWNER | владелец (`OWNER_ID` в `.env`) | всё |
| 🛡️ ADMIN | Админ, Гл.Модер | варны, снятие ролей, настройки |
| 🎖️ SENIOR | Ст.Модер | просмотр отчётов, персонала, варнов, `/sync` |
| 👮 MOD | Модер, Мл.Модер | свой профиль, свои отчёты и варны |
| 🎓 STAFF | Стажер | свой профиль и свои отчёты |
| — | остальные | только `/help` |

> Корзины заполняются автоматически при первом запуске. Всё, что вы
> настроите вручную, бот **никогда не перезаписывает**.

## 8️⃣ Как настроить каналы

Бот ищет категорию **«Кп Сервера»** и каналы внутри неё по названиям:

| Канал | Назначение |
|---|---|
| `отчеты-кп` | регистрация отчётов |
| `информация-кп` | справочный канал |
| `объявления-администрации` | объявления |
| `анти-чит-сервера` | анти-чит |
| `стафф-чат` | уведомления «отчёт зарегистрирован», «новый сотрудник» |
| `варны-кп` | публикация всех предупреждений и снятий |

Всё находится автоматически при запуске. Вручную — команда **`/setup`**,
кнопка нужного канала → селектор каналов.

Проверить результат: **`/sync`** или **`/settings`**.

---

## 🚀 Запуск из исходников

```bash
cd KP-KingGrief
python -m venv .venv
# Windows:  .venv\Scripts\activate
# Linux/mac: source .venv/bin/activate

pip install "discord.py>=2.4,<3" "python-dotenv>=1.0" "APScheduler>=3.10,<4" tzdata
cp .env.example .env      # и вписать DISCORD_TOKEN
python main.py
```

## 🧪 Тесты

```bash
pip install pytest pytest-asyncio
python -m pytest tests -v
```

111 тестов покрывают: базу данных и миграции, автопоиск каналов/ролей,
классификацию отчётов, `/ot` со всеми фильтрами, варны 1/2/3, снятие роли
и защиту посторонних ролей, права, еженедельную проверку, панель,
события сервера и упаковку.

---

## 🖥️ 9. Как запустить EXE (Windows 10/11)

### Сборка

```bat
cd KP-KingGrief
build\build.bat
```

Готовый файл: `dist\КП-KingGrief.exe`

> **PyInstaller не поддерживает кросс-компиляцию.** EXE собирается только
> на Windows. Для автоматической сборки есть GitHub Actions:
> `.github/workflows/build-windows.yml` → вкладка **Actions** →
> **Build Windows EXE** → **Run workflow** → артефакт `KP-KingGrief-windows`.

### Запуск

1. Создайте отдельную папку, например `C:\KingGriefBot\`.
2. Скопируйте туда `КП-KingGrief.exe`.
3. Рядом создайте файл `.env`:
   ```env
   DISCORD_TOKEN=ваш_токен
   OWNER_ID=ваш_discord_id
   TEST_GUILD_ID=id_вашего_сервера
   ```
4. Запустите `КП-KingGrief.exe` двойным кликом.

Бот создаст рядом папки `data\` (база и бэкапы) и `logs\`.

**Токен не встраивается в EXE.** Если `.env` отсутствует или токен пустой,
программа печатает понятную инструкцию и завершается с кодом `1` —
это проверено автоматическим тестом.

### Автозапуск при включении компьютера

`Win + R` → `shell:startup` → создайте ярлык на `КП-KingGrief.exe`.

Либо через Планировщик заданий: триггер «При запуске компьютера»,
действие — путь к EXE, «Выполнять вне зависимости от регистрации пользователя».

---

## ☁️ 10. Как запустить на VPS 24/7

### Вариант A — Docker (рекомендуется)

```bash
# на VPS
git clone <ваш-репозиторий> && cd <репозиторий>/KP-KingGrief
cp .env.example .env && nano .env      # вписать DISCORD_TOKEN

docker compose up -d --build
docker compose logs -f                 # смотреть логи
```

В `docker-compose.yml` задан `restart: unless-stopped`, поэтому бот:

- ✅ поднимется после перезагрузки VPS;
- ✅ перезапустится после сбоя;
- ⛔ останется выключенным, если вы остановили его сами (`docker compose stop`).

База и логи живут в именованных томах `kp-data` и `kp-logs` — данные
переживают пересоздание контейнера.

### Вариант B — systemd

```bash
sudo mkdir -p /opt/kp-kinggrief
sudo cp -r . /opt/kp-kinggrief
cd /opt/kp-kinggrief
sudo python3 -m venv .venv
sudo .venv/bin/pip install "discord.py>=2.4,<3" python-dotenv "APScheduler>=3.10,<4" tzdata
sudo nano .env          # DISCORD_TOKEN=...
```

`/etc/systemd/system/kp-kinggrief.service`:

```ini
[Unit]
Description=KP-KingGrief Discord Bot
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
WorkingDirectory=/opt/kp-kinggrief
ExecStart=/opt/kp-kinggrief/.venv/bin/python main.py
Restart=always
RestartSec=10
EnvironmentFile=/opt/kp-kinggrief/.env

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now kp-kinggrief
sudo systemctl status kp-kinggrief
journalctl -u kp-kinggrief -f
```

### Почему это работает без вашего компьютера

Бот работает на сервере VPS, а не на вашем ПК. Достаточно, чтобы VPS был
включён. `restart: unless-stopped` (Docker) или `Restart=always` (systemd)
поднимают процесс после любого сбоя и после перезагрузки машины.

Дополнительно внутри бота:

- **автопереподключение** к Discord Gateway с экспоненциальной задержкой;
- **watchdog** — периодическая проверка БД и задержки шлюза;
- **single-instance lock** — вторая копия не запустится и не повредит базу;
- **резервные копии БД** каждые 12 часов и восстановление при повреждении.

---

## ⚙️ Настройки без правки кода

`/settings` — просмотр и изменение параметров:

```
/settings параметр:report_min_length значение:60
/settings параметр:weekly_check_hour значение:18
/settings параметр:max_warnings значение:3
/settings параметр:report_detection_mode значение:keyword
```

| Параметр | По умолчанию | Смысл |
|---|---|---|
| `report_detection_mode` | `smart` | `smart` · `prefix` · `keyword` · `length` · `everything` |
| `report_min_length` | 40 | минимальная длина сообщения |
| `report_min_words` | 5 | минимальное число слов |
| `report_min_attachments` | 0 | минимальное число вложений |
| `report_require_attachment` | `false` | требовать скриншот |
| `report_ignore_bots` | `true` | не считать сообщения ботов |
| `max_warnings` | 3 | варнов до снятия |
| `auto_demote_on_max_warnings` | `true` | снимать роль автоматически |
| `require_confirm_demote` | `true` | требовать подтверждение админа |
| `allow_demote_above_executor` | `false` | разрешить снятие ролей выше инициатора |
| `weekly_check_enabled` | `true` | еженедельная проверка |
| `weekly_check_day` / `hour` / `minute` | `sun` / 20 / 0 | время проверки |
| `backup_interval_hours` | 12 | периодичность бэкапов |
| `backup_keep` | 14 | сколько копий хранить |

---

## 🧾 Как определяется отчёт

По умолчанию режим **`smart`** — сообщение считается отчётом, если:

1. автор — сотрудник с контролируемой ролью и не бот;
2. есть префикс («Отчёт», «Отчет», «📋») **или** ключевое слово
   («отчёт», «отработан», «выдал», «наказал», …);
3. длина ≥ 40 символов и ≥ 5 слов;
4. выполнено требование по вложениям (если настроено).

Обычная переписка в `отчеты-кп` отчётом **не считается**.

---

## 🔒 Безопасность снятия ролей

Бот снимает роль только когда выполнены **все** условия:

1. роль есть в списке контролируемых (посторонние роли не трогаются никогда);
2. у бота есть право **Manage Roles**;
3. роль бота в иерархии **выше** снимаемой;
4. роль не управляется интеграцией/ботом (`role.managed == False`);
5. инициатор имеет **Manage Roles** или **Administrator**;
6. роль не выше роли инициатора (если не включён `allow_demote_above_executor`);
7. снятие подтверждено уполномоченным администратором кнопкой.

Если у сотрудника несколько контролируемых ролей — появляется выпадающий
список, и администратор выбирает конкретную роль. Снимается **только она**.

Каждое действие пишется в `punishments` и `audit_logs`.

---

## 🩺 Диагностика

| Симптом | Причина и решение |
|---|---|
| `❌ Не найден токен бота` | нет `.env` или пустой `DISCORD_TOKEN` |
| `PrivilegedIntentsRequired` | не включены Message Content / Server Members Intent |
| `Неверный токен бота` | токен сброшен или скопирован с пробелами |
| Команд не видно в Discord | не указан `TEST_GUILD_ID` — глобальная синхронизация занимает до часа |
| `Бот не может снять роль` | роль бота ниже снимаемой, либо нет Manage Roles |
| `Роль не является контролируемой` | название роли на сервере отличается от списка |
| Отчёты не регистрируются | проверьте критерии: `/settings параметр:report_detection_mode` |

Логи: `logs/bot.log` (всё) и `logs/error.log` (только ошибки).

---

## 📜 Лицензия

Проект создан для Discord-сервера **KingGrief**.
