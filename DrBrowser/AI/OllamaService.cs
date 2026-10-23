using System.IO;
using System.Net.Http;
using System.Runtime.CompilerServices;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;

namespace DrBrowser;

public sealed record OllamaStatus(bool Available, string Version, string Message);

/// <summary>Loopback-only Ollama client with model discovery and NDJSON streaming.</summary>
public sealed class OllamaService
{
    private readonly HttpClient _client = new() { Timeout = TimeSpan.FromMinutes(10) };
    private readonly Logger _log;

    public OllamaService(Logger log) => _log = log;

    public async Task<OllamaStatus> CheckAsync(string baseUrl, CancellationToken token = default)
    {
        try
        {
            var root = ValidateRoot(baseUrl);
            using var timeout = CancellationTokenSource.CreateLinkedTokenSource(token);
            timeout.CancelAfter(TimeSpan.FromSeconds(3));
            using var response = await _client.GetAsync(new Uri(root, "/api/version"), timeout.Token).ConfigureAwait(false);
            if (!response.IsSuccessStatusCode)
                return new OllamaStatus(false, string.Empty, $"Ollama вернул HTTP {(int)response.StatusCode}.");
            var json = JsonNode.Parse(await response.Content.ReadAsStringAsync(timeout.Token).ConfigureAwait(false));
            var version = json?["version"]?.GetValue<string>() ?? "unknown";
            return new OllamaStatus(true, version, "Ollama доступен · версия " + version);
        }
        catch (OperationCanceledException) when (!token.IsCancellationRequested)
        {
            return new OllamaStatus(false, string.Empty, "Ollama не ответил за 3 секунды.");
        }
        catch (Exception ex)
        {
            return new OllamaStatus(false, string.Empty, "Ollama недоступен: " + ex.Message);
        }
    }

    public async Task<IReadOnlyList<string>> ListModelsAsync(string baseUrl, CancellationToken token = default)
    {
        var root = ValidateRoot(baseUrl);
        using var response = await _client.GetAsync(new Uri(root, "/api/tags"), token).ConfigureAwait(false);
        if (!response.IsSuccessStatusCode)
            throw new AiException($"Ollama /api/tags: HTTP {(int)response.StatusCode}.");
        var json = JsonNode.Parse(await response.Content.ReadAsStringAsync(token).ConfigureAwait(false));
        return json?["models"]?.AsArray()
                   .Select(node => node?["name"]?.GetValue<string>() ?? string.Empty)
                   .Where(name => name.Length > 0)
                   .Distinct(StringComparer.OrdinalIgnoreCase)
                   .OrderBy(name => name, StringComparer.OrdinalIgnoreCase)
                   .ToArray()
               ?? Array.Empty<string>();
    }

    public async IAsyncEnumerable<string> StreamChatAsync(
        AppSettings settings,
        IEnumerable<ChatMessage> messages,
        string systemPrompt,
        [EnumeratorCancellation] CancellationToken token = default)
    {
        var root = ValidateRoot(settings.OllamaUrl);
        if (string.IsNullOrWhiteSpace(settings.OllamaModel))
            throw new AiException("Выберите локальную модель Ollama в настройках DrMind.");

        var messageArray = new JsonArray();
        if (!string.IsNullOrWhiteSpace(systemPrompt))
            messageArray.Add(new JsonObject { ["role"] = "system", ["content"] = systemPrompt });
        foreach (var message in messages.Where(message => !string.IsNullOrWhiteSpace(message.Text)))
        {
            messageArray.Add(new JsonObject
            {
                ["role"] = message.Role == "assistant" ? "assistant" : "user",
                ["content"] = message.Text
            });
        }

        var payload = new JsonObject
        {
            ["model"] = settings.OllamaModel,
            ["stream"] = true,
            ["messages"] = messageArray,
            ["options"] = new JsonObject
            {
                ["temperature"] = settings.AiTemperature,
                ["num_ctx"] = settings.AiContextSize
            }
        };

        using var request = new HttpRequestMessage(HttpMethod.Post, new Uri(root, "/api/chat"))
        {
            Content = new StringContent(payload.ToJsonString(), Encoding.UTF8, "application/json")
        };

        HttpResponseMessage response;
        try
        {
            response = await _client.SendAsync(request, HttpCompletionOption.ResponseHeadersRead, token).ConfigureAwait(false);
        }
        catch (OperationCanceledException) when (token.IsCancellationRequested)
        {
            throw;
        }
        catch (Exception ex)
        {
            _log.Warn("Ollama request failed: " + ex.Message);
            throw new AiException("Не удалось подключиться к Ollama: " + ex.Message);
        }

        using (response)
        {
            if (!response.IsSuccessStatusCode)
            {
                var error = await response.Content.ReadAsStringAsync(token).ConfigureAwait(false);
                throw new AiException($"Ollama HTTP {(int)response.StatusCode}: {Trim(error)}");
            }

            await using var stream = await response.Content.ReadAsStreamAsync(token).ConfigureAwait(false);
            using var reader = new StreamReader(stream, Encoding.UTF8);
            while (true)
            {
                token.ThrowIfCancellationRequested();
                var line = await reader.ReadLineAsync(token).ConfigureAwait(false);
                if (line is null) break;
                if (line.Length == 0) continue;

                JsonNode? node;
                try { node = JsonNode.Parse(line); }
                catch (JsonException) { continue; }
                var error = node?["error"]?.GetValue<string>();
                if (!string.IsNullOrWhiteSpace(error)) throw new AiException("Ollama: " + error);
                var chunk = node?["message"]?["content"]?.GetValue<string>();
                if (!string.IsNullOrEmpty(chunk)) yield return chunk;
                if (node?["done"]?.GetValue<bool>() == true) break;
            }
        }
    }

    private static Uri ValidateRoot(string baseUrl)
    {
        var value = string.IsNullOrWhiteSpace(baseUrl) ? "http://127.0.0.1:11434" : baseUrl.Trim().TrimEnd('/');
        if (!Uri.TryCreate(value + "/", UriKind.Absolute, out var uri)
            || uri.Scheme is not ("http" or "https")
            || !uri.IsLoopback
            || !string.IsNullOrEmpty(uri.UserInfo))
            throw new AiException("Ollama endpoint должен быть loopback-адресом (http://127.0.0.1:11434). Это ограничение защищает локальный API.");
        return uri;
    }

    private static string Trim(string text) => text.Length <= 400 ? text : text[..400] + "…";
}
