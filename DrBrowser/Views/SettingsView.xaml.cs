using System.Windows;
using System.Windows.Controls;

namespace DrBrowser;

public partial class SettingsView : UserControl
{
    public SettingsView() => InitializeComponent();

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
