using System.Collections.ObjectModel;

namespace DrBrowser;

/// <summary>Tab collection: order, pinning, activation, recently closed stack.</summary>
public sealed class TabManager : ObservableObject
{
    private readonly List<BrowserTab> _closed = new();
    private BrowserTab? _activeTab;
    private int _newTabCounter;

    public ObservableCollection<BrowserTab> Tabs { get; } = new();

    public BrowserTab? ActiveTab
    {
        get => _activeTab;
        set
        {
            if (ReferenceEquals(_activeTab, value)) return;
            if (_activeTab is not null) _activeTab.IsActive = false;
            _activeTab = value;
            if (value is not null) value.IsActive = true;
            if (value is not null) value.LastActivatedAt = DateTime.Now;
            OnPropertyChanged();
        }
    }

    public int Count => Tabs.Count;
    public IReadOnlyList<BrowserTab> RecentlyClosed => _closed;

    public BrowserTab Create(string url, TabKind kind = TabKind.Web, bool activate = true, string? title = null)
    {
        var tab = new BrowserTab { Url = url, Kind = kind, PendingUrl = kind == TabKind.Web ? url : null };
        if (!string.IsNullOrWhiteSpace(title)) tab.Title = title!;

        var pinned = Tabs.Count(t => t.IsPinned);
        Tabs.Insert(pinned, tab);
        if (activate) ActiveTab = tab;

        _newTabCounter++;
        return tab;
    }

    public BrowserTab CreateNewTabPage(bool activate = true)
        => Create("drbrowser://newtab", TabKind.NewTab, activate, "Новая вкладка");

    public void Close(BrowserTab tab, bool remember = true)
    {
        var index = Tabs.IndexOf(tab);
        if (index < 0) return;

        if (remember && !UrlParser.IsInternal(tab.Url)) _closed.Add(tab);
        if (_closed.Count > 20) _closed.RemoveAt(0);

        Tabs.Remove(tab);

        if (ReferenceEquals(ActiveTab, tab))
            ActiveTab = Tabs.ElementAtOrDefault(Math.Min(index, Math.Max(0, Tabs.Count - 1)));

        tab.Dispose();
    }

    public BrowserTab? ReopenClosed()
    {
        var tab = _closed.LastOrDefault();
        if (tab is null) return null;

        _closed.Remove(tab);
        var reopened = Create(tab.Url, tab.Kind, activate: true, title: tab.Title);
        reopened.IsPinned = tab.IsPinned;
        return reopened;
    }

    public void Move(BrowserTab tab, int newIndex)
    {
        var oldIndex = Tabs.IndexOf(tab);
        if (oldIndex < 0) return;

        // Pinned tabs stay in front.
        var minIndex = tab.IsPinned ? 0 : Tabs.Count(t => t.IsPinned);
        newIndex = Math.Clamp(newIndex, minIndex, Tabs.Count - 1);
        if (newIndex == oldIndex) return;

        Tabs.Move(oldIndex, newIndex);
    }

    public void TogglePin(BrowserTab tab)
    {
        tab.IsPinned = !tab.IsPinned;
        Tabs.Remove(tab);
        if (tab.IsPinned)
        {
            var insertAt = Tabs.Count(t => t.IsPinned);
            Tabs.Insert(insertAt, tab);
        }
        else
        {
            var insertAt = Tabs.Count(t => t.IsPinned);
            Tabs.Insert(Math.Min(insertAt, Tabs.Count), tab);
        }
    }

    public BrowserTab? Find(Func<BrowserTab, bool> predicate) => Tabs.FirstOrDefault(predicate);

    public BrowserTab? NextOf(BrowserTab tab, int direction)
    {
        if (Tabs.Count == 0) return null;
        var index = Tabs.IndexOf(tab);
        if (index < 0) return Tabs[0];
        var next = (index + direction + Tabs.Count) % Tabs.Count;
        return Tabs[next];
    }

    public void DisposeAll()
    {
        foreach (var tab in Tabs.ToList())
        {
            Tabs.Remove(tab);
            tab.Dispose();
        }

        _closed.Clear();
        ActiveTab = null;
    }
}
