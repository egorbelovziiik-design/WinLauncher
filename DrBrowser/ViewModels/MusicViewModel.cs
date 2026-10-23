using System.Collections.ObjectModel;
using System.Windows.Input;
using System.Windows.Threading;

namespace DrBrowser;

/// <summary>State + commands behind drbrowser://music.</summary>
public sealed class MusicViewModel : ObservableObject
{
    private readonly Logger _log;
    private readonly AppSettings _settings;
    private readonly Func<string?> _pickFolder;
    private readonly Action<string> _openFolder;
    private readonly Action _persist;
    private readonly DispatcherTimer _debounce;

    private readonly MusicCatalogService _catalog;
    private readonly MusicPlaylistService _playlists;
    private readonly Action<string> _navigate;
    private readonly Func<string, string, Task<bool>> _confirm;
    private readonly HashSet<string> _selectedPlaylistPaths = new(StringComparer.OrdinalIgnoreCase);

    private string _filter = string.Empty;
    private string _selectedCategory = MusicService.AllTracksCategory;
    private string _catalogQuery = string.Empty;
    private string _catalogStatus = string.Empty;
    private bool _catalogBusy;
    private string _playlistName = string.Empty;
    private string _playlistStatus = "Плейлисты хранятся локально в SQLite.";
    private MusicPlaylistInfo? _selectedPlaylist;

    public MusicViewModel(
        MusicService service,
        Logger log,
        AppSettings settings,
        Func<string?> pickFolder,
        Action<string> openFolder,
        Action persist,
        MusicCatalogService catalog,
        MusicPlaylistService playlists,
        Action<string> navigate,
        Func<string, string, Task<bool>> confirm)
    {
        Service = service;
        _log = log;
        _settings = settings;
        _pickFolder = pickFolder;
        _openFolder = openFolder;
        _persist = persist;
        _catalog = catalog;
        _playlists = playlists;
        _navigate = navigate;
        _confirm = confirm;

        _debounce = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(200) };
        _debounce.Tick += (_, _) =>
        {
            _debounce.Stop();
            RebuildVisible();
        };

        Service.PropertyChanged += (_, e) =>
        {
            if (e.PropertyName is nameof(MusicService.Current) or nameof(MusicService.IsPlaying))
                Raise(nameof(IsPlaying), nameof(PlayPauseGlyph), nameof(NowPlayingTitle), nameof(MiniPlayerText),
                    nameof(IsStreaming), nameof(NowPlayingBadge), nameof(NowPlayingSubtitle));
        };

