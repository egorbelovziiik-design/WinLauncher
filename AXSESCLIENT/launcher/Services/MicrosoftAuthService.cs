using System.Diagnostics;
using System.Net;
using System.Net.Http.Headers;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using AxsesClient.Launcher.Models;

namespace AxsesClient.Launcher.Services;

public sealed class MicrosoftAuthService
{
    // Public Desktop Azure AD App Client ID configurable via environment or Settings
    public const string DefaultPublicClientId = "00000000402b5328";
    private const string MsAuthorizeEndpoint = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize";
    private const string MsTokenEndpoint = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private const string XboxLiveAuthUrl = "https://user.auth.xboxlive.com/user/authenticate";
    private const string XstsAuthUrl = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private const string McLoginWithXboxUrl = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private const string McEntitlementsUrl = "https://api.minecraftservices.com/entitlements/mcstore";
    private const string McProfileUrl = "https://api.minecraftservices.com/minecraft/profile";

    private readonly string _vaultDir;
    private readonly string _accountsFilePath;
    private readonly HttpClient _http;
    private readonly List<MinecraftAccountRecord> _accounts = [];

    public MicrosoftAuthService(string rootDataDir, HttpClient? httpClient = null)
    {
        _vaultDir = Path.Combine(rootDataDir, "vault");
        _accountsFilePath = Path.Combine(_vaultDir, "accounts.json");
        _http = httpClient ?? new HttpClient { Timeout = TimeSpan.FromSeconds(30) };
        LoadAccounts();
    }

    public IReadOnlyList<MinecraftAccountRecord> Accounts => _accounts;

    public string EffectiveClientId
    {
        get
        {
            string? env = Environment.GetEnvironmentVariable("AXSES_MS_CLIENT_ID");
            return !string.IsNullOrWhiteSpace(env) ? env.Trim() : DefaultPublicClientId;
        }
    }

    public static (string Verifier, string Challenge) GeneratePkcePair()
    {
        byte[] bytes = RandomNumberGenerator.GetBytes(32);
        string verifier = Base64UrlEncode(bytes);
        byte[] hash = SHA256.HashData(Encoding.ASCII.GetBytes(verifier));
        string challenge = Base64UrlEncode(hash);
        return (verifier, challenge);
    }

    public string BuildBrowserAuthorizeUrl(string redirectUri, string state, string codeChallenge)
    {
        var query = new Dictionary<string, string>
        {
            ["client_id"] = EffectiveClientId,
            ["response_type"] = "code",
            ["redirect_uri"] = redirectUri,
            ["scope"] = "XboxLive.signin offline_access",
            ["state"] = state,
            ["code_challenge"] = codeChallenge,
            ["code_challenge_method"] = "S256",
            ["prompt"] = "select_account"
        };
        string qs = string.Join("&", query.Select(kv => $"{Uri.EscapeDataString(kv.Key)}={Uri.EscapeDataString(kv.Value)}"));
        return $"{MsAuthorizeEndpoint}?{qs}";
    }

    public async Task<MinecraftAccountRecord> SignInWithBrowserAsync(
        Action<string>? statusCallback = null,
        CancellationToken ct = default)
    {
        int port = FindAvailableLoopbackPort();
        string redirectUri = $"http://127.0.0.1:{port}/callback/";
        string state = Base64UrlEncode(RandomNumberGenerator.GetBytes(16));
        var (codeVerifier, codeChallenge) = GeneratePkcePair();
        string authorizeUrl = BuildBrowserAuthorizeUrl(redirectUri, state, codeChallenge);

        using var listener = new HttpListener();
        listener.Prefixes.Add(redirectUri);
        listener.Start();

        statusCallback?.Invoke("Opening default system browser for Microsoft OAuth2 sign-in...");
        OpenSystemBrowser(authorizeUrl);

        HttpListenerContext context;
        await using (ct.Register(() => { try { listener.Stop(); } catch { } }))
        {
            context = await listener.GetContextAsync().ConfigureAwait(false);
        }

        string? returnedState = context.Request.QueryString["state"];
        string? code = context.Request.QueryString["code"];
        string? error = context.Request.QueryString["error"];

        string htmlResponse = string.IsNullOrEmpty(error) && !string.IsNullOrEmpty(code)
            ? "<html><body style='background:#070911;color:#F7F8FF;font-family:Segoe UI,sans-serif;text-align:center;padding-top:80px;'><h2 style='color:#9060FF;'>AXSESCLIENT Authentication Received</h2><p>You may now close this browser tab and return to AXSESCLIENT.</p></body></html>"
            : "<html><body style='background:#070911;color:#FF5B79;font-family:Segoe UI,sans-serif;text-align:center;padding-top:80px;'><h2>Sign-In Cancelled or Failed</h2><p>Return to AXSESCLIENT to retry.</p></body></html>";

        byte[] respBytes = Encoding.UTF8.GetBytes(htmlResponse);
        context.Response.ContentType = "text/html; charset=utf-8";
        context.Response.ContentLength64 = respBytes.Length;
        await context.Response.OutputStream.WriteAsync(respBytes, ct).ConfigureAwait(false);
        context.Response.Close();
        listener.Stop();

        if (!string.IsNullOrEmpty(error))
            throw new InvalidOperationException($"Microsoft OAuth returned error: {error}");
        if (!string.Equals(state, returnedState, StringComparison.Ordinal))
            throw new InvalidOperationException("OAuth state mismatch (CSRF protection triggered).");
        if (string.IsNullOrWhiteSpace(code))
            throw new InvalidOperationException("No authorization code received from browser callback.");

        statusCallback?.Invoke("Exchanging authorization code with Microsoft OAuth2...");
        var (msAccess, msRefresh) = await ExchangeCodeForMicrosoftTokensAsync(code, redirectUri, codeVerifier, ct).ConfigureAwait(false);
        return await CompleteXboxAndMinecraftChainAsync(msAccess, msRefresh, statusCallback, ct).ConfigureAwait(false);
    }

