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

    /// <summary>True for the built-in internet radio stations (played from a URL).</summary>
    public bool IsStream { get; init; }

    public string FileName => IsStream ? FilePath : Path.GetFileName(FilePath);
    public string Extension => IsStream ? "LIVE" : Path.GetExtension(FilePath).TrimStart('.').ToUpperInvariant();
    public string Badge => IsStream ? "LIVE" : Extension;
    public string Subtitle => IsStream
        ? $"Прямой эфир · {Category}"
        : string.IsNullOrWhiteSpace(Artist) ? Category : $"{Artist} · {Category}";
    public string Initial => Title.Length > 0 ? Title[..1].ToUpperInvariant() : "♪";
}

/// <summary>A user playlist; the tracks are kept in playlist order.</summary>
public sealed class MusicPlaylist : ObservableObject
{
    private string _name = string.Empty;

    public MusicPlaylist(long id, string name)
    {
        Id = id;
        _name = name;
    }

    public long Id { get; }

    public string Name
    {
        get => _name;
        set
        {
            if (Set(ref _name, value)) Raise(nameof(Label));
        }
    }

    public ObservableCollection<MusicTrack> Tracks { get; } = new();

    private bool _isActive;

    /// <summary>True for the playlist currently opened in the DrMusic panel.</summary>
    public bool IsActive
    {
        get => _isActive;
        set => Set(ref _isActive, value);
    }

    public string CountText => Tracks.Count.ToString();
    public string Label => $"{Name}  ·  {Tracks.Count}";
    public bool IsEmpty => Tracks.Count == 0;
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
    public const string RadioCategory = "Радио · прямой эфир";

    /// <summary>
    /// Built-in radio stations the user can start with one click — no account, no key.
    /// They are ordinary public MP3/AAC streams, played by the same MediaPlayer as local
    /// files. Availability depends on the network and, for some of them, on the region.
    /// </summary>
    public static readonly (string Title, string Url, string Genre)[] StationCatalog =
    {
        ("Groove Salad", "https://ice1.somafm.com/groovesalad-128-mp3", "Chillout"),
        ("Drone Zone", "https://ice1.somafm.com/dronezone-128-mp3", "Ambient"),
        ("Lush", "https://ice1.somafm.com/lush-128-mp3", "Vocal chill"),
        ("Secret Agent", "https://ice1.somafm.com/secretagent-128-mp3", "Lounge"),
        ("DEF CON Radio", "https://ice1.somafm.com/defcon-128-mp3", "Electronic"),
        ("Nightride FM", "https://stream.nightride.fm/nightride.m4a", "Synthwave"),
        ("Radio Paradise", "https://stream.radioparadise.com/aac-128", "Eclectic"),
        ("Record Deep", "https://radiorecord.hostingradio.ru/deep96.aacp", "Deep house")
    };

    private static readonly string[] AudioExtensions =
    {
        ".mp3", ".wav", ".m4a", ".aac", ".flac", ".ogg", ".wma", ".aiff"
    };

    private readonly Logger _log;
    private readonly MediaPlayer _player = new();
    private readonly DispatcherTimer _timer;
    private bool _disposed;
    private bool _seeking;

    private readonly List<MusicTrack> _stations = new();
    private MusicTrack? _current;
    private bool _isPlaying;
    private double _positionSeconds;
    private double _durationSeconds;
    private double _volume = 0.8;
    private bool _shuffle;
    private bool _repeat;
    private string _status = "Библиотека не выбрана";

    private readonly Db? _db;

    /// <param name="db">Optional storage: when present, plays are remembered and favourites persist.</param>
    public MusicService(Logger log, Db? db = null)
    {
        _log = log;
        _db = db;
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

        SeedStations();
        RebuildCategories();
        ReloadFavorites();
        ReloadRecent();
        ReloadPlaylists();
    }

    // ------------------------------------------------------------------
    // Favourites and "recently played" (SQLite, optional)
    // ------------------------------------------------------------------

    public ObservableCollection<MusicTrack> Favorites { get; } = new();
    public ObservableCollection<MusicTrack> Recent { get; } = new();

    public bool HasFavorites => Favorites.Count > 0;
    public bool HasRecent => Recent.Count > 0;
    public string FavoritesSummary => Favorites.Count == 0
        ? "Избранного пока нет — нажмите ☆ у трека"
        : $"Избранное: {Favorites.Count}";