        Folder = settings.MusicFolder;
        Service.Volume = settings.MusicVolume;
    }

    public MusicService Service { get; }

    // ------------------------------------------------------------------
    // Online catalog search (iTunes / Deezer previews)
    // ------------------------------------------------------------------

    public ObservableCollection<CatalogTrack> CatalogResults { get; } = new();

    public IReadOnlyList<string> CatalogSources => MusicCatalogService.Sources;

    public string CatalogQuery
    {
        get => _catalogQuery;
        set => Set(ref _catalogQuery, value ?? string.Empty);
    }

    public string CatalogSource
    {
        get => _settings.MusicSource;
        set
        {
            if (string.Equals(_settings.MusicSource, value, StringComparison.Ordinal)) return;
            _settings.MusicSource = value;
            _persist();
            Raise(nameof(CatalogSource), nameof(CatalogSourceText));
        }
    }

    public string CatalogSourceText => $"Источник: {CatalogSource} · превью 30 секунд";

    public string CatalogStatus
    {
        get => _catalogStatus;
        private set => Set(ref _catalogStatus, value);
    }

    public bool CatalogBusy
    {
        get => _catalogBusy;
        private set
        {
            if (Set(ref _catalogBusy, value)) Raise(nameof(CatalogSearchText));
        }
    }

    public string CatalogSearchText => CatalogBusy ? "Ищу…" : "Найти в каталоге";

    public bool HasCatalogResults => CatalogResults.Count > 0;

    public string CatalogHint =>
        "Публичный каталог отдаёт только 30-секундный фрагмент — полный трек открывается на сайте каталога. " +
        "Яндекс Музыка не имеет открытого API, поэтому для неё есть кнопка перехода на её поиск (без обхода защиты).";

    public ICommand SearchCatalogCommand => new RelayCommand(async () =>
    {
        var term = CatalogQuery.Trim();
        if (term.Length == 0)
        {
            CatalogStatus = "Введите название трека или исполнителя.";
            return;
        }

        CatalogBusy = true;
        CatalogStatus = "Запрашиваю каталог…";
        try
        {
            var outcome = await _catalog.SearchAsync(term, CatalogSource).ConfigureAwait(true);
            CatalogResults.Clear();
            foreach (var track in outcome.Tracks) CatalogResults.Add(track);
            CatalogStatus = outcome.Note;
        }
        catch (Exception ex)
        {
            _log.Error("catalog search failed", ex);
            CatalogStatus = "Ошибка поиска: " + ex.Message;
        }
        finally
        {
            CatalogBusy = false;
            Raise(nameof(HasCatalogResults));
        }
    });

    public ICommand PlayCatalogCommand => new RelayCommand(p =>
    {
        if (p is not CatalogTrack track) return;

        if (!track.HasPreview)
        {
            if (!string.IsNullOrWhiteSpace(track.PageUrl)) _navigate(track.PageUrl);
            return;
        }

        Service.Play(track.ToMusicTrack());
        CatalogStatus = $"Играет превью: {track.Title} — {track.Artist}";
        Raise(nameof(IsPlaying), nameof(NowPlayingTitle), nameof(PlayPauseGlyph));
    });

    public ICommand OpenCatalogPageCommand => new RelayCommand(p =>
    {
        if (p is CatalogTrack track && !string.IsNullOrWhiteSpace(track.PageUrl)) _navigate(track.PageUrl);
    });

    public ICommand OpenYandexMusicCommand => new RelayCommand(() =>
    {
        var term = CatalogQuery.Trim();
        _navigate(MusicCatalogService.YandexMusicUrl(term));
    });

    /// <summary>Tracks of the selected category after the text filter.</summary>
    public ObservableCollection<MusicTrack> Visible { get; } = new();

    public ObservableCollection<MusicCategory> Categories => Service.Categories;
    public ObservableCollection<MusicTrack> Queue => Service.Queue;
    public ObservableCollection<MusicPlaylistInfo> Playlists { get; } = new();

    public string PlaylistName { get => _playlistName; set => Set(ref _playlistName, value ?? string.Empty); }
    public string PlaylistStatus { get => _playlistStatus; private set => Set(ref _playlistStatus, value); }
    public MusicPlaylistInfo? SelectedPlaylist
    {
        get => _selectedPlaylist;
        private set
        {
            if (Set(ref _selectedPlaylist, value))
                Raise(nameof(HasSelectedPlaylist), nameof(CanDeleteSelectedPlaylist), nameof(SelectedPlaylistTitle), nameof(CurrentCollectionTitle));
        }
    }
    public bool HasSelectedPlaylist => SelectedPlaylist is not null;
    public bool CanDeleteSelectedPlaylist => SelectedPlaylist is { Id: > 0 };
    public string SelectedPlaylistTitle => SelectedPlaylist?.Name ?? "Категория";
    public string CurrentCollectionTitle => SelectedPlaylist?.Name ?? SelectedCategory;

    public ICommand CreatePlaylistCommand => new RelayCommand(async () =>
    {
        try
        {
            var id = await _playlists.CreateAsync(PlaylistName).ConfigureAwait(true);
            var name = PlaylistName.Trim();
            PlaylistName = string.Empty;
            _selectedPlaylistPaths.Clear();
            await ReloadPlaylistsAsync(id).ConfigureAwait(true);
            PlaylistStatus = $"Создан плейлист «{name}».";
        }
        catch (Exception ex) { PlaylistStatus = ex.Message; }
    });

    public ICommand SelectPlaylistCommand => new RelayCommand(async parameter =>
    {
        if (parameter is not MusicPlaylistInfo playlist) return;
        SelectedPlaylist = playlist;
        _selectedPlaylistPaths.Clear();
        var paths = playlist.Id == 0
            ? await _playlists.LoadFavoritePathsAsync().ConfigureAwait(true)
            : await _playlists.LoadPathsAsync(playlist.Id).ConfigureAwait(true);
        foreach (var path in paths) _selectedPlaylistPaths.Add(path);
        RebuildVisible();
        PlaylistStatus = $"«{playlist.Name}»: {Visible.Count} доступных треков.";
    });

    public ICommand DeletePlaylistCommand => new RelayCommand(async () =>
    {
        var playlist = SelectedPlaylist;
        if (playlist is null || playlist.Id <= 0) return;
        if (!await _confirm("Удалить плейлист?", $"Плейлист «{playlist.Name}» будет удалён. Аудиофайлы останутся на диске.").ConfigureAwait(true)) return;
        await _playlists.DeleteAsync(playlist.Id).ConfigureAwait(true);
        SelectedPlaylist = null;
        _selectedPlaylistPaths.Clear();
        await ReloadPlaylistsAsync().ConfigureAwait(true);
        RebuildVisible();
        PlaylistStatus = "Плейлист удалён; файлы не изменялись.";
    });

    public ICommand AddToPlaylistCommand => new RelayCommand(async parameter =>
    {
        if (parameter is not MusicTrack track) return;
        if (SelectedPlaylist is null) { PlaylistStatus = "Сначала выберите или создайте плейлист."; return; }
        if (SelectedPlaylist.Id == 0)
        {
            if (!track.IsFavorite) track.IsFavorite = await _playlists.ToggleFavoriteAsync(track).ConfigureAwait(true);
            _selectedPlaylistPaths.Add(track.FilePath);
            await ReloadPlaylistsAsync(0).ConfigureAwait(true);
            PlaylistStatus = "Добавлено в Избранное.";
            return;
        }
        await _playlists.AddTrackAsync(SelectedPlaylist.Id, track).ConfigureAwait(true);
        _selectedPlaylistPaths.Add(track.FilePath);
        await ReloadPlaylistsAsync(SelectedPlaylist.Id).ConfigureAwait(true);
        PlaylistStatus = $"«{track.Title}» добавлен в плейлист.";
    });

    public ICommand ToggleFavoriteCommand => new RelayCommand(async parameter =>
    {
        if (parameter is not MusicTrack track) return;
        track.IsFavorite = await _playlists.ToggleFavoriteAsync(track).ConfigureAwait(true);
        await ReloadPlaylistsAsync(SelectedPlaylist?.Id).ConfigureAwait(true);
        if (SelectedPlaylist?.Id == 0)
        {
            if (track.IsFavorite) _selectedPlaylistPaths.Add(track.FilePath); else _selectedPlaylistPaths.Remove(track.FilePath);
            RebuildVisible();
        }
        PlaylistStatus = track.IsFavorite ? "Добавлено в Избранное." : "Удалено из Избранного.";
    });

    public ICommand RemoveFromPlaylistCommand => new RelayCommand(async parameter =>
    {
        if (parameter is not MusicTrack track || SelectedPlaylist is null) return;
        if (SelectedPlaylist.Id == 0)
        {
            if (track.IsFavorite) track.IsFavorite = await _playlists.ToggleFavoriteAsync(track).ConfigureAwait(true);
        }
        else await _playlists.RemoveTrackAsync(SelectedPlaylist.Id, track).ConfigureAwait(true);
        _selectedPlaylistPaths.Remove(track.FilePath);
        await ReloadPlaylistsAsync(SelectedPlaylist.Id).ConfigureAwait(true);
        RebuildVisible();
        PlaylistStatus = "Трек убран из текущего списка; файл не удалён.";
    });

    public string Folder
    {
        get => _settings.MusicFolder;
        private set
        {
            _settings.MusicFolder = value ?? string.Empty;
            Raise(nameof(Folder), nameof(FolderText), nameof(HasFolder));
        }
    }

    public bool HasFolder => !string.IsNullOrWhiteSpace(Folder);

    public bool HasFilter => !string.IsNullOrEmpty(_filter);
    public string FolderText => HasFolder ? Folder : "Папка не выбрана — выберите её, чтобы браузер прочитал ваши файлы";

    public string Filter
    {
        get => _filter;
        set
        {
            if (!Set(ref _filter, value ?? string.Empty)) return;
            Raise(nameof(HasFilter));
            _debounce.Stop();
            _debounce.Start();
        }
    }

    public string SelectedCategory
    {
        get => _selectedCategory;
        set
        {
            if (!Set(ref _selectedCategory, string.IsNullOrWhiteSpace(value) ? MusicService.AllTracksCategory : value)) return;
            Raise(nameof(CurrentCollectionTitle));
            RebuildVisible();
        }
    }

    public double Volume
    {
        get => Service.Volume;
        set
        {
            if (Math.Abs(Service.Volume - value) < 0.001) return;
            Service.Volume = value;
            _settings.MusicVolume = value;
            _persist();
            Raise(nameof(Volume));
        }
    }

    public bool Shuffle
    {
        get => Service.Shuffle;
        set
        {
            if (Service.Shuffle == value) return;
            Service.Shuffle = value;
            Raise(nameof(Shuffle));
        }
    }

    public bool Repeat
    {
        get => Service.Repeat;
        set
        {
            if (Service.Repeat == value) return;
            Service.Repeat = value;
            Raise(nameof(Repeat));
        }
    }

    public bool IsPlaying => Service.IsPlaying;
    public string PlayPauseGlyph => Service.PlayPauseGlyph;
    public string NowPlayingTitle => Service.CurrentTitle;
    public bool IsStreaming => Service.IsStreaming;
    public string NowPlayingBadge => Service.NowBadge;
    public string NowPlayingSubtitle => Service.CurrentSubtitle;

    /// <summary>Radio stations shown as a "прямой эфир" strip at the top of DrMusic.</summary>
    public IReadOnlyList<MusicTrack> Stations => Service.Stations;
    public bool HasStations => Service.Stations.Count > 0;
    public string RadioSummary => $"{Service.Stations.Count} станций · интернет-радио без регистрации";
    public string MiniPlayerText => Service.HasTrack
        ? $"{Service.CurrentTitle}{(Service.IsPlaying ? " · играет" : " · пауза")}"
        : "Музыка не играет";

    public string LibrarySummary => Service.Tracks.Count == 0
        ? "Библиотека пуста"
        : $"{Service.Tracks.Count} треков · {Math.Max(0, Service.Categories.Count - 1)} категорий";

    public ICommand PlayCommand => new RelayCommand(p => { if (p is MusicTrack track) Service.Play(track); });

    /// <summary>Starts a built-in radio station (one click, no key required).</summary>
    public ICommand PlayStationCommand => new RelayCommand(p =>
    {
        if (p is not MusicTrack station) return;
        Service.Play(station);
        Raise(nameof(IsPlaying), nameof(PlayPauseGlyph), nameof(NowPlayingTitle), nameof(NowPlayingSubtitle),
            nameof(NowPlayingBadge), nameof(IsStreaming), nameof(MiniPlayerText));
    });
    public ICommand PlayCategoryCommand => new RelayCommand(p =>
    {
        if (p is not string category) return;
        SelectedPlaylist = null;
        _selectedPlaylistPaths.Clear();
        SelectedCategory = category;
        Service.PlayCategory(category);
    });

    public ICommand TogglePlayCommand => new RelayCommand(Service.TogglePlay);
    public ICommand NextCommand => new RelayCommand(Service.Next);
    public ICommand PreviousCommand => new RelayCommand(Service.Previous);
    public ICommand StopCommand => new RelayCommand(Service.Stop);

    public ICommand SeekCommand => new RelayCommand(p =>
    {
        if (p is double fraction) Service.Seek(fraction);
    });

    public ICommand SetVolumeCommand => new RelayCommand(p =>
    {
        if (p is not double volume) return;
        Service.Volume = volume;
        _settings.MusicVolume = volume;
        _persist();
    });

    public ICommand ToggleShuffleCommand => new RelayCommand(() => Service.Shuffle = !Service.Shuffle);
    public ICommand ToggleRepeatCommand => new RelayCommand(() => Service.Repeat = !Service.Repeat);

    public ICommand ChooseFolderCommand => new RelayCommand(async () =>
    {
        var folder = _pickFolder();
        if (string.IsNullOrWhiteSpace(folder)) return;

        Folder = folder;
        _settings.MusicFolder = folder;
        _persist();
        await Service.LoadAsync(folder).ConfigureAwait(true);
        await ApplyFavoriteFlagsAsync().ConfigureAwait(true);
        await ReloadPlaylistsAsync(SelectedPlaylist?.Id).ConfigureAwait(true);
        RebuildVisible();
        Raise(nameof(Service), nameof(LibrarySummary), nameof(HasFolder), nameof(FolderText));
        _log.Info($"music folder set: {folder}");
    });

    public ICommand OpenFolderCommand => new RelayCommand(() =>
    {
        if (HasFolder) _openFolder(Folder);
    });

    public ICommand RefreshCommand => new RelayCommand(async () =>
    {
        await Service.LoadAsync(Folder).ConfigureAwait(true);
        await ApplyFavoriteFlagsAsync().ConfigureAwait(true);
        await ReloadPlaylistsAsync(SelectedPlaylist?.Id).ConfigureAwait(true);
        RebuildVisible();
        Raise(nameof(LibrarySummary));
    });

    /// <summary>Re-applies the saved folder + volume when the page opens.</summary>
    public async Task EnsureLoadedAsync()
    {
        Service.Volume = _settings.MusicVolume;
        if (Service.Tracks.Count == 0 || (!Service.HasLocalTracks && !string.IsNullOrWhiteSpace(_settings.MusicFolder)))
            await Service.LoadAsync(_settings.MusicFolder).ConfigureAwait(true);
        await ApplyFavoriteFlagsAsync().ConfigureAwait(true);
        await ReloadPlaylistsAsync(SelectedPlaylist?.Id).ConfigureAwait(true);
        RebuildVisible();
        Raise(nameof(LibrarySummary), nameof(Folder), nameof(HasFolder), nameof(FolderText));
    }

    private async Task ApplyFavoriteFlagsAsync()
    {
        var favorites = await _playlists.LoadFavoritePathsAsync().ConfigureAwait(true);
        foreach (var track in Service.Tracks) track.IsFavorite = favorites.Contains(track.FilePath);
    }

    private async Task ReloadPlaylistsAsync(long? keepSelectedId = null)
    {
        var favorites = await _playlists.LoadFavoritePathsAsync().ConfigureAwait(true);
        var loaded = await _playlists.LoadAsync().ConfigureAwait(true);
        Playlists.Clear();
        Playlists.Add(new MusicPlaylistInfo(0, "Избранное", favorites.Count));
        foreach (var playlist in loaded) Playlists.Add(playlist);
        if (keepSelectedId is not null)
            SelectedPlaylist = Playlists.FirstOrDefault(item => item.Id == keepSelectedId.Value);
        Raise(nameof(Playlists), nameof(HasSelectedPlaylist), nameof(CanDeleteSelectedPlaylist), nameof(SelectedPlaylistTitle));
    }

    private void RebuildVisible()
    {
        var query = Filter.Trim();
        var tracks = SelectedPlaylist is not null
            ? Service.Tracks.Where(track => _selectedPlaylistPaths.Contains(track.FilePath))
            : SelectedCategory == MusicService.AllTracksCategory
                ? Service.Tracks.AsEnumerable()
                : Service.Tracks.Where(t => t.Category == SelectedCategory);

        if (query.Length > 0)
        {
            tracks = tracks.Where(t =>
                t.Title.Contains(query, StringComparison.CurrentCultureIgnoreCase)
                || t.Artist.Contains(query, StringComparison.CurrentCultureIgnoreCase)
                || t.Category.Contains(query, StringComparison.CurrentCultureIgnoreCase));
        }

        Visible.Clear();
        foreach (var track in tracks) Visible.Add(track);
        Service.SetQueue(Visible);
        Raise(nameof(VisibleCount), nameof(LibrarySummary));
    }

    public int VisibleCount => Visible.Count;
}
