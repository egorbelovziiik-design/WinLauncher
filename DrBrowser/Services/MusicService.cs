using System.Collections.ObjectModel;
using System.IO;
using System.Windows.Media;
using System.Windows.Threading;

namespace DrBrowser;

/// <summary>One audio file found in the local music library.</summary>
public sealed class MusicTrack : ObservableObject
{
    public string FilePath { get; init; } = string.Empty;
    public string Title { get; init; } = string.Empty;
    public string Artist { get; init; } = string.Empty;
    public string Category { get; init; } = MusicService.AllTracksCategory;

    public string FileName => Path.GetFileName(FilePath);
    public string Extension => Path.GetExtension(FilePath).TrimStart('.').ToUpperInvariant();
    public string Subtitle => string.IsNullOrWhiteSpace(Artist) ? Category : $"{Artist} · {Category}";
    public string Initial => Title.Length > 0 ? Title[..1].ToUpperInvariant() : "♪";
}

/// <summary>Category (a sub-folder of the library) with its track count.</summary>
public sealed record MusicCategory(string Name, int Count)
{
    public string Label => $"{Name}  ·  {Count}";
}

/// <summary>
/// Local music library + playback. Files are only ever read from the folders the user
/// picked; playback uses the WPF MediaPlayer, so nothing is uploaded anywhere.
/// </summary>
public sealed class MusicService : ObservableObject, IDisposable
{
    public const string AllTracksCategory = "Все треки";

    private static readonly string[] AudioExtensions =
    {
        ".mp3", ".wav", ".m4a", ".aac", ".flac", ".ogg", ".wma", ".aiff"
    };

    private readonly Logger _log;
    private readonly MediaPlayer _player = new();
    private readonly DispatcherTimer _timer;
    private bool _disposed;
    private bool _seeking;

    private MusicTrack? _current;
    private bool _isPlaying;
    private double _positionSeconds;
    private double _durationSeconds;
    private double _volume = 0.8;
    private bool _shuffle;
    private bool _repeat;
    private string _status = "Библиотека не выбрана";

    public MusicService(Logger log)
    {
        _log = log;
        _player.Volume = _volume;
        _player.MediaOpened += (_, _) =>
        {
            DurationSeconds = NaturalDurationSeconds();
            Status = $"Играет: {_current?.Title}";
            Raise(nameof(DurationText), nameof(Progress));
        };
        _player.MediaEnded += (_, _) => OnMediaEnded();
        _player.MediaFailed += (_, e) =>
        {
            Status = "Не удалось воспроизвести файл: " + e.ErrorException?.Message;
            IsPlaying = false;
            _log.Error($"music playback failed ({_current?.FileName}): {e.ErrorException?.Message}");
        };

        _timer = new DispatcherTimer(DispatcherPriority.Background) { Interval = TimeSpan.FromMilliseconds(250) };
        _timer.Tick += (_, _) => PollPosition();
        _timer.Start();
    }

    public ObservableCollection<MusicTrack> Tracks { get; } = new();
    public ObservableCollection<MusicCategory> Categories { get; } = new();
    public ObservableCollection<MusicTrack> Queue { get; } = new();

    public MusicTrack? Current
    {
        get => _current;
        private set
        {
            if (Set(ref _current, value)) Raise(nameof(CurrentTitle), nameof(CurrentSubtitle), nameof(HasTrack));
        }
    }

    public bool HasTrack => _current is not null;
    public string CurrentTitle => _current?.Title ?? "Ничего не играет";
    public string CurrentSubtitle => _current?.Subtitle ?? "Выберите трек из библиотеки";
    public bool HasLibrary => Tracks.Count > 0;

    public bool IsPlaying
    {
        get => _isPlaying;
        private set
        {
            if (Set(ref _isPlaying, value)) Raise(nameof(PlayPauseGlyph), nameof(PlayPauseText));
        }
    }

    public string PlayPauseGlyph => IsPlaying ? "⏸" : "▶";
    public string PlayPauseText => IsPlaying ? "Пауза" : "Слушать";

    public double PositionSeconds
    {
        get => _positionSeconds;
        private set
        {
            if (Set(ref _positionSeconds, value)) Raise(nameof(PositionText), nameof(Progress));
        }
    }

