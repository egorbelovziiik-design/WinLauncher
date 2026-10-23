using System.Collections.Concurrent;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;

namespace StealAnEggUltimate;

public sealed class BridgeCommand
{
    public int Id { get; set; }
    public string Action { get; set; } = string.Empty;
    public double X { get; set; }
    public double Y { get; set; }
    public double Z { get; set; }
    public long CreatedAt { get; set; }
}

/// <summary>
/// Desired hub state that the Luau bridge pulls every poll. Continuous flags make
/// the bridge resilient: it re-applies the state even if a request is lost.
/// </summary>
public sealed class BridgeStateDoc
{
    public bool Ok { get; set; } = true;
    public string App { get; set; } = AppInfo.ProductName;
    public string Version { get; set; } = AppInfo.Version;
    public long Time { get; set; }
    public string Player { get; set; } = string.Empty;

    public bool Speed { get; set; }
    public double WalkSpeed { get; set; } = 16;
    public bool JumpPower { get; set; }
    public double JumpPowerValue { get; set; } = 50;
    public bool Fly { get; set; }
    public double FlySpeed { get; set; } = 40;
    public bool Noclip { get; set; }
    public bool InfiniteJump { get; set; }

    public string FlyKey { get; set; } = "F";
    public string SpeedKey { get; set; } = "X";
    public string TeleportKey { get; set; } = "T";

    public bool AutoCollect { get; set; }
    public bool AutoReturn { get; set; }
    public bool AutoFarm { get; set; }
    public bool AutoSteal { get; set; }
    public bool AutoBuy { get; set; }
    public bool AutoSell { get; set; }
    public bool AutoEggFinder { get; set; }
    public bool AutoRareTracker { get; set; }

    public int IntervalMs { get; set; } = 1500;
    public string Target { get; set; } = "Nearest";
    public string RarityPriority { get; set; } = "Rare first";
    public bool Notify { get; set; } = true;

    public List<BridgeCommand> Commands { get; set; } = new();
}

public sealed record BridgeSnapshotResult(EggSnapshot Snapshot, string RemoteAddress);

/// <summary>
/// Tiny loopback HTTP server (TcpListener + HTTP/1.1) used as a read/write bridge
/// with the bundled Roblox test place. It binds 127.0.0.1 only, accepts GET /state,
/// POST /snapshot and POST /event, and never touches the Roblox process.
/// </summary>
public sealed class BridgeServer : IDisposable
{
    private const int MaxBodyBytes = 2 * 1024 * 1024;

    private readonly Logger _log;
    private readonly object _sync = new();
    private readonly ConcurrentDictionary<int, DateTime> _pendingCommands = new();
    private TcpListener? _listener;
    private CancellationTokenSource? _cts;
    private Task? _loop;
    private int _commandId;
    private BridgeStateDoc _state = new();
    private bool _disposed;

    public BridgeServer(Logger log)
    {
        _log = log;
    }

    public int Port { get; private set; } = 8787;
    public bool IsRunning { get; private set; }
    public string Endpoint => $"http://127.0.0.1:{Port}";
    public DateTime? LastSnapshotUtc { get; private set; }
    public string? LastRemoteAddress { get; private set; }
    public long SnapshotCount { get; private set; }
    public string? LastError { get; private set; }

    public event EventHandler<BridgeSnapshotResult>? SnapshotReceived;
    public event EventHandler<(string Name, JsonObject? Payload)>? EventReceived;

    public BridgeStateDoc State
    {
        get
        {
            lock (_sync) return _state;
        }
    }

    public void Start(int port)
    {
        if (_disposed) return;
        Stop();

        Port = port;

        try
        {
            _cts = new CancellationTokenSource();
            _listener = new TcpListener(IPAddress.Loopback, Port);
            _listener.Start();
            IsRunning = true;
            LastError = null;
            _loop = Task.Run(() => AcceptLoopAsync(_listener, _cts.Token));
            _log.Info($"Bridge listening on {Endpoint} (loopback only).");
        }
        catch (Exception ex)
        {
            IsRunning = false;
            LastError = ex.Message;
            _log.Error($"Bridge could not listen on port {Port}.", ex);
        }
    }

    public void Stop()
    {
        try
        {
            _cts?.Cancel();
            _listener?.Stop();
        }
        catch
        {
            // ignore shutdown races
        }

        _listener = null;
        _cts?.Dispose();
        _cts = null;
        _loop = null;
        IsRunning = false;
        _log.Info("Bridge stopped.");
    }

