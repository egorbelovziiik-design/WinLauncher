using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;

namespace DrBrowser;

public partial class MusicView : UserControl
{
    public MusicView() => InitializeComponent();

    private MainViewModel? Vm => DataContext as MainViewModel;

    private void Track_Click(object sender, MouseButtonEventArgs e)
    {
        if (sender is not FrameworkElement { DataContext: MusicTrack track }) return;
        Vm?.Music.Service.Play(track);
    }

    private void CatalogQuery_KeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key != Key.Enter) return;
        Vm?.Music.SearchCatalogCommand.Execute(null);
        e.Handled = true;
    }

    private void CatalogSource_Click(object sender, RoutedEventArgs e)
    {
        if (sender is FrameworkElement { Tag: string source })
            Vm?.Music.CatalogSource = source;
    }

    private void Seek_ValueChanged(object sender, RoutedPropertyChangedEventArgs<double> e)
    {
        // Only react to real user drags, otherwise the polled position fights the slider.
        if (SeekSlider is null || !SeekSlider.IsMouseCaptureWithin) return;
        Vm?.Music.Service.Seek(e.NewValue);
    }
}
