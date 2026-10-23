using System.Diagnostics;
using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using Microsoft.Win32;

namespace DrBrowser;

public partial class MainWindow : Window, IBrowserShell
{
    private readonly MainViewModel _vm;
    private readonly Dictionary<string, UserControl> _pages = new();
    private Point _dragStart;
    private BrowserTab? _draggedTab;
    private bool _fullScreen;
    private WindowState _stateBeforeFullScreen = WindowState.Normal;

    public MainWindow(MainViewModel viewModel)
    {
        _vm = viewModel;
        InitializeComponent();
        DataContext = _vm;

        _vm.Tabs.CollectionChanged += (_, _) => UpdateWebViewVisibility();
        _vm.PropertyChanged += (_, e) =>
        {
            if (e.PropertyName is nameof(MainViewModel.WindowTitle) or nameof(MainViewModel.CurrentUrl)
                or nameof(MainViewModel.IsInternalPage))
                UpdateWebViewVisibility();

            if (e.PropertyName is nameof(MainViewModel.IsAiOpen)) EnsureAiPanel();
        };

        ThemeManager.Apply(_vm.Settings);
        _vm.NewTab.LoadWallpaper();

        EnsureAiPanel();
        UpdateWebViewVisibility();

        Loaded += (_, _) =>
        {
            if (_vm.RuntimeMissing && !App.IsTestMode) ShowStartupError(_vm.RuntimeStatus);
        };

        Closing += OnClosing;
    }

    // ------------------------------------------------------------------
    // IBrowserShell
    // ------------------------------------------------------------------

    public Panel WebHost => WebHostPanel;

    public MainViewModel ViewModel => _vm;

    public void ShowInternalPage(string key)
    {
        var view = GetPage(key);
        InternalHost.Content = view;
        InternalHost.Visibility = Visibility.Visible;
        WebHostPanel.Visibility = Visibility.Collapsed;
        UpdateWebViewVisibility();
    }

    public void ShowWebContent()
    {
        InternalHost.Visibility = Visibility.Collapsed;
        WebHostPanel.Visibility = Visibility.Visible;
        UpdateWebViewVisibility();
    }

    public void FocusAddressBar()
    {
        AddressBox.Focus();
        Keyboard.Focus(AddressBox);
    }

    public void SelectAddressBarText() => AddressBox.SelectAll();

    public void RequestFullscreen(bool enabled) => SetFullScreen(enabled);

    public void CloseWindow() => Close();

    public void OpenPrivateWindow() => App.Current.OpenWindow(true);

    public void SetAiPanelVisible(bool visible)
    {
        if (visible) EnsureAiPanel();
    }

    public Task<bool> AskPermissionAsync(string host, string permission)
    {
        return Dispatcher.InvokeAsync(() =>
        {
            var result = MessageBox.Show(
                $"Сайт {host} запрашивает доступ: {permission}.\n\nРазрешить?",
                "Разрешение сайта", MessageBoxButton.YesNo, MessageBoxImage.Question);
            return result == MessageBoxResult.Yes;
        }).Task;
    }

    public Task<bool> ConfirmAsync(string title, string message)
    {
        return Dispatcher.InvokeAsync(() =>
        {
            var result = MessageBox.Show(message, title, MessageBoxButton.YesNo, MessageBoxImage.Question);
            return result == MessageBoxResult.Yes;
        }).Task;
    }

    public string? PickSaveFile(string filter, string defaultName, string initialDirectory)
    {
        var dialog = new SaveFileDialog
        {
            Filter = filter,
            FileName = defaultName,
            InitialDirectory = Directory.Exists(initialDirectory) ? initialDirectory : null,
            OverwritePrompt = true
        };

        return dialog.ShowDialog(this) == true ? dialog.FileName : null;
    }

    public string? PickOpenFile(string filter)
    {
        var dialog = new OpenFileDialog { Filter = filter, CheckFileExists = true };
        return dialog.ShowDialog(this) == true ? dialog.FileName : null;
    }

    public string? PickFolder(string? initialDirectory)
    {
        try
        {
            var dialog = new OpenFolderDialog
            {
                Title = "Выберите папку",
                InitialDirectory = initialDirectory ?? AppPaths.DefaultDownloadsFolder
            };

            return dialog.ShowDialog(this) == true ? dialog.FolderName : null;
        }
        catch
        {
            return null;
        }
    }

