using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;

namespace DrBrowser;

public partial class SearchView : UserControl
{
    public SearchView() => InitializeComponent();

    private MainViewModel? Vm => DataContext as MainViewModel;

    private void QueryBox_KeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key != Key.Enter) return;
        Vm?.Search.Run(QueryBox.Text);
        e.Handled = true;
    }

    private void Search_Click(object sender, RoutedEventArgs e) => Vm?.Search.Run(QueryBox.Text);

    private void WebResult_Click(object sender, MouseButtonEventArgs e)
    {
        if (sender is not FrameworkElement { DataContext: WebSearchHit hit }) return;
        if (string.IsNullOrWhiteSpace(hit.Url)) return;
        Vm?.NavigateActiveTab(hit.Url);
    }

    private void Result_Click(object sender, MouseButtonEventArgs e)
    {
        if (sender is not FrameworkElement { DataContext: SearchResult result }) return;
        if (string.IsNullOrWhiteSpace(result.Url)) return;
        Vm?.NavigateActiveTab(result.Url);
    }
}
