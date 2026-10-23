using System.Text.Json;

namespace DrBrowser;

public sealed class NoteItem : ObservableObject
{
    private string _title = string.Empty;
    private string _body = string.Empty;
    public long Id { get; set; }
    public string Title { get => _title; set => Set(ref _title, value ?? string.Empty); }
    public string Body { get => _body; set => Set(ref _body, value ?? string.Empty); }
    public DateTime UpdatedAt { get; set; }
    public string UpdatedText => UpdatedAt.ToString("dd.MM.yyyy HH:mm");
}

public sealed class TaskItem : ObservableObject
{
    private bool _isCompleted;
    public long Id { get; set; }
    public string Title { get; set; } = string.Empty;
    public bool IsCompleted { get => _isCompleted; set => Set(ref _isCompleted, value); }
    public DateTime? DueAt { get; set; }
    public string DueText => DueAt?.ToString("dd.MM.yyyy") ?? "без срока";
}

public sealed class WorkspaceItem
{
    public long Id { get; set; }
    public string Name { get; set; } = string.Empty;
    public string Color { get; set; } = "#8B5CF6";
    public List<SessionTab> Tabs { get; set; } = new();
    public DateTime UpdatedAt { get; set; }
    public string Summary => Tabs.Count == 1 ? "1 вкладка" : $"{Tabs.Count} вкладок";
    public string UpdatedText => UpdatedAt.ToString("dd.MM.yyyy HH:mm");
}

/// <summary>SQLite persistence for DrNotes, DrTasks and DrWorkspaces.</summary>
public sealed class ProductivityService
{
    private readonly Db _db;
    private readonly Logger _log;

    public ProductivityService(Db db, Logger log)
    {
        _db = db;
        _log = log;
    }

    public Task<List<NoteItem>> LoadNotesAsync() => Task.Run(() =>
    {
        var result = new List<NoteItem>();
        using var connection = _db.Connect();
        using var command = connection.CreateCommand();
        command.CommandText = "SELECT id,title,body,updated_at FROM notes ORDER BY updated_at DESC;";
        using var reader = command.ExecuteReader();
        while (reader.Read())
        {
            result.Add(new NoteItem
            {
                Id = reader.GetInt64(0),
                Title = reader.GetString(1),
                Body = reader.GetString(2),
                UpdatedAt = DateTime.TryParse(reader.GetString(3), out var at) ? at : DateTime.Now
            });
        }
        return result;
    });

    public Task<long> SaveNoteAsync(long id, string title, string body) => Task.Run(() =>
    {
        var now = DateTime.Now.ToString("O");
        using var connection = _db.Connect();
        using var command = connection.CreateCommand();
        if (id <= 0)
        {
            command.CommandText = "INSERT INTO notes(title,body,created_at,updated_at) VALUES($t,$b,$now,$now); SELECT last_insert_rowid();";
        }
        else
        {
            command.CommandText = "UPDATE notes SET title=$t,body=$b,updated_at=$now WHERE id=$id; SELECT $id;";
            command.Parameters.AddWithValue("$id", id);
        }
        command.Parameters.AddWithValue("$t", title.Trim());
        command.Parameters.AddWithValue("$b", body);
        command.Parameters.AddWithValue("$now", now);
        return Convert.ToInt64(command.ExecuteScalar());
    });

    public Task DeleteNoteAsync(long id) => ExecuteAsync("DELETE FROM notes WHERE id=$id;", id);

    public Task<List<TaskItem>> LoadTasksAsync() => Task.Run(() =>
    {
        var result = new List<TaskItem>();
        using var connection = _db.Connect();
        using var command = connection.CreateCommand();
        command.CommandText = "SELECT id,title,is_completed,due_at FROM tasks ORDER BY is_completed,due_at IS NULL,due_at,created_at DESC;";
        using var reader = command.ExecuteReader();
        while (reader.Read())
        {
            result.Add(new TaskItem
            {
                Id = reader.GetInt64(0),
                Title = reader.GetString(1),
                IsCompleted = reader.GetInt64(2) != 0,
                DueAt = reader.IsDBNull(3) ? null : DateTime.TryParse(reader.GetString(3), out var due) ? due : null
            });
        }
        return result;
    });

    public Task<long> AddTaskAsync(string title, DateTime? due = null) => Task.Run(() =>
    {
        using var connection = _db.Connect();
        using var command = connection.CreateCommand();
        command.CommandText = """
            INSERT INTO tasks(title,is_completed,due_at,created_at,updated_at)
            VALUES($title,0,$due,$now,$now); SELECT last_insert_rowid();
            """;
        command.Parameters.AddWithValue("$title", title.Trim());
        command.Parameters.AddWithValue("$due", due is null ? DBNull.Value : due.Value.ToString("O"));
        command.Parameters.AddWithValue("$now", DateTime.Now.ToString("O"));
        return Convert.ToInt64(command.ExecuteScalar());
    });