    private static MusicTrack TrackFromRow(string path, string title, string artist, string category, bool stream) =>
        new()
        {
            FilePath = path,
            Title = string.IsNullOrWhiteSpace(title) ? Path.GetFileName(path) : title,
            Artist = artist,
            Category = category,
            IsStream = stream
        };

    public void ReloadFavorites()
    {
        Favorites.Clear();
        if (_db is not null)
        {
            try
            {
                foreach (var row in _db.QueryMusicFavorites())
                    Favorites.Add(TrackFromRow(row.Path, row.Title, row.Artist, row.Category, row.IsStream));
            }
            catch (Exception ex)
            {
                _log.Error("music favourites read failed", ex);
            }
        }

        Raise(nameof(HasFavorites), nameof(FavoritesSummary));
    }

    public void ReloadRecent()
    {
        Recent.Clear();
        if (_db is not null)
        {
            try
            {
                foreach (var row in _db.QueryMusicRecent(12))
                    Recent.Add(TrackFromRow(row.Path, row.Title, row.Artist, "Недавно прослушано", row.IsStream));
            }
            catch (Exception ex)
            {
                _log.Error("music recent read failed", ex);
            }
        }

        Raise(nameof(HasRecent));
    }

    public bool IsFavorite(MusicTrack? track)
        => track is not null && Favorites.Any(f => string.Equals(f.FilePath, track.FilePath, StringComparison.OrdinalIgnoreCase));

    /// <summary>Adds or removes a track from favourites; returns the new state.</summary>
    public bool ToggleFavorite(MusicTrack track)
    {
        if (track is null) return false;

        var existing = Favorites.FirstOrDefault(f =>
            string.Equals(f.FilePath, track.FilePath, StringComparison.OrdinalIgnoreCase));

        if (existing is not null)
        {
            Favorites.Remove(existing);
            try { _db?.RemoveMusicFavorite(track.FilePath); }
            catch (Exception ex) { _log.Error("music favourite delete failed", ex); }
            Raise(nameof(HasFavorites), nameof(FavoritesSummary));
            return false;
        }

        Favorites.Insert(0, track);
        try
        {
            _db?.AddMusicFavorite(new MusicFavoriteRow(track.FilePath, track.Title, track.Artist, track.Category,
                track.IsStream));
        }
        catch (Exception ex)
        {
            _log.Error("music favourite save failed", ex);
        }

        Raise(nameof(HasFavorites), nameof(FavoritesSummary));
        return true;
    }

    public void ClearRecent()
    {
        Recent.Clear();
        try { _db?.ClearMusicRecent(); }
        catch (Exception ex) { _log.Error("music recent clear failed", ex); }
        Raise(nameof(HasRecent));
    }

    // ------------------------------------------------------------------
    // Playlists (persisted in SQLite)
    // ------------------------------------------------------------------

    public ObservableCollection<MusicPlaylist> Playlists { get; } = new();

    public bool HasPlaylists => Playlists.Count > 0;
    public string PlaylistsSummary => Playlists.Count == 0 ? "пусто" : $"{Playlists.Count} шт.";

    public void ReloadPlaylists()
    {
        Playlists.Clear();
        if (_db is not null)
        {
            try
            {
                foreach (var row in _db.QueryPlaylists())
                {
                    var playlist = new MusicPlaylist(row.Id, row.Name);
                    foreach (var track in _db.QueryPlaylistTracks(row.Id))
                        playlist.Tracks.Add(TrackFromRow(track.Path, track.Title, track.Artist, track.Category, track.IsStream));
                    Playlists.Add(playlist);
                }
            }
            catch (Exception ex)
            {
                _log.Error("playlists read failed", ex);
            }
        }

        Raise(nameof(HasPlaylists), nameof(PlaylistsSummary));
    }

    public MusicPlaylist? CreatePlaylist(string name)
    {
        if (_db is null || string.IsNullOrWhiteSpace(name)) return null;
        try
        {
            var id = _db.CreatePlaylist(name);
            ReloadPlaylists();
            return Playlists.FirstOrDefault(p => p.Id == id);
        }
        catch (Exception ex)
        {
            _log.Error("playlist create failed", ex);
            return null;
        }
    }

