using System.Collections.ObjectModel;
using System.Windows.Controls;

namespace DrBrowser;

public partial class PasswordsView : UserControl
{
    private readonly ObservableCollection<PasswordEntry> _filtered = new();

    public PasswordsView()
    {
        InitializeComponent();
        List.ItemsSource = _filtered;
        Loaded += async (_, _) =>
        {
            if (DataContext is MainViewModel vm) await vm.Passwords.LoadAsync();
            Refresh();
        };
    }

    private void Refresh()
    {
        if (DataContext is not MainViewModel vm) return;

        _filtered.Clear();
        foreach (var entry in vm.Passwords.Entries) _filtered.Add(entry);
    }

    private void Search_Changed(object sender, TextChangedEventArgs e)
    {
        if (DataContext is not MainViewModel vm) return;

        _filtered.Clear();
        foreach (var entry in vm.Passwords.Search((sender as TextBox)?.Text)) _filtered.Add(entry);
    }
}
