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
    public static string ToDisplayBadge(this VersionInstallState state) => state switch
    {
        VersionInstallState.NotInstalled => "NOT INSTALLED",
        VersionInstallState.Installing => "INSTALLING",
        VersionInstallState.Installed => "INSTALLED",
        VersionInstallState.UpdateAvailable => "UPDATE AVAILABLE",
        VersionInstallState.RepairRequired => "REPAIR REQUIRED",
        VersionInstallState.Launching => "LAUNCHING",
        VersionInstallState.Running => "RUNNING",
        _ => "NOT INSTALLED"
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
    public required int RequiredJavaMajor { get; init; }
    public required string FabricLoaderVersion { get; init; }
    public required string FabricApiVersion { get; init; }
    public required string ModJarFileName { get; init; }
    public VersionInstallState State { get; set; } = VersionInstallState.NotInstalled;
    public double Progress { get; set; }
    public string StatusDetail { get; set; } = "Ready to install";
    public long DiskUsageBytes { get; set; }
    public int VerifiedLibraryCount { get; set; }

    public static List<SupportedVersionSpec> CreateDefaults() =>
    [
        new SupportedVersionSpec
        {
            MinecraftVersion = "1.16.5",
            DisplayTitle = "Minecraft 1.16.5",
            Subtitle = "Classic Competitive & Speedrun Era • Java 8 • Fabric 0.20.1",
            RequiredJavaMajor = 8,
            FabricLoaderVersion = "0.20.1",
            FabricApiVersion = "0.42.0+1.16",
            ModJarFileName = "AXSESCLIENT-1.16.5.jar"
        },
        new SupportedVersionSpec
        {
            MinecraftVersion = "1.21.4",
            DisplayTitle = "Minecraft 1.21.4",
            Subtitle = "Modern Performance & PvP Training • Java 21 • Fabric 0.20.1",
            RequiredJavaMajor = 21,
            FabricLoaderVersion = "0.20.1",
            FabricApiVersion = "0.119.4+1.21.4",
            ModJarFileName = "AXSESCLIENT-1.21.4.jar"
        },
        new SupportedVersionSpec
        {
            MinecraftVersion = "1.21.11",
            DisplayTitle = "Minecraft 1.21.11",
            Subtitle = "Latest Flagship Engine (JOML + RenderPipelines) • Java 21 • Fabric 0.20.1",
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
    public string Name { get; set; } = "Default Profile";
    public string MinecraftVersion { get; set; } = "1.21.4";
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
        new() { Id = "axses-dark", Name = "AXSES DARK", BgPrimary = "#070911", BgSecondary = "#10131D", BgSurface = "#191D2B", AccentPrimary = "#9060FF", AccentSecondary = "#536FFF", AccentCyan = "#00D9FF", TextPrimary = "#F7F8FF", TextSecondary = "#929BB1", Success = "#39DFA2", Warning = "#FFC16A", Danger = "#FF5B79" },
        new() { Id = "axses-purple", Name = "AXSES PURPLE", BgPrimary = "#0B0714", BgSecondary = "#140E24", BgSurface = "#1F1736", AccentPrimary = "#B266FF", AccentSecondary = "#7C4DFF", AccentCyan = "#E040FB", TextPrimary = "#FAF7FF", TextSecondary = "#A699C2", Success = "#39DFA2", Warning = "#FFC16A", Danger = "#FF5B79" },
        new() { Id = "axses-blue", Name = "AXSES BLUE", BgPrimary = "#060B14", BgSecondary = "#0D1626", BgSurface = "#152238", AccentPrimary = "#3B82F6", AccentSecondary = "#2563EB", AccentCyan = "#00D9FF", TextPrimary = "#F4F8FF", TextSecondary = "#8FA4C4", Success = "#39DFA2", Warning = "#FFC16A", Danger = "#FF5B79" },
        new() { Id = "axses-cyber", Name = "AXSES CYBER", BgPrimary = "#050A0E", BgSecondary = "#0B141C", BgSurface = "#12202C", AccentPrimary = "#00D9FF", AccentSecondary = "#00FFA3", AccentCyan = "#9060FF", TextPrimary = "#F0FCFF", TextSecondary = "#84A9B8", Success = "#00FFA3", Warning = "#FFE066", Danger = "#FF4D6D" },
        new() { Id = "axses-midnight", Name = "AXSES MIDNIGHT", BgPrimary = "#04050A", BgSecondary = "#090C15", BgSurface = "#111625", AccentPrimary = "#6366F1", AccentSecondary = "#4F46E5", AccentCyan = "#38BDF8", TextPrimary = "#F5F7FF", TextSecondary = "#7E87A1", Success = "#34D399", Warning = "#FBBF24", Danger = "#F87171" },
        new() { Id = "axses-minimal", Name = "AXSES MINIMAL", BgPrimary = "#0C0D10", BgSecondary = "#14161B", BgSurface = "#1D2028", AccentPrimary = "#E2E8F0", AccentSecondary = "#94A3B8", AccentCyan = "#38BDF8", TextPrimary = "#FFFFFF", TextSecondary = "#94A3B8", Success = "#4ADE80", Warning = "#FACC15", Danger = "#FB7185" },
        new() { Id = "axses-graphite", Name = "AXSES GRAPHITE", BgPrimary = "#0E1013", BgSecondary = "#171A1F", BgSurface = "#22262E", AccentPrimary = "#8B5CF6", AccentSecondary = "#64748B", AccentCyan = "#22D3EE", TextPrimary = "#F1F5F9", TextSecondary = "#94A3B8", Success = "#39DFA2", Warning = "#FFC16A", Danger = "#FF5B79" },
        new() { Id = "axses-neon", Name = "AXSES NEON", BgPrimary = "#07060F", BgSecondary = "#110E20", BgSurface = "#1B1632", AccentPrimary = "#FF2E93", AccentSecondary = "#9060FF", AccentCyan = "#00F5D4", TextPrimary = "#FFF7FC", TextSecondary = "#A89CC2", Success = "#00F5D4", Warning = "#FFD166", Danger = "#FF2E93" }
    ];
}

public sealed class ModuleCatalogItem
{
    public required string Id { get; init; }
    public required string Name { get; init; }
    public required string Category { get; init; }
    public required string Description { get; init; }
    public bool Enabled { get; set; }
    public bool Favorite { get; set; }

    public static List<ModuleCatalogItem> CreateAll90Modules() =>
    [
        // Visual & Render (01-30)
        new() { Id = "fullbright", Name = "FullBright", Category = "Render", Description = "Uniform gamma & ambient brightness control for dark environments.", Enabled = true },
        new() { Id = "smooth_zoom", Name = "Smooth Zoom", Category = "Render", Description = "Interpolated optical zoom with scroll-wheel magnification.", Enabled = true },
        new() { Id = "custom_crosshair", Name = "Custom Crosshair", Category = "Render", Description = "Vector crosshair with dynamic movement/cooldown spread.", Enabled = false },
        new() { Id = "freelook", Name = "FreeLook", Category = "Render", Description = "Hold-key 360 perspective orbit preserving movement vector.", Enabled = true },
        new() { Id = "fps_counter", Name = "FPS Counter", Category = "HUD", Description = "Real-time frame rate HUD with frame-time stability indicator.", Enabled = true, Favorite = true },
        new() { Id = "coordinates_hud", Name = "Coordinates HUD", Category = "HUD", Description = "X/Y/Z coordinates, chunk offset, and Nether/Overworld ratio.", Enabled = true, Favorite = true },
        new() { Id = "armor_hud", Name = "Armor HUD", Category = "HUD", Description = "Equipped armor pieces with color-coded durability bars.", Enabled = true },
        new() { Id = "potion_hud", Name = "Potion HUD", Category = "HUD", Description = "Active status effects with amplifier and countdown timer.", Enabled = true },
        new() { Id = "keystrokes", Name = "Keystrokes", Category = "HUD", Description = "Animated WASD, Space, LMB/RMB keystroke visualizer with CPS.", Enabled = true, Favorite = true },
        new() { Id = "cps_counter", Name = "CPS Counter", Category = "HUD", Description = "Rolling 1-second left and right mouse clicks per second.", Enabled = true },
        new() { Id = "custom_fov", Name = "Custom FOV", Category = "Render", Description = "Fine-grained field-of-view controller and dynamic clamp.", Enabled = false },
        new() { Id = "direction_hud", Name = "Direction HUD", Category = "HUD", Description = "Cardinal & intercardinal heading readout with yaw angle.", Enabled = false },
        new() { Id = "biome_display", Name = "Biome Display", Category = "HUD", Description = "Current biome badge on the HUD.", Enabled = false },
        new() { Id = "ping_display", Name = "Ping Display", Category = "HUD", Description = "Server connection latency in milliseconds.", Enabled = true },
        new() { Id = "memory_display", Name = "Memory Display", Category = "HUD", Description = "JVM heap usage percentage and allocated MB.", Enabled = false },
        new() { Id = "session_timer", Name = "Session Timer", Category = "HUD", Description = "Elapsed client play session duration.", Enabled = false },
        new() { Id = "durability_hud", Name = "Durability HUD", Category = "HUD", Description = "Held item remaining uses and percentage.", Enabled = false },
        new() { Id = "inventory_preview", Name = "Inventory Preview", Category = "HUD", Description = "Compact 3x9 main inventory grid overlay.", Enabled = false },
        new() { Id = "custom_hotbar", Name = "Custom Hotbar", Category = "Render", Description = "Themed AXSES NEO-DARK hotbar accent frame.", Enabled = false },
        new() { Id = "custom_scoreboard", Name = "Custom Scoreboard", Category = "Render", Description = "Clean scoreboard styling with optional number hiding.", Enabled = true },
        new() { Id = "custom_boss_bar", Name = "Custom Boss Bar", Category = "Render", Description = "Compact boss bar scaling and percentage overlay.", Enabled = false },
        new() { Id = "custom_chat_bg", Name = "Custom Chat Background", Category = "Render", Description = "Soft translucent chat backdrop with adjustable opacity.", Enabled = true },
        new() { Id = "custom_tooltip", Name = "Custom Tooltip Style", Category = "Render", Description = "AXSES NEO-DARK bordered item tooltip theme.", Enabled = true },
        new() { Id = "hud_bg_blur", Name = "HUD Background Blur", Category = "Render", Description = "Frosted glass backdrop tinting behind HUD widgets.", Enabled = true },
        new() { Id = "screenshot_overlay", Name = "Screenshot Overlay", Category = "Render", Description = "Instant preview toast when F2 screenshot is saved.", Enabled = true },
        new() { Id = "custom_item_counter", Name = "Custom Item Counter", Category = "HUD", Description = "Total inventory count of arrows, totems, or potions.", Enabled = false },
        new() { Id = "clock_hud", Name = "Clock HUD", Category = "HUD", Description = "Real-world or in-game 24h/12h clock widget.", Enabled = false },
        new() { Id = "compass_hud", Name = "Compass HUD", Category = "HUD", Description = "Horizontal scrolling degree tape compass.", Enabled = true },
        new() { Id = "custom_action_bar", Name = "Custom Action Bar", Category = "HUD", Description = "Elevated themed action bar readout.", Enabled = false },
        new() { Id = "session_info", Name = "Session Information", Category = "HUD", Description = "Unified summary card (FPS, Ping, Playtime, World).", Enabled = false },

        // Movement & Player (31-42)
        new() { Id = "toggle_sprint", Name = "Toggle Sprint", Category = "Movement", Description = "Legitimate client-side sprint keylatch without packet modification.", Enabled = true, Favorite = true },
        new() { Id = "toggle_sneak", Name = "Toggle Sneak", Category = "Movement", Description = "Client-side sneak toggle pausing safely when inventories open.", Enabled = false },
        new() { Id = "movement_speed_hud", Name = "Movement Speed HUD", Category = "Movement", Description = "Horizontal velocity in blocks per second (m/s).", Enabled = false },
        new() { Id = "camera_bobbing", Name = "Camera Bobbing Settings", Category = "Player", Description = "View bobbing control keeping crosshair steady.", Enabled = false },
        new() { Id = "fov_transitions", Name = "FOV Transition Settings", Category = "Movement", Description = "Smooth sprint and speed FOV transitions.", Enabled = true },
        new() { Id = "sprint_indicator", Name = "Sprint Indicator", Category = "Movement", Description = "HUD status pill showing active sprint mode.", Enabled = true },
        new() { Id = "sneak_indicator", Name = "Sneak Indicator", Category = "Movement", Description = "HUD status pill showing active sneak mode.", Enabled = false },
        new() { Id = "direction_indicator", Name = "Direction Indicator", Category = "Player", Description = "Compact velocity vector indicator.", Enabled = false },
        new() { Id = "jump_statistics", Name = "Jump Statistics", Category = "Movement", Description = "Tracks session jump count and sprint-jump cadence.", Enabled = false },
        new() { Id = "movement_tracker", Name = "Movement Session Tracker", Category = "Movement", Description = "Cumulative distance traveled in meters/kilometers.", Enabled = false },
        new() { Id = "sensitivity_profiles", Name = "Custom Camera Sensitivity Profiles", Category = "Player", Description = "Switchable mouse sensitivity presets (Low / Standard / High).", Enabled = false },
        new() { Id = "camera_transitions", Name = "Camera Transition Settings", Category = "Player", Description = "Smooth easing on perspective and zoom transitions.", Enabled = true },

        // Combat Training (43-54)
        new() { Id = "attack_cooldown", Name = "Attack Cooldown Indicator", Category = "Combat Training", Description = "1.9+ weapon recharge progress bar below crosshair.", Enabled = true },
        new() { Id = "custom_hitmarker", Name = "Custom Hitmarker", Category = "Combat Training", Description = "Visual confirmation tick on confirmed attacks.", Enabled = false },
        new() { Id = "combat_statistics", Name = "Combat Statistics", Category = "Combat Training", Description = "Tracks hits, swings, combo streak, and hit ratio.", Enabled = false },
        new() { Id = "training_target", Name = "Training Target", Category = "Combat Training", Description = "Singleplayer virtual target dummy overlay for aim drills.", Enabled = false },
        new() { Id = "damage_feedback", Name = "Damage Feedback", Category = "Combat Training", Description = "Singleplayer training charge efficiency readout.", Enabled = false },
        new() { Id = "attack_timing", Name = "Attack Timing Practice", Category = "Combat Training", Description = "Singleplayer rhythm meter grading 95-100% charged swings.", Enabled = false },
        new() { Id = "training_tracker", Name = "Training Session Tracker", Category = "Combat Training", Description = "Records drill duration, swings, and peak combo.", Enabled = false },
        new() { Id = "crosshair_profiles", Name = "Crosshair Profiles", Category = "Combat Training", Description = "Preset switching between melee, bow, and minimalist crosshairs.", Enabled = false },
        new() { Id = "accuracy_stats", Name = "Accuracy Statistics", Category = "Combat Training", Description = "Rolling swing-to-hit percentage HUD metric.", Enabled = false },
        new() { Id = "reaction_training", Name = "Reaction Training", Category = "Combat Training", Description = "Singleplayer visual cue reaction-time benchmark in ms.", Enabled = false },
        new() { Id = "practice_timer", Name = "Practice Timer", Category = "Combat Training", Description = "Configurable countdown round timer for warm-up drills.", Enabled = false },
        new() { Id = "training_results", Name = "Training Results", Category = "Combat Training", Description = "Post-drill summary card with accuracy & reaction metrics.", Enabled = false },

        // World & Utility (55-74)
        new() { Id = "waypoints", Name = "Waypoints", Category = "World", Description = "Local per-world coordinate bookmarks with distance readout.", Enabled = true },
        new() { Id = "death_location", Name = "Death Location", Category = "World", Description = "Automatically records last death X/Y/Z coordinates.", Enabled = true },
        new() { Id = "screenshot_manager", Name = "Screenshot Manager", Category = "Utility", Description = "Indexes captured screenshots with timestamps.", Enabled = true },
        new() { Id = "world_info", Name = "World Information", Category = "World", Description = "Displays dimension, difficulty, weather, and loaded chunks.", Enabled = false },
        new() { Id = "biome_info", Name = "Biome Information", Category = "World", Description = "Biome ID, temperature, and precipitation capability.", Enabled = false },
        new() { Id = "light_level", Name = "Light Level Display", Category = "World", Description = "Block & sky light readout at player feet with spawn alert.", Enabled = false },
        new() { Id = "chunk_borders", Name = "Chunk Border Toggle", Category = "World", Description = "Quick toggle for vanilla chunk boundary grid.", Enabled = false },
        new() { Id = "local_notes", Name = "Local Notes", Category = "Utility", Description = "Persistent scratchpad pinned on HUD or GUI.", Enabled = false },
        new() { Id = "coord_converter", Name = "Coordinate Converter", Category = "Utility", Description = "Live Overworld <-> Nether (8:1) portal coordinate calculator.", Enabled = false },
        new() { Id = "session_stats", Name = "Session Statistics", Category = "Utility", Description = "Aggregated jumps, distance, clicks, and playtime.", Enabled = false },
        new() { Id = "time_display", Name = "Time Display", Category = "World", Description = "Minecraft day count and 24-hour world time.", Enabled = false },
        new() { Id = "durability_warning", Name = "Durability Warning", Category = "Utility", Description = "Toast & HUD alert when held tool or armor drops below threshold.", Enabled = true },
        new() { Id = "inventory_search", Name = "Inventory Search", Category = "Utility", Description = "Filter and count matching items in local player inventory.", Enabled = false },
        new() { Id = "custom_notifications", Name = "Custom Notifications", Category = "Utility", Description = "Animated AXSES NEO-DARK toast notification banner.", Enabled = true },
        new() { Id = "world_session_timer", Name = "World Session Timer", Category = "World", Description = "Tracks elapsed time inside the current world/server.", Enabled = false },
        new() { Id = "resource_pack_mgr", Name = "Resource Pack Manager", Category = "Utility", Description = "Active resource pack summary and quick switcher.", Enabled = false },
        new() { Id = "screenshot_gallery", Name = "Screenshot Gallery", Category = "Utility", Description = "Recent screenshot history browser.", Enabled = false },
        new() { Id = "local_world_notes", Name = "Local World Notes", Category = "World", Description = "Per-world dedicated checklist and coordinates note.", Enabled = false },
        new() { Id = "coord_clipboard", Name = "Coordinate Clipboard", Category = "Utility", Description = "One-key copy of formatted X Y Z coordinates to clipboard.", Enabled = true },
        new() { Id = "local_world_panel", Name = "Local World Information Panel", Category = "World", Description = "Comprehensive HUD card combining world day, light, and biome.", Enabled = false },

        // GUI Features (75-90)
        new() { Id = "gui_scale_mgr", Name = "GUI Scale Manager", Category = "Settings", Description = "Independent scale factor (0.75x - 1.50x) for AXSES ClickGUI.", Enabled = true },
        new() { Id = "theme_preview", Name = "Theme Preview", Category = "Settings", Description = "Live swatch strip previewing all 8 AXSES themes.", Enabled = true },
        new() { Id = "search_history", Name = "Module Search History", Category = "Settings", Description = "Stores recent ClickGUI search queries for one-click recall.", Enabled = true },
        new() { Id = "favorites_mgr", Name = "Favorites Manager", Category = "Settings", Description = "Pin frequently used modules to top of ClickGUI.", Enabled = true },
        new() { Id = "shortcut_editor", Name = "Keyboard Shortcut Editor", Category = "Settings", Description = "Central keybind manager with conflict detection.", Enabled = true },
        new() { Id = "notification_history", Name = "Notification History", Category = "Settings", Description = "Scrollable log of recent client notifications.", Enabled = true },
        new() { Id = "interface_presets", Name = "Interface Presets", Category = "Settings", Description = "Switch between Compact, Standard, and Spacious card layouts.", Enabled = true },
        new() { Id = "hud_presets", Name = "HUD Presets", Category = "Settings", Description = "Apply curated HUD layouts (Competitive, Minimal, Survival, Streamer).", Enabled = true },
        new() { Id = "animation_settings", Name = "Animation Settings", Category = "Settings", Description = "Global animation speed and transition intensity.", Enabled = true },
        new() { Id = "accessibility_settings", Name = "Accessibility Settings", Category = "Settings", Description = "High-contrast borders and larger hit targets.", Enabled = false },
        new() { Id = "color_blind_presets", Name = "Color Blindness Presets", Category = "Settings", Description = "Protanopia, Deuteranopia, and Tritanopia status palettes.", Enabled = false },
        new() { Id = "font_size_settings", Name = "Font Size Settings", Category = "Settings", Description = "Typography scale multiplier across HUD and ClickGUI.", Enabled = true },
        new() { Id = "reduced_motion", Name = "Reduced Motion Mode", Category = "Settings", Description = "Replaces slide/scale motion with instant crisp state changes.", Enabled = false },
        new() { Id = "interface_reset", Name = "Interface Reset", Category = "Settings", Description = "Safely restores ClickGUI and HUD positions to factory defaults.", Enabled = false },
        new() { Id = "config_backup_mgr", Name = "Config Backup Manager", Category = "Settings", Description = "Automatic timestamped config snapshots and one-click restore.", Enabled = true },
        new() { Id = "compat_viewer", Name = "Module Compatibility Viewer", Category = "Settings", Description = "Inspects loaded Fabric mods for potential rendering/keybind overlaps.", Enabled = true }
    ];
}

public sealed class LauncherSettings
{
    public int SchemaVersion { get; set; } = 1;
    public string RootDataDir { get; set; } = GetDefaultDataDir();
    public string SelectedVersion { get; set; } = "1.21.4";
    public string SelectedProfileId { get; set; } = "";
    public string SelectedAccountUuid { get; set; } = "";
    public string Java8Path { get; set; } = "";
    public string Java21Path { get; set; } = "";
    public int ConcurrentDownloads { get; set; } = 8;
    public string CloseBehavior { get; set; } = "Keep Launcher Open"; // Keep Launcher Open | Minimize to Taskbar | Close on Launch
    public bool ReducedMotion { get; set; }
    public string Language { get; set; } = "en_US";
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
