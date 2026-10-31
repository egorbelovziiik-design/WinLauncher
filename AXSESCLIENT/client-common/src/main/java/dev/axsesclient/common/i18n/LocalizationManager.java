package dev.axsesclient.common.i18n;

import java.util.HashMap;
import java.util.Map;

public final class LocalizationManager {
    private String activeLocale = "ru_RU";
    private final Map<String, Map<String, String>> dictionaries = new HashMap<String, Map<String, String>>();
    private final Map<String, String> ruSettings = new HashMap<String, String>();
    private final Map<String, String> ruOptions = new HashMap<String, String>();

    public LocalizationManager() {
        Map<String, String> en = new HashMap<String, String>();
        en.put("gui.title", "AXSESCLIENT");
        en.put("gui.subtitle", "AURORA v2.5");
        en.put("gui.search_placeholder", "Search 96 modules (e.g. KillAura, ESP, Target, Zoom)...");
        en.put("gui.favorites", "Favorites");
        en.put("gui.hud_editor", "HUD Editor");
        en.put("gui.themes", "Themes");
        en.put("gui.configs", "Configs");
        en.put("gui.diag", "System");
        en.put("gui.reset_all", "Reset Module");
        en.put("gui.sort_default", "Order: Default");
        en.put("gui.sort_az", "Order: A-Z");
        en.put("gui.sort_enabled", "Order: Active");
        en.put("gui.open_key", "Menu: ");
        en.put("gui.press_key", "Press key...");
        en.put("gui.no_bind", "—");
        en.put("gui.no_results", "No matching modules found");
        en.put("gui.settings_hint", "Right-click or click arrow to expand settings");
        en.put("gui.lang_card_title", "Interface Language / Язык клиента");
        en.put("gui.lang_card_desc", "Instant switch between Russian and English across all menus & HUD");
        en.put("notif.module_enabled", "Enabled");
        en.put("notif.module_disabled", "Disabled");
        en.put("cat.combat_training", "Combat");
        en.put("cat.movement", "Movement");
        en.put("cat.render", "Visuals & ESP");
        en.put("cat.player", "Player");
        en.put("cat.world", "World");
        en.put("cat.utility", "Utility");
        en.put("cat.hud", "HUD");
        en.put("cat.settings", "Settings");
        en.put("menu.singleplayer", "SINGLEPLAYER");
        en.put("menu.multiplayer", "MULTIPLAYER");
        en.put("menu.settings", "GAME OPTIONS");
        en.put("menu.axses_gui", "AXSES CLICKGUI");
        en.put("menu.accounts", "PROFILE & THEMES");
        en.put("menu.quit", "QUIT GAME");
        en.put("target.player", "PLAYER");
        en.put("target.mob", "MOB");
        en.put("target.winning", "WINNING");
        en.put("target.even", "EVEN");
        en.put("target.danger", "DANGER");
        dictionaries.put("en_US", en);

        Map<String, String> ru = new HashMap<String, String>();
        ru.put("gui.title", "AXSESCLIENT");
        ru.put("gui.subtitle", "AURORA v2.5");
        ru.put("gui.search_placeholder", "Поиск по 96 модулям (КиллАура, ESP, Таргет, Зум)...");
        ru.put("gui.favorites", "Избранное");
        ru.put("gui.hud_editor", "HUD");
        ru.put("gui.themes", "Темы");
        ru.put("gui.configs", "Конфиги");
        ru.put("gui.diag", "Инфо");
        ru.put("gui.reset_all", "Сбросить модуль");
        ru.put("gui.sort_default", "Сорт: Стандарт");
        ru.put("gui.sort_az", "Сорт: А-Я");
        ru.put("gui.sort_enabled", "Сорт: Активные");
        ru.put("gui.open_key", "Меню: ");
        ru.put("gui.press_key", "Клавиша...");
        ru.put("gui.no_bind", "—");
        ru.put("gui.no_results", "Модули по запросу не найдены");
        ru.put("gui.settings_hint", "ПКМ по карточке или стрелка — настройки модуля");
        ru.put("gui.lang_card_title", "Язык интерфейса / Language");
        ru.put("gui.lang_card_desc", "Мгновенное переключение всего клиента на Русский или Английский язык");
        ru.put("notif.module_enabled", "Включён");
        ru.put("notif.module_disabled", "Выключен");
        ru.put("cat.combat_training", "Бой");
        ru.put("cat.movement", "Движение");
        ru.put("cat.render", "Визуал и ESP");
        ru.put("cat.player", "Игрок");
        ru.put("cat.world", "Мир");
        ru.put("cat.utility", "Утилиты");
        ru.put("cat.hud", "HUD");
        ru.put("cat.settings", "Настройки");
        ru.put("menu.singleplayer", "ОДИНОЧНАЯ ИГРА");
        ru.put("menu.multiplayer", "СЕТЕВАЯ ИГРА");
        ru.put("menu.settings", "НАСТРОЙКИ ИГРЫ");
        ru.put("menu.axses_gui", "ОТКРЫТЬ AXSES GUI");
        ru.put("menu.accounts", "ПРОФИЛЬ И ТЕМЫ");
        ru.put("menu.quit", "ВЫЙТИ ИЗ ИГРЫ");
        ru.put("target.player", "ИГРОК");
        ru.put("target.mob", "МОБ");
        ru.put("target.winning", "ПРЕИМУЩЕСТВО");
        ru.put("target.even", "РАВНЫЙ БОЙ");
        ru.put("target.danger", "ОПАСНО");

        // All 96 modules — clean Russian titles & descriptions
        putMod(ru, "kill_aura", "КиллАура (KillAura)", "Беспаливная 1.9+ аура с профилями FunTime / ReallyWorld / HolyWorld, GCD и умными критами");
        putMod(ru, "target_hud", "Таргет HUD (Target HUD)", "Современная карточка цели: ХП с анимацией урона, дистанция и статус боя");
        putMod(ru, "trigger_bot", "ТриггерБот (TriggerBot)", "Умный авто-удар по цели в прицеле с таймингом критов и паузой при еде");

        putMod(ru, "entity_esp", "ESP Подсветка (Entity ESP)", "2D/3D боксы игроков и мобов, полоска ХП, никнеймы, предмет в руке и радар-стрелки");
        putMod(ru, "item_esp", "ESP Предметов (Item ESP)", "Подсветка лута и предметов на земле с названием, количеством и дистанцией");
        putMod(ru, "tracers", "Трейсеры (Tracers)", "Плавные линии-указатели к ближайшим игрокам и мобам с цветовой индикацией");

        putMod(ru, "attack_cooldown_indicator", "Индикатор перезарядки", "Полоска готовности удара 1.9+ прямо под прицелом");
        putMod(ru, "custom_hitmarker", "Хитмаркер ударов", "Визуальная отметка на прицеле при попадании и крите");
        putMod(ru, "combat_statistics", "Статистика боя", "Показывает удары, комбо и критические попадания за сессию");
        putMod(ru, "training_target", "Тренировочная мишень", "Голографическая мишень для тренировки плавности наводки");
        putMod(ru, "damage_feedback", "Сила удара", "Отображает процент силы удара и множитель крита");
        putMod(ru, "attack_timing_practice", "Тайминг ударов", "Оценка точности соблюдения перезарядки оружия в %");
        putMod(ru, "training_session_tracker", "Трекер тренировки", "Учёт времени боевой сессии и максимального комбо");
        putMod(ru, "crosshair_profiles", "Пресеты прицела", "Быстрое переключение соревновательных стилей прицела");
        putMod(ru, "accuracy_statistics", "Точность попаданий", "Процент точных ударов относительно всех взмахов");
        putMod(ru, "reaction_training", "Тест реакции", "Измерение скорости реакции клика в миллисекундах");
        putMod(ru, "practice_timer", "Таймер раунда", "Обратный отсчёт для тренировочных раундов");
        putMod(ru, "training_results", "Итоги боя", "Сводная панель точности, тайминга и реакции");

        putMod(ru, "toggle_sprint", "АвтоСпринт (Toggle Sprint)", "Автоматический бег вперёд без удержания клавиши Ctrl");
        putMod(ru, "toggle_sneak", "АвтоПрисед (Toggle Sneak)", "Фиксация приседа с авто-паузой при открытии инвентаря");
        putMod(ru, "movement_speed_hud", "Скорость движения", "Текущая горизонтальная скорость игрока в блоках/сек");
        putMod(ru, "camera_bobbing", "Покачивание камеры", "Отключение тряски экрана для стабильного прицела");
        putMod(ru, "fov_transition", "Плавность FOV", "Контроль динамического изменения поля зрения при беге");
        putMod(ru, "sprint_indicator", "Статус спринта", "Компактный индикатор активного авто-спринта на экране");
        putMod(ru, "sneak_indicator", "Статус приседа", "Индикатор фиксированного или удерживаемого приседа");
        putMod(ru, "direction_indicator", "Вектор движения", "Отображение направления стрейфа относительно взгляда");
        putMod(ru, "jump_statistics", "Счётчик прыжков", "Подсчёт прыжков и спринт-прыжков за текущую сессию");
        putMod(ru, "movement_session_tracker", "Пройденная дистанция", "Статистика пройденных метров пешком и бегом");
        putMod(ru, "camera_sensitivity_profiles", "Профили сенсы", "Быстрое переключение чувствительности мыши под PvP");
        putMod(ru, "camera_transition_settings", "Плавность камеры", "Настройка сглаживания переходов вида от 1/3 лица");

        putMod(ru, "fullbright", "Полная яркость (FullBright)", "Максимальное освещение в пещерах, ночью и под водой");
        putMod(ru, "smooth_zoom", "Плавный зум (Zoom)", "Плавное оптическое приближение камеры по клавише C");
        putMod(ru, "custom_crosshair", "Кастомный прицел", "Векторный прицел с динамической отдачей и подсветкой цели");
        putMod(ru, "freelook", "Свободный обзор (FreeLook)", "Обзор на 360 градусов без изменения направления бега");
        putMod(ru, "fps_counter", "Счётчик FPS", "Текущая частота кадров и время кадра в миллисекундах");
        putMod(ru, "coordinates_hud", "Координаты X/Y/Z", "Точные координаты игрока на экране в реальном времени");
        putMod(ru, "armor_hud", "Состояние брони", "Отображение надетой брони и запаса её прочности");
        putMod(ru, "potion_hud", "Активные эффекты", "Список действующих зелий с таймерами обратного отсчёта");
        putMod(ru, "keystrokes", "Кейстрокс (WASD + CPS)", "Индикация нажатий клавиш W/A/S/D и кликов мыши");
        putMod(ru, "cps_counter", "Счётчик КПС (CPS)", "Количество левых и правых кликов мыши в секунду");
        putMod(ru, "custom_fov", "Угол обзора (FOV)", "Точная настройка базового угла обзора камеры");
        putMod(ru, "direction_hud", "Направление взгляда", "Сторона света (С/Ю/З/В) и точные углы камеры");
        putMod(ru, "biome_display", "Текущий биом", "Название биома, в котором находится игрок");
        putMod(ru, "ping_display", "Пинг сети", "Задержка соединения с сервером в миллисекундах");
        putMod(ru, "memory_display", "Память ОЗУ (RAM)", "Использование и реальный лимит выделенной памяти JVM");
        putMod(ru, "session_timer", "Время в игре", "Таймер текущей игровой сессии клиента");
        putMod(ru, "durability_hud", "Прочность предмета", "Остаток прочности оружия или инструмента в руке");
        putMod(ru, "inventory_preview", "Обзор инвентаря", "Количество занятых и свободных слотов инвентаря");
        putMod(ru, "custom_hotbar", "Стильный хотбар", "Неоновая подсветка выбранного слота быстрого доступа");
        putMod(ru, "custom_scoreboard", "Кастомный скорборд", "Современный тёмный фон таблицы справа без красных цифр");
        putMod(ru, "custom_boss_bar", "Панель босса", "Настройка масштаба и оформления полоски здоровья босса");
        putMod(ru, "custom_chat_bg", "Фон чата", "Полупрозрачная тёмная подложка области игрового чата");
        putMod(ru, "custom_tooltip_style", "Стиль подсказок", "Неоновая рамка и инфо о прочности во всплывающих описаниях");
        putMod(ru, "hud_background_blur", "Стеклянный фон HUD", "Интенсивность затемнения стеклянных карточек и меню");
        putMod(ru, "screenshot_overlay", "Уведомление скриншота", "Всплывающая плашка при сохранении скриншота F2");
        putMod(ru, "custom_item_counter", "Счётчик предметов", "Подсчёт стрел, тотемов или яблок во всём инвентаре");
        putMod(ru, "clock_hud", "Часы на экране", "Реальное системное или внутриигровое время суток");
        putMod(ru, "compass_hud", "Лента компаса", "Горизонтальная шкала азимута в верхней части экрана");
        putMod(ru, "custom_action_bar", "Панель действий", "Улучшенное оформление уведомлений над хотбаром");
        putMod(ru, "session_information", "Карточка сессии", "Сводка ника, мира, FPS и времени игры в одном блоке");

        putMod(ru, "waypoints", "Вейпоинты (Метки)", "Локальные метки координат с отображением дистанции");
        putMod(ru, "death_location", "Точка смерти", "Авто-сохранение координат последнего места гибели");
        putMod(ru, "screenshot_manager", "Менеджер скриншотов", "Отслеживание папки скриншотов и последнего снимка");
        putMod(ru, "world_information", "Инфо о мире", "Название мира, сложность и число загруженных чанков");
        putMod(ru, "biome_information", "Климат биома", "Температура и тип осадков текущего биома");
        putMod(ru, "light_level_display", "Уровень света", "Освещённость блока и неба с предупреждением спавна мобов");
        putMod(ru, "chunk_border_toggle", "Границы чанков", "Быстрое включение сетки чанков одной кнопкой");
        putMod(ru, "local_notes", "Личные заметки", "Встроенный блокнот с сохранением между перезапусками");
        putMod(ru, "coordinate_converter", "Калькулятор Незера", "Авто-перевод координат Верхнего мира и Незера (x8 / ÷8)");
        putMod(ru, "session_statistics", "Статистика мира", "Сводка посещённых миров, прыжков и дистанции");
        putMod(ru, "time_display", "Дата и время", "Отображение текущей календарной даты и времени");
        putMod(ru, "durability_warning", "Тревога поломки", "Звуковое/визуальное уведомление при износе брони или меча");
        putMod(ru, "inventory_search", "Поиск в инвентаре", "Быстрый поиск нужного предмета по названию в слотах");
        putMod(ru, "custom_notifications", "Уведомления клиента", "Настройка всплывающих уведомлений при переключении модулей");
        putMod(ru, "world_session_timer", "Время в мире", "Отдельный таймер нахождения в текущем мире/сервере");
        putMod(ru, "resource_pack_manager", "Ресурспаки", "Быстрый просмотр и переход в меню текстурпаков");
        putMod(ru, "screenshot_gallery", "Галерея снимков", "Список последних сохранённых скриншотов клиента");
        putMod(ru, "local_world_notes", "Заметки мира", "Индивидуальные заметки для каждого отдельного мира");
        putMod(ru, "coordinate_clipboard", "Копировать координаты", "Мгновенное копирование X/Y/Z в буфер обмена");
        putMod(ru, "local_world_info_panel", "Погода и фаза луны", "Отображение погоды, игровых суток и фазы луны");

        putMod(ru, "gui_scale_manager", "Масштаб интерфейса", "Настройка размера окна AXSES ClickGUI (0.75x - 1.50x)");
        putMod(ru, "theme_preview", "Выбор темы", "Быстрое переключение 8 фирменных тем оформления");
        putMod(ru, "module_search_history", "История поиска", "Быстрые теги последних поисковых запросов в меню");
        putMod(ru, "favorites_manager", "Управление избранным", "Закрепление любимых модулей в верхней вкладке");
        putMod(ru, "keyboard_shortcut_editor", "Горячие клавиши", "Настройка клавиш открытия ClickGUI и редактора HUD");
        putMod(ru, "notification_history", "Журнал событий", "История последних 200 уведомлений клиента");
        putMod(ru, "interface_presets", "Пресеты меню", "Готовые стили скругления и контрастности интерфейса");
        putMod(ru, "hud_presets", "Макеты HUD", "Готовые раскладки виджетов (Минимал, PvP, Полный)");
        putMod(ru, "animation_settings", "Плавность анимаций", "Скорость и интенсивность переходов в меню и HUD");
        putMod(ru, "accessibility_settings", "Чёткость и контраст", "Повышенная чёткость рамок и теней текста");
        putMod(ru, "color_blindness_presets", "Цветокоррекция", "Фильтры восприятия цветов для дальтоников");
        putMod(ru, "font_size_settings", "Язык и шрифт (Русский / EN)", "Переключение языка интерфейса (Русский / English) и размера шрифта");
        putMod(ru, "reduced_motion_mode", "Без анимаций", "Мгновенное открытие окон без плавных сдвигов");
        putMod(ru, "interface_reset", "Сброс интерфейса", "Возврат позиции окна, темы и HUD к заводским");
        putMod(ru, "config_backup_manager", "Резервные копии", "Создание и восстановление бэкапов конфигурации");
        putMod(ru, "module_compatibility_viewer", "Диагностика клиента", "Проверка совместимости версии MC, Java и модов");

        dictionaries.put("ru_RU", ru);
        initSettingAndOptionTranslations();
    }

