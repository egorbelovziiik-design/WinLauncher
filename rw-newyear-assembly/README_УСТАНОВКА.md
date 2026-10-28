# 🎄 Новогодняя Сборка KINGGRIEF v6.0.1 (FunTime × ReallyWorld) + Нейро-Античит **KingАЧ v8.1**

Полностью готовая под ключ сборка: ядро `KingGriefCore.jar` **v6.0.1**, античит `KingAC.jar` **v8.1**
(8 Схем Инжекта + 3D Raycast Хитбокса), сеть Хаб ↔ 1 Гриф (`KingHub.jar` v1.2.0 + `KingConnect.jar` v2.2.0),
`KingClear.jar` v1.0.1, Мега-Ресурспак и облачная синхронизация с сайтом **https://kinggrief.vercel.app**.

---

## 🎨 Ресурспак v6.4 — новые модели предметов

Новый пак: [скачать kinggrief-mega-rp-v64.zip](https://kinggrief.vercel.app/kinggrief-mega-rp-v64.zip).
Его копия лежит в `resourcepack/kinggrief-mega-rp-v64.zip`. В нём есть модели новых шаров,
свитков, тотемов, МЕГА-предметов, мечей и стрел, а также привязки моделей для кирок,
брони и элитр. Пак предназначен для **Minecraft Java Edition**, не Bedrock.

Игроку: в игре откройте **Настройки → Наборы ресурсов → Открыть папку**, положите туда
ZIP **не распаковывая**, включите его и поставьте выше остальных пользовательских паков.
На старых версиях игры может появиться предупреждение о версии пака. Если сервер навязывает
старый пак поверх нового, обновите настройку сервера или в меню изменения сервера выберите
«Ресурспаки сервера: отключены» и используйте новый пак локально.

Для нового сервера в этой сборке пути и SHA-1 уже настроены. Для существующего сервера
**не заменяйте весь конфиг**: поменяйте только эти строки в `server.properties`:

```properties
resource-pack=https\://kinggrief.vercel.app/kinggrief-mega-rp-v64.zip
resource-pack-sha1=21b2343076c751bf392ac3bd47528c59988fb92c
```

И в `plugins/KingGriefCore/config.yml` (а также в `plugins/KingHub/config.yml`, если
используется отдельный хаб):

```yaml
resourcepack-url: "https://kinggrief.vercel.app/kinggrief-mega-rp-v64.zip"
resourcepack-sha1: "21b2343076c751bf392ac3bd47528c59988fb92c"
```

Перезапустите сервер. Старый адрес `kinggrief-mega-rp.zip?v=63` оставлен без изменений,
чтобы установленные ранее плагины не получали неверную контрольную сумму; он содержит старый пак.

---

## ⚡ БЫСТРЫЙ СТАРТ (3 шага)

1. Распакуйте `rw-newyear-full-assembly.zip` — внутри **плоская структура**: `plugins/`, `resourcepack/`,
   `start-server.sh`, `server.properties`, `spigot.yml`, `TechWorksStub.jar`.
2. Скопируйте **всё содержимое архива в корень вашего сервера** (папка, где лежит `paper.jar` / `purpur.jar`).
   Главный файл античита должен оказаться здесь: **`plugins/KingAC.jar`**.
3. Перезапустите сервер. В консоли появится:

```
[KingAC] ==================================================================
[KingAC]  KingAC v8.1 - PROFESSIONAL NEURAL ANTI-CHEAT ENABLED
[KingAC]  * 12 LIVE INJECTION SCHEMES (360 / WALL / ORBIT / ZENITH / SWARM
[KingAC]  * Commands: /ac /kac /kingac /inject /report /reports
```

---

## 🛡 ГЛАВНОЕ: ЧТОБЫ АНТИЧИТ ТОЧНО РАБОТАЛ В ПАПКЕ `plugins/`

| Проверьте | Как должно быть |
|---|---|
| Файл лежит в `plugins/` | `plugins/KingAC.jar` (ровно так, в корне папки плагинов, **не** в подпапке) |
| Старый античит удалён | Файла `plugins/King_antisoft.jar` быть **не должно** (иначе два античита конфликтуют) |
| Версия ядра | Spigot / Paper / Purpur **1.16.5 – 1.21.x** (`api-version: '1.16'` — плагин грузится везде) |
| Java | Java 8+ для 1.16.5, Java 17+ для 1.18+, Java 21 для 1.20.5+ |
| Перезапуск | После добавления `.jar` нужен **полный рестарт**, `/reload` не подходит |
| Команда в игре | `/ac` → откроется центр детектов (для состава и OP) |

**Команда «не найдена»?** Наберите в игре `/ac status` или `/kingac` — если ответа нет, откройте
`latest.log` и найдите строку `KingAC`. Причина почти всегда одна из трёх:
файл не в `plugins/`, рядом лежит старый `King_antisoft.jar`, или сервер запущен без рестарта.

---

## 🎮 КОМАНДЫ KingАЧ v8.0

### Для всех игроков
| Команда | Действие |
|---|---|
| `/report <ник>` | Жалоба на читера (открывает GUI выбора причины) |
| `/жалоба <ник>`, `/репорт <ник>` | Русские алиасы жалобы |
| `/ac` | Информация об античите + как пожаловаться |

### Для состава (мл. модер+, OP, `kingac.staff`)
| Команда | Действие |
|---|---|
| `/ac` или `/kac` или `/kingac` или `/ач` или `/античит` или `/кингач` | Центр детектов: подозреваемые, VL, настройки |
| `/ac inject` | **GUI выбора цели** для схем инжекта |
| `/ac inject <ник>` | **GUI выбора одной из 12 схем** для игрока |
| `/ac inject <ник> <схема>` | Запуск схемы: `back wall orbit zenith swarm hitbox backtrack cascade auto all` |
| `/inject`, `/инжект`, `/схемы` | Быстрый вход в схемы инжекта |
| `/ac schemes` | Вкл/выкл модулей всех 12 схем + авто-прогон |
| `/ac monitor <ник>` | **Живой HUD в ActionBar**: VL, CPS, пинг, схема, последний детект (повторно — выкл.) |
| `/ac top` | Топ-10 подозреваемых по VL |
| `/ac checks` | Полный список всех проверок античита |
| `/ac status` | Статистика сессии (детекты, фантомы, авто-баны, жалобы) |
| `/ac log` | Последние записи `plugins/KingAC/detections.log` |
| `/ac info <ник>` | Нейро-досье: VL по проверкам, микро-дёргания, попадания по фантомам |
| `/ac probe <ник> [схема]` | Быстрый инжект (Спина-360° + Сквозь-Стену) |
| `/ac ban <ник> [причина]`, `/ac unban <ник>` | Авто-бан / разбан силами KingАЧ |
| `/ac alerts` | Вкл/выкл алерты в чате |
| `/reports` | GUI активных жалоб |

---

## 💉 12 СХЕМ ИНЖЕКТА (новое в v8.1)

Схемы спавнят **невидимых микро-фантомов** в точки, куда честный клиент физически не может
отправить пакет атаки. Любой чит-клиент бьёт по ним — и мгновенно получает детект и VL.

| # | ID | Схема | Что ловит |
|---|---|---|---|
| 1 | `back` | Спина-360° | Rage/KillAura на 360° (фантом следует за затылком 20 тиков) |
| 2 | `wall` | Сквозь-Стену | WallHit / удары сквозь стекло Трапки, Пласт, кабинки |
| 3 | `orbit` | Орбита-Вихрь | AimLock / AimBot-магнит (фантом кружится 28°/тик) |
| 4 | `zenith` | Верхний Купол +85° | SilentAura / MultiPoints (цель над макушкой вне экрана) |
| 5 | `swarm` | Мульти-Рой x4 | MultiAura / SwitchAura (4 фантома во всех слепых зонах) |
| 6 | `hitbox` | Байт-Хитбокс | HitBoxes (+0.5…+2.0) / SilentAim / удары в воздух |
| 7 | `backtrack` | Призрак-Отступ | Reach + Backtrack (фантом отпрыгивает на 4.2 м) |
| 8 | `nadir` | Надир-Пол | Ауры «голова вниз» (фантом ПОД НОГАМИ, -1.15 м) |
| 9 | `twinwall` | Двойная-Стена | WallHit сквозь 2 стены сразу (фронт + фланг 90°) |
| 10 | `swap` | Фантом-Обман | Спереди 2.3 м → телепорт за спину (TriggerBot/SwitchAura) |
| 11 | `sweep` | Нижний-Луч | Low-Aim ауры (фантом на уровне ступней, дуга 2.9 м) |
| 12 | `cascade` | Каскад «Полный Нейро-Скан» | Прогон всех 10 боевых схем подряд с отчётом в чат и ActionBar |

Авто-прогон: KingАЧ сам каждые 3 сек. подбирает схему для игроков под жалобой / Deep Watch / VL > 4,
а **каждый дерущийся игрок раз в 12 сек. автоматически получает фантом-приманку** — читер с аурой неизбежно заденет невидимку.

---

## 🧠 ВСЕ ПРОВЕРКИ v8.0

**Бой:** KillAura (360° / Мульти / в GUI / со щитом-едой / без взмаха руки), Reach, HitBoxes,
SilentAura, Backtrack, **WallHit с жёстким блоком урона (голосование 11 лучей: 8+ закрытых = урон не регает)**, **TriggerBot**, AutoClicker (CPS + макрос + **куртозис**),
Criticals (пакет-крит + **микро-хоп**), KeepSprint, AimBot, Aim (микро-дёргание / GCD / горизонт-лок),
**Вертикальный Aim v8.1: Pitch-Snap >45°/тик, «Голова Вниз-Вверх» (флипы pitch), Вертикальный Лок ±75-90° во время ударов**, FastBow.

**Движение:** Fly, AirJump, Speed/Bhop, AirStrafe, Jesus, NoSlow, **Phase (NoClip)**, **AntiKB (вертикаль + горизонталь)**,
**Timer/Blink (баланс move-пакетов)**, Step.

**Мир и инвентарь:** Scaffold, **FastPlace**, **GuiMove (ИнвХод)**, AutoTotem, XRay (скрытые жилы).

**Точный 3D Raycast Хитбокса** — ядро v7.0 сохранено полностью: игрок должен бить **в игрока, а не в воздух**,
удары мимо модели (HitBox / SilentAura / Backtrack) **не регистрируются вообще**, история хитбоксов жертвы
хранится 18 тиков с пинг-компенсацией до 560 мс.

---

## ⚙ ФАЙЛЫ И НАСТРОЙКИ

```
plugins/KingAC.jar                <- сам античит v8.0
plugins/KingAC/config.yml         <- пороги VL, авто-бан, setback, вкл/выкл каждой из 8 схем
plugins/KingAC/bans.yml           <- база авто-банов KingАЧ
plugins/KingAC/detections.log     <- полный лог детектов (время, проверка, игрок, VL, пинг, схема)
```

Ключи `config.yml`: `settings.auto-ban-enabled`, `settings.auto-ban-vl` (18),
`settings.auto-ban-duration-seconds` (30 дней), `settings.movement-setback-enabled`,
`settings.micro-head-twitch-check`, `settings.ray-hitbox-air-block`, `settings.auto-inject-schemes`,
`settings.max-autoban-ping-ms` (420), `settings.report-cooldown-seconds` (25),
`schemes.back/wall/orbit/zenith/swarm/hitbox/backtrack/nadir/twinwall/swap/sweep/cascade`.

---

## 📦 СОСТАВ СБОРКИ

| Файл | Версия | Назначение |
|---|---|---|
| `plugins/KingAC.jar` | **8.1.0** | Нейро-античит KingАЧ + 12 Схем Инжекта (1.16.5–1.21.x) |
| `plugins/KingGriefCore.jar` | **6.0.1** | Ядро: ранги, киты, кейсы, /tm, /duel, Божья Аура, модерация, мост KingАЧ |
| `plugins/KingClear.jar` | 1.0.1 | Отдельный `/clear` |
| `plugins/KingGriefBridge.jar` | 2.x | Облачный мост на сайт kinggrief.vercel.app |
| `plugins/KingConnect.jar` | 2.2.0 | Сервер 1 Гриф: приём переходов из Хаба |
| `KingHub.jar` | 1.2.0 | Хаб: новогодняя карта, компас, НПС (ставится **в папку Хаба**) |
| `resourcepack/` | v6.0 | Мега-РП (115+ 3D предметов) |
| `TechWorksStub.jar` | 1.0 | Заглушка `⚙ Тех работы!` |

---

## ☁ САЙТ И ОБЛАКО

* Сайт и админ-панель: **https://kinggrief.vercel.app** (`/admin`: `admin` / `KingGrief-2026`)
* Прямые ссылки: `https://kinggrief.vercel.app/KingAC.jar`, `/KingGriefCore.jar`, `/rw-newyear-full-assembly.zip`
* Discord: **https://discord.gg/7Cugj4X3eU**
