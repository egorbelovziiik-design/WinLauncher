using System.Text.Json.Serialization;

namespace AxsesClient.Launcher.Models;

public enum VersionInstallState
{
    NotInstalled,
    Installing,
    Installed,
    UpdateAvailable,
    RepairRequired,
    Launching,
    Running
}

public static class VersionInstallStateExtensions
{
    public static string ToDisplayBadge(this VersionInstallState state, bool ru = false) => state switch
    {
        VersionInstallState.NotInstalled => ru ? "НЕ УСТАНОВЛЕНО" : "NOT INSTALLED",
        VersionInstallState.Installing => ru ? "УСТАНОВКА..." : "INSTALLING",
        VersionInstallState.Installed => ru ? "ГОТОВО К ИГРЕ" : "INSTALLED",
        VersionInstallState.UpdateAvailable => ru ? "ДОСТУПНО ОБНОВЛЕНИЕ" : "UPDATE AVAILABLE",
        VersionInstallState.RepairRequired => ru ? "ТРЕБУЕТСЯ ПОЧИНКА" : "REPAIR REQUIRED",
        VersionInstallState.Launching => ru ? "ЗАПУСК JVM..." : "LAUNCHING",
        VersionInstallState.Running => ru ? "ИГРА ЗАПУЩЕНА" : "RUNNING",
        _ => ru ? "НЕ УСТАНОВЛЕНО" : "NOT INSTALLED"
    };
}

public enum LauncherPage
{
    Dashboard,
    Play,
    Versions,
    Accounts,
    Profiles,
    Modules,
    Appearance,
    Settings,
    Downloads,
    Diagnostics,
    About
}

public sealed class SupportedVersionSpec
{
    public required string MinecraftVersion { get; init; }
    public required string DisplayTitle { get; init; }
    public required string Subtitle { get; init; }
    public string SubtitleRu { get; init; } = "";
    public required int RequiredJavaMajor { get; init; }
    public required string FabricLoaderVersion { get; init; }
    public required string FabricApiVersion { get; init; }
    public required string ModJarFileName { get; init; }
    public VersionInstallState State { get; set; } = VersionInstallState.NotInstalled;
    public double Progress { get; set; }
    public string StatusDetail { get; set; } = "Готово к установке";
    public long DiskUsageBytes { get; set; }
    public int VerifiedLibraryCount { get; set; }

    public static List<SupportedVersionSpec> CreateDefaults() =>
    [
        new SupportedVersionSpec
        {
            MinecraftVersion = "1.16.5",
            DisplayTitle = "Minecraft 1.16.5",
            Subtitle = "Classic PvP & Anarchy Servers • Java 8 • Fabric 0.20.1",
            SubtitleRu = "Классический PvP и анархия • Авто-Java 8 • Fabric 0.20.1",
            RequiredJavaMajor = 8,
            FabricLoaderVersion = "0.20.1",
            FabricApiVersion = "0.42.0+1.16",
            ModJarFileName = "AXSESCLIENT-1.16.5.jar"
        },
        new SupportedVersionSpec
        {
            MinecraftVersion = "1.21.4",
            DisplayTitle = "Minecraft 1.21.4",
            Subtitle = "Modern High-FPS PvP & Survival • Java 21 • Fabric 0.20.1",
            SubtitleRu = "Современный PvP и выживание • Авто-Java 21 • Fabric 0.20.1",
            RequiredJavaMajor = 21,
            FabricLoaderVersion = "0.20.1",
            FabricApiVersion = "0.119.4+1.21.4",
            ModJarFileName = "AXSESCLIENT-1.21.4.jar"
        },
        new SupportedVersionSpec
        {
            MinecraftVersion = "1.21.11",
            DisplayTitle = "Minecraft 1.21.11",
            Subtitle = "Flagship JOML Engine • Java 21 • Fabric 0.20.1",
            SubtitleRu = "Флагманский движок 1.21.11 • Авто-Java 21 • Fabric 0.20.1",
            RequiredJavaMajor = 21,
            FabricLoaderVersion = "0.20.1",
            FabricApiVersion = "0.141.6+1.21.11",
            ModJarFileName = "AXSESCLIENT-1.21.11.jar"
        }
    ];
}

public sealed class LaunchProfile
{
    public string Id { get; set; } = Guid.NewGuid().ToString("N")[..10];
    public string Name { get; set; } = "Основной профиль";
    public string MinecraftVersion { get; set; } = "1.21.11";
    public int RamMb { get; set; } = 4096;
    public int ResolutionWidth { get; set; } = 1280;
    public int ResolutionHeight { get; set; } = 720;
    public bool Fullscreen { get; set; }
    public string CustomJvmArgs { get; set; } = "-XX:+UseG1GC -XX:+UnlockExperimentalVMOptions -XX:G1NewSizePercent=20 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=32M";
    public string ClientConfigPreset { get; set; } = "default";
    public string ThemeId { get; set; } = "axses-dark";
    public DateTime CreatedAtUtc { get; set; } = DateTime.UtcNow;
}

