using System.Windows;
using System.Windows.Input;
using System.Windows.Media;

namespace DrBrowser;

/// <summary>DrAI persona (system prompt flavour).</summary>
public sealed record AiPersonaInfo(string Id, string Name, string Glyph, string Hint);

/// <summary>
/// Shell-level features added in 2.0: the DrMusic mini player, gradient avatars for the
/// profile, DrAI personas and clipboard actions. Kept in its own partial file so the
/// main view-model stays readable.
/// </summary>
public sealed partial class MainViewModel
{
    // ------------------------------------------------------------------
    // DrMusic mini player (always reachable from the toolbar / status bar)
    // ------------------------------------------------------------------

    public bool MusicBarVisible => Music.Service.HasTrack;
    public string MusicNowTitle => Music.Service.CurrentTitle;
    public string MusicNowBadge => Music.Service.NowBadge;
    public string MusicNowSubtitle => Music.Service.CurrentSubtitle;
    public bool MusicIsPlaying => Music.Service.IsPlaying;
    public string MusicPlayGlyph => Music.Service.IsPlaying ? "⏸" : "▶";
    public bool MusicIsStreaming => Music.Service.IsStreaming;

    public double MusicVolume
    {
        get => Music.Service.Volume;
        set
        {
            Music.Service.Volume = value;
            Settings.MusicVolume = value;
            Raise(nameof(MusicVolume));
        }
    }

    public ICommand ToggleMusicCommand => Cmd("music.toggle", () =>
    {
        Music.Service.TogglePlay();
        RaiseMusicBar();
    });

    public ICommand MusicNextCommand => Cmd("music.next", () =>
    {
        Music.Service.Next();
        RaiseMusicBar();
    });

    public ICommand MusicPreviousCommand => Cmd("music.prev", () =>
    {
        Music.Service.Previous();
        RaiseMusicBar();
    });

    public ICommand OpenMusicPlayerCommand => Cmd("music.open", () => OpenInternal("music"));

    private void RaiseMusicBar() => Raise(nameof(MusicBarVisible), nameof(MusicNowTitle), nameof(MusicNowBadge),
        nameof(MusicNowSubtitle), nameof(MusicIsPlaying), nameof(MusicPlayGlyph), nameof(MusicIsStreaming), nameof(MusicVolume));

    // ------------------------------------------------------------------
    // Profile avatar (gradient presets, custom picture)
    // ------------------------------------------------------------------

    public static IReadOnlyList<string> AvatarStyles { get; } = new[] { "Aurora", "Nebula", "Sunset", "Ember", "Matrix", "Mono" };

    /// <summary>Accent swatches shown in the profile flyout (one tap to re-skin the browser).</summary>
    public static IReadOnlyList<string> AccentPresets { get; } = new[]
    {
        "#7C5CFF", "#22D3EE", "#3B82F6", "#A855F7", "#EC4899", "#22C55E", "#F59E0B"
    };

    public string AvatarStyle => Settings.AvatarStyle;

    public string ProfileInitials
    {
        get
        {
            var name = Settings.ProfileName?.Trim() ?? string.Empty;
            if (name.Length == 0) return "D";
            var parts = name.Split(' ', StringSplitOptions.RemoveEmptyEntries);
            return parts.Length >= 2
                ? string.Concat(parts[0][..1], parts[1][..1]).ToUpperInvariant()
                : name[..Math.Min(2, name.Length)].ToUpperInvariant();
        }
    }

    /// <summary>Gradient used by the built-in avatar and by the toolbar avatar chip.</summary>
    public Brush AvatarBrush => BuildAvatarBrush(Settings.AvatarStyle);

    public static Brush BuildAvatarBrush(string? style)
    {
        var (from, to) = (style ?? string.Empty).ToLowerInvariant() switch
        {
            "nebula" => ("#A855F7", "#EC4899"),
            "sunset" => ("#FB7185", "#F59E0B"),
            "ember" => ("#F97316", "#EF4444"),
            "matrix" => ("#22C55E", "#0EA5E9"),
            "mono" => ("#94A3B8", "#475569"),
            _ => ("#7C5CFF", "#22D3EE")
        };

        var brush = new LinearGradientBrush
        {
            StartPoint = new Point(0, 0),
            EndPoint = new Point(1, 1)
        };
        brush.GradientStops.Add(new GradientStop((Color)ColorConverter.ConvertFromString(from), 0));
        brush.GradientStops.Add(new GradientStop((Color)ColorConverter.ConvertFromString(to), 1));
        brush.Freeze();
        return brush;
    }

