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
    private AiImageAttachment? _aiAttachment;
    private string _aiLinkContext = "Ссылка в вопросе — DrAI прочитает её метаданные";

    /// <summary>Status of the last link DrAI looked up (shown above the input).</summary>
    public string AiLinkContext
    {
        get => _aiLinkContext;
        private set => Set(ref _aiLinkContext, value);
    }

    /// <summary>"🎬 Видео по ссылке": reads a link from the input (or the clipboard) and explains it.</summary>
    public ICommand ExplainVideoLinkCommand => Cmd("ai.video", async () =>
    {
        if (IsAiBusy) return;

        var candidate = AiInput;
        if (!MediaContextService.TryExtractUrl(candidate, out var url))
        {
            try
            {
                var clip = System.Windows.Clipboard.GetText();
                if (MediaContextService.TryExtractUrl(clip, out url)) candidate = clip;
                else
                {
                    AiStatus = "Вставьте ссылку на видео в поле ввода (или скопируйте её в буфер обмена).";
                    return;
                }
            }
            catch
            {
                AiStatus = "Вставьте ссылку на видео в поле ввода.";
                return;
            }
        }

        AiInput = string.Empty;
        AiMessages.Add(new ChatMessage { Role = "user", Text = "🎬 " + url });
        IsAiOpen = true;
        IsAiBusy = true;
        AiStatus = "DrAI: читаю метаданные видео…";

        try
        {
            var media = await MediaContextService.DescribeAsync(url).ConfigureAwait(true);
            AiLinkContext = LocalAiEngine.MediaStatus(media);
            AiMessages.Add(new ChatMessage { Role = "assistant", Text = LocalAi.DescribeMedia(candidate, media) });
            AiStatus = LocalAiEngine.MediaStatus(media);
        }
        catch (Exception ex)
        {
            _log.Error("video link lookup failed", ex);
            AiMessages.Add(new ChatMessage { Role = "assistant", Text = "⚠ Не удалось прочитать ссылку: " + ex.Message });
            AiStatus = ex.Message;
        }
        finally
        {
            IsAiBusy = false;
        }
    });

    // ------------------------------------------------------------------
    // Local models: Ollama / LM Studio need no key and no cloud
    // ------------------------------------------------------------------

    public IReadOnlyList<AiProviderPreset> AiProviders => AIService.Providers;

    private string _aiProbeText = "Проверка ещё не выполнялась.";
    public string AiProbeText
    {
        get => _aiProbeText;
        private set => Set(ref _aiProbeText, value);
    }

    public ObservableCollection<string> AiModels { get; } = new();

    /// <summary>Selected entry of the discovered-model list (writes into Settings.AiModel).</summary>
    public string? SelectedAiModel
    {
        get => string.IsNullOrWhiteSpace(Settings.AiModel) ? null : Settings.AiModel;
        set
        {
            if (string.IsNullOrWhiteSpace(value)) return;
            Settings.AiModel = value;
            OnPropertyChanged();
            Raise(nameof(AiEngineText));
        }
    }

    public string AiProviderHint
    {
        get
        {
            var check = AIService.ValidateEndpoint(AIService.BaseUrlOf(Settings));
            if (!check.Ok) return "⚠ " + check.Message;

            return AIService.IsLocalEndpoint(AIService.BaseUrlOf(Settings))
                ? "Локальная модель: ключ не требуется, запросы не покидают компьютер. Запустите Ollama (ollama serve) или LM Studio с включённым сервером."
                : "Облачный endpoint: нужен ваш API-ключ. Он хранится через DPAPI и в исходниках отсутствует.";
        }
    }

    public ICommand ApplyAiProviderCommand => Cmd("ai.provider", p =>
    {
        if (p is not string id) return;
        var preset = AIService.ProviderFor(id);
        Settings.AiBaseUrl = preset.BaseUrl;
        if (!string.IsNullOrWhiteSpace(preset.Model)) Settings.AiModel = preset.Model;
        AiProbeText = preset.NeedsKey
            ? "Укажите API-ключ в поле ниже — и нажмите «Проверить подключение»."
            : $"Выбрано: {preset.Name}. Запустите сервер моделей и нажмите «Проверить подключение».";
        Raise(nameof(AiProviderHint), nameof(AiEngineText), nameof(AiKeyStatus));
    });

    public ICommand ProbeAiCommand => Cmd("ai.probe", async () =>
    {
        AiProbeText = "Проверяю…";
        var result = await AI.ProbeAsync(Settings).ConfigureAwait(true);

        AiModels.Clear();
        foreach (var model in result.Models) AiModels.Add(model);
        if (result.Models.Count > 0) Raise(nameof(SelectedAiModel));

        if (result.Ok && AiModels.Count > 0 && string.IsNullOrWhiteSpace(Settings.AiModel))
            Settings.AiModel = AiModels[0];

        AiProbeText = result.Ok
            ? $"✅ {result.Message} · {result.ProbedUrl}"
            : $"⚠ {result.Message} · {result.ProbedUrl}";

        Raise(nameof(AiProviderHint), nameof(AiEngineText), nameof(AiLocalMode), nameof(AiLocalHint));
    });

    public string AiAttachmentText => _aiAttachment is null ? "Фото не прикреплено" : "📷 " + _aiAttachment.Name;

    public ICommand AttachAiImageCommand => Cmd("ai.attach", async () =>
    {
        if (IsAiBusy) return;
        if (!AI.CanRun(Settings)) { AiStatus = "Анализ фото требует модель: локальную (например llava в Ollama) или облачную с vision. DrAI Local работает только с текстом."; return; }
        var path = _shell?.PickOpenFile("Изображения PNG/JPEG|*.png;*.jpg;*.jpeg");
        if (path is null) return;
        try
        {
            if (new System.IO.FileInfo(path).Length > AiImageAttachment.MaxBytes)
                throw new ArgumentException("Максимальный размер фото — 5 МБ.");
            _aiAttachment = AiImageAttachment.FromBytes(path, await System.IO.File.ReadAllBytesAsync(path));
            Raise(nameof(AiAttachmentText));
            AiStatus = "Фото будет передано вашему AI endpoint только после нажатия «Отправить». Нужна vision-модель. Видео: прикрепите отдельный кадр PNG/JPEG.";
        }
        catch (Exception ex) { AiStatus = ex.Message; }
    });

    public ICommand RemoveAiImageCommand => Cmd("ai.detach", () =>
    {
        _aiAttachment = null;
        Raise(nameof(AiAttachmentText));
    });

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
    public string AiEngineText =>
        AIService.IsLocalEndpoint(AIService.BaseUrlOf(Settings)) && AI.CanRun(Settings)
            ? "DrAI Local LLM · " + Settings.AiModel
            : AI.HasKey
                ? "DrAI Cloud · ваш API-ключ"
                : "DrAI Local · офлайн, без ключа";

    public bool AiLocalMode => !AI.CanRun(Settings);

    public string AiLocalHint => AIService.IsLocalEndpoint(AIService.BaseUrlOf(Settings)) && AI.CanRun(Settings)
        ? "Локальная модель на этом компьютере: диалог и перевод работают без ключа и без облака."
        : AI.HasKey
            ? "Ключ сохранён (DPAPI). DrAI Local по-прежнему работает без сети."
            : "Ключа нет — отвечает встроенный офлайн-движок: краткое содержание, вопросы по странице, ключевые слова, разбор кода. Подключите Ollama/LM Studio в настройках, чтобы включить свободный диалог.";

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
        _aiAttachment = null;
        Raise(nameof(AiAttachmentText));
        AiStatus = string.Empty;
    });

    private async Task SendAiAsync(string text)
    {
        if (IsAiBusy || (string.IsNullOrWhiteSpace(text) && _aiAttachment is null)) return;
        if (_aiAttachment is not null && !AI.CanRun(Settings)) { AiStatus = "Для анализа фото подключите модель с поддержкой изображений."; return; }

        var message = string.IsNullOrWhiteSpace(text) ? "Что изображено на фото?" : text.Trim();
        AiInput = string.Empty;
        AiMessages.Add(new ChatMessage { Role = "user", Text = message, Image = _aiAttachment });
        _aiAttachment = null;
        Raise(nameof(AiAttachmentText));

        // A link inside the question upgrades the answer: DrAI fetches the real title,
        // author and description (oEmbed / page tags) instead of guessing.
        MediaContext? media = null;
        if (MediaContextService.TryExtractUrl(message, out var mediaUrl))
        {
            AiStatus = "DrAI: читаю метаданные ссылки…";
            try
            {
                media = await MediaContextService.DescribeAsync(mediaUrl).ConfigureAwait(true);
                AiLinkContext = LocalAiEngine.MediaStatus(media);
            }
            catch (Exception ex)
            {
                _log.Warn("media context failed: " + ex.Message);
            }
        }

        if (!AI.CanRun(Settings))
        {
            IsAiOpen = true;
            IsAiBusy = true;
            try
            {
                if (media is not null)
                {
                    var mediaAnswer = LocalAi.DescribeMedia(message, media);
                    AiMessages.Add(new ChatMessage { Role = "assistant", Text = mediaAnswer });
                    AiStatus = LocalAiEngine.MediaStatus(media);
                    return;
                }

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
                .Select(m => new ChatMessage { Role = m.Role, Text = m.Text, Image = m.Image })
                .ToList();

            var systemPrompt = AIService.PromptFor(Settings.AiPersona);
            if (media is not null)
            {
                systemPrompt += "\n\nПользователь спрашивает про ссылку. Достоверные метаданные, полученные браузером:\n"
                                + media.AsPromptBlock()
                                + "\nОтвечай по этим данным; если чего-то нет — скажи об этом прямо.";
            }

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
        if (!AI.CanRun(Settings))
        {
            // Without any model every action still works — through the built-in local engine.
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
