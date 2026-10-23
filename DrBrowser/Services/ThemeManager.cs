using System.Windows;
using System.Windows.Media;

namespace DrBrowser;

/// <summary>
/// Central OMEGA theme registry. No view owns a hard-coded application palette: every
/// surface, border, text and accent brush is replaced here at runtime.
/// </summary>
public static class ThemeManager
{
    private sealed record Palette(
        string Background,
        string Surface,
        string SurfaceAlt,
        string Card,
        string Border,
        string Text,
        string Muted,
        string Toolbar,
        string Hover,
        string Accent,
        string AccentSecondary);

    private static readonly IReadOnlyDictionary<string, Palette> Palettes =
        new Dictionary<string, Palette>(StringComparer.OrdinalIgnoreCase)
        {
            ["Omega Dark"] = new("#070910", "#111522", "#1B2130", "#111522", "#293247", "#F8FAFC", "#94A3B8", "#0B0E17", "#242C3E", "#8B5CF6", "#3B82F6"),
            ["Omega Light"] = new("#F5F7FC", "#FFFFFF", "#EDF1F8", "#FFFFFF", "#D8DFEC", "#111827", "#64748B", "#FFFFFF", "#E4E9F3", "#7C3AED", "#2563EB"),
            ["AMOLED"] = new("#000000", "#070707", "#101010", "#090909", "#242424", "#FFFFFF", "#9CA3AF", "#030303", "#191919", "#A78BFA", "#22D3EE"),
            ["Cyber Purple"] = new("#090611", "#151023", "#211735", "#181129", "#392857", "#FAF5FF", "#B7A5CC", "#100A1C", "#2C1E42", "#C026D3", "#7C3AED"),
            ["Ocean Blue"] = new("#06101A", "#0B1B2B", "#102A40", "#0D2234", "#1D405A", "#F0F9FF", "#8DB2C9", "#071724", "#17384F", "#0EA5E9", "#2563EB"),
            ["Crimson"] = new("#120709", "#211013", "#30171B", "#251216", "#4A242B", "#FFF1F2", "#C99BA3", "#180B0E", "#3A1C22", "#E11D48", "#F97316"),
            ["Emerald"] = new("#06110D", "#0D2018", "#153126", "#10271D", "#23503D", "#ECFDF5", "#8CBBA7", "#091812", "#1B3D2F", "#10B981", "#22C55E"),
            ["Gaming"] = new("#08060F", "#141020", "#20172E", "#181126", "#3B2852", "#F5F3FF", "#A99ABB", "#0D0916", "#2C1E3E", "#A855F7", "#22D3EE"),
            ["Music"] = new("#10070F", "#211020", "#32172F", "#271225", "#4D2548", "#FDF2F8", "#CAA1BD", "#170A16", "#3A1C36", "#EC4899", "#8B5CF6"),
            ["Custom"] = new("#070910", "#111522", "#1B2130", "#111522", "#293247", "#F8FAFC", "#94A3B8", "#0B0E17", "#242C3E", "#8B5CF6", "#3B82F6")
        };

    public static IReadOnlyList<string> ThemeNames { get; } =
        new[] { "Omega Dark", "Omega Light", "AMOLED", "Cyber Purple", "Ocean Blue", "Crimson", "Emerald", "Gaming", "Music", "Custom" };

    public static event EventHandler? Changed;

    public static string DefaultAccent(string? theme)
        => Resolve(theme).Accent;

    public static void Apply(AppSettings settings)
    {
        var app = Application.Current;
        if (app is null) return;

        var palette = Resolve(settings.Theme);
        var requestedAccent = string.Equals(settings.Theme, "Custom", StringComparison.OrdinalIgnoreCase)
            ? settings.AccentHex
            : settings.AccentHex;
        var accent = ParseColor(requestedAccent, ParseColor(palette.Accent, Color.FromRgb(0x8B, 0x5C, 0xF6)));
        var secondary = ParseColor(palette.AccentSecondary, Color.FromRgb(0x3B, 0x82, 0xF6));

        Set("WindowBackgroundBrush", palette.Background);
        Set("SurfaceBrush", palette.Surface);
        Set("SurfaceAltBrush", palette.SurfaceAlt);
        Set("CardBrush", palette.Card);
        Set("BorderBrush", palette.Border);
        Set("TextBrush", palette.Text);
        Set("MutedBrush", palette.Muted);
        Set("ToolbarBrush", palette.Toolbar);
        Set("HoverBrush", palette.Hover);
        Set("SuccessBrush", "#22C55E");
        Set("WarningBrush", "#F59E0B");
        Set("ErrorBrush", "#EF4444");

        app.Resources["AccentBrush"] = Frozen(new SolidColorBrush(accent));
        app.Resources["AccentSoftBrush"] = Frozen(new SolidColorBrush(Color.FromArgb(42, accent.R, accent.G, accent.B)));
        app.Resources["AccentDarkBrush"] = Frozen(new SolidColorBrush(Darken(accent, 0.25)));
        app.Resources["AccentSecondaryBrush"] = Frozen(new SolidColorBrush(secondary));
        app.Resources["AccentGradientBrush"] = Frozen(new LinearGradientBrush(accent, secondary, 0));

        if (app.MainWindow is not null)
            app.MainWindow.Background = Brush("WindowBackgroundBrush");

        Changed?.Invoke(null, EventArgs.Empty);
    }

    private static Palette Resolve(string? name)
        => name is not null && Palettes.TryGetValue(name, out var palette) ? palette : Palettes["Omega Dark"];

    private static void Set(string key, string hex)
        => Application.Current.Resources[key] = Frozen(new SolidColorBrush(ParseColor(hex, Colors.Black)));

    private static Brush Brush(string key)
        => Application.Current.Resources[key] as Brush ?? Brushes.Black;

    private static T Frozen<T>(T brush) where T : Freezable
    {
        if (brush.CanFreeze) brush.Freeze();
        return brush;
    }

    public static Color ParseColor(string? hex, Color fallback)
    {
        try
        {
            if (!string.IsNullOrWhiteSpace(hex) && ColorConverter.ConvertFromString(hex) is Color color)
                return color;
        }
        catch
        {
            // Malformed custom colour: use the selected theme's safe fallback.
        }

        return fallback;
    }

    private static Color Darken(Color color, double amount)
        => Color.FromArgb(color.A,
            (byte)(color.R * (1 - amount)),
            (byte)(color.G * (1 - amount)),
            (byte)(color.B * (1 - amount)));
}
