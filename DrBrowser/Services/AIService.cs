using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;

namespace DrBrowser;

/// <summary>Preset for the AI endpoint (cloud provider or a local LLM server).</summary>
public sealed record AiProviderPreset(string Id, string Name, string BaseUrl, string Model, bool NeedsKey);

/// <summary>Outcome of "Проверить подключение".</summary>
public sealed record AiProbeResult(bool Ok, string Message, IReadOnlyList<string> Models, string ProbedUrl);

public sealed class AiException : Exception
{
    public AiException(string message) : base(message) { }
}

/// <summary>
/// OpenAI-compatible chat client. The API key is supplied by the user, stored encrypted
/// with DPAPI and never embedded in the binary or written to the settings file.
/// </summary>
public sealed class AIService
{
    private static readonly HttpClient Client = new() { Timeout = TimeSpan.FromSeconds(120) };
    private readonly Logger _log;

    public AIService(Logger log) => _log = log;

    public string LastError { get; private set; } = string.Empty;

    public bool HasKey => SecretStore.HasAiKey();
    public string KeyHint => HasKey ? "ключ сохранён (DPAPI)" : "ключ не задан";

    // ------------------------------------------------------------------
    // Providers: cloud (user key) and local LLM servers (no key, no cloud)
    // ------------------------------------------------------------------

    /// <summary>
    /// Presets for the AI endpoint. Local servers speak the same OpenAI-compatible
    /// protocol, so the rest of the pipeline does not change.
    /// </summary>
    public static IReadOnlyList<AiProviderPreset> Providers { get; } = new List<AiProviderPreset>
    {
        new("openai", "OpenAI", "https://api.openai.com/v1", "gpt-4o-mini", true),
        new("ollama", "Ollama (локально)", "http://127.0.0.1:11434/v1", "llama3.1", false),
        new("lmstudio", "LM Studio (локально)", "http://127.0.0.1:1234/v1", "local-model", false),
        new("custom", "Свой endpoint", "https://", string.Empty, true)
    };

    public static AiProviderPreset ProviderFor(string? id)
        => Providers.FirstOrDefault(p => string.Equals(p.Id, id, StringComparison.OrdinalIgnoreCase))
           ?? Providers[^1];

    /// <summary>True for 127.0.0.1 / ::1 / "localhost" — a model running on this machine.</summary>
    public static bool IsLocalEndpoint(string? endpoint)
        => Uri.TryCreate(endpoint, UriKind.Absolute, out var uri) && uri.IsLoopback;

    /// <summary>
    /// Endpoint policy: HTTPS anywhere, plain HTTP only for a loopback address. Returns a
    /// human-readable reason so the UI can explain a rejection instead of silently failing.
    /// </summary>
    public static (bool Ok, string Message) ValidateEndpoint(string? endpoint)
    {
        if (string.IsNullOrWhiteSpace(endpoint)) return (false, "Укажите адрес AI endpoint.");
        if (!Uri.TryCreate(endpoint, UriKind.Absolute, out var uri)) return (false, "Адрес не является абсолютным URL.");
        if (!string.IsNullOrEmpty(uri.UserInfo)) return (false, "Логин и пароль в адресе не допускаются.");
        if (uri.Scheme == Uri.UriSchemeHttps) return (true, IsLocalEndpoint(endpoint) ? "локальный HTTPS" : "HTTPS");
        if (uri.Scheme == Uri.UriSchemeHttp && uri.IsLoopback) return (true, "локальный HTTP (только localhost)");
        return (false, "Нужен HTTPS; HTTP разрешён только для localhost.");
    }

    /// <summary>Models reported by GET {base}/models (OpenAI style) or /api/tags (Ollama). Pure.</summary>
    public static List<string> ParseModelList(string? json)
    {
        var result = new List<string>();
        if (string.IsNullOrWhiteSpace(json)) return result;

        try
        {
            using var doc = JsonDocument.Parse(json);
            var root = doc.RootElement;

            if (root.TryGetProperty("data", out var data) && data.ValueKind == JsonValueKind.Array)
            {
                foreach (var item in data.EnumerateArray())
                {
                    if (item.TryGetProperty("id", out var id) && id.ValueKind == JsonValueKind.String)
                        Add(id.GetString());
                }
            }

            if (root.TryGetProperty("models", out var models) && models.ValueKind == JsonValueKind.Array)
            {
                foreach (var item in models.EnumerateArray())
                {
                    if (item.TryGetProperty("name", out var name) && name.ValueKind == JsonValueKind.String)
                        Add(name.GetString());
                    else if (item.TryGetProperty("model", out var model) && model.ValueKind == JsonValueKind.String)
                        Add(model.GetString());
                }
            }
        }
        catch (JsonException)
        {
            return result;
        }

        return result;

        void Add(string? value)
        {
            if (string.IsNullOrWhiteSpace(value)) return;
            var clean = value.Trim();
            if (!result.Contains(clean, StringComparer.OrdinalIgnoreCase)) result.Add(clean);
        }
    }