    public bool RenamePlaylist(MusicPlaylist playlist, string name)
    {
        if (_db is null || playlist is null || string.IsNullOrWhiteSpace(name)) return false;
        var clean = name.Trim();
        if (string.Equals(playlist.Name, clean, StringComparison.Ordinal)) return false;
        try
        {
            _db.RenamePlaylist(playlist.Id, clean);
            playlist.Name = clean;
            return true;
        }
        catch (Exception ex)
        {
            _log.Error("playlist rename failed", ex);
            return false;
        }
    }

    public void DeletePlaylist(MusicPlaylist playlist)
    {
        if (playlist is null) return;
        try { _db?.DeletePlaylist(playlist.Id); }
        catch (Exception ex) { _log.Error("playlist delete failed", ex); }
        Playlists.Remove(playlist);
        Raise(nameof(HasPlaylists), nameof(PlaylistsSummary));
    }

    /// <summary>Adds a track to a playlist; returns false when it was already there.</summary>
    public bool AddToPlaylist(MusicPlaylist playlist, MusicTrack track)
    {
        if (playlist is null || track is null) return false;
        if (playlist.Tracks.Any(t => string.Equals(t.FilePath, track.FilePath, StringComparison.OrdinalIgnoreCase)))
            return false;

        try
        {
            _db?.AddPlaylistTrack(playlist.Id, new MusicPlaylistTrackRow(0, playlist.Id, playlist.Tracks.Count,
                track.FilePath, track.Title, track.Artist, track.Category, track.IsStream));
        }
        catch (Exception ex)
        {
            _log.Error("playlist track add failed", ex);
            return false;
        }

        playlist.Tracks.Add(track);
        Raise(nameof(PlaylistsSummary));
        return true;
    }

    public bool RemoveFromPlaylist(MusicPlaylist playlist, MusicTrack track)
    {
        if (playlist is null || track is null) return false;
        var index = playlist.Tracks.ToList().FindIndex(t =>
            string.Equals(t.FilePath, track.FilePath, StringComparison.OrdinalIgnoreCase));
        if (index < 0) return false;

        try { _db?.RemovePlaylistTrack(playlist.Id, track.FilePath); }
        catch (Exception ex) { _log.Error("playlist track remove failed", ex); }

        playlist.Tracks.RemoveAt(index);
        Raise(nameof(PlaylistsSummary));
        return true;
    }

    /// <summary>Moves a track up (-1) or down (+1) inside its playlist.</summary>
    public bool MoveInPlaylist(MusicPlaylist playlist, MusicTrack track, int delta)
    {
        if (playlist is null || track is null) return false;
        var index = playlist.Tracks.ToList().FindIndex(t =>
            string.Equals(t.FilePath, track.FilePath, StringComparison.OrdinalIgnoreCase));
        if (index < 0) return false;

        try { _db?.MovePlaylistTrack(playlist.Id, track.FilePath, delta); }
        catch (Exception ex) { _log.Error("playlist track move failed", ex); }

        var moved = Db.MoveItem(playlist.Tracks, index, delta);
        return moved != index;
    }

    /// <summary>Queues a whole playlist and starts it from the first track.</summary>
    public void PlayPlaylist(MusicPlaylist playlist)
    {
        if (playlist is null || playlist.Tracks.Count == 0) return;
        SetQueue(playlist.Tracks);
        Play(playlist.Tracks[0]);
        Status = $"Плейлист «{playlist.Name}» · {playlist.Tracks.Count} треков";
    }

    /// <summary>The last track that was played (used by "продолжить с места").</summary>
    public MusicTrack? LastPlayed { get; private set; }

    private void RememberPlay(MusicTrack track)
    {
        LastPlayed = track;

        if (_db is not null)
        {
            try
            {
                _db.AddMusicRecent(new MusicRecentRow(track.FilePath, track.Title, track.Artist, track.IsStream));
            }
            catch (Exception ex)
            {
                _log.Error("music recent write failed", ex);
            }
        }

        var existing = Recent.FirstOrDefault(r =>
            string.Equals(r.FilePath, track.FilePath, StringComparison.OrdinalIgnoreCase));
        if (existing is not null) Recent.Remove(existing);
        Recent.Insert(0, track);
        while (Recent.Count > 12) Recent.RemoveAt(Recent.Count - 1);
        Raise(nameof(HasRecent));
    }

