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

    // ------------------------------------------------------------------
    // Drag & drop inside the playlist panel
    // ------------------------------------------------------------------

    private Point _dragStart;
    private MusicTrack? _dragTrack;
    private bool _dragging;

    private void PlaylistDrag_MouseDown(object sender, MouseButtonEventArgs e)
    {
        if (e.ChangedButton != MouseButton.Left) return;
        if (sender is not FrameworkElement { DataContext: MusicTrack track }) return;
        _dragTrack = track;
        _dragStart = e.GetPosition(this);
        _dragging = false;
    }

    private void PlaylistDrag_MouseMove(object sender, MouseEventArgs e)
    {
        if (_dragging || _dragTrack is null || e.LeftButton != MouseButtonState.Pressed) return;

        var delta = e.GetPosition(this) - _dragStart;
        if (Math.Abs(delta.Y) < SystemParameters.MinimumVerticalDragDistance) return;

        var playlist = Vm?.Music.SelectedPlaylist;
        if (playlist is null) return;

        var from = playlist.Tracks.IndexOf(_dragTrack);
        if (from < 0) return;

        _dragging = true;
        try
        {
            DragDrop.DoDragDrop((DependencyObject)sender, new DataObject("drb-playlist-from", from), DragDropEffects.Move);
        }
        finally
        {
            _dragging = false;
            _dragTrack = null;
        }
    }

    private void PlaylistDrag_DragOver(object sender, DragEventArgs e)
    {
        e.Effects = e.Data.GetDataPresent("drb-playlist-from") ? DragDropEffects.Move : DragDropEffects.None;
        e.Handled = true;
    }

    private void PlaylistDrag_Drop(object sender, DragEventArgs e)
    {
        if (!e.Data.GetDataPresent("drb-playlist-from")) return;
        if (sender is not FrameworkElement { DataContext: MusicTrack target }) return;

        var playlist = Vm?.Music.SelectedPlaylist;
        if (playlist is null) return;

        var from = (int)e.Data.GetData("drb-playlist-from")!;
        var to = playlist.Tracks.IndexOf(target);
        if (to < 0) return;

        Vm!.Music.MovePlaylistTrackTo(from, to);
        e.Handled = true;
    }

    private void Seek_ValueChanged(object sender, RoutedPropertyChangedEventArgs<double> e)
    {
        // Only react to real user drags, otherwise the polled position fights the slider.
        if (SeekSlider is null || !SeekSlider.IsMouseCaptureWithin) return;
        Vm?.Music.Service.Seek(e.NewValue);
    }
}