    public void ShowMessage(string title, string message)
        => MessageBox.Show(this, message, title, MessageBoxButton.OK, MessageBoxImage.Information);

    public void OpenExternal(string pathOrUrl)
    {
        try
        {
            if (Uri.TryCreate(pathOrUrl, UriKind.Absolute, out var uri) && uri.Scheme is "http" or "https")
            {
                Process.Start(new ProcessStartInfo(uri.ToString()) { UseShellExecute = true });
                return;
            }

            if (Directory.Exists(pathOrUrl) || File.Exists(pathOrUrl))
                Process.Start(new ProcessStartInfo(pathOrUrl) { UseShellExecute = true });
        }
        catch (Exception ex)
        {
            MessageBox.Show(this, ex.Message, "DrBrowser", MessageBoxButton.OK, MessageBoxImage.Warning);
        }
    }

    public void ShowStartupError(string message)
    {
        if (_vm.RuntimeMissing) return;
        ShowMessage("DrBrowser", message);
    }

    // ------------------------------------------------------------------
    // Internal pages
    // ------------------------------------------------------------------

    private UserControl GetPage(string key)
    {
        if (_pages.TryGetValue(key, out var existing)) return existing;

        UserControl page = key switch
        {
            "settings" => new SettingsView(),
            "history" => new HistoryView(),
            "downloads" => new DownloadsView(),
            "bookmarks" => new BookmarksView(),
            "passwords" => new PasswordsView(),
            "search" => new SearchView(),
            "music" => new MusicView(),
            _ => new NewTabView()
        };

        page.DataContext = _vm;
        _pages[key] = page;
        return page;
    }

    private void EnsureAiPanel()
    {
        if (AiHost.Content is null)
        {
            var panel = new AiPanelView { DataContext = _vm };
            AiHost.Content = panel;
        }
    }

    private void UpdateWebViewVisibility()
    {
        var active = _vm.ActiveTab;
        foreach (var tab in _vm.Tabs)
        {
            if (tab.View is null) continue;
            var visible = ReferenceEquals(tab, active) && !tab.IsInternal && InternalHost.Visibility != Visibility.Visible;
            tab.View.Visibility = visible ? Visibility.Visible : Visibility.Collapsed;
        }
    }

    // ------------------------------------------------------------------
    // Window chrome
    // ------------------------------------------------------------------

    private void ProfileButton_Click(object sender, RoutedEventArgs e) => _vm.ToggleProfile();

    private void AvatarStyle_Click(object sender, RoutedEventArgs e)
    {
        if (sender is FrameworkElement { Tag: string style }) _vm.SetAvatarStyleCommand.Execute(style);
    }

    private void AccentSwatch_Click(object sender, RoutedEventArgs e)
    {
        if (sender is FrameworkElement { Tag: string hex }) _vm.SetAccentCommand.Execute(hex);
    }

    private void ProfileDismiss_Click(object sender, MouseButtonEventArgs e)
    {
        _vm.CloseProfile();
        e.Handled = true;
    }

    private void TitleBar_MouseLeftButtonDown(object sender, MouseButtonEventArgs e)
    {
        if (e.ChangedButton != MouseButton.Left) return;
        if (e.OriginalSource is DependencyObject source && FindAncestor<ButtonBase>(source) is not null) return;

        try
        {
            if (e.ClickCount == 2) ToggleMaximize();
            else DragMove();
        }
        catch
        {
            // dragging can fail when the mouse was released early
        }
    }

    private static T? FindAncestor<T>(DependencyObject? current) where T : DependencyObject
    {
        while (current is not null)
        {
            if (current is T match) return match;
            current = VisualTreeHelper.GetParent(current);
        }

        return null;
    }

    private void Minimize_Click(object sender, RoutedEventArgs e) => WindowState = WindowState.Minimized;

    private void Maximize_Click(object sender, RoutedEventArgs e) => ToggleMaximize();

    private void Close_Click(object sender, RoutedEventArgs e) => Close();

    private void ToggleMaximize()
        => WindowState = WindowState == WindowState.Maximized ? WindowState.Normal : WindowState.Maximized;

