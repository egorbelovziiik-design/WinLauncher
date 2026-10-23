using System.Windows.Input;

namespace StealAnEggUltimate;

/// <summary>Thin wrapper that exposes the hub state to the overlay window.</summary>
public sealed class OverlayViewModel : ObservableObject
{
    public OverlayViewModel(MainViewModel hub)
    {
        Hub = hub;
        Hub.PropertyChanged += (_, e) =>
        {
            if (e.PropertyName != nameof(MainViewModel.OverlayTab)) return;
            OnPropertyChanged(nameof(IsEggTab));
            OnPropertyChanged(nameof(IsPlayerTab));
            OnPropertyChanged(nameof(IsAutoTab));
            OnPropertyChanged(nameof(IsVisualTab));
            OnPropertyChanged(nameof(IsCommandTab));
        };
        hub.MenuStateChanged += (_, _) => OnPropertyChanged(nameof(MenuKeyHint));
    }

    public MainViewModel Hub { get; }

    public bool IsEggTab
    {
        get => Hub.OverlayTab == "Egg";
        set => SetTab(value, "Egg");
    }

    public bool IsPlayerTab
    {
        get => Hub.OverlayTab == "Player";
        set => SetTab(value, "Player");
    }

    public bool IsAutoTab
    {
        get => Hub.OverlayTab == "Auto";
        set => SetTab(value, "Auto");
    }

    public bool IsVisualTab
    {
        get => Hub.OverlayTab == "Visual";
        set => SetTab(value, "Visual");
    }

    public bool IsCommandTab
    {
        get => Hub.OverlayTab == "Cmd";
        set => SetTab(value, "Cmd");
    }

    private void SetTab(bool selected, string tab)
    {
        if (!selected) return;
        Hub.OverlayTab = tab;
        OnPropertyChanged(nameof(IsEggTab));
        OnPropertyChanged(nameof(IsPlayerTab));
        OnPropertyChanged(nameof(IsAutoTab));
        OnPropertyChanged(nameof(IsVisualTab));
        OnPropertyChanged(nameof(IsCommandTab));
    }

    public string VersionLabel => Hub.ShortVersion;
    public string ProductName => AppInfo.ProductName;

    public string MenuKeyLabel => Hub.Settings.Overlay.MenuKey switch
    {
        "RightShift" => "RIGHT SHIFT",
        "LeftShift" => "LEFT SHIFT",
        "Insert" => "INSERT",
        "CapsLock" => "CAPS LOCK",
        var other => other.ToUpperInvariant()
    };

    public string MenuKeyHint => $"{MenuKeyLabel} closes the menu";

    public ICommand DisconnectCommand => Hub.DisconnectCommand;
    public ICommand ConnectCommand => Hub.ConnectCommand;
    public ICommand ActivateHubCommand => Hub.ActivateHubCommand;
    public ICommand ResetMenuPositionCommand => Hub.ResetMenuPositionCommand;
    public ICommand SaveConfigCommand => Hub.SaveConfigCommand;
    public ICommand ToggleAutomationCommand => Hub.ToggleAutomationCommand;
    public ICommand ScanCommand => Hub.ScanCommand;
    public ICommand SellAllCommand => Hub.SellAllCommand;
    public ICommand TrackBestCommand => Hub.TrackBestCommand;
    public ICommand TeleportCommand => Hub.TeleportCommand;
    public ICommand ExecuteCommand => Hub.ExecuteCommand;
    public ICommand ClearCommand => Hub.ClearCommand;
    public ICommand ApplyThemeCommand => Hub.ApplyThemeCommand;
    public ICommand SelectEspColorCommand => Hub.SelectEspColorCommand;
    public ICommand SelectRareColorCommand => Hub.SelectRareColorCommand;

    public IReadOnlyList<string> EspColorPresets => Hub.EspColorPresets;
}