    /// <summary>Publishes the latest desired state (also mirrored to bridge/state.json).</summary>
    public void PublishState(BridgeStateDoc state)
    {
        lock (_sync)
        {
            var now = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();

            // Drop commands the bridge never acknowledged (offline bridge, dropped request).
            foreach (var expired in _pendingCommands
                         .Where(p => (DateTime.UtcNow - p.Value).TotalSeconds > 20)
                         .Select(p => p.Key)
                         .ToList())
            {
                _pendingCommands.TryRemove(expired, out _);
                state.Commands.RemoveAll(c => c.Id == expired);
            }

            // Keep commands that were queued but not yet acknowledged by the bridge.
            var pending = _state.Commands
                .Where(c => _pendingCommands.ContainsKey(c.Id))
                .ToList();

            foreach (var command in state.Commands)
            {
                if (pending.All(c => c.Id != command.Id)) pending.Add(command);
            }

            state.Commands = pending;
            state.Time = now;
            _state = state;
        }

        try
        {
            AppPaths.EnsureCreated();
            File.WriteAllText(Path.Combine(AppPaths.BridgeDir, "state.json"), SettingsJson.Serialize(state));
        }
        catch (Exception ex)
        {
            _log.Debug($"Could not mirror bridge state: {ex.Message}");
        }
    }

    public BridgeCommand Enqueue(string action, double x = 0, double y = 0, double z = 0)
    {
        var command = new BridgeCommand
        {
            Id = Interlocked.Increment(ref _commandId),
            Action = action,
            X = x,
            Y = y,
            Z = z,
            CreatedAt = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
        };

        lock (_sync)
        {
            _state.Commands.Add(command);
            if (_state.Commands.Count > 40) _state.Commands.RemoveRange(0, _state.Commands.Count - 40);
        }

        _pendingCommands[command.Id] = DateTime.UtcNow;
        _log.Info($"Command queued for official place: {action} #{command.Id}.");
        return command;
    }

    private async Task AcceptLoopAsync(TcpListener listener, CancellationToken token)
    {
        while (!token.IsCancellationRequested)
        {
            try
            {
                var client = await listener.AcceptTcpClientAsync(token).ConfigureAwait(false);
                _ = Task.Run(() => HandleClientAsync(client, token), CancellationToken.None);
            }
            catch (OperationCanceledException)
            {
                break;
            }
            catch (Exception ex) when (ex is SocketException or ObjectDisposedException)
            {
                break;
            }
            catch (Exception ex)
            {
                LastError = ex.Message;
                _log.Error("Bridge accept failed.", ex);
                await Task.Delay(200, CancellationToken.None).ConfigureAwait(false);
            }
        }
    }

    private async Task HandleClientAsync(TcpClient client, CancellationToken token)
    {
        using var _ = client;
        try
        {
            client.ReceiveTimeout = 5000;
            client.SendTimeout = 5000;
            using var stream = client.GetStream();

            var request = await ReadRequestAsync(stream, token).ConfigureAwait(false);
            if (request is null) return;

            var (method, path, body) = request.Value;
            var route = path.Split('?')[0].TrimEnd('/').ToLowerInvariant();

            if (method == "OPTIONS")
            {
                await WriteResponseAsync(stream, 204, "{}").ConfigureAwait(false);
                return;
            }

            switch (method, route)
            {
                case ("GET", "/ping"):
                    await WriteResponseAsync(stream, 200, SettingsJson.Serialize(new
                    {
                        ok = true,
                        app = AppInfo.ProductName,
                        version = AppInfo.Version,
                        time = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
                    })).ConfigureAwait(false);
                    break;

                case ("GET", "/state"):
                    await WriteResponseAsync(stream, 200, SettingsJson.Serialize(State)).ConfigureAwait(false);
                    break;

                case ("POST", "/snapshot"):
                    HandleSnapshot(body, client);
                    await WriteResponseAsync(stream, 200, "{\"ok\":true}").ConfigureAwait(false);
                    break;

                case ("POST", "/event"):
                    HandleEvent(body);
                    await WriteResponseAsync(stream, 200, "{\"ok\":true}").ConfigureAwait(false);
                    break;

                case ("GET", ""):
                case ("GET", "/"):
                    await WriteResponseAsync(stream, 200, SettingsJson.Serialize(new
                    {
                        ok = true,
                        app = AppInfo.ProductName,
                        version = AppInfo.Version,
                        routes = new[] { "GET /ping", "GET /state", "POST /snapshot", "POST /event" }
                    })).ConfigureAwait(false);
                    break;

                default:
                    await WriteResponseAsync(stream, 404, "{\"ok\":false,\"error\":\"not found\"}").ConfigureAwait(false);
                    break;
            }
        }
        catch (Exception ex)
        {
            LastError = ex.Message;
            _log.Debug($"Bridge request failed: {ex.Message}");
        }
    }

    private void HandleSnapshot(string body, TcpClient client)
    {
        try
        {
            var snapshot = SettingsJson.Deserialize<EggSnapshot>(body);
            if (snapshot is null) return;

            LastSnapshotUtc = DateTime.UtcNow;
            SnapshotCount++;
            LastRemoteAddress = client.Client.RemoteEndPoint?.ToString();
            snapshot.Time = snapshot.Time == 0
                ? DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
                : snapshot.Time;

            AppPaths.EnsureCreated();
            TryWriteAtomically(Path.Combine(AppPaths.BridgeDir, "eggs.json"), SettingsJson.Serialize(snapshot));

            _log.Info($"Snapshot received from {LastRemoteAddress}: {snapshot.Eggs?.Count ?? 0} eggs, player '{snapshot.Player}'.");
            SnapshotReceived?.Invoke(this, new BridgeSnapshotResult(snapshot, LastRemoteAddress ?? "loopback"));
        }
        catch (Exception ex)
        {
            LastError = ex.Message;
            _log.Error("Bridge snapshot payload was invalid.", ex);
        }
    }