    /// <summary>Can this configuration answer without further setup?</summary>
    public bool CanRun(AppSettings settings)
        => IsLocalEndpoint(BaseUrlOf(settings)) || HasKey;

    public static string BaseUrlOf(AppSettings settings)
        => string.IsNullOrWhiteSpace(settings.AiBaseUrl) ? "https://api.openai.com/v1" : settings.AiBaseUrl.TrimEnd('/');

    /// <summary>
    /// Asks the endpoint which models it has. Used by "Проверить подключение" so the user
    /// can pick a real model instead of guessing its name.
    /// </summary>
    public async Task<AiProbeResult> ProbeAsync(AppSettings settings, CancellationToken token = default)
    {
        var baseUrl = BaseUrlOf(settings);
        var check = ValidateEndpoint(baseUrl);
        if (!check.Ok) return new AiProbeResult(false, check.Message, Array.Empty<string>(), baseUrl);

        var modelsUrl = baseUrl.EndsWith("/chat/completions", StringComparison.OrdinalIgnoreCase)
            ? baseUrl[..^"/chat/completions".Length] + "/models"
            : baseUrl + "/models";

        using var request = new HttpRequestMessage(HttpMethod.Get, modelsUrl);
        var key = SecretStore.ReadAiKey();
        if (!string.IsNullOrWhiteSpace(key))
            // Local servers ignore the key, but some require the header to be present.
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer",
            string.IsNullOrWhiteSpace(key) ? "local" : key);