    private void initSettingAndOptionTranslations() {
        // Common & Combat / ESP / HUD setting titles in Russian
        ruSettings.put("language", "Язык интерфейса");
        ruSettings.put("server_profile", "Профиль обхода");
        ruSettings.put("range", "Дистанция");
        ruSettings.put("fov", "Угол обзора FOV");
        ruSettings.put("attack_mode", "Режим тайминга");
        ruSettings.put("cps", "Скорость CPS");
        ruSettings.put("priority", "Приоритет цели");
        ruSettings.put("rotations", "Наводка камеры");
        ruSettings.put("turn_speed", "Скорость поворота");
        ruSettings.put("aim_point", "Точка хитбокса");
        ruSettings.put("crit_mode", "Режим критов");
        ruSettings.put("sprint_reset", "Сброс спринта (W-Tap)");
        ruSettings.put("gcd_fix", "Фикс мыши GCD");
        ruSettings.put("human_jitter", "Живой микро-джиттер");
        ruSettings.put("check_walls", "Не бить сквозь стены");
        ruSettings.put("strict_raycast", "Строгий луч хитбокса");
        ruSettings.put("pause_eating", "Пауза при еде и щите");
        ruSettings.put("players", "Игроки");
        ruSettings.put("hostiles", "Монстры");
        ruSettings.put("animals", "Животные");
        ruSettings.put("invisible", "Невидимые цели");
        ruSettings.put("pause_gui", "Пауза в инвентаре");
        ruSettings.put("disable_death", "Выкл. после смерти");
        ruSettings.put("target_ring", "3D Кольцо цели");
        ruSettings.put("lock_badge", "Индикатор под прицелом");
        ruSettings.put("min_cd", "Мин. заряд удара %");
        ruSettings.put("smart_crits", "Умные криты в прыжке");
        ruSettings.put("human_delay", "Разброс реакции");
        ruSettings.put("linger_sec", "Время удержания (сек)");
        ruSettings.put("preview_gui", "Превью в ClickGUI");
        ruSettings.put("pvp_badge", "Статус преимущества");
        ruSettings.put("box_style", "Стиль бокса");
        ruSettings.put("fill_box", "Заливка бокса");
        ruSettings.put("health_bar", "Полоска здоровья");
        ruSettings.put("nametags", "Никнеймы и ХП");
        ruSettings.put("held_item", "Предмет в руке");
        ruSettings.put("offscreen_arrows", "Стрелки вне экрана");
        ruSettings.put("show_box", "Рамка предмета");
        ruSettings.put("show_distance", "Показывать дистанцию");
        ruSettings.put("origin", "Начало линий");
        ruSettings.put("color_mode", "Режим цвета");
        ruSettings.put("strength", "Яркость");
        ruSettings.put("mode", "Режим");
        ruSettings.put("scale", "Масштаб меню");
        ruSettings.put("speed", "Скорость анимации");
        ruSettings.put("width", "Ширина");
        ruSettings.put("hide_full", "Скрывать при 100%");
        ruSettings.put("preview_id", "Превью темы");
        ruSettings.put("fav_first", "Избранные сверху");
        ruSettings.put("alert_conflicts", "Предупреждать о дублях");

        // Dropdown / Mode values in Russian
        ruOptions.put("Universal", "Универсальный");
        ruOptions.put("Legit Ghost", "Легит (Скрытный)");
        ruOptions.put("1.9+ Smart", "1.9+ Умный");
        ruOptions.put("1.9+ Cooldown", "1.9+ Кулдаун");
        ruOptions.put("CPS Timer", "Таймер CPS");
        ruOptions.put("Distance", "По дистанции");
        ruOptions.put("Lowest HP", "Меньше ХП");
        ruOptions.put("Angle", "По углу прицела");
        ruOptions.put("Least Armor", "Слабая броня");
        ruOptions.put("Smooth S-Curve", "Плавная S-кривая");
        ruOptions.put("FunTime Human", "Живая FunTime");
        ruOptions.put("Multipoint Clamp", "Мульти-хитбокс");
        ruOptions.put("Snap", "Резкая");
        ruOptions.put("Off", "Выкл");
        ruOptions.put("Nearest Hitbox", "Ближняя точка");
        ruOptions.put("Chest", "Корпус");
        ruOptions.put("Eyes", "Голова");
        ruOptions.put("Dynamic", "Динамическая");
        ruOptions.put("Smart Fall", "Умное падение");
        ruOptions.put("Always Swing", "Всегда бить");
        ruOptions.put("Only Crits", "Только криты");
        ruOptions.put("Corners", "Уголки");
        ruOptions.put("2D Box", "2D Рамка");
        ruOptions.put("3D Box", "3D Бокс");
        ruOptions.put("None", "Нет");
        ruOptions.put("Bottom", "Снизу экрана");
        ruOptions.put("Crosshair", "От прицела");
        ruOptions.put("Theme", "По теме");
    }

