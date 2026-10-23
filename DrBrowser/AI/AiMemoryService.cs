namespace DrBrowser;

public sealed class AiMemoryItem : ObservableObject
{
    public long Id { get; init; }
    public string Category { get; init; } = "user";
    public string Content { get; init; } = string.Empty;
    public bool Enabled { get; init; }
    public DateTime UpdatedAt { get; init; }
    public string UpdatedText => UpdatedAt.ToLocalTime().ToString("dd.MM.yyyy HH:mm");
}

/// <summary>
/// Explicit local memory for DrMind. Nothing is inferred or saved automatically: only text the
/// user deliberately submits reaches this table. Enabled entries may be added to a provider prompt.
/// </summary>
public sealed class AiMemoryService
{
    private readonly Db _db;

    public AiMemoryService(Db db) => _db = db;

    public Task<IReadOnlyList<AiMemoryItem>> LoadAsync(int limit = 50) => Task.Run<IReadOnlyList<AiMemoryItem>>(() =>
    {
        using var connection = _db.Connect();
        using var command = connection.CreateCommand();
        command.CommandText = "SELECT id, category, content, enabled, updated_at FROM ai_memory ORDER BY updated_at DESC LIMIT $limit;";
        command.Parameters.AddWithValue("$limit", Math.Clamp(limit, 1, 200));
        using var reader = command.ExecuteReader();
        var result = new List<AiMemoryItem>();
        while (reader.Read())
        {
            result.Add(new AiMemoryItem
            {
                Id = reader.GetInt64(0),
                Category = reader.GetString(1),
                Content = reader.GetString(2),
                Enabled = reader.GetInt64(3) != 0,
                UpdatedAt = DateTime.TryParse(reader.GetString(4), out var updated) ? updated : DateTime.Now
            });
        }
        return result;
    });

    public Task<long> AddAsync(string content, string category = "user") => Task.Run(() =>
    {
        content = Normalize(content, 1000);
        category = Normalize(category, 40);
        if (content.Length == 0) throw new ArgumentException("Введите факт или предпочтение для памяти.");
        if (category.Length == 0) category = "user";
        using var connection = _db.Connect();
        using var command = connection.CreateCommand();
        command.CommandText = "INSERT INTO ai_memory(category, content, enabled, created_at, updated_at) VALUES($category, $content, 1, $now, $now); SELECT last_insert_rowid();";
        command.Parameters.AddWithValue("$category", category);
        command.Parameters.AddWithValue("$content", content);
        command.Parameters.AddWithValue("$now", DateTime.UtcNow.ToString("O"));
        return (long)(command.ExecuteScalar() ?? 0L);
    });

    public Task DeleteAsync(long id) => ExecuteAsync("DELETE FROM ai_memory WHERE id=$id;", id);

    public Task ClearAsync() => Task.Run(() =>
    {
        using var connection = _db.Connect();
        using var command = connection.CreateCommand();
        command.CommandText = "DELETE FROM ai_memory;";
        command.ExecuteNonQuery();
    });

    public string BuildPromptContext(int maxCharacters = 3000)
    {
        using var connection = _db.Connect();
        using var command = connection.CreateCommand();
        command.CommandText = "SELECT content FROM ai_memory WHERE enabled=1 ORDER BY updated_at DESC LIMIT 20;";
        using var reader = command.ExecuteReader();
        var lines = new List<string>();
        var length = 0;
        while (reader.Read())
        {
            var line = "- " + reader.GetString(0);
            if (length + line.Length > maxCharacters) break;
            lines.Add(line);
            length += line.Length + 1;
        }
        return string.Join("\n", lines);
    }

    private Task ExecuteAsync(string sql, long id) => Task.Run(() =>
    {
        using var connection = _db.Connect();
        using var command = connection.CreateCommand();
        command.CommandText = sql;
        command.Parameters.AddWithValue("$id", id);
        command.ExecuteNonQuery();
    });

    private static string Normalize(string? value, int max)
    {
        var text = (value ?? string.Empty).Trim();
        return text.Length <= max ? text : text[..max];
    }
}