    public ObservableCollection<MusicTrack> Tracks { get; } = new();

    /// <summary>Built-in radio stations, exposed for the "Прямой эфир" strip in DrMusic.</summary>
    public IReadOnlyList<MusicTrack> Stations => _stations;
    public ObservableCollection<MusicCategory> Categories { get; } = new();
    public ObservableCollection<MusicTrack> Queue { get; } = new();

    public MusicTrack? Current
    {
        get => _current;
        private set
        {
            if (Set(ref _current, value))
            Raise(nameof(CurrentTitle), nameof(CurrentSubtitle), nameof(HasTrack), nameof(IsStreaming), nameof(NowBadge));
        }
    }

    public bool HasTrack => _current is not null;

    /// <summary>True while a radio stream (not a local file) is loaded.</summary>
    public bool IsStreaming => _current?.IsStream == true;
    public string CurrentTitle => _current?.Title ?? "Ничего не играет";
    public string CurrentSubtitle => _current?.Subtitle ?? "Выберите трек из библиотеки или включите радио";
    public string NowBadge => _current is null ? string.Empty : _current.IsStream ? "LIVE" : "LOCAL";
    public bool HasLibrary => Tracks.Count > 0;

    /// <summary>True when at least one local audio file is indexed (radio is always there).</summary>
    public bool HasLocalTracks => Tracks.Any(t => !t.IsStream);

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
        set => Set(ref _status, value);
    }

    // ------------------------------------------------------------------
    // Library
    // ------------------------------------------------------------------

    /// <summary>Adds the built-in radio stations to the index (no network access here).</summary>
    private void SeedStations()
    {
        if (_stations.Count > 0) return;

        foreach (var (title, url, genre) in StationCatalog)
        {
            var station = new MusicTrack
            {
                FilePath = url,
                Title = title,
                Artist = genre,
                Category = RadioCategory,
                IsStream = true
            };

            _stations.Add(station);
            Tracks.Add(station);
            Queue.Add(station);
        }
    }

    private void RebuildCategories()
    {
        Categories.Clear();
        Categories.Add(new MusicCategory(AllTracksCategory, Tracks.Count));

        foreach (var group in Tracks.Where(t => !t.IsStream).GroupBy(t => t.Category).OrderBy(g => g.Key))
            Categories.Add(new MusicCategory(group.Key, group.Count()));

        var radio = _stations.Count;
        if (radio > 0) Categories.Add(new MusicCategory(RadioCategory, radio));
    }

    /// <summary>Scans a folder (and its sub-folders) for audio files; radio stays available.</summary>
    public async Task LoadAsync(string? folder)
    {
        Tracks.Clear();
        Queue.Clear();
        Categories.Clear();
        _stations.Clear();
        SeedStations();

        if (string.IsNullOrWhiteSpace(folder) || !Directory.Exists(folder))
        {
            RebuildCategories();
            Status = "Папка не выбрана — доступно встроенное радио DrMusic";
            Raise(nameof(HasLibrary), nameof(HasLocalTracks));
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
            RebuildCategories();
            Raise(nameof(HasLibrary), nameof(HasLocalTracks));
            return;
        }

        var stations = _stations.ToList();
        Tracks.Clear();
        foreach (var track in files) Tracks.Add(track);
        foreach (var station in stations) Tracks.Add(station);

        Queue.Clear();
        foreach (var track in Tracks) Queue.Add(track);

        RebuildCategories();

        Status = files.Count == 0
            ? $"В папке {folder} не найдено аудиофайлов — играет радио DrMusic"
            : $"В библиотеке {files.Count} треков · {_stations.Count} радиостанций · {Math.Max(0, Categories.Count - 1)} категорий";

        Raise(nameof(HasLibrary), nameof(HasLocalTracks));
        _log.Info($"music library loaded: {files.Count} tracks from {folder}, {_stations.Count} radio stations");
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
            RememberPlay(track);
            _player.Open(new Uri(track.FilePath));
            _player.Play();
            IsPlaying = true;
            DurationSeconds = 0;
            PositionSeconds = 0;
            Status = track.IsStream ? $"Прямой эфир: {track.Title} · {track.Artist}" : $"Играет: {track.Title}";
            _log.Info($"music play: {track.FileName} (stream={track.IsStream})");
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
        if (IsStreaming) return; // live radio has no seekable position
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
