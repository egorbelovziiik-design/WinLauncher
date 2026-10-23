using System.Collections.ObjectModel;
using System.Text.Json;
using System.Windows.Input;

namespace DrBrowser;

public sealed partial class MainViewModel
{
    private string _aiInput = string.Empty;
    private string _aiKeyInput = string.Empty;
    private string _aiStatus = string.Empty;
    private bool _aiBusy;

    public ObservableCollection<ChatMessage> AiMessages { get; } = new();

    public string AiInput
    {
        get => _aiInput;
        set => Set(ref _aiInput, value);
    }

    public string AiKeyInput
    {
        get => _aiKeyInput;
        set => Set(ref _aiKeyInput, value);
    }

    public string AiStatus
    {
        get => _aiStatus;
        set => Set(ref _aiStatus, value);
    }

    public bool IsAiBusy
    {
        get => _aiBusy;
        private set { if (Set(ref _aiBusy, value)) OnPropertyChanged(nameof(AiSendText)); }
    }

    public string AiSendText => IsAiBusy ? "…" : "Отправить";

    /// <summary>Which engine answers right now — shown as a badge in the panel.</summary>
    public string AiEngineText => AI.HasKey ? "DrAI Cloud · ваш API-ключ" : "DrAI Local · офлайн, без ключа";

    public bool AiLocalMode => !AI.HasKey;

    public string AiLocalHint => AI.HasKey
        ? "Ключ сохранён (DPAPI). DrAI Local по-прежнему работает без сети."
        : "Ключа нет — отвечает встроенный офлайн-движок: краткое содержание, вопросы по странице, ключевые слова, разбор кода.";

    public ICommand RunLocalActionCommand => Cmd("ai.local", async p =>
    {
        if (p is not string action) return;
        await RunLocalActionAsync(action);
    });

    public ICommand SendAiCommand => Cmd("sendai", async () => await SendAiAsync(AiInput));

    public ICommand AiActionCommand => Cmd("aiaction", async p =>
    {
        if (p is not string action) return;
        await RunAiActionAsync(action);
    });

    public ICommand ClearAiCommand => Cmd("clearai", () =>
    {
        AiMessages.Clear();
        AiStatus = string.Empty;
    });

    private async Task SendAiAsync(string text)
    {
        if (string.IsNullOrWhiteSpace(text)) return;

        var message = text.Trim();
        AiInput = string.Empty;
        AiMessages.Add(new ChatMessage { Role = "user", Text = message });

        if (!AI.HasKey)
        {
            IsAiOpen = true;
            IsAiBusy = true;
            try
            {
                var pageText = await GetPageTextAsync().ConfigureAwait(true);
                var answer = LocalAi.Answer(message, pageText);
                AiMessages.Add(new ChatMessage { Role = "assistant", Text = answer });
                AiStatus = "DrAI Local: ответ построен по тексту открытой страницы.";
            }
            finally
            {
                IsAiBusy = false;
            }

            return;
        }

        IsAiBusy = true;
        AiStatus = "DrAI думает…";
        try
        {
            var history = AiMessages
                .Where(m => !string.IsNullOrWhiteSpace(m.Text))
                .TakeLast(12)
                .Select(m => new ChatMessage { Role = m.Role, Text = m.Text })
                .ToList();

            var systemPrompt = AIService.PromptFor(Settings.AiPersona);
            if (Settings.AiIncludePage)
            {
                var pageText = await GetPageTextAsync().ConfigureAwait(true);
                if (!string.IsNullOrWhiteSpace(pageText))
                    systemPrompt += "\n\nКонтекст активной страницы «" + ActiveTab?.DisplayTitle +
                                    "» (может быть неполным):\n" + Trim(pageText, 8000);
            }

            var answer = await AI.CompleteAsync(Settings, history, systemPrompt).ConfigureAwait(true);
            AiMessages.Add(new ChatMessage { Role = "assistant", Text = answer });
            AiStatus = string.Empty;
        }
        catch (AiException ex)
        {
            AiMessages.Add(new ChatMessage { Role = "assistant", Text = "⚠ " + ex.Message });
            AiStatus = ex.Message;
        }
        catch (Exception ex)
        {
            _log.Error("ai request failed", ex);
            AiMessages.Add(new ChatMessage { Role = "assistant", Text = "⚠ Ошибка: " + ex.Message });
            AiStatus = ex.Message;
        }
        finally
        {
            IsAiBusy = false;
        }
    }

    /// <summary>Offline actions of DrAI Local — always available, no key, no network.</summary>
    private async Task RunLocalActionAsync(string action)
    {
        IsAiOpen = true;
        var pageText = await GetPageTextAsync().ConfigureAwait(true);
        var selection = await GetSelectionAsync().ConfigureAwait(true);

        try
        {
            var (label, answer) = action switch
            {
                "summarize" => ("Кратко по странице",
                    string.IsNullOrWhiteSpace(pageText)
                        ? "На активной странице нет текста."
                        : LocalAi.Summarize(pageText, 6)),
                "keywords" => ("Ключевые слова",
                    LocalAi.Keywords(string.IsNullOrWhiteSpace(pageText) ? selection : pageText)),
                "stats" => ("Статистика чтения",
                    LocalAi.ReadingStats(pageText)),
                "explain-code" => ("Разбор кода",
                    LocalAi.ExplainCode(string.IsNullOrWhiteSpace(selection) ? pageText : selection)),
                "polish" => ("Чистка текста",
                    LocalAi.Polish(string.IsNullOrWhiteSpace(selection) ? pageText : selection)),
                "ask" => ("Вопрос по странице",
                    LocalAi.Answer(AiInput, pageText)),
                "translate" => ("Перевод", LocalAiEngine.TranslateNotice()),
                _ => ("DrAI Local", "Неизвестное действие.")
            };

            AiMessages.Add(new ChatMessage { Role = "user", Text = "⚡ " + label });
            AiMessages.Add(new ChatMessage { Role = "assistant", Text = answer });
            AiStatus = "DrAI Local · офлайн";
        }
        catch (Exception ex)
        {
            _log.Error("local ai action failed", ex);
            AiStatus = ex.Message;
            AiMessages.Add(new ChatMessage { Role = "assistant", Text = "⚠ " + ex.Message });
        }
    }

