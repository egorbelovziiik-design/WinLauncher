# This test installs/uninstalls the real Tauri NSIS output on a disposable
# GitHub Windows runner. It must never run on an end user's existing install.
$ErrorActionPreference = 'Stop'
if ($env:GITHUB_ACTIONS -ne 'true' -or -not $env:RUNNER_TEMP) {
  throw 'Installer acceptance tests are restricted to a disposable GitHub Actions runner.'
}
$reports = [System.Collections.Generic.List[object]]::new()
$destination = Join-Path $env:RUNNER_TEMP ('WinLauncher-setup-' + [Guid]::NewGuid().ToString())
$output = Join-Path $PWD 'release-artifacts'
New-Item -ItemType Directory -Force $output | Out-Null
$probe = Join-Path $env:APPDATA 'io.winlauncher.desktop/data/.ci-uninstall-preservation-probe'
$database = Join-Path $env:APPDATA 'io.winlauncher.desktop/winlauncher.sqlite3'
$failure = $false
function Add-Result([string]$Name, [string]$Status, [string]$Detail) {
  $reports.Add(@{ check = $Name; status = $Status; detail = $Detail })
  Write-Output "$Status`: $Name - $Detail"
}
try {
  if (Test-Path $database) { throw 'An existing native app database is present. Refusing to run installer tests against existing user data.' }
  $installer = Get-ChildItem 'src-tauri/target/x86_64-pc-windows-msvc/release/bundle/nsis/*-setup.exe' | Select-Object -First 1
  if (-not $installer) { throw 'Tauri NSIS output not found' }
  $install = Start-Process -FilePath $installer.FullName -ArgumentList "/S /D=$destination" -PassThru
  $null = $install.Handle
  if (-not $install.WaitForExit(240000)) { Stop-Process -Id $install.Id -Force; throw 'NSIS installation exceeded four minutes' }
  if ($install.ExitCode -ne 0) { throw "NSIS installer returned $($install.ExitCode)" }
  $installed = Join-Path $destination 'winlauncher.exe'
  if (-not (Test-Path $installed)) { throw 'The installer did not create winlauncher.exe in the requested directory' }
  $expected = (Get-FileHash 'src-tauri/target/x86_64-pc-windows-msvc/release/winlauncher.exe' -Algorithm SHA256).Hash
  $actual = (Get-FileHash $installed -Algorithm SHA256).Hash
  if ($actual -ne $expected) {
    $originalBytes = [IO.File]::ReadAllBytes((Resolve-Path 'src-tauri/target/x86_64-pc-windows-msvc/release/winlauncher.exe'))
    $installedBytes = [IO.File]::ReadAllBytes($installed)
    $differences = [System.Collections.Generic.List[string]]::new()
    for ($i = 0; $i -lt [Math]::Min($originalBytes.Length, $installedBytes.Length) -and $differences.Count -lt 16; $i++) {
      if ($originalBytes[$i] -ne $installedBytes[$i]) { $differences.Add(('0x{0:X}:{1:X2}->{2:X2}' -f $i, $originalBytes[$i], $installedBytes[$i])) }
    }
    throw "Installed executable differs: expected SHA256=$expected bytes=$($originalBytes.Length); installed SHA256=$actual bytes=$($installedBytes.Length); first byte differences=$($differences -join ',')"
  }
  Add-Result 'Real NSIS silent installation' 'PASSED' 'Installed into a new isolated directory. The installed executable matches the production MSVC output SHA256.'
  New-Item -ItemType Directory -Force (Split-Path $probe) | Out-Null
  $probeContents = [Guid]::NewGuid().ToString()
  Set-Content -LiteralPath $probe -Value $probeContents -NoNewline
  $uninstaller = Get-ChildItem -LiteralPath $destination -Filter '*uninstall*.exe' | Select-Object -First 1
  if (-not $uninstaller) { throw 'NSIS uninstaller was not found' }
  $uninstall = Start-Process -FilePath $uninstaller.FullName -ArgumentList '/S' -PassThru
  $null = $uninstall.Handle
  if (-not $uninstall.WaitForExit(120000)) { Stop-Process -Id $uninstall.Id -Force; throw 'NSIS uninstallation exceeded two minutes' }
  if ($uninstall.ExitCode -ne 0) { throw "NSIS uninstaller returned $($uninstall.ExitCode)" }
  $deadline = [DateTime]::UtcNow.AddSeconds(30)
  while ((Test-Path $installed) -and [DateTime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 500 }
  if (Test-Path $installed) { throw 'Installed EXE remains after NSIS uninstallation' }
  if (-not (Test-Path $probe) -or (Get-Content -LiteralPath $probe -Raw) -ne $probeContents) { throw 'Uninstaller changed the existing app-data probe' }
  Add-Result 'Real NSIS silent uninstallation' 'PASSED' 'The installed executable was removed. The app-data preservation probe survived unchanged. This probe is not a Minecraft world.'
  Add-Result 'Interactive installer, SmartScreen and standard-user desktop flow' 'NOT TESTED' 'Silent execution on a disposable Windows runner does not verify the full interactive user experience.'
} catch {
  $failure = $true
  $detail = $_.Exception.Message
  Add-Result 'NSIS installer acceptance' 'FAILED' $detail
  $safe = $detail.Replace('%','%25').Replace("`r",'%0D').Replace("`n",'%0A')
  Write-Output "::error title=INSTALLER_ACCEPTANCE::$safe"
} finally {
  $binaryCommit = if ($env:WINLAUNCHER_BINARY_COMMIT) { $env:WINLAUNCHER_BINARY_COMMIT } else { $env:GITHUB_SHA }
  @{ source_commit = $binaryCommit; harness_commit = $env:GITHUB_SHA; generated_at = [DateTime]::UtcNow.ToString('o'); runtime = 'Real Tauri NSIS on Windows x64'; checks = $reports.ToArray() } | ConvertTo-Json -Depth 8 | Set-Content -Encoding utf8 (Join-Path $output 'INSTALLER-SMOKE.json')
  $text = "# Native NSIS installer verification`n`n" + (($reports | ForEach-Object { "## $($_.check): $($_.status)`n`n$($_.detail)`n" }) -join "`n")
  Set-Content -Encoding utf8 (Join-Path $output 'INSTALLER-SMOKE.md') -Value $text
  if (Test-Path $probe) { Remove-Item -LiteralPath $probe }
}
if ($failure) { exit 1 }
