using System.Collections.ObjectModel;
using System.Diagnostics;
using System.Globalization;
using System.Windows;
using System.Windows.Input;

namespace DrBrowser;

/// <summary>State for real local OMEGA modules: workspaces, notes, tasks and tools.</summary>
public sealed class OmegaViewModel : ObservableObject
{
    private readonly ProductivityService _service;
    private readonly Logger _log;
    private readonly Func<string, string, Task<bool>> _confirm;
    private readonly Func<IReadOnlyList<SessionTab>> _captureTabs;
    private readonly Func<WorkspaceItem, Task> _restoreWorkspace;
    private readonly Action<string> _navigate;
    private readonly Action _enableGamingMode;
    private readonly Func<int> _tabCount;
    private readonly Func<string> _runtimeText;
    private bool _loaded;
    private int _selectedSection;

    private NoteItem? _selectedNote;
    private string _noteTitle = string.Empty;
    private string _noteBody = string.Empty;
    private string _taskTitle = string.Empty;
    private WorkspaceItem? _selectedWorkspace;
    private string _workspaceName = string.Empty;
    private string _workspaceColor = "#8B5CF6";
    private string _status = string.Empty;

    private string _calculatorInput = "(12 + 8) * 3 / 2";
    private string _calculatorResult = string.Empty;
    private string _jsonInput = "{\"omega\":true,\"version\":3}";
    private string _jsonOutput = string.Empty;
    private string _unitValue = "1";
    private string _unitFrom = "km";
    private string _unitTo = "mi";
    private string _unitResult = string.Empty;
    private int _passwordLength = 20;
    private string _generatedPassword = string.Empty;
    private string _textInput = string.Empty;
    private string _textOutput = string.Empty;
    private string _colorInput = "#8B5CF6";
    private string _colorOutput = string.Empty;
    private TimeSpan _lastCpu;
    private DateTime _lastCpuAt;

    public OmegaViewModel(
        ProductivityService service,
        Logger log,
        Func<string, string, Task<bool>> confirm,
        Func<IReadOnlyList<SessionTab>> captureTabs,
        Func<WorkspaceItem, Task> restoreWorkspace,
        Action<string> navigate,
        Action enableGamingMode,
        Func<int> tabCount,
        Func<string> runtimeText)
    {
        _service = service;
        _log = log;
        _confirm = confirm;
        _captureTabs = captureTabs;
        _restoreWorkspace = restoreWorkspace;
        _navigate = navigate;
        _enableGamingMode = enableGamingMode;
        _tabCount = tabCount;
        _runtimeText = runtimeText;
    }

    public ObservableCollection<NoteItem> Notes { get; } = new();
    public ObservableCollection<TaskItem> Tasks { get; } = new();
    public ObservableCollection<WorkspaceItem> Workspaces { get; } = new();
    public IReadOnlyList<string> Units => UtilityEngine.Units;

    public int SelectedSection
    {
        get => _selectedSection;
        set => Set(ref _selectedSection, Math.Clamp(value, 0, 5));
    }

    public string Status { get => _status; private set => Set(ref _status, value); }

    public async Task LoadAsync()
    {
        if (_loaded) { RefreshPerformance(); return; }
        _loaded = true;
        await ReloadNotesAsync().ConfigureAwait(true);
        await ReloadTasksAsync().ConfigureAwait(true);
        await ReloadWorkspacesAsync().ConfigureAwait(true);
        RefreshPerformance();
    }

    public void SelectSection(string key)
    {
        SelectedSection = key switch
        {
            "workspaces" => 0,
            "notes" => 1,
            "tasks" => 2,
            "tools" => 3,
            "performance" => 4,
            "gaming" => 5,
            _ => SelectedSection
        };
        if (key == "performance") RefreshPerformance();
    }

    // Notes -----------------------------------------------------------------
    public NoteItem? SelectedNote
    {
        get => _selectedNote;
        set
        {
            if (!Set(ref _selectedNote, value)) return;
            NoteTitle = value?.Title ?? string.Empty;
            NoteBody = value?.Body ?? string.Empty;
        }
    }
    public string NoteTitle { get => _noteTitle; set => Set(ref _noteTitle, value ?? string.Empty); }
    public string NoteBody { get => _noteBody; set => Set(ref _noteBody, value ?? string.Empty); }

