using System.Windows.Controls;

namespace DrBrowser;

/// <summary>
/// Everything the view model needs from the window, so the browser logic stays testable
/// and the XAML window owns only presentation concerns.
/// </summary>
public interface IBrowserShell
{
    Panel WebHost { get; }

    /// <summary>Shows a WPF page (settings / history / downloads / bookmarks / passwords / new tab).</summary>
    void ShowInternalPage(string key);

    /// <summary>Shows the WebView2 layer again.</summary>
    void ShowWebContent();

    void FocusAddressBar();
    void SelectAddressBarText();
    void RequestFullscreen(bool enabled);
    void CloseWindow();
    void OpenPrivateWindow();
    void SetAiPanelVisible(bool visible);

    Task<bool> AskPermissionAsync(string host, string permission);
    Task<bool> ConfirmAsync(string title, string message);
    string? PickSaveFile(string filter, string defaultName, string initialDirectory);
    string? PickOpenFile(string filter);
    string? PickFolder(string? initialDirectory);

    void ShowMessage(string title, string message);
    void OpenExternal(string pathOrUrl);
}