    private void HandleEvent(string body)
    {
        try
        {
            var node = JsonNode.Parse(body);
            if (node is not JsonObject obj) return;

            var name = obj["name"]?.GetValue<string>() ?? "event";
            if (name.Equals("ack", StringComparison.OrdinalIgnoreCase) && obj["id"] is JsonNode idNode)
            {
                var id = idNode.GetValue<int>();
                _pendingCommands.TryRemove(id, out _);
                lock (_sync) _state.Commands.RemoveAll(c => c.Id == id);
                return;
            }

            _log.Info($"Bridge event: {name}");
            EventReceived?.Invoke(this, (name, obj));
        }
        catch (Exception ex)
        {
            _log.Error("Bridge event payload was invalid.", ex);
        }
    }

    private static void TryWriteAtomically(string path, string content)
    {
        try
        {
            var temp = path + ".tmp";
            File.WriteAllText(temp, content);
            File.Move(temp, path, overwrite: true);
        }
        catch
        {
            try
            {
                File.WriteAllText(path, content);
            }
            catch
            {
                // best effort mirror
            }
        }
    }

    private static async Task<(string Method, string Path, string Body)?> ReadRequestAsync(NetworkStream stream, CancellationToken token)
    {
        var buffer = new byte[8192];
        using var ms = new MemoryStream();
        var headerEnd = -1;

        while (true)
        {
            var read = await stream.ReadAsync(buffer.AsMemory(0, buffer.Length), token).ConfigureAwait(false);
            if (read <= 0) return null;

            ms.Write(buffer, 0, read);
            var data = ms.GetBuffer().AsSpan(0, (int)ms.Length);
            headerEnd = data.IndexOf("\r\n\r\n"u8);
            if (headerEnd >= 0 || ms.Length > 64 * 1024) break;
        }

        if (headerEnd < 0) return null;

        var headerText = Encoding.ASCII.GetString(ms.GetBuffer(), 0, headerEnd);
        var lines = headerText.Split("\r\n");
        var requestLine = lines.FirstOrDefault()?.Split(' ');
        if (requestLine is null || requestLine.Length < 2) return null;

        var method = requestLine[0].ToUpperInvariant();
        var path = requestLine[1];
        var contentLength = 0;
        foreach (var line in lines.Skip(1))
        {
            var idx = line.IndexOf(':');
            if (idx <= 0) continue;
            if (line[..idx].Trim().Equals("Content-Length", StringComparison.OrdinalIgnoreCase)
                && int.TryParse(line[(idx + 1)..].Trim(), out var length))
            {
                contentLength = Math.Clamp(length, 0, MaxBodyBytes);
            }
        }

        var bodyBytes = new List<byte>();
        var alreadyRead = (int)ms.Length - (headerEnd + 4);
        if (alreadyRead > 0)
        {
            var arr = ms.GetBuffer();
            bodyBytes.AddRange(arr.AsSpan(headerEnd + 4, alreadyRead).ToArray());
        }

        while (bodyBytes.Count < contentLength)
        {
            var want = Math.Min(buffer.Length, contentLength - bodyBytes.Count);
            var read = await stream.ReadAsync(buffer.AsMemory(0, want), token).ConfigureAwait(false);
            if (read <= 0) break;
            bodyBytes.AddRange(buffer.AsSpan(0, read).ToArray());
        }

        var body = bodyBytes.Count == 0 ? string.Empty : Encoding.UTF8.GetString(bodyBytes.ToArray());
        return (method, path, body);
    }

    private static async Task WriteResponseAsync(NetworkStream stream, int status, string json)
    {
        var payload = Encoding.UTF8.GetBytes(json);
        var reason = status switch
        {
            200 => "OK",
            204 => "No Content",
            404 => "Not Found",
            500 => "Internal Server Error",
            _ => "OK"
        };

        var header = $"HTTP/1.1 {status} {reason}\r\n" +
                     "Content-Type: application/json; charset=utf-8\r\n" +
                     "Access-Control-Allow-Origin: *\r\n" +
                     "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                     "Access-Control-Allow-Headers: Content-Type\r\n" +
                     $"Content-Length: {payload.Length}\r\n" +
                     "Connection: close\r\n\r\n";

        var headerBytes = Encoding.ASCII.GetBytes(header);
        await stream.WriteAsync(headerBytes).ConfigureAwait(false);
        if (payload.Length > 0)
            await stream.WriteAsync(payload).ConfigureAwait(false);
        await stream.FlushAsync().ConfigureAwait(false);
    }

    public void Dispose()
    {
        if (_disposed) return;
        _disposed = true;
        Stop();
    }
}