    public double DurationSeconds
    {
        get => _durationSeconds;
        private set
        {
            if (Set(ref _durationSeconds, value)) Raise(nameof(DurationText), nameof(Progress));
        }
    }

    public string PositionText => Format(PositionSeconds);
    public string DurationText => Format(DurationSeconds);

    /// <summary>0..1 used by the seek slider.</summary>
    public double Progress => DurationSeconds > 0.1 ? Math.Clamp(PositionSeconds / DurationSeconds, 0, 1) : 0;

    public double Volume
    {
        get => _volume;
        set
        {
            var clamped = Math.Clamp(value, 0, 1);
            if (!Set(ref _volume, clamped)) return;
            _player.Volume = clamped;
            Raise(nameof(VolumeText));
        }
    }

    public string VolumeText => $"{Volume * 100:0}%";

    public bool Shuffle
    {
        get => _shuffle;
        set => Set(ref _shuffle, value);
    }

    public bool Repeat
    {
        get => _repeat;
        set => Set(ref _repeat, value);
    }

    public string Status
    {
        get => _status;
        private set => Set(ref _status, value);
    }

    // ------------------------------------------------------------------
    // Library
    // ------------------------------------------------------------------

    /// <summary>Scans a folder (and its sub-folders) for audio files.</summary>
    public async Task LoadAsync(string? folder)
    {
        Tracks.Clear();
        Queue.Clear();
        Categories.Clear();

        if (string.IsNullOrWhiteSpace(folder) || !Directory.Exists(folder))
        {
            Status = "Выберите папку с музыкой — браузер прочитает её локально";
            Raise(nameof(HasLibrary));
            return;
        }

        List<MusicTrack> files;
        try
        {
            files = await Task.Run(() => ScanFolder(folder)).ConfigureAwait(true);
        }
        catch (Exception ex)
        {
            Status = "Не удалось прочитать папку: " + ex.Message;
            _log.Error("music scan failed", ex);
            Raise(nameof(HasLibrary));
            return;
        }

        foreach (var track in files) Tracks.Add(track);

        Categories.Add(new MusicCategory(AllTracksCategory, Tracks.Count));
        foreach (var group in Tracks.GroupBy(t => t.Category).OrderBy(g => g.Key))
            Categories.Add(new MusicCategory(group.Key, group.Count()));

        foreach (var track in Tracks) Queue.Add(track);

        Status = Tracks.Count == 0
            ? $"В папке {folder} не найдено аудиофайлов"
            : $"В библиотеке {Tracks.Count} треков · {Categories.Count - 1} категорий";

        Raise(nameof(HasLibrary));
        _log.Info($"music library loaded: {Tracks.Count} tracks from {folder}");
    }

    /// <summary>Pure file-system scan (also used by the self-test).</summary>
    public static List<MusicTrack> ScanFolder(string folder)
    {
        var result = new List<MusicTrack>();

        foreach (var path in Directory.EnumerateFiles(folder, "*.*", SearchOption.AllDirectories))
        {
            var ext = Path.GetExtension(path);
            if (!AudioExtensions.Contains(ext, StringComparer.OrdinalIgnoreCase)) continue;

            var relative = Path.GetRelativePath(folder, path);
            var parts = relative.Split(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar);
            var category = parts.Length > 1 ? parts[0] : AllTracksCategory;

            var name = Path.GetFileNameWithoutExtension(path);
            var artist = string.Empty;
            var title = name;

            // "Artist - Title" is the most common file naming convention.
            var dash = name.IndexOf(" - ", StringComparison.Ordinal);
            if (dash > 0)
            {
                artist = name[..dash].Trim();
                title = name[(dash + 3)..].Trim();
            }

            result.Add(new MusicTrack
            {
                FilePath = path,
                Title = string.IsNullOrWhiteSpace(title) ? name : title,
                Artist = artist,
                Category = category
            });
        }

        return result
            .OrderBy(t => t.Category, StringComparer.CurrentCultureIgnoreCase)
            .ThenBy(t => t.Title, StringComparer.CurrentCultureIgnoreCase)
            .ToList();
    }

    // ------------------------------------------------------------------
    // Playback
    // ------------------------------------------------------------------

