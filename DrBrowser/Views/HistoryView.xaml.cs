using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;

namespace DrBrowser;

public partial class HistoryView : UserControl
{
    public HistoryView()
    {
        InitializeComponent();
        RangeBox.ItemsSource = new[] { "Всё время", "Сегодня", "7 дней", "30 дней" };
        RangeBox.SelectedIndex = 0;
        Loaded += async (_, _) => await RefreshAsync();
    }

    private MainViewModel? Vm => DataContext as MainViewModel;

    private async Task RefreshAsync()
    {
        if (Vm is null) return;

        DateTime? from = RangeBox.SelectedIndex switch
        {
            1 => DateTime.Today,
            2 => DateTime.Now.AddDays(-7),
            3 => DateTime.Now.AddDays(-30),
            _ => null
        };

        await Vm.History.LoadAsync(SearchBox.Text, from, null);
    }

    private async void Search_Click(object sender, RoutedEventArgs e) => await RefreshAsync();

    private async void Search_KeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key == Key.Enter) await RefreshAsync();
    }

    private async void Range_Changed(object sender, SelectionChangedEventArgs e)
    {
        if (IsLoaded) await RefreshAsync();
    }
}
