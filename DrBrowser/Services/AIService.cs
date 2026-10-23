using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;

namespace DrBrowser;

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

    public async Task<string> CompleteAsync(
        AppSettings settings,
        IEnumerable<ChatMessage> messages,
        string? systemPrompt = null,
        CancellationToken cancellationToken = default)
    {
        var key = SecretStore.ReadAiKey();
        if (string.IsNullOrWhiteSpace(key))
            throw new AiException("AI-ключ не настроен. Откройте Настройки → AI Assistant и вставьте свой API-ключ (он хранится зашифрованным через Windows DPAPI).");

        var baseUrl = string.IsNullOrWhiteSpace(settings.AiBaseUrl) ? "https://api.openai.com/v1" : settings.AiBaseUrl.TrimEnd('/');
        var endpoint = baseUrl.EndsWith("/chat/completions", StringComparison.OrdinalIgnoreCase)
            ? baseUrl
            : baseUrl + "/chat/completions";

        if (!Uri.TryCreate(endpoint, UriKind.Absolute, out var endpointUri)
            || !string.IsNullOrEmpty(endpointUri.UserInfo)
            || (endpointUri.Scheme != "https" && !(endpointUri.Scheme == "http" && endpointUri.IsLoopback)))
            throw new AiException("AI endpoint должен использовать HTTPS (HTTP разрешён только для localhost).");

        var payload = new JsonObject
        {
            ["model"] = string.IsNullOrWhiteSpace(settings.AiModel) ? "gpt-4o-mini" : settings.AiModel,
            ["temperature"] = settings.AiTemperature
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
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", key);

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
    public static string PromptFor(string? persona) => (persona ?? "chat").ToLowerInvariant() switch
    {
        "brief" => SystemPrompt + " Пиши максимально сжато: не больше 8 пунктов, без вступлений.",
        "search" => SystemPrompt + " Отвечай по контексту DrSearch, ставь ссылки [1], [2] и явно говори, если источников недостаточно.",
        "research" => SystemPrompt + " Сопоставь несколько источников DrSearch, отделяй факты от вывода, отмечай противоречия и неопределённость.",
        "translator" => SystemPrompt + " Ты переводчик: выдавай только перевод, сохраняя форматирование и термины.",
        "coder" => SystemPrompt + " Ты senior-разработчик: объясняй код по шагам, указывай риски и предлагай проверяемые исправления.",
        "study" => SystemPrompt + " Объясняй от простого к сложному, приводи пример и заканчивай коротким вопросом для самопроверки.",
        "writing" => SystemPrompt + " Помогай с текстом: сохраняй смысл, тон и структуру; не добавляй непроверенные факты.",
        "browser" => SystemPrompt + " Помогай с функциями браузера. Не утверждай, что действие выполнено, если инструмент его не выполнял, и проси подтверждение перед изменением данных.",
        "music" => SystemPrompt + " Помогай управлять легальной локальной музыкой и открытыми каталогами; не предлагай обход DRM.",
        _ => SystemPrompt
    };

    public const string SystemPrompt =
        "Ты — DrMind, встроенный помощник DrBrowser OMEGA. Отвечай конкретно и по делу, " +
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