public sealed class MinecraftAccountRecord
{
    public string Uuid { get; set; } = "";
    public string Username { get; set; } = "";
    public string AccountType { get; set; } = "Microsoft"; // Microsoft | LocalSandbox
    public string EncryptedAccessToken { get; set; } = "";
    public string EncryptedRefreshToken { get; set; } = "";
    public DateTime ExpiresAtUtc { get; set; } = DateTime.UtcNow;
    public bool OwnsMinecraftJava { get; set; }
    public DateTime LastUsedUtc { get; set; } = DateTime.UtcNow;
}

public sealed class DownloadTaskItem
{
    public string Id { get; init; } = Guid.NewGuid().ToString("N")[..8];
    public required string FileName { get; init; }
    public required string Stage { get; set; }
    public double Progress { get; set; }
    public double SpeedMbps { get; set; }
    public string Status { get; set; } = "Queued";
    public bool ShaVerified { get; set; }
}

public sealed class LauncherThemePalette
{
    public required string Id { get; init; }
    public required string Name { get; init; }
    public required string BgPrimary { get; init; }
    public required string BgSecondary { get; init; }
    public required string BgSurface { get; init; }
    public required string AccentPrimary { get; init; }
    public required string AccentSecondary { get; init; }
    public required string AccentCyan { get; init; }
    public required string TextPrimary { get; init; }
    public required string TextSecondary { get; init; }
    public required string Success { get; init; }
    public required string Warning { get; init; }
    public required string Danger { get; init; }

    public static List<LauncherThemePalette> CreateOfficialThemes() =>
    [
        new() { Id = "axses-dark", Name = "OBSIDIAN AURORA", BgPrimary = "#060810", BgSecondary = "#0D111E", BgSurface = "#141A2E", AccentPrimary = "#8B5CF6", AccentSecondary = "#6366F1", AccentCyan = "#06B6D4", TextPrimary = "#F8FAFC", TextSecondary = "#94A3B8", Success = "#10B981", Warning = "#F59E0B", Danger = "#F43F5E" },
        new() { Id = "axses-purple", Name = "ROYAL VIOLET", BgPrimary = "#090612", BgSecondary = "#120C22", BgSurface = "#1B1333", AccentPrimary = "#A855F7", AccentSecondary = "#7C3AED", AccentCyan = "#EC4899", TextPrimary = "#FAF5FF", TextSecondary = "#A78BFA", Success = "#34D399", Warning = "#FBBF24", Danger = "#FB7185" },
        new() { Id = "axses-blue", Name = "SAPPHIRE PULSE", BgPrimary = "#050A14", BgSecondary = "#0B1325", BgSurface = "#131F3A", AccentPrimary = "#3B82F6", AccentSecondary = "#2563EB", AccentCyan = "#38BDF8", TextPrimary = "#F0F9FF", TextSecondary = "#93C5FD", Success = "#10B981", Warning = "#F59E0B", Danger = "#F43F5E" },
        new() { Id = "axses-cyber", Name = "CYBER MATRIX", BgPrimary = "#040A0E", BgSecondary = "#09141C", BgSurface = "#10212E", AccentPrimary = "#06B6D4", AccentSecondary = "#10B981", AccentCyan = "#8B5CF6", TextPrimary = "#ECFEFF", TextSecondary = "#67E8F9", Success = "#10B981", Warning = "#FACC15", Danger = "#FB7185" },
        new() { Id = "axses-midnight", Name = "MIDNIGHT GLASS", BgPrimary = "#030509", BgSecondary = "#080B14", BgSurface = "#101626", AccentPrimary = "#6366F1", AccentSecondary = "#4F46E5", AccentCyan = "#22D3EE", TextPrimary = "#F8FAFC", TextSecondary = "#818CF8", Success = "#34D399", Warning = "#FBBF24", Danger = "#F87171" },
        new() { Id = "axses-minimal", Name = "MONOCHROME PRO", BgPrimary = "#090A0D", BgSecondary = "#111318", BgSurface = "#1A1D24", AccentPrimary = "#E2E8F0", AccentSecondary = "#94A3B8", AccentCyan = "#38BDF8", TextPrimary = "#FFFFFF", TextSecondary = "#94A3B8", Success = "#4ADE80", Warning = "#FACC15", Danger = "#FB7185" },
        new() { Id = "axses-graphite", Name = "GRAPHITE TITAN", BgPrimary = "#0C0E12", BgSecondary = "#14181F", BgSurface = "#1E242E", AccentPrimary = "#8B5CF6", AccentSecondary = "#64748B", AccentCyan = "#22D3EE", TextPrimary = "#F1F5F9", TextSecondary = "#94A3B8", Success = "#10B981", Warning = "#F59E0B", Danger = "#F43F5E" },
        new() { Id = "axses-neon", Name = "HYPER NEON", BgPrimary = "#070510", BgSecondary = "#100C21", BgSurface = "#1A1434", AccentPrimary = "#F43F5E", AccentSecondary = "#8B5CF6", AccentCyan = "#14B8A6", TextPrimary = "#FFF1F2", TextSecondary = "#FDA4AF", Success = "#14B8A6", Warning = "#FBBF24", Danger = "#F43F5E" }
    ];
}

