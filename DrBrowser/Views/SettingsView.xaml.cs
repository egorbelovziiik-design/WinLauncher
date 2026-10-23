using System.Windows;
using System.Windows.Controls;

namespace DrBrowser;

public partial class SettingsView : UserControl
{
    public SettingsView() => InitializeComponent();

    private void Theme_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (DataContext is MainViewModel vm && sender is ComboBox { SelectedItem: string theme }
            && !string.Equals(vm.Settings.Theme, theme, StringComparison.OrdinalIgnoreCase))
            vm.ApplyThemeCommand.Execute(theme);
    }

    private void AvatarStyle_Click(object sender, RoutedEventArgs e)
    {
        if (DataContext is MainViewModel vm && sender is FrameworkElement { Tag: string style })
            vm.SetAvatarStyleCommand.Execute(style);
    }

    private void Persona_Click(object sender, RoutedEventArgs e)
    {
        if (DataContext is MainViewModel vm && sender is FrameworkElement { Tag: string id })
            vm.SelectPersonaCommand.Execute(id);
    }

    private void SaveKey_Click(object sender, RoutedEventArgs e)
    {
        if (DataContext is not MainViewModel vm) return;

        vm.AiKeyInput = AiKeyBox.Password;
        vm.SaveAiKeyCommand.Execute(null);
        AiKeyBox.Clear();
    }
}