    public ICommand NewNoteCommand => new RelayCommand(() =>
    {
        SelectedNote = null;
        NoteTitle = string.Empty;
        NoteBody = string.Empty;
        Status = "Новая заметка — заполните заголовок или текст.";
    });

    public ICommand SaveNoteCommand => new RelayCommand(async () =>
    {
        if (string.IsNullOrWhiteSpace(NoteTitle) && string.IsNullOrWhiteSpace(NoteBody))
        {
            Status = "Пустая заметка не сохраняется.";
            return;
        }
        var title = string.IsNullOrWhiteSpace(NoteTitle)
            ? NoteBody.Trim().Split('\n', 2)[0][..Math.Min(80, NoteBody.Trim().Split('\n', 2)[0].Length)]
            : NoteTitle.Trim();
        var id = await _service.SaveNoteAsync(SelectedNote?.Id ?? 0, title, NoteBody).ConfigureAwait(true);
        await ReloadNotesAsync().ConfigureAwait(true);
        SelectedNote = Notes.FirstOrDefault(note => note.Id == id);
        Status = "Заметка сохранена в SQLite.";
    });

    public ICommand DeleteNoteCommand => new RelayCommand(async () =>
    {
        if (SelectedNote is null) return;
        if (!await _confirm("Удалить заметку?", $"«{SelectedNote.Title}» будет удалена без возможности восстановления.").ConfigureAwait(true)) return;
        await _service.DeleteNoteAsync(SelectedNote.Id).ConfigureAwait(true);
        await ReloadNotesAsync().ConfigureAwait(true);
        SelectedNote = null;
        Status = "Заметка удалена.";
    });

    private async Task ReloadNotesAsync()
    {
        var rows = await _service.LoadNotesAsync().ConfigureAwait(true);
        Notes.Clear();
        foreach (var row in rows) Notes.Add(row);
    }

    // Tasks -----------------------------------------------------------------
    public string TaskTitle { get => _taskTitle; set => Set(ref _taskTitle, value ?? string.Empty); }
    public ICommand AddTaskCommand => new RelayCommand(async () =>
    {
        if (string.IsNullOrWhiteSpace(TaskTitle)) { Status = "Введите задачу."; return; }
        await _service.AddTaskAsync(TaskTitle).ConfigureAwait(true);
        TaskTitle = string.Empty;
        await ReloadTasksAsync().ConfigureAwait(true);
        Status = "Задача добавлена.";
    });
    public ICommand ToggleTaskCommand => new RelayCommand(async parameter =>
    {
        if (parameter is not TaskItem task) return;
        await _service.SetTaskCompletedAsync(task.Id, task.IsCompleted).ConfigureAwait(true);
        Status = task.IsCompleted ? "Задача выполнена." : "Задача снова активна.";
    });
    public ICommand DeleteTaskCommand => new RelayCommand(async parameter =>
    {
        if (parameter is not TaskItem task) return;
        if (!await _confirm("Удалить задачу?", task.Title).ConfigureAwait(true)) return;
        await _service.DeleteTaskAsync(task.Id).ConfigureAwait(true);
        await ReloadTasksAsync().ConfigureAwait(true);
        Status = "Задача удалена.";
    });
    private async Task ReloadTasksAsync()
    {
        var rows = await _service.LoadTasksAsync().ConfigureAwait(true);
        Tasks.Clear();
        foreach (var row in rows) Tasks.Add(row);
    }

    // Workspaces ------------------------------------------------------------
    public WorkspaceItem? SelectedWorkspace
    {
        get => _selectedWorkspace;
        set
        {
            if (!Set(ref _selectedWorkspace, value)) return;
            WorkspaceName = value?.Name ?? string.Empty;
            WorkspaceColor = value?.Color ?? "#8B5CF6";
        }
    }
    public string WorkspaceName { get => _workspaceName; set => Set(ref _workspaceName, value ?? string.Empty); }
    public string WorkspaceColor { get => _workspaceColor; set => Set(ref _workspaceColor, value ?? "#8B5CF6"); }