public sealed class ModuleCatalogItem
{
    public required string Id { get; init; }
    public required string Name { get; init; }
    public string NameRu { get; init; } = "";
    public required string Category { get; init; }
    public required string Description { get; init; }
    public string DescriptionRu { get; init; } = "";
    public bool Enabled { get; set; }
    public bool Favorite { get; set; }

    public string DisplayName(bool ru) => ru && !string.IsNullOrWhiteSpace(NameRu) ? NameRu : Name;
    public string DisplayDescription(bool ru) => ru && !string.IsNullOrWhiteSpace(DescriptionRu) ? DescriptionRu : Description;

    public static List<ModuleCatalogItem> CreateAll90Modules() => CreateAll93Modules();

    public static List<ModuleCatalogItem> CreateAll93Modules() =>
    [
        // Flagship Combat & Target HUD (01-03)
        new() { Id = "kill_aura", Name = "KillAura", NameRu = "КиллАура", Category = "Combat", Description = "Smart auto-attack with 1.9+ cooldown, target priority, filters & smooth aim.", DescriptionRu = "Умная авто-атака целей в радиусе с КД 1.9+, фильтрами и плавной наводкой.", Enabled = false, Favorite = true },
        new() { Id = "target_hud", Name = "Target HUD", NameRu = "Таргет HUD", Category = "Combat", Description = "Animated target card with HP bar, damage trail, armor, distance & hurt flash.", DescriptionRu = "Современная карточка цели с анимацией ХП, бронёй, дистанцией и вспышкой урона.", Enabled = true, Favorite = true },
        new() { Id = "trigger_bot", Name = "TriggerBot", NameRu = "ТриггерБот", Category = "Combat", Description = "Automatically strikes valid entities directly under your crosshair.", DescriptionRu = "Автоматический удар при наведении прицела на цель с учётом кулдауна.", Enabled = false, Favorite = true },

        // Visual & HUD (04-33)
        new() { Id = "fullbright", Name = "FullBright", NameRu = "Яркость (FullBright)", Category = "Render", Description = "Uniform gamma & ambient brightness control for dark caves.", DescriptionRu = "Полное освещение тёмных пещер и ночного мира без потери FPS.", Enabled = true },
        new() { Id = "smooth_zoom", Name = "Smooth Zoom", NameRu = "Плавный зум", Category = "Render", Description = "Interpolated optical zoom with scroll-wheel magnification.", DescriptionRu = "Плавное оптическое приближение камеры с регулировкой колёсиком мыши.", Enabled = true },
        new() { Id = "custom_crosshair", Name = "Custom Crosshair", NameRu = "Кастомный прицел", Category = "Render", Description = "Vector crosshair with dynamic movement/cooldown spread.", DescriptionRu = "Векторный прицел с динамическим расширением и настройкой цвета.", Enabled = false },
        new() { Id = "freelook", Name = "FreeLook", NameRu = "Свободная камера", Category = "Render", Description = "Hold-key 360 perspective orbit preserving movement vector.", DescriptionRu = "Обзор на 360° вокруг игрока с сохранением направления бега.", Enabled = true },
        new() { Id = "fps_counter", Name = "FPS Counter", NameRu = "Счётчик FPS", Category = "HUD", Description = "Real-time frame rate HUD with stability readout.", DescriptionRu = "Точный показатель кадров в секунду и плавности рендера.", Enabled = true, Favorite = true },
        new() { Id = "coordinates_hud", Name = "Coordinates HUD", NameRu = "Координаты", Category = "HUD", Description = "X/Y/Z coordinates, chunk offset, and Nether ratio.", DescriptionRu = "Позиция X/Y/Z, чанк и автоматический пересчёт координат Незера.", Enabled = true, Favorite = true },
        new() { Id = "armor_hud", Name = "Armor HUD", NameRu = "Статус брони", Category = "HUD", Description = "Equipped armor pieces with color-coded durability.", DescriptionRu = "Отображение надетой экипировки и запаса её прочности.", Enabled = true },
        new() { Id = "potion_hud", Name = "Potion HUD", NameRu = "Эффекты зелий", Category = "HUD", Description = "Active status effects with amplifier and countdown timer.", DescriptionRu = "Список активных эффектов с уровнем и точным таймером.", Enabled = true },
        new() { Id = "keystrokes", Name = "Keystrokes", NameRu = "Кейстрокс", Category = "HUD", Description = "Animated WASD, Space, LMB/RMB visualizer with CPS.", DescriptionRu = "Визуализация нажатий WASD, прыжка и кликов мыши с CPS.", Enabled = true, Favorite = true },
        new() { Id = "cps_counter", Name = "CPS Counter", NameRu = "Счётчик CPS", Category = "HUD", Description = "Rolling 1-second left and right mouse clicks per second.", DescriptionRu = "Скорость кликов левой и правой кнопки мыши в секунду.", Enabled = true },
        new() { Id = "custom_fov", Name = "Custom FOV", NameRu = "Управление FOV", Category = "Render", Description = "Fine-grained field-of-view controller and dynamic clamp.", DescriptionRu = "Точная настройка угла обзора и динамических эффектов скорости.", Enabled = false },
        new() { Id = "direction_hud", Name = "Direction HUD", NameRu = "Направление", Category = "HUD", Description = "Cardinal heading readout with yaw angle.", DescriptionRu = "Отображение стороны света и точного угла поворота камеры.", Enabled = false },
        new() { Id = "biome_display", Name = "Biome Display", NameRu = "Текущий биом", Category = "HUD", Description = "Current biome badge on the HUD.", DescriptionRu = "Компактная плашка с названием текущего биома на экране.", Enabled = false },
        new() { Id = "ping_display", Name = "Ping Display", NameRu = "Пинг сервера", Category = "HUD", Description = "Server connection latency in milliseconds.", DescriptionRu = "Задержка сетевого соединения с сервером в миллисекундах.", Enabled = true },
        new() { Id = "memory_display", Name = "Memory Display", NameRu = "Память ОЗУ", Category = "HUD", Description = "Real JVM heap usage and allocated -Xmx MB.", DescriptionRu = "Реальное потребление и выделенный объём оперативной памяти JVM.", Enabled = true },
        new() { Id = "session_timer", Name = "Session Timer", NameRu = "Таймер сессии", Category = "HUD", Description = "Elapsed client play session duration.", DescriptionRu = "Время непрерывной игры с момента запуска клиента.", Enabled = false },
        new() { Id = "durability_hud", Name = "Durability HUD", NameRu = "Прочность предмета", Category = "HUD", Description = "Held item remaining uses and percentage.", DescriptionRu = "Остаток прочности предмета в руке в единицах и процентах.", Enabled = false },
        new() { Id = "inventory_preview", Name = "Inventory Preview", NameRu = "Превью инвентаря", Category = "HUD", Description = "Compact 3x9 main inventory grid overlay.", DescriptionRu = "Компактная сводка заполненности слотов инвентаря на HUD.", Enabled = false },
        new() { Id = "custom_hotbar", Name = "Custom Hotbar", NameRu = "Стильный хотбар", Category = "Render", Description = "Themed OBSIDIAN AURORA hotbar accent frame.", DescriptionRu = "Акцентная неоновая подсветка панели быстрого доступа.", Enabled = false },
        new() { Id = "custom_scoreboard", Name = "Custom Scoreboard", NameRu = "Чистый скорборд", Category = "Render", Description = "Clean scoreboard styling with optional number hiding.", DescriptionRu = "Современное оформление таблицы сервера без красных цифр.", Enabled = true },
        new() { Id = "custom_boss_bar", Name = "Custom Boss Bar", NameRu = "Компактный боссбар", Category = "Render", Description = "Compact boss bar scaling and percentage overlay.", DescriptionRu = "Аккуратная полоса здоровья боссов с процентами.", Enabled = false },
        new() { Id = "custom_chat_bg", Name = "Custom Chat Background", NameRu = "Фон чата", Category = "Render", Description = "Soft translucent chat backdrop with adjustable opacity.", DescriptionRu = "Мягкая полупрозрачная подложка чата с настройкой прозрачности.", Enabled = true },
        new() { Id = "custom_tooltip_style", Name = "Custom Tooltip Style", NameRu = "Стиль подсказок", Category = "Render", Description = "Themed bordered item tooltip styling.", DescriptionRu = "Фирменная рамка и фон всплывающих описаний предметов.", Enabled = true },
        new() { Id = "hud_background_blur", Name = "HUD Background Blur", NameRu = "Стеклянный фон HUD", Category = "Render", Description = "Frosted glass backdrop tinting behind HUD widgets.", DescriptionRu = "Эффект матового тёмного стекла под всеми виджетами HUD.", Enabled = true },
        new() { Id = "screenshot_overlay", Name = "Screenshot Overlay", NameRu = "Уведомление скриншота", Category = "Render", Description = "Instant preview toast when F2 screenshot is saved.", DescriptionRu = "Мгновенная карточка подтверждения при сохранении снимка F2.", Enabled = true },
        new() { Id = "custom_item_counter", Name = "Custom Item Counter", NameRu = "Счётчик ресурсов", Category = "HUD", Description = "Total inventory count of arrows, totems, or golden apples.", DescriptionRu = "Подсчёт стрел, тотемов бессмертия, золотых яблок и зелий.", Enabled = false },
        new() { Id = "clock_hud", Name = "Clock HUD", NameRu = "Часы", Category = "HUD", Description = "Real-world or in-game 24h clock widget.", DescriptionRu = "Реальное системное или внутриигровое время в формате 24ч.", Enabled = false },
        new() { Id = "compass_hud", Name = "Compass HUD", NameRu = "Компас", Category = "HUD", Description = "Horizontal scrolling degree tape compass.", DescriptionRu = "Горизонтальная шкала азимута и сторон света вверху экрана.", Enabled = true },
        new() { Id = "custom_action_bar", Name = "Custom Action Bar", NameRu = "Панель статуса", Category = "HUD", Description = "Elevated themed action bar readout.", DescriptionRu = "Приподнятая инфо-строка над хотбаром (здоровье, голод, опыт).", Enabled = false },
        new() { Id = "session_information", Name = "Session Information", NameRu = "Карточка сессии", Category = "HUD", Description = "Unified summary card (FPS, Ping, Playtime, World).", DescriptionRu = "Сводная панель: FPS, пинг, время игры и текущий сервер.", Enabled = false },

        // Movement & Player (34-45)
        new() { Id = "toggle_sprint", Name = "Toggle Sprint", NameRu = "Авто-спринт", Category = "Movement", Description = "Automatic sprint latch without holding Ctrl.", DescriptionRu = "Автоматическое удержание бега без зажатия клавиши спринта.", Enabled = true, Favorite = true },
        new() { Id = "toggle_sneak", Name = "Toggle Sneak", NameRu = "Переключение крадучись", Category = "Movement", Description = "Client-side sneak toggle pausing safely in GUIs.", DescriptionRu = "Фиксация режима приседа с безопасной паузой в инвентаре.", Enabled = false },
        new() { Id = "movement_speed_hud", Name = "Movement Speed HUD", NameRu = "Скорость движения", Category = "Movement", Description = "Horizontal velocity in blocks per second (b/s).", DescriptionRu = "Текущая горизонтальная скорость игрока в блоках в секунду.", Enabled = false },
        new() { Id = "camera_bobbing", Name = "Camera Bobbing", NameRu = "Стабилизация камеры", Category = "Player", Description = "View bobbing control keeping crosshair steady.", DescriptionRu = "Отключение покачивания прицела при беге для точной наводки.", Enabled = false },
        new() { Id = "fov_transition", Name = "FOV Transitions", NameRu = "Плавность FOV", Category = "Movement", Description = "Smooth sprint and speed FOV transitions.", DescriptionRu = "Мягкое сглаживание скачков угла обзора при рывках и зельях.", Enabled = true },
        new() { Id = "sprint_indicator", Name = "Sprint Indicator", NameRu = "Индикатор спринта", Category = "Movement", Description = "HUD status pill showing active sprint mode.", DescriptionRu = "Компактный индикатор активного режима авто-бега на HUD.", Enabled = true },
        new() { Id = "sneak_indicator", Name = "Sneak Indicator", NameRu = "Индикатор приседа", Category = "Movement", Description = "HUD status pill showing active sneak mode.", DescriptionRu = "Статус активного режима скрытного передвижения.", Enabled = false },
        new() { Id = "direction_indicator", Name = "Direction Indicator", NameRu = "Вектор движения", Category = "Player", Description = "Compact velocity vector indicator.", DescriptionRu = "Отображение курса и вектора перемещения персонажа.", Enabled = false },
        new() { Id = "jump_statistics", Name = "Jump Statistics", NameRu = "Статистика прыжков", Category = "Movement", Description = "Tracks session jump count and cadence.", DescriptionRu = "Подсчёт количества прыжков за текущую игровую сессию.", Enabled = false },
        new() { Id = "movement_session_tracker", Name = "Movement Tracker", NameRu = "Пройденная дистанция", Category = "Movement", Description = "Cumulative distance traveled in meters/kilometers.", DescriptionRu = "Суммарное расстояние, пройденное игроком в метрах и километрах.", Enabled = false },
        new() { Id = "camera_sensitivity_profiles", Name = "Sensitivity Profiles", NameRu = "Профили сенсы", Category = "Player", Description = "Switchable mouse sensitivity presets (Low / Standard / High).", DescriptionRu = "Быстрое переключение чувствительности мыши для PvP и стрельбы.", Enabled = false },
        new() { Id = "camera_transitions", Name = "Camera Transitions", NameRu = "Плавность камеры", Category = "Player", Description = "Smooth easing on perspective and zoom transitions.", DescriptionRu = "Сглаживание переходов вида от третьего лица и приближения.", Enabled = true },

        // Combat Analytics & Drills (46-57)
        new() { Id = "attack_cooldown_indicator", Name = "Attack Cooldown", NameRu = "Кулдаун удара", Category = "Combat", Description = "1.9+ weapon recharge progress bar below crosshair.", DescriptionRu = "Шкала готовности полного удара 1.9+ прямо под прицелом.", Enabled = true },
        new() { Id = "custom_hitmarker", Name = "Custom Hitmarker", NameRu = "Хитмаркер", Category = "Combat", Description = "Visual confirmation tick on confirmed attacks.", DescriptionRu = "Чёткий визуальный маркер попадания по цели на прицеле.", Enabled = true },
        new() { Id = "combat_statistics", Name = "Combat Statistics", NameRu = "Боевая статистика", Category = "Combat", Description = "Tracks hits, swings, combo streak, and hit ratio.", DescriptionRu = "Счётчик ударов, попаданий, текущей и рекордной серии комбо.", Enabled = false },
        new() { Id = "training_target", Name = "Training Target", NameRu = "Тренировочная мишень", Category = "Combat", Description = "Virtual target overlay for aim drills.", DescriptionRu = "Виртуальная мишень для отработки точности наведения.", Enabled = false },
        new() { Id = "damage_feedback", Name = "Damage Feedback", NameRu = "Оценка силы удара", Category = "Combat", Description = "Weapon charge efficiency readout.", DescriptionRu = "Показатель эффективности заряда оружия при каждой атаке.", Enabled = false },
        new() { Id = "attack_timing_practice", Name = "Attack Timing", NameRu = "Тайминг ударов", Category = "Combat", Description = "Rhythm meter grading 95-100% charged swings.", DescriptionRu = "Индикатор идеального ритма ударов на 95–100% заряда.", Enabled = false },
        new() { Id = "training_session_tracker", Name = "Training Tracker", NameRu = "Трекер тренировки", Category = "Combat", Description = "Records drill duration, swings, and peak combo.", DescriptionRu = "Журнал длительности разминки, количества взмахов и лучшего комбо.", Enabled = false },
        new() { Id = "crosshair_profiles", Name = "Crosshair Profiles", NameRu = "Профили прицела", Category = "Combat", Description = "Preset switching between melee, bow, and dot crosshairs.", DescriptionRu = "Готовые пресеты прицела для ближнего боя, лука и кристаллов.", Enabled = false },
        new() { Id = "accuracy_statistics", Name = "Accuracy Statistics", NameRu = "Точность атак", Category = "Combat", Description = "Rolling swing-to-hit percentage HUD metric.", DescriptionRu = "Процент точных попаданий от общего числа взмахов.", Enabled = false },
        new() { Id = "reaction_training", Name = "Reaction Training", NameRu = "Тест реакции", Category = "Combat", Description = "Visual cue reaction-time benchmark in ms.", DescriptionRu = "Замер скорости реакции на визуальный сигнал в миллисекундах.", Enabled = false },
        new() { Id = "practice_timer", Name = "Practice Timer", NameRu = "Таймер раунда", Category = "Combat", Description = "Configurable countdown round timer for PvP warm-ups.", DescriptionRu = "Обратный отсчёт времени раунда для разминки и дуэлей.", Enabled = false },
        new() { Id = "training_results", Name = "Training Results", NameRu = "Итоги боя", Category = "Combat", Description = "Summary card with accuracy & reaction metrics.", DescriptionRu = "Финальная сводка точности, комбо и средней реакции.", Enabled = false },

        // World & Utility (58-77)
        new() { Id = "waypoints", Name = "Waypoints", NameRu = "Точки пути", Category = "World", Description = "Local per-world coordinate bookmarks with distance.", DescriptionRu = "Локальные метки важных мест с расчётом дистанции до них.", Enabled = true },
        new() { Id = "death_location", Name = "Death Location", NameRu = "Точка смерти", Category = "World", Description = "Automatically records last death X/Y/Z coordinates.", DescriptionRu = "Автоматическое сохранение точных координат последней гибели.", Enabled = true },
        new() { Id = "screenshot_manager", Name = "Screenshot Manager", NameRu = "Менеджер скриншотов", Category = "Utility", Description = "Indexes captured screenshots with timestamps.", DescriptionRu = "Индексация и быстрый учёт сохранённых снимков экрана.", Enabled = true },
        new() { Id = "world_information", Name = "World Information", NameRu = "Данные о мире", Category = "World", Description = "Displays dimension, difficulty, weather, and loaded chunks.", DescriptionRu = "Измерение, сложность, погода и число загруженных чанков.", Enabled = false },
        new() { Id = "biome_information", Name = "Biome Information", NameRu = "Данные о биоме", Category = "World", Description = "Biome ID and climate details.", DescriptionRu = "Подробная сводка климата и идентификатора текущего биома.", Enabled = false },
        new() { Id = "light_level_display", Name = "Light Level", NameRu = "Уровень света", Category = "World", Description = "Block & sky light readout with mob-spawn warning.", DescriptionRu = "Освещённость блока и неба с предупреждением о спавне мобов.", Enabled = false },
        new() { Id = "chunk_border_toggle", Name = "Chunk Borders", NameRu = "Границы чанков", Category = "World", Description = "Quick toggle for vanilla chunk boundary grid.", DescriptionRu = "Быстрое включение 3D-сетки границ чанков одной кнопкой.", Enabled = false },
        new() { Id = "local_notes", Name = "Local Notes", NameRu = "Заметки", Category = "Utility", Description = "Persistent scratchpad pinned on HUD or GUI.", DescriptionRu = "Закреплённый блокнот для быстрых записей прямо на экране.", Enabled = false },
        new() { Id = "coordinate_converter", Name = "Coordinate Converter", NameRu = "Конвертер Незера", Category = "Utility", Description = "Live Overworld <-> Nether (8:1) portal calculator.", DescriptionRu = "Мгновенный пересчёт координат порталов Верхний мир <-> Незер.", Enabled = false },
        new() { Id = "session_statistics", Name = "Session Statistics", NameRu = "Статистика сессии", Category = "Utility", Description = "Aggregated jumps, distance, clicks, and playtime.", DescriptionRu = "Общая сводка активности: дистанция, прыжки, клики и время.", Enabled = false },
        new() { Id = "time_display", Name = "Time Display", NameRu = "Мировое время", Category = "World", Description = "Minecraft day count and 24-hour world time.", DescriptionRu = "Номер игрового дня и точные сутки мира Minecraft.", Enabled = false },
        new() { Id = "durability_warning", Name = "Durability Warning", NameRu = "Тревога прочности", Category = "Utility", Description = "Toast & HUD alert when held tool or armor drops low.", DescriptionRu = "Звуковое и визуальное предупреждение при поломке брони/оружия.", Enabled = true },
        new() { Id = "inventory_search", Name = "Inventory Search", NameRu = "Поиск в инвентаре", Category = "Utility", Description = "Filter and count matching items in local inventory.", DescriptionRu = "Быстрый поиск и подсчёт нужных предметов по названию.", Enabled = false },
        new() { Id = "custom_notifications", Name = "Custom Notifications", NameRu = "Уведомления клиента", Category = "Utility", Description = "Animated OBSIDIAN AURORA toast notification banner.", DescriptionRu = "Плавные всплывающие карточки событий включения модулей.", Enabled = true },
        new() { Id = "world_session_timer", Name = "World Session Timer", NameRu = "Время в мире", Category = "World", Description = "Tracks elapsed time inside the current world/server.", DescriptionRu = "Отдельный таймер времени на текущем сервере или карте.", Enabled = false },
        new() { Id = "resource_pack_manager", Name = "Resource Pack Manager", NameRu = "Ресурспаки", Category = "Utility", Description = "Active resource pack summary and quick switcher.", DescriptionRu = "Контроль подключённых пакетов ресурсов и текстур.", Enabled = false },
        new() { Id = "screenshot_gallery", Name = "Screenshot Gallery", NameRu = "Галерея снимков", Category = "Utility", Description = "Recent screenshot history browser.", DescriptionRu = "Просмотр списка последних сделанных скриншотов.", Enabled = false },
        new() { Id = "local_world_notes", Name = "Local World Notes", NameRu = "Заметки мира", Category = "World", Description = "Per-world dedicated checklist and coordinates note.", DescriptionRu = "Памятка координат баз и задач для конкретного мира.", Enabled = false },
        new() { Id = "coordinate_clipboard", Name = "Coordinate Clipboard", NameRu = "Копирование координат", Category = "Utility", Description = "One-key copy of formatted X Y Z coordinates to clipboard.", DescriptionRu = "Копирование текущих координат X Y Z в буфер обмена.", Enabled = true },
        new() { Id = "local_world_info_panel", Name = "World Info Panel", NameRu = "Панель мира", Category = "World", Description = "Comprehensive HUD card combining day, light, and biome.", DescriptionRu = "Единый инфоблок дня, освещения и биома.", Enabled = false },

        // GUI & Interface Settings (78-93)
        new() { Id = "gui_scale_manager", Name = "GUI Scale Manager", NameRu = "Масштаб интерфейса", Category = "Settings", Description = "Independent density control for ClickGUI.", DescriptionRu = "Адаптация размеров меню под любое разрешение монитора.", Enabled = true },
        new() { Id = "theme_preview", Name = "Theme Preview", NameRu = "Превью тем", Category = "Settings", Description = "Live swatch strip previewing all 8 AXSES themes.", DescriptionRu = "Мгновенный предпросмотр 8 цветовых палитр оформления.", Enabled = true },
        new() { Id = "module_search_history", Name = "Search History", NameRu = "История поиска", Category = "Settings", Description = "Stores recent ClickGUI search queries.", DescriptionRu = "Сохранение недавних поисковых запросов в меню модулей.", Enabled = true },
        new() { Id = "favorites_manager", Name = "Favorites Manager", NameRu = "Избранные модули", Category = "Settings", Description = "Pin frequently used modules to top of ClickGUI.", DescriptionRu = "Закрепление важных функций в верхней части списка.", Enabled = true },
        new() { Id = "keyboard_shortcut_editor", Name = "Shortcut Editor", NameRu = "Редактор биндов", Category = "Settings", Description = "Central keybind manager with conflict detection.", DescriptionRu = "Назначение горячих клавиш для мгновенного включения модулей.", Enabled = true },
        new() { Id = "notification_history", Name = "Notification History", NameRu = "Журнал событий", Category = "Settings", Description = "Scrollable log of recent client notifications.", DescriptionRu = "Архив последних системных уведомлений клиента.", Enabled = true },
        new() { Id = "interface_presets", Name = "Interface Presets", NameRu = "Плотность карточек", Category = "Settings", Description = "Switch between Compact, Standard, and Spacious layouts.", DescriptionRu = "Выбор компактного, стандартного или просторного вида меню.", Enabled = true },
        new() { Id = "hud_presets", Name = "HUD Presets", NameRu = "Пресеты HUD", Category = "Settings", Description = "Apply curated HUD layouts (Competitive, Minimal, Streamer).", DescriptionRu = "Готовые раскладки экрана (PvP, Выживание, Минимализм, Стрим).", Enabled = true },
        new() { Id = "animation_settings", Name = "Animation Settings", NameRu = "Настройки анимаций", Category = "Settings", Description = "Global animation speed and transition intensity.", DescriptionRu = "Регулировка скорости плавных переходов и микроанимаций.", Enabled = true },
        new() { Id = "accessibility_settings", Name = "Accessibility", NameRu = "Контрастность", Category = "Settings", Description = "High-contrast borders and crisp dividers.", DescriptionRu = "Повышенная чёткость границ карточек и элементов управления.", Enabled = false },
        new() { Id = "color_blind_presets", Name = "Color Blind Presets", NameRu = "Цветокоррекция", Category = "Settings", Description = "Protanopia, Deuteranopia, and Tritanopia palettes.", DescriptionRu = "Адаптированные палитры индикаторов для цветовосприятия.", Enabled = false },
        new() { Id = "font_size_settings", Name = "Font Settings", NameRu = "Чёткость текста", Category = "Settings", Description = "Pixel-snapped typography across HUD and ClickGUI.", DescriptionRu = "Кристально чёткий рендеринг кириллицы и латиницы без размытия.", Enabled = true },
        new() { Id = "reduced_motion_mode", Name = "Reduced Motion", NameRu = "Уменьшение движения", Category = "Settings", Description = "Replaces slide motion with instant crisp state changes.", DescriptionRu = "Мгновенное открытие окон без инерционных эффектов.", Enabled = false },
        new() { Id = "interface_reset", Name = "Interface Reset", NameRu = "Сброс позиций", Category = "Settings", Description = "Safely restores ClickGUI and HUD positions to defaults.", DescriptionRu = "Возврат расположения всех виджетов к заводским настройкам.", Enabled = false },
        new() { Id = "config_backup_manager", Name = "Config Backups", NameRu = "Резервные копии", Category = "Settings", Description = "Automatic timestamped config snapshots.", DescriptionRu = "Автоматическая защита профилей настроек от повреждения.", Enabled = true },
        new() { Id = "module_compatibility_viewer", Name = "Compatibility Viewer", NameRu = "Совместимость модов", Category = "Settings", Description = "Inspects loaded Fabric mods for overlaps.", DescriptionRu = "Проверка конфликтов с другими установленными модами Fabric.", Enabled = true }
    ];
}