    private async Task RunAiActionAsync(string action)
    {
        if (!AI.HasKey)
        {
            // Without a key every action still works — through the built-in local engine.
            await RunLocalActionAsync(action).ConfigureAwait(true);
            return;
        }

        var tab = ActiveTab;
        var pageText = await GetPageTextAsync().ConfigureAwait(true);
        var selection = await GetSelectionAsync().ConfigureAwait(true);

        string prompt;
        string label;

        switch (action)
        {
            case "summarize":
                if (string.IsNullOrWhiteSpace(pageText))
                {
                    AiMessages.Add(new ChatMessage { Role = "assistant", Text = "На активной веб-странице нет текста для краткого содержания." });
                    return;
                }

                label = "Сделай краткое содержание этой страницы";
                prompt = $"Кратко (5–8 пунктов) перескажи страницу «{tab?.DisplayTitle}»:\n\n{Trim(pageText, 14000)}";
                break;

            case "translate":
                if (string.IsNullOrWhiteSpace(selection))
                {
                    AiMessages.Add(new ChatMessage { Role = "assistant", Text = "Выделите текст на странице и повторите перевод." });
                    return;
                }

                label = "Переведи выделенный текст";
                prompt = $"Переведи на русский, сохранив смысл:\n\n{Trim(selection, 6000)}";
                break;

            case "explain-selection":
                if (string.IsNullOrWhiteSpace(selection))
                {
                    AiMessages.Add(new ChatMessage { Role = "assistant", Text = "Сначала выделите фрагмент текста на странице." });
                    return;
                }

                label = "Объясни выделенный фрагмент";
                prompt = $"Объясни простыми словами этот фрагмент:\n\n{Trim(selection, 6000)}";
                break;

            case "explain-code":
                if (string.IsNullOrWhiteSpace(selection))
                {
                    AiMessages.Add(new ChatMessage { Role = "assistant", Text = "Выделите код на странице." });
                    return;
                }

                label = "Объясни код";
                prompt = $"Объясни по шагам, что делает этот код:\n\n```\n{Trim(selection, 6000)}\n```";
                break;

            case "write":
                label = "Помоги с текстом";
                prompt = "Помоги написать короткий деловой текст. Сначала спроси, о чём он должен быть, и предложи план.";
                break;

            default:
                return;
        }

        AiMessages.Add(new ChatMessage { Role = "user", Text = "⚡ " + label });

        IsAiBusy = true;
        AiStatus = "DrAI думает…";
        try
        {
            var answer = await AI.CompleteAsync(Settings,
                new[] { new ChatMessage { Role = "user", Text = prompt } },
                AIService.PromptFor(Settings.AiPersona)).ConfigureAwait(true);
            AiMessages.Add(new ChatMessage { Role = "assistant", Text = answer });
            AiStatus = string.Empty;
        }
        catch (AiException ex)
        {
            AiMessages.Add(new ChatMessage { Role = "assistant", Text = "⚠ " + ex.Message });
            AiStatus = ex.Message;
        }
        catch (Exception ex)
        {
            _log.Error("ai action failed", ex);
            AiMessages.Add(new ChatMessage { Role = "assistant", Text = "⚠ Ошибка: " + ex.Message });
            AiStatus = ex.Message;
        }
        finally
        {
            IsAiBusy = false;
        }
    }

    private async Task<string> GetPageTextAsync()
    {
        var core = ActiveTab?.Core;
        if (core is null) return string.Empty;

        try
        {
            var raw = await core.ExecuteScriptAsync(
                "(function(){ return (document.body ? document.body.innerText : '').slice(0, 20000); })()").ConfigureAwait(true);
            return JsonSerializer.Deserialize<string>(raw) ?? string.Empty;
        }
        catch (Exception ex)
        {
            _log.Warn("page text extraction failed: " + ex.Message);
            return string.Empty;
        }
    }

    private async Task<string> GetSelectionAsync()
    {
        var core = ActiveTab?.Core;
        if (core is null) return string.Empty;

        try
        {
            var raw = await core.ExecuteScriptAsync("(function(){ return String(window.getSelection()); })()").ConfigureAwait(true);
            return JsonSerializer.Deserialize<string>(raw) ?? string.Empty;
        }
        catch
        {
            return string.Empty;
        }
    }

    private static string Trim(string text, int max)
        => string.IsNullOrEmpty(text) ? string.Empty : text.Length <= max ? text : text[..max] + "\n…(обрезано)";
}
