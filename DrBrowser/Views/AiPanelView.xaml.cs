using System.Windows;
using System.Windows.Controls;
using System.Collections.Specialized;
using System.Windows.Input;

namespace DrBrowser;

public partial class AiPanelView : UserControl
{
    public AiPanelView()
    {
        InitializeComponent();

        if (DataContext is MainViewModel vm)
        {
            vm.AiMessages.CollectionChanged += OnMessagesChanged;
        }

        DataContextChanged += (_, _) =>
        {
            if (DataContext is MainViewModel model)
                model.AiMessages.CollectionChanged += OnMessagesChanged;
        };
    }

    private void OnMessagesChanged(object? sender, NotifyCollectionChangedEventArgs e)
        => Dispatcher.InvokeAsync(() => ChatScroll.ScrollToEnd(), System.Windows.Threading.DispatcherPriority.Background);

    private void Input_KeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key == Key.Enter && !Keyboard.Modifiers.HasFlag(ModifierKeys.Shift))
        {
            (DataContext as MainViewModel)?.SendAiCommand.Execute(null);
            e.Handled = true;
        }
    }

    private void Persona_Click(object sender, RoutedEventArgs e)
    {
        if (DataContext is MainViewModel vm && sender is FrameworkElement { Tag: string id })
            vm.SelectPersonaCommand.Execute(id);
    }
}