public sealed class LauncherSettings
{
    public int SchemaVersion { get; set; } = 2;
    public string RootDataDir { get; set; } = GetDefaultDataDir();
    public string SelectedVersion { get; set; } = "1.21.11";
    public string SelectedProfileId { get; set; } = "";
    public string SelectedAccountUuid { get; set; } = "";
    public int AllocatedRamMb { get; set; } = 0;
    public bool AutoInstallJava { get; set; } = true;
    public string Java8Path { get; set; } = "";
    public string Java21Path { get; set; } = "";
    public int ConcurrentDownloads { get; set; } = 8;
    public string CloseBehavior { get; set; } = "Keep Launcher Open";
    public bool ReducedMotion { get; set; }
    public string Language { get; set; } = "ru_RU";
    public string ActiveThemeId { get; set; } = "axses-dark";
    public bool AutoCheckUpdates { get; set; } = true;
    public string UpdateManifestUrl { get; set; } = "https://raw.githubusercontent.com/egorbelovziiik-design/WinLauncher/main/AXSESCLIENT/shared/update-manifest.json";

    public static string GetDefaultDataDir()
    {
        string appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
        if (string.IsNullOrWhiteSpace(appData))
            appData = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), ".config");
        return Path.Combine(appData, "AXSESCLIENT");
    }
}