    private void SetFullScreen(bool enabled)
    {
        if (_fullScreen == enabled) return;
        _fullScreen = enabled;

        if (enabled)
        {
            _stateBeforeFullScreen = WindowState;
            WindowState = WindowState.Maximized;
        }
        else
        {
            WindowState = _stateBeforeFullScreen;
        }

        if (Content is Grid root && root.RowDefinitions.Count >= 6)
        {
            root.RowDefinitions[0].Height = enabled ? new GridLength(0) : new GridLength(44);
            root.RowDefinitions[1].Height = enabled ? new GridLength(0) : new GridLength(50);
            root.RowDefinitions[2].Height = enabled ? new GridLength(0) : GridLength.Auto;
            root.RowDefinitions[4].Height = enabled ? new GridLength(0) : GridLength.Auto;
            root.RowDefinitions[5].Height = enabled ? new GridLength(0) : new GridLength(26);
        }
    }

    // ------------------------------------------------------------------
    // Tabs: click / drag & drop
    // ------------------------------------------------------------------

    private void Tab_MouseLeftButtonDown(object sender, MouseButtonEventArgs e)
    {
        if (sender is not FrameworkElement element || element.DataContext is not BrowserTab tab) return;
        _draggedTab = tab;
        _dragStart = e.GetPosition(this);
        _vm.SelectTab(tab);
    }

    private void Tab_MouseMove(object sender, MouseEventArgs e)
    {
        if (e.LeftButton != MouseButtonState.Pressed || _draggedTab is null) return;

        var position = e.GetPosition(this);
        if (Math.Abs(position.X - _dragStart.X) < SystemParameters.MinimumHorizontalDragDistance &&
            Math.Abs(position.Y - _dragStart.Y) < SystemParameters.MinimumVerticalDragDistance)
            return;

        var tab = _draggedTab;
        _draggedTab = null;

        try
        {
            DragDrop.DoDragDrop(this, tab, DragDropEffects.Move);
        }
        catch
        {
            // dragging cancelled
        }
    }

    private void Tab_MouseLeftButtonUp(object sender, MouseButtonEventArgs e) => _draggedTab = null;

    private void Tab_DragOver(object sender, DragEventArgs e)
    {
        e.Effects = e.Data.GetDataPresent(typeof(BrowserTab)) ? DragDropEffects.Move : DragDropEffects.None;
        e.Handled = true;
    }

    private void Tab_Drop(object sender, DragEventArgs e)
    {
        if (sender is not FrameworkElement element || element.DataContext is not BrowserTab target) return;
        if (e.Data.GetData(typeof(BrowserTab)) is not BrowserTab dragged) return;

        var from = _vm.Tabs.IndexOf(dragged);
        var to = _vm.Tabs.IndexOf(target);
        if (from < 0 || to < 0 || from == to) return;

        _vm.MoveTab(from, to);
        e.Handled = true;
    }

    private void Tab_MouseRightButtonUp(object sender, MouseButtonEventArgs e)
    {
        if (sender is FrameworkElement element && element.DataContext is BrowserTab tab)
        {
            _vm.SelectTab(tab);
            element.ContextMenu!.IsOpen = true;
            e.Handled = true;
        }
    }

    // ------------------------------------------------------------------
    // Address bar
    // ------------------------------------------------------------------

    private void AddressBox_GotKeyboardFocus(object sender, KeyboardFocusChangedEventArgs e)
        => _vm.IsAddressEditing = true;

    private void AddressBox_KeyDown(object sender, KeyEventArgs e)
    {
        switch (e.Key)
        {
            case Key.Enter:
                _vm.IsAddressEditing = false;
                _vm.NavigateActiveTab(AddressBox.Text);
                Keyboard.ClearFocus();
                e.Handled = true;
                break;
            case Key.Escape:
                _vm.IsAddressEditing = false;
                AddressBox.Text = _vm.CurrentUrl;
                _vm.AddressText = _vm.CurrentUrl;
                e.Handled = true;
                break;
        }
    }

    private void Suggestion_MouseLeftButtonUp(object sender, MouseButtonEventArgs e)
    {
        if (sender is FrameworkElement element && element.DataContext is SuggestionItem item)
        {
            _vm.UseSuggestion(item);
            _vm.IsAddressEditing = false;
            e.Handled = true;
        }
    }

    // ------------------------------------------------------------------
    // Hotkeys
    // ------------------------------------------------------------------