    public void Play(MusicTrack track)
    {
        if (track is null) return;

        try
        {
            Current = track;
            _player.Open(new Uri(track.FilePath));
            _player.Play();
            IsPlaying = true;
            DurationSeconds = 0;
            PositionSeconds = 0;
            Status = $"Играет: {track.Title}";
            _log.Info($"music play: {track.FileName}");
        }
        catch (Exception ex)
        {
            Status = "Ошибка воспроизведения: " + ex.Message;
            _log.Error("music play failed", ex);
        }
    }

    public void TogglePlay()
    {
        if (Current is null)
        {
            if (Queue.Count > 0) Play(Queue[0]);
            return;
        }

        if (IsPlaying)
        {
            _player.Pause();
            IsPlaying = false;
            Status = "Пауза";
        }
        else
        {
            _player.Play();
            IsPlaying = true;
            Status = $"Играет: {Current.Title}";
        }
    }

    public void Stop()
    {
        _player.Stop();
        IsPlaying = false;
        PositionSeconds = 0;
        Status = "Остановлено";
    }

    public void Next()
    {
        if (Queue.Count == 0) return;
        var index = Current is null ? -1 : Queue.IndexOf(Current);
        index = Shuffle ? Random.Shared.Next(Queue.Count) : index + 1;
        if (index >= Queue.Count) index = 0;
        Play(Queue[index]);
    }

    public void Previous()
    {
        if (Queue.Count == 0) return;
        var index = Current is null ? 0 : Queue.IndexOf(Current) - 1;
        if (index < 0) index = Queue.Count - 1;
        Play(Queue[index]);
    }

    /// <summary>Seeks to a fraction (0..1) of the track.</summary>
    public void Seek(double fraction)
    {
        var duration = NaturalDurationSeconds();
        if (duration <= 0.1) return;

        _seeking = true;
        try
        {
            _player.Position = TimeSpan.FromSeconds(Math.Clamp(fraction, 0, 1) * duration);
            PositionSeconds = _player.Position.TotalSeconds;
        }
        catch (Exception ex)
        {
            _log.Warn($"music seek failed: {ex.Message}");
        }
        finally
        {
            _seeking = false;
        }
    }

    /// <summary>Replaces the play queue (used by the category view).</summary>
    public void SetQueue(IEnumerable<MusicTrack> tracks)
    {
        Queue.Clear();
        foreach (var track in tracks) Queue.Add(track);
    }

    public void PlayCategory(string category)
    {
        var tracks = category == AllTracksCategory
            ? Tracks.ToList()
            : Tracks.Where(t => t.Category == category).ToList();

        if (tracks.Count == 0) return;
        SetQueue(tracks);
        Play(tracks[0]);
    }

    private void OnMediaEnded()
    {
        if (Repeat && Current is not null)
        {
            Play(Current);
            return;
        }

        if (Queue.Count > 1)
        {
            Next();
            return;
        }

        IsPlaying = false;
        PositionSeconds = DurationSeconds;
        Status = "Трек завершён";
    }

    private void PollPosition()
    {
        if (_disposed || _seeking || !IsPlaying) return;

        try
        {
            PositionSeconds = _player.Position.TotalSeconds;
            if (DurationSeconds < 0.5) DurationSeconds = NaturalDurationSeconds();
        }
        catch
        {
            // player may be re-opened; ignore
        }
    }

    private double NaturalDurationSeconds()
    {
        try
        {
            return _player.NaturalDuration.HasTimeSpan ? _player.NaturalDuration.TimeSpan.TotalSeconds : 0;
        }
        catch
        {
            return 0;
        }
    }

    private static string Format(double seconds)
    {
        if (seconds < 0 || double.IsNaN(seconds)) seconds = 0;
        var time = TimeSpan.FromSeconds(seconds);
        return time.TotalHours >= 1 ? time.ToString(@"h\:mm\:ss") : time.ToString(@"m\:ss");
    }

    public void Dispose()
    {
        if (_disposed) return;
        _disposed = true;
        _timer.Stop();
        try
        {
            _player.Stop();
            _player.Close();
        }
        catch
        {
            // ignore
        }
    }
}