    private static void putMod(Map<String, String> dict, String id, String name, String desc) {
        dict.put("mod." + id + ".name", name);
        dict.put("mod." + id + ".desc", desc);
    }

    public boolean isRussian() {
        return activeLocale != null && activeLocale.startsWith("ru");
    }

    public String getActiveLocale() {
        return activeLocale;
    }

    public void setActiveLocale(String locale) {
        if (locale != null && dictionaries.containsKey(locale)) {
            this.activeLocale = locale;
        }
    }

    public void toggleLocale() {
        setActiveLocale("en_US".equals(activeLocale) ? "ru_RU" : "en_US");
    }

    public String moduleName(String id, String fallbackName) {
        Map<String, String> dict = dictionaries.get(activeLocale);
        String key = "mod." + id + ".name";
        if (dict != null && dict.containsKey(key)) {
            return dict.get(key);
        }
        return fallbackName;
    }

    public String translateModuleName(String id, String fallbackName) {
        return moduleName(id, fallbackName);
    }

    public String moduleDesc(String id, String fallbackDesc) {
        Map<String, String> dict = dictionaries.get(activeLocale);
        String key = "mod." + id + ".desc";
        if (dict != null && dict.containsKey(key)) {
            return dict.get(key);
        }
        return fallbackDesc;
    }

    public String settingName(String settingId, String fallbackName) {
        if (isRussian() && settingId != null) {
            String tr = ruSettings.get(settingId);
            if (tr != null) {
                return tr;
            }
        }
        return fallbackName;
    }

    public String optionName(String rawValue) {
        if (isRussian() && rawValue != null) {
            String tr = ruOptions.get(rawValue);
            if (tr != null) {
                return tr;
            }
        }
        return rawValue;
    }

    public String tr(String key) {
        Map<String, String> dict = dictionaries.get(activeLocale);
        if (dict != null && dict.containsKey(key)) {
            return dict.get(key);
        }
        Map<String, String> fallback = dictionaries.get("en_US");
        if (fallback != null && fallback.containsKey(key)) {
            return fallback.get(key);
        }
        return key;
    }
}
