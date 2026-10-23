using System.Collections.ObjectModel;
using System.Windows;
using System.Windows.Controls;

namespace DrBrowser;

public partial class BookmarksView : UserControl
{
    private readonly ObservableCollection<Bookmark> _filtered = new();
    private Bookmark? _selected;
    private string _folderFilter = string.Empty;

    public BookmarksView()
    {
        InitializeComponent();
        List.ItemsSource = _filtered;
        Loaded += async (_, _) => await RefreshAsync();
    }

    private MainViewModel? Vm => DataContext as MainViewModel;

    private async Task RefreshAsync()
    {
        if (Vm is null) return;

        await Vm.Bookmarks.LoadAsync();
        ApplyFilter();

        FolderList.ItemsSource = Vm.Bookmarks.Folders;
        FolderBox.ItemsSource = Vm.Bookmarks.Folders;
        if (FolderBox.SelectedItem is null && Vm.Bookmarks.Folders.Count > 0)
            FolderBox.SelectedItem = Vm.Bookmarks.Folders[0];
    }

    private void ApplyFilter()
    {
        if (Vm is null) return;

        _filtered.Clear();
        foreach (var bookmark in Vm.Bookmarks.Search(SearchBox.Text))
        {
            if (!string.IsNullOrEmpty(_folderFilter) && bookmark.Folder != _folderFilter) continue;
            _filtered.Add(bookmark);
        }
    }

    private void Filter_Changed(object sender, TextChangedEventArgs e) => ApplyFilter();

    private async void Add_Click(object sender, RoutedEventArgs e)
    {
        if (Vm is null) return;

        var url = UrlBox.Text?.Trim() ?? string.Empty;
        if (string.IsNullOrWhiteSpace(url))
        {
            return;
        }

        if (!url.Contains("://")) url = "https://" + url;
        if (!Uri.TryCreate(url, UriKind.Absolute, out var uri) || uri.Scheme is not ("http" or "https")) return;

        await Vm.Bookmarks.AddAsync(TitleBox.Text ?? string.Empty, uri.ToString(),
            FolderBox.Text ?? "Закладки");

        TitleBox.Text = string.Empty;
        UrlBox.Text = string.Empty;
        ApplyFilter();
    }

    private void List_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        _selected = List.SelectedItem as Bookmark;
        if (_selected is null) return;

        EditTitle.Text = _selected.Title;
        EditUrl.Text = _selected.Url;
        EditFolder.Text = _selected.Folder;
        EditHint.Text = "Редактируется: " + _selected.Display;
    }

    private async void Save_Click(object sender, RoutedEventArgs e)
    {
        if (Vm is null || _selected is null) return;

        var url = EditUrl.Text?.Trim() ?? string.Empty;
        if (!url.Contains("://")) url = "https://" + url;
        if (!Uri.TryCreate(url, UriKind.Absolute, out var uri)) return;

        _selected.Title = string.IsNullOrWhiteSpace(EditTitle.Text) ? uri.Host : EditTitle.Text.Trim();
        _selected.Url = uri.ToString();
        _selected.Folder = string.IsNullOrWhiteSpace(EditFolder.Text) ? "Закладки" : EditFolder.Text.Trim();

        await Vm.Bookmarks.UpdateAsync(_selected);
        ApplyFilter();
        EditHint.Text = "Сохранено.";
    }

    private void FolderList_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        _folderFilter = FolderList.SelectedItem as string ?? string.Empty;
        ApplyFilter();
    }
}
