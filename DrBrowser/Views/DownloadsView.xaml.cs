using System.Collections.ObjectModel;
using System.Windows.Controls;

namespace DrBrowser;

public partial class DownloadsView : UserControl
{
    private readonly ObservableCollection<HistoryRow> _rows = new();

    public DownloadsView()
    {
        InitializeComponent();
        HistoryList.ItemsSource = _rows;
        Loaded += (_, _) => Load();
    }

    private void Load()
    {
        if (DataContext is not MainViewModel vm) return;

        _rows.Clear();
        foreach (var row in vm.Downloads.History())
        {
            _rows.Add(new HistoryRow
            {
                FileName = row.FileName,
                Path = row.Path,
                Summary = $"{DownloadItem.FormatSize(row.Size)} · {row.State} · {row.Started:dd.MM HH:mm}"
            });
        }
    }

    public sealed class HistoryRow
    {
        public string FileName { get; set; } = string.Empty;
        public string Path { get; set; } = string.Empty;
        public string Summary { get; set; } = string.Empty;
    }
}
