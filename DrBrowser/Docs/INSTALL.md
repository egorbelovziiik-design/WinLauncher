# Install, update and build DrBrowser OMEGA

## Supported platform

- Windows 10 or Windows 11, x64.
- Microsoft Edge WebView2 Evergreen Runtime.
- About 250 MB free disk space for the self-contained app, runtime extraction/cache and user data. The exact binary size is listed by the release and SHA sidecars.

The portable and installer builds contain the .NET 10 runtime. They do **not** contain WebView2 Runtime.

## Install with `DrBrowserSetup.exe`

1. Download `dist/DrBrowserSetup.exe` and `dist/DrBrowserSetup.exe.sha256.txt` from the same source revision/release.
2. Verify the checksum:
   ```powershell
   $actual = (Get-FileHash .\DrBrowserSetup.exe -Algorithm SHA256).Hash
   $expected = (Get-Content .\DrBrowserSetup.exe.sha256.txt).Trim()
   $actual
   $actual -eq $expected
   ```
3. Run the installer. It installs per user into `%LocalAppData%\Programs\DrBrowser`, so elevation is normally not required.
4. If WebView2 Runtime is missing, the installer offers to open Microsoft’s official Evergreen download page. Install it, then start DrBrowser again.

The installer is generated from `Installer/DrBrowser.iss` by Inno Setup in GitHub Actions. It is currently unsigned, so SmartScreen can warn.

## Portable use

1. Download `DrBrowser.exe` and its SHA-256 sidecar.
2. Verify the checksum with the same PowerShell procedure.
3. Put the EXE in any user-writable folder and run it.

The executable is a compressed .NET single file. On first launch, .NET may extract native components to its normal per-user bundle cache. Browser data is still stored under `%AppData%\DrBrowser`, not beside the executable.

## WebView2 Runtime

Official Evergreen Bootstrapper: <https://go.microsoft.com/fwlink/p/?LinkId=2124703>

Typical machine-wide location:

```powershell
Get-ChildItem "${env:ProgramFiles(x86)}\Microsoft\EdgeWebView\Application" -Directory
```

DrBrowser never disables certificate validation to compensate for a missing/broken runtime. When runtime creation fails it keeps local WPF pages available and presents an actionable error.

## Update

- Portable: close all DrBrowser windows, replace `DrBrowser.exe`, and keep `%AppData%\DrBrowser`.
- Installer: run the newer setup over the existing installation. Its stable AppId updates in place.
- Back up first from **Settings → Backup** if local data matters.

Schema creation uses `CREATE TABLE IF NOT EXISTS` and version metadata. Settings are normalized when loaded. Do not downgrade over an important profile without a backup; backward compatibility is not guaranteed for future schema versions.

## Uninstall and data removal

The Inno uninstaller removes program files and shortcuts. It intentionally leaves `%AppData%\DrBrowser`, because it can contain history, Notes, Tasks, bookmarks and DPAPI ciphertext.

To erase all persistent data after closing the app:

```powershell
Remove-Item "$env:APPDATA\DrBrowser" -Recurse -Force
```

This is irreversible. A normal `.drbackup` intentionally excludes API keys and WebView2 cookies/cache.

## Build from source

Install:

- .NET SDK 10 (`10.0.100` or a compatible feature band per `global.json`).
- Git.
- Optional: Inno Setup 6 to build the installer.

From repository root:

```powershell
dotnet --info
dotnet restore DrBrowserOmega.sln
dotnet build DrBrowserOmega.sln -c Release --no-restore
dotnet publish DrBrowser\DrBrowser.csproj -c Release -r win-x64 --self-contained true `
  -p:PublishSingleFile=true `
  -p:IncludeNativeLibrariesForSelfExtract=true `
  -p:EnableCompressionInSingleFile=true `
  -p:DebugType=None -p:DebugSymbols=false `
  -o dist
```

Build installer after publishing:

```powershell
& "${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe" Installer\DrBrowser.iss
```

Outputs:

- `dist\DrBrowser.exe`
- `dist\DrBrowserSetup.exe` (when Inno Setup succeeds)

## Verification modes

```powershell
.\dist\DrBrowser.exe --selftest
$LASTEXITCODE
.\dist\DrBrowser.exe --smoke
$LASTEXITCODE
```

Exit `0` means no executed check failed. A physically unavailable capability may be recorded as `SKIP`; read the report rather than interpreting a process exit alone. Set `DRBROWSER_SHOTS` to choose the report/screenshot directory.

## Troubleshooting

### Websites do not render

Open **Settings → About** and read the runtime status. Install/update WebView2 Runtime. Do not add insecure certificate switches.

### SmartScreen warning

The binaries are unsigned. Verify SHA-256 and the GitHub Actions provenance. Do not disable SmartScreen globally.

### Settings or database problems

Logs are under `%AppData%\DrBrowser\logs`. A corrupt settings JSON is copied to a timestamped recovery file before defaults are created. Use a validated backup to restore SQLite/settings.

### Ollama unavailable

DrBrowser accepts Ollama only on loopback (`127.0.0.1`, `localhost` or `::1`). Start Ollama separately, install a model yourself, then use **Settings → AI Assistant → Check Ollama**. DrBrowser does not download a model.
