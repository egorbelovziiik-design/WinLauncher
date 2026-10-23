using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;

namespace DrBrowser;

public partial class NewTabView : UserControl
{
    public NewTabView() => InitializeComponent();

    private MainViewModel? Vm => DataContext as MainViewModel;

    private void SearchBox_KeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key != Key.Enter) return;
        Navigate(SearchBox.Text);
        e.Handled = true;
    }

    private void Search_Click(object sender, RoutedEventArgs e) => Navigate(SearchBox.Text);

    private void Navigate(string? text)
    {
        if (string.IsNullOrWhiteSpace(text)) return;
        Vm?.NavigateActiveTab(text);
    }

    private void QuickLink_Click(object sender, MouseButtonEventArgs e)
    {
        if (sender is FrameworkElement { DataContext: QuickLink link }) Vm?.NavigateActiveTab(link.Url);
    }

    private void Recent_Click(object sender, MouseButtonEventArgs e)
    {
        if (sender is FrameworkElement { DataContext: VisitedSite site }) Vm?.NavigateActiveTab(site.Url);
    }
}
