using System.Text.Json.Serialization;

namespace StealAnEggUltimate;

/// <summary>
/// Everything the hub persists. The whole tree is serialized to
/// %AppData%\StealAnEggUltimate\config\settings.json and auto-saved on change.
/// </summary>
public sealed class AppSettings : ObservableObject
{
    public const int CurrentSchemaVersion = 1;

    private int _schemaVersion = CurrentSchemaVersion;
    private OverlaySettings _overlay = new();
    private VisualSettings _visual = new();
    private PlayerSettings _player = new();
    private AutomationSettings _automation = new();
    private EggSettings _eggs = new();
    private BridgeSettings _bridge = new();
    private Dictionary<string, bool> _features = new(StringComparer.OrdinalIgnoreCase);

    public int SchemaVersion
    {
        get => _schemaVersion;
        set => Set(ref _schemaVersion, value);
    }

    public OverlaySettings Overlay
    {
        get => _overlay;
        set => Set(ref _overlay, value ?? new OverlaySettings());
    }

    public VisualSettings Visual
    {
        get => _visual;
        set => Set(ref _visual, value ?? new VisualSettings());
    }

    public PlayerSettings Player
    {
        get => _player;
        set => Set(ref _player, value ?? new PlayerSettings());
    }

    public AutomationSettings Automation
    {
        get => _automation;
        set => Set(ref _automation, value ?? new AutomationSettings());
    }

    public EggSettings Eggs
    {
        get => _eggs;
        set => Set(ref _eggs, value ?? new EggSettings());
    }

    public BridgeSettings Bridge
    {
        get => _bridge;
        set => Set(ref _bridge, value ?? new BridgeSettings());
    }

    /// <summary>Flat ON/OFF state for every feature toggle, keyed by feature id.</summary>
    public Dictionary<string, bool> Features
    {
        get => _features;
        set => Set(ref _features, value ?? new Dictionary<string, bool>(StringComparer.OrdinalIgnoreCase));
    }

    /// <summary>Extra user keybinds (id -> key name).</summary>
    public Dictionary<string, string> Keybinds { get; set; } = new(StringComparer.OrdinalIgnoreCase);

    [JsonIgnore]
    public IEnumerable<ObservableObject> ChildSettings
    {
        get
        {
            yield return Overlay;
            yield return Visual;
            yield return Player;
            yield return Automation;
            yield return Eggs;
            yield return Bridge;
        }
    }

    public AppSettings Clone() => SettingsJson.Clone(this);

    /// <summary>Clamps out-of-range / corrupted values coming from disk.</summary>
    public void Normalize()
    {
        SchemaVersion = CurrentSchemaVersion;

        Overlay.Normalize();
        Visual.Normalize();
        Player.Normalize();
        Automation.Normalize();
        Eggs.Normalize();
        Bridge.Normalize();

        if (Overlay.MenuKey is null or "") Overlay.MenuKey = "RightShift";
        if (string.IsNullOrWhiteSpace(Visual.EspColor)) Visual.EspColor = VisualSettings.DefaultEspColor;
        if (string.IsNullOrWhiteSpace(Visual.RareEspColor)) Visual.RareEspColor = VisualSettings.DefaultRareColor;

        if (Features is null) Features = new(StringComparer.OrdinalIgnoreCase);
        if (!ReferenceEquals(Features.Comparer, StringComparer.OrdinalIgnoreCase))
            Features = new Dictionary<string, bool>(Features, StringComparer.OrdinalIgnoreCase);
        Keybinds ??= new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
    }
}

public sealed class OverlaySettings : ObservableObject
{
    public const int DefaultMenuWidth = 430;

    private string _menuKey = "RightShift";
    private double _opacity = 0.96;
    private double _uiScale = 1.0;
    private double _menuOffsetX = 56;
    private double _menuOffsetY = 56;
    private bool _autoStartOverlay;
    private bool _rememberPosition = true;
    private int _menuWidth = DefaultMenuWidth;
    private bool _startMenuOpen;

    /// <summary>Virtual-key name used to toggle the in-game menu (default: Right Shift).</summary>
    public string MenuKey
    {
        get => _menuKey;
        set => Set(ref _menuKey, value);
    }

    public double Opacity
    {
        get => _opacity;
        set => Set(ref _opacity, value);
    }

    public double UiScale
    {
        get => _uiScale;
        set => Set(ref _uiScale, value);
    }

    /// <summary>Menu offset (in px) from the top-left corner of the Roblox client area.</summary>
    public double MenuOffsetX
    {
        get => _menuOffsetX;
        set => Set(ref _menuOffsetX, value);
    }

