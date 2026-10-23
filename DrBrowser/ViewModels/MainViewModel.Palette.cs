using System.Collections.ObjectModel;
using System.Windows.Input;

namespace DrBrowser;

/// <summary>One searchable, keyboard-first registry of user-facing browser actions.</summary>
public sealed record CommandPaletteItem(string Id, string Title, string Group, string Shortcut = "")
{
    public string SearchText => $"{Title} {Group} {Id}";
}

public sealed partial class MainViewModel
{
    private static readonly CommandPaletteItem[] PaletteEntries =
    {
        new("new-tab", "Новая вкладка", "Браузер", "Ctrl+T"),
        new("private-tab", "Новое приватное окно", "Браузер", "Ctrl+Shift+N"),
        new("reopen-tab", "Восстановить закрытую вкладку", "Браузер", "Ctrl+Shift+T"),
        new("bookmark", "Добавить или убрать закладку", "Браузер", "Ctrl+D"),
        new("route:search", "Открыть DrSearch", "OMEGA", "Ctrl+Shift+S"),
        new("mind", "Открыть DrMind", "OMEGA", "Ctrl+Shift+A"),
        new("route:music", "Открыть DrMusic", "OMEGA", "Ctrl+Shift+M"),
        new("route:workspaces", "Открыть Workspaces", "Продуктивность"),
        new("route:notes", "Открыть Notes", "Продуктивность"),
        new("route:tasks", "Открыть Tasks", "Продуктивность"),
        new("route:tools", "Открыть DrTools", "Продуктивность"),
        new("route:performance", "Открыть Performance", "Система"),
        new("route:gaming", "Открыть Gaming", "Режимы"),
        new("gaming-mode", "Включить Gaming Mode", "Режимы"),
        new("route:guard", "Открыть DrGuard", "Безопасность", "Ctrl+Shift+G"),
        new("route:passwords", "Открыть Password Vault", "Безопасность"),
        new("route:downloads", "Открыть загрузки", "Браузер", "Ctrl+J"),
        new("route:history", "Открыть историю", "Браузер", "Ctrl+H"),
        new("route:bookmarks", "Открыть закладки", "Браузер"),
        new("route:settings", "Открыть настройки", "Система"),
        new("find", "Найти на странице", "Браузер", "Ctrl+F"),
        new("devtools", "Открыть DevTools", "Разработка", "F12"),
        new("fullscreen", "Полноэкранный режим", "Окно", "F11")
    };

    private bool _isCommandPaletteOpen;
    private string _commandPaletteQuery = string.Empty;
    private CommandPaletteItem? _selectedPaletteItem;

    public ObservableCollection<CommandPaletteItem> CommandPaletteResults { get; } = new();

    public bool IsCommandPaletteOpen
    {
        get => _isCommandPaletteOpen;
        set => Set(ref _isCommandPaletteOpen, value);
    }

    public string CommandPaletteQuery
    {
        get => _commandPaletteQuery;
        set
        {
            if (Set(ref _commandPaletteQuery, value ?? string.Empty)) RefreshCommandPalette();
        }
    }

    public CommandPaletteItem? SelectedPaletteItem
    {
        get => _selectedPaletteItem;
        set => Set(ref _selectedPaletteItem, value);
    }

    public ICommand OpenCommandPaletteCommand => Cmd("palette.open", OpenCommandPalette);
    public ICommand CloseCommandPaletteCommand => Cmd("palette.close", CloseCommandPalette);
    public ICommand ExecutePaletteItemCommand => Cmd("palette.execute", parameter =>
    {
        var item = parameter as CommandPaletteItem ?? SelectedPaletteItem;
        if (item is not null) ExecutePaletteItem(item);
    });

    public void OpenCommandPalette()
    {
        CommandPaletteQuery = string.Empty;
        RefreshCommandPalette();
        IsCommandPaletteOpen = true;
    }

    public void CloseCommandPalette() => IsCommandPaletteOpen = false;

    public void ExecuteSelectedPaletteItem()
    {
        if (SelectedPaletteItem is not null) ExecutePaletteItem(SelectedPaletteItem);
    }

    private void RefreshCommandPalette()
    {
        var terms = CommandPaletteQuery
            .Split(' ', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries);
        var matches = PaletteEntries
            .Where(entry => terms.All(term => entry.SearchText.Contains(term, StringComparison.CurrentCultureIgnoreCase)))
            .Take(15)
            .ToArray();

        CommandPaletteResults.Clear();
        foreach (var item in matches) CommandPaletteResults.Add(item);
        SelectedPaletteItem = CommandPaletteResults.FirstOrDefault();
    }

    private void ExecutePaletteItem(CommandPaletteItem item)
    {
        CloseCommandPalette();
        if (item.Id.StartsWith("route:", StringComparison.Ordinal))
        {
            OpenInternal(item.Id[6..]);
            return;
        }

        switch (item.Id)
        {
            case "new-tab": NewTabCommand.Execute(null); break;
            case "private-tab": OpenPrivateWindowCommand.Execute(null); break;
            case "reopen-tab": ReopenClosedTab(); break;
            case "bookmark": ToggleBookmarkCommand.Execute(null); break;
            case "mind": IsAiOpen = true; break;
            case "gaming-mode": EnableGamingMode(); break;
            case "find": ToggleFindCommand.Execute(null); break;
            case "devtools": ActiveTab?.Core?.OpenDevToolsWindow(); break;
            case "fullscreen": _shell?.RequestFullscreen(true); break;
        }
    }
}