    public Task SetTaskCompletedAsync(long id, bool completed) => Task.Run(() =>
    {
        using var connection = _db.Connect();
        using var command = connection.CreateCommand();
        command.CommandText = "UPDATE tasks SET is_completed=$done,updated_at=$now WHERE id=$id;";
        command.Parameters.AddWithValue("$done", completed ? 1 : 0);
        command.Parameters.AddWithValue("$now", DateTime.Now.ToString("O"));
        command.Parameters.AddWithValue("$id", id);
        command.ExecuteNonQuery();
    });

    public Task DeleteTaskAsync(long id) => ExecuteAsync("DELETE FROM tasks WHERE id=$id;", id);

    public async Task<List<WorkspaceItem>> LoadWorkspacesAsync()
    {
        var rows = await Task.Run(QueryWorkspaces).ConfigureAwait(false);
        if (rows.Count > 0) return rows;

        var presets = new[]
        {
            ("Gaming", "#A855F7", new[] { "https://store.steampowered.com/", "https://discord.com/app", "https://www.twitch.tv/" }),
            ("Music", "#EC4899", new[] { "drbrowser://music" }),
            ("Work", "#3B82F6", new[] { "https://github.com/", "drbrowser://notes", "drbrowser://tasks" }),
            ("Study", "#F59E0B", new[] { "https://wikipedia.org/", "drbrowser://search" }),
            ("Entertainment", "#EF4444", new[] { "https://www.youtube.com/", "https://www.twitch.tv/" }),
            ("Personal", "#22C55E", new[] { "drbrowser://newtab" })
        };
        foreach (var (name, color, urls) in presets)
        {
            var tabs = urls.Select(url => new SessionTab { Url = url, Title = UrlParser.FallbackTitle(url) }).ToList();
            await SaveWorkspaceAsync(0, name, color, tabs).ConfigureAwait(false);
        }
        return await Task.Run(QueryWorkspaces).ConfigureAwait(false);
    }

    public Task<long> SaveWorkspaceAsync(long id, string name, string color, IReadOnlyList<SessionTab> tabs) => Task.Run(() =>
    {
        var json = JsonSerializer.Serialize(tabs);
        var now = DateTime.Now.ToString("O");
        using var connection = _db.Connect();
        using var command = connection.CreateCommand();
        if (id <= 0)
        {
            command.CommandText = """
                INSERT INTO workspaces(name,color,tabs_json,settings_json,updated_at)
                VALUES($name,$color,$tabs,'{}',$now); SELECT last_insert_rowid();
                """;
        }
        else
        {
            command.CommandText = """
                UPDATE workspaces SET name=$name,color=$color,tabs_json=$tabs,updated_at=$now WHERE id=$id;
                SELECT $id;
                """;
            command.Parameters.AddWithValue("$id", id);
        }
        command.Parameters.AddWithValue("$name", name.Trim());
        command.Parameters.AddWithValue("$color", string.IsNullOrWhiteSpace(color) ? "#8B5CF6" : color);
        command.Parameters.AddWithValue("$tabs", json);
        command.Parameters.AddWithValue("$now", now);
        return Convert.ToInt64(command.ExecuteScalar());
    });

    public Task DeleteWorkspaceAsync(long id) => ExecuteAsync("DELETE FROM workspaces WHERE id=$id;", id);

    private List<WorkspaceItem> QueryWorkspaces()
    {
        var result = new List<WorkspaceItem>();
        using var connection = _db.Connect();
        using var command = connection.CreateCommand();
        command.CommandText = "SELECT id,name,color,tabs_json,updated_at FROM workspaces ORDER BY id;";
        using var reader = command.ExecuteReader();
        while (reader.Read())
        {
            List<SessionTab> tabs;
            try { tabs = JsonSerializer.Deserialize<List<SessionTab>>(reader.GetString(3)) ?? new(); }
            catch (JsonException ex) { _log.Warn("workspace JSON ignored: " + ex.Message); tabs = new(); }
            result.Add(new WorkspaceItem
            {
                Id = reader.GetInt64(0),
                Name = reader.GetString(1),
                Color = reader.GetString(2),
                Tabs = tabs,
                UpdatedAt = DateTime.TryParse(reader.GetString(4), out var at) ? at : DateTime.Now
            });
        }
        return result;
    }

    private Task ExecuteAsync(string sql, long id) => Task.Run(() =>
    {
        using var connection = _db.Connect();
        using var command = connection.CreateCommand();
        command.CommandText = sql;
        command.Parameters.AddWithValue("$id", id);
        command.ExecuteNonQuery();
    });
}
