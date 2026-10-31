; AXSESCLIENT (Codename: OBSIDIAN AURORA) v2.5.0 — Official Windows 10/11 x64 Installer
; Built with Inno Setup 6. Preserves all user worlds, screenshots, and configs on upgrade/uninstall.

#define MyAppName "AXSESCLIENT"
#define MyAppVersion "2.5.0"
#define MyAppPublisher "AXSESCLIENT Engineering"
#define MyAppURL "https://github.com/egorbelovziiik-design/WinLauncher"
#define MyAppExeName "AXSESCLIENT.exe"

[Setup]
AppId={{8D7E4A92-5C21-4F18-9A63-1B0E2F7C9100}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion} (OBSIDIAN AURORA)
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}
DefaultDirName={localappdata}\Programs\{#MyAppName}
DefaultGroupName={#MyAppName}
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
DisableProgramGroupPage=yes
SetupIconFile=..\assets\branding\axsesclient.ico
UninstallDisplayIcon={app}\{#MyAppExeName}
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
OutputDir=..\..\AXSESCLIENT-RELEASE
OutputBaseFilename=AXSESCLIENT-Setup

[Languages]
Name: "russian"; MessagesFile: "compiler:Languages\Russian.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
; Self-contained Windows x64 Launcher Executable
Source: "..\..\AXSESCLIENT-RELEASE\AXSESCLIENT.exe"; DestDir: "{app}"; Flags: ignoreversion

; Isolated Per-Version Fabric Client Mods (Zero proprietary Mojang files bundled)
Source: "..\..\AXSESCLIENT-RELEASE\AXSESCLIENT-1.16.5.jar"; DestDir: "{app}\mods"; Flags: ignoreversion
Source: "..\..\AXSESCLIENT-RELEASE\AXSESCLIENT-1.21.4.jar"; DestDir: "{app}\mods"; Flags: ignoreversion
Source: "..\..\AXSESCLIENT-RELEASE\AXSESCLIENT-1.21.11.jar"; DestDir: "{app}\mods"; Flags: ignoreversion

; Official AXSES NEO-DARK Themes, Schemas & Branding
Source: "..\shared\themes\*.json"; DestDir: "{app}\themes"; Flags: ignoreversion
Source: "..\shared\schemas\*.json"; DestDir: "{app}\schemas"; Flags: ignoreversion
Source: "..\shared\design-tokens.json"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\shared\update-manifest.json"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\assets\branding\axsesclient.ico"; DestDir: "{app}\assets"; Flags: ignoreversion
Source: "..\assets\branding\icon-256.png"; DestDir: "{app}\assets"; Flags: ignoreversion

[Icons]
Name: "{autoprograms}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\assets\axsesclient.ico"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\assets\axsesclient.ico"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent
