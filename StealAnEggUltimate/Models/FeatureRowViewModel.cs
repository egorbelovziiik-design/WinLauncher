namespace StealAnEggUltimate;

/// <summary>One ON/OFF row in the hub and in the in-game menu.</summary>
public sealed class FeatureRowViewModel : ObservableObject
{
    private readonly AppSettings _settings;
    private readonly Action<string, bool> _onChanged;
    private bool _enabled;

    public FeatureRowViewModel(FeatureDefinition definition, AppSettings settings, Action<string, bool> onChanged)
    {
        Definition = definition;
        _settings = settings;
        _onChanged = onChanged;
        _enabled = settings.Features.TryGetValue(definition.Id, out var on) && on;
    }

    public FeatureDefinition Definition { get; }

    public string Id => Definition.Id;
    public string Title => Definition.Title;
    public string Description => Definition.Description;

    public string Badge => Definition.Scope switch
    {
        FeatureScope.Local => "LOCAL",
        FeatureScope.Snapshot => "SNAPSHOT",
        _ => "TEST PLACE"
    };

    public string? Tooltip => Definition.RestrictionNote;

    public bool IsEnabled
    {
        get => _enabled;
        set
        {
            if (!Set(ref _enabled, value)) return;
            _settings.Features[Id] = value;
            _onChanged(Id, value);
        }
    }

    /// <summary>Refreshes the row from settings (used after config load / reset).</summary>
    public void SyncFromSettings()
    {
        var value = _settings.Features.TryGetValue(Id, out var on) && on;
        if (value == _enabled) return;
        _enabled = value;
        OnPropertyChanged(nameof(IsEnabled));
    }
}

public sealed record ConsoleLine(DateTime Time, NoticeKind Kind, string Text)
{
    public string TimeText => Time.ToString("HH:mm:ss");
    public string Display => $"{Time:HH:mm:ss}  {Text}";
}
