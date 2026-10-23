using System.Windows;
using System.Windows.Controls;

namespace DrBrowser;

public partial class SettingsView : UserControl
{
    public SettingsView() => InitializeComponent();

    private void SaveKey_Click(object sender, RoutedEventArgs e)
    {
        if (DataContext is not MainViewModel vm) return;

        vm.AiKeyInput = AiKeyBox.Password;
        vm.SaveAiKeyCommand.Execute(null);
        AiKeyBox.Clear();
    }
}