    public ICommand NewWorkspaceCommand => new RelayCommand(() =>
    {
        SelectedWorkspace = null;
        WorkspaceName = "Новое пространство";
        WorkspaceColor = "#8B5CF6";
    });
    public ICommand SaveWorkspaceCommand => new RelayCommand(async () =>
    {
        if (string.IsNullOrWhiteSpace(WorkspaceName)) { Status = "Введите имя пространства."; return; }
        var tabs = _captureTabs();
        var id = await _service.SaveWorkspaceAsync(SelectedWorkspace?.Id ?? 0, WorkspaceName, WorkspaceColor, tabs).ConfigureAwait(true);
        await ReloadWorkspacesAsync().ConfigureAwait(true);
        SelectedWorkspace = Workspaces.FirstOrDefault(workspace => workspace.Id == id);
        Status = $"Пространство сохранено: {tabs.Count} вкладок.";
    });
    public ICommand OpenWorkspaceCommand => new RelayCommand(async parameter =>
    {
        var workspace = parameter as WorkspaceItem ?? SelectedWorkspace;
        if (workspace is null) return;
        if (!await _confirm("Переключить пространство?", "Текущая сессия вкладок будет заменена вкладками «" + workspace.Name + "». Сначала сохраните её, если нужно.").ConfigureAwait(true)) return;
        await _restoreWorkspace(workspace).ConfigureAwait(true);
        Status = "Открыто пространство «" + workspace.Name + "».";
    });
    public ICommand DeleteWorkspaceCommand => new RelayCommand(async () =>
    {
        if (SelectedWorkspace is null) return;
        if (!await _confirm("Удалить пространство?", "«" + SelectedWorkspace.Name + "» и его снимок вкладок будут удалены.").ConfigureAwait(true)) return;
        await _service.DeleteWorkspaceAsync(SelectedWorkspace.Id).ConfigureAwait(true);
        await ReloadWorkspacesAsync().ConfigureAwait(true);
        SelectedWorkspace = null;
        Status = "Пространство удалено.";
    });
    private async Task ReloadWorkspacesAsync()
    {
        var rows = await _service.LoadWorkspacesAsync().ConfigureAwait(true);
        Workspaces.Clear();
        foreach (var row in rows) Workspaces.Add(row);
    }

    // DrTools ---------------------------------------------------------------
    public string CalculatorInput { get => _calculatorInput; set => Set(ref _calculatorInput, value ?? string.Empty); }
    public string CalculatorResult { get => _calculatorResult; private set => Set(ref _calculatorResult, value); }
    public ICommand CalculateCommand => new RelayCommand(() =>
    {
        try { CalculatorResult = UtilityEngine.Calculate(CalculatorInput).ToString("G15", CultureInfo.CurrentCulture); }
        catch (Exception ex) { CalculatorResult = "Ошибка: " + ex.Message; }
    });

    public string JsonInput { get => _jsonInput; set => Set(ref _jsonInput, value ?? string.Empty); }
    public string JsonOutput { get => _jsonOutput; private set => Set(ref _jsonOutput, value); }
    public ICommand FormatJsonCommand => new RelayCommand(parameter =>
    {
        try { JsonOutput = UtilityEngine.FormatJson(JsonInput, parameter as string == "minify"); }
        catch (Exception ex) { JsonOutput = "Ошибка JSON: " + ex.Message; }
    });

    public string UnitValue { get => _unitValue; set => Set(ref _unitValue, value ?? string.Empty); }
    public string UnitFrom { get => _unitFrom; set => Set(ref _unitFrom, value ?? "m"); }
    public string UnitTo { get => _unitTo; set => Set(ref _unitTo, value ?? "km"); }
    public string UnitResult { get => _unitResult; private set => Set(ref _unitResult, value); }
    public ICommand ConvertUnitCommand => new RelayCommand(() =>
    {
        try
        {
            if (!double.TryParse(UnitValue.Replace(',', '.'), NumberStyles.Float, CultureInfo.InvariantCulture, out var value))
                throw new FormatException("Введите число.");
            UnitResult = UtilityEngine.ConvertUnit(value, UnitFrom, UnitTo).ToString("G12", CultureInfo.CurrentCulture) + " " + UnitTo;
        }
        catch (Exception ex) { UnitResult = "Ошибка: " + ex.Message; }
    });

