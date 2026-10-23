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

    private string _filter = string.Empty;
    private string _selectedCategory = MusicService.AllTracksCategory;

    public MusicViewModel(
        MusicService service,
        Logger log,
        AppSettings settings,
        Func<string?> pickFolder,
        Action<string> openFolder,
        Action persist)
    {
        Service = service;
        _log = log;
        _settings = settings;
        _pickFolder = pickFolder;
        _openFolder = openFolder;
        _persist = persist;

        _debounce = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(200) };
        _debounce.Tick += (_, _) =>
        {
            _debounce.Stop();
            RebuildVisible();
        };

        Service.PropertyChanged += (_, e) =>
        {
            if (e.PropertyName is nameof(MusicService.Current) or nameof(MusicService.IsPlaying))
                Raise(nameof(IsPlaying), nameof(PlayPauseGlyph), nameof(NowPlayingTitle), nameof(MiniPlayerText));
        };

        Folder = settings.MusicFolder;
        Service.Volume = settings.MusicVolume;
    }

    public MusicService Service { get; }

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
    public string FolderText => HasFolder ? Folder : "Папка не выбрана — выберите её, чтобы браузер прочитал ваши файлы";

    public string Filter
    {
        get => _filter;
        set
        {
            if (!Set(ref _filter, value ?? string.Empty)) return;
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
    public string MiniPlayerText => Service.HasTrack
        ? $"{Service.CurrentTitle}{(Service.IsPlaying ? " · играет" : " · пауза")}"
        : "Музыка не играет";

    public string LibrarySummary => Service.Tracks.Count == 0
        ? "Библиотека пуста"
        : $"{Service.Tracks.Count} треков · {Math.Max(0, Service.Categories.Count - 1)} категорий";

    public ICommand PlayCommand => new RelayCommand(p => { if (p is MusicTrack track) Service.Play(track); });
    public ICommand PlayCategoryCommand => new RelayCommand(p =>
    {
        if (p is not string category) return;
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