        try
        {
            using var timeout = CancellationTokenSource.CreateLinkedTokenSource(token);
            timeout.CancelAfter(TimeSpan.FromSeconds(8));
            using var response = await Client.SendAsync(request, timeout.Token).ConfigureAwait(true);
            var body = await response.Content.ReadAsStringAsync(timeout.Token).ConfigureAwait(true);

            if (!response.IsSuccessStatusCode)
                return new AiProbeResult(false, $"Сервер ответил HTTP {(int)response.StatusCode}.", Array.Empty<string>(), modelsUrl);

            var models = ParseModelList(body);
            return models.Count == 0
                ? new AiProbeResult(false, "Сервер отвечает, но список моделей пуст — укажите имя модели вручную.", models, modelsUrl)
                : new AiProbeResult(true, $"Доступно моделей: {models.Count}", models, modelsUrl);
        }
        catch (OperationCanceledException)
        {
            return new AiProbeResult(false, "Сервер не ответил за 8 секунд. Запущен ли он?", Array.Empty<string>(), modelsUrl);
        }
        catch (Exception ex)
        {
            _log.Warn("ai probe failed: " + ex.Message);
            return new AiProbeResult(false, "Нет соединения: " + ex.Message, Array.Empty<string>(), modelsUrl);
        }
    }

    public async Task<string> CompleteAsync(
        AppSettings settings,
        IEnumerable<ChatMessage> messages,
        string? systemPrompt = null,
        CancellationToken cancellationToken = default)
    {
        var key = SecretStore.ReadAiKey();
        var local = IsLocalEndpoint(BaseUrlOf(settings));

        if (string.IsNullOrWhiteSpace(key) && !local)
            throw new AiException("AI-ключ не настроен. Откройте Настройки → AI Assistant и вставьте свой API-ключ " +
                                  "(он хранится зашифрованным через Windows DPAPI) — либо переключитесь на локальную модель " +
                                  "Ollama / LM Studio, которой ключ не нужен.");

        var baseUrl = BaseUrlOf(settings);
        var endpoint = baseUrl.EndsWith("/chat/completions", StringComparison.OrdinalIgnoreCase)
            ? baseUrl
            : baseUrl + "/chat/completions";

        var policy = ValidateEndpoint(endpoint);
        if (!policy.Ok) throw new AiException("AI endpoint отклонён: " + policy.Message);

        var payload = new JsonObject
        {
            ["model"] = string.IsNullOrWhiteSpace(settings.AiModel) ? "gpt-4o-mini" : settings.AiModel,
            ["temperature"] = 0.4
        };

        var array = new JsonArray();
        if (!string.IsNullOrWhiteSpace(systemPrompt))
            array.Add(new JsonObject { ["role"] = "system", ["content"] = systemPrompt });

        foreach (var message in messages)
        {
            JsonNode content = JsonValue.Create(message.Text)!;
            if (message.Image is not null && message.Role != "assistant")
            {
                content = new JsonArray
                {
                    new JsonObject { ["type"] = "text", ["text"] = message.Text },
                    new JsonObject { ["type"] = "image_url", ["image_url"] = new JsonObject
                        { ["url"] = message.Image.DataUrl, ["detail"] = "auto" } }
                };
            }
            array.Add(new JsonObject
            {
                ["role"] = message.Role == "assistant" ? "assistant" : "user",
                ["content"] = content
            });
        }

        payload["messages"] = array;

        using var request = new HttpRequestMessage(HttpMethod.Post, endpoint)
        {
            Content = new StringContent(payload.ToJsonString(), Encoding.UTF8, "application/json")
        };
        // Local servers ignore the key, but some require the header to be present.
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer",
            string.IsNullOrWhiteSpace(key) ? "local" : key);

        HttpResponseMessage response;
        try
        {
            response = await Client.SendAsync(request, cancellationToken).ConfigureAwait(true);
        }
        catch (TaskCanceledException)
        {
            throw new AiException("AI-сервер не ответил вовремя (таймаут 120 с).");
        }
        catch (Exception ex)
        {
            _log.Error("ai request failed", ex);
            throw new AiException("Сеть недоступна: " + ex.Message);
        }

        using var responseScope = response;
        var body = await response.Content.ReadAsStringAsync(cancellationToken).ConfigureAwait(true);

        if (!response.IsSuccessStatusCode)
        {
            var message = response.StatusCode switch
            {
                HttpStatusCode.Unauthorized => "Ключ отклонён сервером (401). Проверьте API-ключ в настройках.",
                HttpStatusCode.TooManyRequests => "Слишком много запросов (429). Попробуйте позже.",
                _ => $"Ошибка сервера {(int)response.StatusCode}: {Trim(body)}"
            };
            LastError = message;
            throw new AiException(message);
        }

        try
        {
            var json = JsonNode.Parse(body);
            var content = json?["choices"]?[0]?["message"]?["content"]?.GetValue<string>();
            if (string.IsNullOrWhiteSpace(content))
                throw new AiException("Ответ сервера не содержит текста. " + Trim(body));

            return content.Trim();
        }
        catch (AiException)
        {
            throw;
        }
        catch (Exception ex)
        {
            LastError = ex.Message;
            throw new AiException("Не удалось разобрать ответ сервера: " + ex.Message);
        }
    }

    private static string Trim(string text)
        => text.Length <= 300 ? text : text[..300] + "…";

    /// <summary>Persona-specific system prompt used by the DrAI panel.</summary>
    public static string PromptFor(string? persona) => (persona ?? "assistant").ToLowerInvariant() switch
    {
        "brief" => SystemPrompt + " Пиши максимально сжато: не больше 8 пунктов, без вступлений.",
        "translator" => SystemPrompt + " Ты переводчик: выдавай только перевод, сохраняя форматирование и термины.",
        "coder" => SystemPrompt + " Ты senior-разработчик: объясняй код по шагам, указывай риски и предлагай исправления.",
        _ => SystemPrompt
    };

    public const string SystemPrompt =
        "Ты — DrAI, встроенный помощник браузера DrBrowser. Отвечай кратко и по делу, " +
        "на языке пользователя. Не выдумывай факты; если не знаешь — так и скажи.";

    public Task<string> SummarizeAsync(AppSettings settings, string pageText, string? title, CancellationToken ct = default)
        => CompleteAsync(settings, new[]
        {
            new ChatMessage
            {
                Text = $"Сделай краткое содержание страницы «{title}» в 5–8 пунктах, затем один вывод.\n\nТекст страницы:\n{Clamp(pageText, 12000)}"
            }
        }, SystemPrompt, ct);

    public Task<string> TranslateAsync(AppSettings settings, string text, string target = "русский", CancellationToken ct = default)
        => CompleteAsync(settings, new[]
        {
            new ChatMessage { Text = $"Переведи на {target}, сохранив смысл и форматирование:\n\n{Clamp(text, 8000)}" }
        }, SystemPrompt, ct);

    public Task<string> ExplainCodeAsync(AppSettings settings, string code, CancellationToken ct = default)
        => CompleteAsync(settings, new[]
        {
            new ChatMessage { Text = $"Объясни, что делает этот код, по шагам:\n\n```\n{Clamp(code, 8000)}\n```" }
        }, SystemPrompt, ct);

    private static string Clamp(string text, int max)
        => string.IsNullOrEmpty(text) ? "(пусто)" : text.Length <= max ? text : text[..max] + "\n…(обрезано)";
}
