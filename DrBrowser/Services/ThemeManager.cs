using System.Windows;
using System.Windows.Media;

namespace DrBrowser;

/// <summary>
/// Applies the dark/light palette and the accent colour to the application resources,
/// so every window (including private ones) follows the user's choice immediately.
/// </summary>
public static class ThemeManager
{
    public static event EventHandler? Changed;

    public static void Apply(AppSettings settings)
    {
        var app = Application.Current;
        if (app is null) return;

        var dark = settings.IsDarkTheme;
        var accent = ParseColor(settings.AccentHex, Color.FromRgb(0x7C, 0x5C, 0xFF));

        Set("WindowBackgroundBrush", dark ? "#0B0D14" : "#F4F5FA");
        Set("SurfaceBrush", dark ? "#12141F" : "#FFFFFF");
        Set("SurfaceAltBrush", dark ? "#171A28" : "#ECEEF6");
        Set("CardBrush", dark ? "#151826" : "#FFFFFF");
        Set("BorderBrush", dark ? "#252A3D" : "#D8DCE8");
        Set("TextBrush", dark ? "#F2F3F9" : "#151824");
        Set("MutedBrush", dark ? "#8A90A8" : "#5C637A");
        Set("ToolbarBrush", dark ? "#0E1018" : "#FFFFFF");
        Set("HoverBrush", dark ? "#232838" : "#E6E9F3");

        app.Resources["AccentBrush"] = new SolidColorBrush(accent);
        app.Resources["AccentSoftBrush"] = new SolidColorBrush(Color.FromArgb(42, accent.R, accent.G, accent.B));
        app.Resources["AccentDarkBrush"] = new SolidColorBrush(Darken(accent, 0.25));
        app.Resources["AccentGradientBrush"] = new LinearGradientBrush(
            accent, Color.FromRgb(0x3B, 0x82, 0xF6), 0);

        if (app.MainWindow is not null)
            app.MainWindow.Background = Brush("WindowBackgroundBrush");

        Changed?.Invoke(null, EventArgs.Empty);
    }

    private static void Set(string key, string hex)
        => Application.Current.Resources[key] = new SolidColorBrush(ParseColor(hex, Colors.Black));

    private static Brush Brush(string key)
        => Application.Current.Resources[key] as Brush ?? Brushes.Black;

    public static Color ParseColor(string? hex, Color fallback)
    {
        try
        {
            if (!string.IsNullOrWhiteSpace(hex) && ColorConverter.ConvertFromString(hex) is Color color)
                return color;
        }
        catch
        {
            // malformed colour -> fallback
        }

        return fallback;
    }

    private static Color Darken(Color color, double amount)
        => Color.FromArgb(color.A,
            (byte)(color.R * (1 - amount)),
            (byte)(color.G * (1 - amount)),
            (byte)(color.B * (1 - amount)));
}