    public double MenuOffsetY
    {
        get => _menuOffsetY;
        set => Set(ref _menuOffsetY, value);
    }

    public bool AutoStartOverlay
    {
        get => _autoStartOverlay;
        set => Set(ref _autoStartOverlay, value);
    }

    public bool RememberPosition
    {
        get => _rememberPosition;
        set => Set(ref _rememberPosition, value);
    }

    public int MenuWidth
    {
        get => _menuWidth;
        set => Set(ref _menuWidth, value);
    }

    public bool StartMenuOpen
    {
        get => _startMenuOpen;
        set => Set(ref _startMenuOpen, value);
    }

    public void Normalize()
    {
        Opacity = Math.Clamp(Opacity, 0.25, 1.0);
        UiScale = Math.Clamp(UiScale, 0.7, 1.6);
        MenuWidth = Math.Clamp(MenuWidth, 320, 620);
        if (double.IsNaN(MenuOffsetX) || MenuOffsetX < -4000 || MenuOffsetX > 4000) MenuOffsetX = 56;
        if (double.IsNaN(MenuOffsetY) || MenuOffsetY < -4000 || MenuOffsetY > 4000) MenuOffsetY = 56;
    }
}

public sealed class VisualSettings : ObservableObject
{
    public const string DefaultEspColor = "#C084FC";
    public const string DefaultRareColor = "#FF4FD8";

    private string _espColor = DefaultEspColor;
    private string _rareEspColor = DefaultRareColor;
    private string _theme = "Violet";
    private bool _rainbowMode;
    private bool _showFps = true;
    private bool _showNotifications = true;
    private bool _crosshair;
    private string _crosshairStyle = "Cross";
    private double _crosshairSize = 18;
    private bool _espShowDistance = true;
    private bool _espShowValue = true;
    private string _espStyle = "Box + Label";

    public string EspColor
    {
        get => _espColor;
        set => Set(ref _espColor, value);
    }

    public string RareEspColor
    {
        get => _rareEspColor;
        set => Set(ref _rareEspColor, value);
    }

    public string Theme
    {
        get => _theme;
        set => Set(ref _theme, value);
    }

    public bool RainbowMode
    {
        get => _rainbowMode;
        set => Set(ref _rainbowMode, value);
    }

    public bool ShowFps
    {
        get => _showFps;
        set => Set(ref _showFps, value);
    }

    public bool ShowNotifications
    {
        get => _showNotifications;
        set => Set(ref _showNotifications, value);
    }

    public bool Crosshair
    {
        get => _crosshair;
        set => Set(ref _crosshair, value);
    }

    public string CrosshairStyle
    {
        get => _crosshairStyle;
        set => Set(ref _crosshairStyle, value);
    }

    public double CrosshairSize
    {
        get => _crosshairSize;
        set => Set(ref _crosshairSize, value);
    }

    public bool EspShowDistance
    {
        get => _espShowDistance;
        set => Set(ref _espShowDistance, value);
    }

    public bool EspShowValue
    {
        get => _espShowValue;
        set => Set(ref _espShowValue, value);
    }

    public string EspStyle
    {
        get => _espStyle;
        set => Set(ref _espStyle, value);
    }

    public void Normalize()
    {
        if (Theme is not ("Violet" or "Cyber" or "Ember" or "Mono")) Theme = "Violet";
        if (CrosshairStyle is not ("Cross" or "Dot" or "Circle")) CrosshairStyle = "Cross";
        CrosshairSize = Math.Clamp(CrosshairSize, 6, 48);
    }
}

public sealed class PlayerSettings : ObservableObject
{
    private double _walkSpeed = 16;
    private double _jumpPower = 50;
    private double _flySpeed = 40;
    private double _teleportX;
    private double _teleportY = 12;
    private double _teleportZ;
    private string _flyKey = "F";
    private string _speedBoostKey = "X";
    private string _teleportKey = "T";

    public double WalkSpeed
    {
        get => _walkSpeed;
        set => Set(ref _walkSpeed, value);
    }

    public double JumpPower
    {
        get => _jumpPower;
        set => Set(ref _jumpPower, value);
    }

    public double FlySpeed
    {
        get => _flySpeed;
        set => Set(ref _flySpeed, value);
    }

    public double TeleportX
    {
        get => _teleportX;
        set => Set(ref _teleportX, value);
    }

    public double TeleportY
    {
        get => _teleportY;
        set => Set(ref _teleportY, value);
    }

    public double TeleportZ
    {
        get => _teleportZ;
        set => Set(ref _teleportZ, value);
    }

    public string FlyKey
    {
        get => _flyKey;
        set => Set(ref _flyKey, value);
    }