    public ICommand SetAvatarStyleCommand => Cmd("avatar.style", p =>
    {
        if (p is not string style) return;
        Settings.AvatarStyle = style;
        _settingsService.Save(Settings);
        Raise(nameof(AvatarStyle), nameof(AvatarBrush));
        StatusMessage = $"Аватар: {style}";
    });

    public ICommand ClearCustomAvatarCommand => Cmd("avatar.clear", () =>
    {
        Settings.AvatarPath = string.Empty;
        _settingsService.Save(Settings);
        Raise(nameof(AvatarImagePath), nameof(HasCustomAvatar));
        StatusMessage = "Свой аватар убран, используется градиент.";
    });

    // ------------------------------------------------------------------
    // DrAI personas
    // ------------------------------------------------------------------

    public static IReadOnlyList<AiPersonaInfo> AiPersonas { get; } = new[]
    {
        new AiPersonaInfo("assistant", "Помощник", "🧠", "Ответит на вопрос, объяснит и подскажет."),
        new AiPersonaInfo("brief", "Кратко", "📄", "Сжимает текст в 5–8 пунктов без воды."),
        new AiPersonaInfo("translator", "Переводчик", "🌐", "Переводит на русский и сохраняет форматирование."),
        new AiPersonaInfo("coder", "Кодер", "🧩", "Разбирает код по шагам и предлагает правки.")
    };

    public IReadOnlyList<AiPersonaInfo> Personas => AiPersonas;

    public string SelectedPersonaId => Settings.AiPersona;

    public AiPersonaInfo SelectedPersona =>
        AiPersonas.FirstOrDefault(p => p.Id == Settings.AiPersona) ?? AiPersonas[0];

    public string AiPersonaHint => SelectedPersona.Hint;

    public ICommand SelectPersonaCommand => Cmd("ai.persona", p =>
    {
        if (p is not string id) return;
        Settings.AiPersona = id;
        _settingsService.Save(Settings);
        Raise(nameof(SelectedPersonaId), nameof(SelectedPersona), nameof(AiPersonaHint));
        AiStatus = $"Режим: {SelectedPersona.Name}";
    });

    public bool AiIncludePage
    {
        get => Settings.AiIncludePage;
        set
        {
            if (Settings.AiIncludePage == value) return;
            Settings.AiIncludePage = value;
            _settingsService.Save(Settings);
            Raise(nameof(AiIncludePage), nameof(AiContextLabel));
        }
    }

    public string AiContextLabel => Settings.AiIncludePage
        ? "DrAI видит текст активной страницы как контекст"
        : "DrAI работает без доступа к странице";

    public ICommand CopyAiMessageCommand => Cmd("ai.copy", p =>
    {
        if (p is not ChatMessage message || string.IsNullOrWhiteSpace(message.Text)) return;
        try
        {
            Clipboard.SetText(message.Text);
            AiStatus = "Ответ скопирован.";
        }
        catch (Exception ex)
        {
            AiStatus = "Буфер обмена недоступен: " + ex.Message;
        }
    });

    // ------------------------------------------------------------------
    // Wiring (called from the constructor)
    // ------------------------------------------------------------------

    private void InitializeShell2()
    {
        Music.Service.PropertyChanged += (_, e) =>
        {
            if (e.PropertyName is nameof(MusicService.IsPlaying) or nameof(MusicService.Current)
                or nameof(MusicService.IsStreaming))
                RaiseMusicBar();
        };

        // Profile card statistics must never show stale numbers (they used to be computed once at startup).
        Tabs.CollectionChanged += (_, _) => OnPropertyChanged(nameof(ProfileStats));
        Bookmarks.Items.CollectionChanged += (_, _) => OnPropertyChanged(nameof(ProfileStats));
        AdBlock.CountersChanged += (_, _) => OnPropertyChanged(nameof(ProfileStats));
    }
}
