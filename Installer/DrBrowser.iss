#define AppName "DrBrowser OMEGA"
#define AppVersion "3.0.0"
#define AppPublisher "DrBrowser"
#define AppExeName "DrBrowser.exe"

[Setup]
AppId={{A922134E-8B5C-4E59-BB42-A77D81838AA4}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher={#AppPublisher}
DefaultDirName={localappdata}\Programs\DrBrowser
DefaultGroupName=DrBrowser OMEGA
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
OutputDir=..\dist
OutputBaseFilename=DrBrowserSetup
UninstallDisplayIcon={app}\{#AppExeName}
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
CloseApplications=yes
RestartApplications=no
VersionInfoVersion={#AppVersion}
VersionInfoDescription=DrBrowser OMEGA Setup
VersionInfoProductName={#AppName}
VersionInfoProductVersion={#AppVersion}

[Languages]
Name: "russian"; MessagesFile: "compiler:Languages\Russian.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Создать ярлык на рабочем столе"; GroupDescription: "Ярлыки:"; Flags: unchecked

[Files]
Source: "..\dist\DrBrowser.exe"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{autoprograms}\DrBrowser OMEGA"; Filename: "{app}\{#AppExeName}"
Name: "{autodesktop}\DrBrowser OMEGA"; Filename: "{app}\{#AppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#AppExeName}"; Description: "Запустить DrBrowser OMEGA"; Flags: nowait postinstall skipifsilent

[Code]
function WebView2Present: Boolean;
begin
  Result :=
    DirExists(ExpandConstant('{pf32}\Microsoft\EdgeWebView\Application')) or
    DirExists(ExpandConstant('{pf}\Microsoft\EdgeWebView\Application')) or
    DirExists(ExpandConstant('{localappdata}\Microsoft\EdgeWebView\Application'));
end;

procedure CurStepChanged(CurStep: TSetupStep);
var
  ErrorCode: Integer;
begin
  if (CurStep = ssPostInstall) and (not WebView2Present) then
  begin
    if MsgBox(
      'Microsoft Edge WebView2 Runtime не найден. Он нужен для отображения веб-страниц.' + #13#10 +
      'Открыть официальную страницу загрузки Microsoft сейчас?',
      mbConfirmation, MB_YESNO) = IDYES then
      ShellExec('open', 'https://go.microsoft.com/fwlink/p/?LinkId=2124703', '', '', SW_SHOWNORMAL, ewNoWait, ErrorCode);
  end;
end;
