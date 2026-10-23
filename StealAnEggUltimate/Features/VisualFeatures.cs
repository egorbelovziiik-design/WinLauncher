using System.Globalization;
using System.Windows.Media;

namespace StealAnEggUltimate;

public sealed record ThemeDefinition(
    string Name,
    string Accent,
    string AccentAlt,
    string Background,
    string Panel,
    string Border,
    string TextPrimary,
    string TextMuted);

public static class ThemeCatalog
{
    public static readonly IReadOnlyList<ThemeDefinition> Themes = new List<ThemeDefinition>
    {
        new("Violet", "#A855F7", "#22D3EE", "#07050D", "#120C1F", "#3B2A5C", "#F4EEFF", "#9C8FB8"),
        new("Cyber", "#22D3EE", "#F472B6", "#04080F", "#0B1622", "#1D4A5C", "#EAFBFF", "#8CB0BD"),
        new("Ember", "#FB923C", "#F43F5E", "#0D0704", "#1A0F0A", "#5C2C1D", "#FFF1E6", "#C0A092"),
        new("Mono", "#E5E7EB", "#A78BFA", "#060606", "#141414", "#333333", "#F5F5F5", "#9A9A9A")
    };

    public static ThemeDefinition Get(string? name)
        => Themes.FirstOrDefault(t => string.Equals(t.Name, name, StringComparison.OrdinalIgnoreCase)) ?? Themes[0];
}

public static class ColorUtil
{
    public static Color Parse(string? hex, Color fallback)
    {
        if (string.IsNullOrWhiteSpace(hex)) return fallback;

        try
        {
            var value = hex.Trim();
            if (!value.StartsWith('#')) value = "#" + value;
            return (Color)ColorConverter.ConvertFromString(value);
        }
        catch
        {
            return fallback;
        }
    }

    public static string ToHex(Color color) => $"#{color.R:X2}{color.G:X2}{color.B:X2}";

    /// <summary>Cycles a neon hue (0..1) for rainbow mode.</summary>
    public static Color Rainbow(double phase, double saturation = 0.85, double value = 1.0)
    {
        phase -= Math.Floor(phase);
        var h = phase * 6.0;
        var sector = (int)Math.Floor(h) % 6;
        var f = h - Math.Floor(h);
        var p = value * (1 - saturation);
        var q = value * (1 - f * saturation);
        var t = value * (1 - (1 - f) * saturation);
        var v = value;

        return sector switch
        {
            0 => Color.FromRgb(Byte(v), Byte(t), Byte(p)),
            1 => Color.FromRgb(Byte(q), Byte(v), Byte(p)),
            2 => Color.FromRgb(Byte(p), Byte(v), Byte(t)),
            3 => Color.FromRgb(Byte(p), Byte(q), Byte(v)),
            4 => Color.FromRgb(Byte(t), Byte(p), Byte(v)),
            _ => Color.FromRgb(Byte(v), Byte(p), Byte(q))
        };

        static byte Byte(double d) => (byte)Math.Clamp(Math.Round(d * 255), 0, 255);
    }
}

public static class RarityPalette
{
    public static string Hex(EggRarity rarity) => rarity switch
    {
        EggRarity.Common => "#9CA3AF",
        EggRarity.Uncommon => "#34D399",
        EggRarity.Rare => "#38BDF8",
        EggRarity.Epic => "#A855F7",
        EggRarity.Legendary => "#FBBF24",
        EggRarity.Mythic => "#F472B6",
        _ => "#9CA3AF"
    };

    public static Brush Brush(EggRarity rarity)
    {
        var brush = new SolidColorBrush(ColorUtil.Parse(Hex(rarity), Colors.Gray));
        brush.Freeze();
        return brush;
    }
}

/// <summary>Rolling FPS counter used by the overlay and the main window status bar.</summary>
public sealed class FpsCounter
{
    private int _frames;
    private double _elapsed;
    private int _fps = 60;

    public int Fps => _fps;

    public string Text => $"{_fps} FPS";

    public bool Update(double deltaSeconds)
    {
        _frames++;
        _elapsed += Math.Max(0, deltaSeconds);
        if (_elapsed < 0.5) return false;

        var next = (int)Math.Round(_frames / _elapsed);
        _frames = 0;
        _elapsed = 0;
        if (next == _fps) return false;
        _fps = next;
        return true;
    }
}

/// <summary>
/// Runtime visual state: active palette, animated accent colour, crosshair style.
/// Recomputed on the UI timer and bound through the view models.
/// </summary>
public sealed class VisualFeatures : ObservableObject
{
    private readonly FpsCounter _fps = new();
    private double _rainbowPhase;
    private Color _accent = Color.FromRgb(0xA8, 0x55, 0xF7);
    private Color _accentAlt = Color.FromRgb(0x22, 0xD3, 0xEE);
    private string _fpsText = "60 FPS";

    public ThemeDefinition Theme { get; private set; } = ThemeCatalog.Themes[0];

    public Color Accent => _accent;
    public Color AccentAlt => _accentAlt;
    public string AccentHex => ColorUtil.ToHex(_accent);

    public string FpsText
    {
        get => _fpsText;
        private set => Set(ref _fpsText, value);
    }

    public bool Rainbow { get; private set; }

    public event EventHandler? AccentChanged;

    public void Apply(AppSettings settings)
    {
        Theme = ThemeCatalog.Get(settings.Visual.Theme);
        Rainbow = settings.Visual.RainbowMode;

        if (!Rainbow)
        {
            _accent = ColorUtil.Parse(Theme.Accent, Colors.MediumPurple);
            _accentAlt = ColorUtil.Parse(Theme.AccentAlt, Colors.Cyan);
        }

        AccentChanged?.Invoke(this, EventArgs.Empty);
    }

    public void Tick(TimeSpan delta, AppSettings settings, bool fpsVisible)
    {
        var dt = Math.Clamp(delta.TotalSeconds, 0, 0.5);

        if (_fps.Update(dt))
        {
            FpsText = fpsVisible ? _fps.Text : _fps.Text;
            OnPropertyChanged(nameof(FpsText));
        }

        Rainbow = settings.Visual.RainbowMode;
        if (!Rainbow) return;

        _rainbowPhase = (_rainbowPhase + dt * 0.12) % 1.0;
        _accent = ColorUtil.Rainbow(_rainbowPhase);
        _accentAlt = ColorUtil.Rainbow(_rainbowPhase + 0.35);
        OnPropertyChanged(nameof(AccentHex));
        OnPropertyChanged(nameof(Accent));
        AccentChanged?.Invoke(this, EventArgs.Empty);
    }

    public Color EspColor(AppSettings settings)
        => ColorUtil.Parse(settings.Visual.EspColor, ColorUtil.Parse(ThemeCatalog.Themes[0].Accent, Colors.MediumPurple));

    public Color RareColor(AppSettings settings)
        => ColorUtil.Parse(settings.Visual.RareEspColor, Colors.DeepPink);
}