    public int PasswordLength { get => _passwordLength; set => Set(ref _passwordLength, Math.Clamp(value, 8, 64)); }
    public string GeneratedPassword { get => _generatedPassword; private set => Set(ref _generatedPassword, value); }
    public ICommand GeneratePasswordCommand => new RelayCommand(() => GeneratedPassword = PasswordService.Generate(PasswordLength));
    public ICommand CopyGeneratedPasswordCommand => new RelayCommand(() => Copy(GeneratedPassword, "Пароль скопирован."));

    public string TextInput { get => _textInput; set => Set(ref _textInput, value ?? string.Empty); }
    public string TextOutput { get => _textOutput; private set => Set(ref _textOutput, value); }
    public ICommand ConvertTextCommand => new RelayCommand(parameter => TextOutput = (parameter as string) switch
    {
        "upper" => TextInput.ToUpper(CultureInfo.CurrentCulture),
        "lower" => TextInput.ToLower(CultureInfo.CurrentCulture),
        "trim" => string.Join(" ", TextInput.Split((char[]?)null, StringSplitOptions.RemoveEmptyEntries)),
        _ => TextInput
    });

    public string ColorInput { get => _colorInput; set => Set(ref _colorInput, value ?? string.Empty); }
    public string ColorOutput { get => _colorOutput; private set => Set(ref _colorOutput, value); }
    public ICommand ConvertColorCommand => new RelayCommand(() =>
    {
        try { ColorOutput = UtilityEngine.HexToRgb(ColorInput); }
        catch (Exception ex) { ColorOutput = "Ошибка: " + ex.Message; }
    });

    public ICommand CopyToolOutputCommand => new RelayCommand(parameter => Copy(parameter as string ?? string.Empty, "Результат скопирован."));

    // Performance / gaming --------------------------------------------------
    public string CpuText { get; private set; } = "—";
    public string RamText { get; private set; } = "—";
    public string ManagedMemoryText { get; private set; } = "—";
    public string WebViewProcessText { get; private set; } = "—";
    public string BrowserTabsText { get; private set; } = "—";
    public string RuntimeText { get; private set; } = "—";
    public ICommand RefreshPerformanceCommand => new RelayCommand(RefreshPerformance);

    public void RefreshPerformance()
    {
        try
        {
            using var process = Process.GetCurrentProcess();
            var now = DateTime.Now;
            var total = process.TotalProcessorTime;
            var cpu = _lastCpuAt == default ? 0 : (total - _lastCpu).TotalMilliseconds /
                Math.Max(1, (now - _lastCpuAt).TotalMilliseconds * Environment.ProcessorCount) * 100;
            _lastCpu = total;
            _lastCpuAt = now;
            CpuText = $"{Math.Clamp(cpu, 0, 100):0.0}%";
            RamText = FormatBytes(process.WorkingSet64);
            ManagedMemoryText = FormatBytes(GC.GetTotalMemory(false));
            var webViewProcesses = Process.GetProcessesByName("msedgewebview2");
            WebViewProcessText = webViewProcesses.Length.ToString(CultureInfo.CurrentCulture);
            foreach (var child in webViewProcesses) child.Dispose();
            BrowserTabsText = _tabCount().ToString(CultureInfo.CurrentCulture);
            RuntimeText = _runtimeText();
            Raise(nameof(CpuText), nameof(RamText), nameof(ManagedMemoryText), nameof(WebViewProcessText), nameof(BrowserTabsText), nameof(RuntimeText));
            Status = "Показатели обновлены в " + now.ToString("HH:mm:ss");
        }
        catch (Exception ex)
        {
            _log.Warn("performance refresh failed: " + ex.Message);
            Status = "Не удалось прочитать показатели: " + ex.Message;
        }
    }

    public ICommand OpenGamingLinkCommand => new RelayCommand(parameter =>
    {
        if (parameter is string url) _navigate(url);
    });
    public ICommand EnableGamingModeCommand => new RelayCommand(() =>
    {
        _enableGamingMode();
        Status = "Gaming Mode: тема Gaming и Aggressive Tab Sleep включены.";
    });

    private void Copy(string value, string success)
    {
        if (string.IsNullOrEmpty(value)) return;
        try { Clipboard.SetText(value); Status = success; }
        catch (Exception ex) { Status = "Буфер обмена недоступен: " + ex.Message; }
    }

    private static string FormatBytes(long bytes)
        => bytes < 1024 * 1024 ? $"{bytes / 1024d:0.0} KB" : $"{bytes / 1024d / 1024:0.0} MB";
}