    public string SpeedBoostKey
    {
        get => _speedBoostKey;
        set => Set(ref _speedBoostKey, value);
    }

    public string TeleportKey
    {
        get => _teleportKey;
        set => Set(ref _teleportKey, value);
    }

    public void Normalize()
    {
        WalkSpeed = Math.Clamp(WalkSpeed, 8, 200);
        JumpPower = Math.Clamp(JumpPower, 0, 500);
        FlySpeed = Math.Clamp(FlySpeed, 5, 200);
        if (double.IsNaN(TeleportX) || double.IsInfinity(TeleportX)) TeleportX = 0;
        if (double.IsNaN(TeleportY) || double.IsInfinity(TeleportY)) TeleportY = 12;
        if (double.IsNaN(TeleportZ) || double.IsInfinity(TeleportZ)) TeleportZ = 0;
        if (string.IsNullOrWhiteSpace(FlyKey)) FlyKey = "F";
        if (string.IsNullOrWhiteSpace(SpeedBoostKey)) SpeedBoostKey = "X";
        if (string.IsNullOrWhiteSpace(TeleportKey)) TeleportKey = "T";
    }
}

public sealed class AutomationSettings : ObservableObject
{
    private int _intervalMs = 1500;
    private string _target = "Nearest";
    private string _rarityPriority = "Rare first";
    private bool _notify = true;
    private bool _autoStart;
    private int _maxActionsPerCycle = 3;

    public int IntervalMs
    {
        get => _intervalMs;
        set => Set(ref _intervalMs, value);
    }

    public string Target
    {
        get => _target;
        set => Set(ref _target, value);
    }

    public string RarityPriority
    {
        get => _rarityPriority;
        set => Set(ref _rarityPriority, value);
    }

    public bool Notify
    {
        get => _notify;
        set => Set(ref _notify, value);
    }

    public bool AutoStart
    {
        get => _autoStart;
        set => Set(ref _autoStart, value);
    }

    public int MaxActionsPerCycle
    {
        get => _maxActionsPerCycle;
        set => Set(ref _maxActionsPerCycle, value);
    }

    public void Normalize()
    {
        IntervalMs = Math.Clamp(IntervalMs, 200, 15000);
        MaxActionsPerCycle = Math.Clamp(MaxActionsPerCycle, 1, 10);
        if (Target is not ("Nearest" or "Rarest" or "Highest value" or "All")) Target = "Nearest";
        if (RarityPriority is not ("Rare first" or "Value first" or "Distance first")) RarityPriority = "Rare first";
    }
}

public sealed class BridgeSettings : ObservableObject
{
    private int _port = 8787;

    /// <summary>Loopback port for the official test place bridge (127.0.0.1 only).</summary>
    public int Port
    {
        get => _port;
        set => Set(ref _port, value);
    }

    public void Normalize() => Port = Math.Clamp(Port, 1024, 65500);
}

public sealed class EggSettings : ObservableObject
{
    private bool _showCommon = true;
    private bool _showUncommon = true;
    private bool _showRare = true;
    private bool _showEpic = true;
    private bool _showLegendary = true;
    private bool _showMythic = true;
    private int _minValue;
    private double _maxDistance = 500;

    public bool ShowCommon
    {
        get => _showCommon;
        set => Set(ref _showCommon, value);
    }

    public bool ShowUncommon
    {
        get => _showUncommon;
        set => Set(ref _showUncommon, value);
    }

    public bool ShowRare
    {
        get => _showRare;
        set => Set(ref _showRare, value);
    }

    public bool ShowEpic
    {
        get => _showEpic;
        set => Set(ref _showEpic, value);
    }

    public bool ShowLegendary
    {
        get => _showLegendary;
        set => Set(ref _showLegendary, value);
    }

    public bool ShowMythic
    {
        get => _showMythic;
        set => Set(ref _showMythic, value);
    }

    public int MinValue
    {
        get => _minValue;
        set => Set(ref _minValue, value);
    }

    public double MaxDistance
    {
        get => _maxDistance;
        set => Set(ref _maxDistance, value);
    }

    public void Normalize()
    {
        MinValue = Math.Clamp(MinValue, 0, 100000);
        MaxDistance = Math.Clamp(MaxDistance, 10, 5000);
    }

    public bool IsRarityVisible(EggRarity rarity) => rarity switch
    {
        EggRarity.Common => ShowCommon,
        EggRarity.Uncommon => ShowUncommon,
        EggRarity.Rare => ShowRare,
        EggRarity.Epic => ShowEpic,
        EggRarity.Legendary => ShowLegendary,
        EggRarity.Mythic => ShowMythic,
        _ => true
    };
}