    private void Window_PreviewKeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key == Key.Escape && _vm.IsProfileOpen)
        {
            _vm.CloseProfile();
            e.Handled = true;
            return;
        }

        if (HandleHotkey(e.Key, Keyboard.Modifiers)) e.Handled = true;
    }

    /// <summary>Browser shortcuts, shared by window keys and by keys pressed inside a web page.</summary>
    private bool HandleHotkey(Key key, ModifierKeys modifiers)
    {
        var ctrl = modifiers.HasFlag(ModifierKeys.Control);
        var shift = modifiers.HasFlag(ModifierKeys.Shift);
        var alt = modifiers.HasFlag(ModifierKeys.Alt);

        if (ctrl)
        {
            switch (key)
            {
                case Key.T when shift: _vm.ReopenClosedTab(); return true;
                case Key.T: _vm.CreateNewTabPage(); return true;
                case Key.W: _vm.CloseTab(_vm.ActiveTab); return true;
                case Key.L: _vm.FocusAddressBar(); return true;
                case Key.R: _vm.Reload(); return true;
                case Key.F: _vm.IsFindVisible = true; return true;
                case Key.H: _vm.OpenInternal("history"); return true;
                case Key.J: _vm.OpenInternal("downloads"); return true;
                case Key.D: _vm.ToggleBookmarkCommand.Execute(null); return true;
                case Key.N when shift: App.Current.OpenWindow(true); return true;
                case Key.Tab: _vm.NextTab(shift ? -1 : 1); return true;
                case Key.OemPlus:
                case Key.Add: _vm.ZoomBy(0.1); return true;
                case Key.OemMinus:
                case Key.Subtract: _vm.ZoomBy(-0.1); return true;
                case Key.D0:
                case Key.NumPad0: _vm.ResetZoom(); return true;
                case Key.P: _vm.SavePagePdf(); return true;
                case Key.M when shift: _vm.OpenInternal("music"); return true;
                case Key.K: _vm.OpenInternal("search"); return true;

                // DrMusic transport: Ctrl+Alt+Space / Ctrl+Alt+Left / Ctrl+Alt+Right.
                case Key.Space when alt: _vm.ToggleMusicCommand.Execute(null); return true;
                case Key.Left when alt: _vm.MusicPreviousCommand.Execute(null); return true;
                case Key.Right when alt: _vm.MusicNextCommand.Execute(null); return true;
            }
        }

        if (alt)
        {
            if (key == Key.Left) { _vm.GoBack(); return true; }
            if (key == Key.Right) { _vm.GoForward(); return true; }
        }

        if (key == Key.F11)
        {
            SetFullScreen(!_fullScreen);
            return true;
        }

        if (key == Key.F5)
        {
            _vm.Reload();
            return true;
        }

        if (key == Key.Escape)
        {
            if (_vm.IsFindVisible) { _vm.IsFindVisible = false; return true; }
            if (_vm.IsAiOpen) { _vm.IsAiOpen = false; return true; }
        }

        return false;
    }

    public bool TryHandleHotkey(int virtualKey)
    {
        try
        {
            return HandleHotkey(KeyInterop.KeyFromVirtualKey(virtualKey), Keyboard.Modifiers);
        }
        catch
        {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    private void OnClosing(object? sender, System.ComponentModel.CancelEventArgs e)
    {
        try
        {
            _vm.Shutdown();
            if (_vm.IsPrivate && !string.IsNullOrWhiteSpace(_vm.WebProfileFolder))
            {
                // Incognito WebView2 profile (cookies, cache, history) is destroyed here.
                AppPaths.TryDeleteDirectory(_vm.WebProfileFolder);
            }
        }
        catch
        {
            // never block closing
        }
    }

    /// <summary>Used by the smoke run to render the window to a PNG.</summary>
    public bool CaptureWindow(string path)
    {
        try
        {
            UpdateLayout();
            var width = (int)Math.Max(1, ActualWidth);
            var height = (int)Math.Max(1, ActualHeight);
            var bitmap = new RenderTargetBitmap(width, height, 96, 96, PixelFormats.Pbgra32);
            bitmap.Render(this);

            Directory.CreateDirectory(Path.GetDirectoryName(path)!);
            var encoder = new PngBitmapEncoder();
            encoder.Frames.Add(BitmapFrame.Create(bitmap));
            using var stream = File.Create(path);
            encoder.Save(stream);
            return true;
        }
        catch
        {
            return false;
        }
    }
}