    public async Task<MinecraftAccountRecord> RefreshSessionAsync(
        MinecraftAccountRecord account,
        Action<string>? statusCallback = null,
        CancellationToken ct = default)
    {
        if (account.AccountType == "LocalSandbox")
            return account;

        string refreshToken = SecureTokenVault.Decrypt(account.EncryptedRefreshToken, _vaultDir);
        if (string.IsNullOrWhiteSpace(refreshToken))
            throw new InvalidOperationException("Stored refresh token unavailable; please sign in again via browser.");

        statusCallback?.Invoke($"Refreshing Microsoft OAuth session for {account.Username}...");
        var form = new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["client_id"] = EffectiveClientId,
            ["grant_type"] = "refresh_token",
            ["refresh_token"] = refreshToken,
            ["scope"] = "XboxLive.signin offline_access"
        });

        using var resp = await _http.PostAsync(MsTokenEndpoint, form, ct).ConfigureAwait(false);
        string body = await resp.Content.ReadAsStringAsync(ct).ConfigureAwait(false);
        if (!resp.IsSuccessStatusCode)
            throw new InvalidOperationException("Microsoft session expired; please re-authenticate in browser.");

        using var doc = JsonDocument.Parse(body);
        string msAccess = doc.RootElement.GetProperty("access_token").GetString() ?? "";
        string msRefresh = doc.RootElement.TryGetProperty("refresh_token", out var rt) ? (rt.GetString() ?? refreshToken) : refreshToken;
        SecureTokenVault.RegisterSensitiveToken(msAccess);
        SecureTokenVault.RegisterSensitiveToken(msRefresh);

        return await CompleteXboxAndMinecraftChainAsync(msAccess, msRefresh, statusCallback, ct).ConfigureAwait(false);
    }

    public MinecraftAccountRecord CreateLocalSandboxAccount(string username)
    {
        string clean = SanitizeMinecraftUsername(username);
        byte[] uuidBytes = MD5.HashData(Encoding.UTF8.GetBytes("OfflinePlayer:" + clean));
        uuidBytes[6] = (byte)((uuidBytes[6] & 0x0F) | 0x30);
        uuidBytes[8] = (byte)((uuidBytes[8] & 0x3F) | 0x80);
        string uuid = Convert.ToHexString(uuidBytes).ToLowerInvariant();

        var existing = _accounts.FirstOrDefault(a => a.Uuid == uuid);
        if (existing is not null)
        {
            existing.LastUsedUtc = DateTime.UtcNow;
            SaveAccounts();
            return existing;
        }

        var record = new MinecraftAccountRecord
        {
            Uuid = uuid,
            Username = clean,
            AccountType = "LocalSandbox",
            EncryptedAccessToken = SecureTokenVault.Encrypt("0", _vaultDir),
            EncryptedRefreshToken = "",
            ExpiresAtUtc = DateTime.UtcNow.AddYears(10),
            OwnsMinecraftJava = false, // Explicitly marked unverified: local singleplayer sandbox only
            LastUsedUtc = DateTime.UtcNow
        };
        _accounts.Add(record);
        SaveAccounts();
        return record;
    }

    public bool RemoveAccount(string uuid)
    {
        int removed = _accounts.RemoveAll(a => a.Uuid == uuid);
        if (removed > 0) SaveAccounts();
        return removed > 0;
    }

    public string GetDecryptedAccessToken(MinecraftAccountRecord account) =>
        SecureTokenVault.Decrypt(account.EncryptedAccessToken, _vaultDir);

    private async Task<(string AccessToken, string RefreshToken)> ExchangeCodeForMicrosoftTokensAsync(
        string code,
        string redirectUri,
        string codeVerifier,
        CancellationToken ct)
    {
        var form = new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["client_id"] = EffectiveClientId,
            ["grant_type"] = "authorization_code",
            ["code"] = code,
            ["redirect_uri"] = redirectUri,
            ["code_verifier"] = codeVerifier,
            ["scope"] = "XboxLive.signin offline_access"
        });

        using var resp = await _http.PostAsync(MsTokenEndpoint, form, ct).ConfigureAwait(false);
        string json = await resp.Content.ReadAsStringAsync(ct).ConfigureAwait(false);
        if (!resp.IsSuccessStatusCode)
            throw new InvalidOperationException("Failed to exchange Microsoft OAuth2 authorization code.");

        using var doc = JsonDocument.Parse(json);
        string access = doc.RootElement.GetProperty("access_token").GetString() ?? "";
        string refresh = doc.RootElement.TryGetProperty("refresh_token", out var rt) ? (rt.GetString() ?? "") : "";
        SecureTokenVault.RegisterSensitiveToken(access);
        SecureTokenVault.RegisterSensitiveToken(refresh);
        return (access, refresh);
    }

    private async Task<MinecraftAccountRecord> CompleteXboxAndMinecraftChainAsync(
        string msAccessToken,
        string msRefreshToken,
        Action<string>? statusCallback,
        CancellationToken ct)
    {
        statusCallback?.Invoke("Authenticating with Xbox Live...");
        var xblPayload = new
        {
            Properties = new
            {
                AuthMethod = "RPS",
                SiteName = "user.auth.xboxlive.com",
                RpsTicket = $"d={msAccessToken}"
            },
            RelyingParty = "http://auth.xboxlive.com",
            TokenType = "JWT"
        };
        using var xblDoc = await PostJsonAsync(XboxLiveAuthUrl, xblPayload, ct).ConfigureAwait(false);
        string xblToken = xblDoc.RootElement.GetProperty("Token").GetString() ?? "";
        string uhs = xblDoc.RootElement
            .GetProperty("DisplayClaims")
            .GetProperty("xui")[0]
            .GetProperty("uhs")
            .GetString() ?? "";
        SecureTokenVault.RegisterSensitiveToken(xblToken);

        statusCallback?.Invoke("Requesting XSTS security token...");
        var xstsPayload = new
        {
            Properties = new
            {
                SandboxId = "RETAIL",
                UserTokens = new[] { xblToken }
            },
            RelyingParty = "rp://api.minecraftservices.com/",
            TokenType = "JWT"
        };
        using var xstsDoc = await PostJsonAsync(XstsAuthUrl, xstsPayload, ct).ConfigureAwait(false);
        string xstsToken = xstsDoc.RootElement.GetProperty("Token").GetString() ?? "";
        SecureTokenVault.RegisterSensitiveToken(xstsToken);

        statusCallback?.Invoke("Signing in to Minecraft Services...");
        var mcLoginPayload = new { identityToken = $"XBL3.0 x={uhs};{xstsToken}" };
        using var mcDoc = await PostJsonAsync(McLoginWithXboxUrl, mcLoginPayload, ct).ConfigureAwait(false);
        string mcAccessToken = mcDoc.RootElement.GetProperty("access_token").GetString() ?? "";
        int expiresIn = mcDoc.RootElement.TryGetProperty("expires_in", out var exp) ? exp.GetInt32() : 86400;
        SecureTokenVault.RegisterSensitiveToken(mcAccessToken);

        statusCallback?.Invoke("Verifying Minecraft: Java Edition license ownership...");
        bool hasStoreEntitlement = await CheckEntitlementsAsync(mcAccessToken, ct).ConfigureAwait(false);
        var (uuid, username, hasProfile) = await FetchMinecraftProfileAsync(mcAccessToken, ct).ConfigureAwait(false);
        bool ownsJava = hasProfile || hasStoreEntitlement;

        if (!ownsJava)
        {
            throw new InvalidOperationException(
                "This Microsoft account does not own Minecraft: Java Edition. Purchase or redeem Java Edition on minecraft.net first.");
        }

        var existing = _accounts.FirstOrDefault(a => a.Uuid == uuid);
        if (existing is not null)
            _accounts.Remove(existing);

        var record = new MinecraftAccountRecord
        {
            Uuid = uuid,
            Username = username,
            AccountType = "Microsoft",
            EncryptedAccessToken = SecureTokenVault.Encrypt(mcAccessToken, _vaultDir),
            EncryptedRefreshToken = SecureTokenVault.Encrypt(msRefreshToken, _vaultDir),
            ExpiresAtUtc = DateTime.UtcNow.AddSeconds(Math.Max(300, expiresIn - 120)),
            OwnsMinecraftJava = true,
            LastUsedUtc = DateTime.UtcNow
        };
        _accounts.Insert(0, record);
        SaveAccounts();
        statusCallback?.Invoke($"Signed in as {username} (Java Edition Verified).");
        return record;
    }

    private async Task<bool> CheckEntitlementsAsync(string mcAccessToken, CancellationToken ct)
    {
        using var req = new HttpRequestMessage(HttpMethod.Get, McEntitlementsUrl);
        req.Headers.Authorization = new AuthenticationHeaderValue("Bearer", mcAccessToken);
        using var resp = await _http.SendAsync(req, ct).ConfigureAwait(false);
        if (!resp.IsSuccessStatusCode) return false;
        string json = await resp.Content.ReadAsStringAsync(ct).ConfigureAwait(false);
        using var doc = JsonDocument.Parse(json);
        return doc.RootElement.TryGetProperty("items", out var items) &&
               items.ValueKind == JsonValueKind.Array &&
               items.GetArrayLength() > 0;
    }

    private async Task<(string Uuid, string Username, bool Exists)> FetchMinecraftProfileAsync(string mcAccessToken, CancellationToken ct)
    {
        using var req = new HttpRequestMessage(HttpMethod.Get, McProfileUrl);
        req.Headers.Authorization = new AuthenticationHeaderValue("Bearer", mcAccessToken);
        using var resp = await _http.SendAsync(req, ct).ConfigureAwait(false);
        if (!resp.IsSuccessStatusCode)
            return ("", "", false);

        string json = await resp.Content.ReadAsStringAsync(ct).ConfigureAwait(false);
        using var doc = JsonDocument.Parse(json);
        string id = doc.RootElement.TryGetProperty("id", out var idEl) ? (idEl.GetString() ?? "") : "";
        string name = doc.RootElement.TryGetProperty("name", out var nameEl) ? (nameEl.GetString() ?? "") : "";
        return (id, name, !string.IsNullOrWhiteSpace(id) && !string.IsNullOrWhiteSpace(name));
    }

    private async Task<JsonDocument> PostJsonAsync(string url, object payload, CancellationToken ct)
    {
        string body = JsonSerializer.Serialize(payload);
        using var req = new HttpRequestMessage(HttpMethod.Post, url)
        {
            Content = new StringContent(body, Encoding.UTF8, "application/json")
        };
        req.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));
        using var resp = await _http.SendAsync(req, ct).ConfigureAwait(false);
        string respJson = await resp.Content.ReadAsStringAsync(ct).ConfigureAwait(false);
        if (!resp.IsSuccessStatusCode)
            throw new InvalidOperationException($"Authentication step failed ({(int)resp.StatusCode}) at {new Uri(url).Host}.");
        return JsonDocument.Parse(respJson);
    }

    private void LoadAccounts()
    {
        _accounts.Clear();
        try
        {
            if (!File.Exists(_accountsFilePath)) return;
            string json = File.ReadAllText(_accountsFilePath);
            var list = JsonSerializer.Deserialize<List<MinecraftAccountRecord>>(json);
            if (list is not null) _accounts.AddRange(list);
        }
        catch
        {
            // Non-crashing recovery if accounts vault is corrupted
        }
    }

    private void SaveAccounts()
    {
        Directory.CreateDirectory(_vaultDir);
        string tmp = _accountsFilePath + ".tmp";
        string json = JsonSerializer.Serialize(_accounts, new JsonSerializerOptions { WriteIndented = true });
        File.WriteAllText(tmp, json);
        File.Move(tmp, _accountsFilePath, overwrite: true);
    }

    private static int FindAvailableLoopbackPort()
    {
        using var sock = new System.Net.Sockets.TcpListener(IPAddress.Loopback, 0);
        sock.Start();
        int port = ((IPEndPoint)sock.LocalEndpoint).Port;
        sock.Stop();
        return port;
    }

    private static void OpenSystemBrowser(string url)
    {
        Process.Start(new ProcessStartInfo
        {
            FileName = url,
            UseShellExecute = true
        });
    }

    private static string SanitizeMinecraftUsername(string raw)
    {
        if (string.IsNullOrWhiteSpace(raw)) return "AxsesPlayer";
        var chars = raw.Trim().Where(c => char.IsLetterOrDigit(c) || c == '_').Take(16).ToArray();
        return chars.Length >= 3 ? new string(chars) : "AxsesPlayer";
    }

    private static string Base64UrlEncode(byte[] bytes) =>
        Convert.ToBase64String(bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_');
}
