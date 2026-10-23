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
    private readonly Action<string> _navigate;

    private string _filter = string.Empty;
    private string _selectedCategory = MusicService.AllTracksCategory;
    private string _catalogQuery = string.Empty;
    private string _catalogStatus = string.Empty;
    private bool _catalogBusy;

    public MusicViewModel(
        MusicService service,
        Logger log,
        AppSettings settings,
        Func<string?> pickFolder,
        Action<string> openFolder,
        Action persist,
        MusicCatalogService catalog,
        Action<string> navigate)
    {
        Service = service;
        _log = log;
        _settings = settings;
        _pickFolder = pickFolder;
        _openFolder = openFolder;
        _persist = persist;
        _catalog = catalog;
        _navigate = navigate;

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

    public ICommand PlayCommand => new RelayCommand(p =>
    {
        if (p is not MusicTrack track) return;
        Service.Play(track);
        Raise(nameof(IsPlaying), nameof(PlayPauseGlyph), nameof(NowPlayingTitle), nameof(NowPlayingSubtitle),
            nameof(NowPlayingBadge), nameof(IsStreaming), nameof(MiniPlayerText), nameof(IsCurrentFavorite),
            nameof(HasRecent));
    });

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
        SelectedCategory = category;
        Service.PlayCategory(category);
    });

    // --- favourites + recent (persisted in SQLite) ---

    public ObservableCollection<MusicTrack> Favorites => Service.Favorites;
    public ObservableCollection<MusicTrack> Recent => Service.Recent;
    public bool HasFavorites => Service.HasFavorites;
    public bool HasRecent => Service.HasRecent;
    public string FavoritesSummary => Service.FavoritesSummary;

    public bool IsCurrentFavorite => Service.IsFavorite(Service.Current);

    public ICommand ToggleFavoriteCommand => new RelayCommand(p =>
    {
        var track = p as MusicTrack ?? Service.Current;
        if (track is null) return;
        var added = Service.ToggleFavorite(track);
        Raise(nameof(HasFavorites), nameof(FavoritesSummary), nameof(IsCurrentFavorite));
        Service.Status = added ? $"☆ {track.Title} — в избранном" : $"Избранное: {track.Title} убран";
    });

    public ICommand FavoriteCurrentCommand => new RelayCommand(() =>
    {
        if (Service.Current is null) return;
        var added = Service.ToggleFavorite(Service.Current);
        Raise(nameof(HasFavorites), nameof(FavoritesSummary), nameof(IsCurrentFavorite));
        Service.Status = added ? "Добавлено в избранное" : "Убрано из избранного";
    });

    public ICommand ClearRecentCommand => new RelayCommand(() =>
    {
        Service.ClearRecent();
        Raise(nameof(HasRecent));
        Service.Status = "История прослушивания очищена";
    });

    public ICommand ResumeLastCommand => new RelayCommand(() =>
    {
        if (Service.LastPlayed is null)
        {
            Service.Status = "Пока нечего продолжать — включите любой трек.";
            return;
        }

        Service.Play(Service.LastPlayed);
        Raise(nameof(IsPlaying), nameof(PlayPauseGlyph), nameof(NowPlayingTitle), nameof(NowPlayingSubtitle),
            nameof(NowPlayingBadge), nameof(IsStreaming), nameof(MiniPlayerText), nameof(IsCurrentFavorite));
    });

    // --- playlists (persisted in SQLite) ---

    private string _newPlaylistName = string.Empty;
    private MusicPlaylist? _selectedPlaylist;

    public ObservableCollection<MusicPlaylist> Playlists => Service.Playlists;
    public bool HasPlaylists => Service.HasPlaylists;
    public string PlaylistsSummary => Service.PlaylistsSummary;

    public string NewPlaylistName
    {
        get => _newPlaylistName;
        set => Set(ref _newPlaylistName, value ?? string.Empty);
    }

    public MusicPlaylist? SelectedPlaylist
    {
        get => _selectedPlaylist;
        set
        {
            if (!Set(ref _selectedPlaylist, value)) return;
            foreach (var playlist in Playlists) playlist.IsActive = ReferenceEquals(playlist, value);
            Raise(nameof(HasSelectedPlaylist), nameof(SelectedPlaylistTitle), nameof(SelectedPlaylistHint),
                nameof(CanPlaySelectedPlaylist));
        }
    }

    public bool HasSelectedPlaylist => SelectedPlaylist is not null;
    public bool CanPlaySelectedPlaylist => SelectedPlaylist is { Tracks.Count: > 0 };
    public string SelectedPlaylistTitle => SelectedPlaylist?.Name ?? "Плейлист не выбран";
    public string SelectedPlaylistHint => SelectedPlaylist is null
        ? "Выберите плейлист слева, чтобы увидеть его треки."
        : SelectedPlaylist.Tracks.Count == 0
            ? "Плейлист пуст — добавьте трек кнопкой ★ или правым кликом по строке."
            : $"{SelectedPlaylist.Tracks.Count} треков · порядок сохраняется";

    public ICommand SelectPlaylistCommand => new RelayCommand(p =>
    {
        if (p is not MusicPlaylist playlist) return;
        SelectedPlaylist = playlist;
        Service.Status = $"Плейлист «{playlist.Name}» · {playlist.Tracks.Count} треков";
    });

    public ICommand CreatePlaylistCommand => new RelayCommand(() =>
    {
        var name = NewPlaylistName.Trim();
        if (name.Length == 0)
        {
            Service.Status = "Введите имя плейлиста";
            return;
        }

        var existing = Playlists.FirstOrDefault(p =>
            string.Equals(p.Name, name, StringComparison.OrdinalIgnoreCase));
        if (existing is not null)
        {
            SelectedPlaylist = existing;
            Service.Status = $"Плейлист «{existing.Name}» уже есть — открыт";
            return;
        }

        var created = Service.CreatePlaylist(name);
        if (created is null)
        {
            Service.Status = "Не удалось создать плейлист";
            return;
        }

        NewPlaylistName = string.Empty;
        SelectedPlaylist = created;
        Raise(nameof(HasPlaylists), nameof(PlaylistsSummary));
        Service.Status = $"Плейлист «{created.Name}» создан";
    });

    public ICommand RenamePlaylistCommand => new RelayCommand(() =>
    {
        if (SelectedPlaylist is null || NewPlaylistName.Trim().Length == 0)
        {
            Service.Status = "Выберите плейлист и введите новое имя";
            return;
        }

        var ok = Service.RenamePlaylist(SelectedPlaylist, NewPlaylistName);
        Service.Status = ok ? $"Плейлист переименован в «{SelectedPlaylist.Name}»" : "Имя не изменилось";
        NewPlaylistName = string.Empty;
        Raise(nameof(SelectedPlaylistTitle), nameof(SelectedPlaylistHint));
    });

    public ICommand DeletePlaylistCommand => new RelayCommand(() =>
    {
        if (SelectedPlaylist is null) return;
        var name = SelectedPlaylist.Name;
        Service.DeletePlaylist(SelectedPlaylist);
        SelectedPlaylist = Playlists.FirstOrDefault();
        Raise(nameof(HasPlaylists), nameof(PlaylistsSummary), nameof(SelectedPlaylistTitle),
            nameof(SelectedPlaylistHint), nameof(CanPlaySelectedPlaylist));
        Service.Status = $"Плейлист «{name}» удалён";
    });

    /// <summary>Adds the currently playing track to the selected playlist.</summary>
    public ICommand AddCurrentToPlaylistCommand => new RelayCommand(() =>
    {
        if (SelectedPlaylist is null)
        {
            Service.Status = "Сначала выберите плейлист слева";
            return;
        }

        if (Service.Current is null)
        {
            Service.Status = "Ничего не играет — включите трек";
            return;
        }

        var added = Service.AddToPlaylist(SelectedPlaylist, Service.Current);
        RaisePlaylistState();
        Service.Status = added
            ? $"«{Service.Current.Title}» добавлен в «{SelectedPlaylist.Name}»"
            : "Этот трек уже есть в плейлисте";
    });

    /// <summary>Adds an arbitrary track (from favourites, history or the library) to the selected playlist.</summary>
    public ICommand AddToPlaylistCommand => new RelayCommand(p =>
    {
        if (p is not MusicTrack track) return;
        if (SelectedPlaylist is null)
        {
            Service.Status = $"Сначала выберите плейлист — «{track.Title}» не добавлен";
            return;
        }

        var added = Service.AddToPlaylist(SelectedPlaylist, track);
        RaisePlaylistState();
        Service.Status = added
            ? $"«{track.Title}» добавлен в «{SelectedPlaylist.Name}»"
            : "Этот трек уже есть в плейлисте";
    });

    public ICommand RemoveFromPlaylistCommand => new RelayCommand(p =>
    {
        if (p is not MusicTrack track || SelectedPlaylist is null) return;
        Service.RemoveFromPlaylist(SelectedPlaylist, track);
        RaisePlaylistState();
        Service.Status = $"«{track.Title}» убран из плейлиста";
    });

    public ICommand MovePlaylistTrackUpCommand => new RelayCommand(p =>
    {
        if (p is not MusicTrack track || SelectedPlaylist is null) return;
        var moved = Service.MoveInPlaylist(SelectedPlaylist, track, -1);
        RaisePlaylistState();
        Service.Status = moved ? "Трек поднят выше" : "Трек уже первый";
    });

    public ICommand MovePlaylistTrackDownCommand => new RelayCommand(p =>
    {
        if (p is not MusicTrack track || SelectedPlaylist is null) return;
        var moved = Service.MoveInPlaylist(SelectedPlaylist, track, 1);
        RaisePlaylistState();
        Service.Status = moved ? "Трек опущен ниже" : "Трек уже последний";
    });

    public ICommand PlayPlaylistCommand => new RelayCommand(() =>
    {
        if (SelectedPlaylist is null) return;
        Service.PlayPlaylist(SelectedPlaylist);
        Raise(nameof(IsPlaying), nameof(PlayPauseGlyph), nameof(NowPlayingTitle), nameof(NowPlayingSubtitle),
            nameof(NowPlayingBadge), nameof(IsStreaming), nameof(MiniPlayerText), nameof(IsCurrentFavorite));
    });

    private void RaisePlaylistState()
    {
        Raise(nameof(HasPlaylists), nameof(PlaylistsSummary), nameof(SelectedPlaylistHint),
            nameof(CanPlaySelectedPlaylist), nameof(HasSelectedPlaylist));
    }

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
        RebuildVisible();
        Raise(nameof(LibrarySummary));
    });

    /// <summary>Re-applies the saved folder + volume when the page opens.</summary>
    public async Task EnsureLoadedAsync()
    {
        Service.Volume = _settings.MusicVolume;
        if (Service.Tracks.Count > 0)
        {
            RebuildVisible();
            return;
        }

        await Service.LoadAsync(_settings.MusicFolder).ConfigureAwait(true);
        RebuildVisible();
        Raise(nameof(LibrarySummary), nameof(Folder), nameof(HasFolder), nameof(FolderText));
    }

    private void RebuildVisible()
    {
        var query = Filter.Trim();
        var tracks = SelectedCategory == MusicService.AllTracksCategory
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
